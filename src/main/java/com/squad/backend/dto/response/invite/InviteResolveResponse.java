package com.squad.backend.dto.response.invite;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class InviteResolveResponse {
    private String purpose;
    private String entityId;
    private String clubId;
    private String seasonId;
    private String clubName;
    private String email;
    private String phone;
    private String countryCode;
    private String role;
    /** User invites: true only for pending invite stubs. Omitted for player invites. */
    private Boolean canPrefill;
    private Boolean alreadySubmitted;
    private Instant expiresAt;
    /** Payment / session confirmation: JWT for the confirmation-request page. */
    private String accessToken;
    /** Session confirmation invite: session summary for RSVP landing. */
    private String sessionName;
    private String sessionType;
    private String sessionDate;
    private String sessionLocation;
    private String sessionPrice;
    private List<InviteSessionPlayerOption> players;
}
