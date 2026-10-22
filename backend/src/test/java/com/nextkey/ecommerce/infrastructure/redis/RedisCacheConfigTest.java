package com.nextkey.ecommerce.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ClassUtils;

import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.review.ReviewService;

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

    // ========== TC-CACHE-R: ratingStats TTL 與 @CacheEvict（Sprint 285，DEF-390）==========

    @Test
    @DisplayName("TC-CACHE-R001: ratingStats cache TTL 為 5 分鐘")
    void ratingStats_ttlShouldBeFiveMinutes() {
        assertThat(RedisConfig.RATING_STATS_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("TC-CACHE-R002: createReview 有 @CacheEvict(ratingStats)")
    void createReview_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = ReviewService.class.getMethod("createReview", ReviewDto.CreateRequest.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("ratingStats");
    }

    @Test
    @DisplayName("TC-CACHE-R003: updateReview 有 @CacheEvict(ratingStats, allEntries=true)")
    void updateReview_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = ReviewService.class.getMethod("updateReview", UUID.class, ReviewDto.UpdateRequest.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("ratingStats");
        assertThat(evict.allEntries()).isTrue();
    }

    @Test
    @DisplayName("TC-CACHE-R004: deleteReview 有 @CacheEvict(ratingStats, allEntries=true)")
    void deleteReview_shouldHaveCacheEvictAnnotation() throws NoSuchMethodException {
        Method method = ReviewService.class.getMethod("deleteReview", UUID.class);
        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertThat(evict).isNotNull();
        assertThat(evict.value()).contains("ratingStats");
        assertThat(evict.allEntries()).isTrue();
    }

    // ========== TC-CACHE-G: 每個快取名稱都必須在 RedisConfig 註冊專屬設定（DEF-390 同類缺陷的守門）==========

    /**
     * 新增 {@code @Cacheable}／{@code @CachePut} 卻沒在 {@code RedisConfig#redisCacheManagerBuilderCustomizer} 註冊該 cache name，
     * 會落回 Spring Boot 預設的 {@code JdkSerializationRedisSerializer}；回傳的 DTO 若沒有 {@code implements Serializable}，
     * 每次寫入快取都拋 {@code SerializationException}，端點在生產環境一律 500。整合測試全域把 {@code spring.cache.type}
     * 設為 {@code simple}（不序列化），所以任何整合測試都看不到——Sprint 285 的 DEF-390 就是這樣潛伏到真實 Redis 走查才現形。
     * 這條規則原本只存在於人的記憶裡，這裡改成機械檢查。
     *
     * <p>只涵蓋直接標在方法上的 {@code @Cacheable}／{@code @CachePut}（含合成註解）；{@code @CacheEvict} 不寫值、不需要序列化器。
     */
    @Test
    @DisplayName("TC-CACHE-G001: 每個 @Cacheable/@CachePut 用到的 cache name，RedisConfig 都有註冊專屬設定")
    void everyCacheableName_isRegisteredInRedisConfig() {
        Set<String> used = cacheNamesUsedByCacheableOrCachePut();

        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(mock(RedisConnectionFactory.class));
        new RedisConfig().redisCacheManagerBuilderCustomizer().customize(builder);

        assertThat(used).as("掃不到任何 @Cacheable/@CachePut——掃描方式失效，這個守門會恆真").isNotEmpty();
        Set<String> unregistered = new TreeSet<>(used);
        unregistered.removeAll(builder.getConfiguredCaches());
        assertThat(unregistered)
                .as("這些 cache name 沒有在 RedisConfig#redisCacheManagerBuilderCustomizer 註冊專屬設定，會落回 JDK 序列化"
                        + "（DTO 沒 implements Serializable 就在生產環境 500，見 DEF-390）；請比照 dashboardStats／ratingStats 註冊")
                .isEmpty();
    }

    private static Set<String> cacheNamesUsedByCacheableOrCachePut() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true); // 預設只收 @Component；快取註解可能出現在任何類別
        Set<String> names = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.nextkey.ecommerce")) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
            for (Method method : type.getDeclaredMethods()) {
                Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
                CachePut cachePut = AnnotatedElementUtils.findMergedAnnotation(method, CachePut.class);
                String[] declared = cacheable != null ? cacheable.cacheNames()
                        : cachePut != null ? cachePut.cacheNames() : null;
                if (declared == null) {
                    continue;
                }
                assertThat(declared)
                        .as("%s#%s 的快取註解沒有明確寫 cacheNames（可能靠 @CacheConfig 繼承）——請明確寫出，或擴充本守門",
                                type.getSimpleName(), method.getName())
                        .isNotEmpty();
                names.addAll(Arrays.asList(declared));
            }
        }
        return names;
    }
}
