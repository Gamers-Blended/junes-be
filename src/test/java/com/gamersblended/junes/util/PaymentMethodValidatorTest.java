package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.model.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PaymentMethodValidatorTest {

    private final PaymentMethodValidator validator = new PaymentMethodValidator();
    private final UUID userID = UUID.randomUUID();
    private static final String STRIPE_CUSTOMER_ID = "cus_test123";

    private int currentYear() {
        return Year.now(ZoneId.of("Asia/Singapore")).getValue();
    }

    private com.stripe.model.PaymentMethod validStripePaymentMethod() {
        com.stripe.model.PaymentMethod paymentMethod = new com.stripe.model.PaymentMethod();
        paymentMethod.setId("pm_test123");
        paymentMethod.setCustomer(STRIPE_CUSTOMER_ID);
        paymentMethod.setCard(new com.stripe.model.PaymentMethod.Card());

        com.stripe.model.PaymentMethod.BillingDetails billingDetails = new com.stripe.model.PaymentMethod.BillingDetails();
        billingDetails.setName("John Doe");
        paymentMethod.setBillingDetails(billingDetails);

        return paymentMethod;
    }

    @Test
    void validatePaymentMethodForAdd_acceptsValidPaymentMethod() {
        com.stripe.model.PaymentMethod paymentMethod = validStripePaymentMethod();

        assertThatCode(() -> validator.validatePaymentMethodForAdd(userID, STRIPE_CUSTOMER_ID, paymentMethod))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePaymentMethodForAdd_rejectsMissingCustomer() {
        com.stripe.model.PaymentMethod paymentMethod = validStripePaymentMethod();
        paymentMethod.setCustomer(null);

        assertThatThrownBy(() -> validator.validatePaymentMethodForAdd(userID, STRIPE_CUSTOMER_ID, paymentMethod))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Payment method is not valid for this account");
    }

    @Test
    void validatePaymentMethodForAdd_rejectsPaymentMethodOwnedByDifferentCustomer() {
        com.stripe.model.PaymentMethod paymentMethod = validStripePaymentMethod();

        assertThatThrownBy(() -> validator.validatePaymentMethodForAdd(userID, "cus_other456", paymentMethod))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Payment method is not valid for this account");
    }

    @Test
    void validatePaymentMethodForAdd_rejectsMissingCardDetails() {
        com.stripe.model.PaymentMethod paymentMethod = validStripePaymentMethod();
        paymentMethod.setCard(null);

        assertThatThrownBy(() -> validator.validatePaymentMethodForAdd(userID, STRIPE_CUSTOMER_ID, paymentMethod))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Unsupported payment method type");
    }

    @Test
    void validatePaymentMethodForAdd_rejectsMissingBillingDetails() {
        com.stripe.model.PaymentMethod paymentMethod = validStripePaymentMethod();
        paymentMethod.setBillingDetails(null);

        assertThatThrownBy(() -> validator.validatePaymentMethodForAdd(userID, STRIPE_CUSTOMER_ID, paymentMethod))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Card holder name is not given");
    }

    @Test
    void validatePaymentMethodForEdit_acceptsValidRequest() {
        EditPaymentMethodRequest request = new EditPaymentMethodRequest(
                "John Doe", "01", String.valueOf(currentYear() + 1));

        assertThatCode(() -> validator.validatePaymentMethodForEdit(userID, request))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePaymentMethodForEdit_rejectsExpiredCard() {
        // Only constructible when the current month isn't January: a same-year, earlier-month
        // expiration is needed so validateExpirationYear passes and the YearMonth check fires.
        YearMonth now = YearMonth.now(ZoneId.of("Asia/Singapore"));
        assumeTrue(now.getMonthValue() > 1);
        YearMonth expired = now.minusMonths(1);

        EditPaymentMethodRequest request = new EditPaymentMethodRequest(
                "John Doe", String.format("%02d", expired.getMonthValue()), String.valueOf(expired.getYear()));

        assertThatThrownBy(() -> validator.validatePaymentMethodForEdit(userID, request))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Card has expired");
    }

    @Test
    void validateCardHolderName_rejectsNullName() {
        assertThatThrownBy(() -> validator.validateCardHolderName(null))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Card holder name is not given");
    }

    @Test
    void validateCardHolderName_rejectsBlankName() {
        assertThatThrownBy(() -> validator.validateCardHolderName("   "))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Card holder name is not given");
    }

    @Test
    void validateCardHolderName_rejectsNameExceedingMaxLength() {
        assertThatThrownBy(() -> validator.validateCardHolderName("A".repeat(101)))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validateCardHolderName_rejectsNameWithInvalidCharacters() {
        assertThatThrownBy(() -> validator.validateCardHolderName("John123"))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("only letters, spaces, hyphens and apostrophes");
    }

    @Test
    void validateCardHolderName_acceptsNameWithHyphenAndApostrophe() {
        assertThatCode(() -> validator.validateCardHolderName("Mary-Jane O'Brien")).doesNotThrowAnyException();
    }

    @Test
    void validateExpirationMonth_rejectsNullMonth() {
        assertThatThrownBy(() -> validator.validateExpirationMonth(null))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Expiration month is not given");
    }

    @Test
    void validateExpirationMonth_rejectsNonNumericMonth() {
        assertThatThrownBy(() -> validator.validateExpirationMonth("AB"))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("invalid format");
    }

    @Test
    void validateExpirationMonth_rejectsMonthBelowRange() {
        assertThatThrownBy(() -> validator.validateExpirationMonth("00"))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("must be between 01 and 12");
    }

    @Test
    void validateExpirationMonth_rejectsMonthAboveRange() {
        assertThatThrownBy(() -> validator.validateExpirationMonth("13"))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("must be between 01 and 12");
    }

    @Test
    void validateExpirationMonth_acceptsValidMonth() {
        assertThat(validator.validateExpirationMonth("07")).isEqualTo(7);
    }

    @Test
    void validateExpirationYear_rejectsNullYear() {
        assertThatThrownBy(() -> validator.validateExpirationYear(null))
                .isInstanceOf(InputValidationException.class)
                .hasMessage("Expiration year is not given");
    }

    @Test
    void validateExpirationYear_rejectsYearNotFourDigits() {
        assertThatThrownBy(() -> validator.validateExpirationYear("99"))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("must be exactly 4 digits");
    }

    @Test
    void validateExpirationYear_rejectsPastYear() {
        assertThatThrownBy(() -> validator.validateExpirationYear(String.valueOf(currentYear() - 1)))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    void validateExpirationYear_rejectsYearTooFarInFuture() {
        assertThatThrownBy(() -> validator.validateExpirationYear(String.valueOf(currentYear() + 21)))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("cannot be more than 20 years in the future");
    }

    @Test
    void validateExpirationYear_acceptsCurrentYear() {
        assertThat(validator.validateExpirationYear(String.valueOf(currentYear()))).isEqualTo(currentYear());
    }

    @Test
    void isDuplicate_returnsTrueWhenAllFieldsMatch() {
        PaymentMethodDTO dto = new PaymentMethodDTO(
                UUID.randomUUID(), "visa", "4242", "John Doe", "01", "2030", UUID.randomUUID(), true);
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setCardType(dto.getCardType());
        paymentMethod.setCardLastFour(dto.getCardLastFour());
        paymentMethod.setCardHolderName(dto.getCardHolderName());
        paymentMethod.setExpirationMonth(dto.getExpirationMonth());
        paymentMethod.setExpirationYear(dto.getExpirationYear());

        assertThat(validator.isDuplicate(dto, paymentMethod)).isTrue();
    }

    @Test
    void isDuplicate_returnsFalseWhenCardLastFourDiffers() {
        PaymentMethodDTO dto = new PaymentMethodDTO(
                UUID.randomUUID(), "visa", "4242", "John Doe", "01", "2030", UUID.randomUUID(), true);
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setCardType(dto.getCardType());
        paymentMethod.setCardLastFour("9999");
        paymentMethod.setCardHolderName(dto.getCardHolderName());
        paymentMethod.setExpirationMonth(dto.getExpirationMonth());
        paymentMethod.setExpirationYear(dto.getExpirationYear());

        assertThat(validator.isDuplicate(dto, paymentMethod)).isFalse();
    }
}
