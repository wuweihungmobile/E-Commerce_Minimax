package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.core.payment.RefundProcessingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 訂房取消退款（Sprint 227，DEF-312；使用者 2026-10-01 回覆「請對齊PRD」）。PRD §15.2.5／Q14：取消前 &gt;= 24 小時全額退款、
 * &lt; 24 小時不退款、商家取消一律全額退款。
 *
 * <p>真實 PostgreSQL＋真實 Redis（日曆鎖），走真實的訂房、付款、取消、webhook 服務方法；只有 Stripe 閘道以
 * {@code @MockBean} 取代。取消決定退款並記在訂房上（{@code refund_status = PENDING}），排程
 * （{@code RefundProcessingService}）再把它退完——所以重點驗證的是兩段合起來之後資料庫裡的付款、訂房、日曆、優惠券、稽核
 * 都一致，以及併發取消、併發處理者、Stripe 失敗都不會重複退款或留下半套狀態。
 *
 * <p>「入住前不足 24 小時」用入住日＝今天（營運時區）固定成立：入住時間是當天 15:00，不論現在幾點離入住都不到 24 小時。
 * 24 小時的邊界本身由 {@code BookingRefundPolicyTest} 用固定時刻驗證。共用資料庫裡可能有其他測試留下的待退款訂房，排程會一併
 * 處理，所以斷言都針對本測試自己建立的訂房，Stripe 呼叫也一律以本測試的 PaymentIntent 過濾。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-BOOKING-CANCEL-REFUND: 訂房取消退款（Sprint 227，PRD Q14）")
class BookingCancellationRefundIntegrationTest {

    private static final String STRIPE_TOGGLE = "STRIPE_PAYMENT_ENABLED";
    private static final BigDecimal TWO_NIGHTS = new BigDecimal("3000.00");

    @Autowired private BookingService bookingService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private PaymentWebhookService paymentWebhookService;
    @Autowired private RefundProcessingService refundProcessingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private Tenant tenant;
    private User buyer;
    private User admin;
    private User storeOwner;
    private UUID roomListingId;

    // Sprint 231（DEF-316）：跨租戶商家取消需要第二個租戶與店主
    private Tenant otherTenant;
    private User otherTenantStoreOwner;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        seed();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        // 共用資料庫：故意做成退款失敗的待退款訂房會永遠卡在那裡，之後每個測試的排程都要再試一次，清掉
        jdbcTemplate.update("UPDATE bookings SET refund_status = 'NONE' WHERE tenant_id = ? AND refund_status = 'PENDING'",
                tenant.getId());
    }

    // ── 買家本人取消（PRD Q14 的 24 小時門檻）────────────────────

    @Test
    @DisplayName("已付款、離入住好幾天、買家取消 → 全額退款：回應 PENDING 與金額；日曆釋放、優惠券退還；排程退回後付款 REFUNDED、訂房 COMPLETED")
    void paidBookingCancelledWellInAdvance_isFullyRefunded() {
        String promo = givenPromo();
        UUID bookingId = buyerBooks(10, promo);
        BigDecimal paid = bookingTotal(bookingId);
        buyerPaysWithMock(bookingId);
        assertThat(promoUsageCount(promo)).isEqualTo(1);

        BookingDto.CancelResponse response = buyerCancels(bookingId);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getCanceledBy()).isEqualTo("CUSTOMER");
        assertThat(response.getRefundStatus()).isEqualTo("PENDING");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(paid);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, cancelled_by, cancelled_at, refund_status, refund_amount FROM bookings WHERE id = ?",
                bookingId);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("cancelled_by")).isEqualTo("CUSTOMER");
        assertThat(row.get("cancelled_at")).isNotNull();
        assertThat(row.get("refund_status")).isEqualTo("PENDING");
        assertThat((BigDecimal) row.get("refund_amount")).isEqualByComparingTo(paid);
        assertThat(bookedNights(bookingId)).as("日曆已釋放").isZero();
        assertThat(promoUsageCount(promo)).as("優惠券額度退還").isZero();
        assertThat(paymentStatus(bookingId)).as("取消當下還沒動錢（由排程退）").isEqualTo("SUCCESS");

        runSweeper(Instant.now());

        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(paymentRefundedAmount(bookingId)).isEqualByComparingTo(paid);
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
        assertThat(auditCount("BOOKING_REFUND_DECIDED", bookingId)).isEqualTo(1);
        assertThat(auditCount("BOOKING_PAYMENT_REFUNDED", paymentId(bookingId))).isEqualTo(1);
        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已付款、入住前不足 24 小時、買家取消 → 不退款：NONE、付款仍 SUCCESS；日曆仍釋放；稽核留下「不退」的決定")
    void paidBookingCancelledTooLate_isNotRefunded() {
        UUID bookingId = buyerBooks(0, null);
        buyerPaysWithMock(bookingId);

        BookingDto.CancelResponse response = buyerCancels(bookingId);

        assertThat(response.getRefundStatus()).isEqualTo("NONE");
        assertThat(response.getRefundAmount()).isNull();
        assertThat(refundStatus(bookingId)).isEqualTo("NONE");
        assertThat(bookedNights(bookingId)).as("不退款也要釋放日曆").isZero();

        runSweeper(Instant.now());

        assertThat(paymentStatus(bookingId)).as("依 Q14 不退款：付款維持 SUCCESS").isEqualTo("SUCCESS");
        assertThat(paymentRefundedAmount(bookingId)).isEqualByComparingTo("0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT new_value FROM audit_log WHERE entity_id = ? AND action = 'BOOKING_REFUND_DECIDED'",
                String.class, bookingId)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("未付款的訂房取消 → 沒有款項可退：NONE，沒有退款決定稽核")
    void unpaidBookingCancel_needsNoRefund() {
        UUID bookingId = buyerBooks(10, null);

        BookingDto.CancelResponse response = buyerCancels(bookingId);

        assertThat(response.getRefundStatus()).isEqualTo("NONE");
        assertThat(auditCount("BOOKING_REFUND_DECIDED", bookingId)).isZero();
    }

    // ── 商家／平台取消 ───────────────────────────────────────

    @Test
    @DisplayName("商家（管理員代為取消）在入住前不足 24 小時取消已付款訂房 → 一律全額退款（取消方 MERCHANT），排程退回")
    void merchantCancellationTooLate_isStillFullyRefunded() {
        UUID bookingId = buyerBooks(0, null);
        BigDecimal paid = bookingTotal(bookingId);
        buyerPaysWithMock(bookingId);

        asAdmin();
        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "host cancelled");

        assertThat(response.getCanceledBy()).isEqualTo("MERCHANT");
        assertThat(response.getRefundStatus()).isEqualTo("PENDING");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(paid);

        runSweeper(Instant.now());

        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
    }

    // ── 商家端訂房管理（Sprint 231，DEF-316）：STORE_OWNER 本人（非管理員）取消自己租戶的訂房 ──

    @Test
    @DisplayName("Sprint 231：本租戶 STORE_OWNER（非管理員、非訂房買家本人）可取消自己租戶的訂房，"
            + "入住前不足 24 小時仍一律全額退款（取消方 MERCHANT）；此前 checkBookingOwnership 無租戶分支，"
            + "即使持有 booking:cancel 仍 403（DEF-316 的核心缺口）")
    void sameTenantStoreOwner_canCancel_isStillFullyRefunded() {
        UUID bookingId = buyerBooks(0, null);
        BigDecimal paid = bookingTotal(bookingId);
        buyerPaysWithMock(bookingId);

        asStoreOwner();
        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "store owner cancelled");

        assertThat(response.getCanceledBy()).isEqualTo("MERCHANT");
        assertThat(response.getRefundStatus()).isEqualTo("PENDING");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(paid);

        runSweeper(Instant.now());

        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Sprint 231：他租戶 STORE_OWNER 取消訂房 → 403（E_1007），租戶隔離未因新分支而失效"
            + "（真實 Postgres：兩個真正持久化的租戶，而非 mock）")
    void differentTenantStoreOwner_cannotCancel() {
        UUID bookingId = buyerBooks(10, null);

        asOtherTenantStoreOwner();

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "cross-tenant attempt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
        assertThat(bookingStatus(bookingId)).as("越權請求不得變更訂房狀態").isEqualTo("CREATED");
    }

    @Test
    @DisplayName("Sprint 231：getTenantBookings 只回傳本租戶的訂房，看不到其他租戶的（真實 Postgres 查詢驗證租戶隔離，"
            + "findByTenantIdOrderByCreatedAtDesc 此前完全沒有呼叫者）")
    void getTenantBookings_onlyReturnsOwnTenantBookings() {
        UUID ownBookingId = buyerBooks(10, null);

        asStoreOwner();
        var ownResult = bookingService.getTenantBookings(0, 20, "createdAt", "DESC");
        asOtherTenantStoreOwner();
        var otherResult = bookingService.getTenantBookings(0, 20, "createdAt", "DESC");

        assertThat(ownResult.getContent()).extracting("id").contains(ownBookingId);
        assertThat(otherResult.getContent()).extracting("id").doesNotContain(ownBookingId);
    }

    // ── Stripe 付款 ──────────────────────────────────────────

    @Test
    @DisplayName("Stripe 付款的訂房被取消 → 排程經 Stripe 全額退一次（冪等鍵綁定這次退款），存 refund id，付款 REFUNDED、訂房 COMPLETED")
    void stripePaidBooking_isRefundedThroughStripeOnce() {
        String pi = "pi_" + UUID.randomUUID();
        UUID bookingId = buyerBooks(10, null);
        BigDecimal paid = bookingTotal(bookingId);
        stripePaid(bookingId, pi);
        buyerCancels(bookingId);
        stripeRefundsSucceed();

        runSweeper(Instant.now());

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi),
                org.mockito.ArgumentMatchers.argThat(a -> a != null && a.compareTo(paid) == 0),
                eq("Automatic refund: booking cancelled"), eq("refund-" + pi + "-0.00-" + paid.setScale(2)));
        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT stripe_refund_id FROM payments WHERE booking_id = ?",
                String.class, bookingId)).isEqualTo("re_test_1");
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Stripe 拒絕退款 → 整個交易回滾（訂房仍 PENDING、付款仍 SUCCESS、refunded_amount 0）、留失敗稽核、退避期間不重試，"
            + "退避結束且 Stripe 恢復後退款成功")
    void stripeRefundFailure_rollsBackThenRetriesAfterBackoff() {
        String pi = "pi_" + UUID.randomUUID();
        UUID bookingId = buyerBooks(10, null);
        stripePaid(bookingId, pi);
        buyerCancels(bookingId);
        stripeRefundFails(pi);
        Instant t0 = Instant.now();

        runSweeper(t0);

        assertThat(refundStatus(bookingId)).isEqualTo("PENDING");
        assertThat(paymentStatus(bookingId)).as("回滾：付款沒有被標成已退款").isEqualTo("SUCCESS");
        assertThat(paymentRefundedAmount(bookingId)).isEqualByComparingTo("0");
        assertThat(auditCount("AUTO_REFUND_FAILED", bookingId)).isEqualTo(1);
        assertThat(notificationsOf("REFUND_COMPLETED")).as("退款失敗：錢沒退，不能通知買家已退款").isEmpty();
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());

        runSweeper(t0.plus(Duration.ofMinutes(1)));
        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());

        stripeRefundsSucceed();
        runSweeper(t0.plus(Duration.ofMinutes(6)));

        verify(paymentGatewayFactory, times(2)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(notificationsOf("REFUND_COMPLETED")).as("失敗重試後成功，只通知一次").hasSize(1);
    }

    @Test
    @DisplayName("STRIPE_PAYMENT_ENABLED 之後被關閉，已在 Stripe 收下的錢仍經 Stripe 退款")
    void stripePaidBooking_isStillRefundedThroughStripeWhenToggleWasTurnedOff() {
        String pi = "pi_" + UUID.randomUUID();
        UUID bookingId = buyerBooks(10, null);
        stripePaid(bookingId, pi);
        buyerCancels(bookingId);
        stripeRefundsSucceed();
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(false);

        runSweeper(Instant.now());

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
    }

    // ── 併發 ─────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 同一筆已付款訂房被同時取消（多裝置／連點）→ 恰好一個成功、其餘 E-4007；日曆與優惠券只處理一次，退款只決定一次")
    void concurrentCancellations_decideTheRefundOnce() throws Exception {
        String promo = givenPromo();
        UUID bookingId = buyerBooks(10, promo);
        buyerPaysWithMock(bookingId);

        List<Callable<ErrorCode>> cancels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cancels.add(() -> {
                asBuyer();
                try {
                    bookingService.cancelBooking(bookingId, "race");
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            });
        }
        List<ErrorCode> results = race(cancels);

        assertThat(results.stream().filter(Objects::isNull).count()).as("恰好一個取消成功").isEqualTo(1);
        assertThat(results.stream().filter(Objects::nonNull).toList()).containsOnly(ErrorCode.E_4007);
        assertThat(auditCount("BOOKING_REFUND_DECIDED", bookingId)).as("退款只決定一次").isEqualTo(1);
        assertThat(notificationsOf("ORDER_CANCELLED")).as("4 個同時取消只有搶到的那一個通知，買家不會收到 4 則").hasSize(1);
        assertThat(promoUsageCount(promo)).as("優惠券額度只退一次，不會變成負數").isZero();
        runSweeper(Instant.now());
        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(paymentRefundedAmount(bookingId)).isEqualByComparingTo(bookingTotal(bookingId));
    }

    @Test
    @DisplayName("兩個以上的處理者同時處理同一筆待退款訂房（多實例）→ Stripe 只被呼叫一次、只退一次")
    void concurrentSweepers_refundOnlyOnce() throws Exception {
        String pi = "pi_" + UUID.randomUUID();
        UUID bookingId = buyerBooks(10, null);
        BigDecimal paid = bookingTotal(bookingId);
        stripePaid(bookingId, pi);
        buyerCancels(bookingId);
        stripeRefundsSucceed();

        List<Callable<Void>> sweepers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sweepers.add(() -> {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
                refundProcessingService.processPendingBookingRefunds(Instant.now());
                return null;
            });
        }
        race(sweepers);

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(paymentRefundedAmount(bookingId)).as("只退一次，不會多退").isEqualByComparingTo(paid);
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
        assertThat(auditCount("BOOKING_PAYMENT_REFUNDED", paymentId(bookingId))).isEqualTo(1);
        assertThat(notificationsOf("REFUND_COMPLETED")).as("多個處理者同時退，買家只收到一則退款完成通知").hasSize(1);
    }

    // ── DEF-308 訂房側：付款成功時訂房已取消 ─────────────────────

    @Test
    @DisplayName("🔴 DEF-308：買家還在 Stripe 付款頁時先取消訂房，之後付款成功（webhook）→ 錢收了不再靜默：全額排入自動退款並留稽核，"
            + "排程經 Stripe 退回（不適用 24 小時門檻：訂房沒有成立）")
    void paymentAfterCancellation_isFullyRefunded() {
        String pi = "pi_" + UUID.randomUUID();
        UUID bookingId = buyerBooks(0, null);          // 入住前不足 24 小時也一樣全額：付款當下訂房已不存在
        BigDecimal total = bookingTotal(bookingId);
        String sessionId = startStripeCheckout(bookingId);
        buyerCancels(bookingId);                        // CREATED → CANCELLED，沒付款所以沒有退款
        assertThat(refundStatus(bookingId)).isEqualTo("NONE");
        stripeRefundsSucceed();

        TenantContext.clear();
        SecurityContextHolder.clearContext();
        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId, pi));

        assertThat(bookingStatus(bookingId)).as("訂房不會被拉回已付款（日曆可能已被別人訂走）").isEqualTo("CANCELLED");
        assertThat(refundStatus(bookingId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT refund_amount FROM bookings WHERE id = ?", BigDecimal.class,
                bookingId)).isEqualByComparingTo(total);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM audit_log WHERE entity_id = ? AND action = 'STRIPE_PAYMENT_BOOKING_NOT_PAYABLE'",
                String.class, paymentId(bookingId))).contains("refundQueued=true");

        runSweeper(Instant.now());

        verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
        assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
        assertThat(refundStatus(bookingId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("付款成功與取消搶同一個狀態，恰好一邊成功：先付款 → 取消走一般的已付款取消；先取消 → 付款走退款——兩種順序最後都退回買家的錢")
    void paymentAndCancellationRace_bothOrdersEndInRefund() throws Exception {
        for (int round = 0; round < 3; round++) {
            String pi = "pi_" + UUID.randomUUID();
            UUID bookingId = buyerBooks(10 + round * 4, null);
            String sessionId = startStripeCheckout(bookingId);
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
                            bookingService.cancelBooking(bookingId, "race");
                        } catch (RuntimeException ignored) {
                            // 付款搶先提交、取消讀到舊快照而被拒（E-4007）——合法，下面補按一次
                        }
                        return null;
                    });
            race(calls);

            if ("PAID".equals(bookingStatus(bookingId))) {
                buyerCancels(bookingId);
            }
            assertThat(bookingStatus(bookingId)).as("round %d", round).isEqualTo("CANCELLED");
            assertThat(refundStatus(bookingId)).as("round %d：兩種順序最後都排入退款", round).isEqualTo("PENDING");
            runSweeper(Instant.now());
            assertThat(paymentStatus(bookingId)).isEqualTo("REFUNDED");
            verify(paymentGatewayFactory, times(1)).processRefund(eq("STRIPE"), eq(pi), any(), any(), any());
            assertThat(bookedNights(bookingId)).as("日曆沒有殘留").isZero();
        }
    }

    // ── 買家通知（Sprint 229，PRD US-005「取消後即時收到退款狀態通知」）─────────────

    @Test
    @DisplayName("取消已付款訂房 → 買家立刻收到站內通知（說明全額退款處理中）；排程退回後再收到「退款已完成」。"
            + "資料列真的寫進資料庫：取消交易提交之後才送，且新交易的寫入有被提交")
    void cancellationAndRefundCompletion_notifyTheBuyer() {
        UUID bookingId = buyerBooks(10, null);
        buyerPaysWithMock(bookingId);

        buyerCancels(bookingId);

        List<Map<String, Object>> cancelled = notificationsOf("ORDER_CANCELLED");
        assertThat(cancelled).as("取消後即時一則通知").hasSize(1);
        assertThat(cancelled.get(0).get("title")).isEqualTo("訂房已取消");
        assertThat((String) cancelled.get(0).get("content")).contains("Booking Refund Room", "您已取消", "退款 NT$3,000",
                "已進入處理");
        assertThat((String) cancelled.get(0).get("data")).contains(bookingId.toString());
        assertThat(notificationsOf("REFUND_COMPLETED")).as("錢還沒退，不能先說退款完成").isEmpty();

        runSweeper(Instant.now());

        List<Map<String, Object>> refunded = notificationsOf("REFUND_COMPLETED");
        assertThat(refunded).as("排程退回後一則退款完成通知（REFUND_COMPLETED 通過資料庫約束）").hasSize(1);
        assertThat(refunded.get(0).get("title")).isEqualTo("退款已完成");
        assertThat((String) refunded.get(0).get("content")).contains("Booking Refund Room", "NT$3,000", "已退回原付款方式");
        assertThat((String) refunded.get(0).get("data")).contains(bookingId.toString());
    }

    @Test
    @DisplayName("入住前不足 24 小時取消 → 通知明說依政策不退款；排程不會再補一則「退款完成」")
    void tooLateCancellation_isToldNoRefund_andNeverGetsARefundNotice() {
        UUID bookingId = buyerBooks(0, null);
        buyerPaysWithMock(bookingId);

        buyerCancels(bookingId);
        runSweeper(Instant.now());

        List<Map<String, Object>> cancelled = notificationsOf("ORDER_CANCELLED");
        assertThat(cancelled).hasSize(1);
        assertThat((String) cancelled.get(0).get("content")).contains("不足 24 小時", "不予退款");
        assertThat(notificationsOf("REFUND_COMPLETED")).isEmpty();
    }

    @Test
    @DisplayName("取消未付款訂房 → 通知說明尚未付款、不需退款")
    void unpaidCancellation_isToldNothingToRefund() {
        UUID bookingId = buyerBooks(10, null);

        buyerCancels(bookingId);

        List<Map<String, Object>> cancelled = notificationsOf("ORDER_CANCELLED");
        assertThat(cancelled).hasSize(1);
        assertThat((String) cancelled.get(0).get("content")).contains("尚未付款", "不需退款");
    }

    @Test
    @DisplayName("商家／平台（管理員）代為取消 → 被取消的買家收到通知，點名是商家取消並說明全額退款")
    void merchantCancellation_notifiesTheBuyer() {
        UUID bookingId = buyerBooks(0, null);
        buyerPaysWithMock(bookingId);

        asAdmin();
        bookingService.cancelBooking(bookingId, "host cancelled");

        List<Map<String, Object>> cancelled = notificationsOf("ORDER_CANCELLED");
        assertThat(cancelled).as("通知的收件人是訂房的買家，不是操作的管理員").hasSize(1);
        assertThat((String) cancelled.get(0).get("content")).contains("商家已取消您的訂房", "退款 NT$3,000");
    }

    // ── 其他 ─────────────────────────────────────────────────

    @Test
    @DisplayName("已不在 PAID／CREATED 的訂房（已取消）再取消 → E-4007，不會再決定一次退款")
    void cancellingTwiceSequentially_isRejected() {
        UUID bookingId = buyerBooks(10, null);
        buyerPaysWithMock(bookingId);
        buyerCancels(bookingId);

        assertThatThrownBy(() -> buyerCancels(bookingId)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_4007);
        assertThat(auditCount("BOOKING_REFUND_DECIDED", bookingId)).isEqualTo(1);
        assertThat(notificationsOf("ORDER_CANCELLED")).as("被拒絕的第二次取消不會再通知一次").hasSize(1);
    }

    // ── 固件與身分 ──────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runSweeper(final Instant now) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        refundProcessingService.processPendingBookingRefunds(now);
    }

    private <T> List<T> race(final List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
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
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /** 買家訂兩晚；{@code daysAhead} 是入住日離今天（營運時區）幾天，0＝今天入住（離入住不足 24 小時）。 */
    private UUID buyerBooks(final int daysAhead, final String promoCode) {
        asBuyer();
        LocalDate checkIn = BusinessTime.today().plusDays(daysAhead);
        return bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("Test Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .promoCode(promoCode).build(), null).getId();
    }

    private void buyerPaysWithMock(final UUID bookingId) {
        asBuyer();
        paymentStateService.mockBookingPaymentSuccess(bookingId);
    }

    private BookingDto.CancelResponse buyerCancels(final UUID bookingId) {
        asBuyer();
        return bookingService.cancelBooking(bookingId, "changed my mind");
    }

    /** 買家發起 Stripe 結帳（Stripe 閘道是 mock），回傳 Checkout Session id。 */
    private String startStripeCheckout(final UUID bookingId) {
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(true);
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any())).thenAnswer(invocation -> {
            String sessionId = "cs_test_" + UUID.randomUUID();
            return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                    .sessionId(sessionId).sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId)
                    .status("open").paymentStatus("unpaid").build();
        });
        asBuyer();
        return paymentStateService.initiateStripeBookingCheckout(bookingId).getSessionId();
    }

    /** 走完真實的 Stripe 付款：結帳 → webhook 通知付款成功（訂房 PAID）。 */
    private void stripePaid(final UUID bookingId, final String paymentIntentId) {
        String sessionId = startStripeCheckout(bookingId);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId, paymentIntentId));
        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
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

    private void stripeRefundFails(final String paymentIntentId) {
        lenient().when(paymentGatewayFactory.processRefund(eq("STRIPE"), eq(paymentIntentId), any(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(false).errorMessage("charge_already_refunded").build());
    }

    private String givenPromo() {
        String code = "BKREFUND" + System.nanoTime();
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
        act(buyer, tenant);
    }

    private void asAdmin() {
        act(admin, tenant);
    }

    /** Sprint 231（DEF-316）：本租戶店主，非訂房買家本人，用於驗證 checkBookingOwnership 的 same-tenant 分支。 */
    private void asStoreOwner() {
        act(storeOwner, tenant);
    }

    /** Sprint 231（DEF-316）：他租戶店主，用於驗證跨租戶取消仍被擋下。 */
    private void asOtherTenantStoreOwner() {
        act(otherTenantStoreOwner, otherTenant);
    }

    private void act(final User user, final Tenant actingTenant) {
        TenantContext.setCurrentUser(user.getId());
        TenantContext.setCurrentTenant(actingTenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(user.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId().toString(), null, authorities));
    }

    private void seed() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder().name("Booking Refund Tenant").slug("booking-refund-" + stamp)
                .contactEmail("booking-refund-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        storeOwner = userRepository.save(User.builder().email("booking-refund-host-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Host").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        buyer = userRepository.save(User.builder().email("booking-refund-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        admin = userRepository.save(User.builder().email("booking-refund-admin-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Admin").role(User.UserRole.ADMIN).status("ACTIVE").build());
        roomListingId = listingRepository.save(Listing.builder().tenant(tenant).owner(storeOwner)
                .listingType(Listing.ListingType.ROOM).title("Booking Refund Room")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        otherTenant = tenantRepository.save(Tenant.builder().name("Booking Refund Other Tenant")
                .slug("booking-refund-other-" + stamp)
                .contactEmail("booking-refund-other-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        otherTenantStoreOwner = userRepository.save(User.builder()
                .email("booking-refund-other-host-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Other Host").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(otherTenant.getId()).build());
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
        // integration-test profile 以 ddl-auto=update 建表，沒有 Flyway V79 的 payments.idempotency_key 唯一索引；補上生產有的那條
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS it_booking_checkout_key_unique ON payments "
                + "(idempotency_key) WHERE idempotency_key LIKE 'BOOKING-CHECKOUT-%'");
    }

    private String bookingStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private String refundStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT refund_status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private BigDecimal bookingTotal(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM bookings WHERE id = ?", BigDecimal.class,
                bookingId);
    }

    private String paymentStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE booking_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", String.class, bookingId);
    }

    private BigDecimal paymentRefundedAmount(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT refunded_amount FROM payments WHERE booking_id = ? "
                + "AND status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED')", BigDecimal.class, bookingId);
    }

    private UUID paymentId(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT id FROM payments WHERE booking_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", UUID.class, bookingId);
    }

    private int bookedNights(final UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_calendar WHERE booking_id = ? AND status = 'BOOKED'", Integer.class,
                bookingId);
    }

    private int promoUsageCount(final String code) {
        return jdbcTemplate.queryForObject("SELECT current_usage_count FROM promo_codes WHERE code = ?",
                Integer.class, code);
    }

    /** 這個測試的買家收到的某類通知（每個測試都建新買家，所以不會混到共用資料庫裡別人的通知）。 */
    private List<Map<String, Object>> notificationsOf(final String type) {
        return jdbcTemplate.queryForList("SELECT title, content, data::text AS data FROM notifications "
                + "WHERE user_id = ? AND notification_type = ? ORDER BY created_at", buyer.getId(), type);
    }

    private int auditCount(final String action, final UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, entityId, action);
    }
}
