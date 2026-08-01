package com.squad.backend.dto.response.masterpanel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterClubAdminContactResponse {
    private String name;
    private String email;
    private String phone;
    private String role;
}
