package com.nextkey.ecommerce.core.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * FeatureToggleService 單元測試（Sprint 79 US-003，多 Sprint 測試強化計劃最後一個模組）。
 *
 * <p>擁有權/租戶檢查現況：{@code checkFeatureEnabled}/{@code isFeatureEnabled} 皆一律以
 * {@link TenantContext#getCurrentTenant()} 取得的當前租戶查詢
 * {@code TenantFeatureToggleRepository.findByTenantIdAndFeatureKey}，沒有任何可由呼叫端
 * 傳入任意 tenantId 的參數，設計上不存在跨租戶讀取/寫入其他租戶開關的路徑（未發現需要修復的缺口）。
 * 本測試以 {@link ArgumentCaptor} 驗證傳入 Repository 的 tenantId 確實來自
 * {@code TenantContext}，不會被忽略或替換為其他值，佐證此結論。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureToggleService 單元測試（Sprint 79）")
class FeatureToggleServiceTest {

    @Mock
    private TenantFeatureToggleRepository featureToggleRepository;

    @InjectMocks
    private FeatureToggleService featureToggleService;

    private static final String FEATURE_KEY = "BOOKING_ENABLED";

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TenantFeatureToggle toggle(final boolean enabled) {
        return TenantFeatureToggle.builder()
                .id(UUID.randomUUID())
                .featureKey(FEATURE_KEY)
                .isEnabled(enabled)
                .build();
    }

    @Test
    @DisplayName("checkFeatureEnabled：功能已啟用時不拋例外")
    void checkFeatureEnabled_enabled_doesNotThrow() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.of(toggle(true)));

        featureToggleService.checkFeatureEnabled(FEATURE_KEY);

        verify(featureToggleRepository).findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY);
    }

    @Test
    @DisplayName("checkFeatureEnabled：功能已停用時拋出 BusinessException(E-2004)")
    void checkFeatureEnabled_disabled_throwsBusinessException() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.of(toggle(false)));

        assertThatThrownBy(() -> featureToggleService.checkFeatureEnabled(FEATURE_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(FEATURE_KEY);
    }

    @Test
    @DisplayName("checkFeatureEnabled：查無此租戶的開關紀錄時視為停用，拋出 BusinessException")
    void checkFeatureEnabled_notFound_throwsBusinessException() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> featureToggleService.checkFeatureEnabled(FEATURE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("isFeatureEnabled：功能已啟用時回傳 true")
    void isFeatureEnabled_enabled_returnsTrue() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.of(toggle(true)));

        assertThat(featureToggleService.isFeatureEnabled(FEATURE_KEY)).isTrue();
    }

    @Test
    @DisplayName("isFeatureEnabled：功能已停用時回傳 false（不拋例外）")
    void isFeatureEnabled_disabled_returnsFalse() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.of(toggle(false)));

        assertThat(featureToggleService.isFeatureEnabled(FEATURE_KEY)).isFalse();
    }

    @Test
    @DisplayName("isFeatureEnabled：查無紀錄時回傳 false（fail-closed，不拋例外）")
    void isFeatureEnabled_notFound_returnsFalse() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY))
                .thenReturn(Optional.empty());

        assertThat(featureToggleService.isFeatureEnabled(FEATURE_KEY)).isFalse();
    }

    @Test
    @DisplayName("租戶隔離：不同租戶情境下呼叫，查詢一律使用當前 TenantContext 的 tenantId，不會互相污染")
    void featureToggle_differentTenantContext_queriesOwnTenantOnly() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);

        TenantContext.setCurrentTenant(tenantA);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(eq(tenantA), eq(FEATURE_KEY)))
                .thenReturn(Optional.of(toggle(true)));
        assertThat(featureToggleService.isFeatureEnabled(FEATURE_KEY)).isTrue();

        TenantContext.setCurrentTenant(tenantB);
        when(featureToggleRepository.findByTenantIdAndFeatureKey(eq(tenantB), eq(FEATURE_KEY)))
                .thenReturn(Optional.empty());
        assertThat(featureToggleService.isFeatureEnabled(FEATURE_KEY)).isFalse();

        verify(featureToggleRepository, org.mockito.Mockito.times(2))
                .findByTenantIdAndFeatureKey(tenantCaptor.capture(), eq(FEATURE_KEY));
        assertThat(tenantCaptor.getAllValues()).containsExactly(tenantA, tenantB);
    }

    // ========== Sprint 147：checkQuotaNotExceeded（MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS 強制執行）==========

    @Test
    @DisplayName("checkQuotaNotExceeded：目前數量低於上限時不拋例外")
    void checkQuotaNotExceeded_belowLimit_doesNotThrow() {
        featureToggleService.checkQuotaNotExceeded(100, 99L);
    }

    @Test
    @DisplayName("checkQuotaNotExceeded：目前數量剛好等於上限時拋出 BusinessException(E-2009)")
    void checkQuotaNotExceeded_atLimit_throwsBusinessException() {
        assertThatThrownBy(() -> featureToggleService.checkQuotaNotExceeded(100, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_2009));
    }

    @Test
    @DisplayName("checkQuotaNotExceeded：目前數量超過上限時仍拋出 BusinessException(E-2009)")
    void checkQuotaNotExceeded_overLimit_throwsBusinessException() {
        assertThatThrownBy(() -> featureToggleService.checkQuotaNotExceeded(100, 101L))
                .isInstanceOf(BusinessException.class);
    }
}
