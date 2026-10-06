package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettingsResponse {
    private Double defaultPlatformFee;
    private Instant updatedAt;
    private String updatedByName;
    private Integer clubsUpdated;
}
