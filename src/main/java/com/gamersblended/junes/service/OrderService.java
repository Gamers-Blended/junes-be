package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.TransactionItemDTO;
import com.gamersblended.junes.dto.event.OrderPlacedEvent;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.InsufficientStockException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@Transactional
public class OrderService {

    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private EventPublisher eventPublisher;
    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;


    public OrderService(KafkaTemplate kafkaTemplate,
                        AddressRepository addressRepository, PaymentMethodRepository paymentMethodRepository,
                        ProductRepository productRepository, InventoryService inventoryService) {
        this.kafkaTemplate = kafkaTemplate;
        this.addressRepository = addressRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    public String placeOrder(UUID userID, PlaceOrderRequest placeOrderRequest) {
        // Validate shipping address and payment method
        validateUserData(userID, placeOrderRequest.getAddressID(), placeOrderRequest.getPaymentMethodID());

        // Deduplicate cart items
        // Product ID -> quantity
        Map<String, Integer> consolidatedItemMap = consolidateCartItems(placeOrderRequest.getTransactionItemDTOList());

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
        }

        Transaction transaction = processOrder(placeOrderRequest, consolidatedItemMap);

        eventPublisher.publishOrderPlaced(transaction, consolidatedItemMap);

        return transaction.getTransactionID().toString();
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

    private Map<String, Integer> consolidateCartItems(List<TransactionItemDTO> transactionItemDTOList) {
        Map<String, Integer> consolidated = new HashMap<>();

        for (TransactionItemDTO item : transactionItemDTOList) {
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
}
