package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "withdrawalrequests")
public class WithdrawalRequest {
    
    @Id
    private String id;
    
    @Indexed
    private String clubId;
    
    private Double amount;
    private String currency = "GBP";
    @Indexed
    private String status;
    
    private Double processingFee = 0.0;
    private Double netAmount;
    
    private BankAccount bankAccount;
    
    @Data
    public static class BankAccount {
        private String accountNumber;
        private String sortCode;
        private String accountName;
    }
    
    @Indexed
    private Instant requestedAt;
    private Instant estimatedCompletion;
    private Instant processedAt;
    
    @Indexed(unique = true, sparse = true)
    private String bankReference;
    @Indexed(unique = true, sparse = true)
    private String stripeTransferId;
    
    private String description;
    private String notes;
    private String createdBy;
    private String processedBy;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    @Field("__v")
    private Integer version;
}
