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
    /**
     * 數量配額用量（MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS，Sprint 153：Sprint 147 §6 範圍外項目補齊）。
     * 與 {@link #features} 分開回傳——數值配額不是布林開關，不應與開關清單混在一起
     * （見 {@code TenantService.getFeatureToggles} 對 DEF-167 的既有說明）。
     */
    private java.util.List<QuotaInfo> quotas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaInfo {
        private String featureKey;
        private String featureName;
        private int limit;
        private long currentUsage;
    }

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