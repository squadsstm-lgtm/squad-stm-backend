package com.squad.backend.dto.request.invite;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SelectSessionPlayerRequest {
    @NotBlank
    private String playerId;
}
