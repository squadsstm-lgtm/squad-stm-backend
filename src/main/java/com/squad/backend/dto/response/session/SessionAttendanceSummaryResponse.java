package com.squad.backend.dto.response.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAttendanceSummaryResponse {
    private String sessionId;
    private String markedBy;
    private String markedByName;
    private LocalDateTime markedAt;
    private int totalPlayers;
    private long attended;
    private long absent;
}
