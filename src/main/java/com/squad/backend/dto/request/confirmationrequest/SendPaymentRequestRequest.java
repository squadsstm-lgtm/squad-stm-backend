package com.squad.backend.dto.request.confirmationrequest;

import lombok.Data;

@Data
public class SendPaymentRequestRequest {
    private String sessionId;
    private String playerId;
}
