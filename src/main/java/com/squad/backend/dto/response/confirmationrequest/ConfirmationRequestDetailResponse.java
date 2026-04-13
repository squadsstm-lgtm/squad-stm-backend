package com.squad.backend.dto.response.confirmationrequest;

import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationRequestDetailResponse {
    private Session session;
    private Player player;
    private ConfirmationRequest request;
}
