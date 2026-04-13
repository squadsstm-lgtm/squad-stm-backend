package com.squad.backend.dto.request.permission;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionItemToggleRequest {

    @NotNull
    private Integer groupId;

    @NotNull
    private Integer roleId;

    @NotNull
    private Boolean status;
}
