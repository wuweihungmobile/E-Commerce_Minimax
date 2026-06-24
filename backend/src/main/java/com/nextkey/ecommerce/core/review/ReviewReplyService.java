package com.nextkey.ecommerce.core.review;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.ReviewReplyDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.model.review.ReviewReply;
import com.nextkey.ecommerce.domain.repository.ReviewReplyRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 評價回覆服務
 *
 * 拆分原因（Sprint 16 Retro AI-001）：
 * - 原本商家回覆邏輯直接整合在 ReviewService 中
 * - 邏輯複雜度增加（權限檢查、欄位更新等）
 * - 拆分後可獨立測試
 *
 * 職責：
 * - 商家回覆評價
 * - 取得評價回覆列表
 * - 防止重複回覆
 *
 * @author Sprint 16 (US-001)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewReplyService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;

    // ========== Public API ==========

    /**
     * 建立商家回覆
     *
     * @param reviewId 評價 ID
     * @param request  回覆請求
     * @return 建立後的回覆資料
     * @throws BusinessException
     *         - E_1087: 評價不存在
     *         - E_1007: 當前用戶不是該評價的 Listing 擁有者
     *         - E_1086: 已存在回覆（重複回覆）
     */
    @Transactional
    public ReviewReplyDto.ReplyResponse createReply(UUID reviewId, ReviewReplyDto.CreateReplyRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        // 1. 查詢評價
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 2. 權限檢查：只有 Listing 擁有者可以回覆
        Listing listing = review.getListing();
        if (listing.getTenantId() == null || !listing.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "Only the listing owner can reply");
        }

        // 3. 重複回覆防護
        if (reviewReplyRepository.existsByReviewId(reviewId)) {
            throw new BusinessException(ErrorCode.E_1086, "Review already has a reply");
        }

        // 4. 建立回覆
        ReviewReply reply = ReviewReply.builder()
                .review(review)
                .replierId(userId)
                .content(request.getContent())
                .build();

        reply = reviewReplyRepository.save(reply);

        log.info("Review reply created: replyId={}, reviewId={}, replierId={}",
                reply.getId(), reviewId, userId);

        return toReplyResponse(reply);
    }

    /**
     * 取得評價的回覆列表（按建立時間升冪排序）
     *
     * @param reviewId 評價 ID
     * @return 回覆列表（可能為空）
     */
    @Transactional(readOnly = true)
    public List<ReviewReplyDto.ReplyResponse> getRepliesByReviewId(UUID reviewId) {
        List<ReviewReply> replies = reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
        return replies.stream()
                .map(this::toReplyResponse)
                .toList();
    }

    /**
     * 取得評價的回覆（單筆回覆或空）
     *
     * @param reviewId 評價 ID
     * @return 回覆（Optional，可能為空）
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ReviewReplyDto.ReplyResponse> getReplyByReviewId(UUID reviewId) {
        return reviewReplyRepository.findFirstByReviewIdOrderByCreatedAtAsc(reviewId)
                .map(this::toReplyResponse);
    }

    // ========== Helper Methods ==========

    private ReviewReplyDto.ReplyResponse toReplyResponse(ReviewReply reply) {
        return ReviewReplyDto.ReplyResponse.builder()
                .replyId(reply.getId())
                .reviewId(reply.getReview().getId())
                .replierId(reply.getReplierId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}
