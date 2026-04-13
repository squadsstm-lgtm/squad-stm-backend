package com.squad.backend.dto.response.role;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoleExistsResponse {
    private boolean exists;
}
