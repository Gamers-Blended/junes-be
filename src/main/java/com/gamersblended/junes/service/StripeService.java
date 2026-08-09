package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.dto.event.StripeEmailUpdateEvent;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_SYNC_EVENTS;

@Slf4j
@Service
public class StripeService {

    private final StripeClient stripeClient;
    private final KafkaEventParser kafkaEventParser;
    private final ProcessedEventRepository processedEventRepository;

    public StripeService(StripeClient stripeClient, KafkaEventParser kafkaEventParser, ProcessedEventRepository processedEventRepository) {
        this.stripeClient = stripeClient;
        this.kafkaEventParser = kafkaEventParser;
        this.processedEventRepository = processedEventRepository;
    }

    public String createCustomer(UUID userID, String email) {
        log.info("Creating Stripe customer for user: {} with email: {}", userID, email);

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName("User-" + userID.toString())
                    .setMetadata(Map.of(
                            "source", "web_application",
                            "userID", userID.toString(),
                            "created_at", String.valueOf(System.currentTimeMillis())
                    ))
                    .build();

            // Idempotency key: same userID -> same key -> Stripe returns same customer object on retry
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("create-customer-" + userID)
                    .build();

            Customer customer = stripeClient.v1().customers().create(params, options);
            log.info("Created Stripe customer with ID: {} for email: {}", customer.getId(), email);

            return customer.getId();
        } catch (StripeException ex) {
            log.error("Failed to create Stripe customer for userID: {}", userID, ex);
            throw new StripeOperationException("Failed to create Stripe customer for userID: " + userID);
        }
    }

    @KafkaListener(topics = STRIPE_SYNC_EVENTS, groupId = "stripe-sync-consumer")
    @Transactional
    public void onStripeEmailUpdateRequested(ConsumerRecord<String, String> stripeEventRecord, Acknowledgment ack) {
        BaseEvent parsed = kafkaEventParser.parse(stripeEventRecord.value());

        if (!(parsed instanceof StripeEmailUpdateEvent event)) {
            ack.acknowledge();
            return;
        }

        if (processedEventRepository.existsByEventID(event.getEventID())) {
            log.info("[StripeService] Event {} already processed, skipping", event.getEventID());
            ack.acknowledge();
            return;
        }

        log.info("Processing Stripe email sync {} for userID: {}", event.getEventID(), event.getUserID());

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("update-customer-email-" + event.getEventID())
                .build();

        updateCustomerEmail(event.getStripeCustomerID(), event.getNewEmail(), options);
    }

    private void updateCustomerEmail(String customerID, String newEmail, RequestOptions options) {
        log.info("Updating Stripe customer email: {}", customerID);

        try {
            CustomerUpdateParams params = CustomerUpdateParams.builder()
                    .setEmail(newEmail)
                    .build();

            stripeClient.v1().customers().update(customerID, params, options);
            log.info("Updated Stripe customer {} email", customerID);
        } catch (StripeException ex) {
            log.error("Failed to update Stripe customer {} email", customerID, ex);
            throw new StripeOperationException("Failed to update Stripe customer email: " + customerID);
        }

    }
}
