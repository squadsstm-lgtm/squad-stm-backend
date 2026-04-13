package com.squad.backend.dto.request.finance;

import lombok.Data;

@Data
public class UpdateTransactionRequest {
    private String status;
    private String sessionStatus;
    private Boolean moneyLocked;
    private Boolean availableForWithdrawal;
    private String description;
    private String notes;
}
