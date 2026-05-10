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
public class FeatureToggleResponse {

    private String tenantId;
    private java.util.List<FeatureInfo> features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureInfo {
        private String featureKey;
        private String featureName;
        private String description;
        private Boolean isEnabled;
        private Instant enabledAt;
        private Instant requestedAt;
    }
}