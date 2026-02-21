package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.AttachAddressToPaymentMethodRequest;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.mapper.PaymentMethodMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.util.AddressValidator;
import com.gamersblended.junes.util.PaymentMethodValidator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.MAX_NUMBER_OF_SAVED_ITEMS;

@Slf4j
@Service
public class SavedItemsService {

    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AddressMapper addressMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final AddressValidator addressValidator;
    private final PaymentMethodValidator paymentMethodValidator;

    private static final String ADDRESS = "address";
    private static final String PAYMENT_METHOD = "payment_method";

    public SavedItemsService(AddressRepository addressRepository, AddressMapper addressMapper,
                             PaymentMethodRepository paymentMethodRepository, PaymentMethodMapper paymentMethodMapper,
                             AddressValidator addressValidator, PaymentMethodValidator paymentMethodValidator) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodMapper = paymentMethodMapper;
        this.addressValidator = addressValidator;
        this.paymentMethodValidator = paymentMethodValidator;
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

    public void addPaymentMethod(UUID userID, PaymentMethodDTO paymentMethodDTO) {

        paymentMethodValidator.validateAndSanitizePaymentMethod(userID, paymentMethodDTO);

        List<PaymentMethod> paymentMethodsFromUserList = paymentMethodRepository.getPaymentMethodsByUserID(userID);

        // Cannot exceed limit
        if (paymentMethodsFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved payment methods", userID, MAX_NUMBER_OF_SAVED_ITEMS);
            throw new SavedItemLimitExceededException("User " + userID + " has reached the maximum of " + MAX_NUMBER_OF_SAVED_ITEMS + " saved payment methods");
        }

        checkAndUpdateDefaultPaymentMethod(userID, paymentMethodsFromUserList, paymentMethodDTO);

        PaymentMethod newPaymentMethod = paymentMethodMapper.toEntity(paymentMethodDTO);
        newPaymentMethod.setUserID(userID);
        newPaymentMethod.setIsActive(true);
        paymentMethodRepository.save(newPaymentMethod);
    }

    @Transactional
    public void editPaymentMethod(UUID userID, UUID targetPaymentMethodID, EditPaymentMethodRequest editPaymentMethodRequest) {

        if (null == targetPaymentMethodID) {
            log.error("Error editing payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        List<PaymentMethod> paymentMethodsFromUserList = paymentMethodRepository.getPaymentMethodsByUserID(userID);

        PaymentMethod paymentMethodToUpdate = paymentMethodsFromUserList.stream()
                .filter(paymentMethod -> paymentMethod.getPaymentMethodID().equals(targetPaymentMethodID))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Payment method not found with ID: {} for user {}", targetPaymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found with ID: " + targetPaymentMethodID);
                });

        PaymentMethodDTO paymentMethodDTO = getPaymentMethodDTO(editPaymentMethodRequest, paymentMethodToUpdate);

        paymentMethodValidator.validateAndSanitizePaymentMethod(userID, paymentMethodDTO);

        checkAndUpdateDefaultPaymentMethod(userID, paymentMethodsFromUserList, paymentMethodDTO);

        paymentMethodMapper.updateEntityFromDTO(paymentMethodDTO, paymentMethodToUpdate);

        paymentMethodRepository.save(paymentMethodToUpdate);
    }

    private static PaymentMethodDTO getPaymentMethodDTO(EditPaymentMethodRequest editPaymentMethodRequest, PaymentMethod paymentMethodToUpdate) {
        PaymentMethodDTO paymentMethodDTO = new PaymentMethodDTO();
        // Static
        paymentMethodDTO.setPaymentMethodID(paymentMethodToUpdate.getPaymentMethodID());
        paymentMethodDTO.setCardType(paymentMethodToUpdate.getCardType());
        paymentMethodDTO.setCardLastFour(paymentMethodToUpdate.getCardLastFour());
        paymentMethodDTO.setIsDefault(paymentMethodToUpdate.getIsDefault()); // Separate API to set as default
        // Possible changes
        paymentMethodDTO.setCardHolderName(editPaymentMethodRequest.getCardHolderName());
        paymentMethodDTO.setExpirationMonth(editPaymentMethodRequest.getExpirationMonth());
        paymentMethodDTO.setExpirationYear(editPaymentMethodRequest.getExpirationYear());
        paymentMethodDTO.setBillingAddressID(editPaymentMethodRequest.getBillingAddressID());

        return paymentMethodDTO;
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

    private void checkAndUpdateDefaultPaymentMethod(UUID userID, List<PaymentMethod> paymentMethodList, PaymentMethodDTO paymentMethodDTO) {
        PaymentMethod currentDefault = null;

        for (PaymentMethod existing : paymentMethodList) {
            // Check for duplicates
            if (paymentMethodValidator.isDuplicate(paymentMethodDTO, existing)) {
                log.error("Payment method already exists for user: {}", userID);
                throw new DuplicatePaymentMethodException("Payment method already exists");
            }

            // Track current default Payment method
            if (paymentMethodDTO.getIsDefault() && existing.getIsDefault()) {
                currentDefault = existing;
            }
        }

        // Set current default to false if needed
        if (null != currentDefault) {
            currentDefault.setIsDefault(false);
            paymentMethodRepository.save(currentDefault);
        }
    }

    private void updateCurrentDefaultPaymentMethod(UUID userID, PaymentMethod newDefaultPaymentMethod) {
        paymentMethodRepository.unsetDefaultForUser(userID);

        newDefaultPaymentMethod.setIsDefault(true);
        paymentMethodRepository.save(newDefaultPaymentMethod);
    }

    @Transactional
    public void attachAddressToPaymentMethod(UUID userID, AttachAddressToPaymentMethodRequest addressToPaymentMethodRequest) {
        UUID addressID = addressToPaymentMethodRequest.getAddressID();
        UUID paymentMethodID = addressToPaymentMethodRequest.getPaymentMethodID();

        if (null == addressID) {
            log.error("Error attaching address to payment method for user {}: address ID is not given", userID);
            throw new InputValidationException("Address ID is not given");
        }

        addressRepository.getAddressByUserIDAndID(userID, addressID)
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

        if (null != paymentMethod.getBillingAddressID() && paymentMethod.getBillingAddressID().equals(addressID)) {
            log.info("Address: {} is already set for Payment method: {}", addressID, paymentMethod.getPaymentMethodID());
            return;
        }

        paymentMethod.setBillingAddressID(addressID);
        paymentMethodRepository.save(paymentMethod);
    }

    @Transactional
    public void setAsDefault(UUID userID, String mode, UUID savedItemID) {
        if (null == savedItemID) {
            log.error("Error setting default saved item for user {}: saved item ID is not given", userID);
            throw new InputValidationException("Saved item ID is not given");
        }

        if (ADDRESS.equals(mode)) {
            Address addressToSetAsDefault = addressRepository.getAddressByUserIDAndID(userID, savedItemID)
                    .orElseThrow(() -> {
                        log.error("Address with ID: {} not found for user: {}", savedItemID, userID);
                        return new SavedItemNotFoundException("Address not found");
                    });

            if (Boolean.TRUE.equals(addressToSetAsDefault.getIsDefault())) {
                log.info("Address with ID: {} already default for user: {}", savedItemID, userID);
                return;
            }

            updateDefaultAddress(userID, addressToSetAsDefault);
        }

        if (PAYMENT_METHOD.equals(mode)) {
            PaymentMethod paymentMethodToSetAsDefault = paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, savedItemID)
                    .orElseThrow(() -> {
                        log.error("Payment method not found with ID: {} for user {}", savedItemID, userID);
                        return new SavedItemNotFoundException("Payment method not found with ID: " + savedItemID);
                    });

            if (Boolean.TRUE.equals(paymentMethodToSetAsDefault.getIsDefault())) {
                log.info("Payment method with ID: {} already default for user: {}", savedItemID, userID);
                return;
            }

            updateCurrentDefaultPaymentMethod(userID, paymentMethodToSetAsDefault);
        }
    }

}
