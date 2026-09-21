package com.nextkey.ecommerce.integration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.controller.ProductController;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.product.ProductService;
import com.nextkey.ecommerce.core.product.ProductSkuService;
import com.nextkey.ecommerce.core.review.ReviewService;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * M08 評分統計端點測試（Sprint 20 US-006）
 *
 * 測試範圍：
 * - IT-M08S-001: GET /v2/products/{id}/reviews/stats — 有評分時回傳正確統計
 * - IT-M08S-002: GET /v2/products/{id}/reviews/stats — 無評分時 averageRating 為 null
 * - IT-M08S-003: GET /v2/products/{id}/reviews/stats — 未授權應回傳 401
 */
@WebMvcTest(controllers = {ProductController.class})
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-M08S: M08 評分統計端點測試")
class M08ReviewStatsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ReviewService reviewService;

    // Sprint 178：ProductController 新增 SKU 管理端點後多了這個建構子相依，此為 @WebMvcTest
    // 只載入 ProductController 的窄範圍 slice context，未宣告的相依無法自動注入
    @MockBean
    private ProductSkuService productSkuService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String STATS_URL = "/v2/products/" + PRODUCT_ID + "/reviews/stats";

    @Test
    @DisplayName("IT-M08S-001: 有評分時回傳正確統計資料")
    void testGetReviewStats_withReviews() throws Exception {
        Map<Integer, Integer> distribution = new HashMap<>();
        distribution.put(1, 0);
        distribution.put(2, 1);
        distribution.put(3, 2);
        distribution.put(4, 5);
        distribution.put(5, 10);

        ReviewDto.RatingStats stats = ReviewDto.RatingStats.builder()
                .listingId(PRODUCT_ID)
                .averageRating(4.39)
                .totalReviews(18)
                .rating1Count(0)
                .rating2Count(1)
                .rating3Count(2)
                .rating4Count(5)
                .rating5Count(10)
                .distribution(distribution)
                .build();

        when(reviewService.getRatingStats(PRODUCT_ID)).thenReturn(stats);

        mockMvc.perform(get(STATS_URL)
                        .with(user("testUser").roles("USER").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.39))
                .andExpect(jsonPath("$.data.totalReviews").value(18))
                .andExpect(jsonPath("$.data.rating5Count").value(10))
                .andExpect(jsonPath("$.data.rating1Count").value(0))
                .andExpect(jsonPath("$.data.distribution").isMap());
    }

    @Test
    @DisplayName("IT-M08S-002: 無評分時 averageRating 為 null，totalReviews 為 0")
    void testGetReviewStats_noReviews() throws Exception {
        Map<Integer, Integer> emptyDistribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) emptyDistribution.put(i, 0);

        ReviewDto.RatingStats emptyStats = ReviewDto.RatingStats.builder()
                .listingId(PRODUCT_ID)
                .averageRating(null)
                .totalReviews(0)
                .rating1Count(0)
                .rating2Count(0)
                .rating3Count(0)
                .rating4Count(0)
                .rating5Count(0)
                .distribution(emptyDistribution)
                .build();

        when(reviewService.getRatingStats(PRODUCT_ID)).thenReturn(emptyStats);

        mockMvc.perform(get(STATS_URL)
                        .with(user("testUser").roles("USER").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").doesNotExist())
                .andExpect(jsonPath("$.data.totalReviews").value(0))
                .andExpect(jsonPath("$.data.rating5Count").value(0));
    }

    @Test
    @DisplayName("IT-M08S-003: 未授權時回傳 401")
    void testGetReviewStats_unauthorized() throws Exception {
        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isUnauthorized());
    }
}
