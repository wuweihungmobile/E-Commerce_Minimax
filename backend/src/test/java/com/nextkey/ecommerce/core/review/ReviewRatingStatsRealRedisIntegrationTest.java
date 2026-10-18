package com.nextkey.ecommerce.core.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.nextkey.ecommerce.api.dto.ReviewDto;

/**
 * ratingStats cache 對真實 Redis 的序列化（Sprint 285，DEF-390）。
 *
 * <p>{@code ReviewService.getRatingStats} 的 {@code @Cacheable(value = "ratingStats", ...)} 原本沒有在
 * {@code RedisConfig} 註冊專屬的 cache configuration，落回 Spring Boot 預設的
 * {@code JdkSerializationRedisSerializer}；{@code ReviewDto.RatingStats}（{@code @Data @Builder}）沒有
 * {@code implements Serializable}，導致每一次呼叫都在「寫入快取」這一步拋
 * {@code SerializationException}，端點對任何商品/房源一律回 500（{@code E-9900}）。
 *
 * <p>{@code application-integration-test.yml} 全域把 {@code spring.cache.type} 設為 {@code simple}（供其他測試用
 * 記憶體 Map 驗證快取「次數」行為），所以這裡必須用 {@code @TestPropertySource} 明確覆寫回 {@code redis}，
 * 否則就會像 {@link ReviewServiceCacheIntegrationTest} 一樣完全不經過真實 Redis 序列化，看不到這個問題
 * ——這正是 285 輪 Sprint 都沒抓到這個 bug 的根本原因：唯一會驗證快取行為的既有測試用的是 simple 模式。
 */
@Tag("slow") // @SpringBootTest 需真實 DB/Redis → pre-commit quick test 排除，pre-push act 仍完整跑
@SpringBootTest
@ActiveProfiles("integration-test")
@TestPropertySource(properties = "spring.cache.type=redis")
@DisplayName("IT-REVIEW-CACHE-REDIS: ratingStats 對真實 Redis 的序列化（Sprint 285，DEF-390）")
class ReviewRatingStatsRealRedisIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private CacheManager cacheManager;

    @Test
    @DisplayName("getRatingStats 對真實 Redis 寫入快取不拋例外，且能從快取正確讀回（修復前：SerializationException → 500）")
    void getRatingStats_roundTripsThroughRealRedis() {
        UUID listingId = UUID.randomUUID();
        Cache cache = cacheManager.getCache("ratingStats");
        assertThat(cache).as("ratingStats cache 必須存在").isNotNull();
        cache.evict(listingId);

        // 第一次呼叫：真正計算並寫入 Redis（修復前在這裡就會拋 SerializationException → 500）
        ReviewDto.RatingStats first = reviewService.getRatingStats(listingId);
        assertThat(first.getListingId()).isEqualTo(listingId);
        assertThat(first.getTotalReviews()).isEqualTo(0);
        assertThat(first.getAverageRating()).isNull();

        // 第二次呼叫：應從 Redis 讀回（反序列化），不是退化成每次都重算而恰好不出錯
        ReviewDto.RatingStats second = reviewService.getRatingStats(listingId);
        assertThat(second.getListingId()).isEqualTo(listingId);
        assertThat(second.getTotalReviews()).isEqualTo(first.getTotalReviews());

        assertThat(cache.get(listingId)).as("第二次呼叫後，快取裡應有這個 listingId 的項目").isNotNull();
    }
}
