package com.gamersblended.junes.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.exception.CartSerialisationException;
import com.gamersblended.junes.exception.CartUpdateConflictException;
import com.gamersblended.junes.exception.RedisDataException;
import com.gamersblended.junes.mapper.CartProductMapper;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCartRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private CartProductMapper cartProductMapper;

    private ObjectMapper objectMapper;
    private RedisCartRepository repository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String USER_KEY = "user:cart:" + USER_ID;
    private static final String GUEST_KEY = "cart:" + SESSION_ID;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        repository = new RedisCartRepository(redisTemplate, objectMapper, cartProductMapper);
    }

    private static CartItem cartItem(String productID, int quantity) {
        CartItem item = new CartItem();
        item.setProductID(productID);
        item.setPrice(new BigDecimal("19.99"));
        item.setQuantity(quantity);
        return item;
    }

    private static Cart cartFor(UUID userID) {
        return Cart.builder()
                .cartID(UUID.randomUUID())
                .userID(userID)
                .sessionID(RedisCartRepositoryTest.SESSION_ID)
                .version(0)
                .build();
    }

    // ---------- buildKey (exercised indirectly through getCart) ----------

    @Test
    void getCart_userCart_usesUserPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        repository.getCart(USER_ID, SESSION_ID);

        verify(valueOperations).get(USER_KEY);
    }

    @Test
    void getCart_guestCart_usesSessionPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(GUEST_KEY)).thenReturn(null);

        repository.getCart(null, SESSION_ID);

        verify(valueOperations).get(GUEST_KEY);
    }

    // ---------- getCart ----------

    @Test
    void getCart_keyMissing_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        Optional<Cart> result = repository.getCart(USER_ID, SESSION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getCart_validJson_returnsParsedCart() throws Exception {
        Cart cart = cartFor(USER_ID);
        String json = objectMapper.writeValueAsString(cart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);

        Optional<Cart> result = repository.getCart(USER_ID, SESSION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getCartID()).isEqualTo(cart.getCartID());
    }

    @Test
    void getCart_corruptJson_throwsRedisDataException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn("{not-valid-json");

        assertThatThrownBy(() -> repository.getCart(USER_ID, SESSION_ID))
                .isInstanceOf(RedisDataException.class);
    }

    // ---------- deleteCart ----------

    @Test
    void deleteCart_keyExisted_returnsTrue() {
        when(redisTemplate.delete(USER_KEY)).thenReturn(true);

        assertThat(repository.deleteCart(USER_ID, SESSION_ID)).isTrue();
    }

    @Test
    void deleteCart_keyDidNotExist_returnsFalse() {
        when(redisTemplate.delete(USER_KEY)).thenReturn(false);

        assertThat(repository.deleteCart(USER_ID, SESSION_ID)).isFalse();
    }

    // ---------- saveCart ----------

    @Test
    void saveCart_userCart_usesUserTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Cart cart = cartFor(USER_ID);

        repository.saveCart(cart);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(USER_KEY), any(String.class), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(30));
        assertThat(cart.getUpdatedOn()).isNotNull();
    }

    @Test
    void saveCart_guestCart_usesGuestTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Cart cart = cartFor(null);

        repository.saveCart(cart);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(GUEST_KEY), any(String.class), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void saveCart_serialisationFailure_throwsCartSerialisationException() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {
        });
        RedisCartRepository repositoryWithFailingMapper = new RedisCartRepository(redisTemplate, failingMapper, cartProductMapper);
        Cart cart = cartFor(USER_ID);

        assertThatThrownBy(() -> repositoryWithFailingMapper.saveCart(cart))
                .isInstanceOf(CartSerialisationException.class);
    }

    // ---------- updateCartAtomic ----------

    @Test
    void updateCartAtomic_scriptReturnsOne_returnsTrueAndKeepsIncrementedVersion() {
        Cart cart = cartFor(USER_ID);
        cart.setVersion(2);

        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.updateCartAtomic(cart);

        assertThat(result).isTrue();
        assertThat(cart.getVersion()).isEqualTo(3);
    }

    @Test
    void updateCartAtomic_scriptReturnsZero_returnsFalseAndRevertsVersion() {
        Cart cart = cartFor(USER_ID);
        cart.setVersion(2);

        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        boolean result = repository.updateCartAtomic(cart);

        assertThat(result).isFalse();
        assertThat(cart.getVersion()).isEqualTo(2);
    }

    @Test
    void updateCartAtomic_passesKeyOldVersionAndTtlSecondsToScript() {
        Cart cart = cartFor(USER_ID);
        cart.setVersion(4);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        when(redisTemplate.execute(any(), keysCaptor.capture(), argsCaptor.capture())).thenReturn(1L);

        repository.updateCartAtomic(cart);

        assertThat(keysCaptor.getValue()).containsExactly(USER_KEY);
        Object[] args = argsCaptor.getValue();
        assertThat(args[1]).isEqualTo("4");
        assertThat(args[2]).isEqualTo(String.valueOf(Duration.ofDays(30).getSeconds()));
    }

    // ---------- addItem ----------

    @Test
    void addItem_noExistingCart_createsCartThenAddsItem() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(cartProductMapper.toCartItemEntity(any(CartItemDTO.class))).thenReturn(cartItem("product-1", 1));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.addItem(USER_ID, SESSION_ID, new CartItemDTO("product-1", new BigDecimal("19.99"), 1, null));

        assertThat(result).isTrue();
    }

    @Test
    void addItem_existingProduct_incrementsQuantityInsteadOfDuplicating() {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 2));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cartProductMapper.toCartItemEntity(any(CartItemDTO.class))).thenReturn(cartItem("product-1", 3));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        try {
            String json = objectMapper.writeValueAsString(existingCart);
            when(valueOperations.get(USER_KEY)).thenReturn(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        repository.addItem(USER_ID, SESSION_ID, new CartItemDTO("product-1", new BigDecimal("19.99"), 3, null));

        ArgumentCaptor<Object> cartJsonCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(), anyList(), cartJsonCaptor.capture(), any(), any());
        assertThat((String) cartJsonCaptor.getValue()).contains("\"quantity\":5");
    }

    @Test
    void addItem_atomicUpdateFailsThenSucceeds_retriesAndReturnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(cartProductMapper.toCartItemEntity(any(CartItemDTO.class))).thenReturn(cartItem("product-1", 1));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(0L)
                .thenReturn(1L);

        boolean result = repository.addItem(USER_ID, SESSION_ID, new CartItemDTO("product-1", new BigDecimal("19.99"), 1, null));

        assertThat(result).isTrue();
        verify(redisTemplate, times(2)).execute(any(), anyList(), any(), any(), any());
    }

    @Test
    void addItem_atomicUpdateFailsAllRetries_throwsCartUpdateConflictException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);
        when(cartProductMapper.toCartItemEntity(any(CartItemDTO.class))).thenReturn(cartItem("product-1", 1));
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);
        CartItemDTO itemDTO = new CartItemDTO("product-1", new BigDecimal("19.99"), 1, null);

        assertThatThrownBy(() -> repository.addItem(USER_ID, SESSION_ID, itemDTO))
                .isInstanceOf(CartUpdateConflictException.class);

        verify(redisTemplate, times(3)).execute(any(), anyList(), any(), any(), any());
    }

    // ---------- removeItem ----------

    @Test
    void removeItem_cartAbsent_returnsFalseWithoutThrowing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        boolean result = repository.removeItem(USER_ID, SESSION_ID, "product-1");

        assertThat(result).isFalse();
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any(), any());
    }

    @Test
    void removeItem_itemPresent_removesItAndReturnsTrue() throws Exception {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 1));
        String json = objectMapper.writeValueAsString(existingCart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.removeItem(USER_ID, SESSION_ID, "product-1");

        assertThat(result).isTrue();
    }

    @Test
    void removeItem_atomicUpdateFailsAllRetries_throwsCartUpdateConflictException() throws Exception {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 1));
        String json = objectMapper.writeValueAsString(existingCart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> repository.removeItem(USER_ID, SESSION_ID, "product-1"))
                .isInstanceOf(CartUpdateConflictException.class);
    }

    // ---------- updateOrAddItem ----------

    @Test
    void updateOrAddItem_matchingProduct_addsQuantitiesTogether() {
        Cart cart = cartFor(USER_ID);
        cart.addItem(cartItem("product-1", 2));

        repository.updateOrAddItem(cart, cartItem("product-1", 3));

        assertThat(cart.getItemList()).hasSize(1);
        assertThat(cart.getItemList().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void updateOrAddItem_newProduct_appendsItem() {
        Cart cart = cartFor(USER_ID);
        cart.addItem(cartItem("product-1", 2));

        repository.updateOrAddItem(cart, cartItem("product-2", 1));

        assertThat(cart.getItemList()).hasSize(2);
    }

    // ---------- updateItemQuantity ----------

    @Test
    void updateItemQuantity_cartAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        boolean result = repository.updateItemQuantity(USER_ID, SESSION_ID, "product-1", 5);

        assertThat(result).isFalse();
    }

    @Test
    void updateItemQuantity_itemFound_setsQuantityDirectlyNotAdditively() throws Exception {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 2));
        String json = objectMapper.writeValueAsString(existingCart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        ArgumentCaptor<Object> cartJsonCaptor = ArgumentCaptor.forClass(Object.class);

        boolean result = repository.updateItemQuantity(USER_ID, SESSION_ID, "product-1", 9);

        assertThat(result).isTrue();
        verify(redisTemplate).execute(any(), anyList(), cartJsonCaptor.capture(), any(), any());
        assertThat((String) cartJsonCaptor.getValue()).contains("\"quantity\":9");
    }

    // ---------- clearCart ----------

    @Test
    void clearCart_cartAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        assertThat(repository.clearCart(USER_ID, SESSION_ID)).isFalse();
    }

    @Test
    void clearCart_cartPresent_clearsItemsAndReturnsTrue() throws Exception {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 2));
        String json = objectMapper.writeValueAsString(existingCart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);

        boolean result = repository.clearCart(USER_ID, SESSION_ID);

        assertThat(result).isTrue();
    }

    @Test
    void clearCart_atomicUpdateFailsAllRetries_throwsCartUpdateConflictException() throws Exception {
        Cart existingCart = cartFor(USER_ID);
        existingCart.addItem(cartItem("product-1", 2));
        String json = objectMapper.writeValueAsString(existingCart);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(json);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> repository.clearCart(USER_ID, SESSION_ID))
                .isInstanceOf(CartUpdateConflictException.class);
    }
}
