package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Document(collection = "requests")
@CompoundIndex(def = "{'clubId': 1, 'sessionId': 1}")
public class ConfirmationRequest {
    
    @Id
    private String id;
    
    private String clubId;
    private String seasonId;
    private String sessionId;
    private String playerId;
    private String teamId;
    private String playerAttendanceResponse;
    private String sessionAttendance;
    private String attendanceMarkedBy;
    private String attendanceMarkedByName;
    private LocalDateTime attendanceMarkedAt;
    private String payment;
    private String amount;
    private Boolean isActive;
    private String createdBy;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    @Field("__v")
    private Integer version;
}
