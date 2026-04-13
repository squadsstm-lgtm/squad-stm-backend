package com.squad.backend.dto.request.auth;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String id;
    private String password;
    private String oldPassword;
}
