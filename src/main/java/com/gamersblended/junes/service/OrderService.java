package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.SnowflakeIDGenerator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class OrderService {

    private final EventPublisher eventPublisher;
    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;
    private final TransactionService transactionService;
    private final EmailProducerService emailProducerService;
    private static final SnowflakeIDGenerator idGenerator = new SnowflakeIDGenerator(1);
    private static final String ORDER_ID_PREFIX = "J";

    public OrderService(
            EventPublisher eventPublisher,
            AddressRepository addressRepository,
            PaymentMethodRepository paymentMethodRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            ShippingService shippingService,
            TransactionService transactionService,
            EmailProducerService emailProducerService) {
        this.eventPublisher = eventPublisher;
        this.addressRepository = addressRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.shippingService = shippingService;
        this.transactionService = transactionService;
        this.emailProducerService = emailProducerService;
    }

    @Transactional
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

            // Send email
            AddressDTO shippingAddress = placeOrderRequest.getAddressDTO();
            String email = userRepository.getUserEmail(userID)
                    .orElseThrow(() -> {
                        log.error("User's email not found for ID: {}", userID);
                        return new EmailNotFoundException("User's email not found");
                    });
            emailProducerService.sendOrderConfirmedEmail(email, transaction, productMap, shippingAddress);
            return transaction.getOrderNumber();

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
                    return new SavedItemNotFoundException("Payment method not found");
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
        transaction.setOrderNumber(ORDER_ID_PREFIX + idGenerator.generateOrderID());
        transaction.setOrderDate(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        transaction.setTotalAmount(totalAmount);
        transaction.setShippingCost(placeOrderRequest.getShippingCost());
        transaction.setShippingWeight(shippingWeight);
        transaction.setTrackingNumber("123");
        transaction.setShippingAddressID(placeOrderRequest.getAddressID());
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

}
