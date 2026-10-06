package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.AddCardRequest;
import com.squad.backend.dto.request.ConfirmPaymentRequest;
import com.squad.backend.dto.request.CreateCheckoutSessionRequest;
import com.squad.backend.dto.request.CreateCustomerRequest;
import com.squad.backend.dto.request.CreatePaymentIntentRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.CheckoutSessionResponse;
import com.squad.backend.dto.response.PaymentIntentResponse;
import com.squad.backend.dto.response.finance.TransactionResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.repository.TransactionRepository;
import com.squad.backend.service.ClubWalletService;
import com.squad.backend.service.FinanceService;
import com.squad.backend.service.PaymentService;
import com.squad.backend.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Token;
import com.stripe.model.checkout.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final StripeService stripeService;
    private final PaymentService paymentService;
    private final ClubWalletService clubWalletService;
    private final FinanceService financeService;
    private final TransactionRepository transactionRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${stripe.webhook-secret:your-webhook-secret-from-stripe-dashboard}")
    private String webhookSecret;

    @PostMapping("/create-customer")
    public ResponseEntity<ApiResponse<Customer>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        try {
            Customer customer = stripeService.createCustomer(request.getName(), request.getEmail());
            return ResponseEntity.ok(ApiResponse.success(customer));
        } catch (StripeException e) {
            log.error("Error creating Stripe customer: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to create customer: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Create customer error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/add-card")
    public ResponseEntity<ApiResponse<Map<String, String>>> addCard(@Valid @RequestBody AddCardRequest request) {
        try {
            Token cardToken = stripeService.createCardToken(
                    request.getCardNumber(),
                    request.getExpMonth(),
                    request.getExpYear(),
                    request.getCvc(),
                    request.getCardName());
            
            com.stripe.model.Card card = stripeService.addCardToCustomer(
                    request.getCustomerId(),
                    cardToken.getId());
            
            Map<String, String> response = new HashMap<>();
            response.put("card", card.getId());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (StripeException e) {
            log.error("Error adding card: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to add card: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Add card error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/create-payment-intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        try {
            PaymentIntentResponse response = paymentService.createPaymentIntent(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            log.error("Error creating payment intent: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Create payment intent error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/confirm-payment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request) {
        try {
            Map<String, Object> response = paymentService.confirmPayment(request);
            return ResponseEntity.ok(ApiResponse.success(response, "Payment confirmed and transaction created"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            log.error("Error confirming payment: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Confirm payment error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        try {
            CheckoutSessionResponse response = paymentService.createCheckoutSession(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            log.error("Error creating checkout session: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to create checkout session: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Create checkout session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> handlePaymentWebhook(
            @RequestBody String payload,
            @RequestHeader("stripe-signature") String sigHeader) {
        try {
            if (webhookSecret == null || webhookSecret.isEmpty() || 
                "your-webhook-secret-from-stripe-dashboard".equals(webhookSecret)) {
                log.warn("Webhook secret not configured. Please set stripe.webhook-secret in application.properties");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Webhook secret not configured"));
            }

            com.stripe.model.Event event = stripeService.constructWebhookEvent(payload, sigHeader, webhookSecret);

            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    Map<String, String> metadata = session.getMetadata();
                    String clubId = metadata.get("clubId");
                    String playerId = metadata.get("playerId");
                    String sessionId = metadata.get("sessionId");
                    String sessionDate = metadata.get("sessionDate");
                    double charged = session.getAmountTotal() / 100.0;
                    String sessionAmountMeta = metadata.get("sessionAmount");
                    String platformFeeMeta = metadata.get("platformFee");
                    double clubAmount = sessionAmountMeta != null ? Double.parseDouble(sessionAmountMeta) : charged;
                    double platformFee = platformFeeMeta != null ? Double.parseDouble(platformFeeMeta) : 0.0;
                    if (sessionAmountMeta != null
                            && Math.abs((clubAmount + platformFee) - charged) > 0.02) {
                        throw new IllegalArgumentException("Checkout split does not match the amount paid");
                    }

                    com.squad.backend.model.Transaction transaction = new com.squad.backend.model.Transaction();
                    transaction.setClubId(clubId);
                    transaction.setPlayerId(playerId);
                    transaction.setSessionId(sessionId);
                    transaction.setAmount(clubAmount);
                    transaction.setPlatformFee(platformFee);
                    transaction.setCurrency(session.getCurrency().toUpperCase());
                    transaction.setType("payment");
                    transaction.setStatus("completed");
                    transaction.setSessionDate(java.time.LocalDate.parse(sessionDate));
                    transaction.setSessionStatus("pending");
                    transaction.setMoneyLocked(true);
                    transaction.setAvailableForWithdrawal(false);
                    transaction.setPaymentMethod("card");
                    transaction.setStripeTransactionId(session.getPaymentIntent());
                    transaction.setProcessingFee(0.0);
                    transaction.setDescription("Checkout session payment - " + clubAmount + " " + session.getCurrency().toUpperCase());
                    transaction.setNotes("Stripe Checkout Session: " + session.getId());
                    transaction.setCreatedAt(java.time.Instant.now());
                    transaction.setUpdatedAt(java.time.Instant.now());

                    transactionRepository.save(transaction);
                    clubWalletService.getOrCreateWallet(clubId);
                    clubWalletService.addEarnings(clubId, clubAmount);

                    log.info("Payment completed for session {}, transaction {} created", sessionId, transaction.getId());
                }
            }

            Map<String, Boolean> response = new HashMap<>();
            response.put("received", true);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (StripeException e) {
            log.error("Webhook signature verification failed: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Webhook Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Webhook error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/status/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getPaymentStatus(
            @PathVariable String transactionId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null || auth.getClubId() == null || auth.getClubId().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized or club not found"));
            }
            String clubId = auth.getClubId();
            TransactionResponse response = financeService.getTransactionById(clubId, transactionId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get payment status error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
