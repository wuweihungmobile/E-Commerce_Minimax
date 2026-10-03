package com.nextkey.ecommerce.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 239：只有 ACTIVE 的店鋪能被下單。守的是「消費者不會把款項付給停權的店鋪」；
 * 用列舉逐一驗證，之後若新增店鋪狀態，預設就是不能下單（要明確放行才行），而不是默默放行。
 */
@DisplayName("Sprint 239: 店鋪能否下單（StoreCheckoutGuard）")
class StoreCheckoutGuardTest {

    private Tenant storeWith(final Tenant.TenantStatus status) {
        return Tenant.builder().name("店鋪").status(status).build();
    }

    @Test
    @DisplayName("ACTIVE 的店鋪可以下單")
    void activeStore_isOpen() {
        assertThat(StoreCheckoutGuard.isOpen(storeWith(Tenant.TenantStatus.ACTIVE))).isTrue();
        assertThatCode(() -> StoreCheckoutGuard.requireOpen(storeWith(Tenant.TenantStatus.ACTIVE))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.TenantStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("非 ACTIVE（待審核、已駁回、停權、終止）的店鋪一律不能下單：E-2010")
    void nonActiveStores_areRejected(final Tenant.TenantStatus status) {
        assertThat(StoreCheckoutGuard.isOpen(storeWith(status))).isFalse();
        assertThatThrownBy(() -> StoreCheckoutGuard.requireOpen(storeWith(status)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_2010));
    }

    @Test
    @DisplayName("店鋪不存在（null）視為不能下單")
    void missingStore_isNotOpen() {
        assertThat(StoreCheckoutGuard.isOpen(null)).isFalse();
    }
}
