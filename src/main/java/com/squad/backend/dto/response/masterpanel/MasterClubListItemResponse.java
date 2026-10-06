package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterClubListItemResponse {
    private String clubId;
    private String clubName;
    private String seasonId;
    /** ACTIVE | EMPTY | INCOMPLETE */
    private String status;
    private Integer playerCount;
    private Integer teamCount;
    private Integer sessionCount;
    private Double totalEarnings;
    private Double platformCollected;
    private Double outstandingAmount;
    private Double availableForWithdrawal;
    private Double pendingWithdrawals;
    private Integer openWithdrawalCount;
    private String primaryAdminName;
    private String primaryAdminEmail;
}
