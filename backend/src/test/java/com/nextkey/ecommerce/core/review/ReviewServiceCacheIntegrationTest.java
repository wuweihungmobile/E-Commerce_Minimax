package com.nextkey.ecommerce.core.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

/**
 * ReviewService @Cacheable 整合測試（US-004）
 *
 * 使用 spring.cache.type=simple（in-memory ConcurrentMap），不依賴 Redis。
 * 透過 @SpyBean ReviewRepository 驗證 Repository 呼叫次數以確認快取行為。
 *
 * 測試範圍：
 * - TC-R001: getRatingStats() 第一次呼叫 → Repository 被呼叫
 * - TC-R002: getRatingStats() 第二次呼叫（相同 listingId）→ 快取命中，Repository 不呼叫
 * - TC-R003: 快取清除後 getRatingStats() → Repository 再次被呼叫
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = "spring.cache.type=simple")
@DisplayName("IT-US004: ReviewService @Cacheable 快取行為測試")
class ReviewServiceCacheIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @SpyBean
    private ReviewRepository reviewRepository;

    @Autowired
    private CacheManager cacheManager;

    private UUID listingId;

    @BeforeEach
    void setUp() {
        listingId = UUID.randomUUID();

        // 清除快取，確保每個測試從乾淨狀態開始
        Cache cache = cacheManager.getCache("ratingStats");
        if (cache != null) {
            cache.evict(listingId);
        }

        // Stub Repository 回傳固定值，避免依賴真實 DB 資料
        doReturn(4.5).when(reviewRepository).getAverageRatingByListingId(listingId);
        doReturn(10).when(reviewRepository).countByListingId(listingId);
        doReturn(List.of()).when(reviewRepository).getRatingDistribution(listingId);
    }

    @Test
    @DisplayName("TC-R001: 第一次呼叫 getRatingStats() → Repository 被呼叫一次")
    void getRatingStats_firstCall_hitsRepository() {
        ReviewDto.RatingStats result = reviewService.getRatingStats(listingId);

        assertThat(result).isNotNull();
        assertThat(result.getListingId()).isEqualTo(listingId);
        assertThat(result.getAverageRating()).isEqualTo(4.5);
        assertThat(result.getTotalReviews()).isEqualTo(10);

        verify(reviewRepository, times(1)).getAverageRatingByListingId(listingId);
    }

    @Test
    @DisplayName("TC-R002: 第二次呼叫 getRatingStats()（相同 listingId）→ 快取命中，Repository 不呼叫")
    void getRatingStats_secondCall_hitsCacheNotRepository() {
        reviewService.getRatingStats(listingId);   // 第一次：呼叫 Repository
        reviewService.getRatingStats(listingId);   // 第二次：應命中快取

        // Repository 在整個測試只被呼叫一次（第二次是快取命中）
        verify(reviewRepository, times(1)).getAverageRatingByListingId(listingId);
        verify(reviewRepository, times(1)).countByListingId(listingId);
    }

    @Test
    @DisplayName("TC-R003: 快取清除後再呼叫 getRatingStats() → Repository 再次被呼叫")
    void getRatingStats_afterCacheEvict_hitsRepositoryAgain() {
        reviewService.getRatingStats(listingId);  // 第一次：填充快取

        // 手動清除快取（模擬 @CacheEvict 行為）
        Cache cache = cacheManager.getCache("ratingStats");
        assertThat(cache).isNotNull();
        cache.evict(listingId);

        reviewService.getRatingStats(listingId);  // 快取已清除，再次呼叫 Repository

        verify(reviewRepository, times(2)).getAverageRatingByListingId(listingId);
    }
}
