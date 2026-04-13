package com.squad.backend.dto.response.clubwallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAccountResponse {
    private String accountName;
    private String sortCode;
    private String accountNumber;
}
