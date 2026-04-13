package com.squad.backend.dto.request.auth;

import lombok.Data;

@Data
public class UpdateAuthRequest {
    private String seasonId;
    private String firstName;
    private String lastName;
    private String userName;
    private String image;
}
