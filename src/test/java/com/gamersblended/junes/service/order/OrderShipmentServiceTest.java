package com.gamersblended.junes.service.order;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.service.email.EmailProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderShipmentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OrderHistoryCacheService orderHistoryCacheService;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private OrderShipmentService self;

    private OrderShipmentService orderShipmentService;

    @BeforeEach
    void setUp() {
        orderShipmentService = new OrderShipmentService(transactionRepository, orderHistoryCacheService,
                emailProducerService, userRepository, addressRepository, addressMapper, transactionService, self);
    }

    private static Transaction transaction(UUID transactionID, UUID userID, String orderNumber, String status, TransactionItem... items) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(transactionID);
        transaction.setUserID(userID);
        transaction.setOrderNumber(orderNumber);
        transaction.setStatus(status);
        transaction.setShippingAddressID(UUID.randomUUID());
        transaction.setItems(List.of(items));
        return transaction;
    }

    private static TransactionItem transactionItem() {
        TransactionItem item = new TransactionItem();
        item.setProductID("p1");
        item.setQuantity(1);
        return item;
    }

    // ---- simulateShipment ----

    @Test
    void simulateShipment_doesNothing_whenNoOrdersAwaitingShipment() {
        when(transactionRepository.findByStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue())).thenReturn(List.of());

        orderShipmentService.simulateShipment();

        verifyNoInteractions(self, emailProducerService);
    }

    @Test
    void simulateShipment_shipsOrder_andSendsShippedEmail() {
        UUID transactionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        Transaction pending = transaction(transactionID, userID, "ORD-1", TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        Transaction shipped = transaction(transactionID, userID, "ORD-1", TransactionStatus.SHIPPED.getTransactionStatusValue(),
                transactionItem());
        when(transactionRepository.findByStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue())).thenReturn(List.of(pending));
        when(self.shipOrder(transactionID)).thenReturn(shipped);
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.of("user@example.com"));
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        Address address = new Address();
        when(addressRepository.getAddressByUserIDAndID(eq(userID), any())).thenReturn(Optional.of(address));
        when(addressMapper.toDTO(address)).thenReturn(new AddressDTO());

        orderShipmentService.simulateShipment();

        verify(self).shipOrder(transactionID);
        verify(emailProducerService).sendOrderShippedEmail(eq("user@example.com"), eq(shipped), anyMap(), any());
    }

    @Test
    void simulateShipment_skipsEmail_whenShipOrderReturnsNull() {
        UUID transactionID = UUID.randomUUID();
        Transaction pending = transaction(transactionID, UUID.randomUUID(), "ORD-1", TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        when(transactionRepository.findByStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue())).thenReturn(List.of(pending));
        when(self.shipOrder(transactionID)).thenReturn(null);

        orderShipmentService.simulateShipment();

        verifyNoInteractions(emailProducerService);
    }

    @Test
    void simulateShipment_continuesShippingRemainingOrders_whenOneShipOrderThrows() {
        UUID transactionID1 = UUID.randomUUID();
        UUID transactionID2 = UUID.randomUUID();
        UUID userID2 = UUID.randomUUID();
        Transaction pending1 = transaction(transactionID1, UUID.randomUUID(), "ORD-1", TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        Transaction pending2 = transaction(transactionID2, userID2, "ORD-2", TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        Transaction shipped2 = transaction(transactionID2, userID2, "ORD-2", TransactionStatus.SHIPPED.getTransactionStatusValue(),
                transactionItem());
        when(transactionRepository.findByStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue()))
                .thenReturn(List.of(pending1, pending2));
        when(self.shipOrder(transactionID1)).thenThrow(new RuntimeException("db error"));
        when(self.shipOrder(transactionID2)).thenReturn(shipped2);
        when(userRepository.getUserEmail(userID2)).thenReturn(Optional.of("user2@example.com"));
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        when(addressRepository.getAddressByUserIDAndID(eq(userID2), any())).thenReturn(Optional.of(new Address()));
        when(addressMapper.toDTO(any())).thenReturn(new AddressDTO());

        orderShipmentService.simulateShipment();

        verify(self).shipOrder(transactionID1);
        verify(self).shipOrder(transactionID2);
        verify(emailProducerService).sendOrderShippedEmail(eq("user2@example.com"), eq(shipped2), anyMap(), any());
    }

    @Test
    void simulateShipment_doesNotPropagate_whenSendShippedEmailFails() {
        UUID transactionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        Transaction pending = transaction(transactionID, userID, "ORD-1", TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        Transaction shipped = transaction(transactionID, userID, "ORD-1", TransactionStatus.SHIPPED.getTransactionStatusValue(),
                transactionItem());
        when(transactionRepository.findByStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue())).thenReturn(List.of(pending));
        when(self.shipOrder(transactionID)).thenReturn(shipped);
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.empty());

        orderShipmentService.simulateShipment();

        verifyNoInteractions(emailProducerService);
    }

    // ---- shipOrder ----

    @Test
    void shipOrder_throwsTransactionNotFoundException_whenTransactionMissing() {
        UUID transactionID = UUID.randomUUID();
        when(transactionRepository.findById(transactionID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderShipmentService.shipOrder(transactionID))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void shipOrder_returnsNull_whenStatusIsNotAwaitingShipment() {
        UUID transactionID = UUID.randomUUID();
        Transaction transaction = transaction(transactionID, UUID.randomUUID(), "ORD-1", TransactionStatus.SHIPPED.getTransactionStatusValue());
        when(transactionRepository.findById(transactionID)).thenReturn(Optional.of(transaction));

        Transaction result = orderShipmentService.shipOrder(transactionID);

        assertThat(result).isNull();
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(orderHistoryCacheService);
    }

    @Test
    void shipOrder_updatesStatusToShipped_setsTrackingNumber_andEvictsCache() {
        UUID transactionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        Transaction transaction = transaction(transactionID, userID, "ORD-1",
                TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue(), transactionItem());
        when(transactionRepository.findById(transactionID)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = orderShipmentService.shipOrder(transactionID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SHIPPED.getTransactionStatusValue());
        assertThat(result.getTrackingNumber()).startsWith("TRK");
        assertThat(result.getShippedDate()).isNotNull();
        verify(orderHistoryCacheService).evict(userID);
    }
}
