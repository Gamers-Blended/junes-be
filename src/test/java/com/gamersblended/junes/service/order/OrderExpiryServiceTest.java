package com.gamersblended.junes.service.order;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.product.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpiryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InventoryService inventoryService;

    private OrderExpiryService orderExpiryService;

    @BeforeEach
    void setUp() {
        orderExpiryService = new OrderExpiryService(transactionRepository, inventoryService);
    }

    private static Transaction transaction(String orderNumber, TransactionItem... items) {
        Transaction transaction = new Transaction();
        transaction.setOrderNumber(orderNumber);
        transaction.setStatus(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        transaction.setItems(List.of(items));
        return transaction;
    }

    private static TransactionItem transactionItem(String productID, int quantity) {
        TransactionItem item = new TransactionItem();
        item.setProductID(productID);
        item.setQuantity(quantity);
        return item;
    }

    // ---- releaseExpiredReservations ----

    @Test
    void releaseExpiredReservations_doesNothing_whenNoExpiredTransactions() {
        when(transactionRepository.findByStatusAndOrderDateBefore(eq(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue()), any()))
                .thenReturn(List.of());

        orderExpiryService.releaseExpiredReservations();

        verifyNoInteractions(inventoryService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void releaseExpiredReservations_queriesWithPaymentPendingStatus_andCutoffInThePast() {
        when(transactionRepository.findByStatusAndOrderDateBefore(anyString(), any())).thenReturn(List.of());

        orderExpiryService.releaseExpiredReservations();

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).findByStatusAndOrderDateBefore(statusCaptor.capture(), cutoffCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now());
    }

    @Test
    void releaseExpiredReservations_restoresStockForEachItem_andCancelsTransaction() {
        Transaction transaction = transaction("ORD-1", transactionItem("p1", 2), transactionItem("p2", 1));
        when(transactionRepository.findByStatusAndOrderDateBefore(anyString(), any())).thenReturn(List.of(transaction));

        orderExpiryService.releaseExpiredReservations();

        verify(inventoryService).restoreStock("p1", 2);
        verify(inventoryService).restoreStock("p2", 1);
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.CANCELLED.getTransactionStatusValue());
    }

    @Test
    void releaseExpiredReservations_continuesReleasingRemainingItems_whenOneRestoreFails() {
        Transaction transaction = transaction("ORD-1", transactionItem("p1", 2), transactionItem("p2", 1));
        when(transactionRepository.findByStatusAndOrderDateBefore(anyString(), any())).thenReturn(List.of(transaction));
        doThrow(new RuntimeException("mongo down")).when(inventoryService).restoreStock("p1", 2);

        orderExpiryService.releaseExpiredReservations();

        verify(inventoryService).restoreStock("p1", 2);
        verify(inventoryService).restoreStock("p2", 1);
        verify(transactionRepository).save(argThat(t -> t.getStatus().equals(TransactionStatus.CANCELLED.getTransactionStatusValue())));
    }

    @Test
    void releaseExpiredReservations_processesEachExpiredTransactionIndependently() {
        Transaction transaction1 = transaction("ORD-1", transactionItem("p1", 1));
        Transaction transaction2 = transaction("ORD-2", transactionItem("p2", 1));
        when(transactionRepository.findByStatusAndOrderDateBefore(anyString(), any())).thenReturn(List.of(transaction1, transaction2));

        orderExpiryService.releaseExpiredReservations();

        verify(transactionRepository, times(2)).save(any());
        verify(inventoryService).restoreStock("p1", 1);
        verify(inventoryService).restoreStock("p2", 1);
    }
}
