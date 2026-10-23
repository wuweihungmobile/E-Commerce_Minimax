package com.nextkey.ecommerce.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.AnalyticsDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * AnalyticsService 單元測試（Sprint 28 US-001 / AI-1202）。
 *
 * <p>背景：AnalyticsService 先前 0 測試。本測試聚焦其「有邏輯」之處（非單純 repo passthrough）：
 * 營收的訂單狀態過濾、成長率（含除以零邊界）、AOV（零訂單邊界）、預設日期區間、
 * 每日分桶筆數、訂單狀態分組、listing 型別/狀態計數。以 Mockito mock repository 與 entity，
 * 並設置 TenantContext。
 */
@DisplayName("AnalyticsService 單元測試（核心統計 + 邊界）")
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- helpers ----

    private Order order(Order.OrderStatus status, String amount, LocalDate createdAt) {
        Order o = org.mockito.Mockito.mock(Order.class);
        lenient().when(o.getStatus()).thenReturn(status);
        lenient().when(o.getTotalAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getCreatedAt())
                .thenReturn(createdAt.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return o;
    }

    /** 掛在某張訂單上的付款（只 stub 淨營收計算會讀的欄位）。 */
    private Payment payment(UUID orderId, Payment.PaymentStatus status, String amount, String refundedAmount) {
        Payment p = org.mockito.Mockito.mock(Payment.class);
        lenient().when(p.getOrderId()).thenReturn(orderId);
        lenient().when(p.getStatus()).thenReturn(status);
        lenient().when(p.getAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(p.getRefundedAmount()).thenReturn(refundedAmount == null ? null : new BigDecimal(refundedAmount));
        return p;
    }

    /** 兩種退款狀態各自的查詢結果（lenient：只查其中一種的實作不會因為嚴格模式的『未使用 stub』而紅燈）。 */
    private void stubRefundedPayments(List<Payment> refunded, List<Payment> partiallyRefunded) {
        lenient().when(paymentRepository.findByOrderIdInAndStatus(anyList(), eq(Payment.PaymentStatus.REFUNDED)))
                .thenReturn(refunded);
        lenient().when(paymentRepository.findByOrderIdInAndStatus(anyList(), eq(Payment.PaymentStatus.PARTIALLY_REFUNDED)))
                .thenReturn(partiallyRefunded);
    }

    private Listing listing(Listing.ListingType type, Listing.ListingStatus status) {
        Listing l = org.mockito.Mockito.mock(Listing.class);
        lenient().when(l.getListingType()).thenReturn(type);
        lenient().when(l.getStatus()).thenReturn(status);
        return l;
    }

    // ---- getRevenueStats ----

    @Test
    @DisplayName("getRevenueStats：營收僅計入帳狀態（已付款、未取消、未退款），AOV 以入帳訂單數平均（DEF-407）、淨額扣退款")
    void getRevenueStats_filtersByStatus_computesAovAndNet() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 12);
        LocalDate mid = LocalDate.of(2026, 1, 11);
        List<Order> orders = List.of(
                order(Order.OrderStatus.PAID, "100", mid),
                order(Order.OrderStatus.CONFIRMED, "200", mid),
                order(Order.OrderStatus.CREATED, "50", mid),        // 不入營收（未付款）
                order(Order.OrderStatus.CANCELLED, "999", mid));     // 不入營收
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);

        // 淨額扣的是「計入營收的訂單」上的退款（DEF-412）：CONFIRMED 那張（200）部分退款 50（付款 PARTIALLY_REFUNDED）。
        // 原本用 any() 讓任何狀態的查詢都回同一個只 stub 了 getAmount() 的 mock，既沒指定它屬於哪張訂單，也會在新實作
        // （分別查 REFUNDED 與 PARTIALLY_REFUNDED）下被算兩次。
        stubRefundedPayments(List.of(),
                List.of(payment(orders.get(1).getId(), Payment.PaymentStatus.PARTIALLY_REFUNDED, "200", "50")));

        AnalyticsDto.AnalyticsRequest req = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(start).endDate(end).build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(req);

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("300"); // 100 + 200
        assertThat(stats.getTotalOrders()).isEqualTo(4);                  // 區間內全部訂單數（含未付款、已取消）
        // DEF-407：分母是「入帳的訂單數」（PAID、CONFIRMED 共 2 張），不含未付款與已取消；原本是 300 / 4 ＝ 75
        assertThat(stats.getAverageOrderValue()).isEqualByComparingTo("150"); // 300 / 2
        assertThat(stats.getTotalRefunds()).isEqualByComparingTo("50");
        assertThat(stats.getNetRevenue()).isEqualByComparingTo("250");   // 300 - 50
        assertThat(stats.getCurrency()).isEqualTo("TWD");
        // 每日分桶：1/10、1/11、1/12 共 3 天；訂單全落在 1/11
        assertThat(stats.getDailyRevenue().getData()).hasSize(3);
    }

    @Test
    @DisplayName("🔴 DEF-407 getRevenueStats：未付款（CREATED）、等待退款（REFUNDING）、已退款（REFUNDED）、已取消都不計營收；"
            + "每日趨勢的營收加總＝總營收，平均客單價以入帳訂單數平均")
    void getRevenueStats_unpaidAndRefundedNeverCountAsRevenue_trendAgreesWithTotal() {
        LocalDate day = LocalDate.of(2026, 1, 11);
        List<Order> orders = List.of(
                order(Order.OrderStatus.PAID, "100", day),
                order(Order.OrderStatus.COMPLETED, "200", day),
                order(Order.OrderStatus.CREATED, "50", day),       // 未付款
                order(Order.OrderStatus.REFUNDING, "70", day),     // 已取消、等待退款
                order(Order.OrderStatus.REFUNDED, "80", day),      // 已退款
                order(Order.OrderStatus.CANCELLED, "999", day));   // 已取消
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(day).endDate(day).build());

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("300"); // 100 + 200
        BigDecimal trendSum = stats.getDailyRevenue().getData().stream()
                .map(AnalyticsDto.DailyRevenue::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 趨勢原本只排除 CANCELLED／REFUNDED，會把 CREATED 50 與 REFUNDING 70 算進去（420），與總營收（300）對不上
        assertThat(trendSum).as("每日趨勢的營收加總必須等於總營收").isEqualByComparingTo("300");
        assertThat(stats.getAverageOrderValue()).as("300 ÷ 2 張入帳訂單").isEqualByComparingTo("150");
        assertThat(stats.getTotalOrders()).as("區間內全部訂單").isEqualTo(6);
    }

    @Test
    @DisplayName("🔴 DEF-412 getRevenueStats：全額退款的訂單已不在營收裡，其退款不得在淨營收再扣一次（原本單一銷售全額退款會得到負數）")
    void getRevenueStats_fullRefund_isNotSubtractedTwiceFromNet() {
        LocalDate day = LocalDate.of(2026, 1, 11);
        Order kept = order(Order.OrderStatus.PAID, "500", day);
        Order refunded = order(Order.OrderStatus.REFUNDED, "500", day);   // 全額退款：訂單與付款都轉 REFUNDED
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of(kept, refunded));
        stubRefundedPayments(
                List.of(payment(refunded.getId(), Payment.PaymentStatus.REFUNDED, "500", "500")), List.of());

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(day).endDate(day).build());

        assertThat(stats.getTotalRevenue()).as("REFUNDED 訂單不計營收").isEqualByComparingTo("500");
        assertThat(stats.getTotalRefunds()).as("退款總額仍要顯示這筆全額退款").isEqualByComparingTo("500");
        // 修復前：500 - 500 = 0（同一筆全額退款在營收端已排除、又在退款端再扣一次）
        assertThat(stats.getNetRevenue()).as("淨營收不該再扣已排除訂單的退款").isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("🔴 DEF-412 getRevenueStats：部分退款（付款 PARTIALLY_REFUNDED、訂單狀態不變）要計入退款總額，並從淨營收扣除")
    void getRevenueStats_partialRefund_isCountedAndDeductedFromNet() {
        LocalDate day = LocalDate.of(2026, 1, 11);
        Order delivered = order(Order.OrderStatus.DELIVERED, "450", day);   // 部分退款後訂單狀態不變
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of(delivered));
        stubRefundedPayments(
                List.of(), List.of(payment(delivered.getId(), Payment.PaymentStatus.PARTIALLY_REFUNDED, "450", "200")));

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(day).endDate(day).build());

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("450");
        // 修復前只查 REFUNDED：部分退款完全不被計入，退款總額 0、淨營收仍是 450
        assertThat(stats.getTotalRefunds()).as("部分退款要計入").isEqualByComparingTo("200");
        assertThat(stats.getNetRevenue()).as("450 - 200").isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("getRevenueStats：付款已是 REFUNDED 但 refundedAmount 沒被寫入（Stripe webhook 路徑只改狀態）→ 以付款金額計；"
            + "訂單若仍在營收狀態，要從淨營收扣除")
    void getRevenueStats_refundedPaymentWithoutRefundedAmount_usesPaymentAmount() {
        LocalDate day = LocalDate.of(2026, 1, 11);
        // webhook 路徑只在 OrderStateMachine.canRefund 時才把訂單轉成 REFUNDED，否則訂單維持原狀態
        Order completed = order(Order.OrderStatus.COMPLETED, "300", day);
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of(completed));
        stubRefundedPayments(
                List.of(payment(completed.getId(), Payment.PaymentStatus.REFUNDED, "300", "0")), List.of());

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(day).endDate(day).build());

        assertThat(stats.getTotalRefunds()).as("markRefundedIfNotAlready 只改 status，refundedAmount 仍是 0，不能因此算成 0")
                .isEqualByComparingTo("300");
        assertThat(stats.getNetRevenue()).as("訂單仍計入營收 300，退款 300").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getRevenueStats（邊界）：部分退款付款的 refundedAmount 為 null（欄位可為空）→ 當 0，不拋例外")
    void getRevenueStats_nullRefundedAmount_isTreatedAsZero() {
        LocalDate day = LocalDate.of(2026, 1, 11);
        Order delivered = order(Order.OrderStatus.DELIVERED, "100", day);
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of(delivered));
        stubRefundedPayments(
                List.of(), List.of(payment(delivered.getId(), Payment.PaymentStatus.PARTIALLY_REFUNDED, "100", null)));

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(day).endDate(day).build());

        assertThat(stats.getTotalRefunds()).isEqualByComparingTo("0");
        assertThat(stats.getNetRevenue()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("getRevenueStats（邊界）：零訂單 → AOV/營收皆為 0，不發生除以零")
    void getRevenueStats_zeroOrders_returnsZeroAov() {
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of());
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.AnalyticsRequest req = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(LocalDate.of(2026, 1, 10)).endDate(LocalDate.of(2026, 1, 10)).build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(req);

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(stats.getTotalOrders()).isZero();
        assertThat(stats.getAverageOrderValue()).isEqualByComparingTo("0");
        assertThat(stats.getNetRevenue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getRevenueStats（邊界）：未帶日期 → 預設區間為近 30 天（含端點共 31 天）")
    void getRevenueStats_nullDates_defaultsTo31DayRange() {
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of());
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.RevenueStats stats =
                analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder().build());

        assertThat(stats.getDailyRevenue().getData()).hasSize(31); // now-30 .. now（含端點）
        assertThat(stats.getDailyRevenue().getStartDate())
                .isEqualTo(stats.getDailyRevenue().getEndDate().minusDays(30));
    }

    @Test
    @DisplayName("getRevenueStats：granularity=WEEK → 依週一為起始分桶（Sprint 65 US-001）")
    void getRevenueStats_weekGranularity_bucketsByIsoWeek() {
        LocalDate start = LocalDate.of(2026, 1, 5); // 週一
        LocalDate end = LocalDate.of(2026, 1, 18);  // 兩週後的週日
        List<Order> orders = List.of(
                order(Order.OrderStatus.PAID, "100", LocalDate.of(2026, 1, 7)),  // 第一週
                order(Order.OrderStatus.PAID, "200", LocalDate.of(2026, 1, 15))); // 第二週
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.AnalyticsRequest req = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(start).endDate(end).granularity("WEEK").build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(req);

        assertThat(stats.getDailyRevenue().getData()).hasSize(2);
        assertThat(stats.getDailyRevenue().getData().get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(stats.getDailyRevenue().getData().get(0).getRevenue()).isEqualByComparingTo("100");
        assertThat(stats.getDailyRevenue().getData().get(1).getDate()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(stats.getDailyRevenue().getData().get(1).getRevenue()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("getRevenueStats：granularity=MONTH → 依月初為起始分桶（Sprint 65 US-001）")
    void getRevenueStats_monthGranularity_bucketsByCalendarMonth() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);
        List<Order> orders = List.of(
                order(Order.OrderStatus.PAID, "100", LocalDate.of(2026, 1, 15)),
                order(Order.OrderStatus.PAID, "200", LocalDate.of(2026, 2, 10)));
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.AnalyticsRequest req = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(start).endDate(end).granularity("MONTH").build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(req);

        assertThat(stats.getDailyRevenue().getData()).hasSize(2);
        assertThat(stats.getDailyRevenue().getData().get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(stats.getDailyRevenue().getData().get(0).getRevenue()).isEqualByComparingTo("100");
        assertThat(stats.getDailyRevenue().getData().get(1).getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(stats.getDailyRevenue().getData().get(1).getRevenue()).isEqualByComparingTo("200");
    }

    // ---- getDashboardStats ----

    @Test
    @DisplayName("getDashboardStats：營收排除取消單，成長率 =(今-昨)/昨×100")
    @SuppressWarnings("unchecked")
    void getDashboardStats_computesGrowth_excludesCancelled() {
        // 先建 mock 清單（避免在 stubbing 進行中又對 entity 開 when → UnfinishedStubbing）
        List<Order> todayOrders = List.of(
                order(Order.OrderStatus.PAID, "120", LocalDate.now()),
                order(Order.OrderStatus.CANCELLED, "999", LocalDate.now())); // 今日：120
        List<Order> yesterdayOrders = List.of(order(Order.OrderStatus.PAID, "100", LocalDate.now())); // 昨日：100
        List<Order> monthOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now()));     // 本月：120
        List<Order> yearOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now()));      // 本年：120
        // 4 次呼叫依序：今日 / 昨日 / 本月 / 本年
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(todayOrders, yesterdayOrders, monthOrders, yearOrders);
        // 3 次 count 呼叫依序：今日 / 昨日 / 本月
        when(orderRepository.countByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(5, 3, 20);
        when(orderRepository.countByTenantIdAndStatus(any(), any())).thenReturn(2);
        when(productRepository.countByListingTenantIdAndListingStatus(any(), any())).thenReturn(7);
        when(roomRepository.countByListingTenantId(any())).thenReturn(4);

        AnalyticsDto.DashboardStats stats = analyticsService.getDashboardStats();

        assertThat(stats.getTodayRevenue()).isEqualByComparingTo("120");      // 排除 CANCELLED 999
        assertThat(stats.getYesterdayRevenue()).isEqualByComparingTo("100");
        assertThat(stats.getRevenueGrowthPercent()).isEqualByComparingTo("20"); // (120-100)/100*100
        assertThat(stats.getTodayOrders()).isEqualTo(5);
        assertThat(stats.getPendingOrders()).isEqualTo(2);
        assertThat(stats.getActiveListings()).isEqualTo(7);
        assertThat(stats.getTotalRooms()).isEqualTo(4);
    }

    @Test
    @DisplayName("🔴 DEF-407 getDashboardStats：今日／昨日／本月／本年營收只計入帳狀態，未付款與等待退款、已退款、已取消都不計")
    void getDashboardStats_revenueCountsOnlyPaidStatuses() {
        LocalDate today = LocalDate.now();
        List<Order> mixed = List.of(
                order(Order.OrderStatus.PAID, "100", today),
                order(Order.OrderStatus.CONFIRMED, "10", today),
                order(Order.OrderStatus.SHIPPING, "20", today),
                order(Order.OrderStatus.DELIVERED, "30", today),
                order(Order.OrderStatus.COMPLETED, "40", today),
                order(Order.OrderStatus.CREATED, "500", today),     // 未付款
                order(Order.OrderStatus.REFUNDING, "600", today),   // 已取消、等待退款
                order(Order.OrderStatus.REFUNDED, "700", today),    // 已退款
                order(Order.OrderStatus.CANCELLED, "800", today));  // 已取消
        // 4 次呼叫（今日／昨日／本月／本年）都回同一批訂單：四個期間的口徑必須一致
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(mixed);
        when(orderRepository.countByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(9, 0, 9);
        when(orderRepository.countByTenantIdAndStatus(any(), any())).thenReturn(1);
        when(productRepository.countByListingTenantIdAndListingStatus(any(), any())).thenReturn(0);
        when(roomRepository.countByListingTenantId(any())).thenReturn(0);

        AnalyticsDto.DashboardStats stats = analyticsService.getDashboardStats();

        // 100 + 10 + 20 + 30 + 40；原本只排除 CANCELLED／REFUNDED，會把 CREATED 500 與 REFUNDING 600 算進去（1300）
        assertThat(stats.getTodayRevenue()).isEqualByComparingTo("200");
        assertThat(stats.getYesterdayRevenue()).isEqualByComparingTo("200");
        assertThat(stats.getMonthRevenue()).isEqualByComparingTo("200");
        assertThat(stats.getYearRevenue()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("getDashboardStats（邊界）：昨日營收為 0 → 成長率保持 0（不除以零）")
    @SuppressWarnings("unchecked")
    void getDashboardStats_zeroYesterday_growthStaysZero() {
        List<Order> todayOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now())); // 今日：120
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(
                        todayOrders, // 今日：120
                        List.of(),   // 昨日：0
                        List.of(),   // 本月
                        List.of());  // 本年
        when(orderRepository.countByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(1, 0, 1);
        when(orderRepository.countByTenantIdAndStatus(any(), any())).thenReturn(0);
        when(productRepository.countByListingTenantIdAndListingStatus(any(), any())).thenReturn(0);
        when(roomRepository.countByListingTenantId(any())).thenReturn(0);

        AnalyticsDto.DashboardStats stats = analyticsService.getDashboardStats();

        assertThat(stats.getYesterdayRevenue()).isEqualByComparingTo("0");
        assertThat(stats.getRevenueGrowthPercent()).isEqualByComparingTo("0"); // 不因除以零爆掉
    }

    // ---- getOrderStats ----

    @Test
    @DisplayName("getOrderStats：依狀態分組計數，缺漏狀態回 0")
    void getOrderStats_groupsByStatus() {
        List<Order> orders = List.of(
                order(Order.OrderStatus.CREATED, "10", LocalDate.now()),
                order(Order.OrderStatus.PAID, "10", LocalDate.now()),
                order(Order.OrderStatus.PAID, "10", LocalDate.now()),
                order(Order.OrderStatus.COMPLETED, "10", LocalDate.now()),
                order(Order.OrderStatus.CANCELLED, "10", LocalDate.now()));
        when(orderRepository.findByTenantId(any())).thenReturn(orders);

        AnalyticsDto.OrderStats stats = analyticsService.getOrderStats();

        assertThat(stats.getTotalOrders()).isEqualTo(5);
        assertThat(stats.getPendingPayment()).isEqualTo(1);  // CREATED
        assertThat(stats.getPendingShipment()).isEqualTo(2); // PAID
        assertThat(stats.getCompleted()).isEqualTo(1);
        assertThat(stats.getCancelled()).isEqualTo(1);
        assertThat(stats.getInTransit()).isZero();           // SHIPPING 缺漏 → 0
    }

    // ---- getListingStats ----

    @Test
    @DisplayName("getListingStats：依 listing 型別/狀態分別計數")
    void getListingStats_countsByTypeAndStatus() {
        List<Listing> listings = List.of(
                listing(Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE),
                listing(Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE),
                listing(Listing.ListingType.PRODUCT, Listing.ListingStatus.INACTIVE),
                listing(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE),
                listing(Listing.ListingType.ROOM, Listing.ListingStatus.INACTIVE));
        when(listingRepository.findByTenantId(any())).thenReturn(listings);

        AnalyticsDto.ListingStats stats = analyticsService.getListingStats();

        assertThat(stats.getTotalListings()).isEqualTo(5);
        assertThat(stats.getActiveProducts()).isEqualTo(2);
        assertThat(stats.getInactiveProducts()).isEqualTo(1);
        assertThat(stats.getActiveRooms()).isEqualTo(1);
        assertThat(stats.getInactiveRooms()).isEqualTo(1);
    }

    // ---- getRecentActivity（Sprint 62 US-002）----

    @Test
    @DisplayName("getRecentActivity：依訂單建立時間倒序組成活動項目")
    void getRecentActivity_mapsOrdersToActivityItems() {
        Order o = order(Order.OrderStatus.PAID, "100", LocalDate.now());
        lenient().when(o.getCurrency()).thenReturn("TWD");
        org.springframework.data.domain.Page<Order> page =
                new org.springframework.data.domain.PageImpl<>(List.of(o));
        when(orderRepository.findByTenantIdOrderByCreatedAtDesc(any(), any())).thenReturn(page);

        AnalyticsDto.RecentActivity result = analyticsService.getRecentActivity(10);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getType()).isEqualTo("ORDER_PAID");
        assertThat(result.getItems().get(0).getDescription()).contains("100").contains("TWD");
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRecentActivity（邊界）：無訂單 → 回傳空清單")
    void getRecentActivity_noOrders_returnsEmpty() {
        org.springframework.data.domain.Page<Order> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(orderRepository.findByTenantIdOrderByCreatedAtDesc(any(), any())).thenReturn(emptyPage);

        AnalyticsDto.RecentActivity result = analyticsService.getRecentActivity(10);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
    }

    @Test
    @DisplayName("Sprint 164: getRecentActivity limit 帶超大值時，實際查詢頁面大小上限為 100（避免資源耗盡）")
    void getRecentActivity_hugeLimit_cappedAt100() {
        org.springframework.data.domain.Page<Order> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(orderRepository.findByTenantIdOrderByCreatedAtDesc(any(), any())).thenReturn(emptyPage);

        analyticsService.getRecentActivity(999999999);

        ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orderRepository).findByTenantIdOrderByCreatedAtDesc(any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
