package com.gamersblended.junes.service.order;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.CreateOrderException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.service.product.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private OrderCreationService orderCreationService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private TransactionService transactionService;

    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        orderProcessingService = new OrderProcessingService(
                addressRepository, paymentMethodRepository, orderCreationService, inventoryService, transactionService);
    }

    private static PlaceOrderRequest placeOrderRequest(UUID addressID, UUID paymentMethodID, OrderItemDTO... items) {
        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setAddressID(addressID);
        return new PlaceOrderRequest(addressDTO, paymentMethodID, List.of(items), BigDecimal.TEN);
    }

    private static void stubValidUserData(AddressRepository addressRepository, PaymentMethodRepository paymentMethodRepository,
                                          UUID userID, UUID addressID, UUID paymentMethodID) {
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(new Address()));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(new PaymentMethod()));
    }

    // ---- processOrder: validation ----

    @Test
    void processOrder_throwsSavedItemNotFoundException_whenAddressMissing() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID, new OrderItemDTO(1, "p1"));
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderProcessingService.processOrder(userID, request, "idem-key"))
                .isInstanceOf(SavedItemNotFoundException.class);

        verifyNoInteractions(inventoryService, orderCreationService);
    }

    @Test
    void processOrder_throwsSavedItemNotFoundException_whenPaymentMethodMissing() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID, new OrderItemDTO(1, "p1"));
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(new Address()));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderProcessingService.processOrder(userID, request, "idem-key"))
                .isInstanceOf(SavedItemNotFoundException.class);

        verifyNoInteractions(inventoryService, orderCreationService);
    }

    // ---- processOrder: happy path ----

    @Test
    void processOrder_consolidatesDuplicateProductIDs_reservesConsolidatedQuantity() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID,
                new OrderItemDTO(2, "p1"), new OrderItemDTO(3, "p1"), new OrderItemDTO(1, "p2"));
        stubValidUserData(addressRepository, paymentMethodRepository, userID, addressID, paymentMethodID);
        when(inventoryService.reserveStock(anyString(), anyInt())).thenReturn(true);
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        Transaction transaction = new Transaction();
        transaction.setOrderNumber("J123456789");
        when(orderCreationService.createPendingOrder(eq(userID), eq(request), anyMap(), anyMap(), eq("idem-key")))
                .thenReturn(transaction);

        String orderNumber = orderProcessingService.processOrder(userID, request, "idem-key");

        assertThat(orderNumber).isEqualTo("J123456789");
        verify(inventoryService).reserveStock("p1", 5);
        verify(inventoryService).reserveStock("p2", 1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void processOrder_createsOrderWithConsolidatedItemsAndProductMap_onSuccess() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID, new OrderItemDTO(1, "p1"));
        stubValidUserData(addressRepository, paymentMethodRepository, userID, addressID, paymentMethodID);
        when(inventoryService.reserveStock("p1", 1)).thenReturn(true);
        Map<String, Product> productMap = Map.of();
        when(transactionService.getProductsByIDMap(eq(request.getOrderItemDTOList()), any())).thenReturn(productMap);
        Transaction transaction = new Transaction();
        transaction.setOrderNumber("J987654321");
        when(orderCreationService.createPendingOrder(eq(userID), eq(request), anyMap(), eq(productMap), eq("idem-key")))
                .thenReturn(transaction);

        String orderNumber = orderProcessingService.processOrder(userID, request, "idem-key");

        assertThat(orderNumber).isEqualTo("J987654321");
        ArgumentCaptor<Map<String, Integer>> itemMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orderCreationService).createPendingOrder(eq(userID), eq(request), itemMapCaptor.capture(), eq(productMap), eq("idem-key"));
        assertThat(itemMapCaptor.getValue()).containsEntry("p1", 1);
    }

    // ---- processOrder: reservation failure ----

    @Test
    void processOrder_throwsCreateOrderException_andRollsBackAlreadyReservedItems_whenReservationFails() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID,
                new OrderItemDTO(1, "p1"), new OrderItemDTO(1, "p2"));
        stubValidUserData(addressRepository, paymentMethodRepository, userID, addressID, paymentMethodID);
        when(inventoryService.reserveStock("p1", 1)).thenReturn(true);
        when(inventoryService.reserveStock("p2", 1)).thenReturn(false);

        assertThatThrownBy(() -> orderProcessingService.processOrder(userID, request, "idem-key"))
                .isInstanceOf(CreateOrderException.class)
                .hasMessageContaining("InsufficientStockException");

        // rollbackInventory is invoked once inline on the failed reservation, then again from the
        // outer catch block - p1 (the only successfully reserved product) gets restored twice
        verify(inventoryService, times(2)).restoreStock("p1", 1);
        verify(inventoryService, never()).restoreStock(eq("p2"), anyInt());
        verifyNoInteractions(orderCreationService);
    }

    @Test
    void processOrder_throwsCreateOrderException_whenOrderCreationServiceFails() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID, new OrderItemDTO(2, "p1"));
        stubValidUserData(addressRepository, paymentMethodRepository, userID, addressID, paymentMethodID);
        when(inventoryService.reserveStock("p1", 2)).thenReturn(true);
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        when(orderCreationService.createPendingOrder(any(), any(), anyMap(), anyMap(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> orderProcessingService.processOrder(userID, request, "idem-key"))
                .isInstanceOf(CreateOrderException.class)
                .hasMessageContaining("db down");

        verify(inventoryService).restoreStock("p1", 2);
    }

    @Test
    void processOrder_rollbackContinuesForRemainingProducts_whenOneRestoreStockThrows() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, paymentMethodID,
                new OrderItemDTO(1, "p1"), new OrderItemDTO(1, "p2"), new OrderItemDTO(1, "p3"));
        stubValidUserData(addressRepository, paymentMethodRepository, userID, addressID, paymentMethodID);
        when(inventoryService.reserveStock("p1", 1)).thenReturn(true);
        when(inventoryService.reserveStock("p2", 1)).thenReturn(true);
        when(inventoryService.reserveStock("p3", 1)).thenReturn(false);
        doThrow(new RuntimeException("mongo down")).when(inventoryService).restoreStock("p1", 1);

        assertThatThrownBy(() -> orderProcessingService.processOrder(userID, request, "idem-key"))
                .isInstanceOf(CreateOrderException.class);

        verify(inventoryService, atLeastOnce()).restoreStock("p2", 1);
    }
}
