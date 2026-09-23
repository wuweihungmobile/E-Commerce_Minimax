package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
 * PaymentService.processOrderPayment/processBookingPayment 擁有權隔離
 * （DEF-019：訂單付款入口；DEF-023：訂房付款入口，皆為 /v2/payments IDOR 修補）。
 *
 * <p>驗證：他人不可為買家訂單/預訂付款（E_1007，映射 HTTP 403）；本人通過擁有權檢查（續走狀態檢查）；
 * admin 放行。以「狀態檢查」證明擁有權**先於**狀態觸發，免除付款 happy-path 深度 mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService.processOrderPayment/processBookingPayment 擁有權隔離（DEF-019/DEF-023）")
class PaymentServiceOwnershipTest {

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
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOfUser(final UUID userId, final Order.OrderStatus status) {
        return Order.builder().id(orderId).userId(userId).status(status).build();
    }

    private Booking bookingOfUser(final UUID userId, final Booking.BookingStatus status) {
        Booking booking = Booking.builder().userId(userId).status(status).build();
        booking.setId(bookingId);
        return booking;
    }

    private PaymentDto.PaymentRequest req() {
        return PaymentDto.PaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentDto.PaymentMethod.MOCK)
                .build();
    }

    private PaymentDto.PaymentRequest bookingReq() {
        return PaymentDto.PaymentRequest.builder()
                .bookingId(bookingId)
                .paymentMethod(PaymentDto.PaymentMethod.MOCK)
                .build();
    }

    @Test
    @DisplayName("他人為買家訂單付款 → E_1007（擁有權先於狀態檢查）")
    void payOrder_otherUser_throwsE1007() {
        // order 屬 buyerA、狀態故意設 PAID（非 CREATED）；當前使用者為 buyerB。
        // 若得 E_1007（而非狀態錯誤 E_5011），證明擁有權檢查「先於」狀態檢查觸發。
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> paymentService.processPayment(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人通過擁有權檢查（續走狀態檢查 → E_5011，非 E_1007）")
    void payOrder_sameUser_passesOwnership() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerA);

        // 擁有權通過 → 撞上「須 CREATED」狀態檢查 → E_5011，證明未被 E_1007 擋下。
        assertThatThrownBy(() -> paymentService.processPayment(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5011);
    }

    @Test
    @DisplayName("admin 為他人訂單付款放行（擁有權通過，續走狀態檢查 → E_5011）")
    void payOrder_admin_bypassesOwnership() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> paymentService.processPayment(req()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5011);
    }

    // ========== processBookingPayment（DEF-023） ==========

    @Test
    @DisplayName("他人為買家預訂付款 → E_1007（擁有權先於狀態檢查）")
    void payBooking_otherUser_throwsE1007() {
        // booking 屬 buyerA、狀態故意設 PAID（非 CREATED）；當前使用者為 buyerB。
        // 若得 E_1007（而非狀態錯誤 E_5011），證明擁有權檢查「先於」狀態檢查觸發。
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.PAID)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> paymentService.processPayment(bookingReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人通過擁有權檢查（續走狀態檢查 → E_5011，非 E_1007）")
    void payBooking_sameUser_passesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.PAID)));
        TenantContext.setCurrentUser(buyerA);

        // 擁有權通過 → 撞上「須 CREATED」狀態檢查 → E_5011，證明未被 E_1007 擋下。
        assertThatThrownBy(() -> paymentService.processPayment(bookingReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5011);
    }

    @Test
    @DisplayName("admin 為他人預訂付款放行（擁有權通過，續走狀態檢查 → E_5011）")
    void payBooking_admin_bypassesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.PAID)));
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> paymentService.processPayment(bookingReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5011);
    }

    // ========== getPaymentStatus（DEF-260，Sprint 186） ==========

    private final UUID paymentId = UUID.randomUUID();

    private Payment paymentForOrder() {
        return Payment.builder().id(paymentId).orderId(orderId)
                .status(Payment.PaymentStatus.SUCCESS).transactionId("MOCK-TX").build();
    }

    @Test
    @DisplayName("🔴 DEF-260：他人查詢買家訂單的付款狀態 → E_1007（先前完全無擁有權檢查）")
    void getPaymentStatus_otherUser_throwsE1007() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(paymentForOrder()));
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> paymentService.getPaymentStatus(paymentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人查詢自己訂單的付款狀態 → 放行，回傳正確狀態")
    void getPaymentStatus_sameUser_returnsStatus() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(paymentForOrder()));
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerA);

        PaymentDto.PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("admin 查詢他人訂單的付款狀態 → 放行")
    void getPaymentStatus_admin_bypassesOwnership() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(paymentForOrder()));
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderOfUser(buyerA, Order.OrderStatus.PAID)));
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        PaymentDto.PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }
}
