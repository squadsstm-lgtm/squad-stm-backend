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

    /** What the player pays: session total plus the frozen Squad fee. */
    private Double totalAmount;
    /** Session prices only. This is the amount credited to the club. */
    private Double sessionTotal;
    /** Squad fee for one session, copied from the club when the invoice was sent. */
    private Double platformFeePerSession;
    /** platformFeePerSession times the number of sessions. */
    private Double platformFeeTotal;
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
