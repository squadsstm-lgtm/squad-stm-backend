package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "payment_invoices")
public class PaymentInvoice {

    @Id
    private String id;

    @Indexed
    private String clubId;
    @Indexed
    private String seasonId;
    @Indexed
    private String playerId;

    /** PENDING | PAID | CANCELLED */
    @Indexed
    private String status;

    private Double totalAmount;
    private String currency = "GBP";

    private List<LineItem> lineItems = new ArrayList<>();

    private String createdBy;
    private Instant paidAt;
    private String stripeTransactionId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class LineItem {
        private String requestId;
        private String sessionId;
        private String sessionName;
        private String sessionDate;
        private Double amount;
    }
}
