package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.event.OrderPlacedEvent;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.CreateOrderException;
import com.gamersblended.junes.exception.InsufficientStockException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.mapper.TransactionItemMapper;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.TransactionItemRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class OrderService {

    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private EventPublisher eventPublisher;
    private TransactionItemMapper itemMapper;
    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductRepository productRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;
    private final TransactionService transactionService;


    public OrderService(KafkaTemplate kafkaTemplate,
                        TransactionItemMapper itemMapper,
                        AddressRepository addressRepository, PaymentMethodRepository paymentMethodRepository,
                        ProductRepository productRepository, TransactionRepository transactionRepository,
                        TransactionItemRepository transactionItemRepository,
                        InventoryService inventoryService, ShippingService shippingService,
                        TransactionService transactionService) {
        this.kafkaTemplate = kafkaTemplate;
        this.itemMapper = itemMapper;
        this.addressRepository = addressRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.inventoryService = inventoryService;
        this.shippingService = shippingService;
        this.transactionService = transactionService;
    }

    public String placeOrder(UUID userID, PlaceOrderRequest placeOrderRequest) {
        // Validate shipping address and payment method
        validateUserData(userID, placeOrderRequest.getAddressID(), placeOrderRequest.getPaymentMethodID());

        // Deduplicate cart items
        // Product ID -> quantity
        Map<String, Integer> consolidatedItemMap = consolidateCartItems(placeOrderRequest.getOrderItemDTOList());

        // Reserve all inventory atomically
        List<String> reservedProductList = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> entry : consolidatedItemMap.entrySet()) {
                boolean reserved = inventoryService.reserveStock(
                        entry.getKey(),
                        entry.getValue()
                );

                if (!reserved) {
                    rollbackInventory(reservedProductList, consolidatedItemMap);
                    log.error("Requested quantity of {} exceeds available stock", entry.getValue());
                    throw new InsufficientStockException("Requested quantity exceeds available stock");
                }

                reservedProductList.add(entry.getKey());
            }

            // Get product metadata
            Map<String, Product> productMap = transactionService.getProductsByIDMap(placeOrderRequest.getOrderItemDTOList(), OrderItemDTO::getProductID);

            // All inventory reserved successfully, create order
            Transaction transaction = createTransaction(userID, placeOrderRequest, consolidatedItemMap, productMap);
            eventPublisher.publishOrderPlaced(transaction, consolidatedItemMap);

            return transaction.getTransactionID().toString();

        } catch (Exception ex) {
            log.error("Exception in creating order for userID: {}", userID, ex);
            rollbackInventory(reservedProductList, consolidatedItemMap);
            throw new CreateOrderException("Exception in creating order: " + ex);
        }

    }

    private void validateUserData(UUID userID, UUID addressID, UUID paymentMethodID) {
        addressRepository.getAddressByUserIDAndID(userID, addressID)
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", addressID, userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)
                .orElseThrow(() -> {
                    log.error("Payment method not found with ID: {} for user {}", paymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found with ID: " + paymentMethodID);
                });

    }

    private Map<String, Integer> consolidateCartItems(List<OrderItemDTO> orderItemDTOList) {
        Map<String, Integer> consolidated = new HashMap<>();

        for (OrderItemDTO item : orderItemDTOList) {
            consolidated.merge(item.getProductID(), item.getQuantity(), Integer::sum);
        }

        return consolidated;
    }

    private void rollbackInventory(List<String> productIDList, Map<String, Integer> quantities) {
        for (String productID : productIDList) {
            try {
                inventoryService.restoreStock(
                        productID,
                        quantities.get(productID)
                );
            } catch (Exception ex) {
                log.error("Failed to rollback inventory for product: {}", productID, ex);
            }
        }
    }

    private Transaction createTransaction(UUID userID, PlaceOrderRequest placeOrderRequest, Map<String, Integer> consolidatedItemMap, Map<String, Product> productMap) {
        BigDecimal itemsTotal = calculateItemsTotal(consolidatedItemMap, productMap);
        BigDecimal totalAmount = itemsTotal.add(placeOrderRequest.getShippingCost());

        BigDecimal shippingWeight = shippingService.getTotalShippingWeight(placeOrderRequest.getOrderItemDTOList(), productMap);

        Transaction transaction = new Transaction();
        transaction.setOrderNumber("test");
        transaction.setOrderDate(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        transaction.setTotalAmount(totalAmount);
        transaction.setShippingCost(placeOrderRequest.getShippingCost());
        transaction.setShippingWeight(shippingWeight);
        transaction.setTrackingNumber("123");
        transaction.setShippingAddressID(placeOrderRequest.getAddressID());
        transaction.setUserID(userID);

        transaction = transactionRepository.save(transaction);

        createTransactionItems(transaction.getTransactionID(), consolidatedItemMap);

        return transaction;
    }

    private void createTransactionItems(UUID transactionID, Map<String, Integer> consolidatedItemMap) {
        for (Map.Entry<String, Integer> entry : consolidatedItemMap.entrySet()) {
            String productID = entry.getKey();
            Integer quantity = entry.getValue();

            TransactionItem item = new TransactionItem();
            item.setTransactionItemID(transactionID);
            item.setProductID(productID);
            item.setQuantity(quantity);
            item.setCreatedOn(LocalDateTime.now());

            transactionItemRepository.save(item);
        }
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

}
