package com.squad.backend.dto.request.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class InvoicePaymentIntentRequest {
    @NotBlank
    private String invoiceId;

    @NotBlank
    private String token;

    @NotNull
    private Double amount;

    private String currency = "GBP";

    @NotBlank
    private String customerName;

    @NotBlank
    @Email
    private String customerEmail;

    private Map<String, String> billingAddress;
}
