package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
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

/**
 * 自動退款（Sprint 226，DEF-303 (5)／DEF-308；使用者 2026-10-01 回覆「請依照最佳化進行！」）。
 *
 * <p>真實 PostgreSQL＋真實 Redis 購物車，走真實的建單、付款、取消、webhook 服務方法；只有 Stripe 閘道以
 * {@code @MockBean} 取代（不打外部網路），用來數「Stripe 被呼叫了幾次、帶什麼鍵」。自動退款的核心是
 * 「{@code REFUNDING} 是持久的待退款標記，排程把它做完」，所以這裡重點驗證：做完之後資料庫裡的付款、訂單、狀態紀錄、稽核
 * 都一致；做不完時（Stripe 拒絕）整個交易回滾、不留半套狀態；同一筆付款不會被兩個處理者各退一次。
 *
 * <p>共用測試資料庫可能有其他測試留下的 {@code REFUNDING} 訂單，排程會一併處理它們，所以斷言都針對本測試自己建立的訂單，
 * Stripe 呼叫也一律以本測試的 PaymentIntent 過濾。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-ORDER-AUTO-REFUND: 取消已付款訂單自動退款（Sprint 226）")
class OrderAutoRefundIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final int STOCK = 100;
    private static final int QTY = 3;

    @Autowired private RefundProcessingService refundProcessingService;
    @Autowired private OrderService orderService;
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
        // 共用資料庫：故意做成退款失敗／沒有付款的 REFUNDING 訂單會永遠卡在那裡，之後每個測試的排程都要再試一次，清掉
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE tenant_id = ? AND status = 'REFUNDING'",
                tenant.getId());
    }

    // ── Mock 付款 ─────────────────────────────────────────────

    @Test
    @DisplayName("Mock 付款的訂單被取消（REFUNDING）→ 排程退款：付款與訂單都轉 REFUNDED，狀態紀錄操作者為系統，不呼叫 Stripe")
    void cancelledMockPaidOrder_isRefundedAutomatically() {
        UUID orderId = buyerPlacesOrder();
        buyerPaysWithMock(orderId);
        buyerCancels(orderId);
        assertThat(orderStatus(orderId)).as("取消已付款訂單先進入 REFUNDING 等待退款").isEqualTo("REFUNDING");
        BigDecimal total = orderTotal(orderId);
        assertThat(refundNoticesOfBuyer()).as("錢還沒退，不能先說退款完成").isEmpty();

        runSweeper(Instant.now());

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, refunded_amount FROM payments WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("REFUNDED");
        assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo(total);
        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, changed_by, reason FROM order_state_log "
                        + "WHERE order_id = ? AND to_status = 'REFUNDED'", orderId);
        assertThat(log.get("from_status")).isEqualTo("REFUNDING");
        assertThat(log.get("changed_by")).as("自動退款沒有操作使用者").isNull();
        assertThat(log.get("reason")).isEqualTo("Automatic refund: order cancelled");
        assertThat(auditCount("ORDER_PAYMENT_REFUNDED", paymentId(orderId))).isEqualTo(1);
        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any(), any());

        // Sprint 229：退款完成後買家收到站內通知（REFUND_COMPLETED 通過資料庫約束），金額是實際退的累計額
        List<Map<String, Object>> notices = refundNoticesOfBuyer();
        assertThat(notices).as("退款完成後通知買家一次").hasSize(1);
        assertThat(notices.get(0).get("title")).isEqualTo("退款已完成");
        assertThat((String) notices.get(0).get("content")).contains("#" + orderId.toString().substring(0, 8),
                "已退回原付款方式");
        assertThat((String) notices.get(0).get("data")).contains(orderId.toString(), "refundAmount");
    }

    @Test
    @DisplayName("重複執行不會重複退款：已 REFUNDED 的訂單不再是候選，退款只記一次")
    void runningTwice_refundsOnce() {
        UUID orderId = buyerPlacesOrder();
        buyerPaysWithMock(orderId);
        buyerCancels(orderId);

        runSweeper(Instant.now());
        runSweeper(Instant.now());

        assertThat(auditCount("ORDER_PAYMENT_REFUNDED", paymentId(orderId))).isEqualTo(1);
        assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
        assertThat(refundNoticesOfBuyer()).as("只有真的退款的那一輪通知，第二輪不再通知").hasSize(1);
    }

    @Test
    @DisplayName("沒有被取消的已付款訂單不會被自動退款（只有 REFUNDING 才是「等待退款」）")
    void paidOrderThatWasNotCancelled_isNeverRefunded() {
        UUID orderId = buyerPlacesOrder();
        buyerPaysWithMock(orderId);

        runSweeper(Instant.now());

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(paymentStatus(orderId)).isEqualTo("SUCCESS");
        assertThat(refundNoticesOfBuyer()).isEmpty();
    }

    // ── Stripe 付款 ───────────────────────────────────────────

    @Test
    @DisplayName("Stripe 付款的訂單被取消 → 排程經 Stripe 退款一次（冪等鍵綁定這次退款），存下 refund id，付款與訂單轉 REFUNDED")
    void cancelledStripePaidOrder_isRefundedThroughStripe() {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        stripePaid(orderId, pi);
        buyerCancels(orderId);
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
        BigDecimal total = orderTotal(orderId);
        stripeRefundsSucceed();

        runSweeper(Instant.now());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi),
                org.mockito.ArgumentMatchers.argThat(a -> a != null && a.compareTo(total) == 0),
                eq("Automatic refund: order cancelled"), key.capture());
        assertThat(key.getValue()).as("冪等鍵＝付款意圖＋退款前累計額＋本次金額")
                .isEqualTo("refund-" + pi + "-0.00-" + total.setScale(2));
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, refunded_amount, stripe_refund_id FROM payments WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("REFUNDED");
        assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo(total);
        assertThat(payment.get("stripe_refund_id")).isEqualTo("re_test_1");
    }

    @Test
    @DisplayName("🔴 STRIPE_PAYMENT_ENABLED 之後被關閉，已在 Stripe 收下的錢仍經 Stripe 退款——不能只在本地標成已退款")
    void stripePaidOrder_isStillRefundedThroughStripeWhenToggleWasTurnedOff() {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        stripePaid(orderId, pi);
        buyerCancels(orderId);
        stripeRefundsSucceed();
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)).thenReturn(false);

        runSweeper(Instant.now());

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("Stripe 拒絕退款 → 整個交易回滾：訂單仍是 REFUNDING、付款未標退款、沒有多餘的狀態紀錄；留一筆失敗稽核；退避期間不重試，"
            + "退避結束且 Stripe 恢復後退款成功")
    void stripeRefundFailure_rollsBackThenRetriesAfterBackoff() {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        stripePaid(orderId, pi);
        buyerCancels(orderId);
        BigDecimal total = orderTotal(orderId);
        stripeRefundFails(pi);
        Instant t0 = Instant.now();

        runSweeper(t0);

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, refunded_amount, stripe_refund_id FROM payments WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).as("回滾：付款沒有被標成已退款").isEqualTo("SUCCESS");
        assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo("0");
        assertThat(payment.get("stripe_refund_id")).isNull();
        assertThat(orderStateLogCount(orderId, "REFUNDED")).isZero();
        assertThat(auditCountForEntity("AUTO_REFUND_FAILED", orderId)).isEqualTo(1);
        assertThat(refundNoticesOfBuyer()).as("退款失敗：錢沒退，不能通知買家已退款").isEmpty();
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());

        runSweeper(t0.plus(Duration.ofMinutes(1)));
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());

        stripeRefundsSucceed();
        runSweeper(t0.plus(Duration.ofMinutes(6)));

        verify(paymentGatewayFactory, times(2)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT refunded_amount FROM payments WHERE order_id = ?",
                BigDecimal.class, orderId)).isEqualByComparingTo(total);
        assertThat(refundNoticesOfBuyer()).as("失敗重試後成功，只通知一次").hasSize(1);
    }

    @Test
    @DisplayName("一張永久失敗的訂單不擋住其他訂單：同一輪裡另一張照常退款")
    void permanentlyFailingOrderDoesNotBlockOthers() {
        String badPi = "pi_" + UUID.randomUUID();
        String goodPi = "pi_" + UUID.randomUUID();
        UUID bad = buyerPlacesOrder();
        stripePaid(bad, badPi);
        buyerCancels(bad);
        UUID good = buyerPlacesOrder();
        stripePaid(good, goodPi);
        buyerCancels(good);
        stripeRefundFails(badPi);
        stripeRefundsSucceedFor(goodPi);

        runSweeper(Instant.now());

        assertThat(orderStatus(bad)).isEqualTo("REFUNDING");
        assertThat(orderStatus(good)).isEqualTo("REFUNDED");
        List<Map<String, Object>> notices = refundNoticesOfBuyer();
        assertThat(notices).as("只有退款成功的那張通知").hasSize(1);
        assertThat((String) notices.get(0).get("data")).contains(good.toString()).doesNotContain(bad.toString());
    }

    @Test
    @DisplayName("兩個處理者同時處理同一張訂單（多實例）→ Stripe 只被呼叫一次，款項只退一次")
    void concurrentSweepers_refundOnlyOnce() throws Exception {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        stripePaid(orderId, pi);
        buyerCancels(orderId);
        BigDecimal total = orderTotal(orderId);
        stripeRefundsSucceed();

        List<Callable<Void>> sweepers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sweepers.add(() -> {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
                refundProcessingService.processPendingOrderRefunds(Instant.now());
                return null;
            });
        }
        race(sweepers);

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT refunded_amount FROM payments WHERE order_id = ?",
                BigDecimal.class, orderId)).as("只退一次，不會多退").isEqualByComparingTo(total);
        assertThat(orderStateLogCount(orderId, "REFUNDED")).isEqualTo(1);
        assertThat(refundNoticesOfBuyer()).as("多個處理者同時退，買家只收到一則退款完成通知").hasSize(1);
    }

    @Test
    @DisplayName("找不到可退款的付款（異常資料：訂單 REFUNDING 但沒有任何付款）→ 大聲失敗：留失敗稽核、訂單維持 REFUNDING，不假裝退了")
    void refundingOrderWithoutPayment_failsLoudly() {
        UUID orderId = buyerPlacesOrder();
        jdbcTemplate.update("UPDATE orders SET status = 'REFUNDING' WHERE id = ?", orderId);

        runSweeper(Instant.now());

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDING");
        assertThat(auditCountForEntity("AUTO_REFUND_FAILED", orderId)).isEqualTo(1);
        assertThat(refundNoticesOfBuyer()).as("沒有退成就不通知，不假裝退了").isEmpty();
    }

    // ── DEF-308：付款成功時訂單已取消 ─────────────────────────

    @Test
    @DisplayName("🔴 DEF-308：買家還在 Stripe 付款頁時訂單被取消，之後付款成功（webhook）→ 錢收了不再靜默："
            + "訂單 CANCELLED → REFUNDING（狀態紀錄註明原因、留稽核），排程經 Stripe 全額退回")
    void paymentAfterCancellation_isRefundedAutomatically() {
        String pi = "pi_" + UUID.randomUUID();
        UUID orderId = buyerPlacesOrder();
        String sessionId = startStripeCheckout(orderId);
        buyerCancels(orderId);                                    // CREATED → CANCELLED（沒付款，沒有退款）
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        BigDecimal total = orderTotal(orderId);
        stripeRefundsSucceed();

        TenantContext.clear();
        SecurityContextHolder.clearContext();
        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId, pi));

        assertThat(orderStatus(orderId)).as("付款成功但訂單已取消 → 等待退款").isEqualTo("REFUNDING");
        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT from_status, changed_by, reason FROM order_state_log "
                        + "WHERE order_id = ? AND to_status = 'REFUNDING'", orderId);
        assertThat(log.get("from_status")).isEqualTo("CANCELLED");
        assertThat(log.get("changed_by")).isNull();
        assertThat((String) log.get("reason")).startsWith("Payment received after cancellation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT new_value FROM audit_log WHERE entity_id = ? AND action = 'STRIPE_PAYMENT_ORDER_NOT_PAYABLE'",
                String.class, paymentId(orderId))).isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM audit_log WHERE entity_id = ? AND action = 'STRIPE_PAYMENT_ORDER_NOT_PAYABLE'",
                String.class, paymentId(orderId))).contains("refundQueued=true");

        runSweeper(Instant.now());

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi),
                org.mockito.ArgumentMatchers.argThat(a -> a != null && a.compareTo(total) == 0), any(), any());
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(refundNoticesOfBuyer()).as("DEF-308：遲到的付款也會退回，買家收到退款完成通知").hasSize(1);
    }

    @Test
    @DisplayName("付款成功與取消搶同一個狀態，恰好一邊成功：先取消 → 付款走退款；先付款 → 取消走一般的『已付款取消』"
            + "——兩種順序最後都退回買家的錢，沒有『訂單是 PAID 卻已釋放預留』的狀態")
    void paymentAndCancellationRace_bothOrdersEndInRefund() throws Exception {
        for (int round = 0; round < 3; round++) {
            String pi = "pi_" + UUID.randomUUID();
            UUID orderId = buyerPlacesOrder();
            String sessionId = startStripeCheckout(orderId);
            stripeRefundsSucceed();

            List<Callable<Void>> calls = List.of(
                    () -> {
                        TenantContext.clear();
                        SecurityContextHolder.clearContext();
                        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId, pi));
                        return null;
                    },
                    () -> {
                        asBuyer();
                        try {
                            orderService.cancelOrder(orderId, "race");
                        } catch (RuntimeException ignored) {
                            // 取消晚於付款時，CAS 之後仍合法（PAID → CANCELLED）；例外不是本測試要斷言的
                        }
                        return null;
                    });
            race(calls);

            if ("PAID".equals(orderStatus(orderId))) {
                // 付款搶先提交、取消讀到的還是舊快照而被拒（E-5002）——合法：買家看到錯誤、再按一次取消
                buyerCancels(orderId);
            }
            assertThat(orderStatus(orderId)).as("round %d：取消成功後訂單必然是 REFUNDING（付款先到：已付款取消；"
                    + "取消先到：遲到付款）", round).isEqualTo("REFUNDING");
            runSweeper(Instant.now());
            assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
            verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
            assertThat(reservedQty()).as("預留沒有被重複釋放或漏釋放").isZero();
        }
    }

    // ── 固件與身分 ──────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runSweeper(final Instant now) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        refundProcessingService.processPendingOrderRefunds(now);
    }

    private void race(final List<Callable<Void>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Void>> results = new ArrayList<>();
        try {
            for (Callable<Void> call : calls) {
                results.add(pool.submit(() -> {
                    startGun.await();
                    try {
                        return call.call();
                    } finally {
                        TenantContext.clear();
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            startGun.countDown();
            for (Future<Void> result : results) {
                result.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
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

    private void buyerPaysWithMock(final UUID orderId) {
        asBuyer();
        paymentStateService.mockPaymentSuccess(orderId);
    }

    private void buyerCancels(final UUID orderId) {
        asBuyer();
        orderService.cancelOrder(orderId, "changed my mind");
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
        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId, paymentIntentId));
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    private static String checkoutCompletedEvent(final String sessionId, final String paymentIntentId) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
                + "{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"payment_intent\":\""
                + paymentIntentId + "\"}}}";
    }

    private void stripeRefundsSucceed() {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), anyString(), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_test_1").status("succeeded").build());
    }

    private void stripeRefundsSucceedFor(final String paymentIntentId) {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), eq(paymentIntentId), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_test_1").status("succeeded").build());
    }

    private void stripeRefundFails(final String paymentIntentId) {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), eq(paymentIntentId), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(false).errorMessage("charge_already_refunded").build());
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
                .name("Auto Refund Tenant")
                .slug("auto-refund-" + stamp)
                .contactEmail("auto-refund-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        seller = userRepository.save(User.builder()
                .email("auto-refund-seller-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Auto Refund Seller")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
        buyer = userRepository.save(User.builder()
                .email("auto-refund-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Auto Refund Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(seller)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Auto Refund Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, listingId, "SKU-AUTOREFUND-" + stamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, 0, 10, 0, NOW())
                """, skuId, STOCK);
    }

    /** 這個測試的買家收到的「退款完成」通知（每個測試都建新買家，不會混到共用資料庫裡別人的通知）。 */
    private List<Map<String, Object>> refundNoticesOfBuyer() {
        return jdbcTemplate.queryForList("SELECT title, content, data::text AS data FROM notifications "
                + "WHERE user_id = ? AND notification_type = 'REFUND_COMPLETED' ORDER BY created_at", buyer.getId());
    }

    private String orderStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private BigDecimal orderTotal(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, orderId);
    }

    private String paymentStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", String.class, orderId);
    }

    private UUID paymentId(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT id FROM payments WHERE order_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", UUID.class, orderId);
    }

    private int reservedQty() {
        return jdbcTemplate.queryForObject("SELECT reserved_qty FROM product_inventory WHERE sku_id = ?",
                Integer.class, skuId);
    }

    private int orderStateLogCount(final UUID orderId, final String toStatus) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_state_log WHERE order_id = ? AND to_status = ?",
                Integer.class, orderId, toStatus);
    }

    private int auditCount(final String action, final UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, entityId, action);
    }

    private int auditCountForEntity(final String action, final UUID entityId) {
        return auditCount(action, entityId);
    }
}
