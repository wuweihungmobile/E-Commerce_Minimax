package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
 * 🔴 DEF-136：processOrderPayment/processBookingPayment 併發防護（V79 payments.idempotency_key
 * 唯一索引 + saveAndFlush）。兩個併發請求都可能通過「是否已有 SUCCESS 記錄」的讀取檢查（讀到同一份
 * 舊快照），此測試驗證資料庫層級擋下第二筆重複寫入時，服務層正確轉譯為 E_6003 業務例外，而非讓
 * DataIntegrityViolationException 原樣外洩或（更糟）被吞掉當作成功。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 併發防護（DEF-136）")
class PaymentServiceConcurrencyTest {

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

    private final UUID buyer = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("processOrderPayment: 併發搶佔（idempotency_key 唯一索引衝突）-> E_6003，不視為成功")
    void processOrderPayment_concurrentClaim_throwsE6003() {
        Order order = Order.builder().id(orderId).userId(buyer).status(Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(1000)).currency("TWD").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        TenantContext.setCurrentUser(buyer);

        PaymentDto.PaymentRequest request = PaymentDto.PaymentRequest.builder()
                .orderId(orderId).paymentMethod(PaymentDto.PaymentMethod.MOCK).build();

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6003);
    }

    @Test
    @DisplayName("processBookingPayment: 併發搶佔（idempotency_key 唯一索引衝突）-> E_6003，不視為成功")
    void processBookingPayment_concurrentClaim_throwsE6003() {
        Booking booking = Booking.builder().userId(buyer).status(Booking.BookingStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(1000)).build();
        booking.setId(bookingId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.existsByBookingIdAndStatus(bookingId, Payment.PaymentStatus.SUCCESS))
                .thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        TenantContext.setCurrentUser(buyer);

        PaymentDto.PaymentRequest request = PaymentDto.PaymentRequest.builder()
                .bookingId(bookingId).paymentMethod(PaymentDto.PaymentMethod.MOCK).build();

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6003);
    }
}
