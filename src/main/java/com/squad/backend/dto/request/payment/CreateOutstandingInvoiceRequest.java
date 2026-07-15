package com.squad.backend.dto.request.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOutstandingInvoiceRequest {
    @NotBlank
    private String playerId;

    /** email | whatsapp */
    @NotBlank
    private String communicationMethod;
}
