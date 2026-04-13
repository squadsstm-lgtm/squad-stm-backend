package com.squad.backend.dto.response.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPlayerStatusResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String paymentStatus;
    private String playerAttendanceResponse;
    private String sessionAttendance;
}
