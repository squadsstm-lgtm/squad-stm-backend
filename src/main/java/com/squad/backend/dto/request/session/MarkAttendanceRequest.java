package com.squad.backend.dto.request.session;

import lombok.Data;

import java.util.List;

@Data
public class MarkAttendanceRequest {
    private List<AttendanceItem> attendance;

    @Data
    public static class AttendanceItem {
        private String playerId;
        private String status;
    }
}
