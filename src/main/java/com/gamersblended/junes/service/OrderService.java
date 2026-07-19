package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.CreateOrderException;
import com.gamersblended.junes.exception.InsufficientStockException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@Transactional
public class OrderService {

    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderCreationService orderCreationService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;

    public OrderService(
            AddressRepository addressRepository,
            PaymentMethodRepository paymentMethodRepository,
            OrderCreationService orderCreationService,
            InventoryService inventoryService,
            TransactionService transactionService) {
        this.addressRepository = addressRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.orderCreationService = orderCreationService;
        this.inventoryService = inventoryService;
        this.transactionService = transactionService;
    }

    // Reserves inventory and creates order in PAYMENT_PENDING status
    @Transactional
    public String placeOrder(UUID userID, PlaceOrderRequest placeOrderRequest) {
        // Validate shipping address and payment method
        validateUserData(userID, placeOrderRequest.getAddressDTO().getAddressID(), placeOrderRequest.getPaymentMethodID());

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
                    log.error("Requested quantity of {} exceeds available stock for {}", entry.getValue(), entry.getKey());
                    throw new InsufficientStockException("Requested quantity exceeds available stock");
                }

                reservedProductList.add(entry.getKey());
            }

            // Get product metadata
            Map<String, Product> productMap = transactionService.getProductsByIDMap(placeOrderRequest.getOrderItemDTOList(), OrderItemDTO::getProductID);

            // All inventory reserved successfully
            // Create order as PAYMENT_PENDING + write OrderPlacedEvent to outbox in 1 database transaction
            Transaction transaction = orderCreationService.createPendingOrder(userID, placeOrderRequest, consolidatedItemMap, productMap);

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

}
