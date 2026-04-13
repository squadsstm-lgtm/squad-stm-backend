package com.squad.backend.dto.response.team;

import com.squad.backend.dto.response.PageMetaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedTeamsResponse {
    private List<TeamResponse> teams;
    private PageMetaResponse pagination;
}
