package com.squad.backend.dto.response.user;

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
public class PagedUsersResponse {
    private List<UserResponse> users;
    private PageMetaResponse pagination;
}
