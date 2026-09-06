package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.model.Address;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressValidatorTest {

    private final AddressValidator validator = new AddressValidator();
    private final UUID userID = UUID.randomUUID();

    private AddressDTO validAddress() {
        AddressDTO dto = new AddressDTO();
        dto.setFullName("John Doe");
        dto.setAddressLine("123 Main St");
        dto.setUnitNumber("01-23");
        dto.setCountry("US");
        dto.setZipCode("94105");
        dto.setPhoneNumber("4155552671");
        return dto;
    }

    @Test
    void validateAddress_acceptsValidAddress() {
        AddressDTO dto = validAddress();

        assertThatCode(() -> validator.validateAddress(userID, dto)).doesNotThrowAnyException();
    }

    @Test
    void validateAddress_normalizesPhoneNumberToE164() {
        AddressDTO dto = validAddress();

        validator.validateAddress(userID, dto);

        assertThat(dto.getPhoneNumber()).isEqualTo("+14155552671");
    }

    @Test
    void validateAddress_rejectsNullFullName() {
        AddressDTO dto = validAddress();
        dto.setFullName(null);

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Full name is not given");
    }

    @Test
    void validateAddress_rejectsBlankFullName() {
        AddressDTO dto = validAddress();
        dto.setFullName("   ");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Full name is not given");
    }

    @Test
    void validateAddress_rejectsFullNameExceedingMaxLength() {
        AddressDTO dto = validAddress();
        dto.setFullName("A".repeat(101));

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateAddress_rejectsFullNameWithInvalidCharacters() {
        AddressDTO dto = validAddress();
        dto.setFullName("John123");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("only letters, spaces, hyphens and apostrophes");
    }

    @Test
    void validateAddress_acceptsFullNameWithHyphenAndApostrophe() {
        AddressDTO dto = validAddress();
        dto.setFullName("Mary-Jane O'Brien");

        assertThatCode(() -> validator.validateAddress(userID, dto)).doesNotThrowAnyException();
    }

    @Test
    void validateAddress_rejectsNullAddressLine() {
        AddressDTO dto = validAddress();
        dto.setAddressLine(null);

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Address line is not given");
    }

    @Test
    void validateAddress_rejectsAddressLineExceedingMaxLength() {
        AddressDTO dto = validAddress();
        dto.setAddressLine("A".repeat(256));

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateAddress_acceptsNullUnitNumber() {
        AddressDTO dto = validAddress();
        dto.setUnitNumber(null);

        assertThatCode(() -> validator.validateAddress(userID, dto)).doesNotThrowAnyException();
    }

    @Test
    void validateAddress_rejectsUnitNumberExceedingMaxLength() {
        AddressDTO dto = validAddress();
        dto.setUnitNumber("A".repeat(51));

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateAddress_rejectsUnitNumberWithInvalidCharacters() {
        AddressDTO dto = validAddress();
        dto.setUnitNumber("#01-23!");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("only letters, number, spaces, hyphens and forward slashes");
    }

    @Test
    void validateAddress_rejectsNullZipCode() {
        AddressDTO dto = validAddress();
        dto.setZipCode(null);

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Zip code is not given");
    }

    @Test
    void validateAddress_rejectsZipCodeExceedingMaxLength() {
        AddressDTO dto = validAddress();
        dto.setZipCode("1".repeat(21));

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateAddress_rejectsZipCodeWithInvalidCharacters() {
        AddressDTO dto = validAddress();
        dto.setZipCode("94105!");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("only letters, number, spaces, hyphens and forward slashes");
    }

    @Test
    void validateAddress_rejectsInvalidCountryCode() {
        AddressDTO dto = validAddress();
        dto.setCountry("USA");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("Invalid country code");
    }

    @Test
    void validateAddress_rejectsInvalidPhoneNumberForCountry() {
        AddressDTO dto = validAddress();
        dto.setPhoneNumber("123");

        assertThatThrownBy(() -> validator.validateAddress(userID, dto))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void validateAndSanitizeAddress_trimsAndNormalizesWhitespaceBeforeValidating() {
        AddressDTO dto = validAddress();
        dto.setFullName("  John   Doe  ");

        validator.validateAndSanitizeAddress(userID, dto);

        assertThat(dto.getFullName()).isEqualTo("John Doe");
    }

    @Test
    void validateCountry_acceptsLowercaseAlpha2Code() {
        assertThatCode(() -> validator.validateCountry("us", userID)).doesNotThrowAnyException();
    }

    @Test
    void validateCountry_rejectsNullCountry() {
        assertThatThrownBy(() -> validator.validateCountry(null, userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Country is not given");
    }

    @Test
    void validateCountry_rejectsCountryExceedingMaxLength() {
        String tooLongCountry = "A".repeat(51);

        assertThatThrownBy(() -> validator.validateCountry(tooLongCountry, userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateCountry_rejectsUnknownAlpha2Code() {
        assertThatThrownBy(() -> validator.validateCountry("ZZ", userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("Invalid country code");
    }

    @Test
    void validateCountry_rejectsNonTwoCharacterCode() {
        assertThatThrownBy(() -> validator.validateCountry("USA", userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("Invalid country code");
    }

    @Test
    void validatePhoneNumber_rejectsNullPhoneNumber() {
        assertThatThrownBy(() -> validator.validatePhoneNumber(null, "US", userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Phone number is not given");
    }

    @Test
    void validatePhoneNumber_rejectsPhoneNumberExceedingMaxLength() {
        String tooLongPhoneNumber = "1".repeat(21);

        assertThatThrownBy(() -> validator.validatePhoneNumber(tooLongPhoneNumber, "US", userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validatePhoneNumber_rejectsUnparsablePhoneNumber() {
        assertThatThrownBy(() -> validator.validatePhoneNumber("not-a-number", "US", userID))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("Invalid phone number format");
    }

    @Test
    void validatePhoneNumber_rejectsInvalidNumberForCountry() {
        assertThatThrownBy(() -> validator.validatePhoneNumber("123", "US", userID))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void validatePhoneNumber_normalizesValidNumberToE164() {
        String normalized = validator.validatePhoneNumber("(415) 555-2671", "US", userID);

        assertThat(normalized).isEqualTo("+14155552671");
    }

    @Test
    void isDuplicate_returnsTrueWhenAllFieldsMatch() {
        AddressDTO dto = validAddress();
        Address address = new Address();
        address.setFullName(dto.getFullName());
        address.setAddressLine(dto.getAddressLine());
        address.setUnitNumber(dto.getUnitNumber());
        address.setCountry(dto.getCountry());
        address.setZipCode(dto.getZipCode());
        address.setPhoneNumber(dto.getPhoneNumber());

        assertThat(validator.isDuplicate(dto, address)).isTrue();
    }

    @Test
    void isDuplicate_returnsFalseWhenAddressLineDiffers() {
        AddressDTO dto = validAddress();
        Address address = new Address();
        address.setFullName(dto.getFullName());
        address.setAddressLine("456 Other St");
        address.setUnitNumber(dto.getUnitNumber());
        address.setCountry(dto.getCountry());
        address.setZipCode(dto.getZipCode());
        address.setPhoneNumber(dto.getPhoneNumber());

        assertThat(validator.isDuplicate(dto, address)).isFalse();
    }
}
