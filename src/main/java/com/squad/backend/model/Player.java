package com.squad.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "players")
public class Player {
    
    @Id
    @JsonProperty("_id")
    private String id;
    
    private String seasonId;
    private String clubId;
    private Map<String, String> teams; // { teamId → teamName }
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String parentName;
    private String parentEmail;
    private String emContact;
    private String emPhone;
    private String clubs;
    private String consentGiven;
    private String status;
    private String photoUploaded;
    private String profileImage;
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
