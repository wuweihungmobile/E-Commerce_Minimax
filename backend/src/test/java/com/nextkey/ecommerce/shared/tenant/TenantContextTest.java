package com.nextkey.ecommerce.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * {@link TenantContext#isStoreTenant} 的單元測試（Sprint 234）。
 *
 * <p>這個判斷是店家層端點守住「沒有店鋪的使用者」的唯一依據：沒有店鋪的使用者租戶脈絡是系統租戶佔位值
 * （不是 null），所有一般買家共用同一個，所以只檢查「非 null」或 {@code hasTenant()} 都擋不住。
 */
@DisplayName("TenantContext.isStoreTenant（Sprint 234）")
class TenantContextTest {

    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("null 不是店鋪租戶")
    void nullIsNotStoreTenant() {
        assertThat(TenantContext.isStoreTenant(null)).isFalse();
    }

    @Test
    @DisplayName("系統租戶佔位值不是店鋪租戶——這正是沒有店鋪的一般買家共用的租戶")
    void systemTenantIsNotStoreTenant() {
        assertThat(TenantContext.isStoreTenant(SYSTEM_TENANT)).isFalse();
    }

    @Test
    @DisplayName("任何其他租戶 id 都是店鋪租戶")
    void anyOtherTenantIsStoreTenant() {
        assertThat(TenantContext.isStoreTenant(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("hasTenant() 對系統租戶也回 true，不能拿來判斷「有沒有店鋪」")
    void hasTenantCannotTellSystemTenantApart() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);

        assertThat(TenantContext.hasTenant()).isTrue();
        assertThat(TenantContext.isStoreTenant(TenantContext.getCurrentTenant())).isFalse();
    }
}
