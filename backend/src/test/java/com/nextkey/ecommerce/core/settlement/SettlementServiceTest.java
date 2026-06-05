package com.nextkey.ecommerce.core.settlement;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SettlementService (Facade) 單元測試
 *
 * 拆分重構後（Sprint 16 Retro TI-002）：
 * - SettlementService 變為薄 Facade，委派給 3 個子服務 + Mapper
 * - 本測試驗證委派行為（delegation），業務邏輯由子服務測試覆蓋
 *
 * 委派關係：
 * - generateWeeklyStatements / generateStatementForTenant / getStatementsByTenant / getStatementById
 *   → SettlementGenerator
 * - submitForReview / approveStatement / rejectStatement / getPendingReviewStatements
 *   → SettlementReviewer
 *
 * 覆蓋率目標：>= 90%（委派邏輯簡單，應高覆蓋率）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService Facade 委派測試 (Sprint 16 TI-002 拆分)")
class SettlementServiceTest {

    @Mock
    private SettlementCalculator calculator;

    @Mock
    private SettlementGenerator generator;

    @Mock
    private SettlementReviewer reviewer;

    @Mock
    private SettlementMapper mapper;

    @InjectMocks
    private SettlementService settlementService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 6, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 7);

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // ========== 委派給 Generator 的方法 ==========

    @Test
    @DisplayName("generateWeeklyStatements: 委派給 generator")
    void generateWeeklyStatements_Delegation() {
        // When
        settlementService.generateWeeklyStatements();

        // Then
        verify(generator, times(1)).generateWeeklyStatements();
        verifyNoInteractions(calculator, reviewer, mapper);
    }

    @Test
    @DisplayName("generateStatementForTenant: 委派給 generator 並返回結果")
    void generateStatementForTenant_Delegation() {
        // Given
        SettlementStatement expected = SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenantId(TENANT_ID)
                .status(SettlementStatus.PENDING)
                .totalGmv(new BigDecimal("10000.00"))
                .netSettlementAmount(new BigDecimal("9000.00"))
                .build();
        when(generator.generateStatementForTenant(TENANT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(expected);

        // When
        SettlementStatement result = settlementService.generateStatementForTenant(
                TENANT_ID, PERIOD_START, PERIOD_END);

        // Then
        assertSame(expected, result);
        verify(generator, times(1)).generateStatementForTenant(TENANT_ID, PERIOD_START, PERIOD_END);
    }

    @Test
    @DisplayName("getStatementsByTenant: 委派給 generator")
    void getStatementsByTenant_Delegation() {
        // Given
        SettlementStatementListResponse expected = SettlementStatementListResponse.builder()
                .page(0).size(20).totalElements(0L).totalPages(0).build();
        when(generator.getStatementsByTenant(0, 20)).thenReturn(expected);

        // When
        SettlementStatementListResponse result = settlementService.getStatementsByTenant(0, 20);

        // Then
        assertSame(expected, result);
        verify(generator, times(1)).getStatementsByTenant(0, 20);
    }

    @Test
    @DisplayName("getStatementById: 委派給 generator")
    void getStatementById_Delegation() {
        // Given
        SettlementStatementResponse expected = SettlementStatementResponse.builder()
                .id(STATEMENT_ID)
                .status("PENDING")
                .build();
        when(generator.getStatementById(STATEMENT_ID)).thenReturn(expected);

        // When
        SettlementStatementResponse result = settlementService.getStatementById(STATEMENT_ID);

        // Then
        assertSame(expected, result);
        verify(generator, times(1)).getStatementById(STATEMENT_ID);
    }

    // ========== 委派給 Reviewer 的方法 ==========

    @Test
    @DisplayName("submitForReview: 委派給 reviewer")
    void submitForReview_Delegation() {
        // Given
        SettlementStatementResponse expected = SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("PENDING_REVIEW").build();
        when(reviewer.submitForReview(STATEMENT_ID)).thenReturn(expected);

        // When
        SettlementStatementResponse result = settlementService.submitForReview(STATEMENT_ID);

        // Then
        assertSame(expected, result);
        verify(reviewer, times(1)).submitForReview(STATEMENT_ID);
    }

    @Test
    @DisplayName("approveStatement: 委派給 reviewer（含 adminId）")
    void approveStatement_Delegation() {
        // Given
        SettlementStatementResponse expected = SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build();
        when(reviewer.approveStatement(STATEMENT_ID, ADMIN_ID)).thenReturn(expected);

        // When
        SettlementStatementResponse result = settlementService.approveStatement(STATEMENT_ID, ADMIN_ID);

        // Then
        assertSame(expected, result);
        verify(reviewer, times(1)).approveStatement(STATEMENT_ID, ADMIN_ID);
    }

    @Test
    @DisplayName("rejectStatement: 委派給 reviewer（含 reason）")
    void rejectStatement_Delegation() {
        // Given
        String reason = "金額計算有誤";
        SettlementStatementResponse expected = SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("REJECTED").rejectionReason(reason).build();
        when(reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, reason)).thenReturn(expected);

        // When
        SettlementStatementResponse result = settlementService.rejectStatement(STATEMENT_ID, ADMIN_ID, reason);

        // Then
        assertSame(expected, result);
        assertEquals(reason, result.getRejectionReason());
        verify(reviewer, times(1)).rejectStatement(STATEMENT_ID, ADMIN_ID, reason);
    }

    @Test
    @DisplayName("getPendingReviewStatements: 委派給 reviewer")
    void getPendingReviewStatements_Delegation() {
        // Given
        SettlementStatementListResponse expected = SettlementStatementListResponse.builder()
                .page(0).size(20).totalElements(0L).totalPages(0).build();
        when(reviewer.getPendingReviewStatements(0, 20)).thenReturn(expected);

        // When
        SettlementStatementListResponse result = settlementService.getPendingReviewStatements(0, 20);

        // Then
        assertSame(expected, result);
        verify(reviewer, times(1)).getPendingReviewStatements(0, 20);
    }

    // ========== Getter 方法（暴露子服務） ==========

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
