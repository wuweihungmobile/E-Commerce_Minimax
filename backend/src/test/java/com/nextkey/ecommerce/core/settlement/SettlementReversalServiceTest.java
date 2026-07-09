package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.CreditNote;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.CreditNoteRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * SettlementReversalService 單元測試（Sprint 86，PRD §6.2.1 PAID 結算單逆轉雙重授權）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReversalService 單元測試（Sprint 86）")
class SettlementReversalServiceTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private CreditNoteRepository creditNoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SettlementMapper mapper;

    private SettlementReversalService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final UUID SUPER_ADMIN_ID = UUID.randomUUID();
    private static final UUID CFO_ID = UUID.randomUUID();

    private SettlementReversalService newService() {
        return new SettlementReversalService(settlementRepository, creditNoteRepository, userRepository, mapper);
    }

    private SettlementStatement paidStatement() {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .tenantId(TENANT_ID)
                .status(SettlementStatus.PAID)
                .netSettlementAmount(new BigDecimal("5000.00"))
                .build();
    }

    private SettlementStatement reversalPendingStatement(String initiatedByRole) {
        SettlementStatement statement = paidStatement();
        statement.setStatus(SettlementStatus.REVERSAL_PENDING);
        statement.setReversalInitiatedByRole(initiatedByRole);
        statement.setReversalReason("測試逆轉原因");
        return statement;
    }

    @Test
    @DisplayName("initiateReversal：PAID 結算單由 SUPER_ADMIN 發起成功，轉 REVERSAL_PENDING")
    void initiateReversal_paidStatement_bySuperAdmin_succeeds() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));
        when(userRepository.findById(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(User.builder().id(SUPER_ADMIN_ID).build()));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "客戶投訴要求全額退款");

        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SettlementStatus.REVERSAL_PENDING);
        assertThat(captor.getValue().getReversalInitiatedByRole()).isEqualTo("SUPER_ADMIN");
        assertThat(captor.getValue().getReversalReason()).isEqualTo("客戶投訴要求全額退款");
    }

    @Test
    @DisplayName("initiateReversal：非 PAID 狀態發起應拋出 E_5014")
    void initiateReversal_nonPaidStatus_throwsE5014() {
        service = newService();
        SettlementStatement statement = paidStatement();
        statement.setStatus(SettlementStatus.APPROVED);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));
    }

    @Test
    @DisplayName("initiateReversal：非 SUPER_ADMIN/CFO 角色發起應拒絕")
    void initiateReversal_invalidRole_throwsE1007() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("confirmReversal：CFO 確認 SUPER_ADMIN 發起的逆轉成功，產生 CreditNote 並轉 REVERSED")
    void confirmReversal_differentRole_succeeds() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));
        when(userRepository.findById(CFO_ID)).thenReturn(Optional.of(User.builder().id(CFO_ID).build()));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO");

        ArgumentCaptor<CreditNote> creditNoteCaptor = ArgumentCaptor.forClass(CreditNote.class);
        verify(creditNoteRepository).save(creditNoteCaptor.capture());
        assertThat(creditNoteCaptor.getValue().getAmount()).isEqualByComparingTo("-5000.00");
        assertThat(creditNoteCaptor.getValue().getTenant().getId()).isEqualTo(TENANT_ID);

        ArgumentCaptor<SettlementStatement> statementCaptor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository).save(statementCaptor.capture());
        assertThat(statementCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.REVERSED);
    }

    @Test
    @DisplayName("confirmReversal：同角色確認應被拒絕（雙重授權核心檢查）")
    void confirmReversal_sameRoleAsInitiator_throwsE1007() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));

        assertThatThrownBy(() -> service.confirmReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));

        verify(creditNoteRepository, never()).save(any());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmReversal：非 REVERSAL_PENDING 狀態確認應拋出 E_5014")
    void confirmReversal_nonReversalPendingStatus_throwsE5014() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));

        assertThatThrownBy(() -> service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));
    }

    @Test
    @DisplayName("initiateReversal：結算單不存在拋出 E_5013")
    void initiateReversal_statementNotFound_throwsE5013() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5013));
    }
}
