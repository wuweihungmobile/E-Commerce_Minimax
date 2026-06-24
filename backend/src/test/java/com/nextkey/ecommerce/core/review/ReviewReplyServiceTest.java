package com.nextkey.ecommerce.core.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.ReviewReplyDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.model.review.ReviewReply;
import com.nextkey.ecommerce.domain.repository.ReviewReplyRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ReviewReplyService 單元測試
 *
 * 測試範圍：
 * - createReply 成功案例
 * - createReply 失敗案例（評價不存在、非擁有者、重複回覆）
 * - getRepliesByReviewId 各種場景（空、單筆、多筆）
 * - getReplyByReviewId
 *
 * 覆蓋率目標：>= 80%
 *
 * @author Sprint 16 (US-001)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewReplyService 單元測試 (Sprint 16 US-001)")
class ReviewReplyServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReplyRepository reviewReplyRepository;

    @InjectMocks
    private ReviewReplyService reviewReplyService;

    private UUID reviewId;
    private UUID listingId;
    private UUID ownerId;
    private UUID otherUserId;
    private UUID tenantId;
    private Review testReview;
    private Listing testListing;
    private ReviewReplyDto.CreateReplyRequest createRequest;

    @BeforeEach
    void setUp() {
        reviewId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        testListing = Listing.builder()
                .id(listingId)
                .ownerId(ownerId)
                .tenantId(tenantId)
                .title("Test Listing")
                .listingType(Listing.ListingType.PRODUCT)
                .build();

        testReview = Review.builder()
                .id(reviewId)
                .listing(testListing)
                .rating(5)
                .title("Great product")
                .content("Loved it")
                .isVisible(true)
                .build();

        createRequest = ReviewReplyDto.CreateReplyRequest.builder()
                .content("Thank you for your feedback!")
                .build();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // ========== createReply 測試 ==========

    @Test
    @DisplayName("AC-001.1: 成功建立回覆")
    void createReply_Success() {
        // Given
        TenantContext.setCurrentUser(ownerId);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(testReview));
        when(reviewReplyRepository.existsByReviewId(reviewId)).thenReturn(false);
        when(reviewReplyRepository.save(any(ReviewReply.class))).thenAnswer(invocation -> {
            ReviewReply reply = invocation.getArgument(0);
            reply.setId(UUID.randomUUID());
            reply.setCreatedAt(Instant.now());
            reply.setUpdatedAt(Instant.now());
            return reply;
        });

        // When
        ReviewReplyDto.ReplyResponse response = reviewReplyService.createReply(reviewId, createRequest);

        // Then
        assertNotNull(response);
        assertNotNull(response.getReplyId());
        assertEquals(reviewId, response.getReviewId());
        assertEquals(ownerId, response.getReplierId());
        assertEquals("Thank you for your feedback!", response.getContent());
        verify(reviewRepository).findById(reviewId);
        verify(reviewReplyRepository).existsByReviewId(reviewId);
        verify(reviewReplyRepository).save(any(ReviewReply.class));
    }

    @Test
    @DisplayName("AC-001.2: 重複回覆拋出 BusinessException E_1086")
    void createReply_DuplicateReply_ThrowsE1086() {
        // Given
        TenantContext.setCurrentUser(ownerId);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(testReview));
        when(reviewReplyRepository.existsByReviewId(reviewId)).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> reviewReplyService.createReply(reviewId, createRequest));
        assertEquals(ErrorCode.E_1086, exception.getErrorCode());
        verify(reviewReplyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    @DisplayName("AC-001.3: 評價不存在拋出 BusinessException E_1087")
    void createReply_ReviewNotFound_ThrowsE1087() {
        // Given
        TenantContext.setCurrentUser(ownerId);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> reviewReplyService.createReply(reviewId, createRequest));
        assertEquals(ErrorCode.E_1087, exception.getErrorCode());
        verify(reviewReplyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    @DisplayName("AC-001.4: 非 Listing 擁有者回覆拋出 E_1007")
    void createReply_NotOwner_ThrowsE1007() {
        // Given
        TenantContext.setCurrentUser(otherUserId); // 非擁有者
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(testReview));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> reviewReplyService.createReply(reviewId, createRequest));
        assertEquals(ErrorCode.E_1007, exception.getErrorCode());
        verify(reviewReplyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    @DisplayName("AC-001.5: 空 content 會被 @NotBlank 攔截（Service 不處理，Controller 層）")
    void createReply_EmptyContent_AllowedAtServiceLayer() {
        // 註：@NotBlank 是 Bean Validation，在 Service 層不驗證
        // 這裡只測試 Service 接受空字串
        TenantContext.setCurrentUser(ownerId);
        ReviewReplyDto.CreateReplyRequest emptyRequest = ReviewReplyDto.CreateReplyRequest.builder()
                .content("")
                .build();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(testReview));
        when(reviewReplyRepository.existsByReviewId(reviewId)).thenReturn(false);
        when(reviewReplyRepository.save(any(ReviewReply.class))).thenAnswer(invocation -> {
            ReviewReply reply = invocation.getArgument(0);
            reply.setId(UUID.randomUUID());
            return reply;
        });

        // Should not throw
        assertDoesNotThrow(() -> reviewReplyService.createReply(reviewId, emptyRequest));
    }

    // ========== getRepliesByReviewId 測試 ==========

    @Test
    @DisplayName("AC-002.1: 取得空回覆列表")
    void getRepliesByReviewId_Empty() {
        // Given
        when(reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId))
                .thenReturn(List.of());

        // When
        List<ReviewReplyDto.ReplyResponse> result = reviewReplyService.getRepliesByReviewId(reviewId);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("AC-002.2: 取得單筆回覆")
    void getRepliesByReviewId_Single() {
        // Given
        ReviewReply reply = createTestReply();
        when(reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId))
                .thenReturn(List.of(reply));

        // When
        List<ReviewReplyDto.ReplyResponse> result = reviewReplyService.getRepliesByReviewId(reviewId);

        // Then
        assertEquals(1, result.size());
        assertEquals(reply.getId(), result.get(0).getReplyId());
        assertEquals("Test reply content", result.get(0).getContent());
    }

    @Test
    @DisplayName("AC-002.3: 取得多筆回覆按時間排序")
    void getRepliesByReviewId_Multiple() {
        // Given
        ReviewReply reply1 = createTestReply();
        reply1.setContent("First reply");
        ReviewReply reply2 = createTestReply();
        reply2.setContent("Second reply");
        when(reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId))
                .thenReturn(List.of(reply1, reply2));

        // When
        List<ReviewReplyDto.ReplyResponse> result = reviewReplyService.getRepliesByReviewId(reviewId);

        // Then
        assertEquals(2, result.size());
        assertEquals("First reply", result.get(0).getContent());
        assertEquals("Second reply", result.get(1).getContent());
    }

    // ========== getReplyByReviewId 測試 ==========

    @Test
    @DisplayName("取得單一回覆（存在）")
    void getReplyByReviewId_Found() {
        // Given
        ReviewReply reply = createTestReply();
        when(reviewReplyRepository.findFirstByReviewIdOrderByCreatedAtAsc(reviewId))
                .thenReturn(Optional.of(reply));

        // When
        Optional<ReviewReplyDto.ReplyResponse> result = reviewReplyService.getReplyByReviewId(reviewId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(reply.getId(), result.get().getReplyId());
    }

    @Test
    @DisplayName("取得單一回覆（不存在）")
    void getReplyByReviewId_NotFound() {
        // Given
        when(reviewReplyRepository.findFirstByReviewIdOrderByCreatedAtAsc(reviewId))
                .thenReturn(Optional.empty());

        // When
        Optional<ReviewReplyDto.ReplyResponse> result = reviewReplyService.getReplyByReviewId(reviewId);

        // Then
        assertFalse(result.isPresent());
    }

    // ========== Helper Methods ==========

    private ReviewReply createTestReply() {
        return ReviewReply.builder()
                .id(UUID.randomUUID())
                .review(testReview)
                .replierId(ownerId)
                .content("Test reply content")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
