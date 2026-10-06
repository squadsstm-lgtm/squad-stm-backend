package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterClubsPlatformSummaryResponse {
    private long totalClubs;
    private long activeClubs;
    private long emptyClubs;
    private long totalPlayers;
    private long totalTeams;
    private long totalSessions;
    private double totalEarnings;
    /** Squad fees on card payments. Not included in totalEarnings. */
    private double platformCollected;
    private double totalOutstanding;
    private double totalAvailableForWithdrawal;
    private double totalPendingWithdrawals;
    private long openWithdrawalCount;
}
