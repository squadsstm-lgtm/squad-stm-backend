package com.squad.backend.dto.response.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    @JsonProperty("_id")
    private String id;
    private String clubId;
    private String seasonId;
    private String playerId;
    private String sessionId;
    private String teamId;
    private Double amount;
    private String currency;
    private String type;
    private String status;
    private LocalDate sessionDate;
    private String sessionStatus;
    private Boolean moneyLocked;
    private Boolean availableForWithdrawal;
    private String paymentMethod;
    private String stripeTransactionId;
    private String stripeCustomerId;
    private Double processingFee;
    private Instant createdAt;
    private Instant completedAt;
}
