package com.squad.backend.dto.response.finance;

import com.squad.backend.dto.response.PageMetaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedTransactionsResponse {
    private List<TransactionResponse> transactions;
    private PageMetaResponse pagination;
}
