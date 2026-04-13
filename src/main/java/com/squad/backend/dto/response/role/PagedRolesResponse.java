package com.squad.backend.dto.response.role;

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
public class PagedRolesResponse {
    private List<RoleResponse> roles;
    private PageMetaResponse pagination;
}
