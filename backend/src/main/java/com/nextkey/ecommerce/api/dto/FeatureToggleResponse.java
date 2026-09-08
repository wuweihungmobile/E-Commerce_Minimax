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
        /** UI 分組用（listing/booking/cms/erp/promo/pricing）。DEF-168：前端一直預期此欄位卻從未被提供。 */
        private String category;
        /** ACTIVE（已啟用）/ PENDING（已申請待審核）/ INACTIVE（未啟用）。DEF-168 補上。 */
        private String status;
        /** 啟用是否需管理員審核；資料源自 FeatureDefinition.requiresApproval，DEF-168 前從未回傳。 */
        private Boolean requiresAdminReview;
    }
}