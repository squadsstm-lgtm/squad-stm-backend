package com.squad.backend.dto.response.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    @JsonProperty("_id")
    private String id;
    private String seasonId;
    private String clubId;
    private String firstName;
    private String lastName;
    private String userName;
    private String email;
    private String phone;
    private String roleId;
    private String role;
    private Boolean isBlocked;
}
