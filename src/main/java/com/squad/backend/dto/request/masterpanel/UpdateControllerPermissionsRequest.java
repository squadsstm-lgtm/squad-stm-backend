package com.squad.backend.dto.request.masterpanel;

import lombok.Data;

@Data
public class UpdateControllerPermissionsRequest {
    private Boolean viewDashboard;
    private Boolean viewRequests;
    private Boolean workRequests;
    private Boolean viewRequestPaymentDetails;
    private Boolean viewClubs;
    private Boolean viewClubMoney;
    private Boolean inviteControllers;
    private Boolean manageControllerPermissions;
}
