package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.core.payment.RefundProcessingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 退款時「跨結算週期調整單」處理失敗會怎樣（DEF-452，Sprint 327）。
 *
 * <p>{@code PaymentStateService} 有三個呼叫點（訂單核心、訂房系統退款、訂房人工退款）在退款的 {@code @Transactional} 方法裡呼叫
 * {@code SettlementAdjustmentService.handleOrderRefund}／{@code handleBookingRefund}，原本用 {@code try/catch} 吞掉例外，註解寫
 * 『失敗不應影響已完成的退款主流程』。但被呼叫的方法自己也是 {@code @Transactional}（{@code REQUIRED}，加入呼叫者的交易），例外一穿出它的
 * 交易代理，共用的交易就被標成 rollback-only——吞掉例外救不了退款。
 *
 * <p><b>本類別鎖住的是實際、也是正確的契約：調整單與退款在同一個交易原子提交。</b>調整單處理失敗 ⇒ 整個退款回滾
 * （付款、訂單、狀態紀錄、稽核、結算單全都不留半套）、呼叫者看到<em>真正的失敗原因</em>（不是泛稱的 rollback-only）、自動退款的失敗稽核
 * 也記真正原因，而且資料修好後重試不會多退（Stripe 冪等鍵相同）。PRD §6.2.1 把調整單列為退款涉及已結算單時『系統自動生成』
 * 的必然後果，{@code OrderService.compensateCancellation}（DEF-303）對同一種形狀的結論也是不吞。
 *
 * <p><b>限定：本類別的『重試收斂』不經過 webhook。</b>Stripe 付款在本地回滾後，{@code charge.refunded} webhook 通常幾秒內就到，
 * {@code markStripeRefunded} 會把訂單與付款標成 {@code REFUNDED}（既不記 {@code refunded_amount}、也不呼叫調整單處理），之後排程不再重試，
 * 調整單就漏了——實測見 Sprint 327 計畫書附錄 A，登記為 DEF-453。
 *
 * <p>做法：真實 PostgreSQL＋真實 Redis，走真實的 {@code PaymentStateService}／{@code SettlementAdjustmentService}（含各自的交易代理）與
 * {@code RefundProcessingService}；只有 Stripe 閘道以 {@code @MockBean} 取代。<b>不用任何測試替身讓調整單服務拋例外</b>——mock 沒有交易代理，
 * 看不到 rollback-only（既有單元測試正是這樣『證明』承諾）。改用真實程式碼會自然失敗的資料：
 * <ul>
 *   <li><b>資料庫層（T-DB）</b>：結算單 {@code total_refunds} 設在 {@code DECIMAL(14,2)} 的上限附近，真實的 {@code applyRefundDeduction}
 *       因 {@code numeric field overflow} 失敗，PostgreSQL 交易中止。</li>
 *   <li><b>Java 層（T-NPE）</b>：結算單 {@code status} 為 NULL，真實的 {@code switch} 拋 {@code NullPointerException}；此時 PostgreSQL
 *       交易<em>沒有</em>中止，只剩 Spring 的 rollback-only 標記讓退款失敗。</li>
 * </ul>
 * 這兩種都是<strong>合成的資料異常</strong>，不代表正式環境會這樣發生（見 Sprint 327 計畫書 §7）。
 *
 * <p>共用測試資料庫可能有其他測試留下的 {@code REFUNDING} 訂單與待退款訂房，排程會一併處理它們，所以斷言都針對本測試自己建立的資料，
 * Stripe 呼叫也一律以本測試的 PaymentIntent 過濾。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-REFUND-ADJUSTMENT-FAILURE: 退款時調整單處理失敗（DEF-452，Sprint 327）")
class RefundSettlementAdjustmentFailureRealStackIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final int STOCK = 100;
    private static final int QTY = 3;
    /** {@code DECIMAL(14,2)} 的上限是 999999999999.99；再加上任何大於 0.99 的退款金額都會 numeric field overflow。 */
    private static final String NEAR_OVERFLOW = "999999999999.00";
    /** 種子結算單的淨額（GMV 10000、抽成 1000）；退款入帳後應該是 9000 − 退款金額。 */
    private static final BigDecimal SEED_NET = new BigDecimal("9000.00");
    private static final String ROLLBACK_ONLY = "rollback-only";

    @Autowired private RefundProcessingService refundProcessingService;
    @Autowired private OrderService orderService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private PaymentWebhookService paymentWebhookService;
    @Autowired private RedisCartService cartService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private Tenant tenant;
    private User seller;
    private User buyer;
    private User admin;
    private UUID listingId;
    private UUID skuId;
    private UUID roomListingId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        seed();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        // 共用資料庫：做成退款失敗的待退款訂單／訂房若留著，之後每個類別的排程都要再試一次，清掉
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE tenant_id = ? AND status = 'REFUNDING'",
                tenant.getId());
        jdbcTemplate.update("UPDATE bookings SET refund_status = 'NONE' WHERE tenant_id = ? AND refund_status = 'PENDING'",
                tenant.getId());
    }

    // ── 對照：沒有異常 ───────────────────────────────────────────

    /**
     * 對照組：證明固件確實走到調整單、而且成功時直接扣款與退款在同一個交易提交。它一失敗，下面的失敗案例就不能歸因於『調整單失敗』。
     */
    @Test
    @DisplayName("C0 對照（沒有異常）：被結算單認領的訂單自動退款成功 → 退款與結算單扣款一起提交，沒有調整單")
    void control_healthyStatement_refundDeductsFromTheStatementInTheSameTransaction() {
        UUID orderId = buyerPlacesOrder();
        buyerPaysOrderWithMock(orderId);
        buyerCancels(orderId);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = seedStatement("PENDING_REVIEW", "0.00");
        settleOrder(orderId, statementId);

        runOrderSweeper(Instant.now());

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        Map<String, Object> statement = statementRow(statementId);
        assertThat((BigDecimal) statement.get("total_refunds")).isEqualByComparingTo(total);
        assertThat((BigDecimal) statement.get("net_settlement_amount")).isEqualByComparingTo(SEED_NET.subtract(total));
        assertThat(adjustmentCount(statementId)).as("PENDING_REVIEW 的結算單直接扣款，不產生調整單").isZero();
    }

    // ── 訂單核心（refundOrderPaymentCore）────────────────────────

    @Test
    @DisplayName("O1 自動退款（訂單、Stripe）：調整單在資料庫層失敗 → 整個退款回滾、失敗稽核記真正原因；資料修好後重試完成，冪等鍵相同")
    void systemOrderRefund_adjustmentFailsAtTheDatabase_rollsBackTheWholeRefund_andRetryConverges() {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        stripePaid(orderId, pi);
        buyerCancels(orderId);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = seedStatement("PENDING_REVIEW", NEAR_OVERFLOW);
        settleOrder(orderId, statementId);
        assertThat(orderTenant(orderId)).as("前提：訂單的租戶就是結算單的租戶，調整單才會去定位到它")
                .isEqualTo(tenant.getId());
        stripeRefundsSucceed();
        Instant t0 = Instant.now();

        runOrderSweeper(t0);

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        SoftAssertions.assertSoftly(softly -> {
            assertOrderRefundRolledBack(softly, orderId, "REFUNDING");
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds"))
                    .as("結算單沒有被動到").isEqualByComparingTo(NEAR_OVERFLOW);
            softly.assertThat(adjustmentCount(statementId)).isZero();
            softly.assertThat(auditCount("AUTO_REFUND_FAILED", orderId)).as("留一筆失敗稽核").isEqualTo(1);
            softly.assertThat(failureReason(orderId)).as("失敗稽核記的是真正原因，不是泛稱的 rollback-only")
                    .contains("numeric field overflow").doesNotContain(ROLLBACK_ONLY);
            softly.assertThat(refundNotices()).as("錢沒退成，不能通知買家已退款").isEmpty();
        });

        // 資料修好 → 退避結束後重試：同一把冪等鍵（Stripe 端不會多退）、退款完成、結算單被扣
        jdbcTemplate.update("UPDATE settlement_statements SET total_refunds = 0.00 WHERE id = ?", statementId);
        runOrderSweeper(t0.plus(Duration.ofMinutes(6)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(paymentGatewayFactory, times(2)).processRefund(eq("STRIPE"), eq(pi), any(), any(), key.capture());
        assertThat(key.getAllValues()).as("重試用同一把冪等鍵").hasSize(2)
                .containsOnly("refund-" + pi + "-0.00-" + total.setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
        Map<String, Object> statement = statementRow(statementId);
        assertThat((BigDecimal) statement.get("total_refunds")).isEqualByComparingTo(total);
        assertThat((BigDecimal) statement.get("net_settlement_amount")).isEqualByComparingTo(SEED_NET.subtract(total));
        assertThat(auditCount("ORDER_PAYMENT_REFUNDED", paymentId(orderId))).as("成功的那一次才留退款稽核").isEqualTo(1);
        assertThat(refundNotices()).as("重試成功後通知一次").hasSize(1);
    }

    @Test
    @DisplayName("O2 使用者退款（訂單、Mock）：調整單拋 Java 例外（PostgreSQL 交易沒有中止）→ 呼叫者看到原本的例外、整個退款回滾；狀態修好後重試完成")
    void userOrderRefund_adjustmentThrowsInsideTheTransaction_callerSeesTheOriginalFailure() {
        UUID orderId = buyerPlacesOrder();
        buyerPaysOrderWithMock(orderId);
        UUID statementId = seedStatement(null, "0.00");
        settleOrder(orderId, statementId);
        assertThat(orderTenant(orderId)).as("前提：訂單的租戶就是結算單的租戶").isEqualTo(tenant.getId());
        BigDecimal refund = new BigDecimal("100.00");

        asAdmin();
        Throwable thrown = catchThrowable(() -> paymentStateService.refundOrderPayment(orderId, refund, "客服核准"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(thrown).as("呼叫者看到的是調整單處理真正的失敗（結算單狀態為 NULL 的 NPE），不是提交時泛稱的 rollback-only")
                    .isInstanceOf(NullPointerException.class);
            assertOrderRefundRolledBack(softly, orderId, "PAID");
            softly.assertThat(adjustmentCount(statementId)).isZero();
        });

        jdbcTemplate.update("UPDATE settlement_statements SET status = 'PENDING_REVIEW' WHERE id = ?", statementId);
        asAdmin();
        paymentStateService.refundOrderPayment(orderId, refund, "客服核准");

        assertThat(paymentStatus(orderId)).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(refund);
        Map<String, Object> statement = statementRow(statementId);
        assertThat((BigDecimal) statement.get("total_refunds")).isEqualByComparingTo(refund);
        assertThat((BigDecimal) statement.get("net_settlement_amount")).isEqualByComparingTo(SEED_NET.subtract(refund));
    }

    // ── 訂房系統退款（refundBookingPaymentAsSystem）───────────────

    @Test
    @DisplayName("B1 自動退款（訂房）：調整單在資料庫層失敗 → 整個退款回滾、失敗稽核記真正原因；資料修好後重試完成，結算單被扣")
    void systemBookingRefund_adjustmentFailsAtTheDatabase_rollsBackTheWholeRefund_andRetryConverges() {
        UUID bookingId = buyerBooks();
        buyerPaysBookingWithMock(bookingId);
        buyerCancelsBooking(bookingId);
        BigDecimal paid = bookingTotal(bookingId);
        assertThat(refundStatus(bookingId)).as("前提：全額退款已決定、等排程退").isEqualTo("PENDING");
        UUID statementId = seedStatement("PENDING_REVIEW", NEAR_OVERFLOW);
        settleBooking(bookingId, statementId);
        assertThat(bookingTenant(bookingId)).as("前提：訂房的租戶就是結算單的租戶").isEqualTo(tenant.getId());
        Instant t0 = Instant.now();

        runBookingSweeper(t0);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(refundStatus(bookingId)).as("退款狀態仍待處理").isEqualTo("PENDING");
            assertBookingPaymentUntouched(softly, bookingId);
            softly.assertThat(auditCount("BOOKING_PAYMENT_REFUNDED", paymentIdOfBooking(bookingId))).isZero();
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds"))
                    .as("結算單沒有被動到").isEqualByComparingTo(NEAR_OVERFLOW);
            softly.assertThat(adjustmentCount(statementId)).isZero();
            softly.assertThat(auditCount("AUTO_REFUND_FAILED", bookingId)).as("留一筆失敗稽核").isEqualTo(1);
            softly.assertThat(failureReason(bookingId)).as("失敗稽核記的是真正原因，不是泛稱的 rollback-only")
                    .contains("numeric field overflow").doesNotContain(ROLLBACK_ONLY);
        });

        jdbcTemplate.update("UPDATE settlement_statements SET total_refunds = 0.00 WHERE id = ?", statementId);
        runBookingSweeper(t0.plus(Duration.ofMinutes(6)));

        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
        assertThat(bookingPaymentStatus(bookingId)).isEqualTo("REFUNDED");
        Map<String, Object> statement = statementRow(statementId);
        assertThat((BigDecimal) statement.get("total_refunds")).as("訂房退款也扣在結算單上").isEqualByComparingTo(paid);
        assertThat((BigDecimal) statement.get("net_settlement_amount")).isEqualByComparingTo(SEED_NET.subtract(paid));
    }

    // ── 訂房人工退款（refundBookingPaymentManually）──────────────

    @Test
    @DisplayName("B2 管理員人工退款（訂房）：調整單拋 Java 例外 → 呼叫者看到原本的例外、整個退款回滾；狀態修好後重試完成，結算單被扣")
    void manualBookingRefund_adjustmentThrowsInsideTheTransaction_callerSeesTheOriginalFailure() {
        UUID bookingId = buyerBooks();
        buyerPaysBookingWithMock(bookingId);
        BigDecimal paid = bookingTotal(bookingId);
        // 模擬未入住（no-show）由系統取消、依政策不退款（refund_status = NONE），之後管理員核實為店家漏標
        jdbcTemplate.update("UPDATE bookings SET status = 'CANCELLED', cancelled_by = 'SYSTEM', refund_status = 'NONE' "
                + "WHERE id = ?", bookingId);
        UUID statementId = seedStatement(null, "0.00");
        settleBooking(bookingId, statementId);
        assertThat(bookingTenant(bookingId)).as("前提：訂房的租戶就是結算單的租戶").isEqualTo(tenant.getId());

        asAdmin();
        Throwable thrown = catchThrowable(() -> paymentStateService.refundBookingPaymentManually(bookingId, null, "店家漏標入住"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(thrown).as("呼叫者看到的是調整單處理真正的失敗，不是提交時泛稱的 rollback-only")
                    .isInstanceOf(NullPointerException.class);
            softly.assertThat(refundStatus(bookingId)).as("退款狀態沒有被推進").isEqualTo("NONE");
            assertBookingPaymentUntouched(softly, bookingId);
            softly.assertThat(auditCount("BOOKING_MANUAL_REFUND", paymentIdOfBooking(bookingId))).isZero();
            softly.assertThat(adjustmentCount(statementId)).isZero();
        });

        jdbcTemplate.update("UPDATE settlement_statements SET status = 'PENDING_REVIEW' WHERE id = ?", statementId);
        asAdmin();
        paymentStateService.refundBookingPaymentManually(bookingId, null, "店家漏標入住");

        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
        assertThat(bookingPaymentStatus(bookingId)).isEqualTo("REFUNDED");
        Map<String, Object> statement = statementRow(statementId);
        assertThat((BigDecimal) statement.get("total_refunds")).as("人工退款也扣在結算單上").isEqualByComparingTo(paid);
        assertThat((BigDecimal) statement.get("net_settlement_amount")).isEqualByComparingTo(SEED_NET.subtract(paid));
    }

    // ── 共用斷言 ─────────────────────────────────────────────────

    /** 訂單版『退款整個沒發生』：付款仍是原狀、沒有退款稽核與狀態紀錄、Stripe 退款 id 沒存。 */
    private void assertOrderRefundRolledBack(final SoftAssertions softly, final UUID orderId, final String expectedOrderStatus) {
        softly.assertThat(orderStatus(orderId)).as("訂單狀態沒有被推進").isEqualTo(expectedOrderStatus);
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, refunded_amount, stripe_refund_id FROM payments WHERE order_id = ? "
                        + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", orderId);
        softly.assertThat(payment.get("status")).as("付款沒有被標成已退款").isEqualTo("SUCCESS");
        softly.assertThat((BigDecimal) payment.get("refunded_amount")).as("累計退款額沒有被推進").isEqualByComparingTo("0");
        softly.assertThat(payment.get("stripe_refund_id")).isNull();
        softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).as("沒有 REFUNDED 狀態紀錄").isZero();
        softly.assertThat(auditCount("ORDER_PAYMENT_REFUNDED", paymentId(orderId))).as("沒有退款稽核（與退款同一個交易）").isZero();
    }

    private void assertBookingPaymentUntouched(final SoftAssertions softly, final UUID bookingId) {
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, refunded_amount FROM payments WHERE booking_id = ? "
                        + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", bookingId);
        softly.assertThat(payment.get("status")).as("付款沒有被標成已退款").isEqualTo("SUCCESS");
        softly.assertThat((BigDecimal) payment.get("refunded_amount")).as("累計退款額沒有被推進").isEqualByComparingTo("0");
    }

    // ── 固件：結算單 ─────────────────────────────────────────────

    /** 一張結算單（GMV 10000、抽成 1000、淨額 9000）；{@code status} 可為 null（Java 層失敗的固件）。 */
    private UUID seedStatement(final String status, final String totalRefunds) {
        UUID statementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-21', DATE '2026-12-27', 1, 10000.00, ?, 1000.00, 9000.00,
                        'TWD', ?, NOW(), NOW(), NOW())
                """, statementId, tenant.getId(), "IT-S327-" + System.nanoTime(), new BigDecimal(totalRefunds), status);
        return statementId;
    }

    private void settleOrder(final UUID orderId, final UUID statementId) {
        jdbcTemplate.update("UPDATE orders SET settled_statement_id = ? WHERE id = ?", statementId, orderId);
    }

    private void settleBooking(final UUID bookingId, final UUID statementId) {
        jdbcTemplate.update("UPDATE bookings SET settled_statement_id = ? WHERE id = ?", statementId, bookingId);
    }

    private Map<String, Object> statementRow(final UUID statementId) {
        return jdbcTemplate.queryForMap("SELECT status, total_refunds, net_settlement_amount "
                + "FROM settlement_statements WHERE id = ?", statementId);
    }

    private int adjustmentCount(final UUID statementId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM adjustment_statements WHERE original_statement_id = ?",
                Integer.class, statementId);
    }

    // ── 固件：流程 ───────────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runOrderSweeper(final Instant now) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        refundProcessingService.processPendingOrderRefunds(now);
    }

    private void runBookingSweeper(final Instant now) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        refundProcessingService.processPendingBookingRefunds(now);
    }

    private UUID buyerPlacesOrder() {
        asBuyer();
        cartService.clearCart(buyer.getId(), tenant.getId());
        cartService.addItem(buyer.getId(), tenant.getId(), CartDto.AddItemRequest.builder()
                .listingId(listingId).skuId(skuId).quantity(QTY).build());
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區信義路五段 7 號");
        request.setShippingRecipientName("測試買家");
        request.setShippingPhone("0912345678");
        return orderService.createOrderFromCart(request).getId();
    }

    private void buyerPaysOrderWithMock(final UUID orderId) {
        asBuyer();
        paymentStateService.mockPaymentSuccess(orderId);
    }

    private void buyerPaysBookingWithMock(final UUID bookingId) {
        asBuyer();
        paymentStateService.mockBookingPaymentSuccess(bookingId);
    }

    private void buyerCancels(final UUID orderId) {
        asBuyer();
        orderService.cancelOrder(orderId, "changed my mind");
    }

    /** 買家訂兩晚，入住日離今天 10 天（取消時離入住超過 24 小時 ⇒ 全額退款，記在 refund_status = PENDING）。 */
    private UUID buyerBooks() {
        asBuyer();
        LocalDate checkIn = BusinessTime.today().plusDays(10);
        return bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("Test Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .build(), null).getId();
    }

    private void buyerCancelsBooking(final UUID bookingId) {
        asBuyer();
        bookingService.cancelBooking(bookingId, "changed my mind");
    }

    /** 買家發起 Stripe 結帳（Stripe 閘道是 mock），回傳 Checkout Session id。 */
    private String startStripeCheckout(final UUID orderId) {
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(true);
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenAnswer(invocation -> {
                    String sessionId = "cs_test_" + UUID.randomUUID();
                    return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                            .sessionId(sessionId).sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId)
                            .status("open").paymentStatus("unpaid").build();
                });
        asBuyer();
        return paymentStateService.initiateStripeCheckout(orderId).getSessionId();
    }

    /** 走完真實的 Stripe 付款：結帳 → webhook 通知付款成功（訂單 PAID）。 */
    private void stripePaid(final UUID orderId, final String paymentIntentId) {
        String sessionId = startStripeCheckout(orderId);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        paymentWebhookService.handleEvent("{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"payment_intent\":\""
                + paymentIntentId + "\"}}}");
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    private void stripeRefundsSucceed() {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), anyString(), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_test_1").status("succeeded").build());
    }

    private void asBuyer() {
        act(buyer);
    }

    private void asAdmin() {
        act(admin);
    }

    private void act(final User user) {
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
                .name("Refund Adjustment Failure Tenant")
                .slug("refund-adjust-fail-" + stamp)
                .contactEmail("refund-adjust-fail-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        seller = userRepository.save(User.builder()
                .email("refund-adjust-fail-seller-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Seller").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        buyer = userRepository.save(User.builder()
                .email("refund-adjust-fail-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        admin = userRepository.save(User.builder()
                .email("refund-adjust-fail-admin-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Admin").role(User.UserRole.ADMIN).status("ACTIVE").build());
        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant).owner(seller).listingType(Listing.ListingType.PRODUCT)
                .title("Refund Adjustment Failure Product").basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE).build()).getId();
        skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-REFUNDADJ-" + stamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, 0, 10, 0, NOW())
                """, skuId, STOCK);
        roomListingId = listingRepository.save(Listing.builder()
                .tenant(tenant).owner(seller).listingType(Listing.ListingType.ROOM)
                .title("Refund Adjustment Failure Room").basePrice(new BigDecimal("1500.00"))
                .status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
    }

    // ── 查詢 ─────────────────────────────────────────────────────

    private String orderStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private UUID orderTenant(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM orders WHERE id = ?", UUID.class, orderId);
    }

    private BigDecimal orderTotal(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, orderId);
    }

    private String paymentStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", String.class, orderId);
    }

    private BigDecimal paymentRefundedAmount(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT refunded_amount FROM payments WHERE order_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", BigDecimal.class, orderId);
    }

    private UUID paymentId(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT id FROM payments WHERE order_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, orderId);
    }

    private int orderStateLogCount(final UUID orderId, final String toStatus) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_state_log WHERE order_id = ? AND to_status = ?",
                Integer.class, orderId, toStatus);
    }

    private UUID bookingTenant(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM bookings WHERE id = ?", UUID.class, bookingId);
    }

    private BigDecimal bookingTotal(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM bookings WHERE id = ?", BigDecimal.class, bookingId);
    }

    private String refundStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT refund_status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private String bookingPaymentStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE booking_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", String.class, bookingId);
    }

    private UUID paymentIdOfBooking(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT id FROM payments WHERE booking_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, bookingId);
    }

    private int auditCount(final String action, final UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, entityId, action);
    }

    /** 自動退款失敗稽核（{@code AUTO_REFUND_FAILED}）的原因文字：{@code attempt=N,nextAttemptAt=…,error=<例外訊息>}。 */
    private String failureReason(final UUID entityId) {
        List<String> reasons = jdbcTemplate.queryForList(
                "SELECT reason FROM audit_log WHERE entity_id = ? AND action = 'AUTO_REFUND_FAILED' ORDER BY created_at",
                String.class, entityId);
        return reasons.isEmpty() ? "<沒有失敗稽核>" : reasons.get(reasons.size() - 1);
    }

    /** 這個測試的買家收到的「退款完成」通知（每個測試都建新買家，不會混到共用資料庫裡別人的通知）。 */
    private List<Map<String, Object>> refundNotices() {
        return jdbcTemplate.queryForList("SELECT title FROM notifications "
                + "WHERE user_id = ? AND notification_type = 'REFUND_COMPLETED'", buyer.getId());
    }
}
