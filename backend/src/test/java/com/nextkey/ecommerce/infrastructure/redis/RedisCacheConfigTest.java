package com.nextkey.ecommerce.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;

import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.order.OrderService;

/**
 * TC-CACHE-D001~D004: dashboardStats Redis TTL 與 @CacheEvict 驗證
 *
 * <p>TC-CACHE-D001: dashboardStats cache TTL = 5 分鐘
 * <p>TC-CACHE-D002: createOrderFromCart 標記 @CacheEvict(dashboardStats)
 * <p>TC-CACHE-D003: updateOrderStatus 標記 @CacheEvict(dashboardStats)
 * <p>TC-CACHE-D004: cancelOrder 標記 @CacheEvict(dashboardStats)
 */
@DisplayName("TC-CACHE-D: dashboardStats Redis TTL + @CacheEvict 驗證")
class RedisCacheConfigTest {

    @Test
    @DisplayName("TC-CACHE-D001: dashboardStats cache TTL 為 5 分鐘")
    void dashboardStats_ttlShouldBeFiveMinutes() {
        assertThat(RedisConfig.DASHBOARD_STATS_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("TC-CACHE-D002: createOrderFromCart 有 @CacheEvict(dashboardStats)")
    void createOrderFromCart_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = OrderService.class.getMethod("createOrderFromCart", OrderDto.CreateRequest.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("dashboardStats");
        assertThat(evict.allEntries()).isTrue();
    }

    @Test
    @DisplayName("TC-CACHE-D003: updateOrderStatus 有 @CacheEvict(dashboardStats)")
    void updateOrderStatus_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = OrderService.class.getMethod("updateOrderStatus", UUID.class, String.class, String.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("dashboardStats");
        assertThat(evict.allEntries()).isTrue();
    }

    @Test
    @DisplayName("TC-CACHE-D004: cancelOrder 有 @CacheEvict(dashboardStats)")
    void cancelOrder_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = OrderService.class.getMethod("cancelOrder", UUID.class, String.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("dashboardStats");
        assertThat(evict.allEntries()).isTrue();
    }
}
