package com.squad.backend.dto.request.user;

import lombok.Data;

@Data
public class InviteUserRequest {
    private String email;
    private String role;
    private String phone;
}
