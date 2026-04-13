package com.squad.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreatePaymentIntentRequest {
    
    @NotNull(message = "Amount is required")
    private Double amount;
    
    private String currency = "GBP";
    
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
    
    private String userId;
    
    @NotBlank(message = "Customer name is required")
    private String customerName;
    
    @NotBlank(message = "Valid customer email is required")
    @jakarta.validation.constraints.Email(message = "Valid customer email is required")
    private String customerEmail;
    
    private Map<String, String> billingAddress;
}
