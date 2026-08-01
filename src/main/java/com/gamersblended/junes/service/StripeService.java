package com.gamersblended.junes.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
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

    public String createCustomer(String email, UUID userID) throws StripeException {
        log.info("Creating Stripe customer for user: {} with email: {}", userID, email);

        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName("User-" + userID.toString())
                .setMetadata(Map.of(
                        "source", "web_application",
                        "created_at", String.valueOf(System.currentTimeMillis())
                ))
                .build();

        Customer customer = stripeClient.v1().customers().create(params);
        log.info("Created Stripe customer with ID: {} for email: {}", customer.getId(), email);

        return customer.getId();
    }


}
