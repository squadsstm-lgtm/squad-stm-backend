package com.squad.backend.dto.response.confirmationrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationRequestsPageResponse {
    private List<ConfirmationRequestResponse> requests;
    private ConfirmationRequestsMetaResponse meta;
}
