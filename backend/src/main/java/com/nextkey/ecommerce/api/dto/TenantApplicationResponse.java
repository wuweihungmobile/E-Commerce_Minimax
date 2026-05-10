package com.nextkey.ecommerce.api.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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