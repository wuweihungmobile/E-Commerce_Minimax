package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantApplicationResponse {

    private String applicationId;
    private String storeName;
    private String businessType;
    private String status;
    private String statusDescription;
    private Instant submittedAt;
    private Integer estimatedReviewDays;
}