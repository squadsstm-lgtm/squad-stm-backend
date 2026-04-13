package com.squad.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonResponse {
    
    @JsonProperty("_id")
    private String id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String year;
    private Boolean active;
}
