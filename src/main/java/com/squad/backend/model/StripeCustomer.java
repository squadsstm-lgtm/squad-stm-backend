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
import java.time.LocalDate;

@Data
@Document(collection = "stripecustomers")
public class StripeCustomer {
    
    @Id
    private String id;
    
    @Indexed(unique = true, sparse = true)
    private String stripeCustomerId;
    
    @Indexed
    private String playerId;
    @Indexed
    private String userId;
    @Indexed
    private String clubId;
    
    private String customerName;
    private String customerEmail;
    private Integer totalPayments = 0;
    private Double totalAmount = 0.0;
    private LocalDate lastPaymentDate;
    private String customerType;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    @Field("__v")
    private Integer version;

    public void updatePaymentStats(Double amount) {
        this.totalPayments = (this.totalPayments == null ? 0 : this.totalPayments) + 1;
        this.totalAmount = (this.totalAmount == null ? 0.0 : this.totalAmount) + amount;
        this.lastPaymentDate = LocalDate.now();
    }
}
