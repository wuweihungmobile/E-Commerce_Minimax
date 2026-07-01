package com.nextkey.ecommerce.core.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.order.OrderStateMachine;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;

    /**
     * 取得訂單支付狀態
     */
    @Transactional(readOnly = true)
    public OrderPaymentStateDto getOrderPaymentState(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 取得預訂支付狀態
     */
    @Transactional(readOnly = true)
    public OrderPaymentStateDto getBookingPaymentState(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006, "Booking not found"));

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);

        return toBookingPaymentStateDto(booking, payment);
    }

    /**
     * 模擬支付完成（Mock）
     */
    @Transactional
    public OrderPaymentStateDto mockPaymentSuccess(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        // 檢查訂單狀態是否可以支付
        if (!OrderStateMachine.canPay(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5011, "Order cannot be paid in current status");
        }

        // 檢查是否已有支付記錄
        if (paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }

        // 建立支付記錄 (Mock 直接成功)
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentMethod(Payment.PaymentMethod.MOCK)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId(generateMockTransactionId())
                .build();

        payment = paymentRepository.save(payment);

        // 更新訂單狀態為 PAID
        order.setStatus(Order.OrderStatus.PAID);
        orderRepository.save(order);

        log.info("Mock payment success: orderId={}, paymentId={}", orderId, payment.getId());

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 模擬支付失敗（Mock）
     */
    @Transactional
    public OrderPaymentStateDto mockPaymentFailure(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        // 檢查訂單狀態是否可以支付
        if (!OrderStateMachine.canPay(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5011, "Order cannot be paid in current status");
        }

        // 建立支付記錄 (Mock 失敗)
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentMethod(Payment.PaymentMethod.MOCK)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(Payment.PaymentStatus.FAILED)
                .transactionId(generateMockTransactionId())
                .build();

        payment = paymentRepository.save(payment);

        log.info("Mock payment failure: orderId={}, paymentId={}, reason={}", orderId, payment.getId(), reason);

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 模擬退款（Mock）
     */
    @Transactional
    public OrderPaymentStateDto mockRefund(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        // 檢查是否允許退款
        if (!OrderStateMachine.canRefund(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5012, "Order cannot be refunded in current status");
        }

        // 找到成功的支付記錄
        Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000, "Payment not found"));

        // 標記支付為已退款
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // 更新訂單狀態為 REFUNDED
        order.setStatus(Order.OrderStatus.REFUNDED);
        orderRepository.save(order);

        log.info("Mock refund: orderId={}, paymentId={}, reason={}", orderId, payment.getId(), reason);

        return toOrderPaymentStateDto(order, payment);
    }

    // ========== Helper Methods ==========

    /**
     * 訂單擁有權檢查（DEF-019：付款讀寫租戶/擁有權隔離）。
     * 比照 OrderService.getOrder/cancelOrder：買家限本人訂單、admin（ROLE_ADMIN/SUPER_ADMIN）放行，
     * 越權回 403/E_1007。杜絕任何登入者查詢/付款/退款他人訂單（IDOR）。
     */
    private void checkOrderOwnership(Order order) {
        UUID userId = TenantContext.getCurrentUser();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to access this order");
        }
    }

    private String generateMockTransactionId() {
        return "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderPaymentStateDto toOrderPaymentStateDto(Order order, Payment payment) {
        String orderStatus = order.getStatus().name();
        String nextValidStates = String.join(",", OrderStateMachine.getNextValidStates(orderStatus));

        OrderPaymentStateDto.OrderPaymentStateDtoBuilder builder = OrderPaymentStateDto.builder()
                .orderId(order.getId())
                .orderStatus(orderStatus)
                .nextValidStates(nextValidStates)
                .canPay(OrderStateMachine.canPay(orderStatus))
                .canCancel(OrderStateMachine.canCancel(orderStatus))
                .canRefund(OrderStateMachine.canRefund(orderStatus))
                .updatedAt(order.getUpdatedAt());

        if (payment != null) {
            builder.paymentId(payment.getId())
                   .paymentStatus(payment.getStatus().name())
                   .transactionId(payment.getTransactionId())
                   .paidAt(payment.getPaidAt());
        }

        return builder.build();
    }

    private OrderPaymentStateDto toBookingPaymentStateDto(Booking booking, Payment payment) {
        String bookingStatus = booking.getStatus().name();
        List<String> nextStates = getBookingNextValidStates(bookingStatus);
        String nextValidStates = String.join(",", nextStates);

        OrderPaymentStateDto.OrderPaymentStateDtoBuilder builder = OrderPaymentStateDto.builder()
                .orderId(booking.getId()) // reuse field for booking id
                .orderStatus(bookingStatus)
                .nextValidStates(nextValidStates)
                .canPay(Booking.BookingStatus.CREATED.name().equals(bookingStatus))
                .canCancel(bookingStatus.equals("CREATED") || bookingStatus.equals("PAID"))
                .canRefund(bookingStatus.equals("PAID"))
                .updatedAt(booking.getUpdatedAt());

        if (payment != null) {
            builder.paymentId(payment.getId())
                   .paymentStatus(payment.getStatus().name())
                   .transactionId(payment.getTransactionId())
                   .paidAt(payment.getPaidAt());
        }

        return builder.build();
    }

    private List<String> getBookingNextValidStates(String currentStatus) {
        return switch (currentStatus) {
            case "CREATED" -> List.of("PAID", "CANCELLED");
            case "PAID" -> List.of("CONFIRMED", "CANCELLED");
            case "CONFIRMED" -> List.of("CHECKED_IN", "CANCELLED");
            case "CHECKED_IN" -> List.of("CHECKED_OUT");
            case "CHECKED_OUT" -> List.of("COMPLETED");
            default -> List.of();
        };
    }
}