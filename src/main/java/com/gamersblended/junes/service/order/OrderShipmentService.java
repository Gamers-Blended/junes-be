package com.gamersblended.junes.service.order;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.exception.EmailNotFoundException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.EmailProducerService;
import com.gamersblended.junes.service.TransactionService;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.util.SnowflakeIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderShipmentService {

    private final TransactionRepository transactionRepository;
    private final OrderHistoryCacheService orderHistoryCacheService;
    private final EmailProducerService emailProducerService;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final TransactionService transactionService;
    private final OrderShipmentService self;

    private static final SnowflakeIDGenerator idGenerator = new SnowflakeIDGenerator(2);
    private static final String TRACKING_NUMBER_PREFIX = "TRK";

    public OrderShipmentService(TransactionRepository transactionRepository, OrderHistoryCacheService orderHistoryCacheService,
                                EmailProducerService emailProducerService, UserRepository userRepository,
                                AddressRepository addressRepository, AddressMapper addressMapper,
                                TransactionService transactionService, @Lazy OrderShipmentService self) {
        this.transactionRepository = transactionRepository;
        this.orderHistoryCacheService = orderHistoryCacheService;
        this.emailProducerService = emailProducerService;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.transactionService = transactionService;
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
                Transaction shippedTransaction = self.shipOrder(transaction.getTransactionID());
                shippedCount++;
                if (null != shippedTransaction) {
                    sendShippedEmail(shippedTransaction);
                }
            } catch (Exception ex) {
                log.error("Failed to ship order {}", transaction.getOrderNumber(), ex);
            }
        }

        log.info("[OrderShipmentService] Shipped {} order(s)", shippedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Transaction shipOrder(UUID transactionID) {
        // Re-fetch to get live status (in case of stale-entities)
        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionID));

        if (!TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue().equals(transaction.getStatus())) {
            log.info("[OrderShipmentService] Skipping order {} - no longer Awaiting Shipment", transaction.getOrderNumber());
            return null;
        }

        transaction.setStatus(TransactionStatus.SHIPPED.getTransactionStatusValue());
        transaction.setShippedDate(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        transaction.setTrackingNumber(TRACKING_NUMBER_PREFIX + idGenerator.generateOrderID());
        transaction = transactionRepository.save(transaction);

        // Force-init the lazy items collection while the transaction/session is still open
        // simulateShipment() (the caller) is not @Transactional, so this must happen here
        Hibernate.initialize(transaction.getItems());

        orderHistoryCacheService.evict(transaction.getUserID());

        log.info("[OrderShipmentService] Order {} shipped", transaction.getOrderNumber());

        return transaction;
    }

    // Failures here must not affect the shipped-status update, which has already committed
    private void sendShippedEmail(Transaction transaction) {
        try {
            String email = userRepository.getUserEmail(transaction.getUserID())
                    .orElseThrow(() -> new EmailNotFoundException("UserID = " + transaction.getUserID() + " email not found"));

            List<OrderItemDTO> orderItemDTOList = transaction.getItems().stream()
                    .map(item -> {
                        OrderItemDTO orderItemDTO = new OrderItemDTO();
                        orderItemDTO.setProductID(item.getProductID());
                        orderItemDTO.setQuantity(item.getQuantity());
                        return orderItemDTO;
                    })
                    .toList();

            Map<String, Product> productMap = transactionService.getProductsByIDMap(
                    orderItemDTOList, OrderItemDTO::getProductID);

            Address address = addressRepository.getAddressByUserIDAndID(transaction.getUserID(), transaction.getShippingAddressID())
                    .orElseThrow(() -> new SavedItemNotFoundException("Address not found"));

            emailProducerService.sendOrderShippedEmail(email, transaction, productMap, addressMapper.toDTO(address));
        } catch (Exception ex) {
            log.error("Failed to queue shipped email for order {}", transaction.getOrderNumber(), ex);
        }
    }
}
