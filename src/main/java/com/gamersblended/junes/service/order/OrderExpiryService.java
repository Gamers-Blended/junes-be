package com.gamersblended.junes.service.order;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.InventoryService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.RESERVATION_EXPIRY_MINUTES;

@Slf4j
@Service
public class OrderExpiryService {

    private final TransactionRepository transactionRepository;
    private final InventoryService inventoryService;

    public OrderExpiryService(TransactionRepository transactionRepository, InventoryService inventoryService) {
        this.transactionRepository = transactionRepository;
        this.inventoryService = inventoryService;
    }

    // Orders left in PAYMENT_PENDING past the reservation window never received a PaymentSucceededEvent
    // or PaymentFailedEvent (e.g. abandoned checkout, lost webhook) - release their held stock and cancel them
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMinutes(RESERVATION_EXPIRY_MINUTES);
        List<Transaction> expiredTransactionList = transactionRepository.findByStatusAndOrderDateBefore(
                TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue(), cutoff);

        for (Transaction transaction : expiredTransactionList) {
            releaseExpiredTransaction(transaction);
        }

        log.info("[OrderExpiryService] Released inventory for {} expired reservation(s)", expiredTransactionList.size());
    }

    private void releaseExpiredTransaction(Transaction transaction) {
        for (TransactionItem item : transaction.getItems()) {
            try {
                inventoryService.restoreStock(item.getProductID(), item.getQuantity());
            } catch (Exception ex) {
                log.error("Failed to release inventory for product {} on expired order {}",
                        item.getProductID(), transaction.getOrderNumber(), ex);
            }
        }

        transaction.setStatus(TransactionStatus.CANCELLED.getTransactionStatusValue());
        transactionRepository.save(transaction);

        log.info("[OrderExpiryService] Expired reservation cancelled for order {}", transaction.getOrderNumber());
    }
}
