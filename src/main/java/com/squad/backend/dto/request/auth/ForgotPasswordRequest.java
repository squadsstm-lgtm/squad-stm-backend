package com.squad.backend.dto.request.auth;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
}
