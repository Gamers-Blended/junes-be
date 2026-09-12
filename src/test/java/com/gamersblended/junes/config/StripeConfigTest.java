package com.gamersblended.junes.config;

import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripeConfigTest {

    private final StripeConfig stripeConfig = new StripeConfig();

    @Test
    void stripeClient_returnsUsableClientForProvidedApiKey() {
        StripeClient client = stripeConfig.stripeClient("sk_test_123");

        assertThat(client).isNotNull();
        assertThat(client.v1().charges()).isNotNull();
    }

    @Test
    void stripeClient_acceptsBlankApiKey() {
        StripeClient client = stripeConfig.stripeClient("");

        assertThat(client).isNotNull();
    }
}
