package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * 結算單<strong>核准之後</strong>的狀態對狀態競態（DEF-447，Sprint 323）：撥款失敗後的重試、Stripe 的
 * {@code transfer.reversed} webhook、PAID 結算單的逆轉（發起／確認）。
 *
 * <p>這三條路徑原本<strong>沒有任何真實棧案例</strong>（提到它們的測試只有 Mockito 單元測試與 {@code @MockBean} 的
 * controller 測試），而它們全都是「讀狀態 → setter → {@code save}」的整列寫回：{@code SettlementStatement} 沒有
 * {@code @DynamicUpdate}，所以後寫者會用讀取當下的舊值，連同它沒打算改的欄位一起蓋掉先寫者已提交的結果。
 *
 * <p>編排沿用 {@code SettlementReviewRaceRealStackIntegrationTest}（不改它，保持既有 18 個案例與其突變驗證結果不動）：
 * 一個「對手」交易先對結算單那一列取得 {@code FOR UPDATE} 鎖並停住；兩個請求各自先讀（普通 SELECT 不被擋）、通過檢查，
 * 再卡在第一個寫入（用 {@code pg_stat_activity} 確認，不靠睡眠）；放行後先到的先寫、後到的後寫——「兩個請求都在對方
 * 寫入前讀過」因此是確定的。每個併發案例都有一個不含併發的對照，紅燈才能歸因於競態。
 *
 * <p>斷言的是一致性不變量（回應、結算單最終狀態與逆轉欄位、transfer、信用票據、閘道呼叫次數），不綁定修法的機制。
 *
 * <p>整合測試資料庫是 Hibernate 建的，沒有 Flyway 的唯一約束：{@code transfers.settlement_statement_id}（V64）與
 * {@code credit_notes (tenant_id, credit_note_number)}（V36）。需要它們的案例自己建一個<strong>只涵蓋本案例</strong>
 * 的同形索引，收尾移除（先例：{@code SettlementReviewRaceRealStackIntegrationTest}、{@code UniqueConstraintRaceRealStackIntegrationTest}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-POST-RACE: 結算單核准之後（撥款重試、transfer.reversed webhook、逆轉）的併發（DEF-447）")
class SettlementPostApprovalRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;
    private static final String CONFLICT_CODE = "E-5014";
    private static final String REASON = "對帳發現重複撥款";
    private static final String WEBHOOK_OK = "ok";

    /** 與 {@code SettlementReviewRaceRealStackIntegrationTest} 相同的 mock 組合，兩個類別共用同一個 Spring context。 */
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;
    @Autowired private PaymentWebhookService paymentWebhookService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private ContractFixture fx;
    private User superAdmin;
    private String superAdminToken;
    private String cfoOneToken;
    private String cfoTwoToken;
    private ExecutorService pool;
    private final List<String> createdIndexes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        superAdmin = fx.admin;
        superAdminToken = fx.adminToken;
        cfoOneToken = tokenOf(newCfo("one"), "CFO");
        cfoTwoToken = tokenOf(newCfo("two"), "CFO");
        pool = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        // 若案例中途失敗，仍被擋著的對手交易在這裡被中斷並回滾，不在資料庫留下開著的交易
        pool.shutdownNow();
        createdIndexes.forEach(name -> jdbcTemplate.execute("DROP INDEX IF EXISTS " + name));
    }

    // ── transfer.reversed webhook × 逆轉發起（PAID 上的兩個寫入者）──────────────────

    @Test
    @DisplayName("對照（單獨的 webhook）：PAID 結算單的 transfer 被 Stripe 撤銷 → transfer REVERSED、結算單 FAILED")
    void sequential_webhookAlone_marksTransferReversedAndStatementFailed() {
        Paid paid = seedPaid();

        String webhook = deliverTransferReversed(paid.stripeTransferId);

        StatementState state = stateOf(paid.statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(webhook).as("webhook 處理結果").isEqualTo(WEBHOOK_OK);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("FAILED");
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("REVERSED");
        });
    }

    /**
     * 不含併發的對照：先發起逆轉、webhook 後到。webhook 讀到 REVERSAL_PENDING 就略過，不可把已進入雙重授權流程的結算單蓋回
     * FAILED（{@code TransferService} 的註解承諾的行為）；Stripe 的事實（transfer 被撤銷）照記。它一失敗，下面的併發案例
     * 紅燈就不能歸因於競態。
     */
    @Test
    @DisplayName("對照（不含併發）：先發起逆轉、webhook 後到 → 結算單維持 REVERSAL_PENDING 且發起人／原因都在；transfer 仍記為 REVERSED")
    void sequential_reversalInitiatedBeforeWebhook_webhookDoesNotOverrideIt() throws Exception {
        Paid paid = seedPaid();

        MockHttpServletResponse initiated = initiate(paid.statementId, superAdminToken);
        String webhook = deliverTransferReversed(paid.stripeTransferId);

        StatementState state = stateOf(paid.statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(initiated.getStatus()).as("發起逆轉：%s", safeBody(initiated)).isEqualTo(200);
            softly.assertThat(webhook).as("webhook 處理結果").isEqualTo(WEBHOOK_OK);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("REVERSAL_PENDING");
            softly.assertThat(state.initiatedByRole).as("發起人角色：%s", state).isEqualTo("SUPER_ADMIN");
            softly.assertThat(state.reason).as("逆轉原因：%s", state).isEqualTo(REASON);
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("REVERSED");
        });
    }

    /**
     * 發起先拿到鎖、webhook 後到（但 webhook 已經讀到 PAID）：發起的搶占先提交（PAID → REVERSAL_PENDING 並寫入發起人、角色、
     * 時間、原因）。webhook 之後不可以把這一切蓋掉——它應該發現結算單已不是 PAID 而略過，transfer 照記 REVERSED。
     */
    @Test
    @DisplayName("發起逆轉先寫、webhook 後寫：逆轉請求不可被 webhook 蓋回 FAILED（狀態、發起人角色、原因都要保留）；transfer 仍是 REVERSED")
    void initiateWinsThenWebhook_webhookDoesNotOverwriteTheReversalRequest() throws Exception {
        Paid paid = seedPaid();

        Outcome<MockHttpServletResponse, String> outcome = race(paid.statementId,
                () -> initiate(paid.statementId, superAdminToken),
                () -> deliverTransferReversed(paid.stripeTransferId));

        StatementState state = stateOf(paid.statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(outcome.first.getStatus()).as("發起逆轉：%s", safeBody(outcome.first)).isEqualTo(200);
            softly.assertThat(outcome.second).as("webhook 處理結果").isEqualTo(WEBHOOK_OK);
            softly.assertThat(state.status).as("發起成功之後結算單必須維持 REVERSAL_PENDING：%s", state)
                    .isEqualTo("REVERSAL_PENDING");
            softly.assertThat(state.initiatedByRole).as("發起人角色不可被清掉：%s", state).isEqualTo("SUPER_ADMIN");
            softly.assertThat(state.reason).as("逆轉原因不可被清掉：%s", state).isEqualTo(REASON);
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("REVERSED");
        });
    }

    /** 方向相反：webhook 先提交 FAILED，發起的搶占（WHERE status='PAID'）要發現狀態已變，得到 409 E-5014。 */
    @Test
    @DisplayName("webhook 先寫、發起逆轉後到：發起得到 409 E-5014；結算單維持 webhook 寫的 FAILED，沒有殘留的逆轉欄位；transfer REVERSED")
    void webhookWinsThenInitiate_initiateGetsConflict() throws Exception {
        Paid paid = seedPaid();

        Outcome<String, MockHttpServletResponse> outcome = race(paid.statementId,
                () -> deliverTransferReversed(paid.stripeTransferId),
                () -> initiate(paid.statementId, superAdminToken));

        StatementState state = stateOf(paid.statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(outcome.first).as("webhook 處理結果").isEqualTo(WEBHOOK_OK);
            softly.assertThat(outcome.second.getStatus()).as("後到的發起逆轉：%s", safeBody(outcome.second)).isEqualTo(409);
            softly.assertThat(code(outcome.second)).as("後到的發起逆轉：%s", safeBody(outcome.second)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("FAILED");
            softly.assertThat(state.initiatedByRole).as("輸家的發起人角色不可寫入：%s", state).isNull();
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("REVERSED");
        });
    }

    // ── 逆轉確認（REVERSAL_PENDING 上的兩個確認）────────────────────────────────

    @Test
    @DisplayName("對照（不含併發）：循序確認兩次 → 第二次得到 409 E-5014；結算單 REVERSED、只有一張信用票據")
    void sequential_confirmTwice_secondIsRejectedByStatusCheck() throws Exception {
        UUID statementId = seedReversalPending();

        MockHttpServletResponse first = confirm(statementId, cfoOneToken);
        MockHttpServletResponse second = confirm(statementId, cfoTwoToken);

        StatementState state = stateOf(statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(first.getStatus()).as("第一次確認：%s", safeBody(first)).isEqualTo(200);
            softly.assertThat(responseStatus(first)).as("第一次確認的回應狀態：%s", safeBody(first)).isEqualTo("REVERSED");
            softly.assertThat(second.getStatus()).as("第二次確認：%s", safeBody(second)).isEqualTo(409);
            softly.assertThat(code(second)).as("第二次確認：%s", safeBody(second)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("REVERSED");
            softly.assertThat(state.creditNoteNumbers).as("信用票據：%s", state).hasSize(1);
        });
    }

    /**
     * 兩位 CFO 同時確認同一張逆轉。正式環境（Flyway V36）有 {@code UNIQUE (tenant_id, credit_note_number)}，而編號是
     * 「租戶前綴＋今天日期」（同一張結算單兩次必然相同），所以後到者的 INSERT 會撞約束——但那是<strong>偶然</strong>的保護，
     * 輸家得到的應該是乾淨的 409，不是資料庫錯誤。
     */
    @Test
    @DisplayName("確認 vs 確認（有 V36 同形唯一索引）：後到的得到 409 E-5014（不是 500 E-9900）；結算單 REVERSED、只有一張信用票據")
    void confirmVsConfirm_withUniqueNumberIndex_loserGetsCleanConflict() throws Exception {
        UUID statementId = seedReversalPending();
        createCreditNoteNumberIndex();

        Outcome<MockHttpServletResponse, MockHttpServletResponse> outcome = race(statementId,
                () -> confirm(statementId, cfoOneToken), () -> confirm(statementId, cfoTwoToken));

        assertOneConfirmationWins(outcome, stateOf(statementId));
    }

    /**
     * 同一個案例<strong>拿掉</strong>唯一索引：模擬「信用票據編號改成每張結算單唯一之後」（DEF-448 的修法方向），也就是
     * Hibernate 建的整合測試庫的預設。那時偶然的保護消失，只有結算單的狀態搶占能擋住「同一張逆轉被沖銷兩次」。
     */
    @Test
    @DisplayName("確認 vs 確認（沒有唯一索引，模擬編號改版後）：後到的仍得到 409 E-5014；結算單只被沖銷一次（一張信用票據）")
    void confirmVsConfirm_withoutNumberIndex_stillOnlyOneCreditNote() throws Exception {
        UUID statementId = seedReversalPending();

        Outcome<MockHttpServletResponse, MockHttpServletResponse> outcome = race(statementId,
                () -> confirm(statementId, cfoOneToken), () -> confirm(statementId, cfoTwoToken));

        assertOneConfirmationWins(outcome, stateOf(statementId));
    }

    private void assertOneConfirmationWins(final Outcome<MockHttpServletResponse, MockHttpServletResponse> outcome,
                                           final StatementState state) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(outcome.first.getStatus()).as("先到的確認：%s", safeBody(outcome.first)).isEqualTo(200);
            softly.assertThat(responseStatus(outcome.first)).as("先到的確認的回應狀態：%s", safeBody(outcome.first))
                    .isEqualTo("REVERSED");
            softly.assertThat(outcome.second.getStatus()).as("後到的確認要輸：%s", safeBody(outcome.second)).isEqualTo(409);
            softly.assertThat(code(outcome.second)).as("後到的確認要輸：%s", safeBody(outcome.second)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("REVERSED");
            softly.assertThat(state.creditNoteNumbers).as("同一張逆轉只能沖銷一次：%s", state).hasSize(1);
        });
    }

    // ── 信用票據編號：開立日＋當日流水號（DEF-448，Sprint 324）──────────────────────────
    //
    // 原本編號是『租戶前綴＋今天日期』（每租戶每天一個），同一租戶同一天的第二張逆轉確認會撞 V36 的
    // UNIQUE (tenant_id, credit_note_number) 回 500（Sprint 323 實測），要等隔天才確認得了。使用者以互動選項選了
    // 『開立日＋當日流水號』：CN-{租戶 id 前 8 碼}-{開立日}-01、-02…（做法同 SupportTicketService 的工單單號）。

    @Test
    @DisplayName("同租戶同一天的兩張結算單依序確認：都成功，信用票據編號是 CN-{租戶前 8 碼}-{開立日}-01 與 -02（有 V36 同形唯一索引）")
    void confirmTwoStatementsOfTheSameTenantOnTheSameDay_getConsecutiveNumbers() throws Exception {
        UUID first = seedReversalPending();
        UUID second = seedReversalPending();
        createCreditNoteNumberIndex();

        MockHttpServletResponse firstConfirmed = confirm(first, cfoOneToken);
        MockHttpServletResponse secondConfirmed = confirm(second, cfoTwoToken);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(firstConfirmed.getStatus()).as("第一張確認：%s", safeBody(firstConfirmed)).isEqualTo(200);
            softly.assertThat(secondConfirmed.getStatus()).as("第二張確認：%s", safeBody(secondConfirmed)).isEqualTo(200);
            softly.assertThat(creditNoteNumbersOfTenant()).as("當日信用票據編號")
                    .containsExactly(todayNumberPrefix() + "01", todayNumberPrefix() + "02");
            softly.assertThat(stateOf(first).status).as("第一張結算單").isEqualTo("REVERSED");
            softly.assertThat(stateOf(second).status).as("第二張結算單").isEqualTo("REVERSED");
        });
    }

    /**
     * 兩張不同結算單同時確認、算出同一個流水號——模擬方式：另一個交易已插入（尚未提交）同租戶當日的 {@code …-01}。
     * 確認請求算出的也是 {@code …-01}（對手尚未提交，count 看不到它），它的 INSERT 卡在對手尚未提交的唯一索引項目上；
     * 對手提交後撞唯一約束，整個交易（含狀態搶占）回滾，在新交易重做：這次 count 看得到對手的票據，編號是 {@code …-02}。
     */
    @Test
    @DisplayName("確認時流水號被別張結算單搶先用掉（唯一索引衝突）：整個交易重做，改用下一個流水號 -02，仍回 200（不是 500）")
    void confirmLosesTheDailyNumberToAnotherStatement_retriesWithTheNextNumber() throws Exception {
        UUID otherStatement = seedStatement("REVERSED");
        UUID statementId = seedReversalPending();
        createCreditNoteNumberIndex();
        String takenNumber = todayNumberPrefix() + "01";

        HeldTransaction opponent = holdTransaction(jdbc -> insertCreditNote(jdbc, otherStatement, takenNumber));
        MockHttpServletResponse confirmed;
        try {
            Future<MockHttpServletResponse> request = pool.submit(() -> confirm(statementId, cfoOneToken));
            awaitRequestsBlocked(1);
            Assertions.assertThat(request.isDone()).as("確認請求應該卡在對手尚未提交的信用票據編號上").isFalse();
            opponent.commit();
            confirmed = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            opponent.release();
        }

        StatementState state = stateOf(statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(confirmed.getStatus()).as("輸給對手的確認：%s", safeBody(confirmed)).isEqualTo(200);
            softly.assertThat(responseStatus(confirmed)).as("回應狀態：%s", safeBody(confirmed)).isEqualTo("REVERSED");
            softly.assertThat(creditNoteNumbersOfTenant()).as("當日信用票據編號")
                    .containsExactly(takenNumber, todayNumberPrefix() + "02");
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("REVERSED");
            softly.assertThat(state.creditNoteNumbers).as("這張結算單自己的信用票據：%s", state)
                    .containsExactly(todayNumberPrefix() + "02");
        });
    }

    /** 流水號是『當日』的：同租戶以前日期開立的票據不算進今天的流水號，今天的第一張仍是 -01。 */
    @Test
    @DisplayName("流水號每天重新從 01 開始：同租戶舊日期的信用票據不算進今天的流水號")
    void dailyNumberRestartsAtOneEachDay() throws Exception {
        UUID olderStatement = seedStatement("REVERSED");
        insertCreditNote(jdbcTemplate, olderStatement, tenantNumberPrefix() + "20200101-05");
        UUID statementId = seedReversalPending();
        createCreditNoteNumberIndex();

        MockHttpServletResponse confirmed = confirm(statementId, cfoOneToken);

        StatementState state = stateOf(statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(confirmed.getStatus()).as("確認：%s", safeBody(confirmed)).isEqualTo(200);
            softly.assertThat(state.creditNoteNumbers).as("今天的第一張信用票據：%s", state)
                    .containsExactly(todayNumberPrefix() + "01");
        });
    }

    // ── 重試撥款（FAILED 上的兩個重試）──────────────────────────────────────────

    @Test
    @DisplayName("對照（不含併發）：循序重試兩次 → 第一次撥款成功（PAID、transfer COMPLETED、閘道 1 次），第二次得到 409 E-5014")
    void sequential_retryTwice_secondIsRejected() throws Exception {
        UUID statementId = seedFailedStatementWithFailedTransfer();
        String connectAccountId = makeStorePayoutReadyWithSuccessfulGateway();

        MockHttpServletResponse first = retry(statementId, superAdminToken);
        MockHttpServletResponse second = retry(statementId, superAdminToken);

        StatementState state = stateOf(statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(first.getStatus()).as("第一次重試：%s", safeBody(first)).isEqualTo(200);
            softly.assertThat(second.getStatus()).as("第二次重試：%s", safeBody(second)).isEqualTo(409);
            softly.assertThat(code(second)).as("第二次重試：%s", safeBody(second)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("PAID");
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("COMPLETED");
        });
        verify(paymentGatewayFactory, times(1)).createTransfer(eq("STRIPE"), eq(connectAccountId), anyLong(), any(), any());
    }

    /**
     * 兩位管理員同時重試同一筆 FAILED 的撥款。撥款一定只能發生一次（錢的不變量）；輸家應該得到與循序的第二次重試相同的結果
     * （409 E-5014），不是 500。
     */
    @Test
    @DisplayName("重試 vs 重試：後到的得到 409 E-5014；閘道只被呼叫 1 次；結算單 PAID、只有一筆 COMPLETED 的 transfer")
    void retryVsRetry_loserGetsCleanConflictAndPayoutHappensOnce() throws Exception {
        UUID statementId = seedFailedStatementWithFailedTransfer();
        String connectAccountId = makeStorePayoutReadyWithSuccessfulGateway();

        Outcome<MockHttpServletResponse, MockHttpServletResponse> outcome = race(statementId,
                () -> retry(statementId, superAdminToken), () -> retry(statementId, superAdminToken));

        StatementState state = stateOf(statementId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(outcome.first.getStatus()).as("先到的重試：%s", safeBody(outcome.first)).isEqualTo(200);
            softly.assertThat(outcome.second.getStatus()).as("後到的重試要輸：%s", safeBody(outcome.second)).isEqualTo(409);
            softly.assertThat(code(outcome.second)).as("後到的重試要輸：%s", safeBody(outcome.second)).isEqualTo(CONFLICT_CODE);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("PAID");
            softly.assertThat(state.transferStatuses).as("transfer：%s", state).containsExactly("COMPLETED");
        });
        verify(paymentGatewayFactory, times(1)).createTransfer(eq("STRIPE"), eq(connectAccountId), anyLong(), any(), any());
    }

    // ── 輔助：競態編排 ─────────────────────────────────────────────

    private record Outcome<A, B>(A first, B second) { }

    /**
     * 讓兩個請求都先讀、再都卡在寫入結算單（或撞上對方未提交的唯一索引項目）上，然後放行：{@code first} 先到（先被觀察到
     * 卡住），所以先寫；{@code second} 後到、後寫。
     */
    private <A, B> Outcome<A, B> race(final UUID statementId, final Callable<A> first, final Callable<B> second)
            throws Exception {
        HeldTransaction lock = holdTransaction(jdbc -> jdbc.queryForList(
                "SELECT id FROM settlement_statements WHERE id = ? FOR UPDATE", statementId));
        try {
            Future<A> firstRequest = pool.submit(first);
            awaitRequestsBlocked(1);
            Future<B> secondRequest = pool.submit(second);
            awaitRequestsBlocked(2);
            Assertions.assertThat(firstRequest.isDone() || secondRequest.isDone())
                    .as("兩個請求都應該卡在鎖上（對手交易持有的結算單那一列，或先到者尚未提交的唯一索引項目）").isFalse();
            lock.commit();
            return new Outcome<>(firstRequest.get(AWAIT_SECONDS, TimeUnit.SECONDS),
                    secondRequest.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            lock.release();
        }
    }

    /**
     * 至少 {@code expected} 條連線正在等 {@code Lock}，而且正在執行的語句碰的是結算單、信用票據或 transfer：寫結算單那一列的
     * {@code UPDATE}、撞上先到者尚未提交的唯一索引項目的 {@code INSERT INTO credit_notes}／{@code INSERT INTO transfers}、
     * 刪除舊 transfer 的 {@code DELETE FROM transfers}。用它取代睡眠——睡多久都不能證明請求真的已經讀過、並且卡在寫入上。
     * 第一個等待者顯示 {@code transactionid}、後面的顯示 {@code tuple}，所以不限定事件名稱。
     */
    private void awaitRequestsBlocked(final int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        Integer waiting = 0;
        while (System.nanoTime() < deadline) {
            waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                            + "AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query ~* ?",
                    Integer.class, "settlement_statement|credit_notes|transfers");
            if (waiting != null && waiting >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等不到 " + expected + " 個請求卡在鎖上（目前 " + waiting + " 個）");
    }

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

    // ── 輔助：資料 ────────────────────────────────────────────────

    private record Paid(UUID statementId, String stripeTransferId) { }

    private User newCfo(final String label) {
        return userRepository.save(User.builder()
                .email("post-race-cfo-" + label + "-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy").fullName("cfo " + label).role(User.UserRole.CFO).status("ACTIVE").build());
    }

    private String tokenOf(final User user, final String role) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), role,
                ContractFixture.SYSTEM_TENANT_ID.toString());
    }

    /**
     * 一張指定狀態的結算單（GMV 1000、抽成 100、淨額 900），掛在 {@code fx.store} 上；同時建一個只涵蓋它的
     * {@code transfers.settlement_statement_id} 同形唯一索引（正式環境的 V64；測試庫沒有它，兩個撥款會各插入一筆 transfer）。
     */
    private UUID seedStatement(final String status) {
        UUID statementId = UUID.randomUUID();
        String indexName = "idx_it_post_race_transfers_" + statementId.toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON transfers (settlement_statement_id) WHERE settlement_statement_id = '" + statementId + "'");
        createdIndexes.add(indexName);
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-21', DATE '2026-12-27', 1, 1000.00, 0.00, 100.00, 900.00,
                        'TWD', ?, NOW(), NOW(), NOW())
                """, statementId, fx.store.getId(), "IT-S323-" + System.nanoTime(), status);
        return statementId;
    }

    private void seedTransfer(final UUID statementId, final String status, final String stripeTransferId) {
        jdbcTemplate.update("INSERT INTO transfers (id, settlement_statement_id, tenant_id, stripe_transfer_id, "
                + "transfer_amount, currency, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 900.00, 'TWD', ?, NOW(), NOW())",
                UUID.randomUUID(), statementId, fx.store.getId(), stripeTransferId, status);
    }

    /** 一張已撥款的結算單（PAID）與它那筆 COMPLETED、帶 Stripe transfer id 的 transfer。 */
    private Paid seedPaid() {
        UUID statementId = seedStatement("PAID");
        jdbcTemplate.update("UPDATE settlement_statements SET approved_at = NOW(), reviewed_at = NOW(), "
                + "paid_at = NOW() WHERE id = ?", statementId);
        String stripeTransferId = "tr_it_post_race_" + System.nanoTime();
        seedTransfer(statementId, "COMPLETED", stripeTransferId);
        return new Paid(statementId, stripeTransferId);
    }

    /** 已由 SUPER_ADMIN 發起、等另一種角色（CFO）確認的逆轉。 */
    private UUID seedReversalPending() {
        UUID statementId = seedStatement("REVERSAL_PENDING");
        jdbcTemplate.update("UPDATE settlement_statements SET approved_at = NOW(), reviewed_at = NOW(), paid_at = NOW(), "
                + "reversal_initiated_by = ?, reversal_initiated_by_role = 'SUPER_ADMIN', "
                + "reversal_requested_at = NOW(), reversal_reason = ? WHERE id = ?",
                superAdmin.getId(), REASON, statementId);
        seedTransfer(statementId, "COMPLETED", "tr_it_post_race_" + System.nanoTime());
        return statementId;
    }

    /** 撥款失敗的結算單：FAILED，加上它那筆 FAILED（沒有 Stripe id）的 transfer——重試端點要刪掉它再重來。 */
    private UUID seedFailedStatementWithFailedTransfer() {
        UUID statementId = seedStatement("FAILED");
        jdbcTemplate.update("UPDATE settlement_statements SET approved_at = NOW(), reviewed_at = NOW() WHERE id = ?",
                statementId);
        seedTransfer(statementId, "FAILED", null);
        return statementId;
    }

    /** 正式環境（Flyway V36）的 {@code UNIQUE (tenant_id, credit_note_number)}，只涵蓋本案例的店鋪。 */
    private void createCreditNoteNumberIndex() {
        String indexName = "idx_it_post_race_credit_notes_" + fx.store.getId().toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON credit_notes (tenant_id, credit_note_number) WHERE tenant_id = '" + fx.store.getId() + "'");
        createdIndexes.add(indexName);
    }

    /** 信用票據編號的租戶前綴：{@code CN-{租戶 id 前 8 碼}-}。 */
    private String tenantNumberPrefix() {
        return "CN-" + fx.store.getId().toString().substring(0, 8) + "-";
    }

    /** 今天開立的信用票據編號前綴：{@code CN-{租戶 id 前 8 碼}-{yyyyMMdd}-}（後面接當日流水號）。 */
    private String todayNumberPrefix() {
        return tenantNumberPrefix() + LocalDate.now().toString().replace("-", "") + "-";
    }

    /** 本案例店鋪（租戶）名下所有信用票據編號，依編號排序。 */
    private List<String> creditNoteNumbersOfTenant() {
        return jdbcTemplate.queryForList(
                "SELECT credit_note_number FROM credit_notes WHERE tenant_id = ? ORDER BY credit_note_number",
                String.class, fx.store.getId());
    }

    /** 直接插入一張信用票據（對手交易或舊日期的既有票據用）；{@code issued_by}、{@code issued_at} 可為空。 */
    private void insertCreditNote(final JdbcTemplate jdbc, final UUID originalStatementId, final String number) {
        jdbc.update("INSERT INTO credit_notes (id, tenant_id, original_statement_id, credit_note_number, type, amount, "
                + "status) VALUES (?, ?, ?, ?, 'CREDIT_NOTE', -900.00, 'PENDING')",
                UUID.randomUUID(), fx.store.getId(), originalStatementId, number);
    }

    /** 讓店鋪成為可撥款的（Connect 帳戶就緒＋撥款開關開啟），並讓閘道回報撥款成功。回傳 Connect 帳戶 id。 */
    private String makeStorePayoutReadyWithSuccessfulGateway() {
        String accountId = "acct_it_post_race_" + System.nanoTime();
        jdbcTemplate.update("UPDATE tenants SET stripe_connect_account_id = ?, connect_onboarding_status = 'COMPLETE', "
                + "connect_charges_enabled = TRUE, connect_payouts_enabled = TRUE WHERE id = ?",
                accountId, fx.store.getId());
        jdbcTemplate.update("INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, created_at, "
                + "updated_at) VALUES (?, ?, 'STRIPE_TRANSFER_ENABLED', TRUE, NOW(), NOW())",
                UUID.randomUUID(), fx.store.getId());
        when(paymentGatewayFactory.createTransfer(eq("STRIPE"), eq(accountId), anyLong(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_it_post_race_new").build());
        return accountId;
    }

    /** 金額一律轉成兩位小數的字串，失敗訊息讀起來直接。 */
    private static String money(final Object amount) {
        return amount == null ? "<null>" : new BigDecimal(amount.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record StatementState(String status, UUID initiatedBy, String initiatedByRole, Object requestedAt,
                                  String reason, String netAmount, List<String> transferStatuses,
                                  List<String> creditNoteNumbers) { }

    private StatementState stateOf(final UUID statementId) {
        var row = jdbcTemplate.queryForMap(
                "SELECT status, reversal_initiated_by, reversal_initiated_by_role, reversal_requested_at, "
                        + "reversal_reason, net_settlement_amount FROM settlement_statements WHERE id = ?", statementId);
        List<String> transfers = jdbcTemplate.queryForList(
                "SELECT status FROM transfers WHERE settlement_statement_id = ? ORDER BY created_at",
                String.class, statementId);
        List<String> creditNotes = jdbcTemplate.queryForList(
                "SELECT credit_note_number FROM credit_notes WHERE original_statement_id = ? ORDER BY issued_at",
                String.class, statementId);
        return new StatementState((String) row.get("status"), (UUID) row.get("reversal_initiated_by"),
                (String) row.get("reversal_initiated_by_role"), row.get("reversal_requested_at"),
                (String) row.get("reversal_reason"), money(row.get("net_settlement_amount")), transfers, creditNotes);
    }

    // ── 輔助：入口 ────────────────────────────────────────────────

    /**
     * Stripe 的 {@code transfer.reversed} webhook：走真實的入口 {@code PaymentWebhookService.handleEvent}（payload 於
     * controller 驗簽之後才進來，這裡直接餵 JSON；含事件 id 去重與外層交易）。回傳 {@code "ok"} 或例外描述，讓競態的
     * 結果可以和 HTTP 回應一起被斷言。
     */
    private String deliverTransferReversed(final String stripeTransferId) {
        String payload = """
                {"id":"evt_it_post_race_%s","type":"transfer.reversed","data":{"object":{"id":"%s"}}}
                """.formatted(UUID.randomUUID(), stripeTransferId);
        try {
            paymentWebhookService.handleEvent(payload);
            return WEBHOOK_OK;
        } catch (RuntimeException e) {
            return "EXCEPTION " + e;
        }
    }

    private MockHttpServletResponse initiate(final UUID statementId, final String token) throws Exception {
        return send(post("/v2/admin/settlements/{id}/reverse/initiate", statementId).param("reason", REASON), token);
    }

    private MockHttpServletResponse confirm(final UUID statementId, final String token) throws Exception {
        return send(post("/v2/admin/settlements/{id}/reverse/confirm", statementId), token);
    }

    private MockHttpServletResponse retry(final UUID statementId, final String token) throws Exception {
        return send(post("/v2/admin/transfers/{id}/retry", statementId), token);
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

    /** 回應 {@code data.status}；欄位不存在或回應不是 JSON 時回標記字串，讓斷言訊息自己說明。 */
    private String responseStatus(final MockHttpServletResponse response) {
        try {
            JsonNode node = objectMapper.readTree(safeBody(response)).path("data").path("status");
            return node.isMissingNode() || node.isNull() ? "<missing>" : node.asText();
        } catch (java.io.IOException e) {
            return "<not json>";
        }
    }
}
