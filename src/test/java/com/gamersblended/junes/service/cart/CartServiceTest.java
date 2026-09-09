package com.gamersblended.junes.service.cart;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.dto.ProductInCartDTO;
import com.gamersblended.junes.exception.DatabaseInsertionException;
import com.gamersblended.junes.exception.InvalidQuantityException;
import com.gamersblended.junes.exception.MissingIdentifierException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.RedisCartRepository;
import com.gamersblended.junes.repository.jpa.CartDatabaseRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String PRODUCT_ID_1 = new ObjectId().toHexString();
    private static final String PRODUCT_ID_2 = new ObjectId().toHexString();

    @Mock
    private RedisCartRepository redisCartRepository;
    @Mock
    private CartDatabaseRepository cartDatabaseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartService self;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(redisCartRepository, cartDatabaseRepository, productRepository, self);
    }

    private static Product product(BigDecimal price) {
        Product product = new Product("Game", "slug", "description", price, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, 0,
                "image.png", List.of(), LocalDate.now());
        product.setId(new ObjectId(PRODUCT_ID_1));
        return product;
    }

    private static CartItem cartItem(String productID, BigDecimal price, int quantity) {
        CartItem item = new CartItem();
        item.setProductID(productID);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setCreatedOn(LocalDateTime.now());
        return item;
    }

    // ---- getOrCreateCart ----

    @Test
    void getOrCreateCart_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> cartService.getOrCreateCart(null, null))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void getOrCreateCart_returnsExistingCart_whenFound() {
        UUID userID = UUID.randomUUID();
        Cart existingCart = Cart.builder().userID(userID).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(existingCart));

        Cart result = cartService.getOrCreateCart(userID, null);

        assertThat(result).isSameAs(existingCart);
        verify(redisCartRepository, never()).createCart(any(), any());
    }

    @Test
    void getOrCreateCart_createsNewCart_whenNotFound() {
        UUID sessionID = UUID.randomUUID();
        Cart newCart = Cart.builder().sessionID(sessionID).build();
        when(redisCartRepository.getCart(null, sessionID)).thenReturn(Optional.empty());
        when(redisCartRepository.createCart(null, sessionID)).thenReturn(newCart);

        Cart result = cartService.getOrCreateCart(null, sessionID);

        assertThat(result).isSameAs(newCart);
    }

    // ---- addItemToCart ----

    @Test
    void addItemToCart_setsPriceFromCatalog_andPersistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.valueOf(59.99));
        CartItemDTO dto = new CartItemDTO(PRODUCT_ID_1, BigDecimal.valueOf(1.00), 2, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.addItem(userID, null, dto)).thenReturn(true);

        cartService.addItemToCart(userID, null, dto);

        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(59.99));
        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void addItemToCart_doesNotPersistAsync_whenRedisAddFails() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.valueOf(59.99));
        CartItemDTO dto = new CartItemDTO(PRODUCT_ID_1, null, 2, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.addItem(userID, null, dto)).thenReturn(false);

        cartService.addItemToCart(userID, null, dto);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    @Test
    void addItemToCart_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        CartItemDTO dto = new CartItemDTO(PRODUCT_ID_1, null, 1, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItemToCart(userID, null, dto))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void addItemToCart_throwsInvalidQuantityException_whenQuantityInvalid() {
        UUID userID = UUID.randomUUID();
        CartItemDTO dto = new CartItemDTO(PRODUCT_ID_1, null, 0, null);

        assertThatThrownBy(() -> cartService.addItemToCart(userID, null, dto))
                .isInstanceOf(InvalidQuantityException.class);
    }

    // ---- removeItemFromCart ----

    @Test
    void removeItemFromCart_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.removeItem(userID, null, PRODUCT_ID_1)).thenReturn(true);

        cartService.removeItemFromCart(userID, null, PRODUCT_ID_1);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void removeItemFromCart_doesNotPersistAsync_whenRedisRemoveFails() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.removeItem(userID, null, PRODUCT_ID_1)).thenReturn(false);

        cartService.removeItemFromCart(userID, null, PRODUCT_ID_1);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    @Test
    void removeItemFromCart_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItemFromCart(userID, null, PRODUCT_ID_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---- updateItemQuantity ----

    @Test
    void updateItemQuantity_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.updateItemQuantity(userID, null, PRODUCT_ID_1, 3)).thenReturn(true);

        cartService.updateItemQuantity(userID, null, PRODUCT_ID_1, 3);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void updateItemQuantity_throwsInvalidQuantityException_whenQuantityInvalid() {
        UUID userID = UUID.randomUUID();

        assertThatThrownBy(() -> cartService.updateItemQuantity(userID, null, PRODUCT_ID_1, 0))
                .isInstanceOf(InvalidQuantityException.class);
    }

    // ---- clearCart ----

    @Test
    void clearCart_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> cartService.clearCart(null, null))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void clearCart_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        when(redisCartRepository.clearCart(userID, null)).thenReturn(true);

        cartService.clearCart(userID, null);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void clearCart_doesNotPersistAsync_whenClearFails() {
        UUID userID = UUID.randomUUID();
        when(redisCartRepository.clearCart(userID, null)).thenReturn(false);

        cartService.clearCart(userID, null);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    // ---- deleteCart ----

    @Test
    void deleteCart_delegatesToRedisRepository() {
        UUID userID = UUID.randomUUID();
        when(redisCartRepository.deleteCart(userID, null)).thenReturn(true);

        boolean result = cartService.deleteCart(userID, null);

        assertThat(result).isTrue();
    }

    // ---- mergeGuestCartIntoUserCart ----

    @Test
    void mergeGuestCartIntoUserCart_doesNothing_whenSessionIDNull() {
        cartService.mergeGuestCartIntoUserCart(UUID.randomUUID(), null);

        verify(redisCartRepository, never()).getCart(any(), any());
    }

    @Test
    void mergeGuestCartIntoUserCart_doesNothing_whenGuestCartNotFound() {
        UUID sessionID = UUID.randomUUID();
        when(redisCartRepository.getCart(null, sessionID)).thenReturn(Optional.empty());

        cartService.mergeGuestCartIntoUserCart(UUID.randomUUID(), sessionID);

        verify(redisCartRepository, never()).deleteCart(any(), any());
    }

    @Test
    void mergeGuestCartIntoUserCart_doesNothing_whenGuestCartEmpty() {
        UUID sessionID = UUID.randomUUID();
        Cart emptyCart = Cart.builder().sessionID(sessionID).build();
        when(redisCartRepository.getCart(null, sessionID)).thenReturn(Optional.of(emptyCart));

        cartService.mergeGuestCartIntoUserCart(UUID.randomUUID(), sessionID);

        verify(redisCartRepository, never()).deleteCart(any(), any());
    }

    @Test
    void mergeGuestCartIntoUserCart_deletesGuestCart_andMergesItemsIntoUserCart() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        CartItem guestItem = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 2);
        Cart guestCart = Cart.builder().sessionID(sessionID).itemList(List.of(guestItem)).build();
        Product product = product(BigDecimal.valueOf(19.99));
        when(redisCartRepository.getCart(null, sessionID)).thenReturn(Optional.of(guestCart));
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisCartRepository.addItem(eq(userID), eq(null), any(CartItemDTO.class))).thenReturn(true);

        cartService.mergeGuestCartIntoUserCart(userID, sessionID);

        verify(redisCartRepository).deleteCart(null, sessionID);
        ArgumentCaptor<CartItemDTO> captor = ArgumentCaptor.forClass(CartItemDTO.class);
        verify(redisCartRepository).addItem(eq(userID), eq(null), captor.capture());
        assertThat(captor.getValue().getProductID()).isEqualTo(PRODUCT_ID_1);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void mergeGuestCartIntoUserCart_skipsItem_whenProductNoLongerExists() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        CartItem guestItem = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 1);
        Cart guestCart = Cart.builder().sessionID(sessionID).itemList(List.of(guestItem)).build();
        when(redisCartRepository.getCart(null, sessionID)).thenReturn(Optional.of(guestCart));
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        cartService.mergeGuestCartIntoUserCart(userID, sessionID);

        verify(redisCartRepository).deleteCart(null, sessionID);
        verify(redisCartRepository, never()).addItem(any(), any(), any());
    }

    // ---- asyncPersistToDatabase ----

    @Test
    void asyncPersistToDatabase_doesNothing_whenUserIDNull() {
        cartService.asyncPersistToDatabase(null, UUID.randomUUID());

        verify(redisCartRepository, never()).getCart(any(), any());
    }

    @Test
    void asyncPersistToDatabase_persistsCart_whenUserIDPresent() {
        UUID userID = UUID.randomUUID();
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of()).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());

        cartService.asyncPersistToDatabase(userID, null);

        verify(cartDatabaseRepository).save(any(Cart.class));
    }

    @Test
    void asyncPersistToDatabase_rethrowsDatabaseInsertionException() {
        UUID userID = UUID.randomUUID();
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of()).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());
        when(cartDatabaseRepository.save(any())).thenThrow(new DatabaseInsertionException("boom"));

        assertThatThrownBy(() -> cartService.asyncPersistToDatabase(userID, null))
                .isInstanceOf(DatabaseInsertionException.class);
    }

    // ---- syncCartFromRedis ----

    @Test
    void syncCartFromRedis_doesNothing_whenRedisCartAbsent() {
        UUID userID = UUID.randomUUID();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.empty());

        cartService.syncCartFromRedis(userID, null);

        verify(cartDatabaseRepository, never()).save(any());
    }

    @Test
    void syncCartFromRedis_createsNewDbCart_whenNoneExists() {
        UUID userID = UUID.randomUUID();
        CartItem redisItem = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 2);
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());

        cartService.syncCartFromRedis(userID, null);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartDatabaseRepository).save(captor.capture());
        Cart savedCart = captor.getValue();
        assertThat(savedCart.getUserID()).isEqualTo(userID);
        assertThat(savedCart.getItemList()).extracting(CartItem::getProductID).containsExactly(PRODUCT_ID_1);
    }

    @Test
    void syncCartFromRedis_removesDbItems_notPresentInRedis() {
        UUID userID = UUID.randomUUID();
        CartItem staleDbItem = cartItem(PRODUCT_ID_2, BigDecimal.ONE, 1);
        Cart dbCart = Cart.builder().userID(userID).itemList(new ArrayList<>(List.of(staleDbItem))).build();
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of()).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbCart));

        cartService.syncCartFromRedis(userID, null);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartDatabaseRepository).save(captor.capture());
        assertThat(captor.getValue().getItemList()).isEmpty();
    }

    @Test
    void syncCartFromRedis_updatesExistingItem_quantityAndPrice() {
        UUID userID = UUID.randomUUID();
        CartItem existingDbItem = cartItem(PRODUCT_ID_1, BigDecimal.ONE, 1);
        Cart dbCart = Cart.builder().userID(userID).itemList(new ArrayList<>(List.of(existingDbItem))).build();
        CartItem redisItem = cartItem(PRODUCT_ID_1, BigDecimal.valueOf(25), 5);
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbCart));

        cartService.syncCartFromRedis(userID, null);

        assertThat(existingDbItem.getQuantity()).isEqualTo(5);
        assertThat(existingDbItem.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(25));
        verify(cartDatabaseRepository).save(dbCart);
    }

    @Test
    void syncCartFromRedis_addsNewItem_notPresentInDb() {
        UUID userID = UUID.randomUUID();
        Cart dbCart = Cart.builder().userID(userID).itemList(new ArrayList<>()).build();
        CartItem redisItem = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 3);
        Cart redisCart = Cart.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(redisCart));
        when(cartDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbCart));

        cartService.syncCartFromRedis(userID, null);

        assertThat(dbCart.getItemList()).extracting(CartItem::getProductID).containsExactly(PRODUCT_ID_1);
    }

    // ---- cleanupInactiveCarts ----

    @Test
    void cleanupInactiveCarts_deletesCartsOlderThanCutoff() {
        when(cartDatabaseRepository.deleteInactiveCarts(any())).thenReturn(4);

        cartService.cleanupInactiveCarts();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cartDatabaseRepository).deleteInactiveCarts(captor.capture());
        LocalDateTime expectedCutoff = LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMonths(2);
        assertThat(captor.getValue()).isCloseTo(expectedCutoff, within(1, ChronoUnit.MINUTES));
    }

    // ---- getCartProducts ----

    @Test
    void getCartProducts_delegatesToGenerateCartPage() {
        UUID userID = UUID.randomUUID();
        CartItem item = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 1);
        Cart cart = Cart.builder().userID(userID).itemList(List.of(item)).build();
        Product product = product(BigDecimal.valueOf(49.99));
        Pageable pageable = PageRequest.of(0, 10);
        when(redisCartRepository.getCart(userID, null)).thenReturn(Optional.of(cart));
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of(product));

        Page<ProductInCartDTO> result = cartService.getCartProducts(userID, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Game");
    }

    // ---- generateCartPage ----

    @Test
    void generateCartPage_returnsEmptyPage_whenCartHasNoItems() {
        Cart cart = Cart.builder().itemList(List.of()).build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<ProductInCartDTO> result = cartService.generateCartPage(cart, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void generateCartPage_mapsMetadata_whenProductFound() {
        CartItem item = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 3);
        Cart cart = Cart.builder().itemList(List.of(item)).build();
        Product product = product(BigDecimal.valueOf(49.99));
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of(product));

        Page<ProductInCartDTO> result = cartService.generateCartPage(cart, pageable);

        ProductInCartDTO dto = result.getContent().get(0);
        assertThat(dto.getName()).isEqualTo("Game");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
        assertThat(dto.getQuantity()).isEqualTo(3);
    }

    @Test
    void generateCartPage_usesUnknownProductPlaceholder_whenMetadataMissing() {
        CartItem item = cartItem(PRODUCT_ID_1, BigDecimal.TEN, 1);
        Cart cart = Cart.builder().itemList(List.of(item)).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of());

        Page<ProductInCartDTO> result = cartService.generateCartPage(cart, pageable);

        ProductInCartDTO dto = result.getContent().get(0);
        assertThat(dto.getName()).isEqualTo("Unknown product");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- validateQuantity ----

    @Test
    void validateQuantity_returnsFalse_forZeroOrNegative() {
        assertThat(cartService.validateQuantity(0)).isFalse();
        assertThat(cartService.validateQuantity(-1)).isFalse();
    }

    @Test
    void validateQuantity_returnsTrue_forPositive() {
        assertThat(cartService.validateQuantity(1)).isTrue();
    }

    // ---- validateForCartItems (4-arg) ----

    @Test
    void validateForCartItems_returnsProduct_whenValid() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));

        Product result = cartService.validateForCartItems(userID, null, 1, PRODUCT_ID_1);

        assertThat(result).isSameAs(product);
    }

    @Test
    void validateForCartItems_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> cartService.validateForCartItems(null, null, 1, PRODUCT_ID_1))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void validateForCartItems_throwsInvalidQuantityException_whenQuantityInvalid() {
        UUID userID = UUID.randomUUID();

        assertThatThrownBy(() -> cartService.validateForCartItems(userID, null, 0, PRODUCT_ID_1))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void validateForCartItems_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.validateForCartItems(userID, null, 1, PRODUCT_ID_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---- validateForCartItems (3-arg) ----

    @Test
    void validateForCartItemsThreeArg_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> cartService.validateForCartItems(null, null, PRODUCT_ID_1))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void validateForCartItemsThreeArg_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.validateForCartItems(userID, null, PRODUCT_ID_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void validateForCartItemsThreeArg_doesNotThrow_whenProductFound() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));

        assertThatCode(() -> cartService.validateForCartItems(userID, null, PRODUCT_ID_1)).doesNotThrowAnyException();
    }
}
