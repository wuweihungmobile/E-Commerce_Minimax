package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * {@link BookingService#checkIn}／{@link BookingService#checkOut}（Sprint 245，DEF-345；PRD Phase 1 預訂路徑）。
 *
 * <p>這裡守的是「規則接對了」：只能從正確的狀態進入、入住日還沒到不可入住、只有同租戶商家與管理員能操作（買家本人不算）、
 * 併發搶不到就拒絕、退房後在同一次呼叫內自動完成。控制器的 {@code booking:update} 權限註解與 HTTP 形狀由
 * {@code BookingApiRealStackIntegrationTest} 以真實權限表與真實資料庫驗證。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService 入住與退房（Sprint 245，DEF-345）")
class BookingServiceCheckInOutTest {

    /** 固定「現在」為 2026-10-04 12:00（台北）；營運時區的今天就是 {@link #TODAY}。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private AuditService auditService;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private BookingService bookingService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID store = UUID.randomUUID();
    private final UUID otherStore = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    @BeforeEach
    void fixToday() {
        BusinessTime.useClockForTesting(Clock.fixed(Instant.parse("2026-10-04T04:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Booking booking(final Booking.BookingStatus status, final LocalDate checkIn) {
        return Booking.builder().id(bookingId).tenantId(store).userId(buyer).roomListingId(UUID.randomUUID())
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2)).guestCount(2).status(status)
                .totalAmount(new BigDecimal("3000.00")).build();
    }

    private void givenBooking(final Booking booking) {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
    }

    private void givenTransition(final Booking.BookingStatus from, final Booking.BookingStatus to, final int rows) {
        when(bookingRepository.updateStatusIfCurrent(bookingId, from, to)).thenReturn(rows);
    }

    private void actAsStoreOf(final UUID tenantId) {
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_STORE_OWNER"))));
    }

    private void actAsAdmin() {
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    private void actAsBuyer() {
        TenantContext.setCurrentUser(buyer);
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
    }

    private static void assertRejectedWith(final ErrorCode expected, final ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ── 入住 ─────────────────────────────────────────────────

    @Test
    @DisplayName("已付款且入住日是今天：同租戶商家可入住，PAID→CHECKED_IN，稽核記下這次轉換，回應帶出新狀態")
    void paidBookingDueToday_checksInAndAuditsTheTransition() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY));
        givenTransition(Booking.BookingStatus.PAID, Booking.BookingStatus.CHECKED_IN, 1);
        actAsStoreOf(store);

        BookingDto.BookingResponse response = bookingService.checkIn(bookingId);

        assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
        verify(auditService).record("BOOKING_CHECKED_IN", "BOOKING", bookingId, store,
                "PAID", "CHECKED_IN", null);
    }

    @Test
    @DisplayName("入住日已過仍可補記入住：規則只擋「入住日還沒到」，不擋事後補記（本輪假設，PRD 未限制）")
    void stayThatAlreadyStarted_canStillBeRecordedAsCheckedIn() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY.minusDays(1)));
        givenTransition(Booking.BookingStatus.PAID, Booking.BookingStatus.CHECKED_IN, 1);
        actAsStoreOf(store);

        assertThat(bookingService.checkIn(bookingId).getStatus()).isEqualTo("CHECKED_IN");
    }

    @Test
    @DisplayName("入住日還沒到不可入住：拒絕（E-5010），不動資料列、不寫稽核")
    void checkInDateStillAhead_isRejectedWithoutTouchingTheRow() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY.plusDays(1)));
        actAsStoreOf(store);

        assertRejectedWith(ErrorCode.E_5010, () -> bookingService.checkIn(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = Booking.BookingStatus.class,
            names = {"CREATED", "CONFIRMED", "CHECKED_IN", "CHECKED_OUT", "COMPLETED", "CANCELLED"})
    @DisplayName("只有已付款的訂房能入住：其他狀態一律拒絕（E-5010），不碰資料列；CONFIRMED 本系統不會產生，同樣拒絕")
    void onlyAPaidBookingCanBeCheckedIn(final Booking.BookingStatus status) {
        givenBooking(booking(status, TODAY));
        actAsStoreOf(store);

        assertRejectedWith(ErrorCode.E_5010, () -> bookingService.checkIn(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("併發搶不到（讀取後狀態已被別人改掉）：條件式 UPDATE 回 0 列 → 拒絕（E-5010），不寫稽核")
    void losingARaceToAConcurrentChange_isRejected() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY));
        givenTransition(Booking.BookingStatus.PAID, Booking.BookingStatus.CHECKED_IN, 0);
        actAsStoreOf(store);

        assertRejectedWith(ErrorCode.E_5010, () -> bookingService.checkIn(bookingId));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("買家本人不能辦理入住：服務層不靠控制器的權限註解擋買家，即使他是訂房的主人也拒絕（E-1007）")
    void buyerWhoOwnsTheBooking_cannotCheckIn() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY));
        actAsBuyer();

        assertRejectedWith(ErrorCode.E_1007, () -> bookingService.checkIn(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("其他租戶的商家不能辦理入住（E-1007），不動資料列")
    void storeOfAnotherTenant_cannotCheckIn() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY));
        actAsStoreOf(otherStore);

        assertRejectedWith(ErrorCode.E_1007, () -> bookingService.checkIn(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("管理員可代為辦理任何店鋪的入住，與取消、更新的管理員放行一致")
    void admin_canCheckInAnyStoresBooking() {
        givenBooking(booking(Booking.BookingStatus.PAID, TODAY));
        givenTransition(Booking.BookingStatus.PAID, Booking.BookingStatus.CHECKED_IN, 1);
        actAsAdmin();

        assertThat(bookingService.checkIn(bookingId).getStatus()).isEqualTo("CHECKED_IN");
    }

    // ── 退房 ─────────────────────────────────────────────────

    @Test
    @DisplayName("入住中退房：CHECKED_IN→CHECKED_OUT→COMPLETED 在同一次呼叫內依序完成，兩段各寫稽核，回應是 COMPLETED")
    void checkedInBooking_checksOutAndCompletesInTheSameCall() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        givenTransition(Booking.BookingStatus.CHECKED_IN, Booking.BookingStatus.CHECKED_OUT, 1);
        givenTransition(Booking.BookingStatus.CHECKED_OUT, Booking.BookingStatus.COMPLETED, 1);
        actAsStoreOf(store);

        BookingDto.BookingResponse response = bookingService.checkOut(bookingId);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        InOrder order = inOrder(bookingRepository, auditService);
        order.verify(bookingRepository).updateStatusIfCurrent(bookingId,
                Booking.BookingStatus.CHECKED_IN, Booking.BookingStatus.CHECKED_OUT);
        order.verify(auditService).record("BOOKING_CHECKED_OUT", "BOOKING", bookingId, store,
                "CHECKED_IN", "CHECKED_OUT", null);
        order.verify(bookingRepository).updateStatusIfCurrent(bookingId,
                Booking.BookingStatus.CHECKED_OUT, Booking.BookingStatus.COMPLETED);
        order.verify(auditService).record("BOOKING_COMPLETED", "BOOKING", bookingId, store,
                "CHECKED_OUT", "COMPLETED", "auto-completed after check-out");
    }

    @ParameterizedTest
    @EnumSource(value = Booking.BookingStatus.class,
            names = {"CREATED", "PAID", "CONFIRMED", "CHECKED_OUT", "COMPLETED", "CANCELLED"})
    @DisplayName("只有入住中的訂房能退房：未入住（含已付款未入住）、已退房、已完成、已取消一律拒絕（E-5010），不碰資料列")
    void onlyACheckedInBookingCanBeCheckedOut(final Booking.BookingStatus status) {
        givenBooking(booking(status, TODAY));
        actAsStoreOf(store);

        assertRejectedWith(ErrorCode.E_5010, () -> bookingService.checkOut(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("退房的第二段（CHECKED_OUT→COMPLETED）搶不到：拋 E-5010；兩段在同一個 @Transactional 內，由 Spring 整體回滾")
    void completionStepLosingItsRace_isRejected() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        givenTransition(Booking.BookingStatus.CHECKED_IN, Booking.BookingStatus.CHECKED_OUT, 1);
        givenTransition(Booking.BookingStatus.CHECKED_OUT, Booking.BookingStatus.COMPLETED, 0);
        actAsStoreOf(store);

        assertRejectedWith(ErrorCode.E_5010, () -> bookingService.checkOut(bookingId));
    }

    @Test
    @DisplayName("退房的擁有權與入住相同：其他租戶的商家不能退房（E-1007），不動資料列")
    void checkOut_fromAnotherTenant_isRejected() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        actAsStoreOf(otherStore);

        assertRejectedWith(ErrorCode.E_1007, () -> bookingService.checkOut(bookingId));
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    // ── 狀態日誌（Sprint 246，DEF-350） ─────────────────────────────────────

    @Test
    @DisplayName("同租戶商家可查狀態日誌：audit_log 依 createdAt 遞增映射為 StateLogResponse")
    void sameTenantStore_canReadStateLogInOrder() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_OUT, TODAY));
        Instant first = Instant.parse("2026-10-04T04:00:00Z");
        Instant second = Instant.parse("2026-10-06T10:00:00Z");
        AuditLog checkedIn = AuditLog.builder().id(UUID.randomUUID()).entityId(bookingId).action("BOOKING_CHECKED_IN")
                .oldValue("PAID").newValue("CHECKED_IN").userId(store).createdAt(first).build();
        AuditLog checkedOut = AuditLog.builder().id(UUID.randomUUID()).entityId(bookingId)
                .action("BOOKING_CHECKED_OUT").oldValue("CHECKED_IN").newValue("CHECKED_OUT").userId(store)
                .reason(null).createdAt(second).build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("BOOKING", bookingId))
                .thenReturn(List.of(checkedIn, checkedOut));
        actAsStoreOf(store);

        List<BookingDto.StateLogResponse> logs = bookingService.getBookingStateLog(bookingId);

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getAction()).isEqualTo("BOOKING_CHECKED_IN");
        assertThat(logs.get(0).getFromStatus()).isEqualTo("PAID");
        assertThat(logs.get(0).getToStatus()).isEqualTo("CHECKED_IN");
        assertThat(logs.get(0).getCreatedAt()).isEqualTo(first);
        assertThat(logs.get(1).getAction()).isEqualTo("BOOKING_CHECKED_OUT");
        assertThat(logs.get(1).getCreatedAt()).isEqualTo(second);
    }

    @Test
    @DisplayName("管理員可查任何店鋪的狀態日誌")
    void admin_canReadStateLogOfAnyStore() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("BOOKING", bookingId))
                .thenReturn(List.of());
        actAsAdmin();

        assertThat(bookingService.getBookingStateLog(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("買家本人不能查狀態日誌：這是店家層端點，與入住／退房同一套擁有權（E-1007）")
    void buyerWhoOwnsTheBooking_cannotReadStateLog() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        actAsBuyer();

        assertRejectedWith(ErrorCode.E_1007, () -> bookingService.getBookingStateLog(bookingId));
    }

    @Test
    @DisplayName("其他租戶的商家不能查狀態日誌（E-1007）")
    void storeOfAnotherTenant_cannotReadStateLog() {
        givenBooking(booking(Booking.BookingStatus.CHECKED_IN, TODAY));
        actAsStoreOf(otherStore);

        assertRejectedWith(ErrorCode.E_1007, () -> bookingService.getBookingStateLog(bookingId));
    }
}
