package com.gamersblended.junes.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    private final ProxyManager<String> proxyManager;

    @Autowired
    public RateLimiterService(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public boolean isAllowed(String key) {
        Bucket bucket = getBucket(key);
        return bucket.tryConsume(1);
    }

    public long getAvailableTokens(String key) {
        Bucket bucket = getBucket(key);
        return bucket.getAvailableTokens();
    }

    public long getRemainingTimeInSeconds(String key) {
        Bucket bucket = getBucket(key);
        return bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
    }

    private Bucket getBucket(String key) {
        Supplier<BucketConfiguration> configSupplier = () -> {
            // 5 requests per min
            Bandwidth bandwidth = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            return BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();
        };

        return proxyManager.builder()
                .build(key, configSupplier);
    }
}
