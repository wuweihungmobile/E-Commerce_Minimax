package com.nextkey.ecommerce.core.logistics;

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
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * LogisticsService.createLogistics 租戶擁有權隔離（DEF-019 物流/賣家側 IDOR）。
 *
 * <p>驗證：他租戶不可為他人訂單建立物流（E_1007，經 GlobalExceptionHandler 映射 HTTP 403）；
 * 本租戶通過擁有權檢查（續走狀態檢查）；admin 放行。以「狀態檢查」證明擁有權**先於**狀態觸發
 * （避免向未授權者洩漏訂單狀態的 IDOR 正確順序），並藉此免除 provider happy-path 深度 mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService.createLogistics 租戶擁有權隔離（DEF-019）")
class LogisticsServiceOwnershipTest {

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOfTenant(final UUID tenantId, final Order.OrderStatus status) {
        return Order.builder().id(orderId).tenantId(tenantId).status(status).build();
    }

    private LogisticsDto.CreateRequest req() {
        return LogisticsDto.CreateRequest.builder()
                .orderId(orderId)
                .logisticsProvider(LogisticsDto.LogisticsProvider.HCT)
                .build();
    }

    @Test
    @DisplayName("他租戶建立他人訂單物流 → E_1007（擁有權先於狀態檢查）")
    void createLogistics_otherTenant_throwsE1007() {
        // order 屬 tenantA、狀態故意設 CREATED（非 CONFIRMED）；當前租戶為 tenantB。
        // 若得 E_1007（而非狀態錯誤 E_5001），證明擁有權檢查「先於」狀態檢查觸發。
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfTenant(tenantA, Order.OrderStatus.CREATED)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.createLogistics(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本租戶通過擁有權檢查（續走狀態檢查 → E_5001，非 E_1007）")
    void createLogistics_sameTenant_passesOwnership() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfTenant(tenantA, Order.OrderStatus.CREATED)));
        TenantContext.setCurrentTenant(tenantA);

        // 擁有權通過 → 撞上「須 CONFIRMED」狀態檢查 → E_5001，證明未被 E_1007 擋下。
        assertThatThrownBy(() -> logisticsService.createLogistics(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5001);
    }

    @Test
    @DisplayName("admin 跨租戶放行（擁有權通過，續走狀態檢查 → E_5001）")
    void createLogistics_admin_bypassesTenant() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfTenant(tenantA, Order.OrderStatus.CREATED)));
        TenantContext.setCurrentTenant(tenantB); // 不同租戶
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> logisticsService.createLogistics(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5001);
    }
}
