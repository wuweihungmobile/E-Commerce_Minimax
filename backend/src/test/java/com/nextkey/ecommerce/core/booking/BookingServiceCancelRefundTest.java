package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * {@link BookingService#cancelBooking} 的退款決定（Sprint 227，DEF-312；PRD §15.2.5／Q14）。
 *
 * <p>24 小時邊界本身由 {@link BookingRefundPolicyTest} 以固定時刻驗證；這裡用「離入住還有好幾天／入住日已過」這種不靠
 * 邊界的日期，驗證取消流程把規則接對了：誰取消、付款狀況、記錄什麼、回應什麼，以及併發搶占。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService.cancelBooking 退款決定（Sprint 227，PRD Q14）")
class BookingServiceCancelRefundTest {

    private static final BigDecimal PAID = new BigDecimal("3000.00");

    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private PromoService promoService;
    @Mock private AuditService auditService;
    @Mock private BuyerNotificationService buyerNotificationService;

    @InjectMocks
    private BookingService bookingService;

    private final UUID buyer = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Booking booking(final Booking.BookingStatus status, final LocalDate checkIn) {
        return Booking.builder().id(bookingId).userId(buyer).roomListingId(UUID.randomUUID())
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2)).guestCount(2).status(status)
                .totalAmount(PAID).build();
    }

    private void givenBooking(final Booking booking) {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.updateStatusIfCurrent(bookingId, booking.getStatus(), Booking.BookingStatus.CANCELLED))
                .thenReturn(1);
        when(roomRepository.findByListingId(any())).thenReturn(Optional.of(Room.builder().build()));
    }

    private void givenPayment(final Payment.PaymentStatus status, final BigDecimal refunded) {
        when(paymentRepository.findEffectiveByBookingId(bookingId)).thenReturn(Optional.of(Payment.builder()
                .bookingId(bookingId).status(status).amount(PAID).refundedAmount(refunded).build()));
    }

    private void actAsBuyer() {
        TenantContext.setCurrentUser(buyer);
    }

    private void actAsAdmin() {
        TenantContext.setCurrentUser(admin);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Booking savedBooking() {
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("已付款、買家本人、離入住好幾天 → 全額退款排入 PENDING，記下取消方／時間／金額，回應帶出退款資訊，稽核記下決定")
    void paidCustomerCancelsWellInAdvance_isFullRefundPending() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsBuyer();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "changed my mind");

        Booking saved = savedBooking();
        assertThat(saved.getStatus()).isEqualTo(Booking.BookingStatus.CANCELLED);
        assertThat(saved.getCancelledBy()).isEqualTo(Booking.CancelledBy.CUSTOMER);
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(saved.getRefundStatus()).isEqualTo(Booking.RefundStatus.PENDING);
        assertThat(saved.getRefundAmount()).isEqualByComparingTo(PAID);
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getCanceledBy()).isEqualTo("CUSTOMER");
        assertThat(response.getRefundStatus()).isEqualTo("PENDING");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(PAID);
        verify(auditService).record(eq("BOOKING_REFUND_DECIDED"), eq("BOOKING"), eq(bookingId), any(), any(),
                eq("PENDING amount=3000.00"), org.mockito.ArgumentMatchers.contains("canceledBy=CUSTOMER"));
        verify(roomCalendarService).releaseDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("已付款、買家本人、入住日已過（< 24 小時）→ 不退款：NONE、金額 null，付款不動，稽核仍記下「不退」的決定")
    void paidCustomerCancelsTooLate_isNoRefund() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().minusDays(1)));
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsBuyer();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "too late");

        Booking saved = savedBooking();
        assertThat(saved.getRefundStatus()).isEqualTo(Booking.RefundStatus.NONE);
        assertThat(saved.getRefundAmount()).isNull();
        assertThat(response.getRefundStatus()).isEqualTo("NONE");
        assertThat(response.getRefundAmount()).isNull();
        verify(auditService).record(eq("BOOKING_REFUND_DECIDED"), eq("BOOKING"), eq(bookingId), any(), any(),
                eq("NONE"), org.mockito.ArgumentMatchers.contains("canceledBy=CUSTOMER"));
        // 不退款也要釋放日曆（PRD：取消時段立即回歸可用池）
        verify(roomCalendarService).releaseDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("已付款、管理員代為取消（商家／平台取消）、入住日已過 → 一律全額退款（取消方 MERCHANT）")
    void paidCancelledByAdminAfterCheckIn_isFullRefundAsMerchant() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().minusDays(1)));
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsAdmin();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "host cancelled");

        Booking saved = savedBooking();
        assertThat(saved.getCancelledBy()).isEqualTo(Booking.CancelledBy.MERCHANT);
        assertThat(saved.getRefundStatus()).isEqualTo(Booking.RefundStatus.PENDING);
        assertThat(saved.getRefundAmount()).isEqualByComparingTo(PAID);
        assertThat(response.getCanceledBy()).isEqualTo("MERCHANT");
    }

    @Test
    @DisplayName("未付款（CREATED）取消 → 沒有款項可退：不查付款、NONE，也不寫退款決定稽核")
    void unpaidCancel_hasNothingToRefund() {
        givenBooking(booking(Booking.BookingStatus.CREATED, BusinessTime.today().plusDays(5)));
        actAsBuyer();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "changed my mind");

        assertThat(response.getRefundStatus()).isEqualTo("NONE");
        assertThat(response.getRefundAmount()).isNull();
        verify(paymentRepository, never()).findEffectiveByBookingId(any());
        verify(auditService, never()).record(eq("BOOKING_REFUND_DECIDED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("訂房是 PAID 但找不到可退款的付款（異常資料）→ 不退款、不拋例外（取消本身仍成立）")
    void paidButNoRefundablePayment_isNoRefund() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
        when(paymentRepository.findEffectiveByBookingId(bookingId)).thenReturn(Optional.empty());
        actAsBuyer();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "x");

        assertThat(response.getRefundStatus()).isEqualTo("NONE");
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("付款已部分退款（PARTIALLY_REFUNDED）→ 應退金額是「還可退的金額」，不是訂房總額")
    void partiallyRefundedPayment_refundsOnlyWhatRemains() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
        givenPayment(Payment.PaymentStatus.PARTIALLY_REFUNDED, new BigDecimal("1000.00"));
        actAsBuyer();

        BookingDto.CancelResponse response = bookingService.cancelBooking(bookingId, "x");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("🔴 併發搶占：條件式 UPDATE 影響 0 列（狀態已被另一個取消／付款改掉）→ E_4007，不釋放日曆、不決定退款、不寫任何東西")
    void lostTheRace_isRejectedBeforeAnySideEffect() {
        Booking paid = booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(paid));
        when(bookingRepository.updateStatusIfCurrent(bookingId, Booking.BookingStatus.PAID,
                Booking.BookingStatus.CANCELLED)).thenReturn(0);
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsBuyer();

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4007);

        verify(roomCalendarService, never()).releaseDateRange(any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verify(auditService, never()).record(eq("BOOKING_REFUND_DECIDED"), any(), any(), any(), any(), any(), any());
    }

    // ========== 取消後通知買家（Sprint 229，PRD US-005「取消後即時收到退款狀態通知」）==========

    @Test
    @DisplayName("通知：已付款的訂房取消 → 通知買家，並標明「取消前已付款」（文案才分得出沒付過款與依政策不退）")
    void paidCancel_notifiesTheBuyerAsPaid() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsBuyer();

        bookingService.cancelBooking(bookingId, "changed my mind");

        verify(buyerNotificationService).notifyBookingCancelled(bookingId, true);
    }

    @Test
    @DisplayName("通知：未付款的訂房取消 → 也通知買家，但標明「沒付過款」")
    void unpaidCancel_notifiesTheBuyerAsUnpaid() {
        givenBooking(booking(Booking.BookingStatus.CREATED, BusinessTime.today().plusDays(5)));
        actAsBuyer();

        bookingService.cancelBooking(bookingId, "changed my mind");

        verify(buyerNotificationService).notifyBookingCancelled(bookingId, false);
    }

    @Test
    @DisplayName("通知：管理員（商家／平台）代為取消 → 買家同樣收到通知（被取消的人最需要知道）")
    void adminCancel_stillNotifiesTheBooker() {
        givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().minusDays(1)));
        givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
        actAsAdmin();

        bookingService.cancelBooking(bookingId, "host cancelled");

        verify(buyerNotificationService).notifyBookingCancelled(bookingId, true);
    }

    @Test
    @DisplayName("通知：取消被拒絕（併發搶輸、狀態不可取消）→ 沒有取消就不通知")
    void rejectedCancel_sendsNoNotice() {
        Booking paid = booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(paid));
        when(bookingRepository.updateStatusIfCurrent(bookingId, Booking.BookingStatus.PAID,
                Booking.BookingStatus.CANCELLED)).thenReturn(0);
        actAsBuyer();

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "x")).isInstanceOf(BusinessException.class);

        verify(buyerNotificationService, never()).notifyBookingCancelled(any(), anyBoolean());
    }

    @Test
    @DisplayName("🔴 在交易裡：通知等到提交之後才送；提交前不送，交易回滾則永遠不送（不能通知一個其實沒取消成功的訂房）")
    void insideATransaction_theNoticeWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
            givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
            actAsBuyer();

            bookingService.cancelBooking(bookingId, "changed my mind");

            verify(buyerNotificationService, never()).notifyBookingCancelled(any(), anyBoolean());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(buyerNotificationService).notifyBookingCancelled(bookingId, true);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("🔴 交易回滾 → 通知永遠不送")
    void rolledBackTransaction_neverSendsTheNotice() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            givenBooking(booking(Booking.BookingStatus.PAID, BusinessTime.today().plusDays(5)));
            givenPayment(Payment.PaymentStatus.SUCCESS, BigDecimal.ZERO);
            actAsBuyer();

            bookingService.cancelBooking(bookingId, "changed my mind");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(buyerNotificationService, never()).notifyBookingCancelled(any(), anyBoolean());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
