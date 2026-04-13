package com.squad.backend.dto.response.confirmationrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationRequestResponse {
    @JsonProperty("_id")
    private String id;
    private String clubId;
    private String seasonId;
    private String sessionId;
    private String playerId;
    private String teamId;
    private String playerAttendanceResponse;
    private String sessionAttendance;
    private String attendanceMarkedBy;
    private LocalDateTime attendanceMarkedAt;
    private String payment;
    private String amount;
    private Boolean isActive;
    private String clubName;
    private String playerName;
    private String sessionName;
}
