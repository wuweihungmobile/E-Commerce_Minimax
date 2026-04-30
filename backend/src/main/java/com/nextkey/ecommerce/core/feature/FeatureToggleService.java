package com.nextkey.ecommerce.core.feature;

import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
    public void checkFeatureEnabled(String featureKey) {
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
     * 檢查 Feature Toggle 是否啟用（不回拋異常）
     * @return true if enabled, false otherwise
     */
    public boolean isFeatureEnabled(String featureKey) {
        UUID tenantId = TenantContext.getCurrentTenant();

        return featureToggleRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .map(TenantFeatureToggle::getIsEnabled)
                .orElse(false);
    }
}