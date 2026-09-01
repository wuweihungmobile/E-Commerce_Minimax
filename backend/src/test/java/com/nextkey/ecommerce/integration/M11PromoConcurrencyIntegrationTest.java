package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

/**
 * 優惠券額度佔用的併發正確性整合測試（Sprint 102，DEF-046；真實 PostgreSQL）。
 *
 * <p>為什麼一定要真實 DB：DEF-046 的本質是「檢查」與「遞增」分屬兩次資料庫往返所形成的
 * 讀後寫窗口。任何把 {@code PromoCodeRepository} mock 掉的測試，都是在單執行緒中依序
 * 回放 stub，窗口根本不存在——這正是 Sprint 97 記取的「所有相關測試都用固件繞過同一段
 * 邏輯」教訓。本測試以多執行緒、各自獨立交易，直接壓在同一列 {@code promo_codes} 上，
 * 由資料庫決定結果。
 *
 * <p>紅燈驗證（2026-09-01 實跑）：把 {@code PromoService.tryConsumeUsageQuota} 暫時換回
 * 修復前的「findById 檢查 → 加 1 → save」兩段式語意後，本類別 5 個案例中有 2 個立即失敗：
 * {@link #concurrentConsumeNeverExceedsLimit} 期望 3、實得 **10**（上限 3 的券被 10 條執行緒
 * 全數領走），{@link #unlimitedPromoGrantsEveryAttemptWithoutLostUpdate} 期望計數 10、
 * 實得 **1**（10 次遞增有 9 次被互相覆蓋）。換回條件式 UPDATE 後 5 個案例全綠。
 *
 * <p>Context 快取：本類別的 {@code @SpringBootTest}/{@code @AutoConfigureMockMvc}/
 * {@code @ActiveProfiles}/{@code @MockBean} 組合刻意與 {@code M11PromoCheckoutIntegrationTest}
 * 完全一致，使兩者共用同一個已快取的 Spring context——DEF-049 已量測出整合測試的耗時
 * 由 context 啟動主導，新增測試類別若順手改動這組註解，等於替 CI 再加一次冷啟動。
 * 本類別不使用 MockMvc，{@code @AutoConfigureMockMvc} 僅為對齊快取鍵而保留。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-M11-RACE: 優惠券額度併發佔用（Sprint 102 / DEF-046）")
class M11PromoConcurrencyIntegrationTest {

    @Autowired private PromoService promoService;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** 併發執行緒數；刻意大於券的總量上限，讓超發（若存在）必然顯現。 */
    private static final int THREADS = 10;

    /** 券的總量上限。 */
    private static final int MAX_USAGE = 3;

    private TransactionTemplate txTemplate;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        txTemplate = new TransactionTemplate(transactionManager);

        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Promo Race Tenant")
                .slug("promo-race-tenant-" + stamp)
                .contactEmail("promo-race@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
    }

    /** 真實寫入一張券；tenant 以關聯物件設定（{@code tenantId} 是 insertable=false 的影子欄位）。 */
    private PromoCode givenPromo(final Integer maxUsageCount, final int currentUsageCount) {
        return promoCodeRepository.save(PromoCode.builder()
                .tenant(tenant)
                .code("RACE" + System.nanoTime())
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50.00"))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .maxUsageCount(maxUsageCount)
                .currentUsageCount(currentUsageCount)
                .maxUsagePerUser(1)
                .isActive(true)
                .build());
    }

    /**
     * 讓 {@code THREADS} 條執行緒在同一瞬間、各自獨立交易內搶同一張券。
     *
     * @return 宣稱佔用成功的次數
     */
    private int raceForQuota(final PromoCode promo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<Boolean> attempt = () -> {
                    startGun.await();
                    // 每條執行緒各開一筆交易：條件式 UPDATE 取得的行鎖須在該交易提交時才釋放，
                    // 若共用單一交易就退化成單執行緒，測不到競態
                    return Boolean.TRUE.equals(
                            txTemplate.execute(status -> promoService.tryConsumeUsageQuota(promo)));
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();

            int granted = 0;
            for (Future<Boolean> f : results) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    granted++;
                }
            }
            return granted;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /** 在獨立交易內佔用一次額度（{@code @Modifying} 需要交易）。 */
    private boolean consumeInOwnTransaction(final PromoCode promo) {
        Boolean granted = txTemplate.execute(status -> promoService.tryConsumeUsageQuota(promo));
        return Boolean.TRUE.equals(granted);
    }

    private int reloadUsageCount(final UUID promoId) {
        return promoCodeRepository.findById(promoId).orElseThrow().getCurrentUsageCount();
    }

    @Test
    @DisplayName("10 條執行緒搶上限 3 的限量券 → 恰好 3 次成功，計數精準等於 3（不超發、不漏計）")
    void concurrentConsumeNeverExceedsLimit() throws Exception {
        PromoCode promo = givenPromo(MAX_USAGE, 0);

        int granted = raceForQuota(promo);

        // 修復前：讀後寫使多條執行緒讀到同一份「還有額度」快照而全數放行（超發），
        // 且互相覆蓋的寫入讓 current_usage_count 遠小於實際發出的張數（漏計）。
        assertThat(granted)
                .as("成功佔用次數必須恰好等於總量上限，多一次就是超發")
                .isEqualTo(MAX_USAGE);
        assertThat(reloadUsageCount(promo.getId()))
                .as("DB 計數必須等於實際發出張數，少一筆代表寫入被覆蓋（lost update）")
                .isEqualTo(MAX_USAGE);
    }

    @Test
    @DisplayName("不限量券（max_usage_count 為 NULL）→ 全數放行且計數不漏，仍不退化為讀後寫")
    void unlimitedPromoGrantsEveryAttemptWithoutLostUpdate() throws Exception {
        PromoCode promo = givenPromo(null, 0);

        int granted = raceForQuota(promo);

        assertThat(granted).isEqualTo(THREADS);
        assertThat(reloadUsageCount(promo.getId()))
                .as("不限量券同樣走原子遞增，10 次併發佔用必須累計為 10")
                .isEqualTo(THREADS);
    }

    @Test
    @DisplayName("已達上限的券 → 併發搶購全數被擋，計數不被推過上限")
    void exhaustedPromoGrantsNothing() throws Exception {
        PromoCode promo = givenPromo(MAX_USAGE, MAX_USAGE);

        int granted = raceForQuota(promo);

        assertThat(granted).isZero();
        assertThat(reloadUsageCount(promo.getId())).isEqualTo(MAX_USAGE);
    }

    @Test
    @DisplayName("計數為 0 時退還額度 → 不得成為負數（下限保護，Sprint 102 起由 SQL GREATEST 負責）")
    void releaseNeverGoesBelowZero() {
        PromoCode promo = givenPromo(MAX_USAGE, 0);

        txTemplate.executeWithoutResult(status -> promoService.releaseUsageQuota(promo.getId()));

        // 本案例原為 OrderPromoCodeTest#refundNeverGoesNegative，Sprint 102 起下限保護
        // 下沉到 SQL，mock 掉 Repository 的單元測試已無從驗證，故遷移至此以真實 DB 確認。
        assertThat(reloadUsageCount(promo.getId())).isZero();
    }

    @Test
    @DisplayName("佔用後退還 → 額度回到原值，可再次被佔用（取消訂單的完整往返）")
    void releaseRestoresQuotaForReuse() {
        PromoCode promo = givenPromo(1, 0);

        assertThat(consumeInOwnTransaction(promo)).isTrue();
        assertThat(consumeInOwnTransaction(promo)).as("上限 1 的券第二次必須被擋").isFalse();

        txTemplate.executeWithoutResult(status -> promoService.releaseUsageQuota(promo.getId()));

        assertThat(reloadUsageCount(promo.getId())).isZero();
        assertThat(consumeInOwnTransaction(promo)).as("退還後應可再次佔用").isTrue();
    }
}
