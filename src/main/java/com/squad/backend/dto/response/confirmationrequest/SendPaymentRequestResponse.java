package com.squad.backend.dto.response.confirmationrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendPaymentRequestResponse {
    private boolean success;
    private String requestId;
    private String paymentUrl;
    private String message;
}
