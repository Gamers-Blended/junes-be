package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.SavedItemLimitExceededException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.mapper.PaymentMethodMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.MAX_NUMBER_OF_SAVED_ITEMS;
import static com.gamersblended.junes.constant.ValidationConstants.*;
import static com.gamersblended.junes.util.AddressValidator.validateCountry;
import static com.gamersblended.junes.util.AddressValidator.validatePhoneNumber;
import static com.gamersblended.junes.util.InputValidatorUtils.sanitizeString;

@Slf4j
@Service
public class SavedItemsService {

    private final AddressRepository addressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AddressMapper addressMapper;
    private final PaymentMethodMapper paymentMethodMapper;

    public SavedItemsService(AddressRepository addressRepository, AddressMapper addressMapper,
                             PaymentMethodRepository paymentMethodRepository, PaymentMethodMapper paymentMethodMapper) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodMapper = paymentMethodMapper;
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

    public String addAddress(UUID userID, AddressDTO addressDTO) {
        validateAndSanitizeAddress(userID, addressDTO);

        List<Address> addressesFromUserList = addressRepository.getTop5AddressesByUserID(userID);

        // Cannot exceed limit
        if (addressesFromUserList.size() == MAX_NUMBER_OF_SAVED_ITEMS) {
            log.info("User {} has reached the maximum of {} saved addresses", userID, MAX_NUMBER_OF_SAVED_ITEMS);
            throw new SavedItemLimitExceededException("User " + userID + " has reached the maximum of " + MAX_NUMBER_OF_SAVED_ITEMS + " saved addresses");
        }

        // TODO Check for no duplicate Addresses
        return "";
    }

    private void validateAndSanitizeAddress(UUID userID, AddressDTO addressDTO) {
        addressDTO.setFullName(sanitizeString(addressDTO.getFullName()));
        addressDTO.setAddressLine(sanitizeString(addressDTO.getAddressLine()));
        addressDTO.setUnitNumber(sanitizeString(addressDTO.getUnitNumber()));
        addressDTO.setCountry(sanitizeString(addressDTO.getFullName()));
        addressDTO.setZipCode(sanitizeString(addressDTO.getZipCode()));
        addressDTO.setPhoneNumber(sanitizeString(addressDTO.getPhoneNumber()));

        validateAddress(userID, addressDTO);
    }

    private void validateAddress(UUID userID, AddressDTO addressDTO) {
        // Full name
        if (null == addressDTO.getFullName() || addressDTO.getFullName().isBlank()) {
            log.error("Error adding new address for user {}: full name is not given", userID);
            throw new InputValidationException("Full name is not given");
        }

        if (addressDTO.getFullName().length() > FULL_NAME_MAX_LENGTH) {
            log.error("Error adding new address for user {}: full name exceeds maximum length of {} characters", userID, FULL_NAME_MAX_LENGTH);
            throw new InputValidationException("Full name exceeds maximum length of " + FULL_NAME_MAX_LENGTH + " characters");
        }

        if (!addressDTO.getFullName().matches("^[a-zA-Z\\s'-]+$")) {
            log.error("Error adding new address for user {}: full name should contain only letters, spaces, hyphens and apostrophes", userID);
            throw new InputValidationException("Full name should contain only letters, spaces, hyphens and apostrophes");
        }

        // Address line
        if (null == addressDTO.getAddressLine() || addressDTO.getAddressLine().isBlank()) {
            log.error("Error adding new address for user {}: address line is not given", userID);
            throw new InputValidationException("Address line is not given");
        }

        if (addressDTO.getAddressLine().length() > ADDRESS_LINE_MAX_LENGTH) {
            log.error("Error adding new address for user {}: address line exceeds maximum length of {} characters", userID, ADDRESS_LINE_MAX_LENGTH);
            throw new InputValidationException("Address line exceeds maximum length of " + ADDRESS_LINE_MAX_LENGTH + " characters");
        }

        // Unit number
        if (null != addressDTO.getUnitNumber()) {
            if (addressDTO.getUnitNumber().length() > UNIT_NUMBER_MAX_LENGTH) {
                log.error("Error adding new address for user {}: unit number exceeds maximum length of {} characters", userID, UNIT_NUMBER_MAX_LENGTH);
                throw new InputValidationException("Unit number exceeds maximum length of " + UNIT_NUMBER_MAX_LENGTH + " characters");
            }

            if (!addressDTO.getUnitNumber().matches("^[a-zA-Z0-9\\s\\/-]+$")) {
                log.error("Error adding new address for user {}: unit number should contain only letters, number, spaces, hyphens and forward slashes", userID);
                throw new InputValidationException("Unit number should contain only letters, number, spaces, hyphens and forward slashes");
            }
        }

        // Country
        validateCountry(addressDTO.getCountry(), userID);

        // Zip code
        if (null == addressDTO.getZipCode() || addressDTO.getZipCode().isBlank()) {
            log.error("Error adding new address for user {}: zip code is not given", userID);
            throw new InputValidationException("Zip code is not given");
        }

        if (addressDTO.getZipCode().length() > ZIP_CODE_MAX_LENGTH) {
            log.error("Error adding new address for user {}: zip code exceeds maximum length of {} characters", userID, ZIP_CODE_MAX_LENGTH);
            throw new InputValidationException("Zip code exceeds maximum length of " + ZIP_CODE_MAX_LENGTH + " characters");
        }

        if (!addressDTO.getZipCode().matches("^[a-zA-Z0-9\\s\\/-]+$")) {
            log.error("Error adding new address for user {}: zip code should contain only letters, number, spaces, hyphens and forward slashes", userID);
            throw new InputValidationException("Zip code should contain only letters, number, spaces, hyphens and forward slashes");
        }

        // Phone number
        validatePhoneNumber(addressDTO.getPhoneNumber(), addressDTO.getCountry(), userID);
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
}
