package com.squad.backend.dto.request.confirmationrequest;

import lombok.Data;

@Data
public class SendPaymentRequestRequest {
    private String sessionId;
    private String playerId;
    /** Optional: "email" (default) or "whatsapp". */
    private String communicationMethod;
}
