package com.squad.backend.service;

import com.squad.backend.dto.request.ConfirmPaymentRequest;
import com.squad.backend.dto.request.CreatePaymentIntentRequest;
import com.squad.backend.dto.request.payment.ConfirmInvoicePaymentRequest;
import com.squad.backend.dto.request.payment.InvoicePaymentIntentRequest;
import com.squad.backend.dto.response.PaymentIntentResponse;
import com.squad.backend.model.ClubWallet;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.PaymentInvoice;
import com.squad.backend.model.StripeCustomer;
import com.squad.backend.model.Transaction;
import com.squad.backend.repository.ConfirmationRequestRepository;
import com.squad.backend.repository.PaymentInvoiceRepository;
import com.squad.backend.repository.StripeCustomerRepository;
import com.squad.backend.repository.TransactionRepository;
import com.squad.backend.security.JwtTokenProvider;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StripeService stripeService;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final TransactionRepository transactionRepository;
    private final ConfirmationRequestRepository confirmationRequestRepository;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final ClubWalletService clubWalletService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PaymentInvoiceService paymentInvoiceService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request) throws StripeException {
        validateEmailLinkPaymentAccess(
                request.getRequestId(),
                request.getToken(),
                request.getClubId(),
                request.getPlayerId(),
                request.getSessionId()
        );

        // Handle amount as String or Double (frontend may send string)
        Double amount = request.getAmount();
        if (amount == null) {
            throw new IllegalArgumentException("Missing required field: amount");
        }
        
        if (request.getClubId() == null || 
            request.getPlayerId() == null || request.getSessionId() == null || 
            request.getSessionDate() == null) {
            throw new IllegalArgumentException("Missing required fields: clubId, playerId, sessionId, sessionDate");
        }

        Optional<Transaction> existingPayment = transactionRepository.findBySessionIdAndPlayerIdAndStatus(
                request.getSessionId(), request.getPlayerId(), "completed");
        if (existingPayment.isPresent()) {
            throw new IllegalArgumentException("You have already paid for this session");
        }

        StripeCustomer stripeCustomer = findOrCreateCustomer(
                request.getPlayerId(),
                request.getUserId(),
                request.getClubId(),
                request.getCustomerName() != null ? request.getCustomerName() : "Customer",
                request.getCustomerEmail());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("clubId", request.getClubId());
        metadata.put("playerId", request.getPlayerId());
        metadata.put("sessionId", request.getSessionId());
        metadata.put("sessionDate", request.getSessionDate());
        metadata.put("stripeCustomerId", stripeCustomer.getId());
        if (request.getBillingAddress() != null) {
            metadata.put("billingCountry", request.getBillingAddress().getOrDefault("country", ""));
            metadata.put("billingPostcode", request.getBillingAddress().getOrDefault("postcode", ""));
        }

        Long amountInCents = Math.round(request.getAmount() * 100);
        PaymentIntent paymentIntent = stripeService.createPaymentIntent(
                amountInCents,
                request.getCurrency(),
                metadata);

        return PaymentIntentResponse.builder()
                .clientSecret(paymentIntent.getClientSecret())
                .id(paymentIntent.getId())
                .build();
    }

    @Transactional
    public Map<String, Object> confirmPayment(ConfirmPaymentRequest request) throws StripeException {
        ConfirmationRequest linkedRequest = validateEmailLinkPaymentAccess(
                request.getRequestId(),
                request.getToken(),
                request.getClubId(),
                request.getPlayerId(),
                request.getSessionId()
        );

        if (request.getPaymentIntentId() == null || request.getClubId() == null ||
            request.getPlayerId() == null || request.getSessionId() == null ||
            request.getSessionDate() == null || request.getAmount() == null) {
            throw new IllegalArgumentException("Missing required fields");
        }

        PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(request.getPaymentIntentId());
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new IllegalArgumentException("Payment not completed");
        }

        String chargeId = null;
        try {
            if (paymentIntent.getLatestCharge() != null) {
                chargeId = paymentIntent.getLatestCharge();
            }
        } catch (Exception e) {
            log.warn("Could not get latest charge from payment intent: {}", e.getMessage());
        }
        if (chargeId == null || chargeId.isEmpty()) {
            throw new IllegalArgumentException("Payment intent does not have a charge");
        }
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeTransactionId(chargeId);
        if (existingTransaction.isPresent()) {
            throw new IllegalArgumentException("This payment has already been processed");
        }

        String stripeCustomerId = paymentIntent.getMetadata().get("stripeCustomerId");
        StripeCustomer stripeCustomer = stripeCustomerRepository.findById(stripeCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Stripe customer not found"));

        if (stripeCustomer.getStripeCustomerId() == null || stripeCustomer.getStripeCustomerId().isEmpty()) {
            // Create Stripe customer with metadata (matching Node.js behavior)
            Map<String, String> metadata = new HashMap<>();
            metadata.put("playerId", request.getPlayerId());
            if (stripeCustomer.getUserId() != null) {
                metadata.put("userId", stripeCustomer.getUserId());
            }
            metadata.put("clubId", request.getClubId());
            metadata.put("ourCustomerId", stripeCustomer.getId());
            
            com.stripe.model.Customer stripeCustomerData = stripeService.createCustomerWithMetadata(
                    stripeCustomer.getCustomerName() != null ? stripeCustomer.getCustomerName() : "Customer",
                    stripeCustomer.getCustomerEmail(),
                    metadata);
            stripeCustomer.setStripeCustomerId(stripeCustomerData.getId());
            stripeCustomerRepository.save(stripeCustomer);
        }

        try {
            String paymentMethodId = null;
            if (paymentIntent.getPaymentMethod() != null) {
                paymentMethodId = paymentIntent.getPaymentMethod();
            }
            if (paymentMethodId != null && !paymentMethodId.isEmpty()) {
                stripeService.setDefaultPaymentMethod(
                        stripeCustomer.getStripeCustomerId(),
                        paymentMethodId);
            }
        } catch (Exception e) {
            log.warn("Could not set default payment method: {}", e.getMessage());
        }

        com.squad.backend.model.Transaction transaction = new com.squad.backend.model.Transaction();
        transaction.setClubId(request.getClubId());
        transaction.setSeasonId(request.getSeasonId());
        transaction.setPlayerId(request.getPlayerId());
        transaction.setSessionId(request.getSessionId());
        transaction.setTeamId(request.getTeamId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setType("payment");
        transaction.setStatus("completed");
        try {
            transaction.setSessionDate(java.time.LocalDate.parse(request.getSessionDate()));
        } catch (Exception e) {
            transaction.setSessionDate(LocalDate.now());
        }
        transaction.setSessionStatus("pending");
        transaction.setMoneyLocked(true);
        transaction.setAvailableForWithdrawal(false);
        transaction.setPaymentMethod("card");
        transaction.setStripeTransactionId(chargeId);
        transaction.setStripeCustomerId(stripeCustomer.getId());
        transaction.setStripeCustomerStripeId(stripeCustomer.getStripeCustomerId());
        transaction.setProcessingFee(0.0);
        transaction.setDescription("Session payment - " + request.getAmount() + " " + request.getCurrency());
        transaction.setNotes("Stripe Payment Intent: " + request.getPaymentIntentId());
        if (request.getBillingAddress() != null) {
            com.squad.backend.model.Transaction.BillingAddress billingAddress = 
                    new com.squad.backend.model.Transaction.BillingAddress();
            billingAddress.setCountry(request.getBillingAddress().getOrDefault("country", null));
            billingAddress.setPostcode(request.getBillingAddress().getOrDefault("postcode", null));
            transaction.setBillingAddress(billingAddress);
        }
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());
        transaction.setCompletedAt(Instant.now());

        transaction = transactionRepository.save(transaction);

        clubWalletService.addEarnings(request.getClubId(), request.getAmount());

        stripeCustomer.updatePaymentStats(request.getAmount());
        stripeCustomerRepository.save(stripeCustomer);

        linkedRequest.setPayment("Yes");
        linkedRequest.setUpdatedAt(Instant.now());
        confirmationRequestRepository.save(linkedRequest);

        ClubWallet wallet = clubWalletService.getOrCreateWallet(request.getClubId());

        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transaction.getId());
        result.put("amount", transaction.getAmount());
        result.put("status", transaction.getStatus());
        result.put("walletBalance", wallet.getTotalEarnings());
        result.put("requestUpdated", true);

        return result;
    }

    public PaymentIntentResponse createInvoicePaymentIntent(InvoicePaymentIntentRequest request) throws StripeException {
        PaymentInvoice invoice = paymentInvoiceService.validateInvoiceAccess(request.getInvoiceId(), request.getToken());
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalArgumentException("This invoice has already been paid");
        }
        if (request.getAmount() == null || Math.abs(request.getAmount() - invoice.getTotalAmount()) > 0.009) {
            throw new IllegalArgumentException("Payment amount does not match invoice total");
        }

        StripeCustomer stripeCustomer = findOrCreateCustomer(
                invoice.getPlayerId(),
                null,
                invoice.getClubId(),
                request.getCustomerName() != null ? request.getCustomerName() : "Customer",
                request.getCustomerEmail());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("clubId", invoice.getClubId());
        metadata.put("playerId", invoice.getPlayerId());
        metadata.put("invoiceId", invoice.getId());
        metadata.put("paymentType", "invoice");
        metadata.put("stripeCustomerId", stripeCustomer.getId());

        Long amountInCents = Math.round(request.getAmount() * 100);
        PaymentIntent paymentIntent = stripeService.createPaymentIntent(
                amountInCents,
                request.getCurrency() != null ? request.getCurrency() : "GBP",
                metadata);

        return PaymentIntentResponse.builder()
                .clientSecret(paymentIntent.getClientSecret())
                .id(paymentIntent.getId())
                .build();
    }

    @Transactional
    public Map<String, Object> confirmInvoicePayment(ConfirmInvoicePaymentRequest request) throws StripeException {
        PaymentInvoice invoice = paymentInvoiceService.validateInvoiceAccess(request.getInvoiceId(), request.getToken());
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalArgumentException("This invoice has already been paid");
        }
        if (request.getAmount() == null || Math.abs(request.getAmount() - invoice.getTotalAmount()) > 0.009) {
            throw new IllegalArgumentException("Payment amount does not match invoice total");
        }
        if (request.getPaymentIntentId() == null) {
            throw new IllegalArgumentException("Missing required fields");
        }

        PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(request.getPaymentIntentId());
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new IllegalArgumentException("Payment not completed");
        }

        String metaInvoiceId = paymentIntent.getMetadata() != null
                ? paymentIntent.getMetadata().get("invoiceId")
                : null;
        if (metaInvoiceId == null || !metaInvoiceId.equals(invoice.getId())) {
            throw new IllegalArgumentException("Payment does not match this invoice");
        }
        String paymentType = paymentIntent.getMetadata() != null
                ? paymentIntent.getMetadata().get("paymentType")
                : null;
        if (paymentType != null && !"invoice".equalsIgnoreCase(paymentType)) {
            throw new IllegalArgumentException("Payment does not match this invoice");
        }

        long expectedCents = Math.round(invoice.getTotalAmount() * 100);
        Long paidCents = paymentIntent.getAmountReceived() != null && paymentIntent.getAmountReceived() > 0
                ? paymentIntent.getAmountReceived()
                : paymentIntent.getAmount();
        if (paidCents == null || Math.abs(paidCents - expectedCents) > 1) {
            throw new IllegalArgumentException("Paid amount does not match invoice total");
        }

        if (invoice.getLineItems() != null) {
            for (PaymentInvoice.LineItem line : invoice.getLineItems()) {
                if (line.getRequestId() == null) {
                    continue;
                }
                ConfirmationRequest alreadyPaid = confirmationRequestRepository.findById(line.getRequestId()).orElse(null);
                if (alreadyPaid != null && "Yes".equalsIgnoreCase(alreadyPaid.getPayment())) {
                    throw new IllegalArgumentException(
                            "One or more sessions on this invoice were already paid. Ask your club for a new outstanding invoice link.");
                }
            }
        }

        String chargeId = null;
        try {
            if (paymentIntent.getLatestCharge() != null) {
                chargeId = paymentIntent.getLatestCharge();
            }
        } catch (Exception e) {
            log.warn("Could not get latest charge from payment intent: {}", e.getMessage());
        }
        if (chargeId == null || chargeId.isEmpty()) {
            throw new IllegalArgumentException("Payment intent does not have a charge");
        }
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeTransactionId(chargeId);
        if (existingTransaction.isPresent()) {
            throw new IllegalArgumentException("This payment has already been processed");
        }

        String stripeCustomerId = paymentIntent.getMetadata().get("stripeCustomerId");
        StripeCustomer stripeCustomer = stripeCustomerRepository.findById(stripeCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Stripe customer not found"));

        Transaction transaction = new Transaction();
        transaction.setClubId(invoice.getClubId());
        transaction.setSeasonId(invoice.getSeasonId());
        transaction.setPlayerId(invoice.getPlayerId());
        transaction.setSessionId(invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()
                ? invoice.getLineItems().get(0).getSessionId()
                : null);
        transaction.setAmount(invoice.getTotalAmount());
        transaction.setCurrency(request.getCurrency() != null ? request.getCurrency() : "GBP");
        transaction.setType("payment");
        transaction.setStatus("completed");
        transaction.setSessionDate(LocalDate.now());
        transaction.setSessionStatus("pending");
        transaction.setMoneyLocked(true);
        transaction.setAvailableForWithdrawal(false);
        transaction.setPaymentMethod("card");
        transaction.setStripeTransactionId(chargeId);
        transaction.setStripeCustomerId(stripeCustomer.getId());
        transaction.setStripeCustomerStripeId(stripeCustomer.getStripeCustomerId());
        transaction.setProcessingFee(0.0);
        transaction.setDescription("Outstanding invoice payment - " + invoice.getTotalAmount() + " "
                + (request.getCurrency() != null ? request.getCurrency() : "GBP"));
        transaction.setNotes("Invoice: " + invoice.getId() + "; Stripe PI: " + request.getPaymentIntentId());
        if (invoice.getLineItems() != null) {
            transaction.setTags(invoice.getLineItems().stream()
                    .map(PaymentInvoice.LineItem::getRequestId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList());
        }
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());
        transaction.setCompletedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        clubWalletService.addEarnings(invoice.getClubId(), invoice.getTotalAmount());
        stripeCustomer.updatePaymentStats(invoice.getTotalAmount());
        stripeCustomerRepository.save(stripeCustomer);

        Instant now = Instant.now();
        if (invoice.getLineItems() != null) {
            for (PaymentInvoice.LineItem line : invoice.getLineItems()) {
                if (line.getRequestId() == null) {
                    continue;
                }
                confirmationRequestRepository.findById(line.getRequestId()).ifPresent(cr -> {
                    cr.setPayment("Yes");
                    cr.setUpdatedAt(now);
                    confirmationRequestRepository.save(cr);
                });
            }
        }

        invoice.setStatus("PAID");
        invoice.setPaidAt(now);
        invoice.setStripeTransactionId(chargeId);
        invoice.setUpdatedAt(now);
        paymentInvoiceRepository.save(invoice);

        ClubWallet wallet = clubWalletService.getOrCreateWallet(invoice.getClubId());
        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transaction.getId());
        result.put("amount", transaction.getAmount());
        result.put("status", transaction.getStatus());
        result.put("walletBalance", wallet.getTotalEarnings());
        result.put("invoiceUpdated", true);
        result.put("settledRequestCount", invoice.getLineItems() != null ? invoice.getLineItems().size() : 0);
        return result;
    }

    private ConfirmationRequest validateEmailLinkPaymentAccess(
            String requestId,
            String token,
            String clubId,
            String playerId,
            String sessionId) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new SecurityException("This Link Is Expired");
        }
        String tokenSubject = jwtTokenProvider.getUserIdFromToken(token);
        if (requestId == null || requestId.isBlank() || !requestId.equals(tokenSubject)) {
            throw new SecurityException("Invalid payment link");
        }

        ConfirmationRequest request = confirmationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!request.getClubId().equals(clubId)
                || !request.getPlayerId().equals(playerId)
                || !request.getSessionId().equals(sessionId)) {
            throw new SecurityException("Payment request does not match link");
        }
        return request;
    }

    private StripeCustomer findOrCreateCustomer(String playerId, String userId, String clubId,
                                                String customerName, String customerEmail) {
        Optional<StripeCustomer> existing = stripeCustomerRepository.findByPlayerIdAndClubId(playerId, clubId);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (userId != null) {
            existing = stripeCustomerRepository.findByUserIdAndClubId(userId, clubId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        StripeCustomer customer = new StripeCustomer();
        customer.setPlayerId(playerId);
        customer.setUserId(userId);
        customer.setClubId(clubId);
        customer.setCustomerName(customerName);
        customer.setCustomerEmail(customerEmail);
        if (playerId != null && userId != null) {
            customer.setCustomerType("both");
        } else if (playerId != null) {
            customer.setCustomerType("player");
        } else {
            customer.setCustomerType("user");
        }
        customer.setTotalPayments(0);
        customer.setTotalAmount(0.0);
        customer.setCreatedAt(Instant.now());
        customer.setUpdatedAt(Instant.now());

        return stripeCustomerRepository.save(customer);
    }
}
