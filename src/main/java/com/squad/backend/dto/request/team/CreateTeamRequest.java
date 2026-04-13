package com.squad.backend.dto.request.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTeamRequest {
    @NotBlank(message = "Club ID is required")
    private String clubId;

    private String clubName;

    @NotBlank(message = "Team name is required")
    private String teamName;

    private String league;
    private String playersList;
    private String notes;
}
