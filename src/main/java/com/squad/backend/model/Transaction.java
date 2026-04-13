package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Document(collection = "transactions")
@CompoundIndex(def = "{'clubId': 1, 'type': 1, 'status': 1, 'moneyLocked': 1}")
public class Transaction {
    
    @Id
    private String id;
    
    @Indexed
    private String clubId;
    @Indexed
    private String seasonId;
    @Indexed
    private String playerId;
    @Indexed
    private String sessionId;
    @Indexed
    private String teamId;
    
    private Double amount;
    private String currency = "GBP";
    @Indexed
    private String type;
    @Indexed
    private String status;
    
    private LocalDate sessionDate;
    private String sessionStatus;
    private Boolean moneyLocked = true;
    private Boolean availableForWithdrawal = false;
    
    private String paymentMethod;
    @Indexed(unique = true, sparse = true)
    private String stripeTransactionId;
    @Indexed
    private String stripeCustomerId;
    @Indexed
    private String stripeCustomerStripeId;
    private Double processingFee = 0.0;
    
    private BillingAddress billingAddress;
    
    @Data
    public static class BillingAddress {
        private String country;
        private String postcode;
    }
    
    @CreatedDate
    @Indexed
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    private Instant completedAt;
    
    private String description;
    private String notes;
    private List<String> tags;
    
    private String refundStatus;
    private Double refundAmount = 0.0;
    private String refundReason;
    private Instant refundedAt;
    
    @Version
    @Field("__v")
    private Integer version;
}
