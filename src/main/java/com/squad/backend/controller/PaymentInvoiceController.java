package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.payment.ConfirmInvoicePaymentRequest;
import com.squad.backend.dto.request.payment.CreateOutstandingInvoiceRequest;
import com.squad.backend.dto.request.payment.InvoicePaymentIntentRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PaymentIntentResponse;
import com.squad.backend.dto.response.payment.CreateOutstandingInvoiceResponse;
import com.squad.backend.dto.response.payment.PaymentInvoiceResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.PaymentInvoiceService;
import com.squad.backend.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/invoices")
@RequiredArgsConstructor
@Slf4j
public class PaymentInvoiceController {

    private final PaymentInvoiceService paymentInvoiceService;
    private final PaymentService paymentService;

    @PostMapping("/outstanding")
    public ResponseEntity<ApiResponse<CreateOutstandingInvoiceResponse>> createOutstanding(
            @Valid @RequestBody CreateOutstandingInvoiceRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            CreateOutstandingInvoiceResponse response =
                    paymentInvoiceService.createOutstandingInvoice(request, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Create outstanding invoice error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<PaymentInvoiceResponse>> getInvoice(
            @PathVariable String id,
            @PathVariable String token) {
        try {
            PaymentInvoiceResponse response = paymentInvoiceService.getInvoiceForPayment(id, token);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get invoice error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    /** Single-session payment link rendered as an invoice (one line item). */
    @GetMapping("/from-request/{requestId}/{token}")
    public ResponseEntity<ApiResponse<PaymentInvoiceResponse>> getInvoiceFromRequest(
            @PathVariable String requestId,
            @PathVariable String token) {
        try {
            PaymentInvoiceResponse response =
                    paymentInvoiceService.getInvoiceFromConfirmationRequest(requestId, token);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get invoice from request error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/create-payment-intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @Valid @RequestBody InvoicePaymentIntentRequest request) {
        try {
            PaymentIntentResponse response = paymentService.createInvoicePaymentIntent(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Invoice create payment intent error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/confirm-payment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmPayment(
            @Valid @RequestBody ConfirmInvoicePaymentRequest request) {
        try {
            Map<String, Object> response = paymentService.confirmInvoicePayment(request);
            return ResponseEntity.ok(ApiResponse.success(response, "Invoice payment confirmed"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Invoice confirm payment error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
