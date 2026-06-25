package com.nextkey.ecommerce.core.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SettlementService 單元測試（Sprint 20 US-004 清理後）
 *
 * 驗證 getter 方法正確回傳注入的子服務實例。
 * 業務邏輯測試請見 SettlementGeneratorTest / SettlementReviewerTest。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService getter 測試")
class SettlementServiceTest {

    @Mock
    private SettlementCalculator calculator;

    @Mock
    private SettlementGenerator generator;

    @Mock
    private SettlementReviewer reviewer;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    @DisplayName("getCalculator: 返回注入的 calculator 實例")
    void getCalculator_ReturnsCalculator() {
        assertSame(calculator, settlementService.getCalculator());
    }

    @Test
    @DisplayName("getGenerator: 返回注入的 generator 實例")
    void getGenerator_ReturnsGenerator() {
        assertSame(generator, settlementService.getGenerator());
    }

    @Test
    @DisplayName("getReviewer: 返回注入的 reviewer 實例")
    void getReviewer_ReturnsReviewer() {
        assertSame(reviewer, settlementService.getReviewer());
    }
}
