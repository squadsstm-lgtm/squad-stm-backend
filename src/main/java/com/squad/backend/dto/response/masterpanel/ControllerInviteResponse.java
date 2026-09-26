package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControllerInviteResponse {
    private ControllerListItemResponse controller;
    private String inviteLink;
    private String inviteCode;
    private Instant expiresAt;
}
