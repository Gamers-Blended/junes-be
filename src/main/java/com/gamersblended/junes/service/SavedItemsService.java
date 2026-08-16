package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.AddPaymentMethodRequest;
import com.gamersblended.junes.dto.request.AttachAddressToPaymentMethodRequest;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.mapper.PaymentMethodMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.MAX_NUMBER_OF_SAVED_ITEMS;

@Slf4j
@Service
public class SavedItemsService {

    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final AddressValidator addressValidator;
    private final PaymentMethodValidator paymentMethodValidator;
    private final StripeClient stripeClient;

    private static final String ADDRESS = "address";
    private static final String PAYMENT_METHOD = "payment_method";

    public SavedItemsService(AddressRepository addressRepository, AddressMapper addressMapper,
                             PaymentMethodRepository paymentMethodRepository, PaymentMethodMapper paymentMethodMapper,
                             AddressValidator addressValidator, PaymentMethodValidator paymentMethodValidator,
                             UserRepository userRepository, StripeClient stripeClient) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodMapper = paymentMethodMapper;
        this.addressValidator = addressValidator;
        this.paymentMethodValidator = paymentMethodValidator;
        this.userRepository = userRepository;
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

        address.setDeletedOn(LocalDateTime.now());
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
            // Set all user's Payment Method(s) to not default
            paymentMethodRepository.resetDefaultStatusForUser(userID);

            CustomerUpdateParams customerParams = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(request.getStripePaymentMethodID())
                                    .build()
                    )
                    .build();

            stripeClient.v1().customers().update(stripeCustomerID, customerParams);
            log.info("Stripe customer profile default payment method updated for Stripe customer ID: {}", stripeCustomerID);
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
    public void editPaymentMethod(UUID userID, UUID targetPaymentMethodID, EditPaymentMethodRequest editPaymentMethodRequest, String idempotencyKey) throws StripeException {

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

        // 3. Update billing data on Stripe cloud
        PaymentMethodUpdateParams updateParams = PaymentMethodUpdateParams.builder()
                .setBillingDetails(
                        PaymentMethodUpdateParams.BillingDetails.builder()
                                .setName(editPaymentMethodRequest.getCardHolderName())
                                .build()
                )
                .setCard(PaymentMethodUpdateParams.Card.builder()
                        .setExpMonth(Long.parseLong(editPaymentMethodRequest.getExpirationMonth()))
                        .setExpYear(Long.parseLong(editPaymentMethodRequest.getExpirationYear()))
                        .build())
                .build();

        // 4. Update billing data on Stripe cloud
        RequestOptions updateOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey + "-stripe-pm-edit")
                .build();

        stripeClient.v1().paymentMethods().update(currentPaymentMethod.getStripePaymentMethodID(), updateParams, updateOptions);

        // 5. Save to database
        currentPaymentMethod.setCardHolderName(editPaymentMethodRequest.getCardHolderName());
        currentPaymentMethod.setExpirationMonth(editPaymentMethodRequest.getExpirationMonth());
        currentPaymentMethod.setExpirationYear(editPaymentMethodRequest.getExpirationYear());
        paymentMethodRepository.save(currentPaymentMethod);
    }

    @Transactional
    public void deletePaymentMethod(UUID userID, UUID targetPaymentMethodID) {
        if (null == targetPaymentMethodID) {
            log.error("Error deleting payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        PaymentMethod paymentMethod = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetPaymentMethodID)
                .orElseThrow(() -> {
                    log.error("Payment method with ID: {} not found for user: {}", targetPaymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found");
                });

        paymentMethodRepository.delete(paymentMethod);
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
    public void attachAddressToPaymentMethod(UUID userID, AttachAddressToPaymentMethodRequest addressToPaymentMethodRequest, String idempotencyKey) throws StripeException {
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

        // 3. Update billing data on Stripe cloud
        PaymentMethodUpdateParams updateParams = PaymentMethodUpdateParams.builder()
                .setBillingDetails(
                        PaymentMethodUpdateParams.BillingDetails.builder()
                                .setAddress(
                                        PaymentMethodUpdateParams.BillingDetails.Address.builder()
                                                .setLine1(address.getAddressLine())
                                                .setPostalCode(address.getZipCode())
                                                .setCountry(address.getCountry())
                                                .build()
                                )
                                .build()
                )
                .build();

        RequestOptions updateOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey + "-stripe-pm-edit-billing-address")
                .build();

        stripeClient.v1().paymentMethods().update(paymentMethod.getStripePaymentMethodID(), updateParams, updateOptions);

        // 4. Save to database
        paymentMethod.setBillingAddressID(addressID);
        paymentMethodRepository.save(paymentMethod);
    }

    @Transactional
    public void setAsDefault(UUID userID, String mode, UUID savedItemID, String idempotencyKey) throws StripeException {
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

            // 4a. Update data on Stripe cloud
            CustomerUpdateParams customerParams = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(paymentMethodToSetAsDefault.getStripePaymentMethodID())
                                    .build()
                    )
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey + "-stripe-pm-set-default")
                    .build();

            stripeClient.v1().customers().update(paymentMethodToSetAsDefault.getStripeCustomerID(), customerParams, requestOptions);
            log.info("Stripe customer profile default payment method updated for Stripe customer ID: {}", paymentMethodToSetAsDefault.getStripeCustomerID());

            // 4a. Save to database
            paymentMethodRepository.unsetDefaultForUser(userID);
            paymentMethodToSetAsDefault.setIsDefault(true);
            paymentMethodRepository.save(paymentMethodToSetAsDefault);
        }
    }

}
