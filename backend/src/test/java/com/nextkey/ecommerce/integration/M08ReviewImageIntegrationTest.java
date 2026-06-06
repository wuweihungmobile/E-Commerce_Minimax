package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.api.controller.ReviewController;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.media.MediaService;
import com.nextkey.ecommerce.core.review.BookingReviewService;
import com.nextkey.ecommerce.core.review.ReviewReplyService;
import com.nextkey.ecommerce.core.review.ReviewService;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M08 評價多圖管理整合測試
 *
 * 測試範圍（Sprint 16 US-005 + US-006）：
 * - POST /v2/reviews/{id}/images - 新增單張圖片
 * - DELETE /v2/reviews/{id}/images/{imageIndex} - 刪除圖片
 * - PUT /v2/reviews/{id}/images/order - 重新排序
 * - 邊界：9 張上限、非本人操作 403
 */
@WebMvcTest(controllers = ReviewController.class)
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("M08 評價多圖管理整合測試 (Sprint 16 US-005 + US-006)")
class M08ReviewImageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewReplyService reviewReplyService;

    @MockBean
    private BookingReviewService bookingReviewService;

    @MockBean
    private ReviewRepository reviewRepository;

    @MockBean
    private BookingReviewRepository bookingReviewRepository;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private MediaService mediaService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private RolePermissionMapping rolePermissionMapping;

    private UUID reviewId;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        reviewId = UUID.randomUUID();
        listingId = UUID.randomUUID();
    }

    // ========== US-005: 9 張上限測試 ==========

    @Test
    @DisplayName("AC-001/002: POST 9 張圖片成功")
    @WithMockUser(authorities = {"order:create"})
    void createReview_with9Images_success() throws Exception {
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .listingId(listingId)
                .rating(5)
                .title("Great!")
                .content("Loved it")
                .images(List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9"))
                .createdAt(Instant.now())
                .build();

        when(reviewService.createReview(any(ReviewDto.CreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/v2/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"" + listingId + "\",\"rating\":5,\"title\":\"Great!\","
                                + "\"content\":\"Loved it\","
                                + "\"images\":[\"m1\",\"m2\",\"m3\",\"m4\",\"m5\",\"m6\",\"m7\",\"m8\",\"m9\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images.length()").value(9));

        verify(reviewService).createReview(any(ReviewDto.CreateRequest.class));
    }

    @Test
    @DisplayName("AC-002: POST 10 張圖片 - @Size 驗證失敗 400")
    @WithMockUser(authorities = {"order:create"})
    void createReview_with10Images_returns400() throws Exception {
        // 10 張圖片 - 會被 @Size(max = 9) 擋下
        mockMvc.perform(post("/v2/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"" + listingId + "\",\"rating\":5,\"title\":\"Great!\","
                                + "\"content\":\"Loved it\","
                                + "\"images\":[\"m1\",\"m2\",\"m3\",\"m4\",\"m5\",\"m6\",\"m7\",\"m8\",\"m9\",\"m10\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ========== US-006: 圖片管理 API ==========

    @Test
    @DisplayName("AC-101: POST /v2/reviews/{id}/images - 新增單張圖片成功")
    @WithMockUser(authorities = {"order:read"})
    void addImage_success() throws Exception {
        String newImageUrl = "media-new-uuid";
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .images(List.of("m1", "m2", "m3", newImageUrl))
                .build();

        when(reviewService.addImage(eq(reviewId), eq(newImageUrl))).thenReturn(response);

        mockMvc.perform(post("/v2/reviews/{id}/images", reviewId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"" + newImageUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images.length()").value(4));

        verify(reviewService).addImage(eq(reviewId), eq(newImageUrl));
    }

    @Test
    @DisplayName("AC-102: DELETE /v2/reviews/{id}/images/{index} - 刪除圖片成功")
    @WithMockUser(authorities = {"order:read"})
    void removeImage_success() throws Exception {
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .images(List.of("m1", "m2"))  // 刪除 1 張後剩 2 張
                .build();

        when(reviewService.removeImage(eq(reviewId), eq(1))).thenReturn(response);

        mockMvc.perform(delete("/v2/reviews/{id}/images/{index}", reviewId, 1)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images.length()").value(2));

        verify(reviewService).removeImage(eq(reviewId), eq(1));
    }

    @Test
    @DisplayName("AC-103: PUT /v2/reviews/{id}/images/order - 重新排序成功")
    @WithMockUser(authorities = {"order:read"})
    void reorderImages_success() throws Exception {
        List<String> newOrder = List.of("m3", "m1", "m2");
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .images(newOrder)
                .build();

        when(reviewService.reorderImages(eq(reviewId), any())).thenReturn(response);

        mockMvc.perform(put("/v2/reviews/{id}/images/order", reviewId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrls\":[\"m3\",\"m1\",\"m2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images[0]").value("m3"))
                .andExpect(jsonPath("$.data.images[1]").value("m1"))
                .andExpect(jsonPath("$.data.images[2]").value("m2"));

        verify(reviewService).reorderImages(eq(reviewId), any());
    }
}
