package com.squad.backend.dto.response.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {
    @JsonProperty("_id")
    private String id;
    private String seasonId;
    private String clubId;
    private String clubName;
    private String teamName;
    private String league;
    private String playersList;
    private String notes;
    private Boolean isActive;
    private Integer playerCount;
    private Integer gameCount;
    private Integer trainingCount;
}
