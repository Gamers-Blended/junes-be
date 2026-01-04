package com.gamersblended.junes.util;

import com.gamersblended.junes.exception.InputValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.gamersblended.junes.constant.ValidationConstants.COUNTRY_MAX_LENGTH;

@Slf4j
@Component
public class AddressValidator {

    private static final Set<String> VALID_COUNTRY_CODES_ALPHA2;

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
}
