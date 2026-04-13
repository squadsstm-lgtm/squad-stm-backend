package com.squad.backend.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String clubId;
    private String seasonId;
    private String firstName;
    private String lastName;
    private String userName;
    private String clubName;
    private String email;
    private String phone;
    private Boolean isVerified;
    private String roleId;
    private String role;
    private String userId;
    private Boolean isBlocked;
    private Boolean hasMpin;
    private Integer version;
}
