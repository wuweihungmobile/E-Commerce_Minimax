package com.nextkey.ecommerce.core.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.api.dto.ReviewSearchCriteria;
import com.nextkey.ecommerce.core.media.MediaService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;

/**
 * ReviewService.searchReviews 單元測試
 *
 * Sprint 18 US-003: 多維度評價搜尋與篩選
 *
 * 測試範圍：
 * - 搜尋成功（空條件、單一條件、多條件）
 * - 評分範圍驗證（無效範圍、min > max）
 * - 排序驗證（預設、自訂欄位與方向）
 * - 分頁驗證
 * - Listing 不存在錯誤處理
 * - 平均評分與總數計算
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService.searchReviews 單元測試 (Sprint 18 US-003)")
class ReviewServiceSearchTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ReviewService reviewService;

    private UUID listingId;
    private UUID userId;
    private Listing testListing;
    private User testUser;
    private List<Review> testReviews;

    @BeforeEach
    void setUp() {
        listingId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testListing = Listing.builder()
                .id(listingId)
                .title("Test Product")
                .listingType(Listing.ListingType.PRODUCT)
                .build();

        testUser = User.builder()
                .id(userId)
                .fullName("Test User")
                .build();

        // 準備 3 筆測試評價
        testReviews = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Review review = Review.builder()
                    .id(UUID.randomUUID())
                    .listing(testListing)
                    .user(testUser)
                    .listingId(listingId)
                    .userId(userId)
                    .reviewType(Review.ReviewType.PRODUCT)
                    .rating(5 - i)  // 5, 4, 3
                    .title("Review " + i)
                    .content("Content " + i)
                    .isVisible(true)
                    .helpfulCount(i)
                    .createdAt(Instant.now().minusSeconds(i * 86400L))
                    .build();
            testReviews.add(review);
        }
    }

    // ========== 基本搜尋測試 ==========

    @Test
    @DisplayName("空條件搜尋 - 應回傳所有可見評價")
    void searchReviews_withEmptyCriteria_returnsAllVisibleReviews() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .page(0)
                .size(10)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews, PageRequest.of(0, 10), 3);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertNotNull(response);
        assertEquals(3, response.getReviews().size());
        assertEquals(3, response.getTotalElements());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(4.0, response.getAverageRating());
        assertEquals(3, response.getTotalReviews());
    }

    @Test
    @DisplayName("關鍵字搜尋 - 應將關鍵字傳遞給 Repository")
    void searchReviews_withKeyword_passesKeywordToRepository() {
        // Given
        String keyword = "great product";
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .keyword(keyword)
                .build();

        Page<Review> mockPage = new PageImpl<>(List.of(testReviews.get(0)));

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), eq(keyword), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(5.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(1);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertEquals(1, response.getReviews().size());
        verify(reviewRepository).searchReviews(
                eq(listingId), eq(keyword), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class));
    }

    // ========== 評分範圍測試 ==========

    @Test
    @DisplayName("評分範圍 - 有效的 min/max 評分")
    void searchReviews_withValidRatingRange_succeeds() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .minRating(3)
                .maxRating(5)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), isNull(), eq(3), eq(5), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertNotNull(response);
    }

    @Test
    @DisplayName("評分範圍 - minRating 小於 1 應拋出例外")
    void searchReviews_withInvalidMinRating_throwsException() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .minRating(0)  // 無效
                .build();

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.searchReviews(criteria));
        assertTrue(ex.getMessage().contains("minRating"));
    }

    @Test
    @DisplayName("評分範圍 - maxRating 大於 5 應拋出例外")
    void searchReviews_withInvalidMaxRating_throwsException() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .maxRating(6)  // 無效
                .build();

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.searchReviews(criteria));
        assertTrue(ex.getMessage().contains("maxRating"));
    }

    @Test
    @DisplayName("評分範圍 - minRating 大於 maxRating 應拋出例外")
    void searchReviews_withMinGreaterThanMax_throwsException() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .minRating(5)
                .maxRating(3)
                .build();

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.searchReviews(criteria));
        assertTrue(ex.getMessage().contains("cannot be greater"));
    }

    // ========== 日期範圍測試 ==========

    @Test
    @DisplayName("日期範圍 - 有效的起訖日期")
    void searchReviews_withValidDateRange_succeeds() {
        // Given
        Instant startDate = Instant.now().minusSeconds(7 * 86400L);  // 7 天前
        Instant endDate = Instant.now();

        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), isNull(), isNull(), isNull(), eq(startDate), eq(endDate), isNull(), isNull(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertNotNull(response);
        verify(reviewRepository).searchReviews(
                eq(listingId), isNull(), isNull(), isNull(), eq(startDate), eq(endDate), isNull(), isNull(),
                any(Pageable.class));
    }

    // ========== 篩選測試 ==========

    @Test
    @DisplayName("hasImages=true - 應傳遞給 Repository")
    void searchReviews_withHasImagesTrue_succeeds() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .hasImages(true)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), isNull(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertNotNull(response);
    }

    @Test
    @DisplayName("hasReply=true - 應傳遞給 Repository")
    void searchReviews_withHasReplyTrue_succeeds() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .hasReply(true)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                eq(listingId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertNotNull(response);
    }

    // ========== 排序測試 ==========

    @Test
    @DisplayName("排序 - 預設為 createdAt DESC")
    void searchReviews_withoutSort_usesDefaultSort() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        reviewService.searchReviews(criteria);

        // Then
        verify(reviewRepository).searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                argThat((Pageable p) -> {
                    Sort.Order order = p.getSort().getOrderFor("createdAt");
                    return order != null && order.getDirection() == Sort.Direction.DESC;
                }));
    }

    @Test
    @DisplayName("排序 - 自訂 rating ASC")
    void searchReviews_withCustomSort_usesCustomSort() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .sortBy(ReviewSearchCriteria.SortBy.RATING)
                .sortDir(ReviewSearchCriteria.SortDir.ASC)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        reviewService.searchReviews(criteria);

        // Then
        verify(reviewRepository).searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                argThat((Pageable p) -> {
                    Sort.Order order = p.getSort().getOrderFor("rating");
                    return order != null && order.getDirection() == Sort.Direction.ASC;
                }));
    }

    // ========== 分頁測試 ==========

    @Test
    @DisplayName("分頁 - size 超過上限應被截斷")
    void searchReviews_withLargeSize_clampsToMax() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .page(0)
                .size(999)  // 超過 DEFAULT_PAGE_SIZE (50)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertEquals(ReviewService.DEFAULT_PAGE_SIZE, response.getSize());
    }

    @Test
    @DisplayName("分頁 - 負數 page 應被修正為 0")
    void searchReviews_withNegativePage_clampsToZero() {
        // Given
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .page(-1)
                .size(10)
                .build();

        Page<Review> mockPage = new PageImpl<>(testReviews);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(testListing));
        when(reviewRepository.searchReviews(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(mockPage);
        when(reviewRepository.getAverageRatingByListingId(listingId)).thenReturn(4.0);
        when(reviewRepository.countByListingId(listingId)).thenReturn(3);

        // When
        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);

        // Then
        assertEquals(0, response.getPage());
    }

    // ========== 錯誤處理測試 ==========

    @Test
    @DisplayName("Listing 不存在 - 應拋出 BusinessException")
    void searchReviews_withNonExistentListing_throwsException() {
        // Given
        UUID nonExistentListingId = UUID.randomUUID();
        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(nonExistentListingId)
                .build();

        when(listingRepository.findById(nonExistentListingId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BusinessException.class,
                () -> reviewService.searchReviews(criteria));
    }
}
