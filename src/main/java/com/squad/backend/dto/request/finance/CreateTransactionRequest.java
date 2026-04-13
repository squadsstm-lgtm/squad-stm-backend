package com.squad.backend.dto.request.finance;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTransactionRequest {
    private String clubId;
    private String seasonId;
    private String playerId;
    private String sessionId;
    private String teamId;
    private Double amount;
    private String currency = "GBP";
    private String type;
    private String status;
    private String paymentMethod;
    private String stripeTransactionId;
    private String description;
    private String notes;
    private LocalDate sessionDate;
}
