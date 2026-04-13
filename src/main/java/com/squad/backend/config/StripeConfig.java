package com.squad.backend.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            log.error("Stripe secret key is not configured! Please set stripe.secret-key in application.properties");
            throw new IllegalStateException("Stripe secret key is required but not configured");
        }
        Stripe.apiKey = secretKey.trim();
        log.info("Stripe API key initialized successfully (length: {})", Stripe.apiKey.length());
    }
}
