package com.squad.backend.dto.request.masterpanel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateClubPlatformFeeRequest {

    @NotNull(message = "Platform fee is required.")
    @DecimalMin(value = "0.00", message = "Platform fee cannot be negative.")
    @DecimalMax(value = "999.99", message = "Platform fee cannot be more than 999.99.")
    @Digits(integer = 3, fraction = 2, message = "Platform fee can have at most 2 decimal places.")
    private BigDecimal platformFee;
}
