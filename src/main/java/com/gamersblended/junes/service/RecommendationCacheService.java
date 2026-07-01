package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RecommendationCacheService {

    private final RedisTemplate<String, RecommendationResponseDTO> recommendationRedisTemplate;
    private final RecommendationCacheKeyBuilder cacheKeyBuilder;

    @Value("${recommender.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Autowired
    public RecommendationCacheService(RedisTemplate<String, RecommendationResponseDTO> recommendationRedisTemplate, RecommendationCacheKeyBuilder cacheKeyBuilder) {
        this.recommendationRedisTemplate = recommendationRedisTemplate;
        this.cacheKeyBuilder = cacheKeyBuilder;
    }

    public Optional<RecommendationResponseDTO> get(List<ProductSignalDTO> productSignalDTOList) {
        String key = cacheKeyBuilder.buildKey(productSignalDTOList);

        try {
            RecommendationResponseDTO cached = recommendationRedisTemplate.opsForValue().get(key);

            if (null != cached) {
                log.info("[RecommendationCache] HIT for key = {}", key);
                return Optional.of(cached);
            }

            log.info("[RecommendationCache] MISS for key = {}", key);
            return Optional.empty();
        } catch (Exception ex) {
            log.error("[RecommendationCache] Failed to read from Redis for key = {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(List<ProductSignalDTO> productSignalDTOList, RecommendationResponseDTO responseDTO) {
        String key = cacheKeyBuilder.buildKey(productSignalDTOList);

        try {
            recommendationRedisTemplate.opsForValue().set(key, responseDTO, cacheTtlMinutes, TimeUnit.MINUTES);
            log.info("[RecommendationCache] Stored key = {} TTL = {}min", key, cacheTtlMinutes);
        } catch (Exception ex) {
            log.error("[RecommendationCache] Failed to write to Redis for key = {}: {}", key, ex.getMessage());
        }
    }
}
