package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class OrderService {

    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private EventPublisher eventPublisher;
    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;


    public OrderService(KafkaTemplate kafkaTemplate, AddressRepository addressRepository, PaymentMethodRepository paymentMethodRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.addressRepository = addressRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    public String placeOrder(UUID userID, PlaceOrderRequest placeOrderRequest) {
        // Validate shipping address and payment method
        validateUserData(userID, placeOrderRequest.getAddressID(), placeOrderRequest.getPaymentMethodID());

        // Deduplicate cart items
        Map<Long, Integer> consolidatedItemMap = consolidateCartItems(placeOrderRequest.getTransactionItemDTOList());

        reserveInventory(consolidatedItemMap);

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
}
