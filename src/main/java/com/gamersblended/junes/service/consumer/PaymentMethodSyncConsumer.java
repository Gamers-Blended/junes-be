package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodAddressAttachedEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodEditEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodSetDefaultEvent;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.model.ProcessedEvent;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_PM_SYNC_EVENTS;

@Slf4j
@Service
public class PaymentMethodSyncConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final StripeClient stripeClient;
    private final KafkaEventParser kafkaEventParser;

    public PaymentMethodSyncConsumer(ProcessedEventRepository processedEventRepository, StripeClient stripeClient, KafkaEventParser kafkaEventParser) {
        this.processedEventRepository = processedEventRepository;
        this.stripeClient = stripeClient;
        this.kafkaEventParser = kafkaEventParser;
    }

    @KafkaListener(topics = STRIPE_PM_SYNC_EVENTS, groupId = "stripe-pm-sync-consumer")
    @Transactional
    public void onPaymentMethodSyncRequested(ConsumerRecord<String, String> stripeEventRecord, Acknowledgment ack) {
        BaseEvent parsed = kafkaEventParser.parse(stripeEventRecord.value());

        // Kafka gives at-least-once delivery so same event can arrive more than once
        if (processedEventRepository.existsByEventID(parsed.getEventID())) {
            log.info("[PaymentMethodSyncConsumer] Event {} already processed, skipping", parsed.getEventID());
            ack.acknowledge();
            return;
        }

        try {
            if (parsed instanceof StripePaymentMethodEditEvent event) {
                applyEdit(event);
            } else if (parsed instanceof StripePaymentMethodAddressAttachedEvent event) {
                applyAddressAttach(event);
            } else if (parsed instanceof StripePaymentMethodSetDefaultEvent event) {
                applySetDefault(event);
            } else {
                log.warn("[PaymentMethodSyncConsumer] Unhandled event type {}, skipping", parsed.getEventType());
                ack.acknowledge();
                return;
            }
        } catch (StripeException ex) {
            log.error("[PaymentMethodSyncConsumer] Stripe sync failed for event {}: {}", parsed.getEventID(), ex.getMessage(), ex);
            throw new StripeOperationException("Stripe sync failed for event " + parsed.getEventID());
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventID(parsed.getEventID());
        processed.setProcessedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        processedEventRepository.save(processed);

        ack.acknowledge();
    }

    private void applyEdit(StripePaymentMethodEditEvent event) throws StripeException {
        PaymentMethodUpdateParams updateParams = PaymentMethodUpdateParams.builder()
                .setBillingDetails(
                        PaymentMethodUpdateParams.BillingDetails.builder()
                                .setName(event.getCardHolderName())
                                .build()
                )
                .setCard(PaymentMethodUpdateParams.Card.builder()
                        .setExpMonth(Long.parseLong(event.getExpirationMonth()))
                        .setExpYear(Long.parseLong(event.getExpirationYear()))
                        .build())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(event.getEventID())
                .build();

        stripeClient.v1().paymentMethods().update(event.getStripePaymentMethodID(), updateParams, options);
        log.info("[PaymentMethodSyncConsumer] Updated billing details for Stripe Payment Method {}", event.getStripePaymentMethodID());
    }

    private void applyAddressAttach(StripePaymentMethodAddressAttachedEvent event) throws StripeException {
        PaymentMethodUpdateParams updateParams = PaymentMethodUpdateParams.builder()
                .setBillingDetails(
                        PaymentMethodUpdateParams.BillingDetails.builder()
                                .setAddress(
                                        PaymentMethodUpdateParams.BillingDetails.Address.builder()
                                                .setLine1(event.getAddressLine())
                                                .setPostalCode(event.getZipCode())
                                                .setCountry(event.getCountry())
                                                .build()
                                )
                                .build()
                )
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(event.getEventID())
                .build();

        stripeClient.v1().paymentMethods().update(event.getStripePaymentMethodID(), updateParams, options);
        log.info("[PaymentMethodSyncConsumer] Updated billing address for Stripe Payment Method {}", event.getStripePaymentMethodID());
    }

    private void applySetDefault(StripePaymentMethodSetDefaultEvent event) throws StripeException {
        CustomerUpdateParams customerParams = CustomerUpdateParams.builder()
                .setInvoiceSettings(
                        CustomerUpdateParams.InvoiceSettings.builder()
                                .setDefaultPaymentMethod(event.getStripePaymentMethodID())
                                .build()
                )
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(event.getEventID())
                .build();

        stripeClient.v1().customers().update(event.getStripeCustomerID(), customerParams, options);
        log.info("[PaymentMethodSyncConsumer] Updated default Payment Method for Stripe customer {}", event.getStripeCustomerID());
    }
}