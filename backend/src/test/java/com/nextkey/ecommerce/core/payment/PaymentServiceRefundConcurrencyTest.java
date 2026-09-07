package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 🔴 DEF-136: PaymentService.processRefund 併發防護（條件式 UPDATE 取代
 * 「讀 status==SUCCESS→setStatus→save」）。兩個併發退款請求都可能通過同一份舊快照的狀態檢查，
 * 此測試驗證資料庫層級擋下第二筆重複轉換時，服務層正確拒絕，而非讓兩邊都回應「退款成功」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService.processRefund 併發防護（DEF-136）")
class PaymentServiceRefundConcurrencyTest {

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

    private final UUID paymentId = UUID.randomUUID();

    private Payment successPayment() {
        return Payment.builder().id(paymentId).status(Payment.PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(1000)).build();
    }

    @Test
    @DisplayName("併發搶佔（updateStatusIfCurrent 影響 0 列）-> E_6002，不視為成功")
    void processRefund_concurrentClaim_throwsE6002() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(successPayment()));
        when(paymentRepository.updateStatusIfCurrent(paymentId, Payment.PaymentStatus.SUCCESS,
                Payment.PaymentStatus.REFUNDED)).thenReturn(0);

        PaymentDto.RefundRequest request = PaymentDto.RefundRequest.builder()
                .paymentId(paymentId).amount(BigDecimal.valueOf(1000)).reason("test").build();

        assertThatThrownBy(() -> paymentService.processRefund(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6002);
    }

    @Test
    @DisplayName("成功佔用（updateStatusIfCurrent 影響 1 列）-> 正常回傳退款結果")
    void processRefund_claimSucceeds_returnsRefundResponse() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(successPayment()));
        when(paymentRepository.updateStatusIfCurrent(paymentId, Payment.PaymentStatus.SUCCESS,
                Payment.PaymentStatus.REFUNDED)).thenReturn(1);

        PaymentDto.RefundRequest request = PaymentDto.RefundRequest.builder()
                .paymentId(paymentId).amount(BigDecimal.valueOf(1000)).reason("test").build();

        PaymentDto.RefundResponse response = paymentService.processRefund(request);

        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }
}
