package com.squad.backend.dto.response.invite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchSessionPlayerResponse {
    private List<InviteSessionPlayerOption> matches;
    private String requestId;
    private String accessToken;
    private String firstName;
    private String lastName;
}
