package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "sessions")
public class Session {
    
    @Id
    private String id;
    
    private String seasonId;
    private String clubId;
    private Boolean active;
    private String sessionName;
    private String sessionType;
    private String location;
    private String teamList;
    private String date;
    private String price;
    private String additionalPlayers;
    private String notes;
    private Boolean isActive;
    private String createdBy;
    private String updatedBy;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    @Field("__v")
    private Integer version;
}
