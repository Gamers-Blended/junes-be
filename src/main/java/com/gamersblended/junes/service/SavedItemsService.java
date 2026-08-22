package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodAddressAttachedEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodDetachEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodEditEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodSetDefaultEvent;
import com.gamersblended.junes.dto.request.AddPaymentMethodRequest;
import com.gamersblended.junes.dto.request.AttachAddressToPaymentMethodRequest;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.mapper.PaymentMethodMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.AddressValidator;
import com.gamersblended.junes.util.PaymentMethodValidator;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodUpdateParams;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.MAX_NUMBER_OF_SAVED_ITEMS;
import static com.gamersblended.junes.constant.KafkaConstants.*;

@Slf4j
@Service
public class SavedItemsService {

    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final AddressValidator addressValidator;
    private final PaymentMethodValidator paymentMethodValidator;
    private final StripeClient stripeClient;
    private final ObjectMapper objectMapper;

    private static final String ADDRESS = "address";
    private static final String PAYMENT_METHOD = "payment_method";

    public SavedItemsService(AddressRepository addressRepository, AddressMapper addressMapper,
                             PaymentMethodRepository paymentMethodRepository, PaymentMethodMapper paymentMethodMapper,
                             AddressValidator addressValidator, PaymentMethodValidator paymentMethodValidator,
                             UserRepository userRepository, OutboxEventRepository outboxEventRepository,
                             ObjectMapper objectMapper, StripeClient stripeClient) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodMapper = paymentMethodMapper;
        this.addressValidator = addressValidator;
        this.paymentMethodValidator = paymentMethodValidator;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.stripeClient = stripeClient;
    }

    public List<AddressDTO> getAllSavedAddressesForUser(UUID userID) {
        List<Address> addressesFromUserList = addressRepository.getAddressesByUserID(userID);

        if (addressesFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved addresses", userID, MAX_NUMBER_OF_SAVED_ITEMS);
        } else {
            log.info("User {} has {} saved address(es)", userID, addressesFromUserList.size());
        }

        return addressMapper.toDTOList(addressesFromUserList);
    }

    public AddressDTO getSavedAddressForUser(UUID addressID, UUID userID) {
        Address address = addressRepository.getAddressByUserIDAndID(userID, addressID)
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", addressID, userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        return addressMapper.toDTO(address);
    }

    public void addAddress(UUID userID, AddressDTO addressDTO) {
        addressValidator.validateAndSanitizeAddress(userID, addressDTO);

        List<Address> addressesFromUserList = addressRepository.getAddressesByUserID(userID);

        // Cannot exceed limit
        if (addressesFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved addresses", userID, MAX_NUMBER_OF_SAVED_ITEMS);
            throw new SavedItemLimitExceededException("User " + userID + " has reached the maximum of " + MAX_NUMBER_OF_SAVED_ITEMS + " saved addresses");
        }

        checkAndUpdateDefaultAddress(userID, addressesFromUserList, addressDTO);

        Address newAddress = addressMapper.toEntity(addressDTO);
        newAddress.setUserID(userID);
        addressRepository.save(newAddress);
    }

    @Transactional
    public void editAddress(UUID userID, UUID targetAddressID, AddressDTO addressDTO) {
        if (null == targetAddressID) {
            log.error("Error editing address for user {}: address ID is not given", userID);
            throw new InputValidationException("Address ID is not given");
        }

        addressValidator.validateAndSanitizeAddress(userID, addressDTO);

        List<Address> addressesFromUserList = addressRepository.getAddressesByUserID(userID);

        Address addressToUpdate = addressesFromUserList.stream()
                .filter(address -> address.getAddressID().equals(targetAddressID))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", targetAddressID, userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        checkAndUpdateDefaultAddress(userID, addressesFromUserList, addressDTO);

        addressMapper.updateEntityFromDTO(addressDTO, addressToUpdate);

        addressRepository.save(addressToUpdate);
    }

    @Transactional
    public void deleteAddress(UUID userID, UUID targetAddressID) {
        if (null == targetAddressID) {
            log.error("Error deleting address for user {}: address ID is not given", userID);
            throw new InputValidationException("Address ID is not given");
        }

        Address address = addressRepository.getAddressByUserIDAndID(userID, targetAddressID)
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", targetAddressID, userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        address.setDeletedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        addressRepository.save(address);
    }

    public List<PaymentMethodDTO> getAllPaymentMethodsForUser(UUID userID) {
        List<PaymentMethod> paymentMethodFromUserList = paymentMethodRepository.getPaymentMethodsByUserID(userID);

        if (paymentMethodFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved payment methods", userID, MAX_NUMBER_OF_SAVED_ITEMS);
        } else {
            log.info("User {} has {} saved payment method(s)", userID, paymentMethodFromUserList.size());
        }

        return paymentMethodMapper.toDTOList(paymentMethodFromUserList);
    }

    public PaymentMethodDTO getSavedPaymentMethodForUser(UUID paymentMethodID, UUID userID) {
        PaymentMethod paymentMethod = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)
                .orElseThrow(() -> {
                    log.error("Payment method with ID: {} not found for user: {}", paymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found");
                });

        return paymentMethodMapper.toDTO(paymentMethod);
    }

    @Transactional
    public void addPaymentMethod(UUID userID, AddPaymentMethodRequest request, String idempotencyKey) throws StripeException {

        // 1. Check if exceed size limit
        List<PaymentMethod> paymentMethodsFromUserList = paymentMethodRepository.getPaymentMethodsByUserID(userID);

        if (paymentMethodsFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved payment methods", userID, MAX_NUMBER_OF_SAVED_ITEMS);
            throw new SavedItemLimitExceededException("User " + userID + " has reached the maximum of " + MAX_NUMBER_OF_SAVED_ITEMS + " saved payment methods");
        }

        // 2. Get Payment Method from Stripe, Stripe customer ID from database
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        com.stripe.model.PaymentMethod stripePaymentMethod = stripeClient.v1().paymentMethods().retrieve(request.getStripePaymentMethodID(), options);

        String stripeCustomerID = userRepository.getStripeCustomerID(userID)
                .orElseThrow(() -> {
                    log.error("User's Stripe customer ID not found for ID: {}", userID);
                    return new StripeOperationException("User's Stripe customer ID not found");
                });

        // 3. Validate Payment Method
        paymentMethodValidator.validatePaymentMethodForAdd(userID, stripeCustomerID, stripePaymentMethod);

        // 4. Check if duplicate card for same user
        String fingerprint = stripePaymentMethod.getCard().getFingerprint();

        Optional<PaymentMethod> existing = paymentMethodRepository.findByUserIDAndCardFingerprintAndIsActiveTrue(userID, fingerprint);
        if (existing.isPresent()) {
            log.error("User {} attempted to add a duplicate card (fingerprint match with PM {})",
                    userID, existing.get().getPaymentMethodID());

            try {
                stripePaymentMethod.detach();
            } catch (StripeException ex) {
                log.error("Failed to detach duplicate Payment Method {} from Stripe: {}", stripePaymentMethod.getId(), ex.getMessage());
                // Reconciliation job will catch orphaned attach latter
            }
            throw new DuplicatePaymentMethodException("This card is already saved to your account");
        }

        // 5. Clear old default Payment Method settings (if needed)
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            CustomerUpdateParams customerParams = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(request.getStripePaymentMethodID())
                                    .build()
                    )
                    .build();

            RequestOptions setDefaultOptions = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey + "-stripe-pm-add-set-default")
                    .build();

            stripeClient.v1().customers().update(stripeCustomerID, customerParams, setDefaultOptions);
            log.info("Stripe customer profile default payment method updated for Stripe customer ID: {}", stripeCustomerID);

            // Unset current default Payment Method
            paymentMethodRepository.unsetDefaultForUser(userID);
        }

        // 6. Save to database
        PaymentMethod newPaymentMethod = new PaymentMethod();
        newPaymentMethod.setCardType(stripePaymentMethod.getCard().getBrand());
        newPaymentMethod.setCardLastFour(stripePaymentMethod.getCard().getLast4());
        newPaymentMethod.setCardHolderName(stripePaymentMethod.getBillingDetails().getName());
        newPaymentMethod.setExpirationMonth(String.valueOf(stripePaymentMethod.getCard().getExpMonth()));
        newPaymentMethod.setExpirationYear(String.valueOf(stripePaymentMethod.getCard().getExpYear()));
        newPaymentMethod.setUserID(userID);
        newPaymentMethod.setIsDefault(request.getIsDefault());
        newPaymentMethod.setIsActive(true);
        newPaymentMethod.setCardFingerprint(fingerprint);
        newPaymentMethod.setStripeCustomerID(stripeCustomerID);
        newPaymentMethod.setStripePaymentMethodID(stripePaymentMethod.getId());

        paymentMethodRepository.save(newPaymentMethod);
        log.info("Payment method with Stripe ID {} is saved to database", stripePaymentMethod.getId());
    }

    @Transactional
    public void editPaymentMethod(UUID userID, UUID targetPaymentMethodID, EditPaymentMethodRequest editPaymentMethodRequest, String idempotencyKey) {

        if (null == targetPaymentMethodID) {
            log.error("Error editing payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        // 1. Validate new changes
        paymentMethodValidator.validatePaymentMethodForEdit(userID, editPaymentMethodRequest);

        PaymentMethod currentPaymentMethod = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetPaymentMethodID)
                .orElseThrow(() -> new IllegalArgumentException("Target payment method not found in database record storage"));

        // 2. If no changes, skip updates
        if (currentPaymentMethod.getCardHolderName().trim().equals(editPaymentMethodRequest.getCardHolderName().trim())
                && Integer.parseInt(currentPaymentMethod.getExpirationMonth()) == Integer.parseInt(editPaymentMethodRequest.getExpirationMonth())
                && Integer.parseInt(currentPaymentMethod.getExpirationYear()) == Integer.parseInt(editPaymentMethodRequest.getExpirationYear())) {
            log.info("No changes, skipping updating of Payment Method {}", targetPaymentMethodID);
            return;
        }

        // 3. Idempotency guard against duplicate client submissions (e.g. double-click, retried request)
        if (isDuplicateSubmission(userID, PAYMENT_METHOD_EDITED, idempotencyKey)) {
            log.info("Duplicate edit request for Payment Method {} (idempotencyKey = {}), skipping", targetPaymentMethodID, idempotencyKey);
            return;
        }

        // 4. Save to database immediately - UI reflects the change without depending on Stripe
        currentPaymentMethod.setCardHolderName(editPaymentMethodRequest.getCardHolderName());
        currentPaymentMethod.setExpirationMonth(editPaymentMethodRequest.getExpirationMonth());
        currentPaymentMethod.setExpirationYear(editPaymentMethodRequest.getExpirationYear());
        paymentMethodRepository.save(currentPaymentMethod);

        // 5. Queue Stripe sync via outbox
        StripePaymentMethodEditEvent event = new StripePaymentMethodEditEvent();
        event.setEventID(UUID.randomUUID().toString());
        event.setSchemaVersion(1);
        event.setUserID(userID);
        event.setPaymentMethodID(currentPaymentMethod.getPaymentMethodID());
        event.setStripePaymentMethodID(currentPaymentMethod.getStripePaymentMethodID());
        event.setCardHolderName(editPaymentMethodRequest.getCardHolderName());
        event.setExpirationMonth(editPaymentMethodRequest.getExpirationMonth());
        event.setExpirationYear(editPaymentMethodRequest.getExpirationYear());

        writeOutboxEvent(userID, PAYMENT_METHOD_EDITED, STRIPE_PM_SYNC_EVENTS, event, idempotencyKey);
        log.info("Payment method {} edited in database, Stripe sync event {} queued for userID {}", targetPaymentMethodID, event.getEventID(), userID);
    }

    @Transactional
    public void deletePaymentMethod(UUID userID, UUID targetPaymentMethodID, String idempotencyKey) {
        // 1. Validation checks
        if (null == targetPaymentMethodID) {
            log.error("Error deleting payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        PaymentMethod paymentMethod = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetPaymentMethodID)
                .orElseThrow(() -> {
                    log.error("Payment method with ID: {} not found for user: {}", targetPaymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found");
                });

        // 2. Idempotency guard against duplicate client submissions (e.g. double-click, retried request)
        if (isDuplicateSubmission(userID, PAYMENT_METHOD_DETACHED, idempotencyKey)) {
            log.info("Duplicate delete request for Payment Method {} (idempotencyKey = {}), skipping", targetPaymentMethodID, idempotencyKey);
            return;
        }

        // 3. Soft-delete in database - disappears from UI immediately without depending on Stripe
        paymentMethod.setIsActive(false);
        paymentMethodRepository.save(paymentMethod);

        // 4. Create Outbox Event - includes outbox event's own ID so consumer can dedupe via ProcessedEvent
        StripePaymentMethodDetachEvent event = new StripePaymentMethodDetachEvent();
        event.setEventID(UUID.randomUUID().toString());
        event.setSchemaVersion(1);
        event.setUserID(userID);
        event.setStripePaymentMethodID(paymentMethod.getStripePaymentMethodID());
        event.setPaymentMethodID(paymentMethod.getPaymentMethodID());

        writeOutboxEvent(userID, PAYMENT_METHOD_DETACHED, STRIPE_DETACH_PM_EVENTS, event, idempotencyKey);
        log.info("Payment Method {} soft-deleted, detach event {} queued for userID {}", targetPaymentMethodID, event.getEventID(), userID);
    }

    private void checkAndUpdateDefaultAddress(UUID userID, List<Address> addressList, AddressDTO addressDTO) {
        Address currentDefault = null;

        for (Address existing : addressList) {
            // Check for duplicates
            if (addressValidator.isDuplicate(addressDTO, existing)) {
                log.error("Address already exists for user: {}", userID);
                throw new DuplicateAddressException("Address already exists");
            }

            // Track current default Address
            if (addressDTO.getIsDefault() && existing.getIsDefault()) {
                currentDefault = existing;
            }
        }

        // Set current default to false if needed
        if (null != currentDefault) {
            currentDefault.setIsDefault(false);
            addressRepository.save(currentDefault);
        }
    }

    private void updateDefaultAddress(UUID userID, Address newDefaultAddress) {
        addressRepository.unsetDefaultForUser(userID);

        newDefaultAddress.setIsDefault(true);
        addressRepository.save(newDefaultAddress);
    }

    @Transactional
    public void attachAddressToPaymentMethod(UUID userID, AttachAddressToPaymentMethodRequest addressToPaymentMethodRequest, String idempotencyKey) {
        UUID addressID = addressToPaymentMethodRequest.getAddressID();
        UUID paymentMethodID = addressToPaymentMethodRequest.getPaymentMethodID();

        // 1. Validation checks
        if (null == addressID) {
            log.error("Error attaching address to payment method for user {}: address ID is not given", userID);
            throw new InputValidationException("Address ID is not given");
        }

        Address address = addressRepository.getAddressByUserIDAndID(userID, addressID)
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", addressID, userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        if (null == paymentMethodID) {
            log.error("Error attaching address to payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        PaymentMethod paymentMethod = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)
                .orElseThrow(() -> {
                    log.error("Payment method with ID: {} not found for user: {}", paymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found");
                });

        // 2. Skip if billing address is already the target one
        if (null != paymentMethod.getBillingAddressID() && paymentMethod.getBillingAddressID().equals(addressID)) {
            log.info("Address: {} is already set for Payment method: {}", addressID, paymentMethod.getPaymentMethodID());
            return;
        }

        // 3. Idempotency guard against duplicate client submissions (e.g. double-click, retried request)
        if (isDuplicateSubmission(userID, PAYMENT_METHOD_ADDRESS_ATTACHED, idempotencyKey)) {
            log.info("Duplicate attach-address request for Payment Method {} (idempotencyKey = {}), skipping", paymentMethodID, idempotencyKey);
            return;
        }

        // 4. Save to database immediately - UI reflects the change without depending on Stripe
        paymentMethod.setBillingAddressID(addressID);
        paymentMethodRepository.save(paymentMethod);

        // 5. Queue Stripe sync via outbox
        StripePaymentMethodAddressAttachedEvent event = new StripePaymentMethodAddressAttachedEvent();
        event.setEventID(UUID.randomUUID().toString());
        event.setSchemaVersion(1);
        event.setUserID(userID);
        event.setPaymentMethodID(paymentMethod.getPaymentMethodID());
        event.setStripePaymentMethodID(paymentMethod.getStripePaymentMethodID());
        event.setAddressLine(address.getAddressLine());
        event.setZipCode(address.getZipCode());
        event.setCountry(address.getCountry());

        writeOutboxEvent(userID, PAYMENT_METHOD_ADDRESS_ATTACHED, STRIPE_PM_SYNC_EVENTS, event, idempotencyKey);
        log.info("Address {} attached to Payment method {} in database, Stripe sync event {} queued for userID {}",
                addressID, paymentMethodID, event.getEventID(), userID);
    }

    @Transactional
    public void setAsDefault(UUID userID, String mode, UUID savedItemID, String idempotencyKey) {
        // 1. Validation checks
        if (null == savedItemID) {
            log.error("Error setting default saved item for user {}: saved item ID is not given", userID);
            throw new InputValidationException("Saved item ID is not given");
        }

        if (ADDRESS.equals(mode)) {
            // 2a. Check if Address exist for user
            Address addressToSetAsDefault = addressRepository.getAddressByUserIDAndID(userID, savedItemID)
                    .orElseThrow(() -> {
                        log.error("Address with ID: {} not found for user: {}", savedItemID, userID);
                        return new SavedItemNotFoundException("Address not found");
                    });

            // 3a. Skip if already default
            if (Boolean.TRUE.equals(addressToSetAsDefault.getIsDefault())) {
                log.info("Address with ID: {} already default for user: {}", savedItemID, userID);
                return;
            }

            // 4a. Save to database
            updateDefaultAddress(userID, addressToSetAsDefault);
        }

        if (PAYMENT_METHOD.equals(mode)) {
            // 2b. Check if Payment Method exist for user
            PaymentMethod paymentMethodToSetAsDefault = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, savedItemID)
                    .orElseThrow(() -> {
                        log.error("Payment method not found with ID: {} for user {}", savedItemID, userID);
                        return new SavedItemNotFoundException("Payment method not found with ID: " + savedItemID);
                    });

            // 3b. Skip if already default
            if (Boolean.TRUE.equals(paymentMethodToSetAsDefault.getIsDefault())) {
                log.info("Payment method with ID: {} already default for user: {}", savedItemID, userID);
                return;
            }

            // 4b. Idempotency guard against duplicate client submissions (e.g. double-click, retried request)
            if (isDuplicateSubmission(userID, PAYMENT_METHOD_SET_DEFAULT, idempotencyKey)) {
                log.info("Duplicate set-default request for Payment Method {} (idempotencyKey = {}), skipping", savedItemID, idempotencyKey);
                return;
            }

            // 5b. Save to database immediately - UI reflects the change without depending on Stripe
            paymentMethodRepository.unsetDefaultForUser(userID);
            paymentMethodToSetAsDefault.setIsDefault(true);
            paymentMethodRepository.save(paymentMethodToSetAsDefault);

            // 6b. Queue Stripe sync via outbox
            StripePaymentMethodSetDefaultEvent event = new StripePaymentMethodSetDefaultEvent();
            event.setEventID(UUID.randomUUID().toString());
            event.setSchemaVersion(1);
            event.setUserID(userID);
            event.setStripeCustomerID(paymentMethodToSetAsDefault.getStripeCustomerID());
            event.setStripePaymentMethodID(paymentMethodToSetAsDefault.getStripePaymentMethodID());

            writeOutboxEvent(userID, PAYMENT_METHOD_SET_DEFAULT, STRIPE_PM_SYNC_EVENTS, event, idempotencyKey);
            log.info("Payment method {} set as default in database, Stripe sync event {} queued for userID {}", savedItemID, event.getEventID(), userID);
        }
    }

    private boolean isDuplicateSubmission(UUID userID, String eventType, String idempotencyKey) {
        return Boolean.TRUE.equals(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), eventType, idempotencyKey));
    }

    private void writeOutboxEvent(UUID userID, String eventType, String topic, BaseEvent event, String idempotencyKey) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateID(String.valueOf(userID));
            outboxEvent.setEventType(eventType);
            outboxEvent.setTopic(topic);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setIdempotencyKey(idempotencyKey);
            outboxEvent.setStatus(PENDING);
            outboxEvent.setCreatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
            outboxEvent.setPublished(false);
            outboxEvent.setRetryCount(0);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            log.error("[SavedItemsService] Failed to write outbox event {} for userID {}", eventType, userID, ex);
            throw new OutboxEventCreationException("Failed to write outbox event: " + ex.getMessage());
        }
    }

}
