package com.gamersblended.junes.config;

import com.stripe.StripeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(@Value("${stripe.apiKey:}") String apiKey) {
        return new StripeClient(apiKey);
    }
}
