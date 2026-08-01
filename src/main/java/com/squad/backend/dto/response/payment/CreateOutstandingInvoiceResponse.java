package com.squad.backend.dto.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOutstandingInvoiceResponse {
    private String invoiceId;
    private String playerId;
    private String playerName;
    private String paymentUrl;
    private Double totalAmount;
    private int lineItemCount;
    private String communicationMethod;
    private String message;
}
