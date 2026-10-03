package com.nextkey.ecommerce.core.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderStateMachine;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
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
    /** 訂房沒有幣別欄位（{@code BookingService} 的回應同樣寫死 TWD）。 */
    private static final String BOOKING_CURRENCY = "TWD";
    /** 退款金額允許的最大小數位數（幣別最小單位：分），見 {@link #resolveRefundAmount}。 */
    private static final int REFUND_AMOUNT_MAX_SCALE = 2;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final FeatureToggleService featureToggleService;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final SettlementAdjustmentService settlementAdjustmentService;
    private final OrderStateLogRepository orderStateLogRepository;
    private final AuditService auditService;
    private final PaymentStoreGuard paymentStoreGuard;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /** 只用於重新整理被條件式 UPDATE 改過的付款實體，見 {@link #confirmStripeBookingCheckout}。 */
    @PersistenceContext
    private EntityManager entityManager;

    public PaymentStateService(PaymentRepository paymentRepository, OrderRepository orderRepository,
            BookingRepository bookingRepository, FeatureToggleService featureToggleService,
            PaymentGatewayFactory paymentGatewayFactory, SettlementAdjustmentService settlementAdjustmentService,
            OrderStateLogRepository orderStateLogRepository, AuditService auditService,
            PaymentStoreGuard paymentStoreGuard) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.featureToggleService = featureToggleService;
        this.paymentGatewayFactory = paymentGatewayFactory;
        this.settlementAdjustmentService = settlementAdjustmentService;
        this.orderStateLogRepository = orderStateLogRepository;
        this.auditService = auditService;
        this.paymentStoreGuard = paymentStoreGuard;
    }

    /**
     * 記錄付款流程觸發的 Order 狀態轉換到既有的 order_state_log（Sprint 135，DEF-111）。
     * 比照 {@code OrderService.recordStateLog} 的序號規則；付款/退款狀態轉換原先只寫入
     * 暫時性的 log.info，未落地到這張與其他 Order 狀態變更共用的稽核表。
     */
    private void recordOrderStateLog(Order order, String fromStatus, String toStatus, UUID changedBy, String reason) {
        Integer maxSequence = orderStateLogRepository.findMaxSequenceByOrderId(order.getId());
        int nextSequence = maxSequence != null ? maxSequence + 1 : 1;

        OrderStateLog log = OrderStateLog.builder()
                .order(order)
                .sequence(nextSequence)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .reason(reason)
                .build();

        orderStateLogRepository.save(log);
    }

    /**
     * 取得訂單支付狀態
     */
    @Transactional(readOnly = true)
    public OrderPaymentStateDto getOrderPaymentState(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderOwnership(order);

        Payment payment = paymentRepository.findEffectiveByOrderId(orderId).orElse(null);

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 取得預訂支付狀態
     */
    @Transactional(readOnly = true)
    public OrderPaymentStateDto getBookingPaymentState(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006, "Booking not found"));
        checkBookingOwnership(booking);

        Payment payment = paymentRepository.findEffectiveByBookingId(bookingId).orElse(null);

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
        requireMockPaymentAllowed();

        // 檢查訂單狀態是否可以支付
        if (!OrderStateMachine.canPay(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5011, "Order cannot be paid in current status");
        }

        // 檢查是否已有支付記錄
        if (paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }
        // Sprint 242（使用者拍板）：店鋪已停權／終止就不能再付款給它；放在既有檢查之後（不改變錯誤先後）、任何寫入之前
        paymentStoreGuard.requireStoreOpen(order.getTenantId());

        // 併發防護（DEF-125，claim-before-side-effects）：先原子搶占「目前狀態→PAID」這個轉換，
        // 只有搶到的一方才繼續建立 Payment 記錄。上面兩個檢查都是 check-then-act，
        // 兩個併發的 mockPaymentSuccess 呼叫都可能通過同一份舊快照，各自建立一筆 SUCCESS
        // Payment（當年還會各自扣一次庫存；Sprint 218 起付款不再扣庫存），比照 markStripePaymentSucceeded 既有修法。
        String previousStatus = order.getStatus().name();
        if (orderRepository.updateStatusIfCurrent(order.getId(), order.getStatus(), Order.OrderStatus.PAID) == 0) {
            throw new BusinessException(ErrorCode.E_5011, "Order cannot be paid in current status");
        }
        order.setStatus(Order.OrderStatus.PAID);

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

        // Sprint 218（DEF-303 (6)）：付款不再扣庫存，預留保留到出貨才扣（PRD §6.7.3，見 ProductInventoryService）
        recordOrderStateLog(order, previousStatus, Order.OrderStatus.PAID.name(),
                TenantContext.getCurrentUser(), "Mock payment success");

        log.info("Mock payment success: orderId={}, paymentId={}", orderId, payment.getId());
        auditService.record("ORDER_PAYMENT_MOCK_SUCCESS", "PAYMENT", payment.getId(), order.getTenantId(),
                previousStatus, Order.OrderStatus.PAID.name(), null, TenantContext.getCurrentUser());

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
        requireMockPaymentAllowed();

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
        auditService.record("ORDER_PAYMENT_MOCK_FAILURE", "PAYMENT", payment.getId(), order.getTenantId(),
                null, "FAILED", reason, TenantContext.getCurrentUser());

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 模擬訂房付款完成（Mock，Sprint 221，DEF-303 (1)）：訂房 CREATED → PAID，並留下一筆成功的 Mock 付款。
     *
     * <p>與 {@link #mockPaymentSuccess} 同一模式：先以條件式 UPDATE 搶占「CREATED → PAID」這個轉換，只有搶到的一方才建立
     * 付款紀錄。前面的狀態與已付款檢查都是 check-then-act，兩個併發請求會通過同一份舊快照，若各自建立一筆 SUCCESS 付款，
     * 同一筆訂房的營收就被重複計入。
     */
    @Transactional
    public OrderPaymentStateDto mockBookingPaymentSuccess(UUID bookingId) {
        Booking booking = loadOwnedBooking(bookingId);
        requireMockPaymentAllowed();
        requireBookingPayable(booking);
        if (paymentRepository.existsByBookingIdAndStatus(bookingId, Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }
        paymentStoreGuard.requireStoreOpen(booking.getTenantId());
        if (bookingRepository.updateStatusIfCurrent(bookingId, Booking.BookingStatus.CREATED,
                Booking.BookingStatus.PAID) == 0) {
            throw new BusinessException(ErrorCode.E_5011, "Booking cannot be paid in current status");
        }
        booking.setStatus(Booking.BookingStatus.PAID);

        Payment payment = paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .paymentMethod(Payment.PaymentMethod.MOCK)
                .amount(booking.getTotalAmount())
                .currency(BOOKING_CURRENCY)
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId(generateMockTransactionId())
                .build());

        log.info("Mock booking payment success: bookingId={}, paymentId={}", bookingId, payment.getId());
        auditService.record("BOOKING_PAYMENT_MOCK_SUCCESS", "PAYMENT", payment.getId(), booking.getTenantId(),
                Booking.BookingStatus.CREATED.name(), Booking.BookingStatus.PAID.name(), null,
                TenantContext.getCurrentUser());

        return toBookingPaymentStateDto(booking, payment);
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
        return refundOrderPaymentCore(order, amount, reason);
    }

    /**
     * 自動退款（Sprint 226，DEF-303 (5)／DEF-308）：系統（{@code RefundProcessingService} 排程）對「等待退款」
     * （{@code REFUNDING}）的訂單退還剩餘全額。PRD §15.2.5「若已支付，觸發 M04 退款流程」。
     *
     * <p>與 {@link #refundOrderPayment} 共用同一個退款核心（額度 CAS、Stripe 呼叫與冪等鍵、訂單轉 REFUNDED、
     * 狀態紀錄、結算調整），差別只有兩點：呼叫者是系統而不是登入使用者，所以不做擁有權檢查，狀態紀錄與稽核的操作者為
     * null；以及只處理 {@code REFUNDING}——系統不主動退款給其他狀態的訂單（退款金額一律是剩餘全額，不接受部分退款）。
     *
     * <p>失敗（Stripe 拒絕、找不到可退款的付款…）一律拋出並回滾整個交易，訂單維持 {@code REFUNDING}，由呼叫端決定
     * 何時重試；不會出現「本地標成已退款、Stripe 沒退」的半套狀態。
     */
    @Transactional
    public OrderPaymentStateDto refundOrderPaymentAsSystem(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        if (order.getStatus() != Order.OrderStatus.REFUNDING) {
            throw new BusinessException(ErrorCode.E_5012, "Order is not waiting for a refund");
        }
        return refundOrderPaymentCore(order, null, reason);
    }

    /**
     * 自動退款（Sprint 227，DEF-312／DEF-308 訂房側）：系統（{@code RefundProcessingService} 排程）退還「已取消、等待退款」
     * （{@code refund_status = PENDING}）的訂房款項，金額是取消時依 PRD Q14 決定的 {@code refund_amount}。
     *
     * <p>與訂單版（{@link #refundOrderPaymentAsSystem}）同一個做法：先以 compare-and-swap 佔用付款的退款額度，再呼叫 Stripe
     * （付款方式為 STRIPE 一律經 Stripe；冪等鍵同為付款意圖＋退款前累計額＋本次金額），最後把訂房 {@code PENDING → COMPLETED}。
     * 失敗（Stripe 拒絕、找不到可退款的付款…）一律拋出並回滾整個交易，訂房維持 {@code PENDING}，由呼叫端決定何時重試。
     * 訂房不參與結算（結算只算訂單），所以沒有結算調整。
     */
    @Transactional
    public void refundBookingPaymentAsSystem(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006, "Booking not found"));
        if (booking.getStatus() != Booking.BookingStatus.CANCELLED
                || booking.getRefundStatus() != Booking.RefundStatus.PENDING || booking.getRefundAmount() == null) {
            throw new BusinessException(ErrorCode.E_5012, "Booking is not waiting for a refund");
        }
        Payment payment = paymentRepository.findEffectiveByBookingId(bookingId)
                .filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS
                        || p.getStatus() == Payment.PaymentStatus.PARTIALLY_REFUNDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000, "Payment not found"));

        BigDecimal previousRefundedAmount = payment.getRefundedAmount();
        BigDecimal remaining = payment.getAmount().subtract(previousRefundedAmount);
        BigDecimal refundAmount = booking.getRefundAmount().min(remaining);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.E_6009, "Refund amount must be positive: " + refundAmount);
        }
        BigDecimal newRefundedAmount = previousRefundedAmount.add(refundAmount);
        Payment.PaymentStatus newPaymentStatus = newRefundedAmount.compareTo(payment.getAmount()) >= 0
                ? Payment.PaymentStatus.REFUNDED : Payment.PaymentStatus.PARTIALLY_REFUNDED;

        // 🔴 併發防護：同 refundOrderPaymentCore——先 CAS 佔用額度，才呼叫 Stripe
        if (paymentRepository.applyRefundIfUnchanged(payment.getId(), previousRefundedAmount, newRefundedAmount,
                newPaymentStatus) == 0) {
            throw new BusinessException(ErrorCode.E_6009,
                    "Refund amount conflicts with a concurrent refund on the same payment, please retry");
        }
        if (payment.getPaymentMethod() == Payment.PaymentMethod.STRIPE) {
            executeStripeRefund(bookingId, payment, previousRefundedAmount, refundAmount, reason);
        }
        payment.setRefundedAmount(newRefundedAmount);
        payment.setStatus(newPaymentStatus);

        // 條件式 UPDATE：0 代表別的處理者已先完成，冪等
        bookingRepository.completeRefundIfPending(bookingId, Booking.RefundStatus.PENDING,
                Booking.RefundStatus.COMPLETED);

        log.info("Booking refund processed: bookingId={}, paymentId={}, amount={}, reason={}", bookingId,
                payment.getId(), refundAmount, reason);
        auditService.record("BOOKING_PAYMENT_REFUNDED", "PAYMENT", payment.getId(), booking.getTenantId(),
                "refunded=" + previousRefundedAmount, "refunded=" + newRefundedAmount + ",status=" + newPaymentStatus,
                reason, TenantContext.getCurrentUser());
    }

    /** 退款核心：呼叫端已載入訂單並完成授權（使用者）或狀態確認（系統）。 */
    private OrderPaymentStateDto refundOrderPaymentCore(Order order, BigDecimal amount, String reason) {
        UUID orderId = order.getId();

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

        // 累計已退款金額；達全額才轉 REFUNDED + Order REFUNDED，否則 PARTIALLY_REFUNDED（Order 狀態不變）
        BigDecimal previousRefundedAmount = payment.getRefundedAmount();
        BigDecimal newRefundedAmount = previousRefundedAmount.add(refundAmount);
        boolean fullyRefunded = newRefundedAmount.compareTo(payment.getAmount()) >= 0;
        Payment.PaymentStatus newPaymentStatus =
                fullyRefunded ? Payment.PaymentStatus.REFUNDED : Payment.PaymentStatus.PARTIALLY_REFUNDED;

        // 🔴 併發防護：先以 compare-and-swap 原子性佔用本次退款額度，才呼叫 Stripe——若同一筆付款被
        // 併發送出第二次退款請求，這裡會因為 refundedAmount 已被搶先改變而影響 0 列，直接拒絕、
        // 不呼叫 Stripe、不重複做下游結算調整，避免短付賣家或事後可退超過原始付款金額。
        int claimed = paymentRepository.applyRefundIfUnchanged(payment.getId(), previousRefundedAmount,
                newRefundedAmount, newPaymentStatus);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_6009,
                    "Refund amount conflicts with a concurrent refund on the same payment, please retry");
        }

        // 真實退款（stripe path）：Payment 為 STRIPE → 一律呼叫 Stripe Refund（指定金額）。
        // Sprint 226：原本還要求 STRIPE_PAYMENT_ENABLED 開啟。那個開關只決定「新的付款」走哪條路；已經在 Stripe 收下的錢，
        // 開關之後被關掉，也不能只在本地標成已退款——錢沒退回去而系統說退了（自動退款上線後這會是靜默的錯帳）。
        // 排在額度佔用「之後」：若佔用失敗直接拒絕於上方，絕不會走到這裡才呼叫外部金流。
        // 🔴 in-memory 的 setRefundedAmount/setStatus 特意延後到 Stripe 呼叫「之後」才做：若 Stripe
        // 失敗於此拋出，交易整體回滾（DB 的 compare-and-swap 結果也一併復原），payment 物件不應該在
        // 記憶體裡已經呈現「已退款」——否則呼叫端若誤用這個已拋例外方法留下的物件會看到不一致的假象。
        if (payment.getPaymentMethod() == Payment.PaymentMethod.STRIPE) {
            executeStripeRefund(orderId, payment, previousRefundedAmount, refundAmount, reason);
        }
        payment.setRefundedAmount(newRefundedAmount);
        payment.setStatus(newPaymentStatus);

        if (fullyRefunded) {
            String previousStatus = order.getStatus().name();
            order.setStatus(Order.OrderStatus.REFUNDED);
            orderRepository.save(order);
            recordOrderStateLog(order, previousStatus, Order.OrderStatus.REFUNDED.name(),
                    TenantContext.getCurrentUser(), reason);
        }

        log.info("Refund processed: orderId={}, paymentId={}, amount={}, fullyRefunded={}, reason={}",
                orderId, payment.getId(), refundAmount, fullyRefunded, reason);
        auditService.record("ORDER_PAYMENT_REFUNDED", "PAYMENT", payment.getId(), order.getTenantId(),
                "refunded=" + previousRefundedAmount, "refunded=" + newRefundedAmount + ",status=" + newPaymentStatus,
                reason, TenantContext.getCurrentUser());

        // Sprint 86（PRD §6.2.1）：跨結算週期退款處理，失敗不應影響已完成的退款主流程
        try {
            settlementAdjustmentService.handleOrderRefund(
                    order.getTenantId(), order.getId(), refundAmount);
        } catch (RuntimeException e) {
            log.error("Settlement adjustment failed after refund: orderId={}, error={}", orderId, e.getMessage(), e);
        }

        return toOrderPaymentStateDto(order, payment);
    }

    /**
     * 解析並驗證退款金額（AI-2415）：null = 剩餘全額；否則須為正數且不超過剩餘可退額度。
     *
     * <p>DEF-267：呼叫端指定的金額另須落在幣別最小單位（至多 2 位小數）。Stripe 換算
     * {@code amount × 100} 後以 {@code longValue()} 截斷，而 {@code payments.refunded_amount} 是
     * NUMERIC(12,2) 四捨五入，對 {@code 500.005} 兩邊會分別得到 500.00 與 500.01。以數值判斷而非
     * 字面 scale（{@code 500.500} 只是尾端補零，仍合法）。
     */
    private BigDecimal resolveRefundAmount(Payment payment, BigDecimal amount) {
        if (amount != null && amount.stripTrailingZeros().scale() > REFUND_AMOUNT_MAX_SCALE) {
            throw new BusinessException(ErrorCode.E_6009,
                    "Refund amount must not have more than " + REFUND_AMOUNT_MAX_SCALE + " decimal places");
        }
        BigDecimal remaining = payment.getAmount().subtract(payment.getRefundedAmount());
        BigDecimal refundAmount = amount != null ? amount : remaining;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.E_6009,
                    "Refund amount must be positive and not exceed remaining refundable amount: " + remaining);
        }
        return refundAmount;
    }

    /** 呼叫 Stripe Refund（AI-2415 起支援指定金額），成功後存 stripeRefundId（覆蓋最後一次）。 */
    private void executeStripeRefund(UUID orderId, Payment payment, BigDecimal refundedBefore,
            BigDecimal refundAmount, String reason) {
        String paymentIntentId = payment.getStripePaymentIntentId();
        if (paymentIntentId == null) {
            throw new BusinessException(ErrorCode.E_6001, "Missing Stripe payment intent for refund");
        }
        // DEF-288：冪等鍵綁定「這一次邏輯退款」＝付款意圖＋退款前累計已退額＋本次金額。原本恆為
        // refund-<pi>，同一筆付款的每次部分退款都送同一把鍵：依 Stripe 文件，相同鍵＋不同金額會被拒、
        // 相同鍵＋相同金額會回傳第一次的結果而不建立新退款，本地卻已累計兩次。累計額來自呼叫端 CAS
        // 前讀到的值，故「Stripe 失敗、本地回滾後重試同一筆退款」會得到相同的鍵，仍由 Stripe 去重。
        String idempotencyKey = "refund-" + paymentIntentId + "-" + refundedBefore.setScale(2, RoundingMode.HALF_UP)
                .toPlainString() + "-" + refundAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        PaymentGatewayRequestResponse.RefundResult result = paymentGatewayFactory.processRefund(
                GATEWAY_STRIPE, paymentIntentId, refundAmount, reason, idempotencyKey);
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
        // 併發防護（DEF-162）：CAS 取代「讀 status==REFUNDED 冪等檢查→setStatus→save」，
        // 避免 Stripe webhook 對同一筆退款事件重複送達時，兩邊都通過舊快照的冪等檢查，
        // 各自對訂單寫入重複的 order_state_log。
        int updated = paymentRepository.markRefundedIfNotAlready(payment.getId(), Payment.PaymentStatus.REFUNDED, refundId);
        if (updated == 0) {
            return false; // 已是 REFUNDED 或已被另一併發 webhook 搶先處理（冪等）
        }
        UUID refundTenantId = null;
        if (payment.getOrderId() != null) {
            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                refundTenantId = order.getTenantId();
            }
            if (order != null && OrderStateMachine.canRefund(order.getStatus().name())) {
                String previousStatus = order.getStatus().name();
                order.setStatus(Order.OrderStatus.REFUNDED);
                orderRepository.save(order);
                recordOrderStateLog(order, previousStatus, Order.OrderStatus.REFUNDED.name(),
                        null, "Stripe refund webhook (charge.refunded), refundId=" + refundId);
            }
        }
        log.info("Stripe payment marked REFUNDED (webhook): paymentIntent={}, orderId={}",
                paymentIntentId, payment.getOrderId());
        auditService.record("STRIPE_PAYMENT_REFUNDED_WEBHOOK", "PAYMENT", payment.getId(), refundTenantId,
                null, "REFUNDED", "refundId=" + refundId);
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
        // Sprint 242：必須在呼叫 Stripe 建 session 之前——停權店鋪不該有新的 Checkout Session
        paymentStoreGuard.requireStoreOpen(order.getTenantId());

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
        // 🔴 併發防護：Stripe 端已用相同 idempotencyKey 保證重複的請求拿回同一個 session（result 對每次呼叫相同），
        // 本地 payments 表的 idempotency_key 有唯一索引（V79），所以已經有這一列就不再寫。
        // Sprint 221（DEF-310）：原本是「寫入、捕捉 DataIntegrityViolationException、記 log 後照常回傳」，但違反約束的例外
        // 發生在 repository 的交易代理內，會把外層交易標成 rollback-only——捕捉沒有用，買家在 Stripe 頁按返回再按一次
        // 「前往付款」（正常操作，不是罕見競態）時，請求在提交時以 UnexpectedRollbackException（500）收場。
        // 改為先查再寫；查與寫之間的極小競態由唯一索引兜底，輸家收到可重試的 E-6005。
        if (paymentRepository.findByIdempotencyKey(idempotencyKey).isEmpty()) {
            try {
                paymentRepository.saveAndFlush(payment);
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.E_6005, "Checkout for this order is already being created");
            }
        }

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
     * 發起訂房的 Stripe Checkout（Sprint 221，DEF-303 (1)）：與 {@link #initiateStripeCheckout} 同一模式——建 PROCESSING
     * 付款紀錄＋建 Checkout Session，回傳前端重導 URL。需 STRIPE_PAYMENT_ENABLED 開啟。
     *
     * <p>同一筆訂房再次發起（買家在 Stripe 頁按了返回、重新付款）會以相同的冪等鍵拿回 Stripe 端同一個 session，這時不能
     * 再寫一列：{@code payments.idempotency_key} 有唯一索引，重複寫入的例外發生在 repository 的交易代理內，會把外層交易
     * 標成 rollback-only，即使在這裡捕捉，整個請求仍在提交時以 500 收場。所以先查再寫；查與寫之間的極小競態由唯一索引
     * 兜底，輸家收到「處理中」的可重試錯誤（{@code E-6005}），資料不會壞。
     */
    @Transactional
    public CheckoutSessionResponse initiateStripeBookingCheckout(UUID bookingId) {
        Booking booking = loadOwnedBooking(bookingId);
        if (!featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)) {
            throw new BusinessException(ErrorCode.E_6002, "Stripe payment not enabled");
        }
        requireBookingPayable(booking);
        if (paymentRepository.existsByBookingIdAndStatus(bookingId, Payment.PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.E_6003, "Payment already processed");
        }
        paymentStoreGuard.requireStoreOpen(booking.getTenantId());

        String idempotencyKey = "BOOKING-CHECKOUT-" + bookingId;
        PaymentGatewayRequestResponse.CheckoutSessionResult result = paymentGatewayFactory.createCheckoutSession(
                GATEWAY_STRIPE, PaymentGatewayRequestResponse.CheckoutSessionRequest.builder()
                        .bookingId(bookingId)
                        .amount(booking.getTotalAmount())
                        .currency(BOOKING_CURRENCY)
                        .productName("Booking " + bookingId)
                        .successUrl(frontendBaseUrl + "/bookings/" + bookingId
                                + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                        .cancelUrl(frontendBaseUrl + "/bookings/" + bookingId + "/payment/cancel")
                        .idempotencyKey(idempotencyKey)
                        .build());

        if (paymentRepository.findByIdempotencyKey(idempotencyKey).isEmpty()) {
            try {
                paymentRepository.saveAndFlush(Payment.builder()
                        .bookingId(bookingId)
                        .paymentMethod(Payment.PaymentMethod.STRIPE)
                        .amount(booking.getTotalAmount())
                        .currency(BOOKING_CURRENCY)
                        .status(Payment.PaymentStatus.PROCESSING)
                        .transactionId(result.getSessionId())
                        .stripeSessionId(result.getSessionId())
                        .stripePaymentIntentId(result.getPaymentIntentId())
                        .idempotencyKey(idempotencyKey)
                        .build());
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.E_6005, "Checkout for this booking is already being created");
            }
        }

        log.info("Stripe booking checkout initiated: bookingId={}, sessionId={}", bookingId, result.getSessionId());
        return CheckoutSessionResponse.builder()
                .bookingId(bookingId)
                .sessionId(result.getSessionId())
                .sessionUrl(result.getSessionUrl())
                .build();
    }

    /**
     * 回跳後確認訂房的 Stripe Checkout（Sprint 221，DEF-303 (1)）：以 sessionId retrieve 狀態，已付款則走與 webhook
     * 相同的 {@link #markStripePaymentSucceeded}（付款 SUCCESS＋訂房 PAID，冪等）。
     *
     * <p>付款與訂房是用條件式 UPDATE 改的（不經過 persistence context），而這個方法在更新前已載入付款實體；open-in-view
     * 又讓同一個 request 的多個交易共用同一個 persistence context，所以之後不論在交易內或提交後再讀，讀到的都是更新前的舊實體
     * （狀態仍是 PROCESSING，訂房卻已是 PAID——同一份回應自相矛盾）。因此更新後重新整理付款實體，再組回應。
     */
    @Transactional
    public OrderPaymentStateDto confirmStripeBookingCheckout(UUID bookingId, String sessionId) {
        Booking booking = loadOwnedBooking(bookingId);
        // 只認屬於這筆訂房的 session：否則可以拿別筆訂房（甚至別人）的 session 來「確認」這一筆
        Payment payment = paymentRepository.findByTransactionId(sessionId)
                .filter(p -> bookingId.equals(p.getBookingId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.E_6000, "Payment not found"));
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            return toBookingPaymentStateDto(booking, payment); // 冪等：已成功（webhook 先到），不必再問 Stripe
        }

        PaymentGatewayRequestResponse.CheckoutSessionResult result =
                paymentGatewayFactory.retrieveCheckoutSession(GATEWAY_STRIPE, sessionId);
        if ("paid".equalsIgnoreCase(result.getPaymentStatus())) {
            markStripePaymentSucceeded(sessionId, result.getPaymentIntentId());
            entityManager.refresh(payment);
            log.info("Stripe booking checkout confirmed PAID (return): bookingId={}, sessionId={}", booking.getId(),
                    sessionId);
        } else {
            log.info("Stripe booking checkout not yet paid (return): bookingId={}, sessionId={}, paymentStatus={}",
                    booking.getId(), sessionId, result.getPaymentStatus());
        }
        return toBookingPaymentStateDto(booking, payment);
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
        UUID orderId = payment.getOrderId();

        // 🔴 併發防護：原子條件式 UPDATE 取代「讀狀態→判斷→setStatus→save」，避免兩個併發呼叫
        // （例如回跳確認流程與 webhook 幾乎同時處理同一筆付款）都通過舊有的 in-memory 檢查，
        // 都真的執行一次下游副作用（當年是庫存扣減，Sprint 218 起付款不再扣庫存；重複的仍會是狀態紀錄與稽核）。
        int updated = paymentRepository.markSuccessIfNotAlready(payment.getId(), Payment.PaymentStatus.SUCCESS,
                paymentIntentId, Instant.now());
        if (updated == 0) {
            return false; // 冪等：已成功，或已被併發的另一次呼叫搶先標記
        }

        UUID successTenantId = null;
        if (orderId != null) {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                successTenantId = order.getTenantId();
                markOrderPaidByStripe(order, payment, sessionId);
            }
        } else if (payment.getBookingId() != null) {
            successTenantId = markBookingPaidByStripe(payment, sessionId);
        }
        log.info("Stripe payment marked SUCCESS: session={}, orderId={}", sessionId, orderId);
        auditService.record("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", "PAYMENT", payment.getId(), successTenantId,
                null, "SUCCESS", "session=" + sessionId);
        return true;
    }

    /**
     * Stripe 付款成功後把訂單推進到 PAID（與訂房的 {@link #markBookingPaidByStripe} 對稱）。
     *
     * <p>Sprint 226（DEF-308）：原本是「讀狀態→{@code canPay}→setStatus→save」。管理員、賣家、買家自己取消，或逾時取消，
     * 都可能發生在買家還停在 Stripe 付款頁的時候：付款成功、錢已收，訂單卻已是 {@code CANCELLED}，而這裡什麼都不做——
     * 沒有退款、沒有告警。併發時更糟：save 會用舊快照把剛取消的訂單寫回 {@code PAID}，已釋放的預留與優惠券額度沒有人收回。
     * 改用條件式 UPDATE 搶占 {@code CREATED → PAID}（取消也是同一種條件式 UPDATE），恰好一邊成功；
     * 搶不到就走 {@link #queueRefundForUnpayableOrder}。
     */
    private void markOrderPaidByStripe(Order order, Payment payment, String sessionId) {
        if (orderRepository.updateStatusIfCurrent(order.getId(), Order.OrderStatus.CREATED,
                Order.OrderStatus.PAID) == 1) {
            order.setStatus(Order.OrderStatus.PAID);
            recordOrderStateLog(order, Order.OrderStatus.CREATED.name(), Order.OrderStatus.PAID.name(),
                    null, "Stripe payment webhook success, session=" + sessionId);
            return;
        }
        queueRefundForUnpayableOrder(order, payment, sessionId);
    }

    /**
     * 付款成功但訂單已不可付款（DEF-308）：錢收了、訂單沒成立。訂單是 {@code CANCELLED}（逾時、被管理員／賣家／買家取消）時，
     * 把它轉 {@code REFUNDING}（條件式 UPDATE，狀態紀錄註明原因）交給自動退款；這筆付款是該訂單唯一的付款，
     * 退款金額就是付款金額。其他狀態（例如已經 {@code PAID} 又收到一筆）不自動處理——那是重複付款，該退哪一筆沒有唯一答案，
     * 留稽核紀錄與錯誤日誌讓人處理，不再靜默。兩種情況都寫一筆 {@code STRIPE_PAYMENT_ORDER_NOT_PAYABLE}。
     */
    private void queueRefundForUnpayableOrder(Order order, Payment payment, String sessionId) {
        boolean queued = orderRepository.updateStatusIfCurrent(order.getId(), Order.OrderStatus.CANCELLED,
                Order.OrderStatus.REFUNDING) == 1;
        Order.OrderStatus current = queued ? Order.OrderStatus.CANCELLED
                : orderRepository.findStatusById(order.getId()).orElse(order.getStatus());
        if (queued) {
            order.setStatus(Order.OrderStatus.REFUNDING);
            recordOrderStateLog(order, Order.OrderStatus.CANCELLED.name(), Order.OrderStatus.REFUNDING.name(), null,
                    "Payment received after cancellation: refund pending, session=" + sessionId);
        }
        log.error("Stripe payment succeeded but the order is not payable (money collected, order not paid): "
                + "orderId={}, orderStatus={}, refundQueued={}, session={}", order.getId(), current, queued, sessionId);
        auditService.record("STRIPE_PAYMENT_ORDER_NOT_PAYABLE", "PAYMENT", payment.getId(), order.getTenantId(),
                current.name(), "SUCCESS", "session=" + sessionId + ",orderId=" + order.getId()
                        + ",refundQueued=" + queued);
    }

    /**
     * Stripe 付款成功後把訂房推進到 PAID（Sprint 221）。用條件式 UPDATE 搶占 CREATED → PAID；搶不到代表訂房已不是待付款
     * （例如買家在 Stripe 頁面停留時先把它取消了，日曆已釋放）——付款已經成功、錢已收，但訂房不成立。這裡不把它拉回已付款
     * （日曆可能已被別人訂走），也不自動退款（要不要退、怎麼退是產品決定，見 DEF-308），只留下可查的稽核紀錄，
     * 不再靜默。回傳訂房所屬租戶供呼叫端寫稽核。
     */
    private UUID markBookingPaidByStripe(Payment payment, String sessionId) {
        Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            log.warn("markStripePaymentSucceeded: booking not found: bookingId={}, session={}",
                    payment.getBookingId(), sessionId);
            return null;
        }
        if (bookingRepository.updateStatusIfCurrent(booking.getId(), Booking.BookingStatus.CREATED,
                Booking.BookingStatus.PAID) == 0) {
            // Sprint 227（DEF-308 訂房側）：訂房已被取消（逾時、或買家還在 Stripe 頁時先取消）→ 付款金額全額退回：
            // 訂房沒有成立、買家什麼都沒拿到，不適用 Q14 的 24 小時門檻。條件式 UPDATE 同時確認訂房仍是 CANCELLED
            // 且尚未有退款安排；其他狀態（例如已 PAID 又收到一筆＝重複付款）不自動處理，只留稽核與錯誤日誌。
            boolean queued = bookingRepository.requestRefundIfCancelled(booking.getId(),
                    Booking.BookingStatus.CANCELLED, Booking.RefundStatus.NONE, Booking.RefundStatus.PENDING,
                    payment.getAmount()) == 1;
            Booking.BookingStatus current = queued ? Booking.BookingStatus.CANCELLED
                    : bookingRepository.findStatusById(booking.getId()).orElse(booking.getStatus());
            log.error("Stripe payment succeeded but the booking is not payable (money collected, booking not paid): "
                    + "bookingId={}, bookingStatus={}, refundQueued={}, session={}", booking.getId(), current, queued,
                    sessionId);
            auditService.record("STRIPE_PAYMENT_BOOKING_NOT_PAYABLE", "PAYMENT", payment.getId(), booking.getTenantId(),
                    current.name(), "SUCCESS", "session=" + sessionId + ",bookingId=" + booking.getId()
                            + ",refundQueued=" + queued);
        } else {
            booking.setStatus(Booking.BookingStatus.PAID);
        }
        return booking.getTenantId();
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
        auditService.record("STRIPE_PAYMENT_FAILED_WEBHOOK", "PAYMENT", payment.getId(), tenantOfPayment(payment),
                null, "FAILED", "paymentIntent=" + paymentIntentId);
        return true;
    }

    /** 付款所屬訂單或訂房的租戶（供稽核紀錄使用）；兩者都沒有（或已不存在）時為 null。 */
    private UUID tenantOfPayment(Payment payment) {
        if (payment.getOrderId() != null) {
            return orderRepository.findById(payment.getOrderId()).map(Order::getTenantId).orElse(null);
        }
        if (payment.getBookingId() != null) {
            return bookingRepository.findById(payment.getBookingId()).map(Booking::getTenantId).orElse(null);
        }
        return null;
    }

    /**
     * Mock 付款只在未啟用真實金流時可用（DEF-299）。
     *
     * <p>Mock 付款不收錢就把訂單／預訂標成已付款。啟用 Stripe 後若仍可呼叫，買家不必付款就能拿到商品。
     * 原本擋住這件事的只是「買家剛好沒有 {@code order:update}」（DEF-298 修正付款端點權限後就不再成立），
     * 而舊版 {@code POST /v2/payments} 買家本來就能呼叫。所有 Mock 付款入口一律呼叫本方法；判斷與
     * {@link #getOrderPaymentState} 回給前端的 {@code paymentProvider} 相同，前端在 Stripe 模式本就不顯示
     * Mock 付款按鈕。
     */
    public void requireMockPaymentAllowed() {
        if (featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED)) {
            throw new BusinessException(ErrorCode.E_6004,
                    "Mock payment is not available while Stripe payment is enabled");
        }
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

    /**
     * 訂房擁有權檢查（DEF-023：booking 付款讀取擁有權隔離）。
     * 比照 checkOrderOwnership：買家限本人預訂、admin（ROLE_ADMIN/SUPER_ADMIN）放行，
     * 越權回 403/E_1007。杜絕任何登入者查詢他人預訂付款狀態（IDOR）。
     */
    private void checkBookingOwnership(Booking booking) {
        UUID userId = TenantContext.getCurrentUser();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        if (!isAdmin && !userId.equals(booking.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to access this booking");
        }
    }

    /** 載入訂房並確認呼叫者是本人（或 admin）——比 {@link #getBookingPaymentState} 多一個共用入口，供付款動作使用。 */
    private Booking loadOwnedBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006, "Booking not found"));
        checkBookingOwnership(booking);
        return booking;
    }

    /** 只有待付款（CREATED）的訂房可以付款——已付款、已取消（日曆已釋放）的都不行。 */
    private void requireBookingPayable(Booking booking) {
        if (booking.getStatus() != Booking.BookingStatus.CREATED) {
            throw new BusinessException(ErrorCode.E_5011, "Booking cannot be paid in current status");
        }
    }

    /** 目前的付款提供者（mock / stripe）；前端據此決定顯示模擬付款按鈕或重導 Stripe。 */
    private String paymentProvider() {
        return featureToggleService.isFeatureEnabled(STRIPE_PAYMENT_ENABLED) ? "stripe" : "mock";
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
                .paymentProvider(paymentProvider())
                .storeOpen(paymentStoreGuard.isStoreOpen(order.getTenantId()))
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
                .paymentProvider(paymentProvider())
                .storeOpen(paymentStoreGuard.isStoreOpen(booking.getTenantId()))
                .updatedAt(booking.getUpdatedAt());

        if (payment != null) {
            builder.paymentId(payment.getId())
                   .paymentStatus(payment.getStatus().name())
                   .transactionId(payment.getTransactionId())
                   .paidAt(payment.getPaidAt())
                   .refundedAmount(payment.getRefundedAmount());
        }

        return builder.build();
    }

    /** 預訂的下一步。Sprint 245（DEF-345）：付款即等同確認（PRD Phase 1），PAID 直接入住，不經 CONFIRMED。 */
    private List<String> getBookingNextValidStates(String currentStatus) {
        return switch (currentStatus) {
            case "CREATED" -> List.of("PAID", "CANCELLED");
            case "PAID" -> List.of("CHECKED_IN", "CANCELLED");
            case "CHECKED_IN" -> List.of("CHECKED_OUT");
            case "CHECKED_OUT" -> List.of("COMPLETED");
            default -> List.of();
        };
    }
}