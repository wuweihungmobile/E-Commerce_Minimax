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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.SoftAssertions;
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
 * Stripe {@code charge.refunded} webhook 完結的退款，必須記在付款與結算單上（DEF-453，Sprint 342）。
 *
 * <p><b>為什麼這件事重要</b>：已出貨（{@code SHIPPED}／{@code DELIVERED}／{@code COMPLETED}）的 Stripe 訂單，程式裡沒有任何入口能退款
 * （訂單退款端點與自動退款只收 {@code PAID}／{@code CANCELLED}／{@code REFUNDING}；退貨域沒有退款步驟；Mock 退款端點明確拒絕 Stripe
 * 付款）——Stripe 後台操作、再由這個 webhook 通知，是這類訂單把錢退回買家的唯一路徑（Sprint 330 補量測）。修復前這條路徑只把付款標成
 * {@code REFUNDED}：不記 {@code refunded_amount}、不看金額（部分退款被當成全額）、不呼叫結算調整，賣家因此保留已退還給買家的款項。
 *
 * <p><b>修法的語意（本類別鎖住的契約）</b>：以 Stripe 端的<em>累計值</em> {@code amount_refunded} 當真相來源，與本地
 * {@code payments.refunded_amount} 的<em>差額</em>才是本次退款。差額 ≤ 0（事件重送、API 路徑已先記帳、亂序到達的舊事件）什麼都不做；
 * 差額 &gt; 0 才在同一個交易內記帳並扣結算單。這讓「API 先到／webhook 先到／兩者併發」三種順序都收斂到同一個結果，不重複扣款。
 *
 * <p>做法：真實 PostgreSQL＋真實 Redis，走真實的 {@code PaymentWebhookService}／{@code PaymentStateService}／{@code SettlementAdjustmentService}
 * （含各自的交易代理）；只有 Stripe 閘道以 {@code @MockBean} 取代。固件沿用 Sprint 327
 * {@code RefundSettlementAdjustmentFailureRealStackIntegrationTest}：真實的 {@code checkout.session.completed} webhook 讓訂單 {@code PAID}；
 * 「被結算單認領」與 {@code COMPLETED} 用 SQL 種（正式環境的認領由週結算完成，這裡是合成的）。webhook 內容是手寫的 {@code charge.refunded} JSON。
 *
 * <p>共用測試資料庫可能有其他測試留下的 {@code REFUNDING} 訂單，排程會一併處理，所以斷言都針對本測試自己建立的資料，
 * Stripe 呼叫也一律以本測試的 PaymentIntent 過濾。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-STRIPE-REFUND-WEBHOOK: charge.refunded webhook 的退款記帳（DEF-453，Sprint 342）")
class StripeRefundWebhookAccountingRealStackIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final String WEBHOOK_AUDIT = "STRIPE_PAYMENT_REFUNDED_WEBHOOK";
    private static final int STOCK = 100;
    private static final int QTY = 3;
    /** {@code DECIMAL(14,2)} 的上限是 999999999999.99；再加上任何大於 0.99 的退款金額都會 numeric field overflow。 */
    private static final String NEAR_OVERFLOW = "999999999999.00";
    /** 種子結算單的淨額（GMV 10000、抽成 1000）。 */
    private static final BigDecimal SEED_NET = new BigDecimal("9000.00");
    private static final int ROUNDS = 8;

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
        // 共用資料庫：A3 把訂單留在 REFUNDING 以外的終態，其餘案例若中途失敗也不要留下待退款訂單讓別的類別的排程去處理
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE tenant_id = ? AND status = 'REFUNDING'",
                tenant.getId());
    }

    // ── 對照組：證明固件可用，也鎖住「不該變」的既有語意 ───────────────

    /**
     * 對照（修復前後都該綠）：未結算的 PAID 訂單收到全額 {@code charge.refunded}——付款 REFUNDED、訂單 REFUNDED（一筆狀態紀錄）、
     * 一筆 webhook 稽核。這是修復前就有的行為，修法不能弄丟。
     */
    @Test
    @DisplayName("R0 對照：未結算的 PAID 訂單收到全額 → 付款與訂單都 REFUNDED，一筆狀態紀錄、一筆稽核")
    void r0_control_fullRefundOfAnUnsettledPaidOrder_keepsExistingStateTransitions() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        long total = cents(orderTotal(orderId));

        deliver(chargeRefunded(pi, total, total));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("R1 未結算訂單也要記 refunded_amount：之後結算（SettlementGenerator 讀這個欄位）才不會多付給賣家")
    void r1_fullRefundOfAnUnsettledOrder_recordsTheRefundedAmount() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);

        deliver(chargeRefunded(pi, cents(total), cents(total)));

        assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
    }

    // ── 結算單：已出貨訂單（Stripe 後台退款是唯一路徑）─────────────────

    @Test
    @DisplayName("P1 COMPLETED、被 PENDING_REVIEW 結算單認領，全額 → 付款 REFUNDED、refunded_amount＝總額、結算單直接扣款；訂單狀態不動")
    void p1_fullRefundOfACompletedOrder_deductsFromAPendingStatement() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        markCompleted(orderId);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = claim(orderId, "PENDING_REVIEW");

        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).as("付款說退了多少，不能是 0").isEqualByComparingTo(total);
            softly.assertThat(orderStatus(orderId)).as("已出貨的訂單沿用 canRefund 規則，狀態不動").isEqualTo("COMPLETED");
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isZero();
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("net_settlement_amount"))
                    .isEqualByComparingTo(SEED_NET.subtract(total));
            softly.assertThat(adjustmentCount(statementId)).as("PENDING_REVIEW 直接扣款，不產生調整單").isZero();
        });
    }

    @Test
    @DisplayName("P1b 同 P1，結算單已 PAID（款項已撥）→ 結算單不動、產生一筆 −總額 的調整單，下一期折入")
    void p1b_fullRefundOfACompletedOrder_createsAnAdjustmentForAPaidStatement() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        markCompleted(orderId);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = claim(orderId, "PAID");

        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo("0");
            softly.assertThat(adjustmentCount(statementId)).isEqualTo(1);
            softly.assertThat(adjustmentSum(statementId)).isEqualByComparingTo(total.negate());
        });
    }

    @Test
    @DisplayName("P2 部分退款（1/3）→ 付款 PARTIALLY_REFUNDED、refunded_amount＝1/3、訂單不動（不是整筆退款）、結算單只扣 1/3")
    void p2_partialRefund_isNotTreatedAsAFullRefund() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");

        deliver(chargeRefunded(pi, cents(third), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("PARTIALLY_REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(third);
            softly.assertThat(orderStatus(orderId)).as("部分退款訂單持續履約，狀態不動").isEqualTo("PAID");
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isZero();
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(third);
            softly.assertThat((BigDecimal) statementRow(statementId).get("net_settlement_amount"))
                    .isEqualByComparingTo(SEED_NET.subtract(third));
        });
    }

    @Test
    @DisplayName("P3 先退 1/3、再退到全額（兩個事件，累計值）→ 最終 REFUNDED、結算單合計只扣總額（不是總額＋1/3）、各留一筆稽核")
    void p3_cumulativeRefunds_accountOnlyTheDifference() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");

        deliver(chargeRefunded(pi, cents(third), cents(total)));
        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
            softly.assertThat(orderStatus(orderId)).as("達到全額才轉訂單").isEqualTo("REFUNDED");
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("net_settlement_amount"))
                    .isEqualByComparingTo(SEED_NET.subtract(total));
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).as("每次真的記帳留一筆稽核").isEqualTo(2);
        });
    }

    @Test
    @DisplayName("P4 事件亂序：先收到全額、後收到較舊的累計 1/3 → 舊事件不動任何東西，結算單只扣總額一次")
    void p4_outOfOrderEvents_anOlderCumulativeNeverMovesTheRefundBackwards() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");

        deliver(chargeRefunded(pi, cents(total), cents(total)));
        deliver(chargeRefunded(pi, cents(third), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("P5 重送：同一個事件 id 送兩次（去重）、另一個事件 id 但累計相同（差額 0）→ 結算單只扣一次")
    void p5_redelivery_neverDeductsTwice() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");
        String event = chargeRefunded("evt_" + UUID.randomUUID(), pi, cents(third), cents(total));

        deliver(event);
        deliver(event);
        deliver(chargeRefunded(pi, cents(third), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(third);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(third);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isEqualTo(1);
        });
    }

    // ── 與 API 退款路徑的互動 ───────────────────────────────────────

    /**
     * 對照（修復前後都該綠）：最常見的順序——API 先退款（付款已 REFUNDED、結算單已扣），Stripe 之後送 {@code charge.refunded}。
     * webhook 必須是 no-op：結算單不能被扣第二次、不能多一筆狀態紀錄。
     */
    @Test
    @DisplayName("A1 對照：API 全額退款完成後 webhook 重述同一筆 → 什麼都不再發生（結算單只扣一次）")
    void a1_control_apiRefundThenWebhook_isANoOp() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = claim(orderId, "PENDING");
        stripeRefundsSucceed();
        asBuyer();
        paymentStateService.refundOrderPayment(orderId, null, "customer request");

        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).as("webhook 是 no-op，不留稽核").isZero();
        });
    }

    @Test
    @DisplayName("A2 API 部分退款完成後 webhook（累計＝同一筆）→ 付款維持 PARTIALLY_REFUNDED、訂單不動、結算單只扣那一次")
    void a2_apiPartialRefundThenWebhook_doesNotTurnItIntoAFullRefund() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");
        stripeRefundsSucceed();
        asBuyer();
        paymentStateService.refundOrderPayment(orderId, third, "partial");

        deliver(chargeRefunded(pi, cents(third), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("PARTIALLY_REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(third);
            softly.assertThat(orderStatus(orderId)).isEqualTo("PAID");
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(third);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isZero();
        });
    }

    /**
     * DEF-452 情境 A（Sprint 327 實測、Sprint 330 登記為 DEF-453）：Stripe 付款的訂單被自動退款，Stripe 已退出去，但本地因調整單失敗而整個回滾；
     * 之後 {@code charge.refunded} 到達。修復前：訂單 REFUNDING→REFUNDED、付款 REFUNDED 但 {@code refunded_amount} 0.00、結算單永遠沒被扣
     * （排程不再重試，因為訂單已不是候選）。修復後：webhook 補記，結算單被扣，排程也不會再叫 Stripe 退一次。
     */
    @Test
    @DisplayName("A3 DEF-452 情境 A：自動退款因調整單失敗回滾（Stripe 已退）→ 資料修好 → webhook 補記並扣結算單；排程不再重複退款")
    void a3_autoRefundRolledBack_thenWebhookRepairsTheAccounting() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        buyerCancels(orderId);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = seedStatement("PENDING_REVIEW", NEAR_OVERFLOW);
        settleOrder(orderId, statementId);
        stripeRefundsSucceed();
        Instant t0 = Instant.now();
        runOrderSweeper(t0);
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(orderStatus(orderId)).as("前提：自動退款回滾，訂單仍在等退款").isEqualTo("REFUNDING");
        assertThat(paymentRefundedAmount(orderId)).as("前提：本地沒有記帳").isEqualByComparingTo("0");

        jdbcTemplate.update("UPDATE settlement_statements SET total_refunds = 0.00 WHERE id = ?", statementId);
        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("net_settlement_amount"))
                    .isEqualByComparingTo(SEED_NET.subtract(total));
        });
        runOrderSweeper(t0.plus(Duration.ofMinutes(6)));
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
    }

    // ── 訂房付款 ────────────────────────────────────────────────────

    @Test
    @DisplayName("B1 訂房 Stripe 付款被 PENDING 結算單認領，全額 → 付款 REFUNDED、refunded_amount＝總額、結算單直接扣款")
    void b1_fullRefundOfABookingPayment_deductsFromTheStatement() {
        String pi = newPi();
        UUID bookingId = buyerBooks();
        stripeBookingPaid(bookingId, pi);
        BigDecimal total = bookingTotal(bookingId);
        UUID statementId = seedStatement("PENDING", "0.00");
        settleBooking(bookingId, statementId);
        assertThat(bookingTenant(bookingId)).as("前提：訂房的租戶就是結算單的租戶").isEqualTo(tenant.getId());

        deliver(chargeRefunded(pi, cents(total), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(bookingPaymentStatus(bookingId)).isEqualTo("REFUNDED");
            softly.assertThat(bookingPaymentRefundedAmount(bookingId)).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("net_settlement_amount"))
                    .isEqualByComparingTo(SEED_NET.subtract(total));
        });
    }

    @Test
    @DisplayName("B2 訂房部分退款（1/3）、結算單已 PAID → 付款 PARTIALLY_REFUNDED、一筆 −1/3 的調整單")
    void b2_partialRefundOfABookingPayment_createsAnAdjustment() {
        String pi = newPi();
        UUID bookingId = buyerBooks();
        stripeBookingPaid(bookingId, pi);
        BigDecimal total = bookingTotal(bookingId);
        BigDecimal third = third(total);
        UUID statementId = seedStatement("PAID", "0.00");
        settleBooking(bookingId, statementId);

        deliver(chargeRefunded(pi, cents(third), cents(total)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(bookingPaymentStatus(bookingId)).isEqualTo("PARTIALLY_REFUNDED");
            softly.assertThat(bookingPaymentRefundedAmount(bookingId)).isEqualByComparingTo(third);
            softly.assertThat(adjustmentCount(statementId)).isEqualTo(1);
            softly.assertThat(adjustmentSum(statementId)).isEqualByComparingTo(third.negate());
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo("0");
        });
    }

    // ── 不該動的事件 ────────────────────────────────────────────────

    @Test
    @DisplayName("E1 付款仍是 PROCESSING（成功事件還沒處理）就收到 charge.refunded → 不變：沒確認收過款的付款不能記退款")
    void e1_refundEventForAPaymentThatWasNeverConfirmedPaid_changesNothing() {
        String pi = newPi();
        UUID orderId = buyerPlacesOrder();
        startStripeCheckout(orderId);
        jdbcTemplate.update("UPDATE payments SET stripe_payment_intent_id = ? WHERE order_id = ?", pi, orderId);
        long total = cents(orderTotal(orderId));

        deliver(chargeRefunded(pi, total, total));

        Map<String, Object> payment = latestPayment(orderId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(payment.get("status")).isEqualTo("PROCESSING");
            softly.assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo("0");
            softly.assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        });
    }

    @Test
    @DisplayName("E2 amount_refunded 缺漏、非數字、負數、帶小數 → 都不變（不知道退了多少，就不能記成全額）")
    void e2_unusableAmountRefunded_changesNothing() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        long total = cents(orderTotal(orderId));

        deliver(chargeRefundedRaw(pi, total, null));
        deliver(chargeRefundedRaw(pi, total, "\"abc\""));
        deliver(chargeRefundedRaw(pi, total, "-5"));
        deliver(chargeRefundedRaw(pi, total, "100.5"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("SUCCESS");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo("0");
            softly.assertThat(orderStatus(orderId)).isEqualTo("PAID");
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isZero();
        });
    }

    @Test
    @DisplayName("E3 對照：payment_intent 找不到付款 → 不丟例外、事件仍記為已處理")
    void e3_control_unknownPaymentIntent_isIgnoredButRecordedAsProcessed() {
        String eventId = "evt_" + UUID.randomUUID();

        deliver(chargeRefunded(eventId, "pi_unknown_" + UUID.randomUUID(), 100, 100));

        assertThat(processedEventCount(eventId)).isEqualTo(1);
    }

    // ── 併發 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("C1 兩條執行緒同時送累計 1/3 與累計全額（8 輪）→ 不論誰先：最終 REFUNDED、結算單合計只扣總額、REFUNDED 狀態紀錄恰一筆")
    void c1_concurrentWebhooksForTheSamePayment_serialize() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            String pi = newPi();
            UUID orderId = stripePaidOrder(pi);
            BigDecimal total = orderTotal(orderId);
            BigDecimal third = third(total);
            UUID statementId = claim(orderId, "PENDING");
            String small = chargeRefunded(pi, cents(third), cents(total));
            String big = chargeRefunded(pi, cents(total), cents(total));
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (String event : List.of(small, big)) {
                    futures.add(pool.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        deliver(event);
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            final int r = round;
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(paymentStatus(orderId)).as("第 %d 輪", r).isEqualTo("REFUNDED");
                softly.assertThat(paymentRefundedAmount(orderId)).as("第 %d 輪", r).isEqualByComparingTo(total);
                softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).as("第 %d 輪", r)
                        .isEqualByComparingTo(total);
                softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).as("第 %d 輪", r).isEqualTo(1);
            });
        }
    }

    /**
     * API 退款的交易已經佔住這筆付款的退款額度、還沒提交時，Stripe 的 webhook 同時到達（Stripe 在退款成功後很快就送，而 API 的交易要等
     * 退款呼叫回來才提交）。webhook 必須<b>等</b> API 的交易結束，然後看見 API 已記下的累計值、判斷差額為 0——不能搶先記一份、
     * 也不能把部分退款翻成全額。「被擋住」本身就是這條路徑安全的原因，所以這裡直接斷言它。
     */
    @Test
    @DisplayName("C2 API 部分退款的交易還沒提交時 webhook 同時到達 → webhook 被擋到 API 提交之後，再看到新累計而不重複扣")
    void c2_webhookArrivingWhileTheApiRefundIsInFlight_waitsAndThenFindsNothingToDo() throws Exception {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        BigDecimal third = third(total);
        UUID statementId = claim(orderId, "PENDING");
        AtomicBoolean webhookFinishedBeforeApiCommit = new AtomicBoolean(true);
        AtomicReference<Throwable> webhookFailure = new AtomicReference<>();
        Thread webhook = new Thread(() -> {
            try {
                deliver(chargeRefunded(pi, cents(third), cents(total)));
            } catch (Throwable t) {
                webhookFailure.set(t);
            }
        }, "c2-webhook");
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), eq(pi), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 這個回呼在 API 的交易裡執行：額度已經被 compare-and-swap 佔住、交易還沒提交
                    webhook.start();
                    webhook.join(1500);
                    webhookFinishedBeforeApiCommit.set(!webhook.isAlive());
                    return PaymentGatewayRequestResponse.RefundResult.builder()
                            .success(true).refundId("re_c2").status("succeeded").build();
                });
        asBuyer();

        paymentStateService.refundOrderPayment(orderId, third, "partial");
        webhook.join(15_000);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(webhook.isAlive()).as("webhook 執行緒已結束").isFalse();
            softly.assertThat(webhookFailure.get()).as("webhook 沒有失敗").isNull();
            softly.assertThat(webhookFinishedBeforeApiCommit.get())
                    .as("webhook 在 API 的交易提交之前不該完成（它必須等那筆付款的鎖）").isFalse();
            softly.assertThat(paymentStatus(orderId)).isEqualTo("PARTIALLY_REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(third);
            softly.assertThat(orderStatus(orderId)).isEqualTo("PAID");
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(third);
            softly.assertThat(auditCount("ORDER_PAYMENT_REFUNDED", paymentId(orderId))).isEqualTo(1);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).as("webhook 是 no-op，不留稽核").isZero();
        });
    }

    // ── 原子性：記帳失敗不留半套，資料修好後重送會收斂 ───────────────────

    @Test
    @DisplayName("X1 webhook 記帳途中失敗（結算單金額溢位）→ 例外往外丟、什麼都沒記、事件沒被標成已處理；資料修好後重送同一事件記帳恰一次")
    void x1_failingAccounting_leavesNothingBehind_andRedeliveryConverges() {
        String pi = newPi();
        UUID orderId = stripePaidOrder(pi);
        BigDecimal total = orderTotal(orderId);
        UUID statementId = seedStatement("PENDING_REVIEW", NEAR_OVERFLOW);
        settleOrder(orderId, statementId);
        String eventId = "evt_" + UUID.randomUUID();
        String event = chargeRefunded(eventId, pi, cents(total), cents(total));

        Throwable thrown = catchThrowable(() -> deliver(event));

        assertThat(thrown).as("記帳失敗必須讓整個事件處理失敗，而不是吞掉").isNotNull();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).as("付款沒有被標成已退款").isEqualTo("SUCCESS");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo("0");
            softly.assertThat(orderStatus(orderId)).isEqualTo("PAID");
            softly.assertThat(orderStateLogCount(orderId, "REFUNDED")).isZero();
            softly.assertThat(processedEventCount(eventId)).as("事件沒被標成已處理，重送才有機會成功").isZero();
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds"))
                    .isEqualByComparingTo(NEAR_OVERFLOW);
        });

        jdbcTemplate.update("UPDATE settlement_statements SET total_refunds = 0.00 WHERE id = ?", statementId);
        deliver(event);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            softly.assertThat(paymentRefundedAmount(orderId)).isEqualByComparingTo(total);
            softly.assertThat((BigDecimal) statementRow(statementId).get("total_refunds")).isEqualByComparingTo(total);
            softly.assertThat(auditCount(WEBHOOK_AUDIT, paymentId(orderId))).isEqualTo(1);
            softly.assertThat(processedEventCount(eventId)).isEqualTo(1);
        });
    }

    // ── 固件：webhook 內容 ───────────────────────────────────────────

    private static String newPi() {
        return "pi_" + UUID.randomUUID();
    }

    /** 一個 {@code charge.refunded} 事件；{@code amount_refunded} 是 Stripe 端<b>累計</b>已退款的最小單位（分）。 */
    private static String chargeRefunded(final String eventId, final String pi, final long refundedCents,
            final long chargeCents) {
        return chargeRefundedJson(eventId, pi, chargeCents, "\"amount_refunded\":" + refundedCents + ",",
                refundedCents >= chargeCents);
    }

    private static String chargeRefunded(final String pi, final long refundedCents, final long chargeCents) {
        return chargeRefunded("evt_" + UUID.randomUUID(), pi, refundedCents, chargeCents);
    }

    /** {@code rawAmountRefunded} 為 null 表示整個欄位缺漏；否則原樣放進 JSON（用來造「不可用」的值）。 */
    private static String chargeRefundedRaw(final String pi, final long chargeCents, final String rawAmountRefunded) {
        String field = rawAmountRefunded == null ? "" : "\"amount_refunded\":" + rawAmountRefunded + ",";
        return chargeRefundedJson("evt_" + UUID.randomUUID(), pi, chargeCents, field, true);
    }

    private static String chargeRefundedJson(final String eventId, final String pi, final long chargeCents,
            final String amountRefundedField, final boolean refunded) {
        return "{\"id\":\"" + eventId + "\",\"type\":\"charge.refunded\",\"data\":{\"object\":{\"id\":\"ch_"
                + UUID.randomUUID() + "\",\"object\":\"charge\",\"payment_intent\":\"" + pi + "\",\"amount\":"
                + chargeCents + "," + amountRefundedField + "\"refunded\":" + refunded
                + ",\"refunds\":{\"data\":[{\"id\":\"re_" + UUID.randomUUID() + "\"}]}}}}";
    }

    /** Stripe 的 webhook 沒有登入使用者、沒有租戶內容。 */
    private void deliver(final String eventJson) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        paymentWebhookService.handleEvent(eventJson);
    }

    private static long cents(final BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /** 總額的三分之一（無條件捨去到分），一定小於總額。 */
    private static BigDecimal third(final BigDecimal total) {
        return total.divide(BigDecimal.valueOf(3), 2, RoundingMode.DOWN);
    }

    // ── 固件：結算單 ─────────────────────────────────────────────────

    /** 一張結算單（GMV 10000、抽成 1000、淨額 9000）。 */
    private UUID seedStatement(final String status, final String totalRefunds) {
        UUID statementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-21', DATE '2026-12-27', 1, 10000.00, ?, 1000.00, 9000.00,
                        'TWD', ?, NOW(), NOW(), NOW())
                """, statementId, tenant.getId(), "IT-S342-" + System.nanoTime(), new BigDecimal(totalRefunds), status);
        return statementId;
    }

    /** 這張訂單被一張新結算單認領（正式環境由週結算完成，這裡合成）。 */
    private UUID claim(final UUID orderId, final String statementStatus) {
        UUID statementId = seedStatement(statementStatus, "0.00");
        settleOrder(orderId, statementId);
        assertThat(orderTenant(orderId)).as("前提：訂單的租戶就是結算單的租戶，調整單才會去定位到它").isEqualTo(tenant.getId());
        return statementId;
    }

    private void settleOrder(final UUID orderId, final UUID statementId) {
        jdbcTemplate.update("UPDATE orders SET settled_statement_id = ? WHERE id = ?", statementId, orderId);
    }

    private void settleBooking(final UUID bookingId, final UUID statementId) {
        jdbcTemplate.update("UPDATE bookings SET settled_statement_id = ? WHERE id = ?", statementId, bookingId);
    }

    private void markCompleted(final UUID orderId) {
        jdbcTemplate.update("UPDATE orders SET status = 'COMPLETED' WHERE id = ?", orderId);
    }

    private Map<String, Object> statementRow(final UUID statementId) {
        return jdbcTemplate.queryForMap("SELECT status, total_refunds, net_settlement_amount "
                + "FROM settlement_statements WHERE id = ?", statementId);
    }

    private int adjustmentCount(final UUID statementId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM adjustment_statements WHERE original_statement_id = ?",
                Integer.class, statementId);
    }

    private BigDecimal adjustmentSum(final UUID statementId) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM adjustment_statements "
                + "WHERE original_statement_id = ?", BigDecimal.class, statementId);
    }

    // ── 固件：流程 ───────────────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runOrderSweeper(final Instant now) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        refundProcessingService.processPendingOrderRefunds(now);
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

    private void buyerCancels(final UUID orderId) {
        asBuyer();
        orderService.cancelOrder(orderId, "changed my mind");
    }

    /** 買家訂兩晚，入住日離今天 10 天。 */
    private UUID buyerBooks() {
        asBuyer();
        LocalDate checkIn = BusinessTime.today().plusDays(10);
        return bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("Test Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .build(), null).getId();
    }

    private void stubCheckoutSessions() {
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(true);
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenAnswer(invocation -> {
                    String sessionId = "cs_test_" + UUID.randomUUID();
                    return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                            .sessionId(sessionId).sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId)
                            .status("open").paymentStatus("unpaid").build();
                });
    }

    /** 買家發起 Stripe 結帳（Stripe 閘道是 mock），回傳 Checkout Session id。 */
    private String startStripeCheckout(final UUID orderId) {
        stubCheckoutSessions();
        asBuyer();
        return paymentStateService.initiateStripeCheckout(orderId).getSessionId();
    }

    private UUID stripePaidOrder(final String paymentIntentId) {
        UUID orderId = buyerPlacesOrder();
        String sessionId = startStripeCheckout(orderId);
        deliver(checkoutCompleted(sessionId, paymentIntentId));
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        return orderId;
    }

    private void stripeBookingPaid(final UUID bookingId, final String paymentIntentId) {
        stubCheckoutSessions();
        asBuyer();
        String sessionId = paymentStateService.initiateStripeBookingCheckout(bookingId).getSessionId();
        deliver(checkoutCompleted(sessionId, paymentIntentId));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId))
                .isEqualTo("PAID");
    }

    private static String checkoutCompleted(final String sessionId, final String paymentIntentId) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"payment_intent\":\""
                + paymentIntentId + "\"}}}";
    }

    private void stripeRefundsSucceed() {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), anyString(), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_test_1").status("succeeded").build());
    }

    private void asBuyer() {
        act(buyer);
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
                .name("Stripe Refund Webhook Tenant")
                .slug("stripe-refund-webhook-" + stamp)
                .contactEmail("stripe-refund-webhook-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        seller = userRepository.save(User.builder()
                .email("stripe-refund-webhook-seller-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Seller").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        buyer = userRepository.save(User.builder()
                .email("stripe-refund-webhook-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant).owner(seller).listingType(Listing.ListingType.PRODUCT)
                .title("Stripe Refund Webhook Product").basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE).build()).getId();
        skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-STRIPEREFUNDWH-" + stamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, 0, 10, 0, NOW())
                """, skuId, STOCK);
        roomListingId = listingRepository.save(Listing.builder()
                .tenant(tenant).owner(seller).listingType(Listing.ListingType.ROOM)
                .title("Stripe Refund Webhook Room").basePrice(new BigDecimal("1500.00"))
                .status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
    }

    // ── 查詢 ─────────────────────────────────────────────────────────

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

    private Map<String, Object> latestPayment(final UUID orderId) {
        return jdbcTemplate.queryForMap("SELECT status, refunded_amount FROM payments WHERE order_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", orderId);
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

    private String bookingPaymentStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE booking_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", String.class, bookingId);
    }

    private BigDecimal bookingPaymentRefundedAmount(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT refunded_amount FROM payments WHERE booking_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", BigDecimal.class, bookingId);
    }

    private int auditCount(final String action, final UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, entityId, action);
    }

    private int processedEventCount(final String eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_stripe_events WHERE event_id = ?",
                Integer.class, eventId);
    }
}
