package com.nextkey.ecommerce.core.feature;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Feature Toggle 檢查服務
 * 用於檢查各項功能是否啟用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureToggleService {

    private final TenantFeatureToggleRepository featureToggleRepository;

    /**
     * 檢查 Feature Toggle 是否啟用
     * 若未啟用則拋出 BusinessException(ErrorCode.E_2020)
     */
    public void checkFeatureEnabled(final String featureKey) {
        UUID tenantId = TenantContext.getCurrentTenant();

        TenantFeatureToggle toggle = featureToggleRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .orElse(null);

        if (toggle == null || !toggle.getIsEnabled()) {
            log.warn("Feature toggle disabled: tenantId={}, feature={}", tenantId, featureKey);
            throw new BusinessException(ErrorCode.E_2004,
                    String.format("Feature '%s' is disabled for this tenant", featureKey));
        }

        log.debug("Feature toggle enabled: tenantId={}, feature={}", tenantId, featureKey);
    }

    /**
     * 檢查數量配額是否已達上限（Sprint 147：MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS 強制執行，見 PRD §4.4）
     * 若目前啟用中/上架中數量已達到（或超過）上限則拋出 BusinessException(ErrorCode.E_2009)
     *
     * @param limit 配額上限（見 {@link com.nextkey.ecommerce.shared.constants.AppConstants} 的 QUOTA_MAX_* 常數）
     * @param currentActiveCount 該租戶目前啟用中/上架中的數量
     */
    public void checkQuotaNotExceeded(final int limit, final long currentActiveCount) {
        if (currentActiveCount >= limit) {
            log.warn("Quota exceeded: limit={}, current={}", limit, currentActiveCount);
            throw new BusinessException(ErrorCode.E_2009);
        }
    }

    /**
     * 檢查 Feature Toggle 是否啟用（不回拋異常）
     * @return true if enabled, false otherwise
     */
    public boolean isFeatureEnabled(final String featureKey) {
        UUID tenantId = TenantContext.getCurrentTenant();
        return isFeatureEnabledForTenant(tenantId, featureKey);
    }

    /**
     * 檢查指定租戶的 Feature Toggle 是否啟用（不依賴 {@code TenantContext}）
     *
     * Sprint 80（AI-2416）：{@code SettlementReviewer.approveStatement} 由 Admin 審核他人（賣家）
     * 的結算單，呼叫當下的 {@code TenantContext} 是 Admin 自己的租戶，並非結算單所屬租戶，
     * 不可用 {@link #isFeatureEnabled} 誤判為 Admin 自己租戶的 toggle 設定。
     *
     * @return true if enabled, false otherwise
     */
    public boolean isFeatureEnabledForTenant(final UUID tenantId, final String featureKey) {
        return featureToggleRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .map(TenantFeatureToggle::getIsEnabled)
                .orElse(false);
    }
}