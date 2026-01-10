package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
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
        List<Address> addressesFromUserList = addressRepository.getTop5AddressesByUserID(userID);

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
                    log.error("Address not found with ID: {} for user {}", addressID, userID);
                    return new SavedItemNotFoundException("Address not found with ID: " + addressID);
                });

        return addressMapper.toDTO(address);
    }

    public void addAddress(UUID userID, AddressDTO addressDTO) {
        addressValidator.validateAndSanitizeAddress(userID, addressDTO);

        List<Address> addressesFromUserList = addressRepository.getTop5AddressesByUserID(userID);

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

        List<Address> addressesFromUserList = addressRepository.getTop5AddressesByUserID(userID);

        Address addressToUpdate = addressesFromUserList.stream()
                .filter(address -> address.getAddressID().equals(targetAddressID))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Address not found with ID: {} for user {}", targetAddressID, userID);
                    return new SavedItemNotFoundException("Address not found with ID: " + targetAddressID);
                });

        checkAndUpdateDefaultAddress(userID, addressesFromUserList, addressDTO);

        addressMapper.updateEntityFromDTO(addressDTO, addressToUpdate);

        addressRepository.save(addressToUpdate);
    }

    public List<PaymentMethodDTO> getAllPaymentMethodsForUser(UUID userID) {
        List<PaymentMethod> paymentMethodFromUserList = paymentMethodRepository.getTop5PaymentMethodsByUserID(userID);

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
                    log.error("Payment method not found with ID: {} for user {}", paymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found with ID: " + paymentMethodID);
                });

        return paymentMethodMapper.toDTO(paymentMethod);
    }

    public void addPaymentMethod(UUID userID, PaymentMethodDTO paymentMethodDTO) {

        paymentMethodValidator.validateAndSanitizePaymentMethod(userID, paymentMethodDTO);

        List<PaymentMethod> paymentMethodsFromUserList = paymentMethodRepository.getTop5PaymentMethodsByUserID(userID);

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
    public void editPaymentMethod(UUID userID, UUID targetPaymentMethodID, PaymentMethodDTO paymentMethodDTO) {

        if (null == targetPaymentMethodID) {
            log.error("Error editing payment method for user {}: payment method ID is not given", userID);
            throw new InputValidationException("Payment method ID is not given");
        }

        paymentMethodValidator.validatePaymentMethod(userID, paymentMethodDTO);

        List<PaymentMethod> paymentMethodsFromUserList = paymentMethodRepository.getTop5PaymentMethodsByUserID(userID);

        PaymentMethod paymentMethodToUpdate = paymentMethodsFromUserList.stream()
                .filter(paymentMethod -> paymentMethod.getPaymentMethodID().equals(targetPaymentMethodID))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Payment method not found with ID: {} for user {}", targetPaymentMethodID, userID);
                    return new SavedItemNotFoundException("Payment method not found with ID: " + targetPaymentMethodID);
                });

        checkAndUpdateDefaultPaymentMethod(userID, paymentMethodsFromUserList, paymentMethodDTO);

        paymentMethodMapper.updateEntityFromDTO(paymentMethodDTO, paymentMethodToUpdate);

        paymentMethodRepository.save(paymentMethodToUpdate);
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
}
