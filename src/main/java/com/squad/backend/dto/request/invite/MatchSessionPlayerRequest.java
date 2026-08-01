package com.squad.backend.dto.request.invite;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MatchSessionPlayerRequest {
    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /** Optional: when multiple names match, caller picks one playerId. */
    private String playerId;
}
