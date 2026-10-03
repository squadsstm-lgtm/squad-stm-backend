package com.squad.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkWalkthroughSeenRequest {
    @NotBlank
    @Size(max = 40)
    private String page;
}
