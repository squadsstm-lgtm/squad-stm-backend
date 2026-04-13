package com.squad.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ConfirmPaymentRequest {
    
    @NotBlank(message = "Payment intent ID is required")
    private String paymentIntentId;
    
    @NotBlank(message = "Club ID is required")
    private String clubId;
    
    @NotBlank(message = "Player ID is required")
    private String playerId;
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Request ID is required")
    private String requestId;

    @NotBlank(message = "Token is required")
    private String token;
    
    @NotBlank(message = "Session date is required")
    private String sessionDate;
    
    @NotNull(message = "Amount is required")
    private Double amount;
    
    private String currency = "GBP";
    private String teamId;
    private String seasonId;
    private Map<String, String> billingAddress;
}
