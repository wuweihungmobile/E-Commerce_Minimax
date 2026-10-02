package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.order.OrderTimeoutService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 未付款訂單逾時自動取消（Sprint 219，DEF-302；使用者拍板 24 小時）。
 *
 * <p>真實 PostgreSQL＋真實 Redis 購物車，走真實的建單、付款服務方法；「訂單已建立多久」以 JDBC 改 {@code created_at}
 * 模擬。取消用的批次查詢與條件式 UPDATE 都是 JPQL（含 NOT EXISTS 子查詢），mock Repository 的測試碰不到它們，
 * 必須在真實資料庫執行過。
 *
 * <p>共用測試資料庫裡可能有其他測試留下的舊 CREATED 訂單，所以斷言都針對本測試自己建立的訂單，不比對總張數。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-ORDER-TIMEOUT: 未付款訂單逾時自動取消（Sprint 219）")
class OrderTimeoutIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final int STOCK = 100;
    private static final int QTY = 3;

    @Autowired private OrderTimeoutService orderTimeoutService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private RedisCartService cartService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private PaymentRepository paymentRepository;

    @MockBean private FeatureToggleService featureToggleService;

    private Tenant tenant;
    private User seller;
    private User buyer;
    private UUID listingId;
    private UUID skuId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        seed();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("建立超過 24 小時仍未付款 → 取消，預留釋放、優惠券額度退還、狀態紀錄與稽核記為系統操作")
    void expiredUnpaidOrder_isCancelledAndCompensated() {
        String promo = givenPromo();
        UUID orderId = buyerPlacesOrder(promo);
        assertThat(reservedQty()).isEqualTo(QTY);
        assertThat(promoUsageCount(promo)).isEqualTo(1);
        ageOrder(orderId, Duration.ofHours(25));

        runJobAsScheduler();

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(reservedQty()).isZero();
        assertThat(totalQty()).isEqualTo(STOCK);
        assertThat(promoUsageCount(promo)).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "RELEASE");

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, changed_by, reason FROM order_state_log "
                        + "WHERE order_id = ? AND to_status = 'CANCELLED'", orderId);
        assertThat(log.get("from_status")).isEqualTo("CREATED");
        assertThat(log.get("changed_by")).as("系統取消沒有操作使用者").isNull();
        assertThat(log.get("reason")).isEqualTo("Unpaid order timed out");
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = 'ORDER_CANCELLED'",
                Integer.class, orderId);
        assertThat(audits).isEqualTo(1);

        // Sprint 229（PRD US-014）：逾時取消後買家收到站內通知，data 帶 orderId 供前端連到訂單
        List<Map<String, Object>> notices = notificationsOfBuyer();
        assertThat(notices).as("取消後通知買家一次").hasSize(1);
        assertThat(notices.get(0).get("notification_type")).isEqualTo("ORDER_CANCELLED");
        assertThat(notices.get(0).get("title")).isEqualTo("訂單因逾期未付款已取消");
        assertThat((String) notices.get(0).get("content")).contains("#" + orderId.toString().substring(0, 8),
                "超過付款期限", "重新下單");
        assertThat((String) notices.get(0).get("data")).contains(orderId.toString(), "PAYMENT_TIMEOUT");
    }

    @Test
    @DisplayName("建立未滿 24 小時 → 不動")
    void orderWithinDeadline_isUntouched() {
        UUID orderId = buyerPlacesOrder(null);
        ageOrder(orderId, Duration.ofHours(23));

        runJobAsScheduler();

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(reservedQty()).isEqualTo(QTY);
        assertThat(notificationsOfBuyer()).as("沒取消就不通知").isEmpty();
    }

    @Test
    @DisplayName("已付款的訂單即使超過 24 小時也不動")
    void paidOrder_isUntouched() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        ageOrder(orderId, Duration.ofHours(30));

        runJobAsScheduler();

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(reservedQty()).isEqualTo(QTY);
        assertThat(notificationsOfBuyer()).as("已付款的訂單沒有被取消，不能對買家說「逾期未付款已取消」").isEmpty();
    }

    @Test
    @DisplayName("24 小時內開始過 Stripe 結帳（工作階段可能仍開著）→ 不取消；工作階段也已過期才取消")
    void recentStripeCheckout_blocksCancellationUntilSessionExpires() {
        UUID orderId = buyerPlacesOrder(null);
        ageOrder(orderId, Duration.ofHours(25));
        UUID paymentId = givenProcessingStripePayment(orderId);
        agePayment(paymentId, Duration.ofHours(1));

        runJobAsScheduler();

        assertThat(orderStatus(orderId)).as("結帳工作階段 1 小時前才建立，預設 24 小時內仍可付款").isEqualTo("CREATED");
        assertThat(reservedQty()).isEqualTo(QTY);

        agePayment(paymentId, Duration.ofHours(25));
        runJobAsScheduler();

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(reservedQty()).isZero();
    }

    @Test
    @DisplayName("已有成功付款紀錄的 CREATED 訂單（異常資料）→ 不當成未付款取消")
    void orderWithSuccessfulPayment_isNeverCancelledAsUnpaid() {
        UUID orderId = buyerPlacesOrder(null);
        ageOrder(orderId, Duration.ofHours(30));
        UUID paymentId = givenProcessingStripePayment(orderId);
        jdbcTemplate.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?", paymentId);

        runJobAsScheduler();

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("取消用的條件式 UPDATE 自己也把關（不依賴候選查詢）：未逾時、已付款、24 小時內開始過結帳、已有成功付款都回 false")
    void cancelExpiredUnpaidOrder_guardsItself() {
        // 候選查詢只是挑人；買家付款、開始結帳都可能發生在「挑出來」與「取消」之間，把關必須在 UPDATE 本身
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));

        UUID fresh = buyerPlacesOrder(null);
        assertThat(orderService.cancelExpiredUnpaidOrder(fresh, cutoff)).as("未逾時").isFalse();
        assertThat(orderStatus(fresh)).isEqualTo("CREATED");

        UUID paid = buyerPlacesOrder(null);
        buyerPays(paid);
        ageOrder(paid, Duration.ofHours(30));
        assertThat(orderService.cancelExpiredUnpaidOrder(paid, cutoff)).as("已付款").isFalse();
        assertThat(orderStatus(paid)).isEqualTo("PAID");

        UUID checkingOut = buyerPlacesOrder(null);
        ageOrder(checkingOut, Duration.ofHours(25));
        agePayment(givenProcessingStripePayment(checkingOut), Duration.ofHours(1));
        assertThat(orderService.cancelExpiredUnpaidOrder(checkingOut, cutoff)).as("1 小時前開始結帳").isFalse();
        assertThat(orderStatus(checkingOut)).isEqualTo("CREATED");

        UUID succeeded = buyerPlacesOrder(null);
        ageOrder(succeeded, Duration.ofHours(30));
        jdbcTemplate.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?", givenProcessingStripePayment(succeeded));
        assertThat(orderService.cancelExpiredUnpaidOrder(succeeded, cutoff)).as("已有成功付款").isFalse();
        assertThat(orderStatus(succeeded)).isEqualTo("CREATED");

        assertThat(reservedQty()).as("四張都沒被取消，預留都還在").isEqualTo(4 * QTY);
    }

    @Test
    @DisplayName("被取消後買家再付款 → E-5011，訂單維持 CANCELLED（與付款搶同一個狀態，恰好一邊成功）")
    void payAfterTimeoutCancel_isRejected() {
        UUID orderId = buyerPlacesOrder(null);
        ageOrder(orderId, Duration.ofHours(25));
        runJobAsScheduler();
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");

        asBuyer();
        assertThatThrownBy(() -> paymentStateService.mockPaymentSuccess(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5011);
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("重複執行不會重複補償：預留只釋放一次")
    void runningTwice_compensatesOnce() {
        UUID orderId = buyerPlacesOrder(null);
        ageOrder(orderId, Duration.ofHours(25));

        runJobAsScheduler();
        runJobAsScheduler();

        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "RELEASE");
        assertThat(notificationsOfBuyer()).as("只有真的取消的那一輪通知，第二輪不再通知").hasSize(1);
    }

    // ── 固件與身分 ──────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runJobAsScheduler() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        orderTimeoutService.cancelExpiredUnpaidOrders(Instant.now());
    }

    private UUID buyerPlacesOrder(final String promoCode) {
        asBuyer();
        cartService.clearCart(buyer.getId(), tenant.getId());
        cartService.addItem(buyer.getId(), tenant.getId(), CartDto.AddItemRequest.builder()
                .listingId(listingId).skuId(skuId).quantity(QTY).build());
        if (promoCode != null) {
            cartService.applyPromoCode(buyer.getId(), tenant.getId(), null, promoCode);
        }
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區信義路五段 7 號");
        request.setShippingRecipientName("測試買家");
        request.setShippingPhone("0912345678");
        return orderService.createOrderFromCart(request).getId();
    }

    private void buyerPays(final UUID orderId) {
        asBuyer();
        paymentStateService.mockPaymentSuccess(orderId);
    }

    private UUID givenProcessingStripePayment(final UUID orderId) {
        return paymentRepository.save(Payment.builder()
                .orderId(orderId)
                .paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(new BigDecimal("300.00"))
                .currency("TWD")
                .status(Payment.PaymentStatus.PROCESSING)
                .transactionId("cs_test_" + UUID.randomUUID())
                .build()).getId();
    }

    private void ageOrder(final UUID orderId, final Duration age) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(age)), orderId);
    }

    private void agePayment(final UUID paymentId, final Duration age) {
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(age)), paymentId);
    }

    private String givenPromo() {
        String code = "TIMEOUT" + System.nanoTime();
        promoCodeRepository.save(PromoCode.builder()
                .tenant(tenant)
                .code(code)
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10.00"))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .maxUsageCount(5)
                .currentUsageCount(0)
                .maxUsagePerUser(1)
                .isActive(true)
                .build());
        return code;
    }

    private void asBuyer() {
        TenantContext.setCurrentUser(buyer.getId());
        TenantContext.setCurrentTenant(tenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(buyer.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(buyer.getId().toString(), null, authorities));
    }

    private void seed() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Order Timeout Tenant")
                .slug("order-timeout-" + stamp)
                .contactEmail("order-timeout-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        seller = userRepository.save(User.builder()
                .email("order-timeout-seller-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Order Timeout Seller")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
        buyer = userRepository.save(User.builder()
                .email("order-timeout-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Order Timeout Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(seller)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Order Timeout Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-TIMEOUT-" + stamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, 0, 10, 0, NOW())
                """, skuId, STOCK);
    }

    private int totalQty() {
        return jdbcTemplate.queryForObject("SELECT total_qty FROM product_inventory WHERE sku_id = ?",
                Integer.class, skuId);
    }

    private int reservedQty() {
        return jdbcTemplate.queryForObject("SELECT reserved_qty FROM product_inventory WHERE sku_id = ?",
                Integer.class, skuId);
    }

    private int promoUsageCount(final String code) {
        return jdbcTemplate.queryForObject("SELECT current_usage_count FROM promo_codes WHERE code = ?",
                Integer.class, code);
    }

    /** 這個測試的買家收到的通知（每個測試都建新買家，不會混到共用資料庫裡別人的通知）。 */
    private List<Map<String, Object>> notificationsOfBuyer() {
        return jdbcTemplate.queryForList("SELECT notification_type, title, content, data::text AS data "
                + "FROM notifications WHERE user_id = ? ORDER BY created_at", buyer.getId());
    }

    private String orderStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private List<String> movementTypes(final UUID orderId) {
        return jdbcTemplate.queryForList(
                "SELECT movement_type FROM stock_movements WHERE reference_id = ? ORDER BY created_at, movement_type",
                String.class, orderId);
    }
}
