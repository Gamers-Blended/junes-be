package com.gamersblended.junes.service;

import com.gamersblended.junes.annotation.RateLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    private final ProxyManager<String> proxyManager;

    public RateLimiterService(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public boolean isAllowed(String key, RateLimit rateLimit) {
        Bucket bucket = getBucket(key, rateLimit);
        return bucket.tryConsume(1);
    }

    public long getAvailableTokens(String key, RateLimit rateLimit) {
        Bucket bucket = getBucket(key, rateLimit);
        return bucket.getAvailableTokens();
    }

    public long getRemainingTimeInSeconds(String key, RateLimit rateLimit) {
        Bucket bucket = getBucket(key, rateLimit);
        return bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
    }

    private Bucket getBucket(String key, RateLimit rateLimit) {
        Supplier<BucketConfiguration> configSupplier = () -> {
            Duration duration = convertToDuration(rateLimit.duration(), rateLimit.timeUnit());
            Bandwidth bandwidth = Bandwidth.classic(rateLimit.requests(), Refill.intervally(rateLimit.requests(), duration));
            return BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();
        };

        String bucketKey = key + ":" + rateLimit.requests() + ":" + rateLimit.duration() + ":" + rateLimit.timeUnit();
        return proxyManager.builder()
                .build(bucketKey, configSupplier);
    }

    private Duration convertToDuration(int duration, TimeUnit timeUnit) {
        return switch (timeUnit) {
            case SECONDS -> Duration.ofSeconds(duration);
            case MINUTES -> Duration.ofMinutes(duration);
            case HOURS -> Duration.ofHours(duration);
            case DAYS -> Duration.ofDays(duration);
            default -> Duration.ofMinutes(duration);
        };
    }
}
