package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * LogisticsService.cancelLogistics 錯誤碼與狀態驗證（DEF-011）。
 *
 * <p>DEF-011：原誤用 Supplier/PO 的 E_7000/E_7002，改為物流專用 E_7500/E_7502。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService.cancelLogistics（DEF-011 錯誤碼）")
class LogisticsServiceCancelTest {

    @Mock
    private LogisticsRepository logisticsRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LogisticsProviderFactory logisticsProviderFactory;

    @InjectMocks
    private LogisticsService logisticsService;

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
                .status(Logistics.LogisticsStatus.DELIVERED)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .build();
        when(logisticsRepository.findById(id)).thenReturn(Optional.of(delivered));

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
                .status(Logistics.LogisticsStatus.IN_TRANSIT)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .build();
        when(logisticsRepository.findById(id)).thenReturn(Optional.of(inTransit));
        when(logisticsRepository.save(any(Logistics.class))).thenAnswer(inv -> inv.getArgument(0));

        logisticsService.cancelLogistics(id, "客戶取消");

        assertThat(inTransit.getStatus()).isEqualTo(Logistics.LogisticsStatus.RETURNED);
    }
}
