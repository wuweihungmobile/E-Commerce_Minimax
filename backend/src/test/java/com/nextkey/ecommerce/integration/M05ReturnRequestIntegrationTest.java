package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.returns.ReturnDto;
import com.nextkey.ecommerce.core.returns.ReturnRequestService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 退貨申請與退貨入庫整合測試（Sprint 118，DEF-044；真實 PostgreSQL）。
 *
 * <p><b>本功能存在的理由</b>：已付款訂單退款後，先前正式扣除的庫存不會回補——那批貨在系統裡永遠消失。
 * 使用者拍板的規則是「**店家實際收到貨、確認可售後才回補**」：退錢與收貨是兩件事，
 * 退款當下就把庫存加回可售池，等於在賣還沒拿回來的東西。
 *
 * <p>因此本測試最重要的一個案例是 <b>002：核准不得動庫存</b>——它守的正是整個功能的核心主張。
 * 若哪天有人為了「方便」把回補搬到核准那一步，這個案例會紅。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M05-RETURN: 退貨申請與退貨入庫（Sprint 118 / DEF-044）")
class M05ReturnRequestIntegrationTest {

    @Autowired private ReturnRequestService returnRequestService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private String runStamp;
    private UUID tenantId;
    private UUID buyerId;
    private UUID listingId;
    private UUID skuId;
    private UUID orderId;
    private UUID orderItemId;

    private static final int ORDER_QTY = 5;
    private static final int INITIAL_STOCK = 100;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        runStamp = String.valueOf(System.nanoTime());
        seedTenantBuyerListing();
        skuId = seedSkuWithInventory(INITIAL_STOCK);
        seedOrder("DELIVERED");

        TenantContext.setCurrentUser(buyerId);
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("IT-M05-RETURN-001: 買家提出申請 → REQUESTED，庫存完全不動")
    void createReturnRequest_doesNotTouchInventory() {
        ReturnDto.Response response = returnRequestService.createReturnRequest(createRequest(3));

        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.getReturnNumber()).startsWith("RMA-");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getRequestedQty()).isEqualTo(3);
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK);
        assertThat(movementCount()).isZero();
    }

    @Test
    @DisplayName("IT-M05-RETURN-002: 🔴 店家核准 → APPROVED，庫存仍然完全不動（本功能的核心主張）")
    void approveReturn_stillDoesNotTouchInventory() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(3)).getId();

        ReturnDto.Response approved = returnRequestService.approveReturn(returnId);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getReviewedAt()).isNotNull();
        // 核准只是同意收貨，貨還在路上——這時候回補等於在賣還沒拿回來的東西
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK);
        assertThat(movementCount()).isZero();
    }

    @Test
    @DisplayName("IT-M05-RETURN-003: 收貨確認（可售 2／不可售 1）→ 淨回補 2，台帳留下 RETURN(+3) 與 SCRAP(-1)")
    void receiveReturn_restocksSellableAndScrapsUnsellable() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(3)).getId();
        returnRequestService.approveReturn(returnId);
        UUID itemId = returnRequestService.getReturnRequest(returnId).getItems().get(0).getId();

        ReturnDto.Response received = returnRequestService.receiveReturn(returnId,
                ReturnDto.ReceiveRequest.builder()
                        .items(List.of(ReturnDto.ReceiveItem.builder()
                                .itemId(itemId).sellableQty(2).unsellableQty(1).build()))
                        .build());

        assertThat(received.getStatus()).isEqualTo("RECEIVED");
        assertThat(received.getReceivedAt()).isNotNull();
        // 淨額只加回可售的 2 件——不可售的不回補（使用者決策）
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK + 2);

        // 但台帳看得到全貌：貨確實回來 3 件，其中 1 件當場報廢
        List<Map<String, Object>> movements = movements();
        assertThat(movements).hasSize(2);
        Map<String, Object> ret = movements.stream()
                .filter(m -> "RETURN".equals(m.get("movement_type"))).findFirst().orElseThrow();
        Map<String, Object> scrap = movements.stream()
                .filter(m -> "SCRAP".equals(m.get("movement_type"))).findFirst().orElseThrow();
        assertThat(ret.get("quantity")).isEqualTo(3);
        assertThat(ret.get("before_total_qty")).isEqualTo(INITIAL_STOCK);
        assertThat(ret.get("after_total_qty")).isEqualTo(INITIAL_STOCK + 3);
        assertThat(scrap.get("quantity")).isEqualTo(1);
        assertThat(scrap.get("before_total_qty")).isEqualTo(INITIAL_STOCK + 3);
        assertThat(scrap.get("after_total_qty")).isEqualTo(INITIAL_STOCK + 2);
        // 兩筆都回指得到退貨單與原訂單
        assertThat(ret.get("reference_number")).isEqualTo(received.getReturnNumber());
        assertThat(ret.get("reference_id")).isEqualTo(orderId);
        assertThat(ret.get("order_item_id")).isEqualTo(orderItemId);
    }

    @Test
    @DisplayName("IT-M05-RETURN-004: 全部可售 → 只寫一筆 RETURN，不得無故產生 SCRAP")
    void receiveReturn_allSellable_writesOnlyReturnMovement() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(2)).getId();
        returnRequestService.approveReturn(returnId);
        UUID itemId = returnRequestService.getReturnRequest(returnId).getItems().get(0).getId();

        returnRequestService.receiveReturn(returnId, ReturnDto.ReceiveRequest.builder()
                .items(List.of(ReturnDto.ReceiveItem.builder()
                        .itemId(itemId).sellableQty(2).unsellableQty(0).build()))
                .build());

        assertThat(totalQty()).isEqualTo(INITIAL_STOCK + 2);
        assertThat(movements()).hasSize(1);
        assertThat(movements().get(0).get("movement_type")).isEqualTo("RETURN");
    }

    @Test
    @DisplayName("IT-M05-RETURN-005: 駁回 → REJECTED，庫存不動，且該數量重新可申請")
    void rejectReturn_freesUpReturnableQuantity() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(5)).getId();

        returnRequestService.rejectReturn(returnId,
                ReturnDto.RejectRequest.builder().rejectionReason("超過退貨期限").build());

        assertThat(totalQty()).isEqualTo(INITIAL_STOCK);
        assertThat(movementCount()).isZero();
        // 駁回的單不佔額度，買家可以重新申請同樣的數量
        assertThat(returnRequestService.createReturnRequest(createRequest(5)).getStatus())
                .isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("IT-M05-RETURN-006: 累計申請數量不得超過訂單品項數量")
    void createReturnRequest_exceedingOrderQuantity_throwsE5018() {
        returnRequestService.createReturnRequest(createRequest(4));

        // 已申請 4 件，訂單只有 5 件 → 再申請 2 件應被擋
        assertThatThrownBy(() -> returnRequestService.createReturnRequest(createRequest(2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_5018);
    }

    @Test
    @DisplayName("IT-M05-RETURN-007: 收到的數量不得超過申請數量")
    void receiveReturn_exceedingRequested_throwsE5018() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(2)).getId();
        returnRequestService.approveReturn(returnId);
        UUID itemId = returnRequestService.getReturnRequest(returnId).getItems().get(0).getId();

        assertThatThrownBy(() -> returnRequestService.receiveReturn(returnId,
                ReturnDto.ReceiveRequest.builder()
                        .items(List.of(ReturnDto.ReceiveItem.builder()
                                .itemId(itemId).sellableQty(2).unsellableQty(1).build()))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_5018);
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("IT-M05-RETURN-008: 未經核准不得直接收貨（狀態機守衛）")
    void receiveReturn_withoutApproval_throwsE5017() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(2)).getId();
        UUID itemId = returnRequestService.getReturnRequest(returnId).getItems().get(0).getId();

        assertThatThrownBy(() -> returnRequestService.receiveReturn(returnId,
                ReturnDto.ReceiveRequest.builder()
                        .items(List.of(ReturnDto.ReceiveItem.builder()
                                .itemId(itemId).sellableQty(2).unsellableQty(0).build()))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_5017);
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("🔴 IT-M05-RETURN-RACE: 同一筆退貨單被併發呼叫兩次 receiveReturn -> 庫存只淨回補一次"
            + "（DEF-136：原本無併發防護時，兩邊都會各自呼叫 applyReturnReceipt 造成幽靈庫存）")
    void receiveReturn_concurrentCalls_onlyCreditsInventoryOnce() throws Exception {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(3)).getId();
        returnRequestService.approveReturn(returnId);
        UUID itemId = returnRequestService.getReturnRequest(returnId).getItems().get(0).getId();
        ReturnDto.ReceiveRequest receiveRequest = ReturnDto.ReceiveRequest.builder()
                .items(List.of(ReturnDto.ReceiveItem.builder()
                        .itemId(itemId).sellableQty(3).unsellableQty(0).build()))
                .build();

        int threadCount = 5;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startGun = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                java.util.concurrent.Callable<Boolean> attempt = () -> {
                    // TenantContext 是 ThreadLocal，每條執行緒各自的 receiveReturn 呼叫都需要重新設定。
                    TenantContext.setCurrentUser(buyerId);
                    TenantContext.setCurrentTenant(tenantId);
                    try {
                        startGun.await();
                        returnRequestService.receiveReturn(returnId, receiveRequest);
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    } finally {
                        TenantContext.clear();
                    }
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();

            int succeeded = 0;
            for (java.util.concurrent.Future<Boolean> f : results) {
                if (Boolean.TRUE.equals(f.get(60, java.util.concurrent.TimeUnit.SECONDS))) {
                    succeeded++;
                }
            }
            assertThat(succeeded)
                    .as("5 個併發請求中，應恰好只有 1 個真正成功轉換 APPROVED->RECEIVED")
                    .isEqualTo(1);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        }

        // 核心斷言：即使 5 個併發請求都嘗試收貨，庫存也只能被淨加回一次（+3），不是 +6/+9/.../+15
        assertThat(totalQty()).isEqualTo(INITIAL_STOCK + 3);
        assertThat(movementCount())
                .as("只應有 1 筆 RETURN 台帳紀錄，不是每個成功/半成功的呼叫各留一筆")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("IT-M05-RETURN-009: 訂單尚未送達不得申請退貨")
    void createReturnRequest_orderNotDelivered_throwsE5019() {
        seedOrder("CREATED");

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(createRequest(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_5019);
    }

    @Test
    @DisplayName("IT-M05-RETURN-010: 他租戶不得核准本租戶的退貨單（IDOR）")
    void approveReturn_otherTenant_throwsE1007() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(2)).getId();
        TenantContext.setCurrentTenant(UUID.randomUUID());

        assertThatThrownBy(() -> returnRequestService.approveReturn(returnId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("IT-M05-RETURN-012: 品項回應帶 SKU 編號／品名（DEF-069：店家審核台曾只能顯示截斷 orderItemId）")
    void createReturnRequest_itemResponseIncludesSkuDisplayInfo() {
        ReturnDto.Response response = returnRequestService.createReturnRequest(createRequest(2));

        ReturnDto.ItemResponse item = response.getItems().get(0);
        assertThat(item.getSkuCode()).isEqualTo("SKU-RETURN-" + runStamp);
        assertThat(item.getProductName()).isEqualTo("退貨測試商品");
    }

    @Test
    @DisplayName("IT-M05-RETURN-011: 買家撤回申請 → CANCELLED，該數量重新可申請")
    void cancelReturnRequest_freesUpQuantity() {
        UUID returnId = returnRequestService.createReturnRequest(createRequest(5)).getId();

        assertThat(returnRequestService.cancelReturnRequest(returnId).getStatus()).isEqualTo("CANCELLED");
        assertThat(returnRequestService.createReturnRequest(createRequest(5)).getStatus())
                .isEqualTo("REQUESTED");
    }

    // ── 固件 ──────────────────────────────────────────────────

    private ReturnDto.CreateRequest createRequest(final int quantity) {
        return ReturnDto.CreateRequest.builder()
                .orderId(orderId)
                .reason("商品與描述不符")
                .items(List.of(ReturnDto.CreateItem.builder()
                        .orderItemId(orderItemId).quantity(quantity).build()))
                .build();
    }

    private int totalQty() {
        Integer qty = jdbcTemplate.queryForObject(
                "SELECT total_qty FROM product_inventory WHERE sku_id = ?", Integer.class, skuId);
        return qty == null ? 0 : qty;
    }

    private List<Map<String, Object>> movements() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM stock_movements WHERE sku_id = ? ORDER BY created_at", skuId);
    }

    private int movementCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE sku_id = ?", Integer.class, skuId);
        return count == null ? 0 : count;
    }

    private void seedTenantBuyerListing() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Return Tenant")
                .slug("return-" + runStamp)
                .contactEmail("return-" + runStamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        User buyer = userRepository.save(User.builder()
                .email("return-buyer-" + runStamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Return Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
        buyerId = buyer.getId();

        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(buyer)
                .listingType(Listing.ListingType.PRODUCT)
                .title("退貨測試商品")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    private UUID seedSkuWithInventory(final int totalQty) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, id, listingId, "SKU-RETURN-" + runStamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, 0, 10, 0, NOW())
                """, id, totalQty);
        return id;
    }

    /** 種一張指定狀態的訂單（含一個品項）；重複呼叫會換成新的一張。 */
    private void seedOrder(final String status) {
        orderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', ?, 500.00, 0.00, 0.00, 'TWD', NOW(), NOW())
                """, orderId, tenantId, buyerId, status);
        orderItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, listing_id, sku_id, quantity, unit_price, subtotal,
                                         created_at)
                VALUES (?, ?, ?, ?, ?, 100.00, 500.00, NOW())
                """, orderItemId, orderId, listingId, skuId, ORDER_QTY);
    }
}
