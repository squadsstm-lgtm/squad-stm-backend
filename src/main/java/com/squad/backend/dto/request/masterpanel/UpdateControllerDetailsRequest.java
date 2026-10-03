package com.squad.backend.dto.request.masterpanel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateControllerDetailsRequest {
    @NotBlank(message = "First name is required")
    @Size(max = 30)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 30)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;
}
