package com.squad.backend.dto.request.clubwallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdatePayoutAccountRequest {
    @NotBlank(message = "Account name is required")
    private String accountName;

    @NotBlank(message = "Sort code is required")
    @Pattern(regexp = "\\d{6}|\\d{2}-?\\d{2}-?\\d{2}", message = "Sort code must be 6 digits (e.g. 12-34-56)")
    private String sortCode;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "\\d{8}", message = "Account number must be 8 digits")
    private String accountNumber;
}
