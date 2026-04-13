package com.squad.backend.dto.response.confirmationrequest;

import com.squad.backend.model.ConfirmationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentResponse {
    private ConfirmationRequest request;
    private TransactionInfo transaction;
    private WalletInfo wallet;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInfo {
        private String id;
        private Double amount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletInfo {
        private Double totalEarnings;
        private Double availableForWithdrawal;
    }
}
