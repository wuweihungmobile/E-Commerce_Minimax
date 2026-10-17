package com.nextkey.ecommerce.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.core.logistics.LogisticsService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * DEF-385：{@code LogisticsController.updateLogisticsStatus} 的 {@code status} 參數解析失敗
 * 時應回乾淨的 422/E-7505，而非未經處理的 {@code IllegalArgumentException} 落入 catch-all 變 500。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DEF-385: LogisticsController 無效 status 參數處理")
class LogisticsControllerStatusValidationTest {

    @Mock private LogisticsService logisticsService;
    @InjectMocks private LogisticsController controller;

    @Test
    @DisplayName("無效的物流狀態字串 → BusinessException(E_7505)，不是未經處理的 IllegalArgumentException")
    void updateLogisticsStatus_invalidStatus_throwsBusinessExceptionE7505() {
        UUID logisticsId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.updateLogisticsStatus(logisticsId, "NOT_A_REAL_STATUS"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_7505);
    }
}
