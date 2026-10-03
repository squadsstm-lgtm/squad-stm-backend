package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per Master Panel Controller (authId unique).
 * Boolean flags map 1:1 to the Controllers permissions popup.
 */
@Data
@Document(collection = "controller_permissions")
public class ControllerPermissions {

    @Id
    private String id;

    /** Auth document id of the Controller. Unique — one row each. */
    @Indexed(unique = true)
    private String authId;

    private Boolean viewDashboard;
    private Boolean viewRequests;
    /** Approve / reject / update withdrawal status. */
    private Boolean workRequests;
    /** View bank account details on a withdrawal. */
    private Boolean viewRequestPaymentDetails;
    private Boolean viewClubs;
    /** See money totals on clubs list, summary, and club detail. */
    private Boolean viewClubMoney;
    private Boolean inviteControllers;
    private Boolean manageControllerPermissions;

    private String createdBy;
    private String updatedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
