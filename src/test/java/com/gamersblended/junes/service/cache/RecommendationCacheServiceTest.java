package com.gamersblended.junes.service.cache;

import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationCacheServiceTest {

    private static final long TTL_MINUTES = 10L;
    private static final String KEY_PREFIX = "recommendations:";

    @Mock
    private RedisTemplate<String, RecommendationResponseDTO> recommendationRedisTemplate;
    @Mock
    private ValueOperations<String, RecommendationResponseDTO> valueOperations;

    private RecommendationCacheService recommendationCacheService;

    @BeforeEach
    void setUp() {
        recommendationCacheService = new RecommendationCacheService(recommendationRedisTemplate);
        ReflectionTestUtils.setField(recommendationCacheService, "cacheTtlMinutes", TTL_MINUTES);
    }

    private static ProductSignalDTO signal(String productID) {
        return new ProductSignalDTO(productID, "view", LocalDateTime.now());
    }

    private static String expectedKey(String sortedCommaJoinedIDs) {
        return KEY_PREFIX + DigestUtils.md5DigestAsHex(sortedCommaJoinedIDs.getBytes(StandardCharsets.UTF_8));
    }

    // ---- buildKey ----

    @Test
    void buildKey_returnsEmptyKey_whenListIsNull() {
        assertThat(recommendationCacheService.buildKey(null)).isEqualTo(KEY_PREFIX + "empty");
    }

    @Test
    void buildKey_returnsEmptyKey_whenListIsEmpty() {
        assertThat(recommendationCacheService.buildKey(List.of())).isEqualTo(KEY_PREFIX + "empty");
    }

    @Test
    void buildKey_sortsProductIDs_beforeHashing() {
        List<ProductSignalDTO> signals = List.of(signal("p2"), signal("p1"));

        String key = recommendationCacheService.buildKey(signals);

        assertThat(key).isEqualTo(expectedKey("p1,p2"));
    }

    @Test
    void buildKey_isOrderIndependent_forSameProductIDs() {
        List<ProductSignalDTO> signalsA = List.of(signal("p1"), signal("p2"));
        List<ProductSignalDTO> signalsB = List.of(signal("p2"), signal("p1"));

        assertThat(recommendationCacheService.buildKey(signalsA))
                .isEqualTo(recommendationCacheService.buildKey(signalsB));
    }

    @Test
    void buildKey_filtersOutNullProductIDs() {
        List<ProductSignalDTO> signals = List.of(signal("p1"), new ProductSignalDTO(null, "view", LocalDateTime.now()));

        String key = recommendationCacheService.buildKey(signals);

        assertThat(key).isEqualTo(expectedKey("p1"));
    }

    @Test
    void buildKey_producesDifferentKeys_forDifferentProductSets() {
        String keyA = recommendationCacheService.buildKey(List.of(signal("p1")));
        String keyB = recommendationCacheService.buildKey(List.of(signal("p2")));

        assertThat(keyA).isNotEqualTo(keyB);
    }

    // ---- get ----

    @Test
    void get_returnsCached_whenPresent() {
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        RecommendationResponseDTO response = new RecommendationResponseDTO();
        when(recommendationRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(recommendationCacheService.buildKey(signals))).thenReturn(response);

        Optional<RecommendationResponseDTO> result = recommendationCacheService.get(signals);

        assertThat(result).contains(response);
    }

    @Test
    void get_returnsEmpty_whenCacheMiss() {
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        when(recommendationRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(recommendationCacheService.buildKey(signals))).thenReturn(null);

        Optional<RecommendationResponseDTO> result = recommendationCacheService.get(signals);

        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsEmpty_whenRedisThrows() {
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        when(recommendationRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(recommendationCacheService.buildKey(signals)))
                .thenThrow(new RuntimeException("redis down"));

        Optional<RecommendationResponseDTO> result = recommendationCacheService.get(signals);

        assertThat(result).isEmpty();
    }

    // ---- put ----

    @Test
    void put_storesResponse_withConfiguredTtl() {
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        RecommendationResponseDTO response = new RecommendationResponseDTO();
        when(recommendationRedisTemplate.opsForValue()).thenReturn(valueOperations);

        recommendationCacheService.put(signals, response);

        verify(valueOperations).set(recommendationCacheService.buildKey(signals), response, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Test
    void put_doesNotThrow_whenRedisWriteFails() {
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        RecommendationResponseDTO response = new RecommendationResponseDTO();
        String key = recommendationCacheService.buildKey(signals);
        when(recommendationRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set(key, response, TTL_MINUTES, TimeUnit.MINUTES);

        assertThatCode(() -> recommendationCacheService.put(signals, response)).doesNotThrowAnyException();
    }
}
