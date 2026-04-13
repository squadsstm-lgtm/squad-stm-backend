package com.squad.backend.dto.response.player;

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
public class PagedPlayersResponse {
    private List<PlayerResponse> players;
    private PageMetaResponse pagination;
}
