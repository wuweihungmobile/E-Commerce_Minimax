package com.nextkey.ecommerce.core.seller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerDashboardService {

    private static final long WINDOW_7D = 7L;
    private static final long WINDOW_30D = 30L;

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public SellerDashboardDto.DashboardResponse getDashboard(UUID tenantId) {
        log.info("Getting seller dashboard for tenant: {}", tenantId);

        Instant now = Instant.now();
        Instant since7d = now.minus(WINDOW_7D, ChronoUnit.DAYS);
        Instant since30d = now.minus(WINDOW_30D, ChronoUnit.DAYS);

        long orderCount7d = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, since7d);
        long orderCount30d = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, since30d);

        BigDecimal revenue30d = orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                tenantId, Order.OrderStatus.COMPLETED, since30d);

        long activeListingCount = listingRepository.countByTenantIdAndStatus(
                tenantId, Listing.ListingStatus.ACTIVE);

        List<Order.OrderStatus> pendingStatuses = List.of(
                Order.OrderStatus.CREATED,
                Order.OrderStatus.PAID,
                Order.OrderStatus.CONFIRMED);
        long pendingOrderCount = orderRepository.countByTenantIdAndStatusIn(tenantId, pendingStatuses);

        List<Order> latestOrders = orderRepository.findTopByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(0, 1));
        Instant lastOrderAt = latestOrders.isEmpty() ? null : latestOrders.get(0).getCreatedAt();

        return SellerDashboardDto.DashboardResponse.builder()
                .orderCount7d(orderCount7d)
                .orderCount30d(orderCount30d)
                .revenue30d(revenue30d)
                .activeListingCount(activeListingCount)
                .pendingOrderCount(pendingOrderCount)
                .lastOrderAt(lastOrderAt)
                .build();
    }
}
