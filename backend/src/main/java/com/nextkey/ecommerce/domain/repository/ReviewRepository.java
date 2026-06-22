package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.review.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByListingIdAndIsVisibleTrueOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    Page<Review> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Review> findByOrderId(UUID orderId);

    List<Review> findByBookingId(UUID bookingId);

    Optional<Review> findByOrderIdAndListingId(UUID orderId, UUID listingId);

    Optional<Review> findByBookingIdAndListingId(UUID bookingId, UUID listingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listingId = :listingId AND r.isVisible = true")
    Double getAverageRatingByListingId(@Param("listingId") UUID listingId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.listingId = :listingId AND r.isVisible = true")
    int countByListingId(@Param("listingId") UUID listingId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.listingId = :listingId AND r.isVisible = true GROUP BY r.rating")
    List<Object[]> getRatingDistribution(@Param("listingId") UUID listingId);

    @Query("SELECT r FROM Review r WHERE r.listingId = :listingId AND r.isVisible = true AND r.rating >= :minRating")
    Page<Review> findByListingIdAndRatingGreaterThanEqual(
            @Param("listingId") UUID listingId,
            @Param("minRating") Integer minRating,
            Pageable pageable);

    Page<Review> findByIsHandled(Boolean isHandled, Pageable pageable);

    // ========== Sprint 18 US-003: 多維度搜尋與篩選 ==========

    /**
     * 多維度評價搜尋
     *
     * @param listingId 商品/房型 ID（必要）
     * @param keyword 關鍵字（搜尋標題或內容，可為 null）
     * @param minRating 最低評分（可為 null）
     * @param maxRating 最高評分（可為 null）
     * @param startDate 起始日期（可為 null）
     * @param endDate 結束日期（可為 null）
     * @param hasImages 是否有圖片（可為 null，true=只回傳有圖片的）
     * @param hasReply 是否有回覆（可為 null，true=只回傳有回覆的）
     * @param pageable 分頁與排序
     * @return 符合條件的評價分頁
     */
    @Query("""
        SELECT r FROM Review r
        WHERE r.listingId = :listingId
          AND r.isVisible = true
          AND (:keyword IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(r.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:minRating IS NULL OR r.rating >= :minRating)
          AND (:maxRating IS NULL OR r.rating <= :maxRating)
          AND (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
          AND (:hasImages IS NULL
               OR (:hasImages = true AND r.images IS NOT NULL AND SIZE(r.images) > 0)
               OR (:hasImages = false AND (r.images IS NULL OR SIZE(r.images) = 0)))
          AND (:hasReply IS NULL
               OR (:hasReply = true AND EXISTS (SELECT 1 FROM ReviewReply rr WHERE rr.reviewId = r.id))
               OR (:hasReply = false AND NOT EXISTS (SELECT 1 FROM ReviewReply rr WHERE rr.reviewId = r.id)))
    """)
    Page<Review> searchReviews(
            @Param("listingId") UUID listingId,
            @Param("keyword") String keyword,
            @Param("minRating") Integer minRating,
            @Param("maxRating") Integer maxRating,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("hasImages") Boolean hasImages,
            @Param("hasReply") Boolean hasReply,
            Pageable pageable);
}
