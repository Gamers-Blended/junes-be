package com.gamersblended.junes.service.order;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.util.SnowflakeIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OrderShipmentService {

    private final TransactionRepository transactionRepository;
    private final OrderHistoryCacheService orderHistoryCacheService;
    private final OrderShipmentService self;

    private static final SnowflakeIDGenerator idGenerator = new SnowflakeIDGenerator(2);
    private static final String TRACKING_NUMBER_PREFIX = "TRK";

    public OrderShipmentService(TransactionRepository transactionRepository, OrderHistoryCacheService orderHistoryCacheService, @Lazy OrderShipmentService self) {
        this.transactionRepository = transactionRepository;
        this.orderHistoryCacheService = orderHistoryCacheService;
        this.self = self;
    }

    // Simulates a daily shipping batch: orders sitting in AWAITING_SHIPMENT are shipped once a day
    // Each order is transitioned in its own transaction
    // A failure on 1 order can't roll back the rest
    public void simulateShipment() {
        List<Transaction> awaitingShipmentList = transactionRepository.findByStatus(
                TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());

        int shippedCount = 0;
        for (Transaction transaction : awaitingShipmentList) {
            try {
                self.shipOrder(transaction.getTransactionID());
                shippedCount++;
            } catch (Exception ex) {
                log.error("Failed to ship order {}", transaction.getOrderNumber(), ex);
            }
        }

        log.info("[OrderShipmentService] Shipped {} order(s)", shippedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void shipOrder(UUID transactionID) {
        // Re-fetch to get live status (in case of stale-entities)
        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionID));

        if (!TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue().equals(transaction.getStatus())) {
            log.info("[OrderShipmentService] Skipping order {} - no longer Awaiting Shipment", transaction.getOrderNumber());
            return;
        }

        transaction.setStatus(TransactionStatus.SHIPPED.getTransactionStatusValue());
        transaction.setShippedDate(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        transaction.setTrackingNumber(TRACKING_NUMBER_PREFIX + idGenerator.generateOrderID());
        transactionRepository.save(transaction);

        orderHistoryCacheService.evict(transaction.getUserID());

        log.info("[OrderShipmentService] Order {} shipped", transaction.getOrderNumber());
    }
}
