package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 「發起付款」的店鋪營業狀態守門（Sprint 242，使用者拍板：停權前已成立、尚未付款的單，停權後也不能付款）。
 *
 * <p>守門本身（{@link PaymentStoreGuard}）是 mock，這裡驗證的是 {@link PaymentStateService} 的<b>接線</b>：
 * ① 四個會收錢的入口都諮詢守門、用的是訂單／訂房自己的租戶；② 守門拒絕時<b>沒有任何副作用</b>（沒有搶占狀態、沒有寫付款、
 * 沒有呼叫 Stripe、沒有稽核）；③ 既有錯誤的先後順序不變（不可付款、已付款的單不會先被說成「店鋪停業」）；
 * ④ 刻意不擋的入口（Stripe 入帳、Mock 付款失敗）即使守門會拒絕也照常運作——錢已收進來就必須入帳。
 *
 * <p>守門的判斷邏輯與真實資料庫的端對端行為，見 {@code PaymentStoreGuardTest}、{@code StoreSuspendedPaymentIntegrationTest}。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentStateService 發起付款的店鋪營業守門（Sprint 242）")
class PaymentStateServiceStoreGuardTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STORE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;
    @Mock private SettlementAdjustmentService settlementAdjustmentService;
    @Mock private OrderStateLogRepository orderStateLogRepository;
    @Mock private AuditService auditService;
    @Mock private PaymentStoreGuard paymentStoreGuard;
    @Mock private EntityManager entityManager;
    @Mock private BuyerNotificationService buyerNotificationService;

    private PaymentStateService service;

    @BeforeEach
    void setUp() {
        service = new PaymentStateService(paymentRepository, orderRepository, bookingRepository,
                featureToggleService, paymentGatewayFactory, settlementAdjustmentService,
                orderStateLogRepository, auditService, paymentStoreGuard, buyerNotificationService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(1);
        when(bookingRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(1);
        when(paymentRepository.markSuccessIfNotAlready(any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 店鋪不營業：守門只要被諮詢就丟 E-2010。 */
    private void storeIsClosed() {
        doThrow(new BusinessException(ErrorCode.E_2010)).when(paymentStoreGuard).requireStoreOpen(any());
        when(paymentStoreGuard.isStoreOpen(any())).thenReturn(false);
    }

    private void order(final Order.OrderStatus status) {
        Order order = Order.builder().userId(USER_ID).tenantId(STORE_ID).status(status)
                .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private void booking(final Booking.BookingStatus status) {
        Booking booking = Booking.builder().userId(USER_ID).tenantId(STORE_ID).status(status)
                .totalAmount(new BigDecimal("3000.00")).build();
        booking.setId(BOOKING_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    private static ErrorCode codeOf(final Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    private void stripeEnabled() {
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
    }

    /** 守門拒絕後必須完全沒有副作用：沒有搶占狀態、沒有寫付款、沒有呼叫金流、沒有稽核、沒有狀態紀錄。 */
    private void verifyNothingHappened() {
        verify(orderRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(paymentGatewayFactory, auditService, orderStateLogRepository);
    }

    @Nested
    @DisplayName("訂單付款")
    class OrderPayment {

        @Test
        @DisplayName("Mock 付款：店鋪不營業 → E-2010，沒有任何副作用；守門是用訂單自己的租戶諮詢的")
        void mockPay_closedStore_isRejectedWithoutSideEffects() {
            order(Order.OrderStatus.CREATED);
            storeIsClosed();

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .isInstanceOf(BusinessException.class).satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
            verifyNothingHappened();
        }

        @Test
        @DisplayName("Stripe Checkout：店鋪不營業 → E-2010，而且根本沒有呼叫 Stripe（不替停權店鋪建 session）")
        void stripeCheckout_closedStore_neverCallsStripe() {
            order(Order.OrderStatus.CREATED);
            stripeEnabled();
            storeIsClosed();

            assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                    .isInstanceOf(BusinessException.class).satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
            verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
            verifyNothingHappened();
        }

        @Test
        @DisplayName("同一個持久化脈絡剛建立的訂單（只有 tenant 關聯、tenantId 影子欄位是 null）：守門用的是關聯的店鋪，不是 null——否則營業中的店鋪也會被當成不存在而擋下付款")
        void mockPay_orderWithOnlyTheTenantAssociation_consultsTheGuardWithThatStore() {
            Tenant store = Tenant.builder().name("店").slug("s").build();
            store.setId(STORE_ID);
            Order order = Order.builder().userId(USER_ID).tenant(store).status(Order.OrderStatus.CREATED)
                    .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
            order.setId(ORDER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            service.mockPaymentSuccess(ORDER_ID);

            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
        }

        @Test
        @DisplayName("店鋪營業：Mock 付款照常成功（守門沒有擋掉正常付款）")
        void mockPay_openStore_stillSucceeds() {
            order(Order.OrderStatus.CREATED);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            OrderPaymentStateDto state = service.mockPaymentSuccess(ORDER_ID);

            assertThat(state.getOrderStatus()).isEqualTo("PAID");
            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
        }

        @Test
        @DisplayName("錯誤先後不變：已取消（不可付款）的單回 E-5011，不會先被說成店鋪停業——守門不被諮詢")
        void notPayable_isReportedBeforeStoreClosed() {
            order(Order.OrderStatus.CANCELLED);
            storeIsClosed();

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("錯誤先後不變：已有成功付款的單回 E-6003，不會先被說成店鋪停業")
        void alreadyPaid_isReportedBeforeStoreClosed() {
            order(Order.OrderStatus.CREATED);
            when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(true);
            storeIsClosed();

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6003));

            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("刻意不擋：Stripe 付款成功入帳（webhook／回跳共用）即使店鋪已停權，訂單仍標成已付款——錢已經收了")
        void stripeSucceeded_isStillRecordedForClosedStore() {
            order(Order.OrderStatus.CREATED);
            storeIsClosed();
            Payment payment = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").build();
            payment.setId(UUID.randomUUID());
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(payment));

            boolean marked = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(marked).isTrue();
            verify(orderRepository).updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.CREATED, Order.OrderStatus.PAID);
            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("刻意不擋：Mock 付款失敗（不收錢）即使店鋪已停權也照常記錄")
        void mockFailure_isNotGuarded() {
            order(Order.OrderStatus.CREATED);
            storeIsClosed();
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            OrderPaymentStateDto state = service.mockPaymentFailure(ORDER_ID, "test");

            assertThat(state.getPaymentStatus()).isEqualTo("FAILED");
            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("付款狀態回應的 storeOpen 反映守門的判斷（前端據此顯示「暫停營業」並收起付款按鈕）")
        void paymentState_exposesStoreOpen() {
            order(Order.OrderStatus.CREATED);
            when(paymentRepository.findEffectiveByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            when(paymentStoreGuard.isStoreOpen(STORE_ID)).thenReturn(true);
            assertThat(service.getOrderPaymentState(ORDER_ID).getStoreOpen()).isTrue();

            when(paymentStoreGuard.isStoreOpen(STORE_ID)).thenReturn(false);
            OrderPaymentStateDto closed = service.getOrderPaymentState(ORDER_ID);
            assertThat(closed.getStoreOpen()).isFalse();
            assertThat(closed.getCanPay()).as("canPay 仍是「訂單狀態允許付款」，店鋪狀態由 storeOpen 單獨表達").isTrue();
        }
    }

    @Nested
    @DisplayName("訂房付款")
    class BookingPayment {

        @Test
        @DisplayName("Mock 付款：店鋪不營業 → E-2010，沒有任何副作用；守門是用訂房自己的租戶諮詢的")
        void mockPay_closedStore_isRejectedWithoutSideEffects() {
            booking(Booking.BookingStatus.CREATED);
            storeIsClosed();

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
            verifyNothingHappened();
        }

        @Test
        @DisplayName("Stripe Checkout：店鋪不營業 → E-2010，而且根本沒有呼叫 Stripe")
        void stripeCheckout_closedStore_neverCallsStripe() {
            booking(Booking.BookingStatus.CREATED);
            stripeEnabled();
            storeIsClosed();

            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_2010));

            verify(paymentStoreGuard).requireStoreOpen(STORE_ID);
            verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
            verifyNothingHappened();
        }

        @Test
        @DisplayName("錯誤先後不變：已取消（不可付款）的訂房回 E-5011，不會先被說成店鋪停業")
        void notPayable_isReportedBeforeStoreClosed() {
            booking(Booking.BookingStatus.CANCELLED);
            storeIsClosed();

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("刻意不擋：Stripe 付款成功入帳即使店鋪已停權，訂房仍標成已付款——錢已經收了")
        void stripeSucceeded_isStillRecordedForClosedStore() {
            booking(Booking.BookingStatus.CREATED);
            storeIsClosed();
            Payment payment = Payment.builder().bookingId(BOOKING_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_b1").amount(new BigDecimal("3000.00")).build();
            payment.setId(UUID.randomUUID());
            when(paymentRepository.findByTransactionId("cs_b1")).thenReturn(Optional.of(payment));

            boolean marked = service.markStripePaymentSucceeded("cs_b1", "pi_b1");

            assertThat(marked).isTrue();
            verify(bookingRepository).updateStatusIfCurrent(eq(BOOKING_ID), eq(Booking.BookingStatus.CREATED),
                    eq(Booking.BookingStatus.PAID));
            verify(paymentStoreGuard, never()).requireStoreOpen(any());
        }

        @Test
        @DisplayName("付款狀態回應的 storeOpen 反映守門的判斷")
        void paymentState_exposesStoreOpen() {
            booking(Booking.BookingStatus.CREATED);
            when(paymentRepository.findEffectiveByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            when(paymentStoreGuard.isStoreOpen(STORE_ID)).thenReturn(true);
            assertThat(service.getBookingPaymentState(BOOKING_ID).getStoreOpen()).isTrue();

            when(paymentStoreGuard.isStoreOpen(STORE_ID)).thenReturn(false);
            assertThat(service.getBookingPaymentState(BOOKING_ID).getStoreOpen()).isFalse();
        }
    }
}
