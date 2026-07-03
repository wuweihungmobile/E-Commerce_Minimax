package com.nextkey.ecommerce.core.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderStateMachine;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentStateService {

    /** 真實金流 toggle（Sprint 50 AI-2410）：開啟→stripe 路徑，關閉（預設）→mock 路徑。 */
    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final String GATEWAY_STRIPE = "STRIPE";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final FeatureToggleService featureToggleService;
    private final PaymentGatewayFactory paymentGatewayFactory;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public PaymentStateService(PaymentRepository paymentRepository, OrderRepository orderRepository,
            BookingRepository bookingRepository, FeatureToggleService featureToggleService,
            PaymentGatewayFactory paymentGatewayFactory) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.featureToggleService = featureToggleService;
        this.paymentGatewayFactory = paymentGatewayFactory;
    }

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

    /**
     * 發起 Stripe Checkout（Sprint 50 AI-2410，Phase A，平台代收）：
     * 建 PROCESSING 付款記錄 + 建 Checkout Session，回傳前端重導 URL。需 STRIPE_PAYMENT_ENABLED 開啟。
     */
    @Transactional
    public CheckoutSessionResponse initiateStripeCheckout(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        if (!featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)) {
            throw new BusinessException(ErrorCode.E_6002, "Stripe payment not enabled");
        }
        if (!OrderStateMachine.canPay(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5011, "Order cannot be paid in current status");
        }
        if (paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }

        String idempotencyKey = "ORDER-CHECKOUT-" + orderId;
        PaymentGatewayRequestResponse.CheckoutSessionRequest req =
                PaymentGatewayRequestResponse.CheckoutSessionRequest.builder()
                        .orderId(orderId)
                        .amount(order.getTotalAmount())
                        .currency(order.getCurrency())
                        .productName("Order " + orderId)
                        .successUrl(frontendBaseUrl + "/orders/" + orderId
                                + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                        .cancelUrl(frontendBaseUrl + "/orders/" + orderId + "/payment/cancel")
                        .idempotencyKey(idempotencyKey)
                        .build();

        PaymentGatewayRequestResponse.CheckoutSessionResult result =
                paymentGatewayFactory.createCheckoutSession(GATEWAY_STRIPE, req);

        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(Payment.PaymentStatus.PROCESSING)
                .transactionId(result.getSessionId())
                .stripeSessionId(result.getSessionId())
                .stripePaymentIntentId(result.getPaymentIntentId())
                .idempotencyKey(idempotencyKey)
                .build();
        paymentRepository.save(payment);

        log.info("Stripe checkout initiated: orderId={}, sessionId={}", orderId, result.getSessionId());

        return CheckoutSessionResponse.builder()
                .orderId(orderId)
                .sessionId(result.getSessionId())
                .sessionUrl(result.getSessionUrl())
                .build();
    }

    /**
     * 回跳後確認 Stripe Checkout（Sprint 50 AI-2410，Phase A）：以 sessionId retrieve 狀態，
     * 已付款則更新 Payment=SUCCESS + Order=PAID（冪等，重入不重複）。robust webhook 事件驅動留 Phase B。
     */
    @Transactional
    public OrderPaymentStateDto confirmStripeCheckout(UUID orderId, String sessionId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        // 冪等：已成功則直接回狀態，不重複更新
        Payment existingSuccess = paymentRepository
                .findByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS).orElse(null);
        if (existingSuccess != null) {
            return toOrderPaymentStateDto(order, existingSuccess);
        }

        PaymentGatewayRequestResponse.CheckoutSessionResult result =
                paymentGatewayFactory.retrieveCheckoutSession(GATEWAY_STRIPE, sessionId);
        Payment payment = paymentRepository.findByTransactionId(sessionId).orElse(null);

        if ("paid".equalsIgnoreCase(result.getPaymentStatus())) {
            if (payment != null) {
                payment.setStatus(Payment.PaymentStatus.SUCCESS);
                payment.setStripePaymentIntentId(result.getPaymentIntentId());
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
            }
            if (OrderStateMachine.canPay(order.getStatus().name())) {
                order.setStatus(Order.OrderStatus.PAID);
                orderRepository.save(order);
            }
            log.info("Stripe checkout confirmed PAID: orderId={}, sessionId={}", orderId, sessionId);
        } else {
            log.info("Stripe checkout not yet paid: orderId={}, sessionId={}, paymentStatus={}",
                    orderId, sessionId, result.getPaymentStatus());
        }

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
                .paymentProvider(featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED) ? "stripe" : "mock")
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