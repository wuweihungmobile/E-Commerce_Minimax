package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.logistics.LogisticsService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 訂單取消的補償在每個入口都一樣，而且依 PRD 在出貨時才扣庫存（Sprint 218，DEF-301／DEF-303 (2)(4)(6)）。
 *
 * <p>真實 PostgreSQL＋真實 Redis 購物車，走真實的建單、付款、確認、出貨、取消服務方法（不以 raw SQL 繞過流程）；
 * 只有「改版前付款時就扣過帳的訂單」這個過渡情境，以 JDBC 補上當年付款時會寫下的扣帳與流水帳。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-CANCEL-COMP: 訂單取消補償與出貨扣帳（Sprint 218）")
class OrderCancellationCompensationIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final int STOCK = 100;
    private static final int QTY = 3;

    @Autowired private OrderService orderService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private LogisticsService logisticsService;
    @Autowired private RedisCartService cartService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;

    @MockBean private FeatureToggleService featureToggleService;

    private Tenant tenant;
    private User seller;
    private User buyer;
    private UUID listingId;
    private UUID skuId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        // 本類別測的是 Mock 付款之後的流程；開關全開會連 Stripe 一起開，Mock 付款就會被 DEF-299 擋下
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        seed();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── DEF-301：賣家以改狀態端點取消，補償與買家取消相同（PRD TC-M05-015）──────────

    @Test
    @DisplayName("賣家把未付款訂單改成 CANCELLED → 預留釋放、優惠券額度退還")
    void sellerCancelsUnpaidOrder_releasesReservationAndPromo() {
        String promo = givenPromo();
        UUID orderId = buyerPlacesOrder(promo);
        assertThat(reservedQty()).isEqualTo(QTY);
        assertThat(promoUsageCount(promo)).isEqualTo(1);

        asSeller();
        OrderDto.OrderResponse cancelled = orderService.updateOrderStatus(orderId, "CANCELLED", "out of stock");

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(reservedQty()).isZero();
        assertThat(totalQty()).isEqualTo(STOCK);
        assertThat(promoUsageCount(promo)).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "RELEASE");
    }

    // ── DEF-303 (6)：出貨前取消已付款訂單，庫存回到可售 ──────────

    @Test
    @DisplayName("付款不動總量；買家付款後取消 → 預留釋放、總量不變，進入退款中")
    void buyerCancelsPaidOrder_releasesReservation() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        // PRD §6.7.3：扣帳發生在出貨，付款只是把預留保留到出貨
        assertThat(totalQty()).isEqualTo(STOCK);
        assertThat(reservedQty()).isEqualTo(QTY);

        asBuyer();
        OrderDto.OrderResponse cancelled = orderService.cancelOrder(orderId, "changed mind");

        assertThat(cancelled.getStatus()).isEqualTo("REFUNDING");
        assertThat(totalQty()).isEqualTo(STOCK);
        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "RELEASE");
    }

    @Test
    @DisplayName("DEF-303 (4)：買家取消已確認（已付款）訂單 → 進入退款中，不是只停在 CANCELLED")
    void buyerCancelsConfirmedOrder_goesToRefunding() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        asSeller();
        orderService.updateOrderStatus(orderId, "CONFIRMED", "accepted");

        asBuyer();
        OrderDto.OrderResponse cancelled = orderService.cancelOrder(orderId, "changed mind");

        assertThat(cancelled.getStatus()).isEqualTo("REFUNDING");
        assertThat(reservedQty()).isZero();
        assertThat(totalQty()).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("賣家取消已付款訂單（PAID→CANCELLED）→ 補償後進入退款中")
    void sellerCancelsPaidOrder_goesToRefundingWithCompensation() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);

        asSeller();
        OrderDto.OrderResponse cancelled = orderService.updateOrderStatus(orderId, "CANCELLED", "cannot fulfil");

        assertThat(cancelled.getStatus()).isEqualTo("REFUNDING");
        assertThat(reservedQty()).isZero();
        assertThat(totalQty()).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("退款中只能由取消流程進入：改狀態端點直接指定 REFUNDING → E-5001，訂單不動")
    void patchToRefunding_isRejected() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);

        asSeller();
        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, "REFUNDING", "skip compensation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5001);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(reservedQty()).isEqualTo(QTY);
    }

    // ── 出貨才扣帳（PRD §6.7.3「訂單出貨 → OUTBOUND」）──────────

    @Test
    @DisplayName("建立物流（CONFIRMED→SHIPPING）時才扣帳：總量與預留同時遞減一次")
    void shipmentDeductsStockOnce() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        asSeller();
        orderService.updateOrderStatus(orderId, "CONFIRMED", "accepted");

        logisticsService.createLogistics(LogisticsDto.CreateRequest.builder()
                .orderId(orderId)
                .logisticsProvider(LogisticsDto.LogisticsProvider.HCT)
                .build());

        assertThat(orderStatus(orderId)).isEqualTo("SHIPPING");
        assertThat(totalQty()).isEqualTo(STOCK - QTY);
        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "OUTBOUND");
    }

    @Test
    @DisplayName("以改狀態端點出貨（CONFIRMED→SHIPPING）同樣扣帳")
    void patchToShippingDeductsStock() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        asSeller();
        orderService.updateOrderStatus(orderId, "CONFIRMED", "accepted");

        orderService.updateOrderStatus(orderId, "SHIPPING", "shipped");

        assertThat(totalQty()).isEqualTo(STOCK - QTY);
        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "OUTBOUND");
    }

    // ── 過渡：改版前付款時就已扣帳的訂單 ──────────

    @Test
    @DisplayName("改版前付款即扣帳的訂單被取消 → 以 ADJUST_PLUS 把總量加回，不再動預留")
    void legacyDeductedOrderCancelled_reversesDeduction() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        simulateLegacyDeductionAtPayment(orderId);
        assertThat(totalQty()).isEqualTo(STOCK - QTY);
        assertThat(reservedQty()).isZero();

        asBuyer();
        orderService.cancelOrder(orderId, "changed mind");

        assertThat(totalQty()).isEqualTo(STOCK);
        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "OUTBOUND", "ADJUST_PLUS");
    }

    @Test
    @DisplayName("改版前付款即扣帳的訂單出貨 → 不重複扣帳")
    void legacyDeductedOrderShipped_doesNotDeductTwice() {
        UUID orderId = buyerPlacesOrder(null);
        buyerPays(orderId);
        simulateLegacyDeductionAtPayment(orderId);
        asSeller();
        orderService.updateOrderStatus(orderId, "CONFIRMED", "accepted");

        orderService.updateOrderStatus(orderId, "SHIPPING", "shipped");

        assertThat(totalQty()).isEqualTo(STOCK - QTY);
        assertThat(reservedQty()).isZero();
        assertThat(movementTypes(orderId)).containsExactly("RESERVE", "OUTBOUND");
    }

    // ── 固件與身分 ──────────────────────────────────────────

    private UUID buyerPlacesOrder(final String promoCode) {
        asBuyer();
        cartService.clearCart(buyer.getId(), tenant.getId());
        cartService.addItem(buyer.getId(), tenant.getId(), CartDto.AddItemRequest.builder()
                .listingId(listingId).skuId(skuId).quantity(QTY).build());
        if (promoCode != null) {
            cartService.applyPromoCode(buyer.getId(), tenant.getId(), promoCode);
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

    /**
     * 改版前（Sprint 88～217）付款成功時會呼叫 deductForOrder：total 與 reserved 同時扣掉、寫一筆 OUTBOUND。
     * 這裡照當年的 SQL 補上，模擬「付款在改版前、取消或出貨在改版後」的訂單。
     */
    private void simulateLegacyDeductionAtPayment(final UUID orderId) {
        UUID orderItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", UUID.class, orderId);
        jdbcTemplate.update("""
                UPDATE product_inventory SET total_qty = total_qty - ?, reserved_qty = GREATEST(reserved_qty - ?, 0),
                       version = version + 1 WHERE sku_id = ?
                """, QTY, QTY, skuId);
        jdbcTemplate.update("""
                INSERT INTO stock_movements (id, tenant_id, sku_id, movement_type, quantity, before_total_qty,
                        after_total_qty, balance_after, before_reserved_qty, after_reserved_qty, reference_type,
                        reference_id, order_item_id, notes, created_by, created_at)
                VALUES (?, ?, ?, 'OUTBOUND', ?, ?, ?, ?, ?, 0, 'ORDER', ?, ?, 'Order outbound', ?, NOW())
                """, UUID.randomUUID(), tenant.getId(), skuId, QTY, STOCK, STOCK - QTY, STOCK - QTY, QTY,
                orderId, orderItemId, buyer.getId());
    }

    private String givenPromo() {
        String code = "CANCEL" + System.nanoTime();
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
        as(buyer);
    }

    private void asSeller() {
        as(seller);
    }

    /** 身分與權限都用生產的來源：TenantContext 取使用者與租戶，權限取自生產的 RolePermissionMapping。 */
    private void as(final User user) {
        TenantContext.setCurrentUser(user.getId());
        TenantContext.setCurrentTenant(tenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(user.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId().toString(), null, authorities));
    }

    private void seed() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Cancel Comp Tenant")
                .slug("cancel-comp-" + stamp)
                .contactEmail("cancel-comp-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        seller = userRepository.save(User.builder()
                .email("cancel-comp-seller-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Cancel Comp Seller")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
        buyer = userRepository.save(User.builder()
                .email("cancel-comp-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Cancel Comp Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(seller)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Cancel Comp Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-CANCEL-" + stamp);
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

    private String orderStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private List<String> movementTypes(final UUID orderId) {
        return jdbcTemplate.queryForList(
                "SELECT movement_type FROM stock_movements WHERE reference_id = ? ORDER BY created_at, movement_type",
                String.class, orderId);
    }
}
