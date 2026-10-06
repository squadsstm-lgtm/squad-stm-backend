package com.squad.backend.dto.request.masterpanel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDefaultPlatformFeeRequest {

    @NotNull(message = "Default platform fee is required.")
    @DecimalMin(value = "0.00", message = "Default platform fee cannot be negative.")
    @DecimalMax(value = "999.99", message = "Default platform fee cannot be more than 999.99.")
    @Digits(integer = 3, fraction = 2, message = "Default platform fee can have at most 2 decimal places.")
    private BigDecimal defaultPlatformFee;
}
