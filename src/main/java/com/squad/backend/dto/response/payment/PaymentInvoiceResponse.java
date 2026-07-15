package com.squad.backend.dto.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInvoiceResponse {
    private String id;
    private String clubId;
    private String seasonId;
    private String playerId;
    private String playerName;
    private String playerEmail;
    private String status;
    private Double totalAmount;
    private String currency;
    private List<LineItemResponse> lineItems;
    private boolean alreadyPaid;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemResponse {
        private String requestId;
        private String sessionId;
        private String sessionName;
        private String sessionDate;
        private Double amount;
    }
}
