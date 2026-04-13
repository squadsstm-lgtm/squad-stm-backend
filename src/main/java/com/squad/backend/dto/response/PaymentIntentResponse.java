package com.squad.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {
    
    private String clientSecret;
    private String id; // Payment Intent ID (not MongoDB _id, so no @JsonProperty needed)
}
