package com.squad.backend.dto.request.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {
    @NotBlank(message = "Club ID is required")
    private String clubId;

    private Boolean active;

    @NotBlank(message = "Session name is required")
    private String sessionName;

    private String sessionType;
    private String location;
    private String teamList;
    private String date;
    private String price;
    private String additionalPlayers;
    private String notes;
}
