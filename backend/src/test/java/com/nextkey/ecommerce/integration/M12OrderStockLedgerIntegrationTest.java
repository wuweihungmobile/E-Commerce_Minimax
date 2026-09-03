package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 訂單流程的庫存流水帳整合測試（Sprint 115，DEF-065；真實 PostgreSQL）。
 *
 * <p>PRD §6.7.3「ERP → C 端連動機制」明訂：下單產生 {@code StockMovement type=RESERVE}、
 * 出貨產生 {@code OUTBOUND}、取消回滾產生 {@code RELEASE}。但在 Sprint 115 之前，
 * 全專案只有 {@code PurchaseOrderService}（採購收貨）與 {@code StockMovementService}（手動異動）
 * 寫入 {@code stock_movements}——{@code ProductInventoryService} 對 {@code StockMovement} 零引用，
 * {@code product_inventory} 的數字會動、流水帳一筆都不會留。
 *
 * <p>後果不只是「少了記錄」：庫存台帳（PRD §6.7.3 列為 P0）對 C 端的所有進出完全沒有資料，
 * 而且正因為 {@code RESERVE}／{@code OUTBOUND}／{@code RELEASE} 三型從來沒有被寫入過，
 * 後端枚舉把它們拼錯成 {@code RESERVATION}／{@code SALE} 也沒有人發現（DEF-063，Sprint 114 修）。
 *
 * <p>為什麼要真實 DB：本測試斷言的是「哪些列真的落到 {@code stock_movements} 表裡、每一欄的值是什麼」。
 * 把 Repository mock 掉就只能驗到「有沒有呼叫 save()」，驗不到 NOT NULL 欄位、FK、以及前後數量是否自洽。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M12-LEDGER: 訂單流程庫存流水帳（Sprint 115 / DEF-065）")
class M12OrderStockLedgerIntegrationTest {

    @Autowired private ProductInventoryService productInventoryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private OrderService orderService;
    @Autowired private RedisCartService cartService;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /**
     * 🔴 刻意保留實體而非只留 id：{@code Order.tenantId}／{@code userId} 是 {@code insertable=false}
     * 的影子欄位，{@code OrderService} 建單時設的是 {@code .tenant(...)}／{@code .user(...)}，
     * 影子欄位要等實體重新從 DB 載入才會有值。本測試因此比照生產的物件形狀組訂單——
     * 只設關聯物件、不設影子欄位，若實作改讀 {@code getTenantId()} 就會拿到 null 而撞 NOT NULL。
     */
    private Tenant tenant;
    private User owner;
    private UUID tenantId;
    private UUID userId;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        seedTenantUserListing();
    }

    // ── PRD §6.7.3 的三個寫入點 ────────────────────────────────

    @Test
    @DisplayName("IT-M12-LEDGER-001: 訂單建立預扣 → 寫下一筆 RESERVE，記錄預留量前後值並指回訂單")
    void reserveForOrder_writesReserveMovement() {
        UUID skuId = givenSkuWithInventory(100, 10);
        Order order = persistedOrderOf(skuId, 7);

        productInventoryService.reserveForOrder(order);

        Map<String, Object> row = singleMovementOf(skuId);
        assertThat(row.get("movement_type")).isEqualTo("RESERVE");
        assertThat(row.get("quantity")).isEqualTo(7);
        // PRD §6.7.4：RESERVE 為 +reserved_qty，total_qty 不動
        assertThat(row.get("before_reserved_qty")).isEqualTo(10);
        assertThat(row.get("after_reserved_qty")).isEqualTo(17);
        assertThat(row.get("before_total_qty")).isEqualTo(100);
        assertThat(row.get("after_total_qty")).isEqualTo(100);
        // 流水帳必須能回指來源單據，否則庫存台帳查得到數字卻查不到原因
        assertThat(row.get("reference_type")).isEqualTo("ORDER");
        assertThat(row.get("reference_id")).isEqualTo(order.getId());
        assertThat(row.get("order_item_id")).isEqualTo(order.getItems().get(0).getId());
        assertThat(row.get("tenant_id")).isEqualTo(tenantId);
        assertThat(row.get("created_by")).isEqualTo(userId);
    }

    @Test
    @DisplayName("IT-M12-LEDGER-002: 付款扣帳 → 寫下一筆 OUTBOUND，total 與 reserved 同時遞減")
    void deductForOrder_writesOutboundMovement() {
        UUID skuId = givenSkuWithInventory(100, 30);
        Order order = persistedOrderOf(skuId, 5);

        productInventoryService.deductForOrder(order);

        Map<String, Object> row = singleMovementOf(skuId);
        assertThat(row.get("movement_type")).isEqualTo("OUTBOUND");
        assertThat(row.get("quantity")).isEqualTo(5);
        // PRD §6.7.4：OUTBOUND 為 -total_qty, -reserved_qty
        assertThat(row.get("before_total_qty")).isEqualTo(100);
        assertThat(row.get("after_total_qty")).isEqualTo(95);
        assertThat(row.get("before_reserved_qty")).isEqualTo(30);
        assertThat(row.get("after_reserved_qty")).isEqualTo(25);
        assertThat(row.get("reference_id")).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("IT-M12-LEDGER-003: 未付款取消釋放 → 寫下一筆 RELEASE，只動 reserved")
    void releaseForOrder_writesReleaseMovement() {
        UUID skuId = givenSkuWithInventory(100, 40);
        Order order = persistedOrderOf(skuId, 15);

        productInventoryService.releaseForOrder(order);

        Map<String, Object> row = singleMovementOf(skuId);
        assertThat(row.get("movement_type")).isEqualTo("RELEASE");
        assertThat(row.get("quantity")).isEqualTo(15);
        // PRD §6.7.4：RELEASE 為 -reserved_qty，total_qty 不動
        assertThat(row.get("before_reserved_qty")).isEqualTo(40);
        assertThat(row.get("after_reserved_qty")).isEqualTo(25);
        assertThat(row.get("before_total_qty")).isEqualTo(100);
        assertThat(row.get("after_total_qty")).isEqualTo(100);
    }

    // ── 邊界：既有「未啟用庫存追蹤」語意不得被流水帳破壞 ──────────

    @Test
    @DisplayName("IT-M12-LEDGER-004: SKU 無庫存列（未啟用追蹤）→ 庫存不動，也不得留下流水帳")
    void untrackedSku_writesNoMovement() {
        UUID skuId = givenSkuWithoutInventory();
        Order order = persistedOrderOf(skuId, 3);

        productInventoryService.reserveForOrder(order);
        productInventoryService.deductForOrder(order);
        productInventoryService.releaseForOrder(order);

        // 沒有庫存列就沒有異動可言：寫一筆 before/after 皆為 0 的流水帳會讓台帳出現不存在的異動
        assertThat(movementCountOf(skuId)).isZero();
    }

    @Test
    @DisplayName("IT-M12-LEDGER-005: 預扣失敗（可售量不足）→ 不得留下流水帳")
    void insufficientStock_writesNoMovement() {
        UUID skuId = givenSkuWithInventory(10, 8); // 可售 2
        Order order = persistedOrderOf(skuId, 5);

        assertThatThrownBy(() -> productInventoryService.reserveForOrder(order))
                .isInstanceOf(RuntimeException.class);

        assertThat(movementCountOf(skuId)).isZero();
    }

    @Test
    @DisplayName("IT-M12-LEDGER-006: 多品項訂單 → 每個品項各留一筆，各自指回自己的 order_item")
    void multiItemOrder_writesOneMovementPerItem() {
        UUID skuA = givenSkuWithInventory(50, 0);
        UUID skuB = givenSkuWithInventory(50, 0);
        Order order = persistedOrderOfItems(Map.of(skuA, 2, skuB, 3));

        productInventoryService.reserveForOrder(order);

        assertThat(movementCountOf(skuA)).isEqualTo(1);
        assertThat(movementCountOf(skuB)).isEqualTo(1);
        assertThat(singleMovementOf(skuA).get("quantity")).isEqualTo(2);
        assertThat(singleMovementOf(skuB).get("quantity")).isEqualTo(3);
        // 每一筆的 order_item_id 必須是自己那一項，不能全部指到第一項
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sku_id, order_item_id FROM stock_movements WHERE reference_id = ?", order.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(r -> r.get("order_item_id")).distinct()).hasSize(2);
    }

    // ── 守衛：預扣必須發生在訂單持久化之後 ──────────────────────

    @Test
    @DisplayName("IT-M12-LEDGER-007: 守衛——未持久化的訂單不得預扣（否則流水帳指不回任何單據）")
    void reserveForOrder_transientOrder_failsLoudly() {
        // DEF-065 修復前，OrderService 是「先 reserveForOrder 再 save」，此時 order.getId() 與
        // orderItem.getId() 都還是 null。若日後有人把順序改回去，流水帳會寫出一批
        // reference_id 為 null 的孤兒列——查得到數字、查不到來源，等同沒記。
        // 這裡讓它直接爆，而不是靜默寫出壞資料。
        UUID skuId = givenSkuWithInventory(100, 0);
        Order transientOrder = Order.builder()
                .tenant(tenant)
                .user(owner)
                .totalAmount(BigDecimal.TEN)
                .build();
        transientOrder.addItem(OrderItem.builder()
                .sku(ProductSku.builder().id(skuId).build())
                .quantity(1)
                .build());

        assertThatThrownBy(() -> productInventoryService.reserveForOrder(transientOrder))
                .isInstanceOf(IllegalStateException.class);

        assertThat(movementCountOf(skuId)).isZero();
    }

    // ── 走真實建單路徑：固件不得繞過 OrderService 的呼叫順序 ──────

    @Test
    @DisplayName("IT-M12-LEDGER-008: 真實 createOrderFromCart → RESERVE 流水帳帶得到真實生成的 id")
    void createOrderFromCart_writesReserveMovementWithGeneratedIds() {
        // 上面幾個案例用 raw SQL 種訂單，好處是失敗訊息乾淨，代價是**繞過了 OrderService 本身**——
        // 而 DEF-065 修復動到的正是它的呼叫順序（reserveForOrder 從 save() 之前移到之後）。
        // 只靠那些案例，等於這條修改在測試裡一次都沒被真的跑過（承 S97 的固件繞過教訓）。
        UUID skuId = givenSkuWithInventory(20, 0);
        givenCartWith(skuId, 3);

        OrderDto.OrderResponse created = orderService.createOrderFromCart(productCheckoutRequest());

        Map<String, Object> row = singleMovementOf(skuId);
        assertThat(row.get("movement_type")).isEqualTo("RESERVE");
        assertThat(row.get("quantity")).isEqualTo(3);
        // 關鍵：這兩個欄位在 save() 之前都是 null。非 null 才證明順序真的對了。
        assertThat(row.get("reference_id")).isNotNull().isEqualTo(created.getId());
        assertThat(row.get("order_item_id")).isNotNull();
        assertThat(row.get("tenant_id")).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("IT-M12-LEDGER-009: 真實建單遇庫存不足 → 訂單與流水帳都不留下（交易回滾）")
    void createOrderFromCart_insufficientStock_rollsBackOrderAndLedger() {
        // DEF-065 之前這個保證來自「reserveForOrder 排在 save() 之前，所以根本沒 save」；
        // 改成排在 save() 之後後，保證改由 @Transactional 的回滾提供（BusinessException 是
        // RuntimeException）。原本的單元測試以 verify(never()).save() 斷言前者——那驗的是機制，
        // 不是不變量，且 mock 測試看不到回滾。真正該守的不變量在這裡驗。
        UUID skuId = givenSkuWithInventory(2, 0); // 可售 2
        givenCartWith(skuId, 5);

        assertThatThrownBy(() -> orderService.createOrderFromCart(productCheckoutRequest()))
                .isInstanceOf(BusinessException.class);

        assertThat(movementCountOf(skuId)).isZero();
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(orderCount).isZero();
        // 預扣也必須跟著回滾，否則那批貨會被永久鎖住
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
        assertThat(reserved).isZero();
    }

    // ── 固件 ──────────────────────────────────────────────────

    /**
     * 種出 tenant → user → listing 的最小鏈（比照 {@code M12InventoryConcurrencyIntegrationTest}）。
     * 以 JPA builder 設定關聯物件而非影子欄位：{@code Listing.tenantId}／{@code ownerId} 是
     * {@code insertable=false}，用影子欄位會寫成 null 而撞 NOT NULL（既知陷阱）。
     */
    private void seedTenantUserListing() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Ledger Tenant")
                .slug("ledger-" + stamp)
                .contactEmail("ledger-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        owner = userRepository.save(User.builder()
                .email("ledger-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Ledger Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
        userId = owner.getId();

        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Ledger Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    private UUID givenSkuWithInventory(final int totalQty, final int reservedQty) {
        UUID skuId = givenSkuWithoutInventory();
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, ?, 10, 0, NOW())
                """, skuId, totalQty, reservedQty);
        return skuId;
    }

    private UUID givenSkuWithoutInventory() {
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-LEDGER-" + System.nanoTime());
        return skuId;
    }

    /**
     * 以 raw SQL 種一張已持久化的訂單（含品項），回傳帶有真實 id 的記憶體物件。
     *
     * <p>刻意不走 {@code OrderService.createOrderFromCart}：那條路要先把購物車寫進 Redis、
     * 過運費與促銷碼，與本測試要驗的「流水帳有沒有寫、欄位對不對」無關，只會讓失敗訊息變難讀。
     * 這裡要的只是「一張 id 與 order_item_id 都不是 null 的訂單」。
     */
    private Order persistedOrderOf(final UUID skuId, final int quantity) {
        return persistedOrderOfItems(Map.of(skuId, quantity));
    }

    private Order persistedOrderOfItems(final Map<UUID, Integer> skuQuantities) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', 'CREATED', 100.00, 0.00, 0.00, 'TWD', NOW(), NOW())
                """, orderId, tenantId, userId);

        // 只設關聯物件，比照 OrderService.createOrderFromCart（見欄位宣告處的說明）
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .user(owner)
                .totalAmount(BigDecimal.TEN)
                .build();

        for (Map.Entry<UUID, Integer> entry : skuQuantities.entrySet()) {
            UUID itemId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO order_items (id, order_id, listing_id, sku_id, quantity, unit_price, subtotal,
                                             created_at)
                    VALUES (?, ?, ?, ?, ?, 100.00, 100.00, NOW())
                    """, itemId, orderId, listingId, entry.getKey(), entry.getValue());
            order.addItem(OrderItem.builder()
                    .id(itemId)
                    .sku(ProductSku.builder().id(entry.getKey()).build())
                    .quantity(entry.getValue())
                    .build());
        }
        return order;
    }

    /** 把 SKU 放進買家的購物車，並設好 TenantContext（{@code createOrderFromCart} 由此取得身分）。 */
    private void givenCartWith(final UUID skuId, final int quantity) {
        TenantContext.setCurrentUser(userId);
        TenantContext.setCurrentTenant(tenantId);
        cartService.clearCart(userId, tenantId);
        cartService.addItem(userId, tenantId, CartDto.AddItemRequest.builder()
                .listingId(listingId)
                .skuId(skuId)
                .quantity(quantity)
                .build());
    }

    private OrderDto.CreateRequest productCheckoutRequest() {
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區信義路五段 7 號");
        request.setShippingRecipientName("測試買家");
        request.setShippingPhone("0912345678");
        return request;
    }

    private Map<String, Object> singleMovementOf(final UUID skuId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM stock_movements WHERE sku_id = ?", skuId);
        assertThat(rows).as("預期 SKU %s 恰有一筆庫存流水帳", skuId).hasSize(1);
        return rows.get(0);
    }

    private int movementCountOf(final UUID skuId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE sku_id = ?", Integer.class, skuId);
        return count == null ? 0 : count;
    }
}
