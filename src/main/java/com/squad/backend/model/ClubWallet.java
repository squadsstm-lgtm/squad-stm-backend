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
@Document(collection = "clubwallets")
public class ClubWallet {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String clubId;
    
    private Double totalEarnings = 0.0;
    private Double lockedEarnings = 0.0;
    private Double availableForWithdrawal = 0.0;
    private Double totalWithdrawn = 0.0;
    private Double pendingWithdrawals = 0.0;
    private Double processingFees = 0.0;
    private Double monthlyRevenue = 0.0;
    private Double yearlyRevenue = 0.0;
    private Double totalRefunds = 0.0;
    
    /** Payout bank account (Phase 2) */
    private String payoutAccountName;
    private String payoutSortCode;
    private String payoutAccountNumber;
    
    private Instant lastUpdated;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    @Field("__v")
    private Integer version;
}
