package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.nextkey.ecommerce.core.settlement.SettlementGenerator;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 結算單生成的交易邊界與併發（DEF-450、DEF-451，Sprint 326；真實 PostgreSQL）。
 *
 * <p>共同背景：PostgreSQL 在交易內任何一個語句失敗後，整個交易進入中止狀態（見 {@code UniqueConflictRetry}）。
 * 週結算作業原本把<b>所有租戶</b>包在同一個交易裡（{@code generateStatementForTenant} 是同類別的自我呼叫，
 * 方法上的 {@code @Transactional} 不生效），所以：
 * <ul>
 *   <li>某個租戶發生資料庫層級的失敗，整個交易被標成 rollback-only，<b>所有租戶</b>的結算單（包含失敗者之前已處理好的）
 *       一起丟掉，作業以 {@code UnexpectedRollbackException} 結束；</li>
 *   <li>「認領筆數不足」的 {@code IllegalStateException} 被逐租戶的 {@code try/catch} 吞掉後，外層交易照常提交——
 *       程式碼註解承諾的『回滾整張結算單』並不成立，結算單與已認領的那幾筆訂單留在資料庫。</li>
 * </ul>
 * 手動產生端點則是『已有就回傳既有』的檢查和 INSERT 之間沒有鎖，同一期的並行請求輸家得 {@code 500}，但文件承諾冪等。
 *
 * <p>真實競態不靠 mock：一個「對手」交易先寫入但<b>不提交</b>並停住，另一個執行緒的語句卡在它的鎖上
 * （用 {@code pg_stat_activity} 確認真的卡住了，不靠睡眠），放行對手之後才繼續。
 *
 * <p>整合測試資料庫是 Hibernate 建的，{@code (tenant_id, statement_number)} 唯一約束只有 Flyway {@code V35} 有，
 * 所以需要它的案例自建只涵蓋該案例租戶的同形索引，收尾移除。
 *
 * <p>週結算作業處理的是資料庫裡<b>所有</b> ACTIVE 租戶；本類別的租戶在案例結束時一律改成 SUSPENDED，
 * 之後的作業不會再碰到它們（也不會讓下一個案例被前一個案例的失敗租戶干擾）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-TX: 結算單生成的交易邊界與併發（DEF-450／DEF-451）")
class SettlementGenerationRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;
    private static final String GENERATE_STATEMENT_PATH = "/v2/admin/settlements/generate";
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 6);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private SettlementStatementRepository settlementRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SettlementGenerator settlementGenerator;

    private ExecutorService pool;
    private final List<String> createdIndexes = new ArrayList<>();
    private final List<UUID> probeTenants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        pool = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        // 若案例中途失敗，仍被擋著的對手交易在這裡被中斷並回滾，不在資料庫留下開著的交易
        pool.shutdownNow();
        createdIndexes.forEach(name -> jdbcTemplate.execute("DROP INDEX IF EXISTS " + name));
        probeTenants.forEach(id -> jdbcTemplate.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", id));
    }

    // ── 手動產生（generateStatementManually，DEF-450）────────────────────────────────────

    /**
     * 兩個「手動產生同一期結算單」的請求同時送出（例如管理員連點兩下）：兩個都通過『這一期是否已有結算單』的檢查，各自 INSERT，
     * 輸的那個撞上 {@code (tenant_id, statement_number)} 唯一約束（結算單號是決定性的：{@code STL-租戶前 8 碼-期間起日}）。
     * 這個端點的文件承諾是冪等，所以輸家要整個交易重做，看到贏家的結算單並回 {@code 200}，不是 {@code 500}。
     */
    @Test
    @DisplayName("結算單手動產生：同租戶同期間被對手搶先建立（唯一約束衝突）→ 200 且回傳贏家的那張結算單，不是 500；只剩一張")
    void generateStatementManually_losingTheSamePeriodRace_returnsTheWinnersStatement() throws Exception {
        ContractFixture fx = new ContractFixture(
                tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        probeTenants.add(fx.store.getId());
        String tenant = fx.store.getId().toString();
        // 遙遠未來的一個週一：不會跟其他測試的結算期間重疊
        String statementNumber = "STL-" + tenant.substring(0, 8) + "-20300107";
        createPartialUniqueIndex(fx.store.getId());
        UUID winners = UUID.randomUUID();

        // 對手：同一期的結算單已經寫入但還沒提交
        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2030-01-07', DATE '2030-01-13', 0, 0.00, 0.00, 0.00, 0.00,
                        'TWD', 'PENDING', NOW(), NOW(), NOW())
                """, winners, fx.store.getId(), statementNumber));
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(() -> send(
                    post(GENERATE_STATEMENT_PATH).param("tenantId", tenant).param("periodStart", "2030-01-07"),
                    fx.adminToken));
            awaitBlockedOn("insert into settlement_statements");
            Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
            rival.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            rival.release();
        }

        Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(200);
        Assertions.assertThat(json(loser).path("data").path("id").asText())
                .as("輸家應該拿到贏家的那張結算單：%s", body(loser)).isEqualTo(winners.toString());
        Assertions.assertThat(json(loser).path("data").path("statementNumber").asText()).isEqualTo(statementNumber);
        Integer statements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM settlement_statements WHERE tenant_id = ?", Integer.class, fx.store.getId());
        Assertions.assertThat(statements).as("這家店的結算單張數").isEqualTo(1);
    }

    // ── 週結算作業（generateWeeklyStatements，DEF-451）────────────────────────────────────

    /**
     * 三個租戶，中間那個（F）的結算單號被一列『期間不同』的結算單佔用：F 的 INSERT 撞唯一約束。這個資料庫層級的失敗只能影響 F 自己——
     * 失敗者之前（H1）與之後（H2）的租戶都要有結算單、訂單都要被認領，作業本身不能以例外結束。
     * 不假設誰先誰後：原本只要 F 失敗，無論排在哪裡，全部租戶的結算單一起被丟掉。
     */
    @Test
    @DisplayName("週結算：某租戶發生資料庫層級失敗（唯一約束）→ 只有它自己沒有結算單，其他租戶不受影響，作業不以例外結束")
    void weeklyJob_oneTenantFailingAtTheDatabase_doesNotTakeTheOtherTenantsWithIt() {
        UUID h1 = newTenant("h1");
        UUID f = newTenant("f");
        UUID h2 = newTenant("h2");
        newOrders(h1, 2);
        newOrders(f, 2);
        newOrders(h2, 2);
        createPartialUniqueIndex(f);
        // F 的結算單號被一列期間不同的結算單佔用（冪等檢查看不到它，INSERT 卻撞號）
        oldStatement(f, statementNumber(f, lastMonday()), lastMonday().minusYears(3));

        Throwable thrown = null;
        try {
            settlementGenerator.generateWeeklyStatements();
        } catch (RuntimeException e) {
            thrown = e;
        }

        Assertions.assertThat(thrown).as("單一租戶的失敗不應讓整個作業以例外結束").isNull();
        for (UUID healthy : List.of(h1, h2)) {
            Assertions.assertThat(thisWeeksStatements(healthy)).as("健康租戶 %s 的本期結算單", healthy).hasSize(1);
            Assertions.assertThat(settledOrders(healthy)).as("健康租戶 %s 被認領的訂單", healthy).isEqualTo(2);
        }
        Assertions.assertThat(thisWeeksStatements(f)).as("失敗的租戶沒有本期結算單").isEmpty();
        Assertions.assertThat(settledOrders(f)).as("失敗的租戶沒有訂單被認領（它的交易整個回滾）").isZero();
    }

    /**
     * 排程讀到一批未結算訂單之後、認領之前，另一個結算（這裡用持有列鎖的對手交易代表：手動補產另一期、或另一個節點）搶先認領了其中一筆：
     * 認領筆數不足（1／2）。程式碼承諾『回滾整張結算單』——結算單、已認領的那一筆、調整單的套用都不能留下。
     * 下一次作業要能產出<b>正確</b>的結算單（只含實際認領到的訂單，並套用前期調整單）。
     */
    @Test
    @DisplayName("週結算：認領筆數不足（訂單被別的結算搶先認領）→ 整張結算單回滾、沒有半套寫入；下一次作業產出正確的結算單")
    void weeklyJob_claimShortfall_rollsBackTheWholeStatement_andTheNextRunSettlesCorrectly() throws Exception {
        UUID b = newTenant("b");
        List<UUID> orders = newOrders(b, 2);
        UUID rivalStatement = oldStatement(b, statementNumber(b, LONG_AGO), LONG_AGO);
        // 前期已產生、尚未套用的調整單
        UUID adjustment = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO adjustment_statements (id, tenant_id, original_statement_id, adjustment_type, "
                + "amount, status, created_at) VALUES (?, ?, ?, 'REFUND_DEDUCTION', -30.00, 'PENDING', NOW())",
                adjustment, b, rivalStatement);

        // 對手：把第 2 筆訂單改掛到別張結算單，但還沒提交（列鎖）。排程此時讀得到兩筆都是『未結算』
        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                "UPDATE orders SET settled_statement_id = ? WHERE id = ?", rivalStatement, orders.get(1)));
        Throwable thrown = null;
        try {
            Future<?> job = pool.submit(() -> settlementGenerator.generateWeeklyStatements());
            awaitBlockedOn("update orders");
            Assertions.assertThat(job.isDone()).as("排程的認領應該卡在對手的列鎖上").isFalse();
            rival.commit();
            try {
                job.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                thrown = e.getCause();
            }
        } finally {
            rival.release();
        }

        Assertions.assertThat(thrown).as("認領不足只影響這一個租戶，作業本身不以例外結束").isNull();
        Assertions.assertThat(thisWeeksStatements(b)).as("認領不足 → 整張結算單回滾，不留下半套的結算單").isEmpty();
        Assertions.assertThat(statementOf(orders.get(0))).as("第 1 筆訂單的認領隨結算單一起回滾").isNull();
        Assertions.assertThat(statementOf(orders.get(1))).as("第 2 筆仍歸對手的結算單").isEqualTo(rivalStatement);
        Assertions.assertThat(adjustmentStatus(adjustment)).as("調整單不能被當成已套用").isEqualTo("PENDING");

        // 下一次作業：對手已提交、沒有任何東西卡著 → 產出只含第 1 筆訂單的正確結算單，並套用調整單
        settlementGenerator.generateWeeklyStatements();

        List<SettlementStatement> statements = thisWeeksStatements(b);
        Assertions.assertThat(statements).as("下一次作業產出本期結算單").hasSize(1);
        SettlementStatement next = statements.get(0);
        Assertions.assertThat(next.getTotalOrders()).as("只含實際可認領的訂單").isEqualTo(1);
        Assertions.assertThat(next.getTotalGmv()).isEqualByComparingTo(new BigDecimal("100.00"));
        Assertions.assertThat(next.getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        Assertions.assertThat(statementOf(orders.get(0))).isEqualTo(next.getId());
        Assertions.assertThat(adjustmentStatus(adjustment)).as("調整單在這一張結算單被套用").isEqualTo("APPLIED");
    }

    // ── 固件與查詢 ─────────────────────────────────────────────

    private static LocalDate lastMonday() {
        return BusinessTime.today().with(DayOfWeek.MONDAY).minusWeeks(1);
    }

    private static String statementNumber(final UUID tenantId, final LocalDate periodStart) {
        return "STL-" + tenantId.toString().substring(0, 8) + "-" + periodStart.toString().replace("-", "");
    }

    /** 建立一個 ACTIVE 租戶；案例結束時一律改成 SUSPENDED，之後的週結算作業不會再處理它。 */
    private UUID newTenant(final String label) {
        String stamp = label + "-" + System.nanoTime();
        UUID id = tenantRepository.save(Tenant.builder()
                .name("Settle TX " + stamp)
                .slug("settle-tx-" + stamp)
                .contactEmail("settle-tx-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
        probeTenants.add(id);
        return id;
    }

    /** 上週（營運時區）內建立、已完成、每筆 100 元的訂單。 */
    private List<UUID> newOrders(final UUID tenantId, final int count) {
        String stamp = String.valueOf(System.nanoTime());
        UUID buyerId = userRepository.save(User.builder()
                .email("settle-tx-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Settle TX Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();
        OffsetDateTime createdAt = OffsetDateTime.of(lastMonday().plusDays(1), LocalTime.NOON, ZoneOffset.ofHours(8));
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                        shipping_fee, discount_amount, currency, created_at, updated_at)
                    VALUES (?, ?, ?, 'PRODUCT', 'COMPLETED', 100.00, 0.00, 0.00, 'TWD', ?, ?)
                    """, id, tenantId, buyerId, createdAt, createdAt);
            ids.add(id);
        }
        return ids;
    }

    private UUID oldStatement(final UUID tenantId, final String number, final LocalDate periodStart) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        return settlementRepository.save(SettlementStatement.builder()
                .tenant(tenant)
                .statementNumber(number)
                .periodStart(periodStart)
                .periodEnd(periodStart.plusDays(6))
                .totalGmv(BigDecimal.ZERO)
                .status(SettlementStatement.SettlementStatus.PENDING)
                .build()).getId();
    }

    /** 自建只涵蓋該租戶的 {@code (tenant_id, statement_number)} 唯一索引（Flyway V35 才有；收尾移除）。 */
    private void createPartialUniqueIndex(final UUID tenantId) {
        String indexName = "idx_it_settle_tx_" + tenantId.toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON settlement_statements (tenant_id, statement_number) WHERE tenant_id = '" + tenantId + "'");
        createdIndexes.add(indexName);
    }

    private List<SettlementStatement> thisWeeksStatements(final UUID tenantId) {
        return settlementRepository.findByTenantIdAndPeriodStartBetween(
                tenantId, lastMonday(), lastMonday().plusDays(6));
    }

    private int settledOrders(final UUID tenantId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE tenant_id = ? AND settled_statement_id IS NOT NULL",
                Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    private UUID statementOf(final UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT settled_statement_id FROM orders WHERE id = ?", UUID.class, orderId);
    }

    private String adjustmentStatus(final UUID adjustmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM adjustment_statements WHERE id = ?", String.class, adjustmentId);
    }

    // ── HTTP 輔助 ─────────────────────────────────────────────

    private MockHttpServletResponse send(final MockHttpServletRequestBuilder builder, final String token) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static String body(final MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode json(final MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(body(response));
    }

    // ── 持鎖的對手交易 ─────────────────────────────────────────────

    /**
     * 一個開著、尚未提交的交易：先寫入，然後停住，直到 {@link #commit()} 才提交。輸家的語句因此被它尚未提交的
     * 唯一索引項目（或列鎖）擋住。{@link #release()} 是收尾用的（已提交時無作用），確保案例中途失敗也不留下開著的交易。
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
     * 輸家的語句已經卡在「等對方交易提交」上：{@code pg_stat_activity} 裡有一條正在等 {@code transactionid} 鎖、
     * 而且正在執行含 {@code sqlFragment} 的語句。用它取代睡眠——睡多久都不能證明語句真的卡住了。
     */
    private void awaitBlockedOn(final String sqlFragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                            + "AND wait_event_type = 'Lock' AND wait_event = 'transactionid' AND query ILIKE ?",
                    Integer.class, "%" + sqlFragment + "%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等不到含「" + sqlFragment + "」的語句卡在另一個交易的鎖上");
    }
}
