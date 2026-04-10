package com.nextkey.ecommerce.core.payment;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 支付服務 (Mock Implementation)
 * Phase 1 使用 Mock 支付，不需要真實金流整合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final OrderService orderService;

    /**
     * 處理支付（Mock）
     */
    @Transactional
    public PaymentDto.PaymentResponse processPayment(PaymentDto.PaymentRequest request) {
        log.info("Processing payment: orderId={}, bookingId={}, method={}",
                request.getOrderId(), request.getBookingId(), request.getPaymentMethod());

        Payment payment;

        if (request.getOrderId() != null) {
            // 處理訂單支付
            payment = processOrderPayment(request);
        } else if (request.getBookingId() != null) {
            // 處理預訂支付
            payment = processBookingPayment(request);
        } else {
            throw new BusinessException(ErrorCode.E_9005, "Order ID or Booking ID is required");
        }

        return toPaymentResponse(payment);
    }

    /**
     * 處理訂單支付
     */
    private Payment processOrderPayment(PaymentDto.PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));

        // 檢查訂單狀態
        if (order.getStatus() != Order.OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.E_5001, "Order cannot be paid in current status");
        }

        // 檢查是否已有支付記錄
        if (paymentRepository.existsByOrderIdAndStatus(request.getOrderId(), Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }

        // 建立支付記錄
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .paymentMethod(Payment.PaymentMethod.valueOf(request.getPaymentMethod().name()))
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(Payment.PaymentStatus.SUCCESS) // Mock 直接成功
                .transactionId(generateMockTransactionId())
                .idempotencyKey(generateIdempotencyKey(request))
                .build();

        payment = paymentRepository.save(payment);

        // 更新訂單狀態
        orderService.updateOrderStatus(order.getId(), "PAID", "Payment received via " + request.getPaymentMethod());

        log.info("Order payment processed: paymentId={}, transactionId={}",
                payment.getId(), payment.getTransactionId());

        return payment;
    }

    /**
     * 處理預訂支付
     */
    private Payment processBookingPayment(PaymentDto.PaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006, "Booking not found"));

        // 檢查預訂狀態
        if (booking.getStatus() != Booking.BookingStatus.CREATED) {
            throw new BusinessException(ErrorCode.E_5001, "Booking cannot be paid in current status");
        }

        // 檢查是否已有支付記錄
        if (paymentRepository.existsByBookingIdAndStatus(request.getBookingId(), Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }

        // 建立支付記錄
        Payment payment = Payment.builder()
                .bookingId(booking.getId())
                .paymentMethod(Payment.PaymentMethod.valueOf(request.getPaymentMethod().name()))
                .amount(booking.getTotalAmount())
                .currency("TWD")
                .status(Payment.PaymentStatus.SUCCESS) // Mock 直接成功
                .transactionId(generateMockTransactionId())
                .idempotencyKey(generateIdempotencyKey(request))
                .build();

        payment = paymentRepository.save(payment);

        // 更新預訂狀態
        booking.setStatus(Booking.BookingStatus.PAID);
        bookingRepository.save(booking);

        log.info("Booking payment processed: paymentId={}, transactionId={}",
                payment.getId(), payment.getTransactionId());

        return payment;
    }

    /**
     * 處理退款（Mock）
     */
    @Transactional
    public PaymentDto.RefundResponse processRefund(PaymentDto.RefundRequest request) {
        log.info("Processing refund: paymentId={}, amount={}, reason={}",
                request.getPaymentId(), request.getAmount(), request.getReason());

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000, "Payment not found"));

        if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.E_6002, "Payment cannot be refunded");
        }

        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();

        // Mock: 直接標記為已退款
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // 如果是訂單支付，更新訂單狀態
        if (payment.getOrderId() != null) {
            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null && order.getStatus() == Order.OrderStatus.REFUNDING) {
                order.setStatus(Order.OrderStatus.REFUNDED);
                orderRepository.save(order);
            }
        }

        // 如果是預訂支付，更新預訂狀態
        if (payment.getBookingId() != null) {
            Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
            if (booking != null) {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
            }
        }

        log.info("Refund processed: paymentId={}, amount={}", request.getPaymentId(), refundAmount);

        return PaymentDto.RefundResponse.builder()
                .refundId(UUID.randomUUID()) // Mock refund ID
                .paymentId(request.getPaymentId())
                .refundAmount(refundAmount)
                .status("SUCCESS")
                .reason(request.getReason())
                .processedAt(java.time.Instant.now())
                .build();
    }

    /**
     * 取得支付狀態
     */
    @Transactional(readOnly = true)
    public PaymentDto.PaymentStatusResponse getPaymentStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000));

        return PaymentDto.PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .message(payment.getStatus() == Payment.PaymentStatus.SUCCESS ? "Payment successful" : "Payment pending")
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    // ========== Helper Methods ==========

    private String generateMockTransactionId() {
        return "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateIdempotencyKey(PaymentDto.PaymentRequest request) {
        if (request.getOrderId() != null) {
            return "ORDER-" + request.getOrderId().toString();
        } else if (request.getBookingId() != null) {
            return "BOOKING-" + request.getBookingId().toString();
        }
        return UUID.randomUUID().toString();
    }

    private PaymentDto.PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentDto.PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .bookingId(payment.getBookingId())
                .paymentMethod(payment.getPaymentMethod().name())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}