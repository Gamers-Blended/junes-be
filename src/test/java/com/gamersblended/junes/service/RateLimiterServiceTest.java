package com.gamersblended.junes.service;

import com.gamersblended.junes.annotation.RateLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private RemoteBucketBuilder<String> remoteBucketBuilder;

    @Mock
    private BucketProxy bucket;

    @Captor
    private ArgumentCaptor<Supplier<BucketConfiguration>> configCaptor;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(proxyManager);
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        when(remoteBucketBuilder.build(anyString(), ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
    }

    private static RateLimit rateLimit(int requests, int duration, TimeUnit timeUnit) {
        return new RateLimit() {
            @Override
            public int requests() {
                return requests;
            }

            @Override
            public int duration() {
                return duration;
            }

            @Override
            public TimeUnit timeUnit() {
                return timeUnit;
            }

            @Override
            public String key() {
                return "";
            }

            @Override
            public boolean perUser() {
                return false;
            }

            @Override
            public String keyFromRequestBody() {
                return "";
            }

            @Override
            public String keyFromRequestParam() {
                return "";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RateLimit.class;
            }
        };
    }

    // ---- isAllowed ----

    @Test
    void isAllowed_returnsTrue_whenBucketAllowsConsumption() {
        when(bucket.tryConsume(1)).thenReturn(true);

        boolean allowed = rateLimiterService.isAllowed("user-1", rateLimit(5, 1, TimeUnit.MINUTES));

        assertThat(allowed).isTrue();
    }

    @Test
    void isAllowed_returnsFalse_whenBucketDeniesConsumption() {
        when(bucket.tryConsume(1)).thenReturn(false);

        boolean allowed = rateLimiterService.isAllowed("user-1", rateLimit(5, 1, TimeUnit.MINUTES));

        assertThat(allowed).isFalse();
    }

    // ---- getAvailableTokens ----

    @Test
    void getAvailableTokens_returnsTokenCountFromBucket() {
        when(bucket.getAvailableTokens()).thenReturn(3L);

        long tokens = rateLimiterService.getAvailableTokens("user-1", rateLimit(5, 1, TimeUnit.MINUTES));

        assertThat(tokens).isEqualTo(3L);
    }

    // ---- getRemainingTimeInSeconds ----

    @Test
    void getRemainingTimeInSeconds_returnsRawNanosToWaitForRefillFromProbe() {
        when(bucket.estimateAbilityToConsume(1))
                .thenReturn(EstimationProbe.canNotBeConsumed(0, 45_000_000_000L));

        long remaining = rateLimiterService.getRemainingTimeInSeconds("user-1", rateLimit(5, 1, TimeUnit.MINUTES));

        // Despite the method name, the raw nanosecond value from the probe is returned unconverted.
        assertThat(remaining).isEqualTo(45_000_000_000L);
    }

    @Test
    void getRemainingTimeInSeconds_returnsZero_whenBucketCanBeConsumed() {
        when(bucket.estimateAbilityToConsume(1)).thenReturn(EstimationProbe.canBeConsumed(5));

        long remaining = rateLimiterService.getRemainingTimeInSeconds("user-1", rateLimit(5, 1, TimeUnit.MINUTES));

        assertThat(remaining).isZero();
    }

    // ---- bucket key construction ----

    @Test
    void isAllowed_buildsBucketKey_fromKeyAndRateLimitParams() {
        when(bucket.tryConsume(1)).thenReturn(true);

        rateLimiterService.isAllowed("192.168.1.1", rateLimit(10, 2, TimeUnit.HOURS));

        verify(remoteBucketBuilder).build(
                eq("192.168.1.1:10:2:HOURS"), ArgumentMatchers.<Supplier<BucketConfiguration>>any());
    }

    // ---- bandwidth/duration conversion ----

    @ParameterizedTest
    @EnumSource(value = TimeUnit.class, names = {"SECONDS", "MINUTES", "HOURS", "DAYS"})
    void getBucket_convertsDuration_forEachSupportedTimeUnit(TimeUnit timeUnit) {
        when(bucket.tryConsume(1)).thenReturn(true);

        rateLimiterService.isAllowed("user-1", rateLimit(7, 3, timeUnit));

        verify(remoteBucketBuilder).build(anyString(), configCaptor.capture());
        Duration expected = switch (timeUnit) {
            case SECONDS -> Duration.ofSeconds(3);
            case MINUTES -> Duration.ofMinutes(3);
            case HOURS -> Duration.ofHours(3);
            case DAYS -> Duration.ofDays(3);
            default -> throw new IllegalStateException("Unexpected value: " + timeUnit);
        };
        assertThat(refillPeriod(configCaptor.getValue())).isEqualTo(expected);
    }

    @Test
    void getBucket_fallsBackToMinutes_forUnsupportedTimeUnit() {
        when(bucket.tryConsume(1)).thenReturn(true);

        rateLimiterService.isAllowed("user-1", rateLimit(7, 3, TimeUnit.MILLISECONDS));

        verify(remoteBucketBuilder).build(anyString(), configCaptor.capture());
        assertThat(refillPeriod(configCaptor.getValue())).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void getBucket_setsBandwidthCapacity_toRequestsFromRateLimit() {
        when(bucket.tryConsume(1)).thenReturn(true);

        rateLimiterService.isAllowed("user-1", rateLimit(20, 1, TimeUnit.MINUTES));

        verify(remoteBucketBuilder).build(anyString(), configCaptor.capture());
        Bandwidth bandwidth = configCaptor.getValue().get().getBandwidths()[0];
        assertThat(bandwidth.getCapacity()).isEqualTo(20L);
    }

    private static Duration refillPeriod(Supplier<BucketConfiguration> configSupplier) {
        Bandwidth bandwidth = configSupplier.get().getBandwidths()[0];
        return Duration.ofNanos(bandwidth.getRefillPeriodNanos());
    }
}
