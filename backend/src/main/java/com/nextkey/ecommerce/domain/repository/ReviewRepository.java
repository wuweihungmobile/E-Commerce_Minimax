package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 依處理狀態與租戶取得評價列表（租戶隔離查詢，Sprint 73 DEF-029 修復）。
     * Review 本身無 tenant_id 欄位，經由 listing 關聯取得所屬租戶。
     */
    @Query("SELECT r FROM Review r WHERE r.isHandled = :isHandled AND r.listing.tenant.id = :tenantId")
    Page<Review> findByIsHandledAndTenantId(
            @Param("isHandled") Boolean isHandled,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

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

    /**
     * 原子且冪等地登記一次「有幫助」投票（Sprint 105，DEF-054）。
     *
     * <p>取代原本 {@code ReviewService.markHelpful} 的「讀出整份 JSON map → 記憶體改 → save()」，
     * 一次修掉該實作的兩個獨立缺陷：
     *
     * <ol>
     *   <li><b>無去重</b>：原本 {@code votes.put(userId, currentVotes + 1)} 讓同一人可無限次遞增，
     *       而前端顯示的是「N <b>人</b>覺得有幫助」、{@code helpfulCount} 又是搜尋排序欄位。
     *       {@code ||} 合併對同一 key 只會覆寫成 1，重複呼叫不改變任何值——**天然冪等**。</li>
     *   <li><b>讀後寫競態</b>：{@code Review} 無 {@code @Version}，併發投票會整份覆蓋而靜默漏計。
     *       實測 10 位相異使用者同時投票只有 <b>2</b> 票存活（見
     *       {@code M08ReviewHelpfulVotingIntegrationTest}）。改為單一敘述後由資料庫序列化。</li>
     * </ol>
     *
     * <p>{@code helpful_count} 取合併後的 key 數，語意即「相異投票人數」，與前端顯示一致。
     * SET 子句右側對 {@code helpful_votes} 的引用取的是**該列的舊值**（SQL 語意），
     * 故兩個欄位都基於同一份合併結果，不會互相脫節。
     *
     * <p><b>務必使用 {@code CAST(x AS jsonb)} 而非 PostgreSQL 慣用的 {@code x::jsonb}</b>：
     * 本查詢帶具名參數，Hibernate 會把 {@code ::} 的第一個冒號當成參數前綴吃掉，
     * 送到資料庫的是 {@code '{}':jsonb} 而報 {@code syntax error at or near ":"}。
     *
     * <p><b>{@code clearAutomatically = true} 會清掉整個持久化上下文</b>，呼叫端必須在其後
     * 重新讀取才拿得到更新後的值（{@code ReviewService.markHelpful} 即如此）。目前唯一呼叫端
     * 自成一筆交易，故安全；**若日後從一筆更大的交易裡呼叫本方法，該交易中其他尚未 flush
     * 的實體會被 detach 而遺失變更**——屆時應改為呼叫端自行 {@code refresh} 單一實體。
     *
     * @param reviewId 目標評價
     * @param userId   投票者 id 的字串形式（JSONB 的 key 必須是 text）
     * @return 受影響筆數；1 表示已登記，0 表示該評價不存在
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE reviews
               SET helpful_votes = COALESCE(helpful_votes, CAST('{}' AS jsonb))
                                   || jsonb_build_object(CAST(:userId AS text), 1),
                   helpful_count = (
                       SELECT COUNT(*)
                         FROM jsonb_object_keys(
                                  COALESCE(helpful_votes, CAST('{}' AS jsonb))
                                  || jsonb_build_object(CAST(:userId AS text), 1))
                   ),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :reviewId
            """, nativeQuery = true)
    int registerHelpfulVote(@Param("reviewId") UUID reviewId, @Param("userId") String userId);
}
