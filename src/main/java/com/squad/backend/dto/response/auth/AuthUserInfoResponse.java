package com.squad.backend.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AuthUserInfoResponse {
    @JsonProperty("_id")
    private String mongoId;
    private String id;
    private String uid;
    private String email;
    private String name;
    private String firstName;
    private String lastName;
    private String clubId;
    private String seasonId;
    private String role;
    private String roleId;
    private Boolean isVerified;
    private Boolean isBlocked;
    private String userName;
    private String phone;
    private Boolean hasMpin;
    private Object seasonDetails;
    /** Present only for Controller role. */
    private ControllerPermissionsResponse controllerPermissions;
    /** Pages whose first-visit walkthrough is already done. */
    private List<String> seenWalkthroughPages;
}
