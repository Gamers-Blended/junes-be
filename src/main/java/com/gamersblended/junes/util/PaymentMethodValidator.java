package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.gamersblended.junes.constant.ValidationConstants.CARD_HOLDER_NAME_MAX_LENGTH;
import static com.gamersblended.junes.util.InputValidatorUtils.sanitizeString;

@Slf4j
@Component
public class PaymentMethodValidator {

    private static final Set<String> VALID_CARD_TYPES = Set.of("VISA", "MASTERCARD", "AMEX", "JCB", "UNIONPAY");
    private static final int EXPIRATION_YEAR_UPPER_BOUND = 20;

    public void validateAndSanitizePaymentMethod(UUID userID, PaymentMethodDTO paymentMethodDTO) {
        paymentMethodDTO.setCardType(sanitizeString(paymentMethodDTO.getCardType()));
        paymentMethodDTO.setCardLastFour(sanitizeString(paymentMethodDTO.getCardLastFour()));
        paymentMethodDTO.setCardHolderName(sanitizeString(paymentMethodDTO.getCardHolderName()));
        paymentMethodDTO.setExpirationMonth(sanitizeString(paymentMethodDTO.getExpirationMonth()));
        paymentMethodDTO.setExpirationYear(sanitizeString(paymentMethodDTO.getExpirationYear()));

        validatePaymentMethod(userID, paymentMethodDTO);
    }

    public void validatePaymentMethod(UUID userID, PaymentMethodDTO paymentMethodDTO) {
        // Card Type
        if (null == paymentMethodDTO.getCardType() || paymentMethodDTO.getCardType().isBlank()) {
            log.error("Error adding new payment method for user {}: card type is not given", userID);
            throw new InputValidationException("Card type is not given");
        }

        if (!VALID_CARD_TYPES.contains(paymentMethodDTO.getCardType())) {
            log.error("Error adding new payment method for user {}: Invalid card type: {}", userID, paymentMethodDTO.getCardType());
            throw new InputValidationException("Invalid card type: " + paymentMethodDTO.getCardType() + ", only these cards are accepted: " + VALID_CARD_TYPES);
        }

        // Last 4 Digits
        if (null == paymentMethodDTO.getCardLastFour() || paymentMethodDTO.getCardLastFour().isBlank()) {
            log.error("Error adding new payment method for user {}: card last 4 digits are not given", userID);
            throw new InputValidationException("Card last 4 digits are not given");
        }

        if (!paymentMethodDTO.getCardLastFour().matches("^\\d{4}$")) {
            log.error("Error adding new payment method for user {}: card last 4 digits: {} must be exactly 4 digits", userID, paymentMethodDTO.getCardLastFour());
            throw new InputValidationException("Card last four digits must be exactly 4 digits");
        }

        // Cardholder Name
        if (null == paymentMethodDTO.getCardHolderName() || paymentMethodDTO.getCardHolderName().isBlank()) {
            log.error("Error adding new payment method for user {}: card holder name is not given", userID);
            throw new InputValidationException("Card holder name is not given");
        }

        if (paymentMethodDTO.getCardHolderName().length() > CARD_HOLDER_NAME_MAX_LENGTH) {
            log.error("Error adding new payment method for user {}: card holder name exceeds maximum length of {} characters", userID, CARD_HOLDER_NAME_MAX_LENGTH);
            throw new InputValidationException("Card holder name exceeds maximum length of " + CARD_HOLDER_NAME_MAX_LENGTH + " characters");
        }

        if (!paymentMethodDTO.getCardHolderName().matches("^[a-zA-Z\\s'-]+$")) {
            log.error("Error adding new payment method for user {}: card holder name should contain only letters, spaces, hyphens and apostrophes", userID);
            throw new InputValidationException("Card holder name should contain only letters, spaces, hyphens and apostrophes");
        }

        // Expiration Month & Year
        int validatedExpirationMonth = validateExpirationMonth(paymentMethodDTO, userID);
        int validatedExpirationYear = validateExpirationYear(paymentMethodDTO.getExpirationYear(), userID);
        YearMonth expiration = YearMonth.of(validatedExpirationYear, validatedExpirationMonth);
        if (expiration.isBefore(YearMonth.now())) {
            log.error("Error adding new payment method for user {}: card has expired, {}", userID, expiration);
            throw new InputValidationException("Card has expired");
        }

    }

    public int validateExpirationMonth(PaymentMethodDTO paymentMethodDTO, UUID userID) {
        String expirationMonth = paymentMethodDTO.getExpirationMonth();

        if (null == expirationMonth || expirationMonth.isBlank()) {
            log.error("Error adding new payment method for user {}: expiration month is not given", userID);
            throw new InputValidationException("Expiration month is not given");
        }

        if (!expirationMonth.matches("^\\d{1,2}$")) {
            log.error("Error adding new payment method for user {}: expiration month: {} is in an invalid format", userID, expirationMonth);
            throw new InputValidationException("Expiration month: " + expirationMonth + " is in an invalid format");
        }

        // Pad single digit and update DTO
        if (expirationMonth.length() == 1) {
            expirationMonth = "0" + expirationMonth;
            paymentMethodDTO.setExpirationMonth(expirationMonth);
        }
        int month = Integer.parseInt(expirationMonth);
        if (month < 1 || month > 12) {
            log.error("Error adding new payment method for user {}: expiration month: {} must be between 01 and 12", userID, expirationMonth);
            throw new InputValidationException("Expiration month: " + expirationMonth + " must be between 01 and 12");
        }

        return month;
    }

    public int validateExpirationYear(String expirationYear, UUID userID) {
        if (null == expirationYear || expirationYear.isBlank()) {
            log.error("Error adding new payment method for user {}: expiration year is not given", userID);
            throw new InputValidationException("Expiration year is not given");
        }

        if (!expirationYear.matches("^\\d{4}$")) {
            log.error("Error adding new payment method for user {}: expiration year: {} must be exactly 4 digits (YYYY format)", userID, expirationYear);
            throw new InputValidationException("Expiration year: " + expirationYear + " must be exactly 4 digits (YYYY format)");
        }

        int year = Integer.parseInt(expirationYear);
        int currentYear = Year.now().getValue();
        int maxYear = currentYear + EXPIRATION_YEAR_UPPER_BOUND;

        if (year < currentYear) {
            log.error("Error adding new payment method for user {}: expiration year: {} cannot be in the past", userID, expirationYear);
            throw new InputValidationException("Expiration year: " + expirationYear + " cannot be in the past");
        }

        if (year > maxYear) {
            log.error("Error adding new payment method for user {}: expiration year: {} cannot be more than {} years in the future", userID, expirationYear, EXPIRATION_YEAR_UPPER_BOUND);
            throw new InputValidationException("Expiration year cannot be more than " + EXPIRATION_YEAR_UPPER_BOUND + " years in the future");
        }

        return year;
    }

    public boolean isDuplicate(PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod) {
        return Objects.equals(paymentMethodDTO.getCardType(), paymentMethod.getCardType()) &&
                Objects.equals(paymentMethodDTO.getCardLastFour(), paymentMethod.getCardLastFour()) &&
                Objects.equals(paymentMethodDTO.getCardHolderName(), paymentMethod.getCardHolderName()) &&
                Objects.equals(paymentMethodDTO.getExpirationMonth(), paymentMethod.getExpirationMonth()) &&
                Objects.equals(paymentMethodDTO.getExpirationYear(), paymentMethod.getExpirationYear());
    }
}
