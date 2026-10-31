package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-REVIEW-RACE: 批准／駁回結算單的併發要有且只有一個贏家（DEF-442）")
class SettlementReviewRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;
    private static final String CONFLICT_CODE = "E-5014";

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

    private record State(String status, UUID reviewedBy, String rejectionReason, List<String> transferStatuses,
                         boolean orderClaimedByStatement, long approvedAudits, long rejectedAudits) { }

    private State stateOf(final Seed seed) {
        var row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, rejection_reason FROM settlement_statements WHERE id = ?", seed.statementId);
        List<String> transfers = jdbcTemplate.queryForList(
                "SELECT status FROM transfers WHERE settlement_statement_id = ? ORDER BY created_at",
                String.class, seed.statementId);
        UUID claimedBy = jdbcTemplate.queryForObject(
                "SELECT settled_statement_id FROM orders WHERE id = ?", UUID.class, seed.orderId);
        return new State((String) row.get("status"), (UUID) row.get("reviewed_by"), (String) row.get("rejection_reason"),
                transfers, seed.statementId.equals(claimedBy), auditCount(seed, "SETTLEMENT_APPROVED"),
                auditCount(seed, "SETTLEMENT_REJECTED"));
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
