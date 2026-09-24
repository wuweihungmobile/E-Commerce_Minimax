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
}
