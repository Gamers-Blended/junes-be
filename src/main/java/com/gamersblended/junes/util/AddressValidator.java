package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.model.Address;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.gamersblended.junes.constant.ValidationConstants.*;
import static com.gamersblended.junes.util.InputValidatorUtils.sanitizeString;

@Slf4j
@Component
public class AddressValidator {

    private static final Set<String> VALID_COUNTRY_CODES_ALPHA2;
    private static final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    static {
        // Get all ISO 3166-1 alpha-2 country codes
        VALID_COUNTRY_CODES_ALPHA2 = Arrays.stream(Locale.getISOCountries())
                .collect(Collectors.toSet());
    }

    public void validateAndSanitizeAddress(UUID userID, AddressDTO addressDTO) {
        addressDTO.setFullName(sanitizeString(addressDTO.getFullName()));
        addressDTO.setAddressLine(sanitizeString(addressDTO.getAddressLine()));
        addressDTO.setUnitNumber(sanitizeString(addressDTO.getUnitNumber()));
        addressDTO.setCountry(sanitizeString(addressDTO.getCountry()));
        addressDTO.setZipCode(sanitizeString(addressDTO.getZipCode()));
        addressDTO.setPhoneNumber(sanitizeString(addressDTO.getPhoneNumber()));

        validateAddress(userID, addressDTO);
    }

    public void validateAddress(UUID userID, AddressDTO addressDTO) {
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
        String normalizedPhoneNumber = validatePhoneNumber(addressDTO.getPhoneNumber(), addressDTO.getCountry(), userID);
        addressDTO.setPhoneNumber(normalizedPhoneNumber);
    }

    public void validateCountry(String country, UUID userID) {
        if (null == country || country.isBlank()) {
            log.error("Error adding new address for user {}: country is not given", userID);
            throw new InputValidationException("Country is not given");
        }

        if (country.length() > COUNTRY_MAX_LENGTH) {
            log.error("Error adding new address for user {}: country exceeds maximum length of {} characters", userID, COUNTRY_MAX_LENGTH);
            throw new InputValidationException("Country exceeds maximum length of " + COUNTRY_MAX_LENGTH + " characters");
        }

        // Convert to uppercase for case-insensitive comparison
        String countryUpper = country.toUpperCase();

        // Check if valid alpha-2 (2 characters) code
        boolean isValidAlpha2 = countryUpper.length() == 2 &&
                VALID_COUNTRY_CODES_ALPHA2.contains(countryUpper);

        if (!isValidAlpha2) {
            log.error("Invalid country code: {}", country);
            throw new InputValidationException("Invalid country code. Must be a valid ISO 3166-1 alpha-2 code");
        }

    }

    // Normalize phone number to #.164 format
    public String validatePhoneNumber(String phoneNumber, String countryCode, UUID userID) {
        if (null == phoneNumber || phoneNumber.isBlank()) {
            log.error("Error adding new address for user {}: phone number is not given", userID);
            throw new InputValidationException("Phone number is not given");
        }

        if (phoneNumber.length() > PHONE_NUMBER_MAX_LENGTH) {
            log.error("Error adding new address for user {}: phone number exceeds maximum length of {} characters", userID, PHONE_NUMBER_MAX_LENGTH);
            throw new InputValidationException("Phone number exceeds maximum length of " + PHONE_NUMBER_MAX_LENGTH + " characters");
        }

        try {
            // Parse phone number with country context
            Phonenumber.PhoneNumber parsedNumber = phoneUtil.parse(phoneNumber, countryCode);

            if (!phoneUtil.isValidNumber(parsedNumber)) {
                log.error("Error adding new address for user {}: invalid phone number for country: {}", userID, countryCode);
                throw new InputValidationException("Invalid phone number for country: " + countryCode);
            }

            if (!phoneUtil.isPossibleNumber(parsedNumber)) {
                log.error("Error adding new address for user {}: phone number has invalid length", userID);
                throw new InputValidationException("Phone number has invalid length");
            }

            // Format to E.164 international format: +14155552671
            String normalizedNumber = phoneUtil.format(parsedNumber,
                    PhoneNumberUtil.PhoneNumberFormat.E164);

            if (normalizedNumber.length() > PHONE_NUMBER_MAX_LENGTH) {
                log.error("Error adding new address for user {}: phone number exceeds maximum length of {} characters", userID, PHONE_NUMBER_MAX_LENGTH);
                throw new InputValidationException("Phone number exceeds maximum length of " + PHONE_NUMBER_MAX_LENGTH + " characters");
            }

            return normalizedNumber;

        } catch (NumberParseException ex) {
            log.error("Error adding new address for user {}: invalid phone number format: ", userID, ex);
            throw new InputValidationException("Invalid phone number format: " + ex.getMessage());
        }
    }

    public boolean isDuplicate(AddressDTO addressDTO, Address address) {
        return Objects.equals(addressDTO.getAddressLine(), address.getAddressLine()) &&
                Objects.equals(addressDTO.getUnitNumber(), address.getUnitNumber()) &&
                Objects.equals(addressDTO.getCountry(), address.getCountry()) &&
                Objects.equals(addressDTO.getZipCode(), address.getZipCode()) &&
                Objects.equals(addressDTO.getPhoneNumber(), address.getPhoneNumber());
    }
}
