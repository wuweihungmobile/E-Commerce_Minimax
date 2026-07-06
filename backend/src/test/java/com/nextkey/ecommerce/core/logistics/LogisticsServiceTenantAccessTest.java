package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * LogisticsService 除 createLogistics 之外其餘方法的租戶擁有權檢查（DEF-036）。
 *
 * <p>探查發現：DEF-019 只替 createLogistics 加上 checkOrderTenant，其餘 6 個方法
 * （getLogistics/getLogisticsByOrderId/trackLogistics/getTrackingDetail/updateLogisticsStatus/
 * cancelLogistics）完全沒有租戶擁有權檢查——Controller 層僅要求 order:read/order:update 權限，
 * 任一租戶皆可讀取或竄改他租戶的物流單（跨租戶 IDOR）。cancelLogistics 於
 * {@link LogisticsServiceCancelTest} 另外驗證，本檔涵蓋其餘 5 個方法，比照既有
 * checkOrderTenant 模式修復：本租戶放行、他租戶拒絕（E_1007）、admin 放行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService 查詢/追蹤/狀態更新的租戶擁有權隔離（DEF-036）")
class LogisticsServiceTenantAccessTest {

    @Mock
    private LogisticsRepository logisticsRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LogisticsProviderFactory logisticsProviderFactory;

    @InjectMocks
    private LogisticsService logisticsService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID logisticsId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOfTenant(final UUID tenantId) {
        return Order.builder().id(orderId).tenantId(tenantId).status(Order.OrderStatus.SHIPPING).build();
    }

    private Logistics logisticsOfOrder() {
        return Logistics.builder()
                .id(logisticsId)
                .orderId(orderId)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .status(Logistics.LogisticsStatus.IN_TRANSIT)
                .build();
    }

    private void setAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== getLogistics ==========

    @Test
    @DisplayName("getLogistics：他租戶讀取他人物流單 → E_1007")
    void getLogistics_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getLogistics：本租戶讀取自己物流單 → 正常回傳")
    void getLogistics_sameTenant_succeeds() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantA);

        assertThatCode(() -> logisticsService.getLogistics(logisticsId)).doesNotThrowAnyException();
    }

    // ========== getLogisticsByOrderId ==========

    @Test
    @DisplayName("getLogisticsByOrderId：他租戶查詢他人訂單物流列表 → E_1007")
    void getLogisticsByOrderId_crossTenant_throwsE1007() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getLogisticsByOrderId：本租戶查詢自己訂單物流列表 → 正常回傳")
    void getLogisticsByOrderId_sameTenant_succeeds() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        when(logisticsRepository.findByOrderId(orderId)).thenReturn(List.of());
        TenantContext.setCurrentTenant(tenantA);

        assertThatCode(() -> logisticsService.getLogisticsByOrderId(orderId)).doesNotThrowAnyException();
    }

    // ========== trackLogistics ==========

    @Test
    @DisplayName("trackLogistics：他租戶追蹤他人物流單 → E_1007")
    void trackLogistics_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.trackLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    // ========== getTrackingDetail ==========

    @Test
    @DisplayName("getTrackingDetail：他租戶查詢他人物流追蹤歷史 → E_1007")
    void getTrackingDetail_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getTrackingDetail(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    // ========== updateLogisticsStatus ==========

    @Test
    @DisplayName("updateLogisticsStatus：他租戶竄改他人物流狀態 → E_1007（寫入操作，風險更高）")
    void updateLogisticsStatus_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.updateLogisticsStatus(logisticsId, Logistics.LogisticsStatus.DELIVERED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("updateLogisticsStatus：admin 跨租戶放行")
    void updateLogisticsStatus_admin_bypassesTenant() {
        Logistics logistics = logisticsOfOrder();
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logistics));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        when(logisticsRepository.save(any(Logistics.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setCurrentTenant(tenantB);
        setAdmin();

        assertThatCode(() -> logisticsService.updateLogisticsStatus(logisticsId, Logistics.LogisticsStatus.PICKED_UP))
                .doesNotThrowAnyException();
    }
}
