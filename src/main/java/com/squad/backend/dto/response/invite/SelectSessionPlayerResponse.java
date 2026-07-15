package com.squad.backend.dto.response.invite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectSessionPlayerResponse {
    private String requestId;
    private String accessToken;
}
