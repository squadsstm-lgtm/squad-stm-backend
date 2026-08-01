package com.squad.backend.dto.request.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ConfirmInvoicePaymentRequest {
    @NotBlank
    private String paymentIntentId;

    @NotBlank
    private String invoiceId;

    @NotBlank
    private String token;

    @NotNull
    private Double amount;

    private String currency = "GBP";

    private Map<String, String> billingAddress;
}
