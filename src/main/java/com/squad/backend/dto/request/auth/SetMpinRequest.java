package com.squad.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetMpinRequest {
    @NotBlank(message = "MPIN is required")
    @Size(min = 4, max = 4, message = "MPIN must be 4 digits")
    @Pattern(regexp = "\\d{4}", message = "MPIN must contain only 4 digits")
    private String mpin;
    @NotBlank(message = "Confirm MPIN is required")
    private String confirmMpin;
}
