package com.squad.backend.dto.response.permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionItemDto {
    private Integer groupId;
    private Integer roleId;
    private String description;
    private Boolean status;
}
