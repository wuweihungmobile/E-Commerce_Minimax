package com.nextkey.ecommerce.core.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

/**
 * dashboardStats cache 對真實 Redis 的序列化。
 *
 * <p>{@code ratingStats} 在 Sprint 285（DEF-390）補了
 * {@link com.nextkey.ecommerce.core.review.ReviewRatingStatsRealRedisIntegrationTest}，但全後端只有兩個
 * {@code @Cacheable}，另一個 {@code dashboardStats} 一直沒有對真實 Redis 的驗證：
 * {@link SellerDashboardServiceCacheTest} 用的是 {@code spring.cache.type=simple}（記憶體 Map，完全不序列化），
 * 而且它把「最新訂單」stub 成 {@code List.of()}，{@code lastOrderAt} 永遠是 null——所以 {@code Instant}、
 * {@code BigDecimal} 兩種欄位型別在 {@code GenericJackson2JsonRedisSerializer}（{@code RedisConfig#buildObjectMapper}：
 * JavaTimeModule＋NON_FINAL 預設型別標記）下能不能寫進去、讀回來，從來沒有任何測試執行過。
 *
 * <p>{@code application-integration-test.yml} 全域把 {@code spring.cache.type} 設為 {@code simple}，這裡用
 * {@code @TestPropertySource} 覆寫回 {@code redis}，並明確斷言 cache manager 真的是 {@link RedisCacheManager}
 * ——覆寫若悄悄失效，本測試會變成在測記憶體 Map 而仍然全綠。
 */
@Tag("slow") // @SpringBootTest 需真實 DB/Redis → pre-commit quick test 排除，pre-push act 仍完整跑
@SpringBootTest
@ActiveProfiles("integration-test")
@TestPropertySource(properties = "spring.cache.type=redis")
@DisplayName("IT-DASH-REDIS: dashboardStats 對真實 Redis 的序列化")
class SellerDashboardRealRedisIntegrationTest {

    @Autowired private SellerDashboardService sellerDashboardService;
    @Autowired private CacheManager cacheManager;

    // 只在「填滿欄位」案例 stub 回傳值；空店鋪案例走真實 DB 查詢（隨機 tenantId 不會有任何資料）
    @SpyBean private OrderRepository orderRepository;
    @SpyBean private ListingRepository listingRepository;

    private Cache dashboardStatsCache(UUID tenantId) {
        assertThat(cacheManager).as("必須是 RedisCacheManager，否則是在測記憶體 Map").isInstanceOf(RedisCacheManager.class);
        Cache cache = cacheManager.getCache("dashboardStats");
        assertThat(cache).as("dashboardStats cache 必須存在").isNotNull();
        cache.evict(tenantId);
        return cache;
    }

    @Test
    @DisplayName("欄位全部有值（long／BigDecimal／Instant）→ 寫入真實 Redis 後讀回，每個欄位都一致，且第二次沒有重算")
    void getDashboard_populatedDto_roundTripsThroughRealRedis() {
        UUID tenantId = UUID.randomUUID();
        Cache cache = dashboardStatsCache(tenantId);

        Instant lastOrderAt = Instant.parse("2026-10-20T08:15:30.123456789Z"); // 帶奈秒，驗證 Instant 精度不被截斷
        Order latestOrder = mock(Order.class);
        doReturn(lastOrderAt).when(latestOrder).getCreatedAt();

        doReturn(5L).when(orderRepository).countByTenantIdAndCreatedAtAfter(eq(tenantId), any());
        doReturn(new BigDecimal("50000.75")).when(orderRepository)
                .sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                        eq(tenantId), eq(Order.OrderStatus.COMPLETED), any());
        doReturn(3L).when(listingRepository).countByTenantIdAndStatus(eq(tenantId), eq(Listing.ListingStatus.ACTIVE));
        doReturn(2L).when(orderRepository).countByTenantIdAndStatusIn(eq(tenantId), anyList());
        doReturn(List.of(latestOrder)).when(orderRepository)
                .findTopByTenantIdOrderByCreatedAtDesc(eq(tenantId), any(Pageable.class));

        // 第一次呼叫：真正計算並寫入 Redis
        SellerDashboardDto.DashboardResponse first = sellerDashboardService.getDashboard(tenantId);
        assertThat(first.getRevenue30d()).isEqualByComparingTo("50000.75");
        assertThat(first.getLastOrderAt()).isEqualTo(lastOrderAt);

        // 第二次呼叫：必須從 Redis 反序列化讀回，不是退化成每次重算而恰好不出錯
        SellerDashboardDto.DashboardResponse second = sellerDashboardService.getDashboard(tenantId);

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(second.getRevenue30d()).as("BigDecimal 的值與小數位數不得在 JSON 來回中改變").isEqualTo(new BigDecimal("50000.75"));
        assertThat(second.getLastOrderAt()).as("Instant 的奈秒精度不得被截斷").isEqualTo(lastOrderAt);
        verify(listingRepository, times(1)).countByTenantIdAndStatus(eq(tenantId), eq(Listing.ListingStatus.ACTIVE));
        assertThat(cache.get(tenantId)).as("快取裡應有這個 tenantId 的項目").isNotNull();
    }

    @Test
    @DisplayName("新店鋪（沒有任何訂單與商品，真實 DB 查詢）→ 含 null 欄位的結果也能寫入並從 Redis 讀回")
    void getDashboard_emptyTenant_roundTripsThroughRealRedis() {
        UUID tenantId = UUID.randomUUID();
        Cache cache = dashboardStatsCache(tenantId);

        SellerDashboardDto.DashboardResponse first = sellerDashboardService.getDashboard(tenantId);
        assertThat(first.getOrderCount7d()).isZero();
        assertThat(first.getLastOrderAt()).as("沒有任何訂單時為 null").isNull();

        SellerDashboardDto.DashboardResponse second = sellerDashboardService.getDashboard(tenantId);

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(cache.get(tenantId)).as("第二次呼叫後，快取裡應有這個 tenantId 的項目").isNotNull();
    }
}
