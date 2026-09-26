package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControllerListItemResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    /** ACTIVE | PENDING | INACTIVE | BLOCKED */
    private String status;
    private Boolean isVerified;
    private Boolean isBlocked;
    private Boolean isInactive;
    private ControllerPermissionsResponse permissions;
    private Instant inviteExpiresAt;
}
