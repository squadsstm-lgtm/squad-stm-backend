package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One document for Squad-wide settings. The id is always "platform".
 * Today this only stores the default platform fee copied onto a new club.
 */
@Data
@Document(collection = "platform_settings")
public class PlatformSettings {

    public static final String SINGLETON_ID = "platform";

    @Id
    private String id;

    private Double defaultPlatformFee;

    private Instant updatedAt;

    private String updatedBy;
}
