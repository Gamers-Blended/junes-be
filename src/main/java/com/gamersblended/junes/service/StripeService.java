package com.gamersblended.junes.service;

import com.gamersblended.junes.exception.StripeOperationException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class StripeService {

    private final StripeClient stripeClient;

    public StripeService(StripeClient stripeClient) {
        this.stripeClient = stripeClient;
    }

    public String createCustomer(String email, UUID userID) {
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


}
