package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * PaymentService.processRefund 擁有權檢查 + 退款金額上限（DEF-243）。
 *
 * <p>背景：舊版 `/v2/payments/refund` 端點先前完全沒有擁有權檢查（`processOrderPayment`/
 * `processBookingPayment` 皆有，唯獨 `processRefund` 遺漏），且 `request.getAmount()` 沒有任何
 * 上限驗證——任何具備 `order:update`/`booking:update` 權限者（一般賣家帳號皆有）可對任意租戶的
 * `paymentId` 觸發退款，且金額可任意灌水。前端已改走有完整防護的 `/v2/orders/{id}/refund`
 * （{@code PaymentStateService.refundOrderPayment}），此端點屬「新路徑修好、舊路徑被遺忘」的
 * 雙路徑遺留，但仍是真實可達的 REST 端點，故直接修復而非僅登記。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService.processRefund 擁有權隔離與金額上限（DEF-243）")
class PaymentServiceRefundOwnershipTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private final UUID buyerA = UUID.randomUUID();
    private final UUID buyerB = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Payment orderPayment(final BigDecimal amount) {
        return Payment.builder().id(paymentId).orderId(orderId)
                .status(Payment.PaymentStatus.SUCCESS).amount(amount).build();
    }

    private Payment bookingPayment(final BigDecimal amount) {
        return Payment.builder().id(paymentId).bookingId(bookingId)
                .status(Payment.PaymentStatus.SUCCESS).amount(amount).build();
    }

    private Order orderOfUser(final UUID userId) {
        return Order.builder().id(orderId).userId(userId).status(Order.OrderStatus.REFUNDING).build();
    }

    private Booking bookingOfUser(final UUID userId) {
        Booking booking = Booking.builder().userId(userId).status(Booking.BookingStatus.PAID).build();
        booking.setId(bookingId);
        return booking;
    }

    private PaymentDto.RefundRequest refundRequest(final BigDecimal amount) {
        return PaymentDto.RefundRequest.builder().paymentId(paymentId).amount(amount).reason("test").build();
    }

    // ========== 訂單付款退款的擁有權隔離 ==========

    @Test
    @DisplayName("他人對買家訂單的付款觸發退款 → E_1007（修復前：完全不檢查，直接退款成功）")
    void refund_orderPayment_otherUser_throwsE1007() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(orderPayment(BigDecimal.valueOf(1000))));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfUser(buyerA)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> paymentService.processRefund(refundRequest(BigDecimal.valueOf(1000))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人觸發自己訂單付款的退款 → 通過擁有權檢查，正常退款")
    void refund_orderPayment_sameUser_succeeds() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(orderPayment(BigDecimal.valueOf(1000))));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfUser(buyerA)));
        when(paymentRepository.updateStatusIfCurrent(paymentId, Payment.PaymentStatus.SUCCESS,
                Payment.PaymentStatus.REFUNDED)).thenReturn(1);
        TenantContext.setCurrentUser(buyerA);

        PaymentDto.RefundResponse response = paymentService.processRefund(refundRequest(BigDecimal.valueOf(1000)));

        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    @DisplayName("admin 對他人訂單付款觸發退款放行（擁有權通過）")
    void refund_orderPayment_admin_bypassesOwnership() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(orderPayment(BigDecimal.valueOf(1000))));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfUser(buyerA)));
        when(paymentRepository.updateStatusIfCurrent(paymentId, Payment.PaymentStatus.SUCCESS,
                Payment.PaymentStatus.REFUNDED)).thenReturn(1);
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        PaymentDto.RefundResponse response = paymentService.processRefund(refundRequest(BigDecimal.valueOf(1000)));

        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    // ========== 訂房付款退款的擁有權隔離 ==========

    @Test
    @DisplayName("他人對買家訂房的付款觸發退款 → E_1007")
    void refund_bookingPayment_otherUser_throwsE1007() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(bookingPayment(BigDecimal.valueOf(500))));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(bookingOfUser(buyerA)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> paymentService.processRefund(refundRequest(BigDecimal.valueOf(500))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    // ========== 退款金額上限 ==========

    @Test
    @DisplayName("退款金額超過原始付款金額 → E_6009（修復前：無上限，任意灌水金額直接回顯成功）")
    void refund_amountExceedsPaymentAmount_throwsE6009() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(orderPayment(BigDecimal.valueOf(1000))));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfUser(buyerA)));
        TenantContext.setCurrentUser(buyerA);

        assertThatThrownBy(() -> paymentService.processRefund(refundRequest(BigDecimal.valueOf(999999))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_6009);
    }

    @Test
    @DisplayName("未帶金額時預設為付款全額，仍受擁有權檢查保護")
    void refund_noAmountSpecified_defaultsToFullPaymentAmount() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(orderPayment(BigDecimal.valueOf(1000))));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfUser(buyerA)));
        when(paymentRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(1);
        TenantContext.setCurrentUser(buyerA);

        PaymentDto.RefundResponse response = paymentService.processRefund(refundRequest(null));

        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }
}
