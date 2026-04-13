package com.squad.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCheckoutSessionRequest {
    
    @NotNull(message = "Amount is required")
    private Double amount;
    
    private String currency = "GBP";
    
    @NotBlank(message = "Club ID is required")
    private String clubId;
    
    @NotBlank(message = "Player ID is required")
    private String playerId;
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;
    
    @NotBlank(message = "Session date is required")
    private String sessionDate;
    
    private String successUrl;
    private String cancelUrl;
}
