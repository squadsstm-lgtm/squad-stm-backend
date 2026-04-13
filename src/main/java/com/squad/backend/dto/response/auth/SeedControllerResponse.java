package com.squad.backend.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedControllerResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String clubId;
}
