package com.squad.backend.dto.request.confirmationrequest;

import lombok.Data;

@Data
public class UpdatePaymentRequest {
    private String payment;
    private String requestId;
    private String paymentMethod = "card";
    private String stripeTransactionId;
}
