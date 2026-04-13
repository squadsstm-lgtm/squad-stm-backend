package com.squad.backend.dto.response.permission;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    @JsonProperty("_id")
    private String id;
    private String clubId;
    private String name;
    private List<PermissionGroupDto> groups;
}
