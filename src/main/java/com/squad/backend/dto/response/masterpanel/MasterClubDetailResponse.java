package com.squad.backend.dto.response.masterpanel;

import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterClubDetailResponse {
    private String clubId;
    private String clubName;
    private String seasonId;
    private String seasonLabel;
    private String status;
    private Integer playerCount;
    private Integer activePlayerCount;
    private Integer inactivePlayerCount;
    private Integer playersWithoutTeam;
    private Integer teamCount;
    private Integer sessionCount;
    private Integer newSessionsThisMonth;
    private Double outstandingAmount;
    private Integer outstandingCount;
    private ClubWalletResponse wallet;
    private List<MasterClubAdminContactResponse> admins;
    private List<String> healthFlags;
    private List<MasterClubRecentItemResponse> recentPayments;
    private List<MasterClubRecentItemResponse> recentWithdrawals;
}
