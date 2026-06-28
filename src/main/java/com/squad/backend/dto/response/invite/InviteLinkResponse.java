package com.squad.backend.dto.response.invite;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InviteLinkResponse {
    private String inviteLink;
    private String inviteCode;
    private Instant expiresAt;
}
