package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.context.TestPropertySource;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingNoShowService;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * no-show 自動取消（Sprint 246，DEF-352；PRD §17.4.6 Q15）：真實 PostgreSQL，走真實的訂房、付款服務方法。
 *
 * <p>只驗證單元測試（mock Repository）碰不到的部分——{@link com.nextkey.ecommerce.domain.repository.BookingRepository
 * #findPotentialNoShowBookingIds} 與 {@code #cancelIfNoShow} 這兩條 JPQL 在真實資料庫能不能正確執行、
 * {@code @ConditionalOnProperty} 開啟後 context 能不能正常啟動並注入；寬限期限的精算邏輯已由
 * {@code BookingNoShowServiceTest} 以 mock 驗證過，這裡不重複。
 *
 * <p>入住日用 JDBC 直接改到過去（訂房建立時只能填今天或未來，{@code @FutureOrPresent}），不影響 room_calendar——
 * 入住日已過的日期本來就不會再被選訂，兩者不同步沒有實際影響（同類測試手法見 {@code BookingTimeoutIntegrationTest}
 * 對 {@code payment_due_at} 的處理）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@TestPropertySource(properties = "app.booking-no-show.enabled=true")
@DisplayName("IT-BOOKING-NOSHOW: no-show 自動取消（Sprint 246，DEF-352）")
class BookingNoShowIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";

    @Autowired private BookingNoShowService bookingNoShowService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private FeatureToggleService featureToggleService;

    private Tenant tenant;
    private User buyer;
    private UUID roomListingId;
    private int nextNight;

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
    @DisplayName("已付款、入住時刻＋24 小時已過仍未入住 → 取消（SYSTEM）、不退款、稽核與買家通知都正確，且 JPQL 候選查詢能在真實 DB 跑")
    void paidBookingPastGracePeriod_isCancelledWithoutRefund() {
        UUID bookingId = buyerBooksAndPays();
        backdateCheckIn(bookingId, LocalDate.now().minusDays(2));

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT cancelled_by, cancelled_at, refund_status FROM bookings WHERE id = ?", bookingId);
        assertThat(row.get("cancelled_by")).isEqualTo("SYSTEM");
        assertThat(row.get("cancelled_at")).isNotNull();
        assertThat(row.get("refund_status")).as("no-show 依使用者決定不退款（DEF-351）").isEqualTo("NONE");

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT old_value, new_value, user_id FROM audit_log WHERE entity_id = ? AND action = 'BOOKING_CANCELLED'",
                bookingId);
        assertThat(audit.get("old_value")).isEqualTo("PAID");
        assertThat(audit.get("new_value")).isEqualTo("CANCELLED");
        assertThat(audit.get("user_id")).as("系統取消沒有操作使用者").isNull();

        List<Map<String, Object>> notices = notificationsOfBuyer();
        assertThat(notices).as("取消後通知買家一次").hasSize(1);
        assertThat(notices.get(0).get("title")).isEqualTo("訂房因未入住已取消");
        assertThat((String) notices.get(0).get("content")).contains("24 小時內未辦理入住", "不退款");
    }

    @Test
    @DisplayName("入住日是明天（還在寬限期內）→ 不動，不通知")
    void paidBookingWithinGracePeriod_isUntouched() {
        UUID bookingId = buyerBooksAndPays();
        backdateCheckIn(bookingId, LocalDate.now().plusDays(1));

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(notificationsOfBuyer()).isEmpty();
    }

    @Test
    @DisplayName("已入住的訂房即使入住日是很久以前也不動——只處理仍是 PAID 的訂房")
    void checkedInBooking_isUntouched() {
        UUID bookingId = buyerBooksAndPays();
        backdateCheckIn(bookingId, LocalDate.now().minusDays(2));
        asOwner();
        bookingService.checkIn(bookingId);

        runJobAsScheduler();

        assertThat(bookingStatus(bookingId)).isEqualTo("CHECKED_IN");
    }

    @Test
    @DisplayName("重複執行不會重複取消或重複通知")
    void runningTwice_cancelsAndNotifiesOnce() {
        UUID bookingId = buyerBooksAndPays();
        backdateCheckIn(bookingId, LocalDate.now().minusDays(2));

        runJobAsScheduler();
        runJobAsScheduler();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = 'BOOKING_CANCELLED'",
                Integer.class, bookingId)).isEqualTo(1);
        assertThat(notificationsOfBuyer()).hasSize(1);
    }

    // ── 固件與身分 ──────────────────────────────────────────

    private void runJobAsScheduler() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        bookingNoShowService.cancelNoShowBookings(Instant.now());
    }

    private UUID buyerBooksAndPays() {
        asBuyer();
        LocalDate checkIn = LocalDate.now().plusDays(nextNight);
        nextNight += 3;
        UUID bookingId = bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("No-Show Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .build(), null).getId();
        asBuyer();
        paymentStateService.mockBookingPaymentSuccess(bookingId);
        return bookingId;
    }

    /** 把入住日（與退房日，維持 2 晚）改到 {@code checkIn}；不動 room_calendar，見類別說明。 */
    private void backdateCheckIn(final UUID bookingId, final LocalDate checkIn) {
        jdbcTemplate.update("UPDATE bookings SET check_in_date = ?, check_out_date = ? WHERE id = ?",
                checkIn, checkIn.plusDays(2), bookingId);
    }

    private void asBuyer() {
        TenantContext.setCurrentUser(buyer.getId());
        TenantContext.setCurrentTenant(tenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(buyer.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(buyer.getId().toString(), null, authorities));
    }

    private void asOwner() {
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenant.getId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_STORE_OWNER"))));
    }

    private void seed() {
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Booking No-Show Tenant")
                .slug("booking-noshow-" + stamp)
                .contactEmail("booking-noshow-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User host = userRepository.save(User.builder()
                .email("booking-noshow-host-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Booking No-Show Host")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
        buyer = userRepository.save(User.builder()
                .email("booking-noshow-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Booking No-Show Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
        roomListingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(host)
                .listingType(Listing.ListingType.ROOM)
                .title("No-Show Room")
                .basePrice(new BigDecimal("1500.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())",
                roomListingId);
        nextNight = 5;
    }

    private List<Map<String, Object>> notificationsOfBuyer() {
        return jdbcTemplate.queryForList("SELECT notification_type, title, content, data::text AS data "
                + "FROM notifications WHERE user_id = ? ORDER BY created_at", buyer.getId());
    }

    private String bookingStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }
}
