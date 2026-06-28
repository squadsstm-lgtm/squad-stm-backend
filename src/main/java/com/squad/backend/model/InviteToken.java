package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "invite_tokens")
public class InviteToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String purpose;
    private String entityId;
    private String clubId;
    private String seasonId;
    private String channel;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    private String createdBy;
    private Map<String, String> metadata;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
