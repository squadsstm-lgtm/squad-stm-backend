package com.squad.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotMpinRequest {
    @NotBlank(message = "Password is required")
    private String password;
}
