package com.squad.backend.dto.response.auth;

import com.squad.backend.dto.response.masterpanel.ControllerPermissionsResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String clubId;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    /** Present only for Controller role — loaded once at login. */
    private ControllerPermissionsResponse controllerPermissions;
    /** Pages whose first-visit walkthrough is already done. Empty until they finish or skip. */
    private List<String> seenWalkthroughPages;
}
