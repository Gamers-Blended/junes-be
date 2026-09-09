package com.gamersblended.junes.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.PaymentResult;
import com.gamersblended.junes.dto.event.OrderCreatedEvent;
import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.PaymentSucceededEvent;
import com.gamersblended.junes.dto.request.ChargeRequest;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.exception.PaymentGatewayException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.service.payment.PaymentGatewayService;
import com.gamersblended.junes.util.KafkaEventParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private KafkaEventParser kafkaEventParser;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Acknowledgment ack;

    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(
                kafkaEventParser, paymentGatewayService, paymentMethodRepository,
                processedEventRepository, outboxEventRepository, objectMapper);
    }

    private static ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(ORDER_EVENTS, 0, 0L, "key", "raw");
    }

    private static OrderCreatedEvent orderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventID("evt-1");
        event.setTransactionID(UUID.randomUUID());
        event.setOrderNumber("ORD-0001");
        event.setUserID(UUID.randomUUID());
        event.setPaymentMethodID(UUID.randomUUID());
        event.setTotalAmount(new BigDecimal("59.99"));
        event.setCurrency("USD");
        event.setIdempotencyKey("idem-1");
        return event;
    }

    private static PaymentMethod paymentMethod() {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setStripeCustomerID("cus_1");
        paymentMethod.setStripePaymentMethodID("pm_1");
        return paymentMethod;
    }

    // ---- onOrderEvent: non OrderCreatedEvent ----

    @Test
    void onOrderEvent_acknowledgesAndSkips_whenParsedEventIsNotOrderCreated() {
        PaymentSucceededEvent other = new PaymentSucceededEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(other);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(paymentMethodRepository, paymentGatewayService, outboxEventRepository);
        verify(processedEventRepository, never()).existsByEventID(anyString());
    }

    // ---- onOrderEvent: idempotency ----

    @Test
    void onOrderEvent_acknowledgesAndSkips_whenEventAlreadyProcessed() {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(paymentMethodRepository, paymentGatewayService, outboxEventRepository);
    }

    // ---- onOrderEvent: payment method lookup ----

    @Test
    void onOrderEvent_throwsSavedItemNotFoundException_whenPaymentMethodNotFound() {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.empty());

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(SavedItemNotFoundException.class)
                .hasMessageContaining(event.getPaymentMethodID().toString());

        verify(ack, never()).acknowledge();
        verifyNoInteractions(paymentGatewayService);
    }

    @Test
    void onOrderEvent_throwsSavedItemNotFoundException_whenPaymentMethodMissingStripeReferences() {
        OrderCreatedEvent event = orderCreatedEvent();
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setStripeCustomerID(null);
        paymentMethod.setStripePaymentMethodID(null);
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.of(paymentMethod));

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(SavedItemNotFoundException.class)
                .hasMessageContaining("no linked Stripe references");

        verify(ack, never()).acknowledge();
        verifyNoInteractions(paymentGatewayService);
    }

    // ---- onOrderEvent: charge ----

    @Test
    void onOrderEvent_chargesCorrectAmountInCents_andPropagatesGatewayException() {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.of(paymentMethod()));
        when(paymentGatewayService.charge(anyString(), any(ChargeRequest.class)))
                .thenThrow(new PaymentGatewayException("stripe down"));

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(PaymentGatewayException.class);

        verify(ack, never()).acknowledge();
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void onOrderEvent_publishesPaymentSucceeded_andMarksProcessed_whenChargeSucceeds() throws Exception {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.of(paymentMethod()));
        PaymentResult result = PaymentResult.builder().success(true).paymentIntentID("pi_1").build();
        when(paymentGatewayService.charge(eq("order-charge-" + event.getOrderNumber()), any(ChargeRequest.class)))
                .thenReturn(result);
        when(objectMapper.writeValueAsString(any(PaymentSucceededEvent.class))).thenReturn("{}");

        consumer.onOrderEvent(consumerRecord(), ack);

        ArgumentCaptor<ChargeRequest> chargeCaptor = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(paymentGatewayService).charge(anyString(), chargeCaptor.capture());
        assertThat(chargeCaptor.getValue().getAmountInCents()).isEqualTo(5999L);
        assertThat(chargeCaptor.getValue().getStripeCustomerID()).isEqualTo("cus_1");
        assertThat(chargeCaptor.getValue().getStripePaymentMethodID()).isEqualTo("pm_1");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent savedOutboxEvent = outboxCaptor.getValue();
        assertThat(savedOutboxEvent.getEventType()).isEqualTo(PAYMENT_SUCCEEDED);
        assertThat(savedOutboxEvent.getTopic()).isEqualTo(ORDER_EVENTS);
        assertThat(savedOutboxEvent.getAggregateID()).isEqualTo(event.getOrderNumber());
        assertThat(savedOutboxEvent.getStatus()).isEqualTo(PENDING);
        assertThat(savedOutboxEvent.isPublished()).isFalse();
        assertThat(savedOutboxEvent.getRetryCount()).isZero();

        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-1")));
        verify(ack).acknowledge();
    }

    @Test
    void onOrderEvent_publishesPaymentFailed_andMarksProcessed_whenChargeFails() throws Exception {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.of(paymentMethod()));
        PaymentResult result = PaymentResult.builder().success(false).failureReason("card_declined").build();
        when(paymentGatewayService.charge(anyString(), any(ChargeRequest.class))).thenReturn(result);
        when(objectMapper.writeValueAsString(any(PaymentFailedEvent.class))).thenReturn("{}");

        consumer.onOrderEvent(consumerRecord(), ack);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(PAYMENT_FAILED);

        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void onOrderEvent_throwsOutboxEventCreationException_whenPayloadSerializationFails() throws Exception {
        OrderCreatedEvent event = orderCreatedEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID()))
                .thenReturn(Optional.of(paymentMethod()));
        PaymentResult result = PaymentResult.builder().success(true).paymentIntentID("pi_1").build();
        when(paymentGatewayService.charge(anyString(), any(ChargeRequest.class))).thenReturn(result);
        when(objectMapper.writeValueAsString(any(PaymentSucceededEvent.class)))
                .thenThrow(new JsonProcessingException("boom") {
                });

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(OutboxEventCreationException.class);

        verify(ack, never()).acknowledge();
        verify(processedEventRepository, never()).save(any());
    }
}
