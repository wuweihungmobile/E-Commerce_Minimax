package com.nextkey.ecommerce.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;

/**
 * {@link BuyerNotificationService}（Sprint 229）：PRD US-005「取消後即時收到退款狀態通知」、US-014 付款逾時通知、
 * 退款完成通知。資料庫與 Redis 的真實行為（資料列真的寫入、使用者看得到）由整合測試驗證；這裡驗證「說什麼」與「失敗時怎麼辦」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BuyerNotificationService 單元測試（Sprint 229）")
class BuyerNotificationServiceTest {

    private static final UUID BUYER = UUID.fromString("b0b0b0b0-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID = UUID.fromString("abcd1234-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("feed5678-0000-0000-0000-000000000002");
    private static final UUID LISTING_ID = UUID.fromString("11110000-0000-0000-0000-000000000003");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 5);

    @Mock private NotificationService notificationService;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private BuyerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new BuyerNotificationService(notificationService, orderRepository, bookingRepository,
                listingRepository, paymentRepository, transactionManager);
    }

    private Booking cancelledBooking(final Booking.CancelledBy by, final BigDecimal refundAmount) {
        return Booking.builder().id(BOOKING_ID).userId(BUYER).roomListingId(LISTING_ID).checkInDate(CHECK_IN)
                .cancelledBy(by).refundStatus(refundAmount != null ? Booking.RefundStatus.PENDING
                        : Booking.RefundStatus.NONE).refundAmount(refundAmount).build();
    }

    private void givenListingTitle(final String title) {
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(Listing.builder().id(LISTING_ID).title(title).build()));
    }

    private NotificationDto.SendRequest sent() {
        ArgumentCaptor<NotificationDto.SendRequest> captor = ArgumentCaptor.forClass(NotificationDto.SendRequest.class);
        verify(notificationService).sendNotification(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("文案：取消訂房後的退款狀態要對應 PRD Q14 的每一種結果（US-005）")
    class BookingCancelledContent {

        private final String name = BuyerNotificationService.bookingName("海景民宿", BOOKING_ID);

        @Test
        @DisplayName("未付款：說明尚未付款、不需退款（不能讓買家以為錢會退）")
        void unpaid() {
            String content = BuyerNotificationService.bookingCancelledContent(name, CHECK_IN,
                    Booking.CancelledBy.CUSTOMER, false, null);

            assertThat(content).contains("您已取消訂房「海景民宿」", "2026-10-05 入住", "尚未付款", "不需退款");
        }

        @Test
        @DisplayName("已付款且有退款：寫出金額、說明已進入處理並會再通知（不是「已退回」，那是退款完成才說的）")
        void paidWithRefund() {
            String content = BuyerNotificationService.bookingCancelledContent(name, CHECK_IN,
                    Booking.CancelledBy.CUSTOMER, true, new BigDecimal("3600.00"));

            assertThat(content).contains("退款 NT$3,600 已進入處理", "再通知您").doesNotContain("已退回原付款方式並");
        }

        @Test
        @DisplayName("買家在入住前不足 24 小時取消：明說依政策不退款，而不是含糊帶過")
        void insideTheNoRefundWindow() {
            String content = BuyerNotificationService.bookingCancelledContent(name, CHECK_IN,
                    Booking.CancelledBy.CUSTOMER, true, null);

            assertThat(content).contains("不足 24 小時", "不予退款");
        }

        @Test
        @DisplayName("商家／管理員取消：點名是商家取消，並照常告知退款金額（PRD：商家取消一律全額）")
        void merchantCancel() {
            String content = BuyerNotificationService.bookingCancelledContent(name, CHECK_IN,
                    Booking.CancelledBy.MERCHANT, true, new BigDecimal("3600"));

            assertThat(content).contains("商家已取消您的訂房「海景民宿」", "退款 NT$3,600");
        }

        @Test
        @DisplayName("商家取消、已付款卻沒有可退金額（找不到有效付款的異常）：不套用「不予退款」，交給客服確認")
        void merchantCancelWithNothingRefundableIsNotReportedAsPolicy() {
            String content = BuyerNotificationService.bookingCancelledContent(name, CHECK_IN,
                    Booking.CancelledBy.MERCHANT, true, null);

            assertThat(content).contains("客服").doesNotContain("不予退款");
        }
    }

    @Nested
    @DisplayName("文案：識別與金額的呈現與前端一致")
    class Wording {

        @Test
        @DisplayName("沒有房源標題時退回「訂房 #編號前 8 碼」，與前端一致（不會出現 null 或空括號）")
        void bookingNameFallsBackToShortId() {
            assertThat(BuyerNotificationService.bookingName(null, BOOKING_ID)).isEqualTo("訂房 #abcd1234");
            assertThat(BuyerNotificationService.bookingName("  ", BOOKING_ID)).isEqualTo("訂房 #abcd1234");
        }

        @Test
        @DisplayName("金額：千分位、去掉多餘的 .00、保留真正的小數；新台幣用 NT$，其他幣別顯示代碼")
        void money() {
            assertThat(BuyerNotificationService.formatMoney(new BigDecimal("1234.00"), "TWD")).isEqualTo("NT$1,234");
            assertThat(BuyerNotificationService.formatMoney(new BigDecimal("99.50"), null)).isEqualTo("NT$99.5");
            assertThat(BuyerNotificationService.formatMoney(new BigDecimal("10"), "USD")).isEqualTo("USD 10");
        }

        @Test
        @DisplayName("退款完成文案：沒有金額時省略金額，不留下「款項 已退回」這種斷句")
        void refundedContentWithoutAmount() {
            assertThat(BuyerNotificationService.orderRefundedContent(ORDER_ID, null, "TWD"))
                    .isEqualTo("訂單 #feed5678 的款項已退回原付款方式，實際入帳時間依付款機構而定。");
            assertThat(BuyerNotificationService.orderRefundedContent(ORDER_ID, new BigDecimal("500"), "TWD"))
                    .contains("款項 NT$500 已退回");
        }
    }

    @Nested
    @DisplayName("送出：對的人、對的類型、站內通知、帶得出連結的 data")
    class Sending {

        @Test
        @DisplayName("取消訂房 → 訂房人收到 ORDER_CANCELLED 站內通知，data 帶 bookingId 與退款金額")
        void bookingCancelled() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, new BigDecimal("3600"))));

            service.notifyBookingCancelled(BOOKING_ID, true);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.ORDER_CANCELLED);
            assertThat(request.getChannel()).as("EMAIL／SMS／PUSH 沒有真正的送出實作（DEF-317），只送站內通知")
                    .isEqualTo(NotificationDto.Channel.IN_APP);
            assertThat(request.getTitle()).isEqualTo("訂房已取消");
            assertThat(request.getContent()).contains("海景民宿", "NT$3,600");
            assertThat(request.getData()).containsEntry("bookingId", BOOKING_ID.toString())
                    .containsEntry("refundAmount", new BigDecimal("3600"));
        }

        @Test
        @DisplayName("沒有退款時 data 不放 refundAmount（不送 null 值）")
        void noRefundAmountInData() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, null)));

            service.notifyBookingCancelled(BOOKING_ID, false);

            assertThat(sent().getData()).containsOnlyKeys("bookingId");
        }

        @Test
        @DisplayName("訂房逾時 → data 帶 listingId，前端用它提供「重新預訂」連結（PRD US-014 重試連結）")
        void bookingPaymentTimeout() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.SYSTEM, null)));

            service.notifyBookingPaymentTimeout(BOOKING_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.ORDER_CANCELLED);
            assertThat(request.getTitle()).isEqualTo("訂房因逾期未付款已取消");
            assertThat(request.getContent()).contains("海景民宿", "超過付款期限");
            assertThat(request.getData()).containsEntry("bookingId", BOOKING_ID.toString())
                    .containsEntry("listingId", LISTING_ID.toString()).containsEntry("reason", "PAYMENT_TIMEOUT");
        }

        @Test
        @DisplayName("訂單逾時 → 訂單人收到通知，data 帶 orderId（前端的「查看訂單」連結用它）")
        void orderPaymentTimeout() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(Order.builder().id(ORDER_ID)
                    .userId(BUYER).totalAmount(new BigDecimal("1280.00")).currency("TWD").build()));

            service.notifyOrderPaymentTimeout(ORDER_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.ORDER_CANCELLED);
            assertThat(request.getContent()).contains("#feed5678", "NT$1,280", "超過付款期限");
            assertThat(request.getData()).containsEntry("orderId", ORDER_ID.toString());
        }

        @Test
        @DisplayName("訂單退款完成 → REFUND_COMPLETED，金額取付款「實際已退」的累計額，而不是訂單總額")
        void orderRefundedReportsWhatWasActuallyRefunded() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(Order.builder().id(ORDER_ID)
                    .userId(BUYER).totalAmount(new BigDecimal("2000")).currency("TWD").build()));
            when(paymentRepository.findEffectiveByOrderId(ORDER_ID)).thenReturn(Optional.of(
                    Payment.builder().refundedAmount(new BigDecimal("1500.00")).build()));

            service.notifyOrderRefunded(ORDER_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.REFUND_COMPLETED);
            assertThat(request.getTitle()).isEqualTo("退款已完成");
            assertThat(request.getContent()).contains("NT$1,500").doesNotContain("2,000");
            assertThat(request.getData()).containsEntry("orderId", ORDER_ID.toString())
                    .containsEntry("refundAmount", new BigDecimal("1500.00"));
        }

        @Test
        @DisplayName("訂單退款完成但查不到付款金額：照樣通知，只是不寫金額")
        void orderRefundedWithoutPaymentStillNotifies() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(Order.builder().id(ORDER_ID)
                    .userId(BUYER).totalAmount(new BigDecimal("2000")).currency("TWD").build()));
            when(paymentRepository.findEffectiveByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.notifyOrderRefunded(ORDER_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getContent()).doesNotContain("NT$");
            assertThat(request.getData()).containsOnlyKeys("orderId");
        }

        @Test
        @DisplayName("訂房退款完成 → REFUND_COMPLETED，金額是取消時依 Q14 決定的應退金額")
        void bookingRefunded() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, new BigDecimal("3600"))));

            service.notifyBookingRefunded(BOOKING_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.REFUND_COMPLETED);
            assertThat(request.getContent()).contains("海景民宿", "NT$3,600", "已退回原付款方式");
            assertThat(request.getData()).containsEntry("bookingId", BOOKING_ID.toString());
        }

        @Test
        @DisplayName("訂房付款成功 → BOOKING_CONFIRMED 站內通知（PRD US-001），data 帶 bookingId")
        void bookingConfirmed() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, null)));

            service.notifyBookingConfirmed(BOOKING_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.BOOKING_CONFIRMED);
            assertThat(request.getChannel()).isEqualTo(NotificationDto.Channel.IN_APP);
            assertThat(request.getTitle()).isEqualTo("預訂已確認");
            assertThat(request.getContent()).contains("海景民宿", "2026-10-05 入住", "已確認");
            assertThat(request.getData()).containsOnlyKeys("bookingId").containsEntry("bookingId", BOOKING_ID.toString());
        }

        @Test
        @DisplayName("訂單付款失敗（Stripe 金流阻斷）→ PAYMENT_FAILED，data 帶 orderId（前端的「查看訂單」連結用它重試付款，PRD US-014）")
        void orderPaymentFailed() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(Order.builder().id(ORDER_ID)
                    .userId(BUYER).totalAmount(new BigDecimal("1280.00")).currency("TWD").build()));

            service.notifyOrderPaymentFailed(ORDER_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.PAYMENT_FAILED);
            assertThat(request.getTitle()).isEqualTo("訂單付款失敗");
            assertThat(request.getContent()).contains("#feed5678", "NT$1,280", "付款失敗", "重新付款");
            assertThat(request.getData()).containsEntry("orderId", ORDER_ID.toString());
        }

        @Test
        @DisplayName("訂房付款失敗（Stripe 金流阻斷）→ PAYMENT_FAILED，data 帶 bookingId（PRD US-014）")
        void bookingPaymentFailed() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, null)));

            service.notifyBookingPaymentFailed(BOOKING_ID);

            NotificationDto.SendRequest request = sent();
            assertThat(request.getUserId()).isEqualTo(BUYER);
            assertThat(request.getNotificationType()).isEqualTo(NotificationDto.NotificationType.PAYMENT_FAILED);
            assertThat(request.getTitle()).isEqualTo("訂房付款失敗");
            assertThat(request.getContent()).contains("海景民宿", "2026-10-05 入住", "付款失敗", "重新付款");
            assertThat(request.getData()).containsEntry("bookingId", BOOKING_ID.toString());
        }
    }

    @Nested
    @DisplayName("失敗隔離：通知出任何狀況都不可影響已完成的取消／退款")
    class FailureIsolation {

        @Test
        @DisplayName("交易必須是 REQUIRES_NEW：在 afterCommit 回呼裡呼叫時，原交易已提交但資源仍綁著，加入它的話通知永遠不會被提交")
        void opensItsOwnTransaction() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            service.notifyOrderPaymentTimeout(ORDER_ID);

            ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(transactionManager).getTransaction(definition.capture());
            assertThat(definition.getValue().getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Test
        @DisplayName("資料列已不存在（例如訂房被刪）：什麼都不送、不拋例外")
        void missingRecordSendsNothing() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatCode(() -> {
                service.notifyBookingCancelled(BOOKING_ID, true);
                service.notifyBookingPaymentTimeout(BOOKING_ID);
                service.notifyBookingRefunded(BOOKING_ID);
                service.notifyOrderPaymentTimeout(ORDER_ID);
                service.notifyOrderRefunded(ORDER_ID);
                service.notifyBookingConfirmed(BOOKING_ID);
                service.notifyOrderPaymentFailed(ORDER_ID);
                service.notifyBookingPaymentFailed(BOOKING_ID);
            }).doesNotThrowAnyException();
            verify(notificationService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("送出失敗（Redis 掛了、使用者不存在…）：吞掉例外。若拋出，排程會把「已退款成功」的訂單當成失敗而重試退款")
        void sendFailureNeverEscapes() {
            givenListingTitle("海景民宿");
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(cancelledBooking(Booking.CancelledBy.CUSTOMER, new BigDecimal("3600"))));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(Order.builder().id(ORDER_ID)
                    .userId(BUYER).totalAmount(BigDecimal.TEN).currency("TWD").build()));
            doThrow(new IllegalStateException("redis down")).when(notificationService).sendNotification(any());

            assertThatCode(() -> {
                service.notifyBookingCancelled(BOOKING_ID, true);
                service.notifyBookingPaymentTimeout(BOOKING_ID);
                service.notifyBookingRefunded(BOOKING_ID);
                service.notifyOrderPaymentTimeout(ORDER_ID);
                service.notifyOrderRefunded(ORDER_ID);
                service.notifyBookingConfirmed(BOOKING_ID);
                service.notifyOrderPaymentFailed(ORDER_ID);
                service.notifyBookingPaymentFailed(BOOKING_ID);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("讀資料就失敗（資料庫暫時不可用）也一樣吞掉")
        void readFailureNeverEscapes() {
            when(bookingRepository.findById(BOOKING_ID)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> service.notifyBookingCancelled(BOOKING_ID, true)).doesNotThrowAnyException();
        }
    }
}
