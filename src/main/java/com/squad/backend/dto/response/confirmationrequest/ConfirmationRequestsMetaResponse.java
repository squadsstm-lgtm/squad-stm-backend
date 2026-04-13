package com.squad.backend.dto.response.confirmationrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationRequestsMetaResponse {
    private long totalItems;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private Long pendingCount;
    private Long paidCount;
    private Double totalPendingAmount;
    private Double totalPaidAmount;
}
