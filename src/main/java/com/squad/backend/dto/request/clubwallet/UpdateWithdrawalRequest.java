package com.squad.backend.dto.request.clubwallet;

import lombok.Data;

@Data
public class UpdateWithdrawalRequest {
    private String status;
    private String processedBy;
    private String notes;
}
