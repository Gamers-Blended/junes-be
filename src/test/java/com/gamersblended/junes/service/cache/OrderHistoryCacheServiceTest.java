package com.gamersblended.junes.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHistoryCacheServiceTest {

    private static final long TTL_MINUTES = 10L;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    private OrderHistoryCacheService orderHistoryCacheService;

    @BeforeEach
    void setUp() {
        orderHistoryCacheService = new OrderHistoryCacheService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(orderHistoryCacheService, "cacheTtlMinutes", TTL_MINUTES);
    }

    private static ProductSignalDTO signal() {
        return new ProductSignalDTO("p1", "view", LocalDateTime.now());
    }

    // ---- buildKey ----

    @Test
    void buildKey_returnsPrefixedUserID() {
        UUID userID = UUID.randomUUID();

        String key = orderHistoryCacheService.buildKey(userID);

        assertThat(key).isEqualTo("order-history:" + userID);
    }

    // ---- get ----

    @Test
    void get_returnsEmpty_whenCacheMiss() {
        UUID userID = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("order-history:" + userID)).thenReturn(null);

        Optional<List<ProductSignalDTO>> result = orderHistoryCacheService.get(userID);

        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsDeserializedList_whenCacheHit() throws Exception {
        UUID userID = UUID.randomUUID();
        List<ProductSignalDTO> cached = List.of(signal());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("order-history:" + userID)).thenReturn("[{\"product_id\":\"p1\"}]");
        when(objectMapper.readValue(eq("[{\"product_id\":\"p1\"}]"),
                ArgumentMatchers.<TypeReference<List<ProductSignalDTO>>>any()))
                .thenReturn(cached);

        Optional<List<ProductSignalDTO>> result = orderHistoryCacheService.get(userID);

        assertThat(result).contains(cached);
    }

    @Test
    void get_returnsEmpty_whenDeserializationFails() throws Exception {
        UUID userID = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("order-history:" + userID)).thenReturn("not-json");
        when(objectMapper.readValue(anyString(), ArgumentMatchers.<TypeReference<List<ProductSignalDTO>>>any()))
                .thenThrow(new JsonProcessingException("boom") {
                });

        Optional<List<ProductSignalDTO>> result = orderHistoryCacheService.get(userID);

        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsEmpty_whenRedisThrows() {
        UUID userID = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("order-history:" + userID)).thenThrow(new RuntimeException("redis down"));

        Optional<List<ProductSignalDTO>> result = orderHistoryCacheService.get(userID);

        assertThat(result).isEmpty();
    }

    // ---- put ----

    @Test
    void put_storesSerializedJson_withConfiguredTtl() throws Exception {
        UUID userID = UUID.randomUUID();
        List<ProductSignalDTO> signals = List.of(signal());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(signals)).thenReturn("[{\"product_id\":\"p1\"}]");

        orderHistoryCacheService.put(userID, signals);

        verify(valueOperations).set("order-history:" + userID, "[{\"product_id\":\"p1\"}]", TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Test
    void put_doesNotThrow_whenSerializationFails() throws Exception {
        UUID userID = UUID.randomUUID();
        List<ProductSignalDTO> signals = List.of(signal());
        when(objectMapper.writeValueAsString(signals)).thenThrow(new JsonProcessingException("boom") {
        });

        orderHistoryCacheService.put(userID, signals);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void put_doesNotThrow_whenRedisWriteFails() throws Exception {
        UUID userID = UUID.randomUUID();
        List<ProductSignalDTO> signals = List.of(signal());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(signals)).thenReturn("[{\"product_id\":\"p1\"}]");
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set("order-history:" + userID, "[{\"product_id\":\"p1\"}]", TTL_MINUTES, TimeUnit.MINUTES);

        assertThatCode(() -> orderHistoryCacheService.put(userID, signals)).doesNotThrowAnyException();
    }

    // ---- evict ----

    @Test
    void evict_deletesKey() {
        UUID userID = UUID.randomUUID();
        when(redisTemplate.delete("order-history:" + userID)).thenReturn(true);

        orderHistoryCacheService.evict(userID);

        verify(redisTemplate).delete("order-history:" + userID);
    }

    @Test
    void evict_doesNotThrow_whenRedisThrows() {
        UUID userID = UUID.randomUUID();
        when(redisTemplate.delete("order-history:" + userID)).thenThrow(new RuntimeException("redis down"));

        orderHistoryCacheService.evict(userID);

        verify(redisTemplate).delete("order-history:" + userID);
    }
}
