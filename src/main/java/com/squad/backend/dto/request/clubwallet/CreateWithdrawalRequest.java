package com.squad.backend.dto.request.clubwallet;

import com.squad.backend.model.WithdrawalRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWithdrawalRequest {
    @NotBlank(message = "Club ID is required")
    private String clubId;

    @NotNull(message = "Amount is required")
    private Double amount;

    private String currency;
    private WithdrawalRequest.BankAccount bankAccount;
}
