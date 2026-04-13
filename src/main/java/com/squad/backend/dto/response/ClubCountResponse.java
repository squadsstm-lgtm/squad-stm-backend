package com.squad.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubCountResponse {
    
    private String clubId;
    private String clubname;
    private Integer noofteams;
    private Integer noofsessions;
    private Integer noofplayers;
}
