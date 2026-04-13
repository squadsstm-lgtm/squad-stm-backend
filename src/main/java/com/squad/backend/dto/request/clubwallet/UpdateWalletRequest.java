package com.squad.backend.dto.request.clubwallet;

import lombok.Data;

@Data
public class UpdateWalletRequest {
    private Double totalEarnings;
    private Double lockedEarnings;
    private Double availableForWithdrawal;
    private Double totalWithdrawn;
    private Double pendingWithdrawals;
    private Double processingFees;
    private Double monthlyRevenue;
    private Double yearlyRevenue;
    private Double totalRefunds;
}
