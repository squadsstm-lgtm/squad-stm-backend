package com.squad.backend.dto.response.clubwallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestResponse {
    @JsonProperty("_id")
    private String id;
    private String clubId;
    private String clubName;
    private Double amount;
    private String currency;
    private String status;
    private Double processingFee;
    private Double netAmount;
    private BankAccountInfo bankAccount;
    private Instant requestedAt;
    private Instant estimatedCompletion;
    private Instant processedAt;
    private String bankReference;
    private String stripeTransferId;
    private String description;
    private String notes;
    private String createdBy;
    private String createdByName;
    private String processedBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccountInfo {
        private String accountNumber;
        private String sortCode;
        private String accountName;
    }
}
