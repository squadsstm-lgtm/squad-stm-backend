package com.squad.backend.dto.response.invite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteSessionPlayerOption {
    private String playerId;
    private String requestId;
    private String firstName;
    private String lastName;
    private boolean alreadyResponded;
}
