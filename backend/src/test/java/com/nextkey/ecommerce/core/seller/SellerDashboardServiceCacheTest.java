package com.nextkey.ecommerce.core.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

/**
 * SellerDashboardService @Cacheable 整合測試（Sprint 23 US-006 / AI-703）
 *
 * 測試範圍：
 * - TC-DASH-C001: getDashboard() 第一次呼叫 → Repository 被呼叫
 * - TC-DASH-C002: getDashboard() 第二次呼叫（相同 tenantId）→ 快取命中，Repository 不呼叫
 * - TC-DASH-C003: 手動清除快取後再呼叫 → Repository 再次被呼叫
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = "spring.cache.type=simple")
@DisplayName("IT-US006: SellerDashboardService @Cacheable 快取行為測試")
class SellerDashboardServiceCacheTest {

    @Autowired
    private SellerDashboardService sellerDashboardService;

    @SpyBean
    private OrderRepository orderRepository;

    @SpyBean
    private ListingRepository listingRepository;

    @Autowired
    private CacheManager cacheManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        // 清除快取，確保每個測試從乾淨狀態開始
        Cache cache = cacheManager.getCache("dashboardStats");
        if (cache != null) {
            cache.evict(tenantId);
        }

        // Stub Repository 回傳固定值，避免依賴真實 DB 資料
        when(orderRepository.countByTenantIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(Order.OrderStatus.COMPLETED),
                org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.valueOf(50000));
        when(listingRepository.countByTenantIdAndStatus(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(Listing.ListingStatus.ACTIVE))).thenReturn(3L);
        when(orderRepository.countByTenantIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(2L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(List.of());
    }

    @Test
    @DisplayName("TC-DASH-C001: 第一次呼叫 getDashboard() → Repository 被呼叫")
    void getDashboard_firstCall_hitsRepository() {
        SellerDashboardDto.DashboardResponse result = sellerDashboardService.getDashboard(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.getActiveListingCount()).isEqualTo(3L);
        assertThat(result.getPendingOrderCount()).isEqualTo(2L);

        verify(orderRepository, times(2)).countByTenantIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("TC-DASH-C002: 第二次呼叫 getDashboard()（相同 tenantId）→ 快取命中，Repository 不呼叫")
    void getDashboard_secondCall_hitsCacheNotRepository() {
        sellerDashboardService.getDashboard(tenantId);   // 第一次：呼叫 Repository
        sellerDashboardService.getDashboard(tenantId);   // 第二次：應命中快取

        // countByTenantIdAndCreatedAtAfter 在整個測試只被呼叫兩次（7d + 30d，第一次呼叫）
        verify(orderRepository, times(2)).countByTenantIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any());
        verify(listingRepository, times(1)).countByTenantIdAndStatus(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("TC-DASH-C003: 手動清除快取後再呼叫 getDashboard() → Repository 再次被呼叫")
    void getDashboard_afterCacheEvict_hitsRepositoryAgain() {
        sellerDashboardService.getDashboard(tenantId);  // 第一次：填充快取

        // 手動清除快取
        Cache cache = cacheManager.getCache("dashboardStats");
        assertThat(cache).isNotNull();
        cache.evict(tenantId);

        sellerDashboardService.getDashboard(tenantId);  // 快取已清除，再次呼叫 Repository

        // 7d + 30d = 2 次 × 2 次呼叫 = 共 4 次
        verify(orderRepository, times(4)).countByTenantIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any());
        verify(listingRepository, times(2)).countByTenantIdAndStatus(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any());
    }
}
