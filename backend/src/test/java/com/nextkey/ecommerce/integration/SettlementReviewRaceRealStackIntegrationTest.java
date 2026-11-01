package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * 兩位管理員同時批准／駁回同一張結算單（DEF-442，Sprint 320）：只有一個能成功，另一個要得到 {@code 409 E-5014}，
 * 而且結算單、transfer、訂單認領、稽核紀錄必須全都停在「贏家那一邊」的一致狀態。
 *
 * <p>做法（真實競態，不靠 mock、不靠睡眠）：一個「對手」交易先對結算單那一列取得 {@code FOR UPDATE} 鎖並停住；
 * 兩個請求都用 MockMvc 走完整條 HTTP／JWT／權限鏈，各自先讀到 {@code PENDING_REVIEW}、通過狀態檢查，再卡在寫入結算單
 * 的 {@code UPDATE}（用 {@code pg_stat_activity} 確認兩個都在等鎖，不靠睡眠）。放行對手後，先到的先寫、後到的後寫——
 * 「兩個請求都在對方寫入前讀過」因此是確定的，而不是碰運氣。
 *
 * <p>斷言的是一致性不變量（贏家與輸家各自的回應，加上資料庫最終狀態），不綁定修法的機制：結算單的狀態轉換不論用
 * 條件式更新或悲觀鎖，都該通過同一組斷言。
 *
 * <p>Sprint 321（DEF-444）補上另一類不變量：搶占只保證<strong>狀態</strong>轉換不被覆寫，批准／駁回之後的整列寫回仍會用
 * 讀取當下的舊金額蓋掉同時入帳的退款。這類案例的對手不是另一位管理員，而是一筆真實的退款（
 * {@code SettlementAdjustmentService.handleOrderRefund}）：它先提交，批准／駁回的整列寫回不可以把它抹掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-REVIEW-RACE: 批准／駁回結算單的併發要有且只有一個贏家（DEF-442）")
class SettlementReviewRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;
    private static final String CONFLICT_CODE = "E-5014";
    /** 種子結算單是 GMV 1000、抽成 100、淨額 900；這筆退款入帳後應該是 total_refunds 100、淨額 800（DEF-444）。 */
    private static final BigDecimal REFUND = new BigDecimal("100.00");

    /** 只有「送給 Stripe 的金額」那個案例會走到閘道（店鋪設成可撥款）；其餘案例的店鋪沒有 Connect 帳戶，撥款在到達閘道前就略過。 */
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;
    @Autowired private SettlementAdjustmentService settlementAdjustmentService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private ContractFixture fx;
    private User firstAdmin;
    private User secondAdmin;
    private String firstAdminToken;
    private String secondAdminToken;
    private ExecutorService pool;
    private final List<String> createdIndexes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        firstAdmin = fx.admin;
        firstAdminToken = fx.adminToken;
        secondAdmin = userRepository.save(User.builder()
                .email("settle-race-admin2-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy").fullName("second admin").role(User.UserRole.SUPER_ADMIN).status("ACTIVE").build());
        secondAdminToken = jwtTokenService.generateAccessToken(secondAdmin.getId(), secondAdmin.getEmail(),
                "SUPER_ADMIN", ContractFixture.SYSTEM_TENANT_ID.toString());
        pool = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        // 若案例中途失敗，仍被擋著的對手交易在這裡被中斷並回滾，不在資料庫留下開著的交易
        pool.shutdownNow();
        createdIndexes.forEach(name -> jdbcTemplate.execute("DROP INDEX IF EXISTS " + name));
    }

    // ── 對照：不含併發 ─────────────────────────────────────────────

    /**
     * 不含併發的對照：循序批准後，再駁回或再批准都被狀態檢查擋下。它一失敗，下面的併發案例紅燈就不能歸因於競態。
     */
    @Test
    @DisplayName("對照（不含併發）：循序批准後再駁回、再批准都得到 409 E-5014，結算單維持 APPROVED、只有一筆批准稽核與一筆 transfer")
    void sequential_secondTransitionIsRejectedByStatusCheck() throws Exception {
        Seed seed = seedPendingReview();

        MockHttpServletResponse approved = approve(seed.statementId, firstAdminToken);
        MockHttpServletResponse rejectedAfter = reject(seed.statementId, secondAdminToken, "太晚了");
        MockHttpServletResponse approvedAgain = approve(seed.statementId, secondAdminToken);

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("第一次批准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(rejectedAfter.getStatus()).as("批准後再駁回：%s", safeBody(rejectedAfter)).isEqualTo(409);
            softly.assertThat(approvedAgain.getStatus()).as("批准後再批准：%s", safeBody(approvedAgain)).isEqualTo(409);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.approvedAudits).as("批准稽核筆數：%s", state).isEqualTo(1);
            softly.assertThat(state.rejectedAudits).as("駁回稽核筆數：%s", state).isZero();
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).hasSize(1);
        });
    }

    // ── 併發 ──────────────────────────────────────────────────────

    /**
     * 批准 vs 批准（例如同一位管理員連點兩下，或兩位管理員同時審核）。Connect 帳戶未就緒，所以 transfer 是
     * {@code SKIPPED_ONBOARDING_INCOMPLETE}、不呼叫 Stripe。
     */
    @Test
    @DisplayName("批准 vs 批准：後到的得到 409 E-5014；結算單維持第一位管理員批准的 APPROVED；只有一筆批准稽核與一筆 transfer")
    void approveVsApprove_secondAdminLoses() throws Exception {
        Seed seed = seedPendingReview();

        Outcome outcome = race(seed, () -> approve(seed.statementId, firstAdminToken),
                () -> approve(seed.statementId, secondAdminToken));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            assertWinnerAndLoser(softly, outcome);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.reviewedBy).as("審核人要是贏家：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.approvedAudits).as("批准稽核只該有贏家那一筆：%s", state).isEqualTo(1);
            softly.assertThat(state.rejectedAudits).as("駁回稽核：%s", state).isZero();
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).hasSize(1);
            softly.assertThat(state.orderClaimedByStatement).as("訂單仍掛在已批准的結算單上：%s", state).isTrue();
        });
    }

    /**
     * 贏家已經把結算單推到 {@code PAID}（對手交易模擬：改成 PAID 並寫入一筆 COMPLETED 的 transfer，尚未提交），
     * 此時一個批准請求（它先前讀到的是 PENDING_REVIEW）才寫入。後到者不可以把已付款的結算單改回 APPROVED。
     */
    @Test
    @DisplayName("贏家已付款（PAID，transfer COMPLETED）時後到的批准：得到 409 E-5014；結算單維持 PAID，不被改回 APPROVED")
    void approveAfterWinnerAlreadyPaid_doesNotOverwritePaid() throws Exception {
        Seed seed = seedPendingReview();

        HeldTransaction winner = holdTransaction(jdbc -> {
            jdbc.update("UPDATE settlement_statements SET status = 'PAID', approved_at = NOW(), reviewed_at = NOW(), "
                    + "reviewed_by = ?, paid_at = NOW() WHERE id = ?", firstAdmin.getId(), seed.statementId);
            jdbc.update("INSERT INTO transfers (id, settlement_statement_id, tenant_id, stripe_transfer_id, "
                    + "transfer_amount, currency, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, 'tr_it_settle_race', 900.00, 'TWD', 'COMPLETED', NOW(), NOW())",
                    UUID.randomUUID(), seed.statementId, fx.store.getId());
        });
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(() -> approve(seed.statementId, secondAdminToken));
            awaitRequestsBlockedOnStatement(1);
            Assertions.assertThat(request.isDone()).as("批准請求應該卡在贏家尚未提交的寫入上").isFalse();
            winner.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            winner.release();
        }

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(loser.getStatus()).as("後到的批准：%s", safeBody(loser)).isEqualTo(409);
            softly.assertThat(code(loser)).as("後到的批准：%s", safeBody(loser)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("已付款的結算單不可被改回 APPROVED：%s", state).isEqualTo("PAID");
            softly.assertThat(state.reviewedBy).as("審核人仍是贏家：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.approvedAudits).as("後到的批准不可留下稽核：%s", state).isZero();
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("COMPLETED");
        });
    }

    /** 批准先、駁回後：駁回要輸，結算單與 transfer、訂單認領停在批准那一邊。 */
    @Test
    @DisplayName("批准 vs 駁回（批准先）：駁回得到 409 E-5014；結算單 APPROVED、transfer 在、訂單仍被認領；沒有駁回稽核")
    void approveThenReject_rejectLoses() throws Exception {
        Seed seed = seedPendingReview();

        Outcome outcome = race(seed, () -> approve(seed.statementId, firstAdminToken),
                () -> reject(seed.statementId, secondAdminToken, "第二位管理員駁回"));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            assertWinnerAndLoser(softly, outcome);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).hasSize(1);
            softly.assertThat(state.orderClaimedByStatement)
                    .as("已批准（會撥款）的結算單，訂單不可被釋放，否則下一期會重複結算：%s", state).isTrue();
            softly.assertThat(state.approvedAudits).as("批准稽核：%s", state).isEqualTo(1);
            softly.assertThat(state.rejectedAudits).as("輸家不可留下駁回稽核：%s", state).isZero();
            softly.assertThat(state.rejectionReason).as("輸家的駁回原因不可寫入：%s", state).isNull();
        });
    }

    /** 駁回先、批准後：批准要輸，不可建立 transfer（不可撥款），訂單維持釋放。 */
    @Test
    @DisplayName("駁回 vs 批准（駁回先）：批准得到 409 E-5014；結算單 REJECTED、沒有 transfer（不撥款）、訂單已釋放；沒有批准稽核")
    void rejectThenApprove_approveLoses() throws Exception {
        Seed seed = seedPendingReview();

        Outcome outcome = race(seed, () -> reject(seed.statementId, firstAdminToken, "第一位管理員駁回"),
                () -> approve(seed.statementId, secondAdminToken));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            assertWinnerAndLoser(softly, outcome);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("REJECTED");
            softly.assertThat(state.transferStatuses).as("被駁回的結算單不可撥款：%s", state).isEmpty();
            softly.assertThat(state.orderClaimedByStatement)
                    .as("被駁回的結算單，訂單必須釋放（否則永遠不被結算）：%s", state).isFalse();
            softly.assertThat(state.rejectedAudits).as("駁回稽核：%s", state).isEqualTo(1);
            softly.assertThat(state.approvedAudits).as("輸家不可留下批准稽核：%s", state).isZero();
            softly.assertThat(state.reviewedBy).as("審核人要是贏家：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.rejectionReason).as("駁回原因要是贏家寫的：%s", state).isEqualTo("第一位管理員駁回");
        });
    }

    /** 駁回 vs 駁回：後到的要輸，只留贏家的審核人、原因與稽核。 */
    @Test
    @DisplayName("駁回 vs 駁回：後到的得到 409 E-5014；審核人與駁回原因是贏家的；只有一筆駁回稽核")
    void rejectVsReject_secondAdminLoses() throws Exception {
        Seed seed = seedPendingReview();

        Outcome outcome = race(seed, () -> reject(seed.statementId, firstAdminToken, "原因甲"),
                () -> reject(seed.statementId, secondAdminToken, "原因乙"));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            assertWinnerAndLoser(softly, outcome);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("REJECTED");
            softly.assertThat(state.reviewedBy).as("審核人要是贏家：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.rejectionReason).as("駁回原因要是贏家寫的：%s", state).isEqualTo("原因甲");
            softly.assertThat(state.rejectedAudits).as("駁回稽核只該有贏家那一筆：%s", state).isEqualTo(1);
            softly.assertThat(state.orderClaimedByStatement).as("訂單已釋放：%s", state).isFalse();
        });
    }

    // ── 非狀態欄位：同時入帳的退款不可被整列寫回蓋掉（DEF-444，Sprint 321）─────────────

    /**
     * 不含併發的對照：退款先提交、批准後到，批准讀到的就是退款後的金額，所以 total_refunds／淨額／transfer 金額都對。
     * 它一失敗，下面兩個併發案例的紅燈就不能歸因於「整列寫回用了過期金額」。
     */
    @Test
    @DisplayName("對照（不含併發，DEF-444）：退款先提交、批准後到 → total_refunds 100、淨額 800、transfer 金額 800、回應也是 800")
    void sequential_refundBeforeApprove_amountsAreReduced() throws Exception {
        Seed seed = seedPendingReview();
        settlementAdjustmentService.handleOrderRefund(fx.store.getId(), seed.orderId, REFUND);

        MockHttpServletResponse approved = approve(seed.statementId, firstAdminToken);

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("批准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.totalRefunds).as("退款扣款：%s", state).isEqualTo("100.00");
            softly.assertThat(state.netAmount).as("淨額：%s", state).isEqualTo("800.00");
            softly.assertThat(state.transferAmounts).as("transfer 金額：%s", state).containsExactly("800.00");
            softly.assertThat(responseAmount(approved, "totalRefunds")).as("回應的 totalRefunds：%s", safeBody(approved))
                    .isEqualTo("100.00");
            softly.assertThat(responseAmount(approved, "netSettlementAmount"))
                    .as("回應的 netSettlementAmount：%s", safeBody(approved)).isEqualTo("800.00");
        });
    }

    /**
     * 批准請求先讀到「沒有退款」的結算單（total_refunds 0／淨額 900），卡在搶占的 UPDATE；此時一筆退款
     * （真實的 {@code SettlementAdjustmentService.handleOrderRefund}，對 PENDING_REVIEW 的結算單以原子 UPDATE 扣款）先提交。
     * 批准的整列寫回不可以用讀到的舊金額把退款蓋掉，也不可以拿舊的淨額撥款——否則賣家多拿到被退款的錢。
     */
    @Test
    @DisplayName("批准時同時入帳的退款（DEF-444）：total_refunds 100、淨額 800 保留；transfer 金額是 800；回應也是 800")
    void approveWhileRefundLandsConcurrently_refundIsNotOverwritten() throws Exception {
        Seed seed = seedPendingReview();

        MockHttpServletResponse approved = requestWhileRefundLands(seed, () -> approve(seed.statementId, firstAdminToken));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("批准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.reviewedBy).as("審核人：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.totalRefunds).as("同時入帳的退款不可被蓋掉：%s", state).isEqualTo("100.00");
            softly.assertThat(state.netAmount).as("淨額要扣掉退款：%s", state).isEqualTo("800.00");
            softly.assertThat(state.transferAmounts).as("撥款金額要用退款後的淨額：%s", state).containsExactly("800.00");
            softly.assertThat(responseAmount(approved, "totalRefunds")).as("回應的 totalRefunds：%s", safeBody(approved))
                    .isEqualTo("100.00");
            softly.assertThat(responseAmount(approved, "netSettlementAmount"))
                    .as("回應的 netSettlementAmount：%s", safeBody(approved)).isEqualTo("800.00");
        });
    }

    /**
     * 上面的案例斷言資料庫裡的金額；這個案例量到<strong>錢的出口</strong>：退款在批准期間入帳時，送給 Stripe 的撥款金額必須是退款後的
     * 淨額（800.00＝80000 分）。修復前批准會用過期的 900.00 撥款，賣家多拿到被退款的 100——Sprint 320 只能「依構造推論」
     * （測試租戶的 transfer 一律略過、沒有走到閘道），這裡讓店鋪可撥款，用 {@code @MockBean} 的閘道直接看它收到什麼。
     */
    @Test
    @DisplayName("批准時同時入帳的退款（DEF-444）：送給 Stripe 的撥款金額是退款後的淨額 80000 分（不是過期的 90000 分）")
    void approveWhileRefundLandsConcurrently_stripeIsAskedToTransferTheReducedNet() throws Exception {
        Seed seed = seedPendingReview();
        String connectAccountId = makeStorePayoutReady();
        when(paymentGatewayFactory.createTransfer(eq("STRIPE"), eq(connectAccountId), anyLong(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_it_refund_race").build());

        MockHttpServletResponse approved = requestWhileRefundLands(seed, () -> approve(seed.statementId, firstAdminToken));

        ArgumentCaptor<Long> cents = ArgumentCaptor.forClass(Long.class);
        verify(paymentGatewayFactory).createTransfer(eq("STRIPE"), eq(connectAccountId), cents.capture(), eq("TWD"),
                eq(seed.statementId.toString()));
        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("批准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(cents.getValue()).as("送給 Stripe 的金額（分）：%s", state).isEqualTo(80000L);
            softly.assertThat(state.status).as("撥款完成：%s", state).isEqualTo("PAID");
            softly.assertThat(state.totalRefunds).as("退款扣款：%s", state).isEqualTo("100.00");
            softly.assertThat(state.transferAmounts).as("transfer 金額：%s", state).containsExactly("800.00");
        });
    }

    /**
     * 駁回同樣會整列寫回。結算單作廢、訂單下一期重新結算，金額影響比批准小，但「別人已提交的原子更新被我蓋掉」的不變量一樣。
     */
    @Test
    @DisplayName("駁回時同時入帳的退款（DEF-444）：total_refunds 100、淨額 800 保留；訂單已釋放；審核人與駁回原因是這位管理員的")
    void rejectWhileRefundLandsConcurrently_refundIsNotOverwritten() throws Exception {
        Seed seed = seedPendingReview();

        MockHttpServletResponse rejected = requestWhileRefundLands(seed,
                () -> reject(seed.statementId, firstAdminToken, "金額有疑慮"));

        State state = stateOf(seed);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(rejected.getStatus()).as("駁回：%s", safeBody(rejected)).isEqualTo(200);
            softly.assertThat(state.status).as("最終狀態：%s", state).isEqualTo("REJECTED");
            softly.assertThat(state.reviewedBy).as("審核人：%s", state).isEqualTo(firstAdmin.getId());
            softly.assertThat(state.rejectionReason).as("駁回原因：%s", state).isEqualTo("金額有疑慮");
            softly.assertThat(state.orderClaimedByStatement).as("訂單已釋放：%s", state).isFalse();
            softly.assertThat(state.totalRefunds).as("同時入帳的退款不可被蓋掉：%s", state).isEqualTo("100.00");
            softly.assertThat(state.netAmount).as("淨額要扣掉退款：%s", state).isEqualTo("800.00");
        });
    }

    // ── 輔助：競態編排 ─────────────────────────────────────────────

    private interface Call {
        MockHttpServletResponse run() throws Exception;
    }

    private record Outcome(MockHttpServletResponse first, MockHttpServletResponse second) { }

    /**
     * 讓兩個請求都先讀到 PENDING_REVIEW、再都卡在寫入結算單的 UPDATE，然後放行：{@code first} 先到（先被觀察到卡住），
     * 所以先寫；{@code second} 後到、後寫。
     */
    private Outcome race(final Seed seed, final Call first, final Call second) throws Exception {
        HeldTransaction lock = holdTransaction(jdbc -> jdbc.queryForList(
                "SELECT id FROM settlement_statements WHERE id = ? FOR UPDATE", seed.statementId));
        Future<MockHttpServletResponse> firstRequest;
        Future<MockHttpServletResponse> secondRequest;
        try {
            firstRequest = pool.submit(first::run);
            awaitRequestsBlockedOnStatement(1);
            secondRequest = pool.submit(second::run);
            awaitRequestsBlockedOnStatement(2);
            Assertions.assertThat(firstRequest.isDone() || secondRequest.isDone())
                    .as("兩個請求都應該卡在對手交易持有的結算單那一列上").isFalse();
            lock.commit();
            return new Outcome(firstRequest.get(AWAIT_SECONDS, TimeUnit.SECONDS),
                    secondRequest.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            lock.release();
        }
    }

    private void assertWinnerAndLoser(final SoftAssertions softly, final Outcome outcome) {
        softly.assertThat(outcome.first.getStatus()).as("先到的請求：%s", safeBody(outcome.first)).isEqualTo(200);
        softly.assertThat(outcome.second.getStatus()).as("後到的請求要輸：%s", safeBody(outcome.second)).isEqualTo(409);
        softly.assertThat(code(outcome.second)).as("後到的請求要輸：%s", safeBody(outcome.second)).isEqualTo(CONFLICT_CODE);
    }

    /**
     * 一筆退款先寫入結算單那一列（真實的退款服務、持鎖、尚未提交）；{@code call} 的請求先讀到退款前的金額、卡在寫入；
     * 然後退款提交。回傳那個請求的回應。請求「已讀過舊金額」因此是確定的（它被觀察到卡住時，退款還沒提交）。
     */
    private MockHttpServletResponse requestWhileRefundLands(final Seed seed, final Call call) throws Exception {
        HeldTransaction refund = holdTransaction(jdbc ->
                settlementAdjustmentService.handleOrderRefund(fx.store.getId(), seed.orderId, REFUND));
        try {
            Future<MockHttpServletResponse> request = pool.submit(call::run);
            awaitRequestsBlockedOnStatement(1);
            Assertions.assertThat(request.isDone()).as("請求應該卡在退款交易持有的結算單那一列上").isFalse();
            refund.commit();
            return request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            refund.release();
        }
    }

    /** 讓種子結算單所屬的店鋪成為可撥款的：Connect 帳戶就緒，且撥款功能開關開啟。回傳 Connect 帳戶 id。 */
    private String makeStorePayoutReady() {
        String accountId = "acct_it_refund_race_" + System.nanoTime();
        jdbcTemplate.update("UPDATE tenants SET stripe_connect_account_id = ?, connect_onboarding_status = 'COMPLETE', "
                + "connect_charges_enabled = TRUE, connect_payouts_enabled = TRUE WHERE id = ?",
                accountId, fx.store.getId());
        jdbcTemplate.update("INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, created_at, "
                + "updated_at) VALUES (?, ?, 'STRIPE_TRANSFER_ENABLED', TRUE, NOW(), NOW())",
                UUID.randomUUID(), fx.store.getId());
        return accountId;
    }

    // ── 輔助：資料 ────────────────────────────────────────────────

    private record Seed(UUID statementId, UUID orderId) { }

    /**
     * 一張 PENDING_REVIEW 的結算單，與一筆被它認領的訂單（駁回時應釋放、批准時應保留）。
     *
     * <p>整合測試資料庫是 Hibernate 建的，沒有 Flyway V64 的 {@code transfers.settlement_statement_id} 唯一索引；
     * 沒有它，兩個批准會各插入一筆 transfer，與正式環境（第二筆會撞唯一索引）不同。這裡建一個只涵蓋本案例結算單的同形索引，
     * 收尾移除（先例：{@code UniqueConstraintRaceRealStackIntegrationTest}）。
     */
    private Seed seedPendingReview() {
        UUID statementId = UUID.randomUUID();
        String indexName = "idx_it_settle_race_transfers_" + statementId.toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON transfers (settlement_statement_id) WHERE settlement_statement_id = '" + statementId + "'");
        createdIndexes.add(indexName);
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-21', DATE '2026-12-27', 1, 1000.00, 0.00, 100.00, 900.00,
                        'TWD', 'PENDING_REVIEW', NOW(), NOW(), NOW())
                """, statementId, fx.store.getId(), "IT-S320-" + System.nanoTime());
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at,
                                    settled_statement_id)
                VALUES (?, ?, ?, 'PRODUCT', 'COMPLETED', 1000.00, 0.00, 0.00, 'TWD', NOW(), NOW(), ?)
                """, orderId, fx.store.getId(), fx.buyer.getId(), statementId);
        return new Seed(statementId, orderId);
    }

    /** 金額一律轉成兩位小數的字串（{@code "800.00"}），失敗訊息讀起來直接，也避開 {@code BigDecimal} 的 scale 比較陷阱。 */
    private record State(String status, UUID reviewedBy, String rejectionReason, List<String> transferStatuses,
                         boolean orderClaimedByStatement, long approvedAudits, long rejectedAudits,
                         String totalRefunds, String netAmount, List<String> transferAmounts) { }

    private State stateOf(final Seed seed) {
        var row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, rejection_reason, total_refunds, net_settlement_amount "
                        + "FROM settlement_statements WHERE id = ?", seed.statementId);
        List<String> transfers = jdbcTemplate.queryForList(
                "SELECT status FROM transfers WHERE settlement_statement_id = ? ORDER BY created_at",
                String.class, seed.statementId);
        List<String> transferAmounts = jdbcTemplate.queryForList(
                "SELECT transfer_amount FROM transfers WHERE settlement_statement_id = ? ORDER BY created_at",
                BigDecimal.class, seed.statementId).stream().map(SettlementReviewRaceRealStackIntegrationTest::money).toList();
        UUID claimedBy = jdbcTemplate.queryForObject(
                "SELECT settled_statement_id FROM orders WHERE id = ?", UUID.class, seed.orderId);
        return new State((String) row.get("status"), (UUID) row.get("reviewed_by"), (String) row.get("rejection_reason"),
                transfers, seed.statementId.equals(claimedBy), auditCount(seed, "SETTLEMENT_APPROVED"),
                auditCount(seed, "SETTLEMENT_REJECTED"), money(row.get("total_refunds")), money(row.get("net_settlement_amount")),
                transferAmounts);
    }

    private static String money(final Object amount) {
        return amount == null ? "<null>" : new BigDecimal(amount.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 回應 {@code data} 裡的金額欄位（兩位小數字串）；欄位不存在或回應不是 JSON 時回標記字串，讓斷言訊息自己說明。 */
    private String responseAmount(final MockHttpServletResponse response, final String field) {
        try {
            JsonNode node = objectMapper.readTree(safeBody(response)).path("data").path(field);
            return node.isMissingNode() || node.isNull() ? "<missing>" : money(node.decimalValue());
        } catch (java.io.IOException e) {
            return "<not json>";
        }
    }

    private long auditCount(final Seed seed, final String action) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'SETTLEMENT_STATEMENT' AND entity_id = ? AND action = ?",
                Long.class, seed.statementId, action);
        return count == null ? 0 : count;
    }

    // ── 輔助：HTTP ────────────────────────────────────────────────

    private MockHttpServletResponse approve(final UUID statementId, final String token) throws Exception {
        return send(put("/v2/admin/settlements/{id}/approve", statementId), token);
    }

    private MockHttpServletResponse reject(final UUID statementId, final String token, final String reason)
            throws Exception {
        return send(put("/v2/admin/settlements/{id}/reject", statementId).param("reason", reason), token);
    }

    private MockHttpServletResponse send(final MockHttpServletRequestBuilder builder, final String token) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static String safeBody(final MockHttpServletResponse response) {
        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            return "<unreadable>";
        }
    }

    private String code(final MockHttpServletResponse response) {
        try {
            JsonNode json = objectMapper.readTree(safeBody(response));
            return json.path("code").asText();
        } catch (java.io.IOException e) {
            return "<not json>";
        }
    }

    // ── 輔助：對手交易與「請求已卡住」的確定性訊號 ───────────────────

    /**
     * 一個開著、尚未提交的交易：先執行寫入（或鎖定），然後停住，直到 {@link #commit()} 才提交。
     * {@link #release()} 是收尾用的（已提交時無作用），確保案例中途失敗也不留下開著的交易。
     */
    private final class HeldTransaction {

        private final CountDownLatch written = new CountDownLatch(1);
        private final CountDownLatch releaseSignal = new CountDownLatch(1);
        private final Future<?> finished;

        HeldTransaction(final Consumer<JdbcTemplate> write) throws Exception {
            finished = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        write.accept(jdbcTemplate);
                        written.countDown();
                        try {
                            releaseSignal.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    });
                } finally {
                    written.countDown();
                }
            });
            Assertions.assertThat(written.await(AWAIT_SECONDS, TimeUnit.SECONDS)).as("對手交易在時限內寫入").isTrue();
            if (finished.isDone()) {
                finished.get();
            }
        }

        void commit() throws Exception {
            releaseSignal.countDown();
            finished.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }

        void release() {
            releaseSignal.countDown();
        }
    }

    private HeldTransaction holdTransaction(final Consumer<JdbcTemplate> write) throws Exception {
        return new HeldTransaction(write);
    }

    /**
     * 至少 {@code expected} 個請求已經卡在「等鎖」上：{@code pg_stat_activity} 裡有這麼多條連線正在等 {@code Lock}
     * 型事件，而且正在執行的語句提到結算單（寫結算單那一列的 {@code UPDATE}，或帶著 {@code settlement_statement_id}
     * 欄位、撞上對手 transfer 唯一索引項目的 {@code INSERT INTO transfers}）。用它取代睡眠——睡多久都不能證明請求
     * 真的已經讀過、並且卡在寫入上。第一個等待者顯示 {@code transactionid}、後面的顯示 {@code tuple}，所以不限定事件名稱。
     */
    private void awaitRequestsBlockedOnStatement(final int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        Integer waiting = 0;
        while (System.nanoTime() < deadline) {
            waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                            + "AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query ILIKE ?",
                    Integer.class, "%settlement_statement%");
            if (waiting != null && waiting >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等不到 " + expected + " 個請求卡在結算單的鎖上（目前 " + waiting + " 個）");
    }
}
