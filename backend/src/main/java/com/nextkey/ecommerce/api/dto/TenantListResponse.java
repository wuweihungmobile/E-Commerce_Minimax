package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantListResponse {

    private String tenantId;
    private String storeName;
    private String businessType;
    private String status;
    private String role;
    private Integer memberCount;
    private Map<String, Boolean> features;
    private Instant createdAt;
}