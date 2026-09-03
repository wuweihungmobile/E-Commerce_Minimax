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

import org.junit.jupiter.api.AfterEach;
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

import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderDto;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderReceiveRequest;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.core.erp.PurchaseOrderService;
import com.nextkey.ecommerce.core.erp.StockMovementService;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * M16 ERP 後台庫存寫入的併發正確性整合測試（Sprint 113，DEF-051；真實 PostgreSQL）。
 *
 * <p>Sprint 103 把**訂單流程**的三段式庫存改成原子 UPDATE 時，刻意沒有一併動 ERP 後台
 * （Rule 3），並記為 DEF-051。ERP 兩處（{@code StockMovementService.createManualMovement}
 * 手動異動、{@code PurchaseOrderService} 收貨時的 {@code createInboundMovement} 入庫）
 * 寫的是**同一張 {@code product_inventory}**，卻仍是「載入實體 → {@code addStock()}/
 * {@code deductStock()} → {@code save()}」的讀後寫。
 *
 * <p>與 Sprint 103 相同，{@code ProductInventory} 帶 {@code @Version}，所以失效模式**不是**
 * lost update 而是 {@code ObjectOptimisticLockingFailureException}；但 ERP 側多了兩個訂單流程
 * 沒有的問題，本測試都要能分辨：
 * <ul>
 *   <li>手動扣減的「庫存是否足夠」與「扣減」分屬兩次往返，條件檢查對併發無效；</li>
 *   <li>{@code deductStock()} 連同 {@code reserved_qty} 一起扣——PRD §6.7.4 明訂
 *       ADJUST_MINUS／TRANSFER_OUT／SCRAP 只動 {@code total_qty}，只有 OUTBOUND（訂單出貨）
 *       才同時扣預留量。這一條與併發無關，是純粹的語意錯誤。</li>
 * </ul>
 *
 * <p>刻意保留**單執行緒**案例（{@code singleManualAdjustment...}／{@code manualDeduction...}）：
 * 若整個類別都是 10 執行緒，語意錯誤會被理所當然地讀成「併發嘛」，根因就被錯過（承 Sprint 106
 * 的教訓）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M16-RACE: ERP 手動異動／採購入庫的庫存併發（Sprint 113 / DEF-051）")
class M16ErpInventoryConcurrencyIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private SupplierRepository supplierRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** 併發執行緒數。 */
    private static final int THREADS = 10;

    private TransactionTemplate txTemplate;
    private UUID tenantId;
    private UUID userId;
    private UUID listingId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        txTemplate = new TransactionTemplate(transactionManager);
        seedTenantScope();
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 種出 tenant → user → listing → supplier。比照 {@code M12InventoryConcurrencyIntegrationTest}
     * 以 JPA builder 建 listing 並設定**關聯物件**：{@code Listing.tenantId}／{@code ownerId} 是
     * {@code insertable=false} 影子欄位，用它們建 listing 會寫成 null 而撞 NOT NULL。
     */
    private void seedTenantScope() {
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("ERP Race Tenant")
                .slug("erp-race-" + stamp)
                .contactEmail("erp-race-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        User owner = userRepository.save(User.builder()
                .email("erp-race-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("ERP Race Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
        userId = owner.getId();

        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title("ERP Race Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();

        supplierId = supplierRepository.save(Supplier.builder()
                .tenantId(tenantId)
                .name("ERP Race Supplier")
                .email("erp-race-supplier-" + stamp + "@example.com")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build()).getId();
    }

    /** 種一個 SKU 並附上庫存列；回傳 skuId。 */
    private UUID givenSkuWithInventory(final int totalQty, final int reservedQty) {
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-ERP-RACE-" + System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, ?, 10, 0, NOW())
                """, skuId, totalQty, reservedQty);
        return skuId;
    }

    private StockMovementRequest movementOf(final UUID skuId, final String type, final int quantity) {
        return StockMovementRequest.builder()
                .skuId(skuId)
                .movementType(type)
                .quantity(quantity)
                .notes("ERP race test")
                .build();
    }

    /** 建立一張只含單一 SKU、數量為 {@code quantity} 的採購單並提交（收貨前置狀態）。 */
    private PurchaseOrderDto givenSubmittedPurchaseOrder(final UUID skuId, final int quantity) {
        PurchaseOrderDto created = purchaseOrderService.createPurchaseOrder(PurchaseOrderCreateRequest.builder()
                .supplierId(supplierId)
                .items(List.of(PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                        .listingId(listingId)
                        .skuId(skuId)
                        .quantity(quantity)
                        .unitCost(new BigDecimal("10.00"))
                        .build()))
                .build(), userId);
        return purchaseOrderService.submitPurchaseOrder(created.getId());
    }

    /** 一次併發競賽的結果分類；未預期的例外型別會被完整保留，避免把失敗模式吞掉。 */
    private record RaceResult(int granted, int insufficientStock, Map<String, Integer> unexpectedFailures) {
    }

    /**
     * 讓 {@code THREADS} 條執行緒在同一瞬間、各自獨立交易內執行 {@code action}。
     *
     * <p>{@code TenantContext} 是 ThreadLocal，工作執行緒不會繼承主執行緒的租戶，
     * 必須各自設定；漏掉會讓每條執行緒在 {@code tenantId.equals(...)} 前先 NPE，
     * 測出來的東西與併發無關。
     */
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
                    TenantContext.setCurrentTenant(tenantId);
                    startGun.await();
                    try {
                        txTemplate.executeWithoutResult(status -> action.run());
                        granted.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.E_7004) {
                            insufficient.incrementAndGet();
                        } else {
                            unexpected.merge(e.getErrorCode().getCode(), 1, Integer::sum);
                        }
                    } catch (RuntimeException e) {
                        unexpected.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    } finally {
                        TenantContext.clear();
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

    private int totalQtyOf(final UUID skuId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
    }

    private int reservedQtyOf(final UUID skuId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
    }

    private List<Integer> afterQtyLedgerOf(final UUID skuId) {
        return jdbcTemplate.queryForList(
                "SELECT after_total_qty FROM stock_movements WHERE sku_id = ? ORDER BY after_total_qty",
                Integer.class, skuId);
    }

    // ── 手動異動（StockMovementService.createManualMovement） ──────────────

    @Test
    @DisplayName("單筆手動盤盈 → 庫存確實寫進 DB，異動記錄的前後數量與實際一致（非併發對照組）")
    void singleManualAdjustmentWritesThrough() {
        UUID skuId = givenSkuWithInventory(0, 0);

        txTemplate.executeWithoutResult(status ->
                stockMovementService.createManualMovement(movementOf(skuId, "ADJUST_PLUS", 5), userId));

        assertThat(totalQtyOf(skuId))
                .as("這一條不含併發；它一失敗就代表問題不在競態，而在寫入路徑本身")
                .isEqualTo(5);
        assertThat(afterQtyLedgerOf(skuId)).containsExactly(5);
    }

    @Test
    @DisplayName("10 筆併發盤盈各 +1 → 全數成功且總量精準 +10，異動流水不得有斷層")
    void concurrentManualAdjustmentsAllApply() throws Exception {
        UUID skuId = givenSkuWithInventory(0, 0);

        RaceResult result = race(() ->
                stockMovementService.createManualMovement(movementOf(skuId, "ADJUST_PLUS", 1), userId));

        assertThat(result.unexpectedFailures())
                .as("後台盤點併發時的樂觀鎖衝突會變成操作員看到的 500，且該筆異動整個消失。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted()).isEqualTo(THREADS);
        assertThat(totalQtyOf(skuId))
                .as("10 筆各 +1 必須累加成 10")
                .isEqualTo(THREADS);
        assertThat(afterQtyLedgerOf(skuId))
                .as("每筆異動記錄的 after_total_qty 必須恰好走完 1..10；重複或跳號代表流水帳與實際庫存對不上")
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    @DisplayName("庫存 3 遇上 10 筆併發報廢各 1 → 恰好 3 筆成功、7 筆 E-7004，總量歸零不成負數")
    void concurrentManualDeductionsNeverOverdraw() throws Exception {
        UUID skuId = givenSkuWithInventory(3, 0);

        RaceResult result = race(() ->
                stockMovementService.createManualMovement(movementOf(skuId, "ADJUST_MINUS", 1), userId));

        assertThat(result.unexpectedFailures())
                .as("扣減失敗必須是語意明確的庫存不足；技術性例外會讓操作員無從判斷是否已扣。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted())
                .as("「庫存是否足夠」與「扣減」若分屬兩次往返，10 條執行緒會同時看到 3 而全數放行")
                .isEqualTo(3);
        assertThat(result.insufficientStock()).isEqualTo(THREADS - 3);
        assertThat(totalQtyOf(skuId)).isZero();
    }

    @Test
    @DisplayName("手動報廢不得動到 reserved_qty（PRD §6.7.4：只有 OUTBOUND 才扣預留量）")
    void manualDeductionMustNotReleaseReservedStock() {
        // 總量 10、其中 4 件已被訂單預留 → 可售 6
        UUID skuId = givenSkuWithInventory(10, 4);

        txTemplate.executeWithoutResult(status ->
                stockMovementService.createManualMovement(movementOf(skuId, "ADJUST_MINUS", 3), userId));

        assertThat(totalQtyOf(skuId))
                .as("報廢 3 件，總量 10 → 7")
                .isEqualTo(7);
        assertThat(reservedQtyOf(skuId))
                .as("報廢的是庫存不是訂單：預留量若被一併扣掉，那 3 件已被買家訂走的貨會重新變成可售（可售量 3 → 6），"
                        + "等於憑空多賣 3 件")
                .isEqualTo(4);
    }

    // ── 採購收貨入庫（PurchaseOrderService.createInboundMovement） ──────────

    @Test
    @DisplayName("10 張採購單併發收貨同一 SKU 各 1 件 → 全數入庫，總量精準 +10")
    void concurrentPurchaseReceiptsAllApply() throws Exception {
        UUID skuId = givenSkuWithInventory(0, 0);

        // 每條執行緒收自己的那張採購單：競爭點只在共用的那一列庫存，不在採購單本身
        List<PurchaseOrderDto> orders = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; i++) {
            orders.add(givenSubmittedPurchaseOrder(skuId, 1));
        }
        AtomicInteger cursor = new AtomicInteger();

        RaceResult result = race(() -> {
            PurchaseOrderDto po = orders.get(cursor.getAndIncrement());
            purchaseOrderService.receivePurchaseOrder(po.getId(), PurchaseOrderReceiveRequest.builder()
                    .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                            .itemId(po.getItems().get(0).getId())
                            .receivedQuantity(1)
                            .build()))
                    .build());
        });

        assertThat(result.unexpectedFailures())
                .as("收貨失敗代表貨已經到了、系統卻沒入帳，且採購單狀態也沒推進。本次結果：%s", result)
                .isEmpty();
        assertThat(result.granted()).isEqualTo(THREADS);
        assertThat(totalQtyOf(skuId))
                .as("10 張採購單各收 1 件必須累加成 10")
                .isEqualTo(THREADS);
    }
}
