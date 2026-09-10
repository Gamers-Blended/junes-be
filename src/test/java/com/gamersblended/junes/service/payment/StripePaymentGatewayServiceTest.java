package com.gamersblended.junes.service.payment;

import com.gamersblended.junes.dto.PaymentResult;
import com.gamersblended.junes.dto.request.ChargeRequest;
import com.gamersblended.junes.exception.PaymentGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.V1Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripePaymentGatewayServiceTest {

    @Mock
    private StripeClient stripeClient;
    @Mock
    private V1Services v1Services;
    @Mock
    private PaymentIntentService paymentIntentService;

    private StripePaymentGatewayService stripePaymentGatewayService;

    @BeforeEach
    void setUp() {
        stripePaymentGatewayService = new StripePaymentGatewayService(stripeClient);
    }

    private static ChargeRequest chargeRequest() {
        return ChargeRequest.builder()
                .stripeCustomerID("cus_1")
                .stripePaymentMethodID("pm_1")
                .amountInCents(1999L)
                .currency("usd")
                .orderNumber("J-1")
                .build();
    }

    private static PaymentIntent paymentIntent(String status) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_1");
        intent.setStatus(status);
        return intent;
    }

    // ---- charge: success ----

    @Test
    void charge_returnsSuccessResult_whenPaymentIntentSucceeds() throws Exception {
        ChargeRequest request = chargeRequest();
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentIntentService.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(paymentIntent("succeeded"));

        PaymentResult result = stripePaymentGatewayService.charge("idem-1", request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPaymentIntentID()).isEqualTo("pi_1");
        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void charge_returnsUnsuccessfulResult_whenIntentStatusIsNotSucceeded() throws Exception {
        ChargeRequest request = chargeRequest();
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentIntentService.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(paymentIntent("requires_action"));

        PaymentResult result = stripePaymentGatewayService.charge("idem-1", request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("requires_action");
    }

    @Test
    void charge_buildsParamsAndRequestOptions_fromChargeRequest() throws Exception {
        ChargeRequest request = chargeRequest();
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentIntentService.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(paymentIntent("succeeded"));

        stripePaymentGatewayService.charge("idem-1", request);

        ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(paymentIntentService).create(paramsCaptor.capture(), optionsCaptor.capture());

        PaymentIntentCreateParams params = paramsCaptor.getValue();
        assertThat(params.getAmount()).isEqualTo(1999L);
        assertThat(params.getCurrency()).isEqualTo("usd");
        assertThat(params.getCustomer()).isEqualTo("cus_1");
        assertThat(params.getPaymentMethod()).isEqualTo("pm_1");
        assertThat(params.getMetadata()).containsEntry("orderNumber", "J-1");
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
    }

    // ---- charge: card declined ----

    @Test
    void charge_returnsFailureResult_whenCardIsDeclined() throws Exception {
        ChargeRequest request = chargeRequest();
        CardException cardException = new CardException("Your card was declined", "req_1", "card_declined",
                null, "generic_decline", null, 402, null);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentIntentService.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenThrow(cardException);

        PaymentResult result = stripePaymentGatewayService.charge("idem-1", request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getFailureReason()).contains("Your card was declined");
        assertThat(result.getPaymentIntentID()).isNull();
    }

    // ---- charge: gateway failure ----

    @Test
    void charge_throwsPaymentGatewayException_whenStripeCallFails() throws Exception {
        ChargeRequest request = chargeRequest();
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentIntentService.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("network down"));

        assertThatThrownBy(() -> stripePaymentGatewayService.charge("idem-1", request))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("network down");
    }
}
