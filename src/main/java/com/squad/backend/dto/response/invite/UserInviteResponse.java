package com.squad.backend.dto.response.invite;

import com.squad.backend.dto.response.user.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserInviteResponse {
    private UserResponse user;
    private String inviteLink;
    private String inviteCode;
    private Instant expiresAt;
}
