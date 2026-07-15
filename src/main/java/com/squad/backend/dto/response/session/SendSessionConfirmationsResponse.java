package com.squad.backend.dto.response.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendSessionConfirmationsResponse {
    private String communicationMethod;
    private String inviteLink;
    private int playerCount;
    private String message;
}
