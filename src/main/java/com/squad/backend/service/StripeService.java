package com.squad.backend.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Token;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StripeService {

    public Customer createCustomer(String name, String email) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(name)
                .setEmail(email)
                .build();
        return Customer.create(params);
    }

    public Customer createCustomerWithMetadata(String name, String email, Map<String, String> metadata) throws StripeException {
        CustomerCreateParams.Builder paramsBuilder = CustomerCreateParams.builder()
                .setName(name)
                .setEmail(email);
        
        if (metadata != null && !metadata.isEmpty()) {
            paramsBuilder.putAllMetadata(metadata);
        }
        
        return Customer.create(paramsBuilder.build());
    }

    public Token createCardToken(String cardNumber, String expMonth, String expYear, String cvc, String name) throws StripeException {
        Map<String, Object> cardParams = new HashMap<>();
        cardParams.put("number", cardNumber);
        cardParams.put("exp_month", expMonth);
        cardParams.put("exp_year", expYear);
        cardParams.put("cvc", cvc);
        cardParams.put("name", name);

        Map<String, Object> tokenParams = new HashMap<>();
        tokenParams.put("card", cardParams);

        return Token.create(tokenParams);
    }

    public com.stripe.model.Card addCardToCustomer(String customerId, String tokenId) throws StripeException {
        Customer customer = Customer.retrieve(customerId);
        Map<String, Object> params = new HashMap<>();
        params.put("source", tokenId);
        return (com.stripe.model.Card) customer.getSources().create(params);
    }

    public PaymentIntent createPaymentIntent(
            Long amountInCents,
            String currency,
            Map<String, String> metadata) throws StripeException {
        // Safety check: ensure API key is set (should be set by StripeConfig, but just in case)
        if (Stripe.apiKey == null || Stripe.apiKey.trim().isEmpty()) {
            log.error("Stripe API key is not set! Check StripeConfig initialization.");
            throw new IllegalStateException("Stripe API key is not configured. Please check application.properties and restart the application.");
        }
        
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .addPaymentMethodType("card")
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(false)
                                .build())
                .build();
        return PaymentIntent.create(params);
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    public void setDefaultPaymentMethod(String customerId, String paymentMethodId) {
        try {
            CustomerUpdateParams params = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(paymentMethodId)
                                    .build())
                    .build();
            Customer.retrieve(customerId).update(params);
        } catch (StripeException e) {
            log.warn("Could not set default payment method: {}", e.getMessage());
        }
    }

    public Session createCheckoutSession(
            Long amountInCents,
            String currency,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata) throws StripeException {
        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Session Payment")
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency.toLowerCase())
                        .setProductData(productData)
                        .setUnitAmount(amountInCents)
                        .build();

        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setPriceData(priceData)
                        .setQuantity(1L)
                        .build();

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(lineItem)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putAllMetadata(metadata)
                .build();

        return Session.create(params);
    }

    public com.stripe.model.Event constructWebhookEvent(String payload, String sigHeader, String webhookSecret) throws StripeException {
        return com.stripe.net.Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
