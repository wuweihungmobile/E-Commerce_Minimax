package com.nextkey.ecommerce.core.settlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.CreditNote;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.CreditNoteRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.persistence.UniqueConflictRetry;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.util.PageableUtils;

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
    private final UniqueConflictRetry uniqueConflictRetry;

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

        // 🔴 併發防護（Sprint 137 DEF-138）：見 SettlementStatementRepository.updateStatusIfCurrent 說明。
        int claimed = settlementRepository.updateStatusIfCurrent(statementId,
                SettlementStatus.PAID, SettlementStatus.REVERSAL_PENDING);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_5014, "Only PAID statements can be reversed");
        }

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
     *
     * <p>Sprint 324（DEF-448）：信用票據編號帶當日流水號，同一租戶同一天、<strong>不同</strong>結算單同時確認可能算出同一個號碼；
     * {@code credit_notes} 的 {@code UNIQUE (tenant_id, credit_note_number)}（V36）是最終仲裁者，輸家的 INSERT 撞約束時，
     * 整個交易（含下面的狀態搶占）回滾，由 {@link UniqueConflictRetry} 在新交易裡重做——重做時贏家的票據已提交，流水號自然錯開。
     * 所以這個方法<strong>不加</strong> {@code @Transactional}：{@code UniqueConflictRetry} 必須是最外層（已在交易內就無法重做），
     * 交易由它裡面的 {@code TransactionTemplate} 開。做法同 {@code SupportTicketService.createTicket}。同一張結算單的輸家
     * 仍在狀態搶占得 {@code 409 E-5014}（{@code BusinessException}，不是唯一約束違反，不會被重試）。
     */
    public SettlementStatementResponse confirmReversal(
            final UUID statementId, final UUID confirmerId, final String confirmerRole) {
        return uniqueConflictRetry.execute(() -> confirmReversalOnce(statementId, confirmerId, confirmerRole));
    }

    private SettlementStatementResponse confirmReversalOnce(
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

        // 🔴 併發防護（Sprint 323 DEF-447）：上面的狀態檢查只是快照。原本這裡沒有任何搶占：兩位 CFO（或同一位連點兩下）
        // 同時確認，都會通過檢查、各自產生一張信用票據。舊編號（租戶前綴＋日期）對同一張結算單必然相同，正式環境只靠
        // credit_notes 的 UNIQUE (tenant_id, credit_note_number) 偶然擋住後到者（輸家得到資料庫錯誤 500 E-9900，不是乾淨的 409）；
        // 沒有那條約束的地方（整合測試庫）兩個都成功，同一張逆轉被沖銷兩次。Sprint 324（DEF-448）起編號帶當日流水號，
        // 同一張結算單的兩個確認不再靠撞約束分勝負，這個搶占就是唯一的防線。比照 initiateReversal（DEF-138）與批准／駁回
        // （DEF-442）：先條件式更新搶占，輸家（回傳 0）不產生信用票據。
        //
        // 搶占的 UPDATE 已把狀態寫完，之後不再 setStatus／save：REVERSAL_PENDING 的內容是凍結的（金額在 DEF-446 之後只在
        // PENDING／PENDING_REVIEW 變動，逆轉欄位只在 PAID → REVERSAL_PENDING 時寫一次），所以不需要 refresh；而讓實體保持
        // 乾淨（沒有 setter）就不會有整列寫回（DEF-444 的機制）。回應的狀態補在 DTO 上，不補在實體上。
        int claimed = settlementRepository.updateStatusIfCurrent(statementId,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_5014, "Only REVERSAL_PENDING statements can be confirmed");
        }

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

        log.info("Settlement statement reversal confirmed: statementId={}, confirmerId={}, confirmerRole={}, "
                + "creditNoteId={}", statementId, confirmerId, confirmerRole, creditNote.getId());

        SettlementStatementResponse response = mapper.toStatementResponse(statement);
        response.setStatus(SettlementStatus.REVERSED.name());
        return response;
    }

    private static final List<SettlementStatus> REVERSAL_CANDIDATE_STATUSES =
            List.of(SettlementStatus.PAID, SettlementStatus.REVERSAL_PENDING);

    /**
     * 取得逆轉候選結算單（PAID 可發起、REVERSAL_PENDING 可確認），跨租戶（Sprint 90）。
     *
     * <p>與 {@link SettlementReviewer#getPendingReviewStatements} 不同，此方法不區分
     * SUPER_ADMIN/CFO——呼叫路徑已由 Controller {@code @PreAuthorize("hasAuthority('settlement:reverse')")}
     * 鎖死，僅 SUPER_ADMIN/CFO 可觸達，且兩者對 {@link #initiateReversal}/{@link #confirmReversal}
     * 本就一律跨租戶操作，列表理應對等，不做「限自己租戶」回退分支。
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getReversalCandidateStatements(
            final int page, final int size, final UUID tenantIdOverride) {
        Pageable pageable = PageableUtils.of(page, size, 100);
        Page<SettlementStatement> statements = tenantIdOverride != null
                ? settlementRepository.findByTenantIdAndStatusInOrderByGeneratedAtDesc(
                        tenantIdOverride, REVERSAL_CANDIDATE_STATUSES, pageable)
                : settlementRepository.findByStatusInOrderByGeneratedAtDesc(REVERSAL_CANDIDATE_STATUSES, pageable);

        return SettlementStatementListResponse.builder()
                .statements(statements.getContent().stream()
                        .map(mapper::toStatementResponse)
                        .collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(statements.getTotalElements())
                .totalPages(statements.getTotalPages())
                .build();
    }

    private void validateReversalRole(final String role) {
        if (!"SUPER_ADMIN".equals(role) && !"CFO".equals(role)) {
            throw new BusinessException(ErrorCode.E_1007, "Only SUPER_ADMIN or CFO may act on statement reversal");
        }
    }

    /**
     * 信用票據編號：{@code CN-{租戶 id 前 8 碼}-{開立日 yyyyMMdd}-{當日流水號}}（Sprint 324，DEF-448，使用者以互動選項選定）。
     * 流水號 ＝ 該租戶當日已開立張數 ＋ 1，至少兩位（{@code 01}、{@code 02}…）。前綴帶結尾的 {@code -}，所以舊格式的編號
     * （沒有流水號後綴）不會被算進去，也不會與新編號相同。計數與 INSERT 之間不是原子的（check-then-act）：兩張不同結算單同時確認
     * 會算出同一個號碼，由 V36 的唯一約束仲裁、{@link UniqueConflictRetry} 重做（見 {@link #confirmReversal}）。
     * 開立日沿用原本的 {@code LocalDate.now()}（JVM 預設時區），本次沒有改時區語意。
     */
    private String generateCreditNoteNumber(final SettlementStatement statement) {
        String dateStr = java.time.LocalDate.now().toString().replace("-", "");
        String prefix = "CN-" + statement.getTenantId().toString().substring(0, CREDIT_NOTE_NUMBER_LENGTH) + "-" + dateStr + "-";
        long issuedToday = creditNoteRepository.countByTenantIdAndCreditNoteNumberStartingWith(statement.getTenantId(), prefix);
        return prefix + String.format("%02d", issuedToday + 1);
    }
}
