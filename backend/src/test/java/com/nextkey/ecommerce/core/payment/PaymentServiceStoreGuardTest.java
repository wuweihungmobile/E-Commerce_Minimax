package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.core.audit.AuditService;
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
 * 舊版 {@code POST /v2/payments}（{@link PaymentService}）也是會收錢的入口（Sprint 242）：訂單與訂房各一，
 * 必須和 {@link PaymentStateService} 的付款入口一樣擋掉不營業的店鋪——否則只擋新端點，買家改打舊端點就繞過了。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService（舊版 /v2/payments）發起付款的店鋪營業守門（Sprint 242）")
class PaymentServiceStoreGuardTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STORE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private OrderService orderService;
    @Mock private AuditService auditService;
    @Mock private PaymentStateService paymentStateService;
    @Mock private PaymentStoreGuard paymentStoreGuard;

    @InjectMocks private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentUser(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "buyer", null, List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
        doThrow(new BusinessException(ErrorCode.E_2010)).when(paymentStoreGuard).requireStoreOpen(any());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private PaymentDto.PaymentRequest request(final UUID orderId, final UUID bookingId) {
        PaymentDto.PaymentRequest request = new PaymentDto.PaymentRequest();
        request.setOrderId(orderId);
        request.setBookingId(bookingId);
        request.setPaymentMethod(PaymentDto.PaymentMethod.CREDIT_CARD);
        return request;
    }

    private static ErrorCode codeOf(final Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    @Test
    @DisplayName("訂單：店鋪不營業 → E-2010，沒有寫付款、沒有推進訂單狀態；守門是用訂單自己的租戶諮詢的")
    void order_closedStore_isRejectedWithoutSideEffects() {
        Order order = Order.builder().userId(USER_ID).tenantId(STORE_ID).status(Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(request(ORDER_ID, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

        verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(orderService, auditService);
    }

    @Test
    @DisplayName("訂房：店鋪不營業 → E-2010，沒有寫付款、沒有改訂房狀態；守門是用訂房自己的租戶諮詢的")
    void booking_closedStore_isRejectedWithoutSideEffects() {
        Booking booking = Booking.builder().userId(USER_ID).tenantId(STORE_ID).status(Booking.BookingStatus.CREATED)
                .totalAmount(new BigDecimal("3000.00")).build();
        booking.setId(BOOKING_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.processPayment(request(null, BOOKING_ID)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

        verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(bookingRepository, never()).save(any(Booking.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("錯誤先後不變：不是待付款的訂單回 E-5011，不會先被說成店鋪停業")
    void order_notPayable_isReportedBeforeStoreClosed() {
        Order order = Order.builder().userId(USER_ID).tenantId(STORE_ID).status(Order.OrderStatus.CANCELLED)
                .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(request(ORDER_ID, null)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

        verify(paymentStoreGuard, never()).requireStoreOpen(any());
    }

    @Test
    @DisplayName("錯誤先後不變：不是待付款的訂房回 E-5011，不會先被說成店鋪停業")
    void booking_notPayable_isReportedBeforeStoreClosed() {
        Booking booking = Booking.builder().userId(USER_ID).tenantId(STORE_ID).status(Booking.BookingStatus.CANCELLED)
                .totalAmount(new BigDecimal("3000.00")).build();
        booking.setId(BOOKING_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.processPayment(request(null, BOOKING_ID)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

        verify(paymentStoreGuard, never()).requireStoreOpen(any());
    }
}
