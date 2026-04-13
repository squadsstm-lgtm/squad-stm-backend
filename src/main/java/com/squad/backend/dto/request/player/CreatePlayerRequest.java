package com.squad.backend.dto.request.player;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatePlayerRequest {
    private String clubId;
    private String teamId;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String surName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String phone;
    private String parentName;
    private String parentEmail;
    private String emergencyContact;
    private String emergencyPhone;
    private String otherClubs;
    private String consentGiven;
    private String contractStatus;
    private String photoUploadedDate;
    private String profileImage;
    private String notes;
}
