package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * SettlementReviewer 單元測試（Sprint 81，DEF-040 修復）。
 *
 * <p>驗證非 SUPER_ADMIN 僅能操作自己租戶的結算單；SUPER_ADMIN 可跨租戶操作/查詢。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReviewer 單元測試 (Sprint 81 DEF-040)")
class SettlementReviewerTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private SettlementMapper mapper;

    @Mock
    private TransferService transferService;

    private SettlementReviewer reviewer;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private SettlementReviewer newReviewer() {
        return new SettlementReviewer(settlementRepository, mapper, transferService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SettlementStatement pendingReviewStatement(UUID tenantId) {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenantId(tenantId)
                .status(SettlementStatus.PENDING_REVIEW)
                .build();
    }

    @Test
    @DisplayName("🔴 approveStatement: 非 SUPER_ADMIN 批准他租戶結算單應被拒絕")
    void approveStatement_crossTenantNonSuperAdmin_denied() {
        reviewer = newReviewer();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(pendingReviewStatement(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("approveStatement: 非 SUPER_ADMIN 批准自己租戶結算單成功")
    void approveStatement_ownTenant_succeeds() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.APPROVED);
        verify(transferService).createTransferForStatement(STATEMENT_ID);
    }

    @Test
    @DisplayName("approveStatement: SUPER_ADMIN 批准任意租戶結算單成功（略過租戶檢查）")
    void approveStatement_superAdmin_bypassesTenantCheck() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(OTHER_TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, true);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("🔴 rejectStatement: 非 SUPER_ADMIN 駁回他租戶結算單應被拒絕")
    void rejectStatement_crossTenantNonSuperAdmin_denied() {
        reviewer = newReviewer();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(pendingReviewStatement(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("rejectStatement: 非 SUPER_ADMIN 駁回自己租戶結算單成功")
    void rejectStatement_ownTenant_succeeds() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("REJECTED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("getPendingReviewStatements: 非 SUPER_ADMIN 僅回自己租戶")
    void getPendingReviewStatements_nonSuperAdmin_scopesToCurrentTenant() {
        reviewer = newReviewer();
        TenantContext.setCurrentTenant(TENANT_ID);
        when(settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        SettlementStatementListResponse response = reviewer.getPendingReviewStatements(0, 20, false, null);

        assertThat(response.getTotalElements()).isEqualTo(0L);
        verify(settlementRepository).findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
    }

    @Test
    @DisplayName("getPendingReviewStatements: SUPER_ADMIN 帶 tenantIdOverride 僅回指定租戶")
    void getPendingReviewStatements_superAdminWithOverride_scopesToGivenTenant() {
        reviewer = newReviewer();
        when(settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(OTHER_TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        reviewer.getPendingReviewStatements(0, 20, true, OTHER_TENANT_ID);

        verify(settlementRepository).findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(OTHER_TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
        verify(settlementRepository, org.mockito.Mockito.never())
                .findByStatusOrderByGeneratedAtDesc(any(), any());
    }

    @Test
    @DisplayName("getPendingReviewStatements: SUPER_ADMIN 未帶 tenantIdOverride 維持跨租戶總覽")
    void getPendingReviewStatements_superAdminWithoutOverride_returnsAllTenants() {
        reviewer = newReviewer();
        when(settlementRepository.findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        reviewer.getPendingReviewStatements(0, 20, true, null);

        verify(settlementRepository).findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
    }
}
