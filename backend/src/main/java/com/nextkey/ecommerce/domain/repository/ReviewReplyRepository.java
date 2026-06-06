package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.review.ReviewReply;

/**
 * 評價回覆 Repository
 *
 * @author Sprint 16 (US-001)
 */
@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, UUID> {

    /**
     * 檢查指定評價是否已有回覆
     * 用於防止重複回覆
     */
    boolean existsByReviewId(UUID reviewId);

    /**
     * 取得評價的所有回覆（按建立時間升冪排序）
     */
    List<ReviewReply> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);

    /**
     * 取得評價的第一個回覆（通常一對一關係下用於查詢）
     */
    Optional<ReviewReply> findFirstByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
