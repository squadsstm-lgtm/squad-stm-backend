package com.squad.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountResponse {

    private Integer playerCount;
    private Integer activePlayerCount;
    private Integer inactivePlayerCount;
    private Integer playersWithoutTeam;
    private Integer newPlayersThisMonth;
    private Integer teamCount;
    private Integer activeTeamCount;
    private Integer inactiveTeamCount;
    private Integer avgPlayersPerTeam;
    private Integer newTeamsThisMonth;
    private Integer sessionCount;
    private Integer newSessionsThisMonth;
}
