package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.event.OrderCreatedEvent;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.CreateOrderException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.util.SnowflakeIDGenerator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderCreationService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;
    private final EmailProducerService emailProducerService;
    private final OrderHistoryCacheService orderHistoryCacheService;

    private static final SnowflakeIDGenerator idGenerator = new SnowflakeIDGenerator(1);
    private static final String ORDER_ID_PREFIX = "J";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String AGGREGATE_TYPE_ORDER = "Order";

    public OrderCreationService(TransactionRepository transactionRepository,
                                OutboxEventRepository outboxEventRepository,
                                ShippingService shippingService,
                                ObjectMapper objectMapper,
                                EmailProducerService emailProducerService,
                                OrderHistoryCacheService orderHistoryCacheService
    ) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.shippingService = shippingService;
        this.objectMapper = objectMapper;
        this.emailProducerService = emailProducerService;
        this.orderHistoryCacheService = orderHistoryCacheService;
    }

    // Creates order in PAYMENT_PENDING status
    // Writes OrderPlacedEvent to outbox table in 1 database transaction (DB write and eventual Kafka publish never diverge)
    @Transactional
    public Transaction createPendingOrder(UUID userID, PlaceOrderRequest placeOrderRequest,
                                          Map<String, Integer> consolidatedItemMap,
                                          Map<String, Product> productMap) {
        Transaction transaction = createTransaction(userID, placeOrderRequest, consolidatedItemMap, productMap);

        writeOutboxEvent(transaction, placeOrderRequest, consolidatedItemMap);

        log.info("[OrderCreationService] Created pending order {} for userID = {}", transaction.getOrderNumber(), userID);

        return transaction;
    }

    private Transaction createTransaction(UUID userID, PlaceOrderRequest placeOrderRequest, Map<String, Integer> consolidatedItemMap, Map<String, Product> productMap) {
        BigDecimal itemsTotal = calculateItemsTotal(consolidatedItemMap, productMap);
        BigDecimal totalAmount = itemsTotal.add(placeOrderRequest.getShippingCost());
        BigDecimal shippingWeight = shippingService.getTotalShippingWeight(placeOrderRequest.getOrderItemDTOList(), productMap);

        Transaction transaction = new Transaction();
        transaction.setOrderNumber(ORDER_ID_PREFIX + idGenerator.generateOrderID());
        transaction.setOrderDate(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        transaction.setTotalAmount(totalAmount);
        transaction.setShippingCost(placeOrderRequest.getShippingCost());
        transaction.setShippingWeight(shippingWeight);
        transaction.setTrackingNumber("123");
        transaction.setShippingAddressID(placeOrderRequest.getAddressDTO().getAddressID());
        transaction.setUserID(userID);

        List<TransactionItem> itemList = createTransactionItems(transaction, consolidatedItemMap);
        transaction.setItems(itemList);

        transaction = transactionRepository.save(transaction);


        return transaction;
    }

    private List<TransactionItem> createTransactionItems(Transaction transaction, Map<String, Integer> consolidatedItemMap) {
        List<TransactionItem> itemList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : consolidatedItemMap.entrySet()) {
            String productID = entry.getKey();
            Integer quantity = entry.getValue();

            TransactionItem item = new TransactionItem();
            item.setTransaction(transaction);
            item.setProductID(productID);
            item.setQuantity(quantity);

            itemList.add(item);
        }

        return itemList;
    }

    private BigDecimal calculateItemsTotal(Map<String, Integer> consolidatedItemMap, Map<String, Product> productMap) {
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : consolidatedItemMap.entrySet()) {
            String productID = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productMap.get(productID);

            if (null == product) {
                log.error("Unable to get product data for product: {}", productID);
                throw new ProductNotFoundException("Unable to get product data for product: productID");
            }

            BigDecimal itemTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(quantity));

            total = total.add(itemTotal);
        }

        return total;
    }

    private void writeOutboxEvent(Transaction transaction, PlaceOrderRequest placeOrderRequest,
                                  Map<String, Integer> consolidatedItemMap) {

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setTransactionID(transaction.getTransactionID());
        event.setOrderNumber(transaction.getOrderNumber());
        event.setUserID(transaction.getUserID());
        event.setPaymentMethodID(placeOrderRequest.getPaymentMethodID());
        event.setTotalAmount(transaction.getTotalAmount());

        List<OrderItemDTO> orderItemList = consolidatedItemMap.entrySet().stream()
                .map(entry -> {
                    OrderItemDTO item = new OrderItemDTO();
                    item.setProductID(entry.getKey());
                    item.setQuantity(entry.getValue());
                    return item;
                })
                .toList();
        event.setItemList(orderItemList);

        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType(AGGREGATE_TYPE_ORDER);
            outbox.setAggregateID(transaction.getOrderNumber()); // Kafka partition key
            outbox.setEventType(event.getEventType()); // "ORDER_PLACED" from BaseEvent
            outbox.setTopic(ORDER_EVENTS_TOPIC);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setCreatedOn(LocalDateTime.now());
            outbox.setPublished(false);
            outbox.setRetryCount(0);

            // Same @Transactional as transactionRepository.save()
            // Either commit together with order, or not at all
            outboxEventRepository.save(outbox);
        } catch (Exception ex) {
            log.error("Failed to write outbox event for order {}", transaction.getOrderNumber(), ex);
            throw new CreateOrderException("Failed to write outbox event: " + ex.getMessage());
        }
    }
}
