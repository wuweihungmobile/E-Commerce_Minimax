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
public class TenantUpdateResponse {

    private String tenantId;
    private String storeName;
    private String storeDescription;
    private String contactEmail;
    private Instant updatedAt;
}