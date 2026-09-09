package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodDetachEvent;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
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

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_DETACH_PM_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
class PaymentMethodDetachConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
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

    private PaymentMethodDetachConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentMethodDetachConsumer(processedEventRepository, paymentMethodRepository, stripeClient, kafkaEventParser);
    }

    private static ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(STRIPE_DETACH_PM_EVENTS, 0, 0L, "key", "raw");
    }

    private static StripePaymentMethodDetachEvent detachEvent(UUID userID, UUID paymentMethodID) {
        StripePaymentMethodDetachEvent event = new StripePaymentMethodDetachEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setStripePaymentMethodID("pm_stripe_1");
        event.setPaymentMethodID(paymentMethodID);
        return event;
    }

    // ---- onStripeDetachPaymentMethodRequested: dispatch / idempotency ----

    @Test
    void onStripeDetachPaymentMethodRequested_acknowledgesAndSkips_whenParsedEventIsWrongType() {
        when(kafkaEventParser.parse("raw")).thenReturn(new PaymentFailedEvent());

        consumer.onStripeDetachPaymentMethodRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient, paymentMethodRepository);
        verify(processedEventRepository, never()).existsByEventID(anyString());
    }

    @Test
    void onStripeDetachPaymentMethodRequested_acknowledgesAndSkips_whenEventAlreadyProcessed() {
        StripePaymentMethodDetachEvent event = detachEvent(UUID.randomUUID(), UUID.randomUUID());
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        consumer.onStripeDetachPaymentMethodRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient, paymentMethodRepository);
    }

    // ---- onStripeDetachPaymentMethodRequested: Stripe call ----

    @Test
    void onStripeDetachPaymentMethodRequested_throwsStripeOperationException_whenStripeDetachFails() throws Exception {
        StripePaymentMethodDetachEvent event = detachEvent(UUID.randomUUID(), UUID.randomUUID());
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.detach(eq("pm_stripe_1"), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onStripeDetachPaymentMethodRequested(consumerRecord, ack))
                .isInstanceOf(StripeOperationException.class)
                .hasMessageContaining("pm_stripe_1");

        verify(ack, never()).acknowledge();
        verify(processedEventRepository, never()).save(any());
        verifyNoInteractions(paymentMethodRepository);
    }

    // ---- onStripeDetachPaymentMethodRequested: success ----

    @Test
    void onStripeDetachPaymentMethodRequested_detachesFromStripe_deletesLocalRow_andMarksProcessed() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        StripePaymentMethodDetachEvent event = detachEvent(userID, paymentMethodID);
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.detach(eq("pm_stripe_1"), any(RequestOptions.class)))
                .thenReturn(mock(PaymentMethod.class));

        consumer.onStripeDetachPaymentMethodRequested(consumerRecord(), ack);

        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(paymentMethodService).detach(eq("pm_stripe_1"), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("evt-1");

        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-1")));
        verify(paymentMethodRepository).deleteById(paymentMethodID);
        verify(ack).acknowledge();
    }

    @Test
    void onStripeDetachPaymentMethodRequested_skipsLocalDelete_whenPaymentMethodIDIsNull() throws Exception {
        StripePaymentMethodDetachEvent event = detachEvent(UUID.randomUUID(), null);
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(paymentMethodService);
        when(paymentMethodService.detach(eq("pm_stripe_1"), any(RequestOptions.class)))
                .thenReturn(mock(PaymentMethod.class));

        consumer.onStripeDetachPaymentMethodRequested(consumerRecord(), ack);

        verify(processedEventRepository).save(any());
        verify(paymentMethodRepository, never()).deleteById(any());
        verify(ack).acknowledge();
    }
}
