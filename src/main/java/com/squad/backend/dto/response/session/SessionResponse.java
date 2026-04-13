package com.squad.backend.dto.response.session;

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
public class SessionResponse {

    @JsonProperty("_id")
    private String id;
    private String seasonId;
    private String clubId;
    private Boolean active;
    private String sessionName;
    private String sessionType;
    private String location;
    private String teamList;
    private String date;
    private String price;
    private String additionalPlayers;
    private String notes;
    private Boolean isActive;
    private Integer confirmedCount;
    private Integer pendingCount;
    /** True when actual attendance has been marked for this session (any ConfirmationRequest has sessionAttendance set). */
    private Boolean attendanceMarked;
    /** When attendance was marked (from ConfirmationRequest). */
    private LocalDateTime attendanceMarkedAt;
    /** Auth id who marked attendance. */
    private String attendanceMarkedBy;
    /** Display name of who marked attendance. */
    private String attendanceMarkedByName;
}
