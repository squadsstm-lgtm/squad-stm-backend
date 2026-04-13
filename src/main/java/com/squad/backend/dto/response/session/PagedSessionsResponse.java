package com.squad.backend.dto.response.session;

import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.session.SessionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedSessionsResponse {
    private List<SessionResponse> sessions;
    private PageMetaResponse pagination;
}
