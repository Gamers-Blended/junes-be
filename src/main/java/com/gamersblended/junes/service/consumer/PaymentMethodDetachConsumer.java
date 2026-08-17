package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodDetachEvent;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.model.ProcessedEvent;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_DETACH_PM_EVENTS;

@Slf4j
@Service
public class PaymentMethodDetachConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final StripeClient stripeClient;
    private final KafkaEventParser kafkaEventParser;

    public PaymentMethodDetachConsumer(ProcessedEventRepository processedEventRepository, PaymentMethodRepository paymentMethodRepository,
                                       StripeClient stripeClient, KafkaEventParser kafkaEventParser) {
        this.processedEventRepository = processedEventRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.stripeClient = stripeClient;
        this.kafkaEventParser = kafkaEventParser;
    }

    @KafkaListener(topics = STRIPE_DETACH_PM_EVENTS, groupId = "stripe-detach-payment-method-consumer")
    @Transactional
    public void onStripeDetachPaymentMethodRequested(ConsumerRecord<String, String> stripeEventRecord, Acknowledgment ack) {
        BaseEvent parsed = kafkaEventParser.parse(stripeEventRecord.value());

        if (!(parsed instanceof StripePaymentMethodDetachEvent event)) {
            ack.acknowledge();
            return;
        }

        // Consumer-side idempotency
        // Kafka gives at-least-once delivery so same event can arrive more than once
        if (processedEventRepository.existsByEventID(event.getEventID())) {
            log.info("[StripeService] Event {} already processed, skipping", event.getEventID());
            ack.acknowledge();
            return;
        }

        log.info("Processing Stripe detach Payment Event event {} for userID: {}", event.getEventID(), event.getUserID());

        RequestOptions detachOptions = RequestOptions.builder()
                .setIdempotencyKey(event.getEventID())
                .build();

        try {
            stripeClient.v1().paymentMethods().detach(event.getStripePaymentMethodID(), detachOptions);
            log.info("[StripeService] Detached Payment Method {} from Stripe for userID {}", event.getStripePaymentMethodID(), event.getUserID());
        } catch (StripeException ex) {
            log.error("[StripeService] Stripe detach failed for Payment Method {}: {}", event.getStripePaymentMethodID(), ex.getMessage(), ex);
            throw new StripeOperationException("Stripe detach failed for Payment Method " + event.getStripePaymentMethodID());
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventID(event.getEventID());
        processed.setProcessedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        processedEventRepository.save(processed);

        paymentMethodRepository.deleteById(event.getPaymentMethodID());
    }
}

