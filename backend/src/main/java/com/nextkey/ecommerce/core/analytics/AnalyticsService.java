package com.nextkey.ecommerce.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分析服務 (Mock Implementation)
 * Phase 1 提供基礎統計數據
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final RoomRepository roomRepository;

    private static final int DEFAULT_PAGE_SIZE = 30;

    /**
     * 取得儀表板統計
     */
    @Transactional(readOnly = true)
    public AnalyticsDto.DashboardStats getDashboardStats() {
        UUID tenantId = TenantContext.getCurrentTenant();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = today.withDayOfYear(1);

        // 今日營收
        BigDecimal todayRevenue = calculateRevenueForDate(tenantId, today);

        // 昨日營收
        BigDecimal yesterdayRevenue = calculateRevenueForDate(tenantId, yesterday);

        // 本月營收
        BigDecimal monthRevenue = calculateRevenueForDateRange(tenantId, monthStart, today);

        // 本年營收
        BigDecimal yearRevenue = calculateRevenueForDateRange(tenantId, yearStart, today);

        // 營收成長
        BigDecimal revenueGrowthPercent = BigDecimal.ZERO;
        if (yesterdayRevenue.compareTo(BigDecimal.ZERO) > 0) {
            revenueGrowthPercent = todayRevenue.subtract(yesterdayRevenue)
                    .divide(yesterdayRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // 訂單統計
        int todayOrders = orderRepository.countByTenantIdAndCreatedAtBetween(
                tenantId, today.atStartOfDay(), today.atTime(LocalTime.MAX));
        int yesterdayOrders = orderRepository.countByTenantIdAndCreatedAtBetween(
                tenantId, yesterday.atStartOfDay(), yesterday.atTime(LocalTime.MAX));
        int monthOrders = orderRepository.countByTenantIdAndCreatedAtBetween(
                tenantId, monthStart.atStartOfDay(), today.atTime(LocalTime.MAX));

        // 待處理訂單
        int pendingOrders = orderRepository.countByTenantIdAndStatus(tenantId, Order.OrderStatus.CREATED);

        // 活躍 listing 統計
        int activeProducts = productRepository.countByListingTenantIdAndListingStatus(
                tenantId, Listing.ListingStatus.ACTIVE);
        int totalRooms = roomRepository.countByListingTenantId(tenantId);

        return AnalyticsDto.DashboardStats.builder()
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .monthRevenue(monthRevenue)
                .yearRevenue(yearRevenue)
                .revenueGrowthPercent(revenueGrowthPercent)
                .todayOrders(todayOrders)
                .yesterdayOrders(yesterdayOrders)
                .monthOrders(monthOrders)
                .pendingOrders(pendingOrders)
                .activeListings(activeProducts)
                .totalRooms(totalRooms)
                .totalProducts(activeProducts)
                .build();
    }

    /**
     * 取得營收統計
     */
    @Transactional(readOnly = true)
    public AnalyticsDto.RevenueStats getRevenueStats(AnalyticsDto.AnalyticsRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now().minusDays(DEFAULT_PAGE_SIZE);
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();

        List<Order> orders = orderRepository.findByTenantIdAndCreatedAtBetween(
                tenantId, startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.PAID ||
                        o.getStatus() == Order.OrderStatus.CONFIRMED ||
                        o.getStatus() == Order.OrderStatus.SHIPPING ||
                        o.getStatus() == Order.OrderStatus.DELIVERED ||
                        o.getStatus() == Order.OrderStatus.COMPLETED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Payment> payments = paymentRepository.findByOrderIdInAndStatus(
                orders.stream().map(Order::getId).collect(Collectors.toList()),
                Payment.PaymentStatus.REFUNDED);
        BigDecimal totalRefunds = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = orders.size();
        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 依 granularity 分桶聚合營收（DAY/WEEK/MONTH，Sprint 65 修正：先前 granularity 參數為死碼，恆按日分組）
        List<AnalyticsDto.DailyRevenue> dailyData = buildRevenueBuckets(orders, startDate, endDate, request.getGranularity());

        return AnalyticsDto.RevenueStats.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .totalRefunds(totalRefunds)
                .netRevenue(totalRevenue.subtract(totalRefunds))
                .currency("TWD")
                .dailyRevenue(AnalyticsDto.RevenueByDay.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .data(dailyData)
                        .build())
                .categoryRevenue(AnalyticsDto.RevenueByCategory.builder()
                        .data(Collections.emptyList())
                        .build())
                .build();
    }

    /**
     * 取得訂單統計
     */
    @Transactional(readOnly = true)
    public AnalyticsDto.OrderStats getOrderStats() {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<Order> orders = orderRepository.findByTenantId(tenantId);

        Map<String, Integer> ordersByStatus = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getStatus().name(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return AnalyticsDto.OrderStats.builder()
                .totalOrders(orders.size())
                .pendingPayment(ordersByStatus.getOrDefault("CREATED", 0))
                .pendingShipment(ordersByStatus.getOrDefault("PAID", 0))
                .inTransit(ordersByStatus.getOrDefault("SHIPPING", 0))
                .delivered(ordersByStatus.getOrDefault("DELIVERED", 0))
                .completed(ordersByStatus.getOrDefault("COMPLETED", 0))
                .cancelled(ordersByStatus.getOrDefault("CANCELLED", 0))
                .refunded(ordersByStatus.getOrDefault("REFUNDED", 0))
                .ordersByStatus(ordersByStatus)
                .build();
    }

    /**
     * 取得 Listing 統計
     */
    @Transactional(readOnly = true)
    public AnalyticsDto.ListingStats getListingStats() {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<Listing> listings = listingRepository.findByTenantId(tenantId);

        int activeProducts = (int) listings.stream()
                .filter(l -> l.getListingType() == Listing.ListingType.PRODUCT)
                .filter(l -> l.getStatus() == Listing.ListingStatus.ACTIVE)
                .count();

        int inactiveProducts = (int) listings.stream()
                .filter(l -> l.getListingType() == Listing.ListingType.PRODUCT)
                .filter(l -> l.getStatus() != Listing.ListingStatus.ACTIVE)
                .count();

        int activeRooms = (int) listings.stream()
                .filter(l -> l.getListingType() == Listing.ListingType.ROOM)
                .filter(l -> l.getStatus() == Listing.ListingStatus.ACTIVE)
                .count();

        int inactiveRooms = (int) listings.stream()
                .filter(l -> l.getListingType() == Listing.ListingType.ROOM)
                .filter(l -> l.getStatus() != Listing.ListingStatus.ACTIVE)
                .count();

        return AnalyticsDto.ListingStats.builder()
                .totalListings(listings.size())
                .activeProducts(activeProducts)
                .inactiveProducts(inactiveProducts)
                .activeRooms(activeRooms)
                .inactiveRooms(inactiveRooms)
                .topProducts(Collections.emptyList())
                .topRooms(Collections.emptyList())
                .build();
    }

    /**
     * 取得最近活動
     */
    @Transactional(readOnly = true)
    public AnalyticsDto.RecentActivity getRecentActivity(int limit) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<Order> recentOrders = orderRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(0, limit));

        List<AnalyticsDto.ActivityItem> items = recentOrders.getContent().stream()
                .map(order -> AnalyticsDto.ActivityItem.builder()
                        .type("ORDER_" + order.getStatus().name())
                        .description("Order " + order.getStatus().name() + " - " + order.getTotalAmount() + " " + order.getCurrency())
                        .referenceId(order.getId())
                        .timestamp(order.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AnalyticsDto.RecentActivity.builder()
                .items(items)
                .totalCount((int) recentOrders.getTotalElements())
                .build();
    }

    // ========== Helper Methods ==========

    /**
     * 依 granularity（DAY/WEEK/MONTH，預設 DAY）將訂單分桶聚合為營收明細（Sprint 65 US-001）。
     * WEEK 以週一為桶起始、MONTH 以月初為桶起始；DailyRevenue.date 代表該桶的起始日。
     */
    private List<AnalyticsDto.DailyRevenue> buildRevenueBuckets(
            List<Order> orders, LocalDate startDate, LocalDate endDate, String granularity) {
        String g = granularity == null ? "DAY" : granularity.toUpperCase();

        List<AnalyticsDto.DailyRevenue> buckets = new ArrayList<>();
        for (LocalDate bucketStart = bucketStart(startDate, g);
                !bucketStart.isAfter(endDate);
                bucketStart = nextBucketStart(bucketStart, g)) {

            LocalDate bucketEnd = nextBucketStart(bucketStart, g).minusDays(1);
            final LocalDate rangeStart = bucketStart;
            final LocalDate rangeEnd = bucketEnd;

            List<Order> bucketOrders = orders.stream()
                    .filter(o -> {
                        LocalDate d = o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                        return !d.isBefore(rangeStart) && !d.isAfter(rangeEnd);
                    })
                    .collect(Collectors.toList());

            BigDecimal bucketRevenue = bucketOrders.stream()
                    .filter(o -> o.getStatus() != Order.OrderStatus.CANCELLED &&
                            o.getStatus() != Order.OrderStatus.REFUNDED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            buckets.add(AnalyticsDto.DailyRevenue.builder()
                    .date(bucketStart)
                    .revenue(bucketRevenue)
                    .orderCount(bucketOrders.size())
                    .build());
        }
        return buckets;
    }

    private LocalDate bucketStart(LocalDate date, String granularity) {
        if ("WEEK".equals(granularity)) {
            return date.with(DayOfWeek.MONDAY);
        }
        if ("MONTH".equals(granularity)) {
            return date.withDayOfMonth(1);
        }
        return date;
    }

    private LocalDate nextBucketStart(LocalDate bucketStart, String granularity) {
        if ("WEEK".equals(granularity)) {
            return bucketStart.plusWeeks(1);
        }
        if ("MONTH".equals(granularity)) {
            return bucketStart.plusMonths(1);
        }
        return bucketStart.plusDays(1);
    }

    private BigDecimal calculateRevenueForDate(UUID tenantId, LocalDate date) {
        List<Order> orders = orderRepository.findByTenantIdAndCreatedAtBetween(
                tenantId, date.atStartOfDay(), date.atTime(LocalTime.MAX));

        return orders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELLED &&
                        o.getStatus() != Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateRevenueForDateRange(UUID tenantId, LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByTenantIdAndCreatedAtBetween(
                tenantId, startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));

        return orders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELLED &&
                        o.getStatus() != Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
