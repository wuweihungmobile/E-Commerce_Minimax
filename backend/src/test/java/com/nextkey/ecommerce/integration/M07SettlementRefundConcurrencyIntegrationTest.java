package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

/**
 * 結算單退款扣除的併發正確性整合測試（Sprint 105，DEF-053；真實 PostgreSQL）。
 *
 * <p>{@code SettlementAdjustmentService.applyDirectDeduction} 對同一列 {@code settlement_statements}
 * 做「讀出金額 → 在記憶體加減 → {@code save()}」。同一結算期間內**不同訂單**的退款會落在**同一列**
 * 結算單上，而 {@code SettlementStatement} 上沒有 {@code @Version}。
 *
 * <p>與 Sprint 103（DEF-050 庫存）的關鍵差異：{@code ProductInventory} 帶 {@code @Version}，
 * 因此那裡的失效模式是「大量樂觀鎖例外」——會拋、看得見。這裡**完全沒有樂觀鎖**，
 * 失效模式是**靜默丟失更新**：後寫入者以自己讀到的舊值覆蓋先寫入者的結果，
 * 沒有任何例外、沒有任何日誌，只有金額對不上。**這是本測試存在的理由**：
 * 沒有一個會失敗的測試，這種錯誤在生產環境只會表現為「帳差了一點」。
 *
 * <p>為什麼必須用真實 DB：既有的 {@code SettlementAdjustmentServiceTest} 6 個案例全部 mock 掉
 * {@code SettlementStatementRepository}，在單執行緒中依序回放 stub——讀後寫的窗口根本不存在。
 * 這正是 Sprint 97 記取、Sprint 103 再次印證的「所有相關測試都用固件繞過同一段邏輯」教訓。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M07-RACE: 結算單退款併發扣除（Sprint 105 / DEF-053）")
class M07SettlementRefundConcurrencyIntegrationTest {

    @Autowired private SettlementAdjustmentService settlementAdjustmentService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;

    /** 併發執行緒數＝同一結算期間內同時發生的退款筆數。 */
    private static final int THREADS = 10;

    /** 每筆退款金額。 */
    private static final BigDecimal REFUND_EACH = new BigDecimal("10.00");

    /** 結算單初始淨結算金額。 */
    private static final BigDecimal INITIAL_NET = new BigDecimal("1000.00");

    /** 訂單日期，落在結算單期間內。 */
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 3, 15);

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = seedTenant();
    }

    /**
     * 種一個租戶。{@code tenants} 有多個帶 NOT NULL 的旗標欄位，手寫 INSERT 得逐欄追 schema，
     * 交給 JPA 更穩（承 Sprint 103 M12 併發測試的同一判斷）。
     */
    private UUID seedTenant() {
        long stamp = System.nanoTime();
        return tenantRepository.save(Tenant.builder()
                .name("Settlement Race Tenant")
                .slug("stl-race-" + stamp)
                .contactEmail("stl-race-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
    }

    /**
     * 種一張涵蓋 {@link #ORDER_DATE} 的結算單。以 raw SQL 而非 JPA builder：
     * {@code SettlementStatement.tenantId} 是 {@code insertable = false} 的影子欄位，
     * 用 builder 設它會寫成 null 而撞 NOT NULL（既知陷阱，見 Sprint 103 記錄）。
     */
    private UUID seedStatement(final String status) {
        UUID statementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end,
                     total_orders, total_gmv, total_refunds, commission_amount,
                     net_settlement_amount, currency, status, generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-03-01', DATE '2026-03-31',
                        10, 1200.00, 0.00, 200.00,
                        ?, 'TWD', ?, NOW(), NOW(), NOW())
                """, statementId, tenantId, "STL-RACE-" + System.nanoTime(), INITIAL_NET, status);
        return statementId;
    }

    private BigDecimal totalRefundsOf(final UUID statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_refunds FROM settlement_statements WHERE id = ?", BigDecimal.class, statementId);
    }

    private BigDecimal netSettlementOf(final UUID statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT net_settlement_amount FROM settlement_statements WHERE id = ?",
                BigDecimal.class, statementId);
    }

    private long adjustmentCountOf(final UUID statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adjustment_statements WHERE original_statement_id = ?",
                Long.class, statementId);
    }

    /**
     * 讓 {@code THREADS} 條執行緒在同一瞬間、各自獨立交易內對同一張結算單送出退款。
     *
     * <p>每條執行緒用**不同的 orderId**——這正是真實情境：同一結算期間內多張訂單各自退款，
     * 全部落在同一列結算單上。回傳未預期例外的分類，避免把失敗模式吞掉。
     */
    private Map<String, Integer> race() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();
        List<Future<?>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        // 直接呼叫 @Transactional 的服務方法：每條執行緒各自開一筆交易，
                        // 讀後寫的窗口才會真的存在。共用交易會退化成單執行緒。
                        settlementAdjustmentService.handleOrderRefund(
                                tenantId, UUID.randomUUID(),
                                ORDER_DATE.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                                REFUND_EACH);
                    } catch (RuntimeException e) {
                        unexpected.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    }
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<?> f : results) {
                f.get(60, TimeUnit.SECONDS);
            }
            return unexpected;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("PENDING 結算單同時收到 10 筆退款 → 10 筆全部入帳，不因競態靜默漏計")
    void concurrentRefundsNeverLoseDeductions() throws Exception {
        UUID statementId = seedStatement("PENDING");
        BigDecimal expectedRefunds = REFUND_EACH.multiply(BigDecimal.valueOf(THREADS));

        Map<String, Integer> unexpected = race();

        assertThat(unexpected)
                .as("退款扣除不應拋出技術性例外；若出現樂觀鎖衝突之類的例外，代表修法選錯方向。本次結果：%s",
                        unexpected)
                .isEmpty();

        assertThat(totalRefundsOf(statementId))
                .as("累計退款必須等於 %s 筆 × %s。少於此數即為讀後寫競態造成的靜默漏計——"
                        + "帳面上賣家少扣了錢，且沒有任何例外或日誌會提示",
                        THREADS, REFUND_EACH)
                .isEqualByComparingTo(expectedRefunds);

        assertThat(netSettlementOf(statementId))
                .as("淨結算金額必須同步扣減；它與 total_refunds 是同一次操作的兩個面向，不可只對一半")
                .isEqualByComparingTo(INITIAL_NET.subtract(expectedRefunds));
    }

    @Test
    @DisplayName("APPROVED 結算單同時收到 10 筆退款 → 產生 10 張調整單，原結算單金額不動")
    void concurrentRefundsOnApprovedStatementCreateAllAdjustments() throws Exception {
        UUID statementId = seedStatement("APPROVED");

        Map<String, Integer> unexpected = race();

        assertThat(unexpected).as("本次結果：%s", unexpected).isEmpty();

        assertThat(adjustmentCountOf(statementId))
                .as("已核准的結算單不可直接改金額，每筆退款都必須留下一張調整單；"
                        + "少一張就是少扣一筆錢。此路徑為純 INSERT，本來就不受讀後寫競態影響——"
                        + "納入本測試是為了把「哪條路徑安全、哪條不安全」一併釘住")
                .isEqualTo(THREADS);

        assertThat(totalRefundsOf(statementId))
                .as("APPROVED 路徑不得改動原結算單金額")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
