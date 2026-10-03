package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * {@link PaymentStoreGuard} 的判斷（Sprint 242）：與 {@code StoreCheckoutGuard}（Sprint 239）同一個標準——只有 ACTIVE 的店鋪
 * 算營業中，租戶不存在也算不營業（預設不能付款，日後新增狀態也一樣）。
 */
@DisplayName("PaymentStoreGuard（Sprint 242）")
class PaymentStoreGuardTest {

    private static final UUID STORE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final PaymentStoreGuard guard = new PaymentStoreGuard(tenantRepository);

    private void storeWithStatus(final Tenant.TenantStatus status) {
        Tenant store = Tenant.builder().name("店").slug("s").status(status).build();
        store.setId(STORE_ID);
        when(tenantRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    @Test
    @DisplayName("ACTIVE 的店鋪：放行，isStoreOpen 為 true")
    void activeStore_isOpen() {
        storeWithStatus(Tenant.TenantStatus.ACTIVE);

        assertThatCode(() -> guard.requireStoreOpen(STORE_ID)).doesNotThrowAnyException();
        assertThat(guard.isStoreOpen(STORE_ID)).isTrue();
    }

    @ParameterizedTest(name = "{0} 的店鋪不能付款")
    @EnumSource(value = Tenant.TenantStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    @DisplayName("非 ACTIVE（待審核、已駁回、停權、終止）的店鋪一律拒絕：E-2010，isStoreOpen 為 false")
    void nonActiveStore_isRejected(final Tenant.TenantStatus status) {
        storeWithStatus(status);

        assertThatThrownBy(() -> guard.requireStoreOpen(STORE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(guard.isStoreOpen(STORE_ID)).isFalse();
    }

    @Test
    @DisplayName("租戶不存在：視為不營業（E-2010），不是 NPE 也不是放行")
    void missingTenant_isRejected() {
        when(tenantRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireStoreOpen(STORE_ID))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(guard.isStoreOpen(STORE_ID)).isFalse();
    }

    @Test
    @DisplayName("storeIdOf：優先讀 tenant 關聯（真正對應資料庫欄位）；同一個持久化脈絡剛建立、tenantId 影子欄位還是 null 的訂單／訂房也取得到店鋪")
    void storeIdOf_prefersTheAssociationOverTheShadowColumn() {
        Tenant store = Tenant.builder().name("店").slug("s").build();
        store.setId(STORE_ID);
        UUID other = UUID.fromString("66666666-6666-6666-6666-666666666666");

        Order justCreated = Order.builder().tenant(store).build();
        assertThat(justCreated.getTenantId()).as("前提：影子欄位是 null").isNull();
        assertThat(PaymentStoreGuard.storeIdOf(justCreated)).isEqualTo(STORE_ID);
        assertThat(PaymentStoreGuard.storeIdOf(Booking.builder().tenant(store).build())).isEqualTo(STORE_ID);

        assertThat(PaymentStoreGuard.storeIdOf(Order.builder().tenant(store).tenantId(other).build()))
                .as("兩者都有時以關聯為準").isEqualTo(STORE_ID);
        assertThat(PaymentStoreGuard.storeIdOf(Order.builder().tenantId(STORE_ID).build()))
                .as("沒有關聯（只有從資料庫載入的影子欄位）時退回影子欄位").isEqualTo(STORE_ID);
        assertThat(PaymentStoreGuard.storeIdOf(Booking.builder().tenantId(STORE_ID).build())).isEqualTo(STORE_ID);
        assertThat(PaymentStoreGuard.storeIdOf(Order.builder().build())).isNull();
    }

    @Test
    @DisplayName("租戶 ID 為 null（訂單沒有租戶）：視為不營業，而且不查詢資料庫")
    void nullTenantId_isRejectedWithoutQuery() {
        assertThatThrownBy(() -> guard.requireStoreOpen(null))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(guard.isStoreOpen(null)).isFalse();

        verifyNoInteractions(tenantRepository);
    }
}
