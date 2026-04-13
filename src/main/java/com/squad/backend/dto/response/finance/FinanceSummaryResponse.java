package com.squad.backend.dto.response.finance;

import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSummaryResponse {
    private ClubWalletResponse wallet;
    private Map<String, Object> transactions;
    private Map<String, Object> withdrawals;
}
