package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * 結算併發認領的真實資料庫測試（DEF-273；真實 PostgreSQL，多執行緒）。
 *
 * <p>「每筆訂單恰好結算一次」的核心保證是<b>併發下</b>的原子認領：兩個節點同時跑排程（或排程與人工重跑，
 * 且期間不同、不會被「同期間冪等檢查」擋下）時，都可能在對方認領之前讀到同一批未結算訂單。
 * 認領是 {@code UPDATE ... WHERE settled_statement_id IS NULL}：READ COMMITTED 下後到者會等待列鎖，
 * 待先到者提交後重新檢查條件、更新 0 列；認領筆數不足則回滾整張結算單。
 *
 * <p>本測試不假設誰贏（時序不可控），只斷言<b>與時序無關的不變量</b>：所有訂單都被結算、
 * 每筆恰好掛在一張結算單上、各結算單的 {@code total_orders} 加總恰等於訂單數（不重複計算）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-RACE: 結算併發認領——每筆訂單恰好結算一次（DEF-273）")
class SettlementConcurrentClaimIntegrationTest {

    private static final int ORDERS = 40;
    private static final int THREADS = 8;

    @Autowired private SettlementGenerator settlementGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;

    @BeforeEach
    void seed() {
        String stamp = String.valueOf(System.nanoTime());
        tenantId = tenantRepository.save(Tenant.builder()
                .name("Settle Race Tenant")
                .slug("settle-race-" + stamp)
                .contactEmail("settle-race-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
        UUID buyerId = userRepository.save(User.builder()
                .email("settle-race-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Settle Race Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();
        OffsetDateTime createdAt = OffsetDateTime.parse("2027-01-05T12:00:00+08:00");
        for (int i = 0; i < ORDERS; i++) {
            jdbcTemplate.update("""
                    INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                        shipping_fee, discount_amount, currency, created_at, updated_at)
                    VALUES (?, ?, ?, 'PRODUCT', 'COMPLETED', 100.00, 0.00, 0.00, 'TWD', ?, ?)
                    """, UUID.randomUUID(), tenantId, buyerId, createdAt, createdAt);
        }
    }

    @Test
    @DisplayName("8 個結算同時搶同一批 40 筆訂單（各用不同期間，不會被同期間冪等檢查擋下）→ 每筆訂單恰好被結算一次")
    void concurrentGenerations_settleEveryOrderExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        Map<String, Integer> failures = new ConcurrentHashMap<>();
        List<Future<?>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                LocalDate start = LocalDate.of(2027, 1, 11).plusWeeks(i);
                LocalDate end = start.plusDays(6);
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        settlementGenerator.generateStatementForTenant(tenantId, start, end);
                    } catch (RuntimeException e) {
                        failures.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    }
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<?> f : results) {
                f.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(failures.keySet())
                .as("輸掉競態的結算只能以「認領不足」的 IllegalStateException 回滾，不可出現其他例外。本次結果：%s", failures)
                .isSubsetOf("IllegalStateException");

        Integer unsettled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE tenant_id = ? AND settled_statement_id IS NULL",
                Integer.class, tenantId);
        assertThat(unsettled).as("所有訂單都必須被某一張結算單結算（不可漏）").isZero();

        Integer settledTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_orders), 0) FROM settlement_statements WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(settledTotal)
                .as("各結算單 total_orders 加總必須恰等於訂單數：大於代表同一筆訂單被兩張結算單各結算一次（重複撥款）")
                .isEqualTo(ORDERS);

        BigDecimal gmv = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_gmv), 0) FROM settlement_statements WHERE tenant_id = ?",
                BigDecimal.class, tenantId);
        assertThat(gmv).as("GMV 加總同理，不可重複計算").isEqualByComparingTo(BigDecimal.valueOf(ORDERS * 100L));
    }

    /**
     * 補上與上面測試互補的既有覆蓋缺口（Sprint 212 自選掃描角度）——那個測試的 Javadoc 自承
     * 「刻意讓每條執行緒用不同期間，不會被同期間冪等檢查擋下」，只壓 {@link #generateStatementForTenant}
     * 的原子認領（{@code markSettled}），從未真正驗證<b>同一個期間</b>被多條執行緒同時搶時，開頭那段
     * 「查有沒有既有結算單、沒有就建立」的 check-then-act（{@code existingStatements.isEmpty()} 與
     * 寫入之間沒有原子保護）是否安全。{@link SettlementGeneratorManualTriggerTest} 的 Javadoc 也逕自
     * 宣稱這段「已有測試覆蓋」，但兩份既有測試檔都是 mock，從未在真實 DB 下驗證過。
     *
     * <p><b>查證結果：這條路徑其實安全</b>，但保護它的不是這段冪等檢查本身，而是 DEF-273 既有的
     * 「原子認領訂單，認領數不足就整張回滾」機制順帶接住了它：即使人為在冪等檢查後插入 200ms 延遲
     * 拉寬 race window（曾用來診斷、已還原），落後的執行緒也只會在 {@code markSettled} 認領到 0 筆
     * 訂單時拋出 {@code IllegalStateException} 並整筆回滾（連同它自己剛 {@code save()} 的重複結算單
     * 一併撤銷），從未真正走到 {@code settlement_statements(tenant_id, statement_number)} 的 UNIQUE
     * 約束衝突。保留本測試作為此不變量的永久回歸守門，避免未來重構誤觸。
     */
    @Test
    @DisplayName("8 個結算同時搶同一個期間 → 只產生一張結算單，落後者只能收到 IllegalStateException")
    void concurrentGenerations_sameTenantAndPeriod_producesExactlyOneStatement() throws Exception {
        LocalDate start = LocalDate.of(2027, 1, 11);
        LocalDate end = start.plusDays(6);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        Map<String, Integer> failures = new ConcurrentHashMap<>();
        List<Future<?>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        settlementGenerator.generateStatementForTenant(tenantId, start, end);
                    } catch (RuntimeException e) {
                        failures.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    }
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<?> f : results) {
                f.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(failures.keySet())
                .as("同期間競態的落後者只能以「認領不足」的 IllegalStateException 回滾，不可讓底層技術性例外"
                        + "（如唯一鍵衝突）未經轉譯就直接冒出。本次結果：%s", failures)
                .isSubsetOf("IllegalStateException");

        Integer statementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_statements WHERE tenant_id = ? AND period_start = ?",
                Integer.class, tenantId, start);
        assertThat(statementCount).as("同一租戶＋期間，無論幾條執行緒同時搶，最終只能有一張結算單").isEqualTo(1);

        Integer settledTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_orders), 0) FROM settlement_statements WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(settledTotal).as("那張唯一的結算單必須把 40 筆訂單全數結算，不可漏算或重複算")
                .isEqualTo(ORDERS);
    }
}
