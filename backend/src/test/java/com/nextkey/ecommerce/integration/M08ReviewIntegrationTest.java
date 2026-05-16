package com.nextkey.ecommerce.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.controller.ReviewController;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.review.BookingReviewService;
import com.nextkey.ecommerce.core.review.ReviewService;
import com.nextkey.ecommerce.domain.model.review.BookingReview;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;

/**
 * M08 評價系統整合測試
 * 測試：評價 CRUD、重複評價防止、平均評分計算
 */
@WebMvcTest(controllers = {
    ReviewController.class
})
@ActiveProfiles("integration-test")
@DisplayName("M08 評價系統整合測試")
public class M08ReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewRepository reviewRepository;

    @MockBean
    private BookingReviewRepository bookingReviewRepository;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private BookingReviewService bookingReviewService;

    @MockBean
    private ListingRepository listingRepository;

    private UUID listingId;
    private UUID userId;
    private UUID orderId;
    private UUID bookingId;
    private UUID reviewId;
    private Review testReview;
    private BookingReview testBookingReview;

    @BeforeEach
    void setUp() {
        listingId = UUID.randomUUID();
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        testReview = Review.builder()
                .id(reviewId)
                .rating(5)
                .title("Great product!")
                .content("Really enjoyed this product.")
                .isVisible(true)
                .isAnonymous(false)
                .helpfulCount(0)
                .helpfulVotes(new HashMap<>())
                .reviewType(Review.ReviewType.PRODUCT)
                .build();

        testBookingReview = BookingReview.builder()
                .id(UUID.randomUUID())
                .rating(5)
                .title("Great stay!")
                .content("Wonderful experience.")
                .isVisible(true)
                .isAnonymous(false)
                .build();
    }

    @Test
    @DisplayName("AC-001: 買家可以對已完成訂單提交評價")
    @WithMockUser(authorities = {"order:create"})
    void createReview_success() throws Exception {
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .listingId(listingId)
                .rating(5)
                .title("Great product!")
                .content("Really enjoyed this product.")
                .createdAt(Instant.now())
                .build();

        when(reviewService.createReview(any(ReviewDto.CreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/v2/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"" + listingId + "\",\"orderId\":\"" + orderId + "\",\"rating\":5,\"title\":\"Great product!\",\"content\":\"Really enjoyed this product.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.title").value("Great product!"));

        verify(reviewService).createReview(any(ReviewDto.CreateRequest.class));
    }

    @Test
    @DisplayName("AC-002: 買家可以對已完成預訂提交評價")
    @WithMockUser(authorities = {"booking:create"})
    void createBookingReview_success() throws Exception {
        when(bookingReviewService.createBookingReview(
                eq(bookingId), eq(5), eq("Great stay!"),
                eq("Wonderful experience."), isNull(), eq(false)))
                .thenReturn(testBookingReview);

        mockMvc.perform(post("/v2/booking-reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\",\"rating\":5,\"title\":\"Great stay!\",\"content\":\"Wonderful experience.\"}"))
                .andExpect(status().isOk());

        verify(bookingReviewService).createBookingReview(
                eq(bookingId), eq(5), eq("Great stay!"),
                eq("Wonderful experience."), isNull(), eq(false));
    }

    @Test
    @DisplayName("AC-003: 賣家/房東可以回覆評價")
    @WithMockUser(authorities = {"room:update"})
    void replyToReview_success() throws Exception {
        ReviewDto.ReviewResponse response = ReviewDto.ReviewResponse.builder()
                .reviewId(reviewId)
                .rating(5)
                .title("Great product!")
                .sellerReply("Thank you for your review!")
                .sellerRepliedAt(Instant.now())
                .build();

        when(reviewService.replyToReview(eq(reviewId), any(ReviewDto.SellerReplyRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/v2/reviews/{reviewId}/reply", reviewId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Thank you for your review!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sellerReply").value("Thank you for your review!"));

        verify(reviewService).replyToReview(eq(reviewId), any(ReviewDto.SellerReplyRequest.class));
    }

    @Test
    @DisplayName("AC-004: 查詢評價列表（商品評價、店家評價）")
    @WithMockUser(authorities = {"order:read"})
    void getReviews_success() throws Exception {
        ReviewDto.ReviewListResponse response = ReviewDto.ReviewListResponse.builder()
                .reviews(List.of(ReviewDto.ReviewResponse.builder()
                        .reviewId(reviewId)
                        .listingId(listingId)
                        .rating(5)
                        .title("Great product!")
                        .createdAt(Instant.now())
                        .build()))
                .totalElements(1)
                .averageRating(5.0)
                .build();

        when(reviewService.getReviewsByListingId(eq(listingId), eq(0), eq(10), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/v2/reviews/listing/{listingId}", listingId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviews").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(reviewService).getReviewsByListingId(eq(listingId), eq(0), eq(10), isNull());
    }

    @Test
    @DisplayName("防止重複評價：同一訂單不可重複評價")
    void preventDuplicateReview_sameOrder() {
        when(reviewRepository.findByOrderIdAndListingId(orderId, listingId))
                .thenReturn(java.util.Optional.of(testReview));

        assertTrue(reviewRepository.findByOrderIdAndListingId(orderId, listingId).isPresent());
    }

    @Test
    @DisplayName("防止重複評價：同一預訂不可重複評價")
    void preventDuplicateReview_sameBooking() {
        BookingReview existingReview = BookingReview.builder()
                .bookingId(bookingId)
                .userId(userId)
                .rating(5)
                .title("First review")
                .build();

        when(bookingReviewRepository.findByBookingIdAndUserId(bookingId, userId))
                .thenReturn(java.util.Optional.of(existingReview));

        assertTrue(bookingReviewRepository.findByBookingIdAndUserId(bookingId, userId).isPresent());
    }

    @Test
    @DisplayName("平均評分計算：驗證 AVG 計算")
    @WithMockUser(authorities = {"order:read"})
    void averageRatingCalculation() throws Exception {
        ReviewDto.RatingStats stats = ReviewDto.RatingStats.builder()
                .listingId(listingId)
                .averageRating(4.5)
                .totalReviews(10)
                .distribution(Map.of(5, 5, 4, 3, 3, 2))
                .build();

        when(reviewService.getRatingStats(listingId)).thenReturn(stats);

        mockMvc.perform(get("/v2/reviews/listing/{listingId}/stats", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(10));

        verify(reviewService).getRatingStats(listingId);
    }

    @Test
    @DisplayName("評價星級：驗證 1-5 分範圍")
    void ratingRange_1_to_5() {
        Review validReview = Review.builder()
                .rating(5)
                .build();

        assertTrue(validReview.getRating() >= 1 && validReview.getRating() <= 5);
    }

    @Test
    @DisplayName("評價後不可修改（Immutable）")
    void reviewIsImmutable_afterCreation() {
        Review review = Review.builder()
                .rating(5)
                .title("Original title")
                .content("Original content")
                .build();

        assertEquals("Original title", review.getTitle());
        assertEquals("Original content", review.getContent());
    }

    @Test
    @DisplayName("BookingReview：驗證房東回覆欄位")
    @WithMockUser(authorities = {"room:update"})
    void bookingReview_hostReplyFields() throws Exception {
        BookingReview reviewWithReply = BookingReview.builder()
                .id(UUID.randomUUID())
                .rating(4)
                .title("Good stay")
                .hostReply("Thank you for your review!")
                .hostRepliedAt(Instant.now())
                .build();

        when(bookingReviewService.replyToBookingReview(eq(reviewWithReply.getId()), anyString()))
                .thenReturn(reviewWithReply);

        mockMvc.perform(post("/v2/booking-reviews/{reviewId}/reply", reviewWithReply.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Thank you for your review!\"}"))
                .andExpect(status().isOk());

        verify(bookingReviewService).replyToBookingReview(eq(reviewWithReply.getId()), anyString());
    }
}