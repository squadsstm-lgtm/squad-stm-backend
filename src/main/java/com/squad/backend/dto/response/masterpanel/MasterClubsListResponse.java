package com.squad.backend.dto.response.masterpanel;

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
public class MasterClubsListResponse {
    private List<MasterClubListItemResponse> clubs;
    private PageMetaResponse pagination;
}
