package com.squad.backend.dto.request.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendSessionConfirmationsRequest {
    /** "email" or "whatsapp" */
    @NotBlank
    private String communicationMethod;
}
