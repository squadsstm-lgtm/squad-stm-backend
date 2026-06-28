package com.squad.backend.dto.response.invite;

import com.squad.backend.dto.response.player.PlayerResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerInviteResponse {
    private PlayerResponse player;
    private String inviteLink;
    private String inviteCode;
    private java.time.Instant expiresAt;
}
