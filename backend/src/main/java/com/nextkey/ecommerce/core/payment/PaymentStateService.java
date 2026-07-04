package com.nextkey.ecommerce.core.payment;

import java.math.BigDecimal;
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
     * 退款（Sprint 52 Phase C，AI-2412；Sprint 56 部分退款，AI-2415）：toggle-aware——
     * STRIPE_PAYMENT_ENABLED 開啟且 Payment 為 STRIPE 時經 gateway 真 Stripe Refund.create
     * （可指定金額，未指定則退剩餘全額）+ 存 stripe_refund_id；否則 mock（既有）。
     * 累計 refundedAmount；達 Payment.amount 全額才轉 REFUNDED + Order REFUNDED，
     * 未達全額則轉 PARTIALLY_REFUNDED，Order 狀態不變（訂單持續履約，比照全額退款外的部分退款慣例）。
     * PO 決策（2026-07-04）：運費（Order.shippingFee）不參與部分退款計算，僅退商品金額。
     */
    @Transactional
    public OrderPaymentStateDto refundOrderPayment(UUID orderId, BigDecimal amount, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        // 檢查是否允許退款
        if (!OrderStateMachine.canRefund(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5012, "Order cannot be refunded in current status");
        }

        // 找到已成功或已部分退款的支付記錄（部分退款可重複呼叫，直到全額退完）
        Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)
                .or(() -> paymentRepository.findByOrderIdAndStatus(orderId, Payment.PaymentStatus.PARTIALLY_REFUNDED))
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000, "Payment not found"));

        // 驗證退款金額：未指定 = 退剩餘全額（向後相容既有全額退款呼叫端）；指定時須為正數且不超過剩餘可退額度
        BigDecimal refundAmount = resolveRefundAmount(payment, amount);

        // 真實退款（stripe path）：toggle 開啟 + Payment 為 STRIPE → 呼叫 Stripe Refund（指定金額）
        if (featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)
                && payment.getPaymentMethod() == Payment.PaymentMethod.STRIPE) {
            executeStripeRefund(orderId, payment, refundAmount, reason);
        }

        // 累計已退款金額；達全額才轉 REFUNDED + Order REFUNDED，否則 PARTIALLY_REFUNDED（Order 狀態不變）
        BigDecimal newRefundedAmount = payment.getRefundedAmount().add(refundAmount);
        payment.setRefundedAmount(newRefundedAmount);
        boolean fullyRefunded = newRefundedAmount.compareTo(payment.getAmount()) >= 0;
        payment.setStatus(fullyRefunded ? Payment.PaymentStatus.REFUNDED : Payment.PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);
        if (fullyRefunded) {
            order.setStatus(Order.OrderStatus.REFUNDED);
            orderRepository.save(order);
        }

        log.info("Refund processed: orderId={}, paymentId={}, amount={}, fullyRefunded={}, reason={}",
                orderId, payment.getId(), refundAmount, fullyRefunded, reason);

        return toOrderPaymentStateDto(order, payment);
    }

    /** 解析並驗證退款金額（AI-2415）：null = 剩餘全額；否則須為正數且不超過剩餘可退額度。 */
    private BigDecimal resolveRefundAmount(Payment payment, BigDecimal amount) {
        BigDecimal remaining = payment.getAmount().subtract(payment.getRefundedAmount());
        BigDecimal refundAmount = amount != null ? amount : remaining;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.E_6009,
                    "Refund amount must be positive and not exceed remaining refundable amount: " + remaining);
        }
        return refundAmount;
    }

    /** 呼叫 Stripe Refund（AI-2415 起支援指定金額），成功後存 stripeRefundId（覆蓋最後一次）。 */
    private void executeStripeRefund(UUID orderId, Payment payment, BigDecimal refundAmount, String reason) {
        String paymentIntentId = payment.getStripePaymentIntentId();
        if (paymentIntentId == null) {
            throw new BusinessException(ErrorCode.E_6001, "Missing Stripe payment intent for refund");
        }
        PaymentGatewayRequestResponse.RefundResult result =
                paymentGatewayFactory.processRefund(GATEWAY_STRIPE, paymentIntentId, refundAmount, reason);
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.E_6001, "Stripe refund failed: " + result.getErrorMessage());
        }
        // 誠實揭露：stripeRefundId 僅存最後一次退款 id，多次部分退款的完整歷史需獨立子表，本次不做
        payment.setStripeRefundId(result.getRefundId());
        log.info("Stripe refund created: orderId={}, refundId={}, amount={}", orderId, result.getRefundId(), refundAmount);
    }

    /**
     * 標記 Stripe 退款完成（webhook charge.refunded 權威，AI-2412）：
     * 依 payment_intent id 找 Payment → REFUNDED + Order REFUNDED。冪等：已 REFUNDED → no-op。
     */
    @Transactional
    public boolean markStripeRefunded(String paymentIntentId, String refundId) {
        if (paymentIntentId == null) {
            return false;
        }
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("markStripeRefunded: payment not found for paymentIntent={}", paymentIntentId);
            return false;
        }
        if (payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
            return false; // 冪等
        }
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        if (refundId != null) {
            payment.setStripeRefundId(refundId);
        }
        paymentRepository.save(payment);
        if (payment.getOrderId() != null) {
            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null && OrderStateMachine.canRefund(order.getStatus().name())) {
                order.setStatus(Order.OrderStatus.REFUNDED);
                orderRepository.save(order);
            }
        }
        log.info("Stripe payment marked REFUNDED (webhook): paymentIntent={}, orderId={}",
                paymentIntentId, payment.getOrderId());
        return true;
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

        if ("paid".equalsIgnoreCase(result.getPaymentStatus())) {
            // 與 webhook 路徑共用同一「標記成功 + Order PAID」核心（冪等），確保雙路徑一致
            markStripePaymentSucceeded(sessionId, result.getPaymentIntentId());
            log.info("Stripe checkout confirmed PAID (return): orderId={}, sessionId={}", orderId, sessionId);
        } else {
            log.info("Stripe checkout not yet paid (return): orderId={}, sessionId={}, paymentStatus={}",
                    orderId, sessionId, result.getPaymentStatus());
        }

        Payment payment = paymentRepository.findByTransactionId(sessionId).orElse(null);
        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 標記 Stripe 付款成功（回跳 retrieve 與 webhook 共用核心，AI-2411）：
     * 依 sessionId 找 Payment → SUCCESS + 回填 payment_intent + Order PAID。冪等：已 SUCCESS → no-op 回 false。
     */
    @Transactional
    public boolean markStripePaymentSucceeded(String sessionId, String paymentIntentId) {
        Payment payment = paymentRepository.findByTransactionId(sessionId).orElse(null);
        if (payment == null) {
            log.warn("markStripePaymentSucceeded: payment not found for session={}", sessionId);
            return false;
        }
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            return false; // 冪等：已成功
        }
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        if (paymentIntentId != null) {
            payment.setStripePaymentIntentId(paymentIntentId);
        }
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        if (payment.getOrderId() != null) {
            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null && OrderStateMachine.canPay(order.getStatus().name())) {
                order.setStatus(Order.OrderStatus.PAID);
                orderRepository.save(order);
            }
        }
        log.info("Stripe payment marked SUCCESS: session={}, orderId={}", sessionId, payment.getOrderId());
        return true;
    }

    /**
     * 標記 Stripe 付款失敗（webhook payment_intent.payment_failed，AI-2411）：
     * 依 payment_intent id 找 Payment → FAILED（Order 維持 CREATED 可重試）。已終態則 no-op。
     */
    @Transactional
    public boolean markStripePaymentFailed(String paymentIntentId) {
        if (paymentIntentId == null) {
            return false;
        }
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("markStripePaymentFailed: payment not found for paymentIntent={}", paymentIntentId);
            return false;
        }
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS
                || payment.getStatus() == Payment.PaymentStatus.FAILED) {
            return false; // 已終態
        }
        payment.setStatus(Payment.PaymentStatus.FAILED);
        paymentRepository.save(payment);
        log.info("Stripe payment marked FAILED: paymentIntent={}, orderId={}", paymentIntentId, payment.getOrderId());
        return true;
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
                   .paidAt(payment.getPaidAt())
                   .refundedAmount(payment.getRefundedAmount());
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