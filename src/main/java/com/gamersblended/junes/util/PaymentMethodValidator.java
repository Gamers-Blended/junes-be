package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import static com.gamersblended.junes.constant.ValidationConstants.CARD_HOLDER_NAME_MAX_LENGTH;

@Slf4j
@Component
public class PaymentMethodValidator {

    private static final int EXPIRATION_YEAR_UPPER_BOUND = 20;

    private final AddressRepository addressRepository;

    public PaymentMethodValidator(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public void validatePaymentMethodForAdd(UUID userID, String stripeCustomerID, com.stripe.model.PaymentMethod paymentMethod) {
        // Payment Method belongs to Customer
        if (null == paymentMethod.getCustomer() || !paymentMethod.getCustomer().equals(stripeCustomerID)) {
            log.error("Error adding new payment method for user {}: User attempted to save PaymentMethod {} not owned by their Stripe customer",
                    userID, paymentMethod.getId());
            throw new InputValidationException("Payment method is not valid for this account");
        }

        if (null == paymentMethod.getCard()) {
            log.error("Error adding new payment method for user {}: PaymentMethod {} has no card details (unexpected type)",
                    userID, paymentMethod.getId());
            throw new InputValidationException("Unsupported payment method type");
        }

        String cardHolderName = paymentMethod.getBillingDetails() != null
                ? paymentMethod.getBillingDetails().getName()
                : null;

        // Cardholder Name
        validateCardHolderName(cardHolderName);
    }

    public void validatePaymentMethodForEdit(UUID userID, EditPaymentMethodRequest editPaymentMethodRequest) {
        // Cardholder Name
        validateCardHolderName(editPaymentMethodRequest.getCardHolderName());

        // Expiration Month & Year
        int validatedExpirationMonth = validateExpirationMonth(editPaymentMethodRequest.getExpirationMonth());
        int validatedExpirationYear = validateExpirationYear(editPaymentMethodRequest.getExpirationYear());
        YearMonth expiration = YearMonth.of(validatedExpirationYear, validatedExpirationMonth);
        if (expiration.isBefore(YearMonth.now(ZoneId.of("Asia/Singapore")))) {
            log.error("Error editing payment method for user {}: card has expired, {}", userID, expiration);
            throw new InputValidationException("Card has expired");
        }
    }

    public void validateCardHolderName(String cardHolderName) {
        if (null == cardHolderName || cardHolderName.isBlank()) {
            log.error("Card holder name validation error: card holder name is not given");
            throw new InputValidationException("Card holder name is not given");
        }

        if (cardHolderName.length() > CARD_HOLDER_NAME_MAX_LENGTH) {
            log.error("Card holder name validation error: card holder name exceeds maximum length of {} characters", CARD_HOLDER_NAME_MAX_LENGTH);
            throw new InputValidationException("Card holder name exceeds maximum length of " + CARD_HOLDER_NAME_MAX_LENGTH + " characters");
        }

        if (!cardHolderName.matches("^[a-zA-Z\\s'-]+$")) {
            log.error("Card holder name validation error: card holder name should contain only letters, spaces, hyphens and apostrophes");
            throw new InputValidationException("Card holder name should contain only letters, spaces, hyphens and apostrophes");
        }
    }

    public int validateExpirationMonth(String expirationMonth) {

        if (null == expirationMonth || expirationMonth.isBlank()) {
            log.error("Expiration month validation error: expiration month is not given");
            throw new InputValidationException("Expiration month is not given");
        }

        if (!expirationMonth.matches("^\\d{1,2}$")) {
            log.error("Expiration month validation error: expiration month: {} is in an invalid format", expirationMonth);
            throw new InputValidationException("Expiration month: " + expirationMonth + " is in an invalid format");
        }

//        // Pad single digit and update DTO
//        if (expirationMonth.length() == 1) {
//            expirationMonth = "0" + expirationMonth;
//            paymentMethodDTO.setExpirationMonth(expirationMonth);
//        }
        int month = Integer.parseInt(expirationMonth);
        if (month < 1 || month > 12) {
            log.error("Expiration month validation error: expiration month: {} must be between 01 and 12", expirationMonth);
            throw new InputValidationException("Expiration month: " + expirationMonth + " must be between 01 and 12");
        }

        return month;
    }

    public int validateExpirationYear(String expirationYear) {
        if (null == expirationYear || expirationYear.isBlank()) {
            log.error("Expiration year validation error: expiration year is not given");
            throw new InputValidationException("Expiration year is not given");
        }

        if (!expirationYear.matches("^\\d{4}$")) {
            log.error("Expiration year validation error: expiration year: {} must be exactly 4 digits (YYYY format)", expirationYear);
            throw new InputValidationException("Expiration year: " + expirationYear + " must be exactly 4 digits (YYYY format)");
        }

        int year = Integer.parseInt(expirationYear);
        int currentYear = Year.now().getValue();
        int maxYear = currentYear + EXPIRATION_YEAR_UPPER_BOUND;

        if (year < currentYear) {
            log.error("Expiration year validation error: expiration year: {} cannot be in the past", expirationYear);
            throw new InputValidationException("Expiration year: " + expirationYear + " cannot be in the past");
        }

        if (year > maxYear) {
            log.error("Expiration year validation error: expiration year: {} cannot be more than {} years in the future", expirationYear, EXPIRATION_YEAR_UPPER_BOUND);
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
