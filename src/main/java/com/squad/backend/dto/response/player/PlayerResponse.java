package com.squad.backend.dto.response.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponse {
    @JsonProperty("_id")
    private String id;
    private String clubId;
    private String seasonId;
    private Map<String, String> teams;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String parentName;
    private String parentEmail;
    private String emContact;
    private String emPhone;
    private String clubs;
    private String consentGiven;
    private String status;
    private String photoUploaded;
    private String profileImage;
    private String notes;
    private Boolean isActive;
    private String clubName;
    private String teamName;
}
