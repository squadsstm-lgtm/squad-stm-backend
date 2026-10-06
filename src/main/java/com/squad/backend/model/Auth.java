package com.squad.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Document(collection = "auths")
public class Auth {
    
    @Id
    private String id;
    
    private String clubId;
    private String seasonId;
    private String firstName;
    private String lastName;
    private String userName;
    private String clubName;
    
    @Indexed(unique = true)
    private String email;
    
    /** Unique only when a number is present. Email invites can omit the phone. */
    @Indexed(unique = true, partialFilter = "{ 'phone': { '$gt': '' } }")
    private String phone;

    @JsonIgnore
    private String password;
    /** MPIN (hashed) for Club Wallet access. Empty until user sets it. */
    @JsonIgnore
    private String mpin;
    /** One-time token for forgot MPIN flow. Cleared after use or expiry. */
    private String forgotMpinToken;
    /** Expiry time for forgotMpinToken (epoch millis). */
    private Long forgotMpinTokenExpiry;
    private Boolean isVerified;
    private String roleId;
    private String role;
    private String userId;
    private Boolean isBlocked;
    /** Soft off for Controllers (and optional elsewhere). Distinct from isBlocked. */
    private Boolean isInactive;
    /**
     * Page keys whose first-visit walkthrough was finished or skipped.
     * One set on the account, shared by every browser. Stored as an array; treated as a set.
     */
    private Set<String> seenWalkthroughPages = new LinkedHashSet<>();
    
    @Version
    @Field("__v")
    private Integer version;
}
