package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.PaymentSucceededEvent;
import com.gamersblended.junes.exception.EmailNotFoundException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.*;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.util.KafkaEventParser;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OrderFinalisationConsumer {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final KafkaEventParser kafkaEventParser;
    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final TransactionService transactionService;
    private final EmailProducerService emailProducerService;
    private final OrderHistoryCacheService orderHistoryCacheService;

    public OrderFinalisationConsumer(
            KafkaEventParser kafkaEventParser,
            TransactionRepository transactionRepository,
            ProcessedEventRepository processedEventRepository,
            InventoryService inventoryService,
            UserRepository userRepository,
            AddressRepository addressRepository,
            AddressMapper addressMapper,
            TransactionService transactionService,
            EmailProducerService emailProducerService,
            OrderHistoryCacheService orderHistoryCacheService) {
        this.kafkaEventParser = kafkaEventParser;
        this.transactionRepository = transactionRepository;
        this.processedEventRepository = processedEventRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.transactionService = transactionService;
        this.emailProducerService = emailProducerService;
        this.orderHistoryCacheService = orderHistoryCacheService;
    }

    @KafkaListener(topics = ORDER_EVENTS_TOPIC, groupId = "order-finalisation")
    @Transactional
    public void onOrderEvent(ConsumerRecord<String, String> orderEvent, Acknowledgment ack) {
        BaseEvent parsedEvent = kafkaEventParser.parse(orderEvent.value());

        if (parsedEvent instanceof PaymentSucceededEvent succeededEvent) {
            handlePaymentSucceeded(succeededEvent);
        } else if (parsedEvent instanceof PaymentFailedEvent failedEvent) {
            handlePaymentFailed(failedEvent);
        }

        ack.acknowledge();
    }

    private void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (processedEventRepository.existsByEventID(event.getEventID())) {
            log.info("[OrderFinalisationConsumer] Event {} already processed, skipping...", event.getEventID());
            return;
        }

        Transaction transaction = transactionRepository.findByUserIDAndOrderNumber(event.getUserID(), event.getOrderNumber())
                .orElseThrow(() -> {
                    log.error("Transaction not found for userID = {} and order = {}", event.getUserID(), event.getOrderNumber());
                    return new TransactionNotFoundException("Transaction not found: " + event.getOrderNumber());
                });

        transaction.setStatus(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());
        transactionRepository.save(transaction);

        sendConfirmationEmail(transaction);

        orderHistoryCacheService.evict(transaction.getUserID());
        log.info("[OrderFinalisationConsumer] Order history cache evicted for userID = {} after payment success", transaction.getUserID());

        processedEventRepository.save(new ProcessedEvent(event.getEventID(), LocalDateTime.now(ZoneId.of("Asia/Singapore"))));

        log.info("[OrderFinalisationConsumer] Order {} finalised as {}", event.getOrderNumber(),
                TransactionStatus.PAYMENT_FAILED.getTransactionStatusValue());
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        if (processedEventRepository.existsByEventID(event.getEventID())) {
            log.info("[OrderFinalisationConsumer] Event {} already processed, skipping...", event.getEventID());
            return;
        }

        Transaction transaction = transactionRepository.findByUserIDAndOrderNumber(event.getUserID(), event.getOrderNumber())
                .orElseThrow(() -> {
                    log.error("Transaction not found for userID = {} and order = {}", event.getUserID(), event.getOrderNumber());
                    return new TransactionNotFoundException("Transaction not found: " + event.getOrderNumber());
                });

        transaction.setStatus(TransactionStatus.PAYMENT_FAILED.getTransactionStatusValue());
        transactionRepository.save(transaction);

        // Inventory was decremented at reservation time in OrderService.placeOrder()
        // Before payment was made
        // Failed charge -> revert stock
        releaseInventory(transaction);

        processedEventRepository.save(new ProcessedEvent(event.getEventID(), LocalDateTime.now(ZoneId.of("Asia/Singapore"))));

        log.error("[OrderFinalisationConsumer] Order {} finalised as {}: {}", event.getOrderNumber(),
                TransactionStatus.PAYMENT_FAILED.getTransactionStatusValue(),
                event.getFailureReason());
    }

    private void releaseInventory(Transaction transaction) {
        for (TransactionItem item : transaction.getItems()) {
            try {
                inventoryService.restoreStock(item.getProductID(), item.getQuantity());
            } catch (Exception ex) {
                // Log and continue restoring the rest rather than letting 1 failure block them
                log.error("Failed to release inventory for product {} on order {}",
                        item.getProductID(), transaction.getOrderNumber(), ex);
            }
        }
    }

    private void sendConfirmationEmail(Transaction transaction) {
        String email = userRepository.getUserEmail(transaction.getUserID())
                .orElseThrow(() -> {
                    log.error("User's email not found for ID: {}", transaction.getUserID());
                    return new EmailNotFoundException("UserID = " + transaction.getUserID() + " email not found");
                });

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
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", transaction.getShippingAddressID(), transaction.getUserID());
                    return new SavedItemNotFoundException("Address not found");
                });

        emailProducerService.sendOrderConfirmedEmail(email, transaction, productMap, addressMapper.toDTO(address));
    }
}
