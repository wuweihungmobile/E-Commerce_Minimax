package com.nextkey.ecommerce.core.settlement;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.CreditNote;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.CreditNoteRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PAID 結算單逆轉服務（PRD §6.2.1，Sprint 86）
 *
 * <p>需 SUPER_ADMIN + CFO 兩種不同角色各簽核一次（雙重授權），確認後產生 CREDIT_NOTE
 * 沖銷原結算金額。不涉及實際銀行/Stripe 資金收回（沿用 Sprint 80/81 既定「不處理 clawback」範圍界線，
 * CREDIT_NOTE 僅為會計沖銷記錄）。
 *
 * <p>狀態轉換：{@code PAID} → {@code REVERSAL_PENDING}（發起）→ {@code REVERSED}（確認）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReversalService {

    private static final int CREDIT_NOTE_NUMBER_LENGTH = 8;

    private final SettlementStatementRepository settlementRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final UserRepository userRepository;
    private final SettlementMapper mapper;

    /**
     * 發起結算單逆轉（PAID → REVERSAL_PENDING）。發起人須為 SUPER_ADMIN 或 CFO。
     */
    @Transactional
    public SettlementStatementResponse initiateReversal(
            final UUID statementId, final UUID initiatorId, final String initiatorRole, final String reason) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        validateReversalRole(initiatorRole);

        if (statement.getStatus() != SettlementStatus.PAID) {
            throw new BusinessException(ErrorCode.E_5014, "Only PAID statements can be reversed");
        }

        User initiator = userRepository.findById(initiatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        statement.setStatus(SettlementStatus.REVERSAL_PENDING);
        statement.setReversalInitiatedBy(initiator);
        statement.setReversalInitiatedByRole(initiatorRole);
        statement.setReversalRequestedAt(Instant.now());
        statement.setReversalReason(reason);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement reversal initiated: statementId={}, initiatorId={}, initiatorRole={}",
                statementId, initiatorId, initiatorRole);

        return mapper.toStatementResponse(statement);
    }

    /**
     * 確認結算單逆轉（REVERSAL_PENDING → REVERSED）。確認人須為 SUPER_ADMIN 或 CFO，
     * 且角色須與發起人不同（雙重授權核心檢查），確認後產生 CREDIT_NOTE。
     */
    @Transactional
    public SettlementStatementResponse confirmReversal(
            final UUID statementId, final UUID confirmerId, final String confirmerRole) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        validateReversalRole(confirmerRole);

        if (statement.getStatus() != SettlementStatus.REVERSAL_PENDING) {
            throw new BusinessException(ErrorCode.E_5014, "Only REVERSAL_PENDING statements can be confirmed");
        }

        if (confirmerRole.equals(statement.getReversalInitiatedByRole())) {
            throw new BusinessException(ErrorCode.E_1007,
                    "Reversal confirmation must come from a different role than the initiator (dual authorization)");
        }

        User confirmer = userRepository.findById(confirmerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        CreditNote creditNote = CreditNote.builder()
                .tenant(statement.getTenant())
                .originalStatement(statement)
                .creditNoteNumber(generateCreditNoteNumber(statement))
                .type("CREDIT_NOTE")
                .amount(statement.getNetSettlementAmount().negate())
                .reason(statement.getReversalReason())
                .issuedBy(confirmer)
                .status(CreditNote.CreditNoteStatus.PENDING)
                .build();
        creditNoteRepository.save(creditNote);

        statement.setStatus(SettlementStatus.REVERSED);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement reversal confirmed: statementId={}, confirmerId={}, confirmerRole={}, "
                + "creditNoteId={}", statementId, confirmerId, confirmerRole, creditNote.getId());

        return mapper.toStatementResponse(statement);
    }

    private void validateReversalRole(final String role) {
        if (!"SUPER_ADMIN".equals(role) && !"CFO".equals(role)) {
            throw new BusinessException(ErrorCode.E_1007, "Only SUPER_ADMIN or CFO may act on statement reversal");
        }
    }

    private String generateCreditNoteNumber(final SettlementStatement statement) {
        String dateStr = java.time.LocalDate.now().toString().replace("-", "");
        return "CN-" + statement.getTenantId().toString().substring(0, CREDIT_NOTE_NUMBER_LENGTH) + "-" + dateStr;
    }
}
