package com.squad.backend.dto.response.clubwallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubWalletResponse {
    @JsonProperty("_id")
    private String id;
    private String clubId;
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
