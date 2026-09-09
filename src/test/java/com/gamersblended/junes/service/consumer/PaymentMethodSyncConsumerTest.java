package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.StripePaymentMethodAddressAttachedEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodDetachEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodEditEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodSetDefaultEvent;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodUpdateParams;
import com.stripe.service.CustomerService;
import com.stripe.service.PaymentMethodService;
import com.stripe.service.V1Services;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_PM_SYNC_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMethodSyncConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private KafkaEventParser kafkaEventParser;
    @Mock
    private Acknowledgment ack;
    @Mock
    private V1Services v1Services;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private CustomerService customerService;

    private PaymentMethodSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentMethodSyncConsumer(processedEventRepository, stripeClient, kafkaEventParser);
    }

    private static ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(STRIPE_PM_SYNC_EVENTS, 0, 0L, "key", "raw");
    }

    // ---- onPaymentMethodSyncRequested: idempotency / unhandled type ----

    @Test
    void onPaymentMethodSyncRequested_acknowledgesAndSkips_whenEventAlreadyProcessed() {
        StripePaymentMethodEditEvent event = new StripePaymentMethodEditEvent();
        event.setEventID("evt-1");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        consumer.onPaymentMethodSyncRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onPaymentMethodSyncRequested_acknowledgesAndSkips_whenEventTypeIsUnhandled() {
        StripePaymentMethodDetachEvent unhandled = new StripePaymentMethodDetachEvent();
        unhandled.setEventID("evt-1");
        when(kafkaEventParser.parse("raw")).thenReturn(unhandled);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);

        consumer.onPaymentMethodSyncRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient);
        verify(processedEventRepository, never()).save(any());
    }

    // ---- onPaymentMethodSyncRequested: applyEdit ----

    @Test
    void onPaymentMethodSyncRequested_updatesBillingDetailsAndCard_forEditEvent() throws Exception {
        StripePaymentMethodEditEvent event = new StripePaymentMethodEditEvent();
        event.setEventID("evt-1");
        event.setUserID(UUID.randomUUID());
        event.setStripePaymentMethodID("pm_1");
        event.setCardHolderName("Jane Doe");
        event.setExpirationMonth("12");
        event.setExpirationYear("2030");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.update(eq("pm_1"), any(PaymentMethodUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(mock(PaymentMethod.class));

        consumer.onPaymentMethodSyncRequested(consumerRecord(), ack);

        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(paymentMethodService).update(eq("pm_1"), any(PaymentMethodUpdateParams.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("evt-1");

        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-1")));
        verify(ack).acknowledge();
    }

    // ---- onPaymentMethodSyncRequested: applyAddressAttach ----

    @Test
    void onPaymentMethodSyncRequested_updatesBillingAddress_forAddressAttachedEvent() throws Exception {
        StripePaymentMethodAddressAttachedEvent event = new StripePaymentMethodAddressAttachedEvent();
        event.setEventID("evt-2");
        event.setUserID(UUID.randomUUID());
        event.setStripePaymentMethodID("pm_2");
        event.setAddressLine("123 Main St");
        event.setZipCode("12345");
        event.setCountry("SG");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-2")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.update(eq("pm_2"), any(PaymentMethodUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(mock(PaymentMethod.class));

        consumer.onPaymentMethodSyncRequested(consumerRecord(), ack);

        verify(paymentMethodService).update(eq("pm_2"), any(PaymentMethodUpdateParams.class), any(RequestOptions.class));
        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-2")));
        verify(ack).acknowledge();
    }

    // ---- onPaymentMethodSyncRequested: applySetDefault ----

    @Test
    void onPaymentMethodSyncRequested_updatesCustomerDefaultPaymentMethod_forSetDefaultEvent() throws Exception {
        StripePaymentMethodSetDefaultEvent event = new StripePaymentMethodSetDefaultEvent();
        event.setEventID("evt-3");
        event.setUserID(UUID.randomUUID());
        event.setStripeCustomerID("cus_1");
        event.setStripePaymentMethodID("pm_3");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-3")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.update(eq("cus_1"), any(CustomerUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(mock(Customer.class));

        consumer.onPaymentMethodSyncRequested(consumerRecord(), ack);

        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(customerService).update(eq("cus_1"), any(CustomerUpdateParams.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("evt-3");

        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-3")));
        verify(ack).acknowledge();
    }

    // ---- onPaymentMethodSyncRequested: Stripe failure ----

    @Test
    void onPaymentMethodSyncRequested_throwsStripeOperationException_whenStripeCallFails() throws Exception {
        StripePaymentMethodEditEvent event = new StripePaymentMethodEditEvent();
        event.setEventID("evt-1");
        event.setUserID(UUID.randomUUID());
        event.setStripePaymentMethodID("pm_1");
        event.setExpirationMonth("12");
        event.setExpirationYear("2030");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.update(eq("pm_1"), any(PaymentMethodUpdateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onPaymentMethodSyncRequested(consumerRecord, ack))
                .isInstanceOf(StripeOperationException.class)
                .hasMessageContaining("evt-1");

        verify(ack, never()).acknowledge();
        verify(processedEventRepository, never()).save(any());
    }
}
