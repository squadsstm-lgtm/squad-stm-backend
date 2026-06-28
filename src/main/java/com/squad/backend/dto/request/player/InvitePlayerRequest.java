package com.squad.backend.dto.request.player;

import lombok.Data;

@Data
public class InvitePlayerRequest {
    private String communicationMethod;
    private String email;
    private String phone;
    private String clubId;
}
