package com.gamersblended.junes.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderHistoryCacheService {

    private final RedisTemplate<String, List<ProductSignalDTO>> orderHistoryRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<ProductSignalDTO>> TYPE_REF = new TypeReference<>() {
    };
    private static final String KEY_PREFIX = "order-history:";

    @Value("${recommender.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Autowired
    public OrderHistoryCacheService(RedisTemplate<String, List<ProductSignalDTO>> orderHistoryRedisTemplate, ObjectMapper objectMapper) {
        this.orderHistoryRedisTemplate = orderHistoryRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public String buildKey(UUID userID) {
        return KEY_PREFIX + userID.toString();
    }

    public Optional<List<ProductSignalDTO>> get(UUID userID) {
        String key = buildKey(userID);

        try {
            Object raw = orderHistoryRedisTemplate.opsForValue().get(key);

            if (null == raw) {
                log.info("[OrderHistoryCache] MISS for userID = {}", userID);
                return Optional.empty();
            }

            List<ProductSignalDTO> cached = objectMapper.convertValue(raw, TYPE_REF);
            log.info("[OrderHistoryCache] HIT for userID = {}", userID);
            return Optional.of(cached);
        } catch (Exception ex) {
            log.error("[OrderHistoryCache] Read failed for userID = {}: {}", userID, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID userID, List<ProductSignalDTO> productSignalDTOList) {
        String key = buildKey(userID);

        try {
            orderHistoryRedisTemplate.opsForValue().set(key, productSignalDTOList, cacheTtlMinutes, TimeUnit.MINUTES);
            log.info("[OrderHistoryCache] Stored for userID = {} TTL = {}min", userID, cacheTtlMinutes);
        } catch (Exception ex) {
            log.error("[OrderHistoryCache] Write failed for userID = {}: {}", userID, ex.getMessage());
        }
    }
}
