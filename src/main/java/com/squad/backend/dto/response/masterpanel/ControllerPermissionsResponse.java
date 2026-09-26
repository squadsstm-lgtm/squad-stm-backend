package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControllerPermissionsResponse {
    private Boolean viewDashboard;
    private Boolean viewRequests;
    private Boolean workRequests;
    private Boolean viewRequestPaymentDetails;
    private Boolean viewClubs;
    private Boolean viewClubMoney;
    private Boolean inviteControllers;
    private Boolean manageControllerPermissions;

    public static ControllerPermissionsResponse allTrue() {
        return ControllerPermissionsResponse.builder()
                .viewDashboard(true)
                .viewRequests(true)
                .workRequests(true)
                .viewRequestPaymentDetails(true)
                .viewClubs(true)
                .viewClubMoney(true)
                .inviteControllers(true)
                .manageControllerPermissions(true)
                .build();
    }

    public static ControllerPermissionsResponse basicDefaults() {
        return ControllerPermissionsResponse.builder()
                .viewDashboard(true)
                .viewRequests(true)
                .workRequests(false)
                .viewRequestPaymentDetails(false)
                .viewClubs(true)
                .viewClubMoney(false)
                .inviteControllers(false)
                .manageControllerPermissions(false)
                .build();
    }

    public static ControllerPermissionsResponse none() {
        return ControllerPermissionsResponse.builder()
                .viewDashboard(false)
                .viewRequests(false)
                .workRequests(false)
                .viewRequestPaymentDetails(false)
                .viewClubs(false)
                .viewClubMoney(false)
                .inviteControllers(false)
                .manageControllerPermissions(false)
                .build();
    }
}
