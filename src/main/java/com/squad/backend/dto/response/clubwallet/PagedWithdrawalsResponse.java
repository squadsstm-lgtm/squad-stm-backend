package com.squad.backend.dto.response.clubwallet;

import com.squad.backend.dto.response.PageMetaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class PagedWithdrawalsResponse {
    private List<WithdrawalRequestResponse> withdrawals;
    private PageMetaResponse pagination;
}
