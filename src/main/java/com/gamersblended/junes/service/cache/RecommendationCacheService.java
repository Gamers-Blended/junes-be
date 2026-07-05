package com.gamersblended.junes.service.cache;

import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecommendationCacheService {

    private final RedisTemplate<String, RecommendationResponseDTO> recommendationRedisTemplate;

    private static final String KEY_PREFIX = "recommendations:";

    @Value("${recommender.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Autowired
    public RecommendationCacheService(RedisTemplate<String, RecommendationResponseDTO> recommendationRedisTemplate) {
        this.recommendationRedisTemplate = recommendationRedisTemplate;
    }

    public String buildKey(List<ProductSignalDTO> signalDTOList) {
        if (null == signalDTOList || signalDTOList.isEmpty()) {
            return KEY_PREFIX + "empty";
        }

        String sortedIDs = signalDTOList.stream()
                .map(ProductSignalDTO::getProductID)
                .filter(Objects::nonNull)
                .sorted() // order-independent
                .collect(Collectors.joining(","));

        // MD5 for fixed-length, collision-resistant strings
        String hash = DigestUtils.md5DigestAsHex(sortedIDs.getBytes(StandardCharsets.UTF_8));

        log.info("[RecommendationCache] signals = {} -> key = {}{}", sortedIDs, KEY_PREFIX, hash);
        return KEY_PREFIX + hash;
    }

    public Optional<RecommendationResponseDTO> get(List<ProductSignalDTO> productSignalDTOList) {
        String key = buildKey(productSignalDTOList);

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
        String key = buildKey(productSignalDTOList);

        try {
            recommendationRedisTemplate.opsForValue().set(key, responseDTO, cacheTtlMinutes, TimeUnit.MINUTES);
            log.info("[RecommendationCache] Stored key = {} TTL = {}min", key, cacheTtlMinutes);
        } catch (Exception ex) {
            log.error("[RecommendationCache] Failed to write to Redis for key = {}: {}", key, ex.getMessage());
        }
    }
}
