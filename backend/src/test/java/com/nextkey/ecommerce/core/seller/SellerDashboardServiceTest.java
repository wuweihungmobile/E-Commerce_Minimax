package com.nextkey.ecommerce.core.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

/**
 * SellerDashboardService 功能單元測試（Sprint 61 US-002）
 *
 * 測試範圍（不涉及 @Cacheable 行為，快取行為已由 SellerDashboardServiceCacheTest 覆蓋）：
 * - getDashboard() 統計欄位計算正確性
 * - 無訂單/無上架品項時的邊界情況
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerDashboardService: 商家儀表板統計")
class SellerDashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private SellerDashboardService sellerDashboardService;

    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

    private static final List<Order.OrderStatus> PENDING_STATUSES = List.of(
            Order.OrderStatus.CREATED,
            Order.OrderStatus.PAID,
            Order.OrderStatus.CONFIRMED);

    @Test
    @DisplayName("TC-DASH-001: 有訂單與上架品項時，各統計欄位回傳正確數值")
    void getDashboard_withData_returnsCorrectStats() {
        Instant lastOrderAt = Instant.parse("2026-06-01T00:00:00Z");
        Order latestOrder = Order.builder().createdAt(lastOrderAt).build();

        when(orderRepository.countByTenantIdAndCreatedAtAfter(eq(TEST_TENANT_ID), any()))
                .thenReturn(5L, 20L); // 第一次呼叫 = 7d 視窗，第二次 = 30d 視窗
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                eq(TEST_TENANT_ID), eq(Order.OrderStatus.COMPLETED), any()))
                .thenReturn(BigDecimal.valueOf(123456));
        when(listingRepository.countByTenantIdAndStatus(eq(TEST_TENANT_ID), eq(Listing.ListingStatus.ACTIVE)))
                .thenReturn(8L);
        when(orderRepository.countByTenantIdAndStatusIn(eq(TEST_TENANT_ID), anyList()))
                .thenReturn(3L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(eq(TEST_TENANT_ID), any(Pageable.class)))
                .thenReturn(List.of(latestOrder));

        SellerDashboardDto.DashboardResponse result = sellerDashboardService.getDashboard(TEST_TENANT_ID);

        assertThat(result.getOrderCount7d()).isEqualTo(5L);
        assertThat(result.getOrderCount30d()).isEqualTo(20L);
        assertThat(result.getRevenue30d()).isEqualByComparingTo(BigDecimal.valueOf(123456));
        assertThat(result.getActiveListingCount()).isEqualTo(8L);
        assertThat(result.getPendingOrderCount()).isEqualTo(3L);
        assertThat(result.getLastOrderAt()).isEqualTo(lastOrderAt);
    }

    @Test
    @DisplayName("TC-DASH-002: 無任何訂單/上架品項時，統計欄位回傳預設值且 lastOrderAt 為 null")
    void getDashboard_noOrdersOrListings_returnsZerosAndNullLastOrder() {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(eq(TEST_TENANT_ID), any()))
                .thenReturn(0L);
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                eq(TEST_TENANT_ID), eq(Order.OrderStatus.COMPLETED), any()))
                .thenReturn(BigDecimal.ZERO);
        when(listingRepository.countByTenantIdAndStatus(eq(TEST_TENANT_ID), eq(Listing.ListingStatus.ACTIVE)))
                .thenReturn(0L);
        when(orderRepository.countByTenantIdAndStatusIn(eq(TEST_TENANT_ID), anyList()))
                .thenReturn(0L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(eq(TEST_TENANT_ID), any(Pageable.class)))
                .thenReturn(List.of());

        SellerDashboardDto.DashboardResponse result = sellerDashboardService.getDashboard(TEST_TENANT_ID);

        assertThat(result.getOrderCount7d()).isZero();
        assertThat(result.getOrderCount30d()).isZero();
        assertThat(result.getRevenue30d()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getActiveListingCount()).isZero();
        assertThat(result.getPendingOrderCount()).isZero();
        assertThat(result.getLastOrderAt()).isNull();
    }

    @Test
    @DisplayName("TC-DASH-003: getDashboard() 以 pending 狀態清單（CREATED/PAID/CONFIRMED）查詢待處理訂單數")
    void getDashboard_queriesPendingOrdersWithExpectedStatuses() {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(eq(TEST_TENANT_ID), any())).thenReturn(0L);
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                eq(TEST_TENANT_ID), eq(Order.OrderStatus.COMPLETED), any())).thenReturn(BigDecimal.ZERO);
        when(listingRepository.countByTenantIdAndStatus(eq(TEST_TENANT_ID), eq(Listing.ListingStatus.ACTIVE)))
                .thenReturn(0L);
        when(orderRepository.countByTenantIdAndStatusIn(eq(TEST_TENANT_ID), eq(PENDING_STATUSES)))
                .thenReturn(7L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(eq(TEST_TENANT_ID), any(Pageable.class)))
                .thenReturn(List.of());

        SellerDashboardDto.DashboardResponse result = sellerDashboardService.getDashboard(TEST_TENANT_ID);

        assertThat(result.getPendingOrderCount()).isEqualTo(7L);
    }
}
