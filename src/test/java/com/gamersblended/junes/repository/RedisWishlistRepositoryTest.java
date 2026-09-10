package com.gamersblended.junes.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.exception.RedisDataException;
import com.gamersblended.junes.exception.WishlistSerialisationException;
import com.gamersblended.junes.exception.WishlistUpdateConflictException;
import com.gamersblended.junes.mapper.WishlistProductMapper;
import com.gamersblended.junes.model.Wishlist;
import com.gamersblended.junes.model.WishlistItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisWishlistRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private WishlistProductMapper wishlistProductMapper;

    private ObjectMapper objectMapper;
    private RedisWishlistRepository repository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String USER_KEY = "user:wishlist:" + USER_ID;
    private static final String GUEST_KEY = "wishlist:" + SESSION_ID;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        repository = new RedisWishlistRepository(redisTemplate, objectMapper, wishlistProductMapper);
    }

    private static WishlistItem wishlistItem(String productID) {
        WishlistItem item = new WishlistItem();
        item.setProductID(productID);
        item.setCreatedOn(LocalDateTime.now());
        return item;
    }

    private static Wishlist wishlistFor(UUID userID) {
        return Wishlist.builder()
                .wishlistID(UUID.randomUUID())
                .userID(userID)
                .sessionID(RedisWishlistRepositoryTest.SESSION_ID)
                .version(0)
                .build();
    }

    // ---------- buildKey (exercised indirectly through getWishlist) ----------

    @Test
    void getWishlist_userWishlist_usesUserPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        repository.getWishlist(USER_ID, SESSION_ID);

        verify(valueOperations).get(USER_KEY);
    }

    @Test
    void getWishlist_guestWishlist_usesSessionPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(GUEST_KEY)).thenReturn(null);

        repository.getWishlist(null, SESSION_ID);

        verify(valueOperations).get(GUEST_KEY);
    }

    // ---------- getWishlist ----------

    @Test
    void getWishlist_keyMissing_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        Optional<Wishlist> result = repository.getWishlist(USER_ID, SESSION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getWishlist_validJson_returnsParsedWishlist() throws Exception {
        Wishlist wishlist = wishlistFor(USER_ID);
        String json = objectMapper.writeValueAsString(wishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);

        Optional<Wishlist> result = repository.getWishlist(USER_ID, SESSION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getWishlistID()).isEqualTo(wishlist.getWishlistID());
    }

    @Test
    void getWishlist_corruptJson_throwsRedisDataException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn("{not-valid-json");

        assertThatThrownBy(() -> repository.getWishlist(USER_ID, SESSION_ID))
                .isInstanceOf(RedisDataException.class);
    }

    // ---------- deleteWishlist ----------

    @Test
    void deleteWishlist_keyExisted_returnsTrue() {
        when(redisTemplate.delete(USER_KEY)).thenReturn(true);

        assertThat(repository.deleteWishlist(USER_ID, SESSION_ID)).isTrue();
    }

    @Test
    void deleteWishlist_keyDidNotExist_returnsFalse() {
        when(redisTemplate.delete(USER_KEY)).thenReturn(false);

        assertThat(repository.deleteWishlist(USER_ID, SESSION_ID)).isFalse();
    }

    // ---------- saveWishlist ----------

    @Test
    void saveWishlist_userWishlist_usesUserTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Wishlist wishlist = wishlistFor(USER_ID);

        repository.saveWishlist(wishlist);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(USER_KEY), any(String.class), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(30));
        assertThat(wishlist.getUpdatedOn()).isNotNull();
    }

    @Test
    void saveWishlist_guestWishlist_usesGuestTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Wishlist wishlist = wishlistFor(null);

        repository.saveWishlist(wishlist);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(GUEST_KEY), any(String.class), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void saveWishlist_serialisationFailure_throwsWishlistSerialisationException() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {
        });
        RedisWishlistRepository repositoryWithFailingMapper = new RedisWishlistRepository(redisTemplate, failingMapper, wishlistProductMapper);
        Wishlist wishlist = wishlistFor(USER_ID);

        assertThatThrownBy(() -> repositoryWithFailingMapper.saveWishlist(wishlist))
                .isInstanceOf(WishlistSerialisationException.class);
    }

    // ---------- updateWishlistAtomic ----------

    @Test
    void updateWishlistAtomic_scriptReturnsOne_returnsTrueAndKeepsIncrementedVersion() {
        Wishlist wishlist = wishlistFor(USER_ID);
        wishlist.setVersion(2);

        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.updateWishlistAtomic(wishlist);

        assertThat(result).isTrue();
        assertThat(wishlist.getVersion()).isEqualTo(3);
    }

    @Test
    void updateWishlistAtomic_scriptReturnsZero_returnsFalseAndRevertsVersion() {
        Wishlist wishlist = wishlistFor(USER_ID);
        wishlist.setVersion(2);

        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        boolean result = repository.updateWishlistAtomic(wishlist);

        assertThat(result).isFalse();
        assertThat(wishlist.getVersion()).isEqualTo(2);
    }

    @Test
    void updateWishlistAtomic_passesKeyOldVersionAndTtlSecondsToScript() {
        Wishlist wishlist = wishlistFor(USER_ID);
        wishlist.setVersion(4);

        ArgumentCaptor<java.util.List<String>> keysCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        when(redisTemplate.execute(any(), keysCaptor.capture(), argsCaptor.capture())).thenReturn(1L);

        repository.updateWishlistAtomic(wishlist);

        assertThat(keysCaptor.getValue()).containsExactly(USER_KEY);
        Object[] args = argsCaptor.getValue();
        assertThat(args[1]).isEqualTo("4");
        assertThat(args[2]).isEqualTo(String.valueOf(Duration.ofDays(30).getSeconds()));
    }

    // ---------- addItem ----------

    @Test
    void addItem_noExistingWishlist_createsWishlistThenAddsItem() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(wishlistProductMapper.toWishlistItemEntity(any(WishlistItemDTO.class))).thenReturn(wishlistItem("product-1"));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.addItem(USER_ID, SESSION_ID, new WishlistItemDTO("product-1", null));

        assertThat(result).isTrue();
    }

    @Test
    void addItem_productAlreadyPresent_doesNotDuplicateEntry() throws Exception {
        Wishlist existingWishlist = wishlistFor(USER_ID);
        existingWishlist.addItem(wishlistItem("product-1"));
        String json = objectMapper.writeValueAsString(existingWishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(wishlistProductMapper.toWishlistItemEntity(any(WishlistItemDTO.class))).thenReturn(wishlistItem("product-1"));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        repository.addItem(USER_ID, SESSION_ID, new WishlistItemDTO("product-1", null));

        ArgumentCaptor<Object> wishlistJsonCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(), anyList(), wishlistJsonCaptor.capture(), any(), any());
        assertThat(((String) wishlistJsonCaptor.getValue()).split("\"productID\":\"product-1\"", -1)).hasSize(2);
    }

    @Test
    void addItem_atomicUpdateFailsThenSucceeds_retriesAndReturnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(wishlistProductMapper.toWishlistItemEntity(any(WishlistItemDTO.class))).thenReturn(wishlistItem("product-1"));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(0L)
                .thenReturn(1L);

        boolean result = repository.addItem(USER_ID, SESSION_ID, new WishlistItemDTO("product-1", null));

        assertThat(result).isTrue();
        verify(redisTemplate, times(2)).execute(any(), anyList(), any(), any(), any());
    }

    @Test
    void addItem_atomicUpdateFailsAllRetries_throwsWishlistUpdateConflictException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(wishlistProductMapper.toWishlistItemEntity(any(WishlistItemDTO.class))).thenReturn(wishlistItem("product-1"));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);
        WishlistItemDTO itemDTO = new WishlistItemDTO("product-1", null);

        assertThatThrownBy(() -> repository.addItem(USER_ID, SESSION_ID, itemDTO))
                .isInstanceOf(WishlistUpdateConflictException.class);

        verify(redisTemplate, times(3)).execute(any(), anyList(), any(), any(), any());
    }

    // ---------- removeItem ----------

    @Test
    void removeItem_wishlistAbsent_returnsFalseWithoutThrowing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        boolean result = repository.removeItem(USER_ID, SESSION_ID, "product-1");

        assertThat(result).isFalse();
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any(), any());
    }

    @Test
    void removeItem_itemPresent_removesItAndReturnsTrue() throws Exception {
        Wishlist existingWishlist = wishlistFor(USER_ID);
        existingWishlist.addItem(wishlistItem("product-1"));
        String json = objectMapper.writeValueAsString(existingWishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.removeItem(USER_ID, SESSION_ID, "product-1");

        assertThat(result).isTrue();
    }

    @Test
    void removeItem_atomicUpdateFailsAllRetries_throwsWishlistUpdateConflictException() throws Exception {
        Wishlist existingWishlist = wishlistFor(USER_ID);
        existingWishlist.addItem(wishlistItem("product-1"));
        String json = objectMapper.writeValueAsString(existingWishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> repository.removeItem(USER_ID, SESSION_ID, "product-1"))
                .isInstanceOf(WishlistUpdateConflictException.class);
    }

    // ---------- addItemIfAbsent ----------

    @Test
    void addItemIfAbsent_productAlreadyPresent_doesNotAddDuplicate() {
        Wishlist wishlist = wishlistFor(USER_ID);
        wishlist.addItem(wishlistItem("product-1"));

        repository.addItemIfAbsent(wishlist, wishlistItem("product-1"));

        assertThat(wishlist.getItemList()).hasSize(1);
    }

    @Test
    void addItemIfAbsent_newProduct_appendsItem() {
        Wishlist wishlist = wishlistFor(USER_ID);
        wishlist.addItem(wishlistItem("product-1"));

        repository.addItemIfAbsent(wishlist, wishlistItem("product-2"));

        assertThat(wishlist.getItemList()).hasSize(2);
    }

    // ---------- clearWishlist ----------

    @Test
    void clearWishlist_wishlistAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        assertThat(repository.clearWishlist(USER_ID, SESSION_ID)).isFalse();
    }

    @Test
    void clearWishlist_wishlistPresent_clearsItemsAndReturnsTrue() throws Exception {
        Wishlist existingWishlist = wishlistFor(USER_ID);
        existingWishlist.addItem(wishlistItem("product-1"));
        String json = objectMapper.writeValueAsString(existingWishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.clearWishlist(USER_ID, SESSION_ID);

        assertThat(result).isTrue();
    }

    @Test
    void clearWishlist_atomicUpdateFailsAllRetries_throwsWishlistUpdateConflictException() throws Exception {
        Wishlist existingWishlist = wishlistFor(USER_ID);
        existingWishlist.addItem(wishlistItem("product-1"));
        String json = objectMapper.writeValueAsString(existingWishlist);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> repository.clearWishlist(USER_ID, SESSION_ID))
                .isInstanceOf(WishlistUpdateConflictException.class);
    }
}
