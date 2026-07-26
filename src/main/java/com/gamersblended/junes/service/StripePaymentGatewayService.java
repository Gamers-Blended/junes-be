package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.PaymentResult;
import com.gamersblended.junes.dto.request.ChargeRequest;
import com.gamersblended.junes.exception.PaymentGatewayException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripePaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentResult charge(String idempotencyKey, ChargeRequest request) {
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmountInCents())
                .setCurrency(request.getCurrency())
                .setCustomer(request.getStripeCustomerID())
                .setPaymentMethod(request.getStripePaymentMethodID())
                .setOffSession(true)
                .setConfirm(true)
                .putMetadata("orderNumber", request.getOrderNumber())
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            return PaymentResult.builder()
                    .success("succeeded".equals(intent.getStatus()))
                    .paymentIntentID(intent.getId())
                    .status(intent.getStatus())
                    .build();
        } catch (CardException ex) {
            // Card declined - normal business outcome, not gateway failure
            log.warn("[StripePaymentGatewayService] Card declined for order {}: {}",
                    request.getOrderNumber(), ex.getMessage());
            return PaymentResult.builder()
                    .success(false)
                    .status("failed")
                    .failureReason(ex.getMessage())
                    .build();

        } catch (StripeException ex) {
            // Network/timeout/5xx/auth
            // Let retry/DLQ policy handle it
            log.error("[StripePaymentGatewayService] Stripe call failed for order {}",
                    request.getOrderNumber(), ex);
            throw new PaymentGatewayException("Stripe call failed: " + ex.getMessage());
        }
    }
}
