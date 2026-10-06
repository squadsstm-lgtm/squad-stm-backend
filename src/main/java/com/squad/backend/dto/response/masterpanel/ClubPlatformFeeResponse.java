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
public class ClubPlatformFeeResponse {
    private String clubId;
    private Double platformFee;
    private Boolean platformFeeSaved;
    private Boolean platformFeeFollowsDefault;
    private Instant platformFeeUpdatedAt;
    private String platformFeeUpdatedByName;
    private Double defaultPlatformFee;
}
