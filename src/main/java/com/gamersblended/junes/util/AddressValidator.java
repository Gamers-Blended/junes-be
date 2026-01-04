package com.gamersblended.junes.util;

import com.gamersblended.junes.exception.InputValidationException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.gamersblended.junes.constant.ValidationConstants.COUNTRY_MAX_LENGTH;
import static com.gamersblended.junes.constant.ValidationConstants.PHONE_NUMBER_MAX_LENGTH;

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

    public static void validateCountry(String country, UUID userID) {
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
    public static String validatePhoneNumber(String phoneNumber, String countryCode, UUID userID) {
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
}
