package com.squad.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCardRequest {
    
    @NotBlank(message = "Customer ID is required")
    private String customerId;
    
    @NotBlank(message = "Card name is required")
    private String cardName;
    
    @NotBlank(message = "Card number is required")
    private String cardNumber;
    
    @NotBlank(message = "Expiration month is required")
    private String expMonth;
    
    @NotBlank(message = "Expiration year is required")
    private String expYear;
    
    @NotBlank(message = "CVC is required")
    private String cvc;
}
