package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.PaymentResult;
import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.OrderCreatedEvent;
import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.PaymentSucceededEvent;
import com.gamersblended.junes.dto.request.ChargeRequest;
import com.gamersblended.junes.exception.PaymentGatewayException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.model.ProcessedEvent;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
public class PaymentEventConsumer {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final KafkaEventParser kafkaEventParser;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(
            KafkaEventParser kafkaEventParser,
            PaymentGatewayService paymentGatewayService,
            PaymentMethodRepository paymentMethodRepository,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.kafkaEventParser = kafkaEventParser;
        this.paymentGatewayService = paymentGatewayService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ORDER_EVENTS_TOPIC, groupId = "payment-worker")
    @Transactional
    public void onOrderEvent(ConsumerRecord<String, String> orderEventRecord, Acknowledgment ack) {
        BaseEvent parsed = kafkaEventParser.parse(orderEventRecord.value());

        // Worker only reacts to OrderCreated
        // OrderFinalisationConsumer reacts to the other event types
        if (!(parsed instanceof OrderCreatedEvent event)) {
            ack.acknowledge();
            return;
        }

        // Check before calling Stripe
        if (processedEventRepository.existsByEventID(event.getEventID())) {
            log.info("[PaymentEventConsumer] Event {} already processed, skipping", event.getEventID());
            ack.acknowledge();
            return;
        }

        // Check payment method matches userID and paymentMethodID (cannot charge card belonging to another user)
        PaymentMethod paymentMethod = paymentMethodRepository
                .getPaymentMethodByUserIDAndID(event.getUserID(), event.getPaymentMethodID())
                .orElseThrow(() -> new SavedItemNotFoundException("Payment method not found: " + event.getPaymentMethodID()));

        if (null == paymentMethod.getStripeCustomerID() || null == paymentMethod.getStripePaymentMethodID()) {
            throw new SavedItemNotFoundException(
                    "Payment method " + event.getPaymentMethodID() + " has no linked Stripe references"
            );
        }

        PaymentResult result;
        try {
            result = paymentGatewayService.charge(
                    "order-charge-" + event.getOrderNumber(), // Stripe idempotency key
                    ChargeRequest.builder()
                            .stripeCustomerID(paymentMethod.getStripeCustomerID())
                            .stripePaymentMethodID(paymentMethod.getStripePaymentMethodID())
                            .amountInCents(toAmountInCents(event.getTotalAmount()))
                            .currency("SGD")
                            .orderNumber(event.getOrderNumber())
                            .build());
        } catch (PaymentGatewayException ex) {
            log.error("[PaymentEventConsumer] Stripe call failed for order {}", event.getOrderNumber(), ex);
            throw ex;
        }

        if (result.isSuccess()) {
            publishPaymentSucceeded(event, result);
        } else {
            publishPaymentFailed(event, result);
        }

        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventID(event.getEventID());
        processedEvent.setProcessedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        processedEventRepository.save(processedEvent);

        ack.acknowledge();
    }

    private long toAmountInCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private void publishPaymentSucceeded(OrderCreatedEvent event, PaymentResult result) {
        PaymentSucceededEvent succeededEvent = new PaymentSucceededEvent();
        succeededEvent.setTransactionID(event.getTransactionID());
        succeededEvent.setOrderNumber(event.getOrderNumber());
        succeededEvent.setUserID(event.getUserID());
        succeededEvent.setAmountCharged(event.getTotalAmount());
        succeededEvent.setStripePaymentIntentID(result.getPaymentIntentID());

        writeOutboxEvent(event.getOrderNumber(), succeededEvent);

        log.info("[PaymentEventConsumer] Payment succeeded for order {}", event.getOrderNumber());
    }

    private void publishPaymentFailed(OrderCreatedEvent event, PaymentResult result) {
        PaymentFailedEvent failedEvent = new PaymentFailedEvent();
        failedEvent.setTransactionID(event.getTransactionID());
        failedEvent.setOrderNumber(event.getOrderNumber());
        failedEvent.setUserID(event.getUserID());
        failedEvent.setFailureReason(result.getFailureReason());

        writeOutboxEvent(event.getOrderNumber(), failedEvent);

        log.error("[PaymentEventConsumer] Payment failed for order {}: {}", event.getOrderNumber(), result.getFailureReason());
    }

    private void writeOutboxEvent(String orderNumber, BaseEvent event) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setAggregateID(orderNumber); // same partition key as OrderCreated for ordering
            outboxEvent.setEventType(event.getEventType());
            outboxEvent.setTopic(ORDER_EVENTS_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setCreatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
            outboxEvent.setPublished(false);
            outboxEvent.setRetryCount(0);

            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            log.error("[PaymentEventConsumer] Failed to write outbox event for order {}", orderNumber, ex);
            throw new RuntimeException("Failed to write outbox event: " + ex.getMessage(), ex);
        }
    }
}
