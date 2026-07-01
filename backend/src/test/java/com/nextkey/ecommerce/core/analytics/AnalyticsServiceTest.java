package com.nextkey.ecommerce.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private Listing listing(Listing.ListingType type, Listing.ListingStatus status) {
        Listing l = org.mockito.Mockito.mock(Listing.class);
        lenient().when(l.getListingType()).thenReturn(type);
        lenient().when(l.getStatus()).thenReturn(status);
        return l;
    }

    // ---- getRevenueStats ----

    @Test
    @DisplayName("getRevenueStats：營收僅計可入帳狀態，AOV 以全部訂單數平均、淨額扣退款")
    void getRevenueStats_filtersByStatus_computesAovAndNet() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 12);
        LocalDate mid = LocalDate.of(2026, 1, 11);
        List<Order> orders = List.of(
                order(Order.OrderStatus.PAID, "100", mid),
                order(Order.OrderStatus.CONFIRMED, "200", mid),
                order(Order.OrderStatus.CREATED, "50", mid),        // 不入營收（未付款）
                order(Order.OrderStatus.CANCELLED, "999", mid));     // 不入營收
        when(orderRepository.findByTenantIdAndCreatedAtBetween(any(), any(), any())).thenReturn(orders);

        Payment refund = org.mockito.Mockito.mock(Payment.class);
        when(refund.getAmount()).thenReturn(new BigDecimal("50"));
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of(refund));

        AnalyticsDto.AnalyticsRequest req = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(start).endDate(end).build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(req);

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("300"); // 100 + 200
        assertThat(stats.getTotalOrders()).isEqualTo(4);                  // 全部訂單數
        assertThat(stats.getAverageOrderValue()).isEqualByComparingTo("75"); // 300 / 4
        assertThat(stats.getTotalRefunds()).isEqualByComparingTo("50");
        assertThat(stats.getNetRevenue()).isEqualByComparingTo("250");   // 300 - 50
        assertThat(stats.getCurrency()).isEqualTo("TWD");
        // 每日分桶：1/10、1/11、1/12 共 3 天；訂單全落在 1/11
        assertThat(stats.getDailyRevenue().getData()).hasSize(3);
    }

    @Test
    @DisplayName("getRevenueStats（邊界）：零訂單 → AOV/營收皆為 0，不發生除以零")
    void getRevenueStats_zeroOrders_returnsZeroAov() {
        when(orderRepository.findByTenantIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
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
        when(orderRepository.findByTenantIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(paymentRepository.findByOrderIdInAndStatus(anyList(), any())).thenReturn(List.of());

        AnalyticsDto.RevenueStats stats =
                analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder().build());

        assertThat(stats.getDailyRevenue().getData()).hasSize(31); // now-30 .. now（含端點）
        assertThat(stats.getDailyRevenue().getStartDate())
                .isEqualTo(stats.getDailyRevenue().getEndDate().minusDays(30));
    }

    // ---- getDashboardStats ----

    @Test
    @DisplayName("getDashboardStats：營收排除取消單，成長率 =(今-昨)/昨×100")
    void getDashboardStats_computesGrowth_excludesCancelled() {
        // 先建 mock 清單（避免在 stubbing 進行中又對 entity 開 when → UnfinishedStubbing）
        List<Order> todayOrders = List.of(
                order(Order.OrderStatus.PAID, "120", LocalDate.now()),
                order(Order.OrderStatus.CANCELLED, "999", LocalDate.now())); // 今日：120
        List<Order> yesterdayOrders = List.of(order(Order.OrderStatus.PAID, "100", LocalDate.now())); // 昨日：100
        List<Order> monthOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now()));     // 本月：120
        List<Order> yearOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now()));      // 本年：120
        // 4 次呼叫依序：今日 / 昨日 / 本月 / 本年
        when(orderRepository.findByTenantIdAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(todayOrders, yesterdayOrders, monthOrders, yearOrders);
        // 3 次 count 呼叫依序：今日 / 昨日 / 本月
        when(orderRepository.countByTenantIdAndCreatedAtBetween(any(), any(), any()))
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
    @DisplayName("getDashboardStats（邊界）：昨日營收為 0 → 成長率保持 0（不除以零）")
    void getDashboardStats_zeroYesterday_growthStaysZero() {
        List<Order> todayOrders = List.of(order(Order.OrderStatus.PAID, "120", LocalDate.now())); // 今日：120
        when(orderRepository.findByTenantIdAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(
                        todayOrders, // 今日：120
                        List.of(),   // 昨日：0
                        List.of(),   // 本月
                        List.of());  // 本年
        when(orderRepository.countByTenantIdAndCreatedAtBetween(any(), any(), any())).thenReturn(1, 0, 1);
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
}
