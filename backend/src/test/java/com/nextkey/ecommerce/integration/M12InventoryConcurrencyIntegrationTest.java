package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 商品庫存三段式操作的併發正確性整合測試（Sprint 103，DEF-050；真實 PostgreSQL）。
 *
 * <p>為什麼一定要真實 DB：DEF-050 的本質是「檢查可售量」與「寫回預扣量」分屬兩次資料庫
 * 往返所形成的讀後寫窗口。任何把 {@code ProductInventoryRepository} mock 掉的測試都是在
 * 單執行緒中依序回放 stub，窗口根本不存在——既有的 {@code ProductInventoryServiceTest}
 * 6 個案例全數如此，這正是 Sprint 97 記取的「所有相關測試都用固件繞過同一段邏輯」教訓。
 * 本測試以多執行緒、各自獨立交易，直接壓在同一列 {@code product_inventory} 上。
 *
 * <p>與 Sprint 102（DEF-046 優惠券額度）的差異：{@code ProductInventory} 帶 {@code @Version}
 * 樂觀鎖而 {@code PromoCode} 沒有，因此兩者的失效模式並不相同——實測結論記於各案例的註解。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M12-RACE: 商品庫存併發預扣／釋放／扣帳（Sprint 103 / DEF-050）")
class M12InventoryConcurrencyIntegrationTest {

    @Autowired private ProductInventoryService productInventoryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** 併發執行緒數；刻意大於庫存量，讓超賣（若存在）必然顯現。 */
    private static final int THREADS = 10;

    /** SKU 的可售庫存量。 */
    private static final int STOCK = 3;

    private TransactionTemplate txTemplate;
    private UUID listingId;

    /**
     * Sprint 115（DEF-065）：訂單流程改為同時寫 {@code stock_movements} 流水帳，流水帳的
     * {@code tenant_id}／{@code created_by} 取自 {@code order.getTenant()}／{@code getUser()}
     * ——刻意走關聯物件而非 {@code getTenantId()}／{@code getUserId()}，因為那兩個是
     * {@code insertable=false} 的影子欄位，訂單剛建立時是 null。這裡保留實體以組出與生產相同形狀的訂單。
     */
    private Tenant tenant;
    private User owner;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        txTemplate = new TransactionTemplate(transactionManager);
        listingId = seedListing();
    }

    /**
     * 種出 tenant → user → listing 的最小鏈（{@code product_skus.product_listing_id} 為 NOT NULL FK）。
     *
     * <p>刻意以 JPA builder 並設定**關聯物件**（{@code .tenant(...)} / {@code .owner(...)}）而非影子
     * 欄位：{@code Listing.tenantId}／{@code ownerId} 是 {@code insertable=false}，用影子欄位建 listing
     * 會寫成 null 而撞 NOT NULL（既知陷阱）。租戶亦不改用 raw SQL——{@code tenants} 有多個帶 NOT NULL
     * 的旗標欄位（如 {@code connect_charges_enabled}），手寫 INSERT 得逐欄追 schema，交給 JPA 更穩。
     */
    private UUID seedListing() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Inventory Race Tenant")
                .slug("inv-race-" + stamp)
                .contactEmail("inv-race-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        owner = userRepository.save(User.builder()
                .email("inv-race-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Inventory Race Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());

        return listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Inventory Race Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    /** 種一個 SKU 並附上庫存列；回傳 skuId。 */
    private UUID givenSkuWithInventory(final int totalQty, final int reservedQty) {
        UUID skuId = givenSkuWithoutInventory();
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, ?, 10, 0, NOW())
                """, skuId, totalQty, reservedQty);
        return skuId;
    }

    /** 種一個沒有庫存列的 SKU（既有語意：視為未啟用庫存追蹤，不限量）。 */
    private UUID givenSkuWithoutInventory() {
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-RACE-" + System.nanoTime());
        return skuId;
    }

    /**
     * 組出一張只在記憶體中存在的訂單。{@code ProductInventoryService} 只讀 {@code getItems()}
     * 的 SKU id 與數量，不需要真的把訂單寫進 DB，讓測試聚焦在庫存列本身。
     */
    private Order orderOf(final UUID skuId, final int quantity) {
        Order order = newOrder();
        order.addItem(persistedItem(skuId, quantity));
        return order;
    }

    /**
     * Sprint 115（DEF-065）：訂單與品項都必須帶 id。{@code ProductInventoryService} 會對未持久化
     * 的訂單大聲失敗——流水帳的 {@code reference_id}／{@code order_item_id} 少了就是查不到來源的孤兒列。
     * 這裡只補 id 與租戶／買家關聯，不真的把訂單寫進 DB：{@code stock_movements} 對這兩欄沒有 FK，
     * 而本測試要壓的是 {@code product_inventory} 那一列，不是訂單本身。
     */
    private Order newOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .user(owner)
                .totalAmount(BigDecimal.TEN)
                .build();
    }

    private OrderItem persistedItem(final UUID skuId, final int quantity) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .sku(ProductSku.builder().id(skuId).build())
                .quantity(quantity)
                .build();
    }

    /** 多品項訂單，每項各 1 件；用於驗證單一品項失敗時整張訂單的回滾行為。 */
    private Order orderOfItems(final UUID... skuIds) {
        Order order = newOrder();
        for (UUID skuId : skuIds) {
            order.addItem(persistedItem(skuId, 1));
        }
        return order;
    }

    /** 一次併發競賽的結果分類；未預期的例外型別會被完整保留，避免把失敗模式吞掉。 */
    private record RaceResult(int granted, int insufficientStock, Map<String, Integer> unexpectedFailures) {
    }

    /** 讓 {@code THREADS} 條執行緒在同一瞬間、各自獨立交易內對同一列庫存執行 {@code action}。 */
    private RaceResult race(final Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();
        List<Future<?>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        // 每條執行緒各開一筆交易：條件式 UPDATE 取得的行鎖須在該交易提交時才釋放，
                        // 若共用單一交易就退化成單執行緒，測不到競態
                        txTemplate.executeWithoutResult(status -> action.run());
                        granted.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.E_3004) {
                            insufficient.incrementAndGet();
                        } else {
                            unexpected.merge(e.getErrorCode().getCode(), 1, Integer::sum);
                        }
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
            return new RaceResult(granted.get(), insufficient.get(), unexpected);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private int reservedQtyOf(final UUID skuId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
    }

    private int totalQtyOf(final UUID skuId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
    }

    @Test
    @DisplayName("10 張訂單搶庫存 3 的 SKU → 恰好 3 張預扣成功，其餘全數收到 E-3004，不超賣也不漏計")
    void concurrentReserveNeverOversells() throws Exception {
        UUID skuId = givenSkuWithInventory(STOCK, 0);

        RaceResult result = race(() -> productInventoryService.reserveForOrder(orderOf(skuId, 1)));

        assertThat(result.unexpectedFailures())
                .as("預扣失敗必須是語意明確的庫存不足；樂觀鎖衝突之類的技術性例外會變成買家看到的 500。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted())
                .as("成功預扣數必須恰好等於可售庫存，多一張就是超賣實體商品")
                .isEqualTo(STOCK);
        assertThat(result.insufficientStock()).isEqualTo(THREADS - STOCK);
        assertThat(reservedQtyOf(skuId))
                .as("DB 預扣量必須等於實際成功的張數，少一筆代表寫入被覆蓋（lost update）")
                .isEqualTo(STOCK);
    }

    @Test
    @DisplayName("庫存已全數預扣的 SKU → 併發下單全被擋，預扣量不被推過總量")
    void exhaustedSkuGrantsNothing() throws Exception {
        UUID skuId = givenSkuWithInventory(STOCK, STOCK);

        RaceResult result = race(() -> productInventoryService.reserveForOrder(orderOf(skuId, 1)));

        assertThat(result.granted()).isZero();
        assertThat(result.insufficientStock()).isEqualTo(THREADS);
        assertThat(reservedQtyOf(skuId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("SKU 無庫存資料列 → 併發下單全數放行（未啟用庫存追蹤的既有語意不得被修法改掉）")
    void untrackedSkuStillSkipsCheck() throws Exception {
        UUID skuId = givenSkuWithoutInventory();

        RaceResult result = race(() -> productInventoryService.reserveForOrder(orderOf(skuId, 1)));

        assertThat(result.unexpectedFailures()).isEmpty();
        assertThat(result.granted()).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("同一張訂單第二項庫存不足 → 第一項的預扣必須隨交易回滾，不留部分預扣")
    void insufficientSecondItemRollsBackFirstItemReservation() {
        UUID plentiful = givenSkuWithInventory(STOCK, 0);
        UUID exhausted = givenSkuWithInventory(STOCK, STOCK);

        try {
            txTemplate.executeWithoutResult(status ->
                    productInventoryService.reserveForOrder(orderOfItems(plentiful, exhausted)));
        } catch (BusinessException expected) {
            assertThat(expected.getErrorCode()).isEqualTo(ErrorCode.E_3004);
        }

        assertThat(reservedQtyOf(plentiful))
                .as("原生 UPDATE 同樣受交易保護；留下孤兒預扣等於庫存被永久鎖死")
                .isZero();
    }

    @Test
    @DisplayName("10 筆取消併發釋放同一 SKU → 預扣量精準歸零（不得因互相覆蓋而殘留）")
    void concurrentReleaseRestoresExactly() throws Exception {
        UUID skuId = givenSkuWithInventory(THREADS, THREADS);

        RaceResult result = race(() -> productInventoryService.releaseForOrder(orderOf(skuId, 1)));

        assertThat(result.unexpectedFailures())
                .as("釋放失敗代表取消訂單沒把庫存還回去，該批貨等於被永久鎖死。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted()).isEqualTo(THREADS);
        assertThat(reservedQtyOf(skuId))
                .as("10 次各釋放 1 必須累計釋放 10；殘留代表有寫入被覆蓋")
                .isZero();
    }

    @Test
    @DisplayName("10 筆付款併發扣帳同一 SKU → 總量與預扣量同步精準歸零")
    void concurrentDeductKeepsLedgerExact() throws Exception {
        UUID skuId = givenSkuWithInventory(THREADS, THREADS);

        RaceResult result = race(() -> productInventoryService.deductForOrder(orderOf(skuId, 1)));

        assertThat(result.unexpectedFailures())
                .as("扣帳失敗會被 PaymentStateService.deductStockSafely 靜默吞掉——"
                        + "付款成功但庫存沒扣，該 SKU 從此帳實不符並持續可賣。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted()).isEqualTo(THREADS);
        assertThat(totalQtyOf(skuId)).isZero();
        assertThat(reservedQtyOf(skuId)).isZero();
    }

    @Test
    @DisplayName("預扣量為 0 時釋放 → 不得成為負數（下限保護，Sprint 103 起由 SQL GREATEST 負責）")
    void releaseNeverGoesBelowZero() {
        UUID skuId = givenSkuWithInventory(STOCK, 0);

        txTemplate.executeWithoutResult(status ->
                productInventoryService.releaseForOrder(orderOf(skuId, 1)));

        // 語意與修復前的 ProductInventory.release() 內 Math.max(0, ...) 一致；
        // 該保護下沉到 SQL 後，mock 掉 Repository 的單元測試已無從驗證。
        assertThat(reservedQtyOf(skuId)).isZero();
    }
}
