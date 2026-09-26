package com.squad.backend.dto.request.masterpanel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateControllerRequest {
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

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*\\W).{8,}$",
            message = "Password must be at least 8 characters and include upper, lower, digit, and special character"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    /** Optional initial permissions; capped to what the creator can grant. */
    private UpdateControllerPermissionsRequest permissions;
}
