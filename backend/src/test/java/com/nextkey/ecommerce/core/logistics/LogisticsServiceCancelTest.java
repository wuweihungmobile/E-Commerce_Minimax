package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * LogisticsService.cancelLogistics 錯誤碼與狀態驗證（DEF-011）+ 租戶擁有權檢查（DEF-036）。
 *
 * <p>DEF-011：原誤用 Supplier/PO 的 E_7000/E_7002，改為物流專用 E_7500/E_7502。
 * <p>DEF-036：cancelLogistics 補上 checkLogisticsTenant，本檔非租戶擁有權案例（not-found/delivered/
 * inTransit）皆改為本租戶（tenantA）情境，確保通過擁有權檢查後才驗證原本的狀態邏輯。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService.cancelLogistics（DEF-011 錯誤碼 / DEF-036 擁有權）")
class LogisticsServiceCancelTest {

    @Mock
    private LogisticsRepository logisticsRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LogisticsProviderFactory logisticsProviderFactory;

    @InjectMocks
    private LogisticsService logisticsService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Order orderOfTenantA() {
        return Order.builder().id(orderId).tenantId(tenantA).status(Order.OrderStatus.SHIPPING).build();
    }

    @Test
    @DisplayName("DEF-011: 物流不存在 → E_7500（Logistics not found，非 E_7000 Supplier）")
    void cancel_notFound_throwsE7500() {
        UUID id = UUID.randomUUID();
        when(logisticsRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logisticsService.cancelLogistics(id, "客戶取消"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_7500);
    }

    @Test
    @DisplayName("DEF-011: 已送達不可取消 → E_7502（非 E_7002 PO status）")
    void cancel_delivered_throwsE7502() {
        UUID id = UUID.randomUUID();
        Logistics delivered = Logistics.builder()
                .orderId(orderId)
                .status(Logistics.LogisticsStatus.DELIVERED)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .build();
        when(logisticsRepository.findById(id)).thenReturn(Optional.of(delivered));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenantA()));

        assertThatThrownBy(() -> logisticsService.cancelLogistics(id, "太晚取消"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_7502);
    }

    @Test
    @DisplayName("可取消狀態（IN_TRANSIT）→ 標記為 RETURNED")
    void cancel_inTransit_setsReturned() {
        UUID id = UUID.randomUUID();
        Logistics inTransit = Logistics.builder()
                .orderId(orderId)
                .status(Logistics.LogisticsStatus.IN_TRANSIT)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .build();
        when(logisticsRepository.findById(id)).thenReturn(Optional.of(inTransit));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenantA()));
        when(logisticsRepository.save(any(Logistics.class))).thenAnswer(inv -> inv.getArgument(0));

        logisticsService.cancelLogistics(id, "客戶取消");

        assertThat(inTransit.getStatus()).isEqualTo(Logistics.LogisticsStatus.RETURNED);
    }
}
