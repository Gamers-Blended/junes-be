package com.gamersblended.junes.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean
    public LettuceBasedProxyManager proxyManager() {
        RedisURI redisURI = RedisURI.Builder.redis(redisHost, redisPort).build();
        RedisClient redisClient = RedisClient.create(redisURI);

        return LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

    @Bean
    public BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(10).refillGreedy(10, Duration.ofMinutes(1)))
                .build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(proxyManager(), bucketConfiguration()))
                .addPathPatterns("/api/**");
    }

    public static class RateLimitInterceptor implements HandlerInterceptor {
        private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

        private final LettuceBasedProxyManager proxyManager;
        private final BucketConfiguration bucketConfiguration;

        public RateLimitInterceptor(LettuceBasedProxyManager proxyManager, BucketConfiguration bucketConfiguration) {
            this.proxyManager = proxyManager;
            this.bucketConfiguration = bucketConfiguration;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String key = getClientKey(request);
            logger.info("Rate limit check for key: {}, URL: {}", key, request.getRequestURI());

            Bucket bucket = proxyManager.builder().build(key, bucketConfiguration);

            long availableTokens = bucket.getAvailableTokens();
            logger.info("Available tokens before request: {}", availableTokens);

            if (bucket.tryConsume(1)) {
                long remainingTokens = bucket.getAvailableTokens();
                logger.info("Request allowed. Remaining tokens: {}", remainingTokens);
                response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
                return true;
            } else {
                logger.warn("Rate limit exceeded for key: {}", key);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests.\"}");
                return false;
            }
        }

        private String getClientKey(HttpServletRequest request) {
            // Try X-Forwarded-For first (for load balancers/proxies)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            // Fall back to remote address
            return request.getRemoteAddr();
        }
    }
}
