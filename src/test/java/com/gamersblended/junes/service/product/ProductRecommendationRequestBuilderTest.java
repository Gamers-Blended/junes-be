package com.gamersblended.junes.service.product;

import com.gamersblended.junes.constant.SignalTypeEnums;
import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.service.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationRequestBuilderTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CartService cartService;
    @Mock
    private OrderHistoryCacheService orderHistoryCacheService;

    private ProductRecommendationRequestBuilder productRecommendationRequestBuilder;

    @BeforeEach
    void setUp() {
        productRecommendationRequestBuilder = new ProductRecommendationRequestBuilder(transactionRepository, cartService, orderHistoryCacheService);
    }

    private static RecommendedProductRequestDTO.HistoryItem historyItem(String productID, LocalDateTime viewAt) {
        RecommendedProductRequestDTO.HistoryItem item = new RecommendedProductRequestDTO.HistoryItem();
        item.setProductID(productID);
        item.setViewAt(viewAt);
        return item;
    }

    private static RecommendedProductRequestDTO requestDTO(RecommendedProductRequestDTO.HistoryItem... items) {
        RecommendedProductRequestDTO dto = new RecommendedProductRequestDTO();
        dto.setHistoryCache(new ArrayList<>(List.of(items)));
        return dto;
    }

    private static CartItem cartItem(String productID, LocalDateTime createdOn) {
        CartItem item = new CartItem();
        item.setProductID(productID);
        item.setCreatedOn(createdOn);
        return item;
    }

    private static Cart cartWithItems(List<CartItem> items) {
        Cart cart = new Cart();
        cart.setItemList(items);
        return cart;
    }

    private void stubEmptyCart(UUID userID, UUID sessionID) {
        when(cartService.getOrCreateCart(userID, sessionID)).thenReturn(cartWithItems(new ArrayList<>()));
    }

    // ---- getRecommendationInputDTOList: browse signals ----

    @Test
    void getRecommendationInputDTOList_includesBrowseSignals_fromHistoryCache() {
        UUID sessionID = UUID.randomUUID();
        LocalDateTime viewAt = LocalDateTime.now();
        RecommendedProductRequestDTO requestDTO = requestDTO(historyItem("p1", viewAt));
        stubEmptyCart(null, sessionID);

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, null, sessionID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductID()).isEqualTo("p1");
        assertThat(result.get(0).getType()).isEqualTo(SignalTypeEnums.BROWSE.getName());
        assertThat(result.get(0).getTimestamp()).isEqualTo(viewAt);
    }

    // ---- getRecommendationInputDTOList: purchase history ----

    @Test
    void getRecommendationInputDTOList_skipsPurchaseHistory_whenUserIDIsNull() {
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        stubEmptyCart(null, sessionID);

        productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, null, sessionID);

        verifyNoInteractions(orderHistoryCacheService, transactionRepository);
    }

    @Test
    void getRecommendationInputDTOList_usesOrderHistoryCache_whenPresent() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        ProductSignalDTO cachedSignal = new ProductSignalDTO("p2", SignalTypeEnums.PURCHASE.getName(), LocalDateTime.now());
        when(orderHistoryCacheService.get(userID)).thenReturn(Optional.of(List.of(cachedSignal)));
        stubEmptyCart(userID, sessionID);

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, userID, sessionID);

        assertThat(result).containsExactly(cachedSignal);
        verifyNoInteractions(transactionRepository);
        verify(orderHistoryCacheService, never()).put(any(), any());
    }

    @Test
    void getRecommendationInputDTOList_fetchesFromDBAndCaches_whenCacheMiss() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        when(orderHistoryCacheService.get(userID)).thenReturn(Optional.empty());
        LocalDateTime createdOn = LocalDateTime.now().minusDays(1);
        Object[] row = new Object[]{"p3", Timestamp.valueOf(createdOn)};
        when(transactionRepository.findRecentItemsByUserID(userID, PageRequest.of(0, 30)))
                .thenReturn(Collections.singletonList(row));
        stubEmptyCart(userID, sessionID);

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, userID, sessionID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductID()).isEqualTo("p3");
        assertThat(result.get(0).getType()).isEqualTo(SignalTypeEnums.PURCHASE.getName());

        ArgumentCaptor<List<ProductSignalDTO>> putCaptor = ArgumentCaptor.captor();
        verify(orderHistoryCacheService).put(eq(userID), putCaptor.capture());
        assertThat(putCaptor.getValue()).hasSize(1);
    }

    @Test
    void getRecommendationInputDTOList_doesNotCache_whenDBReturnsEmpty() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        when(orderHistoryCacheService.get(userID)).thenReturn(Optional.empty());
        when(transactionRepository.findRecentItemsByUserID(eq(userID), any())).thenReturn(List.of());
        stubEmptyCart(userID, sessionID);

        productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, userID, sessionID);

        verify(orderHistoryCacheService, never()).put(any(), any());
    }

    // ---- getRecommendationInputDTOList: cart items ----

    @Test
    void getRecommendationInputDTOList_includesCartItems_whenUnderLimit() {
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        CartItem item = cartItem("p4", LocalDateTime.now());
        when(cartService.getOrCreateCart(null, sessionID)).thenReturn(cartWithItems(new ArrayList<>(List.of(item))));

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, null, sessionID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductID()).isEqualTo("p4");
        assertThat(result.get(0).getType()).isEqualTo(SignalTypeEnums.CART_ADD.getName());
    }

    @Test
    void getRecommendationInputDTOList_trimsCartItems_toMostRecent30_whenExceedingLimit() {
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = requestDTO();
        LocalDateTime base = LocalDateTime.now();
        List<CartItem> items = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            items.add(cartItem("p" + i, base.plusMinutes(i)));
        }
        when(cartService.getOrCreateCart(null, sessionID)).thenReturn(cartWithItems(items));

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, null, sessionID);

        assertThat(result).hasSize(30);
        // Most recent items have the highest index (base + largest offset)
        assertThat(result).extracting(ProductSignalDTO::getProductID).contains("p34", "p5");
        assertThat(result).extracting(ProductSignalDTO::getProductID).doesNotContain("p0", "p4");
    }

    // ---- getRecommendationInputDTOList: dedupe ----

    @Test
    void getRecommendationInputDTOList_dedupesSignals_keepingHighestWeight() {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        RecommendedProductRequestDTO requestDTO = requestDTO(historyItem("shared", now));
        ProductSignalDTO purchaseSignal = new ProductSignalDTO("shared", SignalTypeEnums.PURCHASE.getName(), now);
        when(orderHistoryCacheService.get(userID)).thenReturn(Optional.of(List.of(purchaseSignal)));
        CartItem cartItem = cartItem("shared", now);
        when(cartService.getOrCreateCart(userID, sessionID)).thenReturn(cartWithItems(new ArrayList<>(List.of(cartItem))));

        List<ProductSignalDTO> result = productRecommendationRequestBuilder.getRecommendationInputDTOList(requestDTO, userID, sessionID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(SignalTypeEnums.PURCHASE.getName());
    }

    // ---- getRecommendationRequestDTO ----

    @Test
    void getRecommendationRequestDTO_setsSignalListAndMaxResult() {
        List<ProductSignalDTO> signals = List.of(new ProductSignalDTO("p1", SignalTypeEnums.BROWSE.getName(), LocalDateTime.now()));

        RecommendationRequestDTO result = productRecommendationRequestBuilder.getRecommendationRequestDTO(signals);

        assertThat(result.getSignalList()).isEqualTo(signals);
        assertThat(result.getMaxResult()).isEqualTo(20);
    }
}
