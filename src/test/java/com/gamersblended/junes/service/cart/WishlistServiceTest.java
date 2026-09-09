package com.gamersblended.junes.service.cart;

import com.gamersblended.junes.dto.ProductInWishlistDTO;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.exception.DatabaseInsertionException;
import com.gamersblended.junes.exception.MissingIdentifierException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Wishlist;
import com.gamersblended.junes.model.WishlistItem;
import com.gamersblended.junes.repository.RedisWishlistRepository;
import com.gamersblended.junes.repository.jpa.WishlistDatabaseRepository;
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
class WishlistServiceTest {

    private static final String PRODUCT_ID_1 = new ObjectId().toHexString();
    private static final String PRODUCT_ID_2 = new ObjectId().toHexString();

    @Mock
    private RedisWishlistRepository redisWishlistRepository;
    @Mock
    private WishlistDatabaseRepository wishlistDatabaseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private WishlistService self;

    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(redisWishlistRepository, wishlistDatabaseRepository, productRepository, self);
    }

    private static Product product(BigDecimal price) {
        Product product = new Product("Game", "slug", "description", price, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, 0,
                "image.png", List.of(), LocalDate.now());
        product.setId(new ObjectId(PRODUCT_ID_1));
        return product;
    }

    private static WishlistItem wishlistItem(String productID) {
        WishlistItem item = new WishlistItem();
        item.setProductID(productID);
        item.setCreatedOn(LocalDateTime.now());
        return item;
    }

    // ---- getOrCreateWishlist ----

    @Test
    void getOrCreateWishlist_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> wishlistService.getOrCreateWishlist(null, null))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void getOrCreateWishlist_returnsExistingWishlist_whenFound() {
        UUID userID = UUID.randomUUID();
        Wishlist existingWishlist = Wishlist.builder().userID(userID).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(existingWishlist));

        Wishlist result = wishlistService.getOrCreateWishlist(userID, null);

        assertThat(result).isSameAs(existingWishlist);
        verify(redisWishlistRepository, never()).createWishlist(any(), any());
    }

    @Test
    void getOrCreateWishlist_createsNewWishlist_whenNotFound() {
        UUID sessionID = UUID.randomUUID();
        Wishlist newWishlist = Wishlist.builder().sessionID(sessionID).build();
        when(redisWishlistRepository.getWishlist(null, sessionID)).thenReturn(Optional.empty());
        when(redisWishlistRepository.createWishlist(null, sessionID)).thenReturn(newWishlist);

        Wishlist result = wishlistService.getOrCreateWishlist(null, sessionID);

        assertThat(result).isSameAs(newWishlist);
    }

    // ---- getWishlistProducts ----

    @Test
    void getWishlistProducts_delegatesToGenerateWishlistPage() {
        UUID userID = UUID.randomUUID();
        WishlistItem item = wishlistItem(PRODUCT_ID_1);
        Wishlist wishlist = Wishlist.builder().userID(userID).itemList(List.of(item)).build();
        Product product = product(BigDecimal.valueOf(49.99));
        Pageable pageable = PageRequest.of(0, 10);
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(wishlist));
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of(product));

        Page<ProductInWishlistDTO> result = wishlistService.getWishlistProducts(userID, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Game");
    }

    // ---- generateWishlistPage ----

    @Test
    void generateWishlistPage_returnsEmptyPage_whenWishlistHasNoItems() {
        Wishlist wishlist = Wishlist.builder().itemList(List.of()).build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<ProductInWishlistDTO> result = wishlistService.generateWishlistPage(wishlist, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void generateWishlistPage_mapsMetadata_whenProductFound() {
        WishlistItem item = wishlistItem(PRODUCT_ID_1);
        Wishlist wishlist = Wishlist.builder().itemList(List.of(item)).build();
        Product product = product(BigDecimal.valueOf(49.99));
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of(product));

        Page<ProductInWishlistDTO> result = wishlistService.generateWishlistPage(wishlist, pageable);

        ProductInWishlistDTO dto = result.getContent().get(0);
        assertThat(dto.getName()).isEqualTo("Game");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
    }

    @Test
    void generateWishlistPage_usesUnknownProductPlaceholder_whenMetadataMissing() {
        WishlistItem item = wishlistItem(PRODUCT_ID_1);
        Wishlist wishlist = Wishlist.builder().itemList(List.of(item)).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByIdIn(List.of(new ObjectId(PRODUCT_ID_1)))).thenReturn(List.of());

        Page<ProductInWishlistDTO> result = wishlistService.generateWishlistPage(wishlist, pageable);

        ProductInWishlistDTO dto = result.getContent().get(0);
        assertThat(dto.getName()).isEqualTo("Unknown product");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- addItemToWishlist ----

    @Test
    void addItemToWishlist_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        WishlistItemDTO dto = new WishlistItemDTO(PRODUCT_ID_1, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisWishlistRepository.addItem(userID, null, dto)).thenReturn(true);

        wishlistService.addItemToWishlist(userID, null, dto);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void addItemToWishlist_doesNotPersistAsync_whenRedisAddFails() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        WishlistItemDTO dto = new WishlistItemDTO(PRODUCT_ID_1, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisWishlistRepository.addItem(userID, null, dto)).thenReturn(false);

        wishlistService.addItemToWishlist(userID, null, dto);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    @Test
    void addItemToWishlist_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        WishlistItemDTO dto = new WishlistItemDTO(PRODUCT_ID_1, null);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addItemToWishlist(userID, null, dto))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---- removeItemFromWishlist ----

    @Test
    void removeItemFromWishlist_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisWishlistRepository.removeItem(userID, null, PRODUCT_ID_1)).thenReturn(true);

        wishlistService.removeItemFromWishlist(userID, null, PRODUCT_ID_1);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void removeItemFromWishlist_doesNotPersistAsync_whenRedisRemoveFails() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisWishlistRepository.removeItem(userID, null, PRODUCT_ID_1)).thenReturn(false);

        wishlistService.removeItemFromWishlist(userID, null, PRODUCT_ID_1);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    @Test
    void removeItemFromWishlist_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.removeItemFromWishlist(userID, null, PRODUCT_ID_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---- clearWishlist ----

    @Test
    void clearWishlist_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> wishlistService.clearWishlist(null, null))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void clearWishlist_persistsAsync_whenSuccessful() {
        UUID userID = UUID.randomUUID();
        when(redisWishlistRepository.clearWishlist(userID, null)).thenReturn(true);

        wishlistService.clearWishlist(userID, null);

        verify(self).asyncPersistToDatabase(userID, null);
    }

    @Test
    void clearWishlist_doesNotPersistAsync_whenClearFails() {
        UUID userID = UUID.randomUUID();
        when(redisWishlistRepository.clearWishlist(userID, null)).thenReturn(false);

        wishlistService.clearWishlist(userID, null);

        verify(self, never()).asyncPersistToDatabase(any(), any());
    }

    // ---- validateForWishlistItems ----

    @Test
    void validateForWishlistItems_throwsMissingIdentifierException_whenBothIdentifiersNull() {
        assertThatThrownBy(() -> wishlistService.validateForWishlistItems(null, null, PRODUCT_ID_1))
                .isInstanceOf(MissingIdentifierException.class);
    }

    @Test
    void validateForWishlistItems_throwsProductNotFoundException_whenProductMissing() {
        UUID userID = UUID.randomUUID();
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.validateForWishlistItems(userID, null, PRODUCT_ID_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void validateForWishlistItems_doesNotThrow_whenProductFound() {
        UUID userID = UUID.randomUUID();
        Product product = product(BigDecimal.TEN);
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));

        assertThatCode(() -> wishlistService.validateForWishlistItems(userID, null, PRODUCT_ID_1))
                .doesNotThrowAnyException();
    }

    // ---- asyncPersistToDatabase ----

    @Test
    void asyncPersistToDatabase_doesNothing_whenUserIDNull() {
        wishlistService.asyncPersistToDatabase(null, UUID.randomUUID());

        verify(redisWishlistRepository, never()).getWishlist(any(), any());
    }

    @Test
    void asyncPersistToDatabase_persistsWishlist_whenUserIDPresent() {
        UUID userID = UUID.randomUUID();
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of()).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());

        wishlistService.asyncPersistToDatabase(userID, null);

        verify(wishlistDatabaseRepository).save(any(Wishlist.class));
    }

    @Test
    void asyncPersistToDatabase_rethrowsDatabaseInsertionException() {
        UUID userID = UUID.randomUUID();
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of()).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());
        when(wishlistDatabaseRepository.save(any())).thenThrow(new DatabaseInsertionException("boom"));

        assertThatThrownBy(() -> wishlistService.asyncPersistToDatabase(userID, null))
                .isInstanceOf(DatabaseInsertionException.class);
    }

    // ---- syncWishlistFromRedis ----

    @Test
    void syncWishlistFromRedis_doesNothing_whenRedisWishlistAbsent() {
        UUID userID = UUID.randomUUID();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.empty());

        wishlistService.syncWishlistFromRedis(userID, null);

        verify(wishlistDatabaseRepository, never()).save(any());
    }

    @Test
    void syncWishlistFromRedis_createsNewDbWishlist_whenNoneExists() {
        UUID userID = UUID.randomUUID();
        WishlistItem redisItem = wishlistItem(PRODUCT_ID_1);
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.empty());

        wishlistService.syncWishlistFromRedis(userID, null);

        ArgumentCaptor<Wishlist> captor = ArgumentCaptor.forClass(Wishlist.class);
        verify(wishlistDatabaseRepository).save(captor.capture());
        Wishlist savedWishlist = captor.getValue();
        assertThat(savedWishlist.getUserID()).isEqualTo(userID);
        assertThat(savedWishlist.getItemList()).extracting(WishlistItem::getProductID).containsExactly(PRODUCT_ID_1);
    }

    @Test
    void syncWishlistFromRedis_removesDbItems_notPresentInRedis() {
        UUID userID = UUID.randomUUID();
        WishlistItem staleDbItem = wishlistItem(PRODUCT_ID_2);
        Wishlist dbWishlist = Wishlist.builder().userID(userID).itemList(new ArrayList<>(List.of(staleDbItem))).build();
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of()).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbWishlist));

        wishlistService.syncWishlistFromRedis(userID, null);

        ArgumentCaptor<Wishlist> captor = ArgumentCaptor.forClass(Wishlist.class);
        verify(wishlistDatabaseRepository).save(captor.capture());
        assertThat(captor.getValue().getItemList()).isEmpty();
    }

    @Test
    void syncWishlistFromRedis_addsItem_notPresentInDb() {
        UUID userID = UUID.randomUUID();
        Wishlist dbWishlist = Wishlist.builder().userID(userID).itemList(new ArrayList<>()).build();
        WishlistItem redisItem = wishlistItem(PRODUCT_ID_1);
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbWishlist));

        wishlistService.syncWishlistFromRedis(userID, null);

        assertThat(dbWishlist.getItemList()).extracting(WishlistItem::getProductID).containsExactly(PRODUCT_ID_1);
    }

    @Test
    void syncWishlistFromRedis_doesNotDuplicate_itemAlreadyPresentInDb() {
        UUID userID = UUID.randomUUID();
        WishlistItem existingDbItem = wishlistItem(PRODUCT_ID_1);
        Wishlist dbWishlist = Wishlist.builder().userID(userID).itemList(new ArrayList<>(List.of(existingDbItem))).build();
        WishlistItem redisItem = wishlistItem(PRODUCT_ID_1);
        Wishlist redisWishlist = Wishlist.builder().userID(userID).itemList(List.of(redisItem)).build();
        when(redisWishlistRepository.getWishlist(userID, null)).thenReturn(Optional.of(redisWishlist));
        when(wishlistDatabaseRepository.findByUserID(userID)).thenReturn(Optional.of(dbWishlist));

        wishlistService.syncWishlistFromRedis(userID, null);

        assertThat(dbWishlist.getItemList()).hasSize(1);
    }

    // ---- cleanupInactiveWishlists ----

    @Test
    void cleanupInactiveWishlists_deletesWishlistsOlderThanCutoff() {
        when(wishlistDatabaseRepository.deleteInactiveWishlists(any())).thenReturn(2);

        wishlistService.cleanupInactiveWishlists();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(wishlistDatabaseRepository).deleteInactiveWishlists(captor.capture());
        LocalDateTime expectedCutoff = LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMonths(12);
        assertThat(captor.getValue()).isCloseTo(expectedCutoff, within(1, ChronoUnit.MINUTES));
    }

    // ---- mergeGuestWishlistIntoUserWishlist ----

    @Test
    void mergeGuestWishlistIntoUserWishlist_doesNothing_whenSessionIDNull() {
        wishlistService.mergeGuestWishlistIntoUserWishlist(UUID.randomUUID(), null);

        verify(redisWishlistRepository, never()).getWishlist(any(), any());
    }

    @Test
    void mergeGuestWishlistIntoUserWishlist_doesNothing_whenGuestWishlistNotFound() {
        UUID sessionID = UUID.randomUUID();
        when(redisWishlistRepository.getWishlist(null, sessionID)).thenReturn(Optional.empty());

        wishlistService.mergeGuestWishlistIntoUserWishlist(UUID.randomUUID(), sessionID);

        verify(redisWishlistRepository, never()).deleteWishlist(any(), any());
    }

    @Test
    void mergeGuestWishlistIntoUserWishlist_doesNothing_whenGuestWishlistEmpty() {
        UUID sessionID = UUID.randomUUID();
        Wishlist emptyWishlist = Wishlist.builder().sessionID(sessionID).build();
        when(redisWishlistRepository.getWishlist(null, sessionID)).thenReturn(Optional.of(emptyWishlist));

        wishlistService.mergeGuestWishlistIntoUserWishlist(UUID.randomUUID(), sessionID);

        verify(redisWishlistRepository, never()).deleteWishlist(any(), any());
    }

    @Test
    void mergeGuestWishlistIntoUserWishlist_deletesGuestWishlist_andMergesItemsIntoUserWishlist() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        WishlistItem guestItem = wishlistItem(PRODUCT_ID_1);
        Wishlist guestWishlist = Wishlist.builder().sessionID(sessionID).itemList(List.of(guestItem)).build();
        Product product = product(BigDecimal.valueOf(19.99));
        when(redisWishlistRepository.getWishlist(null, sessionID)).thenReturn(Optional.of(guestWishlist));
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.of(product));
        when(redisWishlistRepository.addItem(eq(userID), eq(null), any(WishlistItemDTO.class))).thenReturn(true);

        wishlistService.mergeGuestWishlistIntoUserWishlist(userID, sessionID);

        verify(redisWishlistRepository).deleteWishlist(null, sessionID);
        ArgumentCaptor<WishlistItemDTO> captor = ArgumentCaptor.forClass(WishlistItemDTO.class);
        verify(redisWishlistRepository).addItem(eq(userID), eq(null), captor.capture());
        assertThat(captor.getValue().getProductID()).isEqualTo(PRODUCT_ID_1);
    }

    @Test
    void mergeGuestWishlistIntoUserWishlist_skipsItem_whenProductNoLongerExists() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        WishlistItem guestItem = wishlistItem(PRODUCT_ID_1);
        Wishlist guestWishlist = Wishlist.builder().sessionID(sessionID).itemList(List.of(guestItem)).build();
        when(redisWishlistRepository.getWishlist(null, sessionID)).thenReturn(Optional.of(guestWishlist));
        when(productRepository.findById(new ObjectId(PRODUCT_ID_1))).thenReturn(Optional.empty());

        wishlistService.mergeGuestWishlistIntoUserWishlist(userID, sessionID);

        verify(redisWishlistRepository).deleteWishlist(null, sessionID);
        verify(redisWishlistRepository, never()).addItem(any(), any(), any());
    }
}
