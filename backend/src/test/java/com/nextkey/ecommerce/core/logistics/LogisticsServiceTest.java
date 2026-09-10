package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * LogisticsService 單元測試（Sprint 151，DEF-190）。
 *
 * <p>背景：{@link LogisticsService} 先前**完全沒有**這類以 Mockito mock repository 的純單元
 * 測試——僅有 {@code M11LogisticsOrderIntegrationTest} 這類需要完整 Spring Context + 真實 DB
 * 的整合測試間接涵蓋部分流程。本檔聚焦補上 {@code checkOrderTenant}（DEF-019/036 既有的租戶
 * 擁有權檢查）的授權矩陣覆蓋，尤其是本輪新發現並修復的 `DEF-190`：
 *
 * <p>修 {@code OrderService} 的 `DEF-189` 時比對發現 {@code checkOrderTenant} 有同型既有漏洞——
 * {@code TenantContextFilter.resolveEffectiveTenantId} 對「未歸屬任何實際租戶」的一般使用者
 * （一般 BUYER、或尚未通過審核的 SELLER）一律 fallback 到同一個常數
 * {@code AppConstants.SYSTEM_TENANT_ID}，租戶比對若未排除這個佔位值，任兩個未加入店鋪的
 * 一般使用者都會落在同一個「租戶」，形成跨使用者 IDOR。本檔以 {@code getLogisticsByOrderId}
 * （直接呼叫 {@code checkOrderTenant}，無需額外 mock 物流商 Provider）驗證修復後行為。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService 單元測試（Sprint 151，DEF-190）")
class LogisticsServiceTest {

    @Mock private LogisticsRepository logisticsRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private LogisticsProviderFactory logisticsProviderFactory;

    @InjectMocks
    private LogisticsService logisticsService;

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOfTenant(final UUID tenantId) {
        Order order = Order.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantId)
                .orderType(Listing.ListingType.PRODUCT)
                .status(Order.OrderStatus.CONFIRMED)
                .items(new java.util.ArrayList<>())
                .build();
        order.setId(ORDER_ID);
        return order;
    }

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== getLogisticsByOrderId（checkOrderTenant 授權矩陣） ==========

    @Test
    @DisplayName("本租戶賣家查詢訂單物流列表 → 放行")
    void getLogisticsByOrderId_sameTenant_passesAuthorization() {
        TenantContext.setCurrentTenant(TENANT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOfTenant(TENANT_ID)));
        when(logisticsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        List<LogisticsDto.LogisticsResponse> result = logisticsService.getLogisticsByOrderId(ORDER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("他租戶賣家查詢訂單物流列表 → E_1007")
    void getLogisticsByOrderId_otherTenant_throwsE1007() {
        TenantContext.setCurrentTenant(OTHER_TENANT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOfTenant(TENANT_ID)));

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("admin 跨租戶查詢訂單物流列表 → 放行")
    void getLogisticsByOrderId_admin_bypassesTenant() {
        TenantContext.setCurrentTenant(OTHER_TENANT_ID);
        asAdmin();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOfTenant(TENANT_ID)));
        when(logisticsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        List<LogisticsDto.LogisticsResponse> result = logisticsService.getLogisticsByOrderId(ORDER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("🔴 DEF-190：兩個都未歸屬任何店鋪的一般使用者（皆落在系統租戶）→ 查詢訂單物流列表"
            + "仍 E_1007，checkOrderTenant 不得對系統租戶放行（否則任一使用者可讀任一使用者掛在"
            + "系統租戶下訂單的物流記錄）")
    void getLogisticsByOrderId_bothUsersOnSystemTenant_throwsE1007() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOfTenant(SYSTEM_TENANT_ID)));

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("訂單不存在 → E_5000")
    void getLogisticsByOrderId_orderNotFound_throwsE5000() {
        TenantContext.setCurrentTenant(TENANT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5000);
    }
}
