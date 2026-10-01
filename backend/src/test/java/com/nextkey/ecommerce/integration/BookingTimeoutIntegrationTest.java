package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.booking.BookingTimeoutService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
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
 * 未付款訂房逾時自動取消（Sprint 225，DEF-311；使用者 2026-10-01 對建議方案回覆「依照建議」）。
 *
 * <p>真實 PostgreSQL＋真實 Redis（日曆鎖），走真實的訂房、付款服務方法；「付款期限已過」以 JDBC 改
 * {@code payment_due_at} 模擬。取消用的批次查詢與條件式 UPDATE 都是 JPQL（含 NOT EXISTS 子查詢），mock Repository 的
 * 測試碰不到它們，必須在真實資料庫執行過。
 *
 * <p>核心的業務保證有兩個：一，新訂房才有付款期限、歷史訂房（{@code payment_due_at} 為 NULL）<b>永不</b>因逾時被取消——
 * 它們在付款入口出現之前就存在了，追溯適用會在部署後第一輪排程把它們全部取消；二，取消與買家付款搶同一個狀態。
 *
 * <p>共用測試資料庫裡可能有其他測試留下的舊訂房，所以斷言都針對本測試自己建立的訂房，不比對總筆數。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-BOOKING-TIMEOUT: 未付款訂房逾時自動取消（Sprint 225）")
class BookingTimeoutIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final String NIGHTS_PRICE = "3000.00";

    @Autowired private BookingTimeoutService bookingTimeoutService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private PaymentRepository paymentRepository;

    @MockBean private FeatureToggleService featureToggleService;

    private Tenant tenant;
    private User buyer;
    private UUID roomListingId;
    private int nextNight;

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
    }

    @Test
    @DisplayName("新訂房建立時寫入付款期限＝建立時間＋24 小時（真實 DB 的欄位對應）")
    void newBooking_carriesPaymentDeadline() {
        UUID bookingId = buyerBooks(null);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT payment_due_at, created_at FROM bookings WHERE id = ?", bookingId);
        Instant due = ((Timestamp) row.get("payment_due_at")).toInstant();
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        assertThat(Duration.between(created, due)).as("付款期限 ＝ 建立時間 ＋ 24 小時（容許交易內的秒級誤差）")
                .isBetween(Duration.ofHours(24).minusSeconds(5), Duration.ofHours(24).plusSeconds(5));
    }

    @Test
    @DisplayName("付款期限已過仍未付款 → 取消：日曆釋放、優惠券額度退還，稽核記為系統操作")
    void expiredUnpaidBooking_isCancelledAndCompensated() {
        String promo = givenPromo();
        UUID bookingId = buyerBooks(promo);
        assertThat(bookedNights(bookingId)).isEqualTo(2);
        assertThat(promoUsageCount(promo)).isEqualTo(1);
        expire(bookingId, Duration.ofMinutes(5));

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        assertThat(bookedNights(bookingId)).as("日曆已釋放").isZero();
        assertThat(promoUsageCount(promo)).as("優惠券額度退還").isZero();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT old_value, new_value, reason, user_id FROM audit_log "
                        + "WHERE entity_id = ? AND action = 'BOOKING_CANCELLED'", bookingId);
        assertThat(audit.get("old_value")).isEqualTo("CREATED");
        assertThat(audit.get("new_value")).isEqualTo("CANCELLED");
        assertThat(audit.get("reason")).isEqualTo("Unpaid booking timed out");
        assertThat(audit.get("user_id")).as("系統取消沒有操作使用者").isNull();
        // Sprint 227：PRD §15.2.5 的取消方／取消時間；逾時取消是 SYSTEM，未付款所以沒有退款
        Map<String, Object> cancelled = jdbcTemplate.queryForMap(
                "SELECT cancelled_by, cancelled_at, refund_status FROM bookings WHERE id = ?", bookingId);
        assertThat(cancelled.get("cancelled_by")).isEqualTo("SYSTEM");
        assertThat(cancelled.get("cancelled_at")).isNotNull();
        assertThat(cancelled.get("refund_status")).isEqualTo("NONE");

        // Sprint 229（PRD US-014）：逾時取消後買家收到站內通知；data 帶 listingId，前端用它提供「重新預訂」連結
        List<Map<String, Object>> notices = notificationsOfBuyer();
        assertThat(notices).as("取消後通知買家一次").hasSize(1);
        assertThat(notices.get(0).get("notification_type")).isEqualTo("ORDER_CANCELLED");
        assertThat(notices.get(0).get("title")).isEqualTo("訂房因逾期未付款已取消");
        assertThat((String) notices.get(0).get("content")).contains("Booking Timeout Room", "超過付款期限", "重新預訂");
        assertThat((String) notices.get(0).get("data")).contains(bookingId.toString(), roomListingId.toString(),
                "PAYMENT_TIMEOUT");
    }

    @Test
    @DisplayName("付款期限還沒到 → 不動")
    void bookingWithinDeadline_isUntouched() {
        UUID bookingId = buyerBooks(null);
        jdbcTemplate.update("UPDATE bookings SET payment_due_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofHours(1))), bookingId);

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(bookedNights(bookingId)).isEqualTo(2);
        assertThat(notificationsOfBuyer()).as("沒取消就不通知").isEmpty();
    }

    @Test
    @DisplayName("歷史訂房（付款期限為 NULL）建立多久都不取消——它們在付款入口出現前就存在，不能追溯適用")
    void legacyBookingWithoutDeadline_neverExpires() {
        UUID bookingId = buyerBooks(null);
        jdbcTemplate.update("UPDATE bookings SET payment_due_at = NULL, created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(30))), bookingId);

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(bookedNights(bookingId)).as("日曆仍為已預訂").isEqualTo(2);
    }

    @Test
    @DisplayName("已付款的訂房即使付款期限已過也不動")
    void paidBooking_isUntouched() {
        UUID bookingId = buyerBooks(null);
        buyerPays(bookingId);
        expire(bookingId, Duration.ofHours(6));

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(bookedNights(bookingId)).isEqualTo(2);
        assertThat(notificationsOfBuyer()).as("已付款的訂房沒有被取消，不能對買家說「逾期未付款已取消」").isEmpty();
    }

    @Test
    @DisplayName("24 小時內開始過 Stripe 結帳（工作階段可能仍開著）→ 不取消；工作階段也已過期才取消")
    void recentStripeCheckout_blocksCancellationUntilSessionExpires() {
        UUID bookingId = buyerBooks(null);
        expire(bookingId, Duration.ofHours(1));
        UUID paymentId = givenProcessingStripePayment(bookingId);
        agePayment(paymentId, Duration.ofHours(1));

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).as("結帳工作階段 1 小時前才建立，預設 24 小時內仍可付款")
                .isEqualTo("CREATED");
        assertThat(bookedNights(bookingId)).isEqualTo(2);

        agePayment(paymentId, Duration.ofHours(25));
        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        assertThat(bookedNights(bookingId)).isZero();
    }

    @Test
    @DisplayName("已有成功付款紀錄的 CREATED 訂房（異常資料）→ 不當成未付款取消")
    void bookingWithSuccessfulPayment_isNeverCancelledAsUnpaid() {
        UUID bookingId = buyerBooks(null);
        expire(bookingId, Duration.ofHours(6));
        UUID paymentId = givenProcessingStripePayment(bookingId);
        jdbcTemplate.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?", paymentId);

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("取消用的條件式 UPDATE 自己也把關（不依賴候選查詢）：未逾時、歷史、已付款、24 小時內開始過結帳、已有成功付款都回 false")
    void cancelExpiredUnpaidBooking_guardsItself() {
        // 候選查詢只是挑人；買家付款、開始結帳都可能發生在「挑出來」與「取消」之間，把關必須在 UPDATE 本身
        Instant now = Instant.now();
        Instant checkoutCutoff = now.minus(Duration.ofHours(24));

        UUID fresh = buyerBooks(null);
        assertThat(bookingService.cancelExpiredUnpaidBooking(fresh, now, checkoutCutoff)).as("期限未到").isFalse();

        UUID legacy = buyerBooks(null);
        jdbcTemplate.update("UPDATE bookings SET payment_due_at = NULL, created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(30))), legacy);
        assertThat(bookingService.cancelExpiredUnpaidBooking(legacy, now, checkoutCutoff)).as("歷史訂房").isFalse();

        UUID paid = buyerBooks(null);
        buyerPays(paid);
        expire(paid, Duration.ofHours(6));
        assertThat(bookingService.cancelExpiredUnpaidBooking(paid, now, checkoutCutoff)).as("已付款").isFalse();
        assertThat(bookingStatus(paid)).isEqualTo("PAID");

        UUID checkingOut = buyerBooks(null);
        expire(checkingOut, Duration.ofHours(1));
        agePayment(givenProcessingStripePayment(checkingOut), Duration.ofHours(1));
        assertThat(bookingService.cancelExpiredUnpaidBooking(checkingOut, now, checkoutCutoff))
                .as("1 小時前開始結帳").isFalse();

        UUID succeeded = buyerBooks(null);
        expire(succeeded, Duration.ofHours(6));
        jdbcTemplate.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?",
                givenProcessingStripePayment(succeeded));
        assertThat(bookingService.cancelExpiredUnpaidBooking(succeeded, now, checkoutCutoff))
                .as("已有成功付款").isFalse();

        for (UUID untouched : List.of(fresh, legacy, checkingOut, succeeded)) {
            assertThat(bookingStatus(untouched)).isEqualTo("CREATED");
            assertThat(bookedNights(untouched)).as("這五筆都沒被取消，日曆都還在").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("被取消後買家再付款 → E-5011，訂房維持 CANCELLED（與付款搶同一個狀態，恰好一邊成功）")
    void payAfterTimeoutCancel_isRejected() {
        UUID bookingId = buyerBooks(null);
        expire(bookingId, Duration.ofMinutes(5));
        runJobAsScheduler();
        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");

        asBuyer();
        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5011);
        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        assertThat(paymentCount(bookingId)).as("沒有留下任何付款紀錄").isZero();
    }

    @Test
    @DisplayName("重複執行不會重複補償：優惠券額度只退一次")
    void runningTwice_compensatesOnce() {
        String promo = givenPromo();
        UUID bookingId = buyerBooks(promo);
        expire(bookingId, Duration.ofMinutes(5));

        runJobAsScheduler();
        runJobAsScheduler();

        assertThat(promoUsageCount(promo)).as("額度只退一次，不會變成負數").isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = 'BOOKING_CANCELLED'",
                Integer.class, bookingId)).isEqualTo(1);
        assertThat(notificationsOfBuyer()).as("只有真的取消的那一輪通知，第二輪不再通知").hasSize(1);
    }

    // ── 固件與身分 ──────────────────────────────────────────

    /** 模擬排程執行緒：沒有登入使用者、沒有租戶內容。 */
    private void runJobAsScheduler() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        bookingTimeoutService.cancelExpiredUnpaidBookings(Instant.now());
    }

    private UUID buyerBooks(final String promoCode) {
        asBuyer();
        LocalDate checkIn = LocalDate.now().plusDays(nextNight);
        nextNight += 3;
        return bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("Test Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .promoCode(promoCode).build(), null).getId();
    }

    private void buyerPays(final UUID bookingId) {
        asBuyer();
        paymentStateService.mockBookingPaymentSuccess(bookingId);
    }

    private UUID givenProcessingStripePayment(final UUID bookingId) {
        return paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(new BigDecimal(NIGHTS_PRICE))
                .currency("TWD")
                .status(Payment.PaymentStatus.PROCESSING)
                .transactionId("cs_test_" + UUID.randomUUID())
                .build()).getId();
    }

    /** 讓付款期限落在 {@code overdue} 之前（模擬「訂房建立已超過 24 小時」）。 */
    private void expire(final UUID bookingId, final Duration overdue) {
        jdbcTemplate.update("UPDATE bookings SET payment_due_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(overdue)), bookingId);
    }

    private void agePayment(final UUID paymentId, final Duration age) {
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(age)), paymentId);
    }

    private String givenPromo() {
        String code = "BKTIMEOUT" + System.nanoTime();
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
                .name("Booking Timeout Tenant")
                .slug("booking-timeout-" + stamp)
                .contactEmail("booking-timeout-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User host = userRepository.save(User.builder()
                .email("booking-timeout-host-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Booking Timeout Host")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
        buyer = userRepository.save(User.builder()
                .email("booking-timeout-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Booking Timeout Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
        roomListingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(host)
                .listingType(Listing.ListingType.ROOM)
                .title("Booking Timeout Room")
                .basePrice(new BigDecimal("1500.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())",
                roomListingId);
        nextNight = 5;
    }

    /** 這個測試的買家收到的通知（每個測試都建新買家，不會混到共用資料庫裡別人的通知）。 */
    private List<Map<String, Object>> notificationsOfBuyer() {
        return jdbcTemplate.queryForList("SELECT notification_type, title, content, data::text AS data "
                + "FROM notifications WHERE user_id = ? ORDER BY created_at", buyer.getId());
    }

    private String bookingStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private int bookedNights(final UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_calendar WHERE booking_id = ? AND status = 'BOOKED'",
                Integer.class, bookingId);
    }

    private int promoUsageCount(final String code) {
        return jdbcTemplate.queryForObject("SELECT current_usage_count FROM promo_codes WHERE code = ?",
                Integer.class, code);
    }

    private int paymentCount(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ?",
                Integer.class, bookingId);
    }
}
