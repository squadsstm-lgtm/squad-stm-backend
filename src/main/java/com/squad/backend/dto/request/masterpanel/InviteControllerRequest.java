package com.squad.backend.dto.request.masterpanel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteControllerRequest {
    /** email | whatsapp */
    @NotBlank(message = "Communication method is required")
    private String communicationMethod;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String firstName;
    private String lastName;
    /** Required when communicationMethod is whatsapp. */
    private String phone;

    /** Optional initial permissions; capped to what the inviter can grant. */
    private UpdateControllerPermissionsRequest permissions;
}
