package com.nextkey.ecommerce.core.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 評價服務
 *
 * Sprint 16 變更：
 * - US-005: 新增 MAX_REVIEW_IMAGES 上限驗證（最多 9 張）
 * - US-006: 新增 addImage / removeImage / reorderImages 圖片管理方法
 * - US-005: 整合 MediaService.verifyMediaExists 驗證每個 imageUrl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;

    // Pagination default
    public static final int DEFAULT_PAGE_SIZE = 50;

    // 🔴 Sprint 16 US-005: 評價圖片上限
    public static final int MAX_REVIEW_IMAGES = ReviewDto.MAX_REVIEW_IMAGES;

    /**
     * 建立評價
     */
    @Transactional
    @CacheEvict(value = "ratingStats", key = "#request.listingId")
    public ReviewDto.ReviewResponse createReview(ReviewDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        // 檢查 Listing 是否存在
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Listing not found"));

        // 檢查是否已評價
        if (request.getOrderId() != null) {
            if (reviewRepository.findByOrderIdAndListingId(request.getOrderId(), request.getListingId()).isPresent()) {
                throw new BusinessException(ErrorCode.E_1093, "You have already reviewed this item");
            }
        }

        // 檢查是否已預訂評價
        if (request.getBookingId() != null) {
            if (reviewRepository.findByBookingIdAndListingId(request.getBookingId(), request.getListingId()).isPresent()) {
                throw new BusinessException(ErrorCode.E_1094, "You have already reviewed this booking");
            }
        }

        // 🔴 Sprint 16 US-005: 驗證圖片上限（最多 9 張）
        validateImageCount(request.getImages());

        // 🔴 Sprint 16 US-005: 驗證圖片有效性（整合 M15 Media）
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            String invalidImageId = mediaService.findFirstInvalidMediaId(request.getImages());
            if (invalidImageId != null) {
                throw new BusinessException(ErrorCode.E_1089,
                        "Invalid image: " + invalidImageId);
            }
        }

        Review review = Review.builder()
                .listing(listing)
                .user(userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.E_1006)))
                .orderId(request.getOrderId())
                .bookingId(request.getBookingId())
                .reviewType(listing.getListingType() == Listing.ListingType.PRODUCT
                        ? Review.ReviewType.PRODUCT : Review.ReviewType.ROOM)
                .rating(request.getRating())
                .title(request.getTitle())
                .content(request.getContent())
                .images(request.getImages())
                .isAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false)
                .helpfulVotes(new HashMap<>())
                .helpfulCount(0)
                .isVisible(true)
                .build();

        review = reviewRepository.save(review);

        log.info("Review created: reviewId={}, listingId={}, rating={}",
                review.getId(), request.getListingId(), request.getRating());

        return toReviewResponse(review, listing);
    }

    /**
     * 更新評價
     */
    @Transactional
    public ReviewDto.ReviewResponse updateReview(UUID reviewId, ReviewDto.UpdateRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 檢查是否是本人
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "You can only update your own review");
        }

        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getTitle() != null) {
            review.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            review.setContent(request.getContent());
        }
        if (request.getImages() != null) {
            // 🔴 Sprint 16 US-005: 驗證圖片上限與有效性
            validateImageCount(request.getImages());
            String invalidImageId = mediaService.findFirstInvalidMediaId(request.getImages());
            if (invalidImageId != null) {
                throw new BusinessException(ErrorCode.E_1089, "Invalid image: " + invalidImageId);
            }
            review.setImages(request.getImages());
        }

        review = reviewRepository.save(review);

        log.info("Review updated: reviewId={}", reviewId);

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 刪除評價（軟刪除）
     */
    @Transactional
    public void deleteReview(final UUID reviewId) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 檢查是否是本人或是管理員
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "You can only delete your own review");
        }

        review.setIsVisible(false);
        reviewRepository.save(review);

        log.info("Review deleted (soft): reviewId={}", reviewId);
    }

    /**
     * 取得評價列表
     */
    @Transactional(readOnly = true)
    public ReviewDto.ReviewListResponse getReviewsByListingId(
            UUID listingId, int page, int size, Integer minRating) {

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Listing not found"));

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));

        Page<Review> reviews;
        if (minRating != null && minRating > 0) {
            reviews = reviewRepository.findByListingIdAndRatingGreaterThanEqual(listingId, minRating, pageRequest);
        } else {
            reviews = reviewRepository.findByListingIdAndIsVisibleTrueOrderByCreatedAtDesc(listingId, pageRequest);
        }

        List<ReviewDto.ReviewResponse> reviewResponses = reviews.getContent().stream()
                .map(r -> toReviewResponse(r, listing))
                .collect(Collectors.toList());

        Double avgRating = reviewRepository.getAverageRatingByListingId(listingId);
        Integer totalReviews = reviewRepository.countByListingId(listingId);

        return ReviewDto.ReviewListResponse.builder()
                .reviews(reviewResponses)
                .page(page)
                .size(size)
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .averageRating(avgRating != null ? avgRating : 0.0)
                .totalReviews(totalReviews)
                .build();
    }

    /**
     * 取得用戶評價列表
     */
    @Transactional(readOnly = true)
    public ReviewDto.ReviewListResponse getUserReviews(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));
        Page<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        List<ReviewDto.ReviewResponse> reviewResponses = reviews.getContent().stream()
                .map(r -> toReviewResponse(r, r.getListing()))
                .collect(Collectors.toList());

        return ReviewDto.ReviewListResponse.builder()
                .reviews(reviewResponses)
                .page(page)
                .size(size)
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .averageRating(null)
                .totalReviews(null)
                .build();
    }

    /**
     * 取得評價統計
     */
    @Cacheable(value = "ratingStats", key = "#listingId")
    @Transactional(readOnly = true)
    public ReviewDto.RatingStats getRatingStats(UUID listingId) {
        Double avgRating = reviewRepository.getAverageRatingByListingId(listingId);
        Integer totalReviews = reviewRepository.countByListingId(listingId);
        List<Object[]> distribution = reviewRepository.getRatingDistribution(listingId);

        Map<Integer, Integer> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingCounts.put(i, 0);
        }
        for (Object[] row : distribution) {
            Integer rating = (Integer) row[0];
            Long count = (Long) row[1];
            ratingCounts.put(rating, count.intValue());
        }

        return ReviewDto.RatingStats.builder()
                .listingId(listingId)
                .averageRating(avgRating) // null when no reviews (AC-002)
                .totalReviews(totalReviews)
                .rating1Count(ratingCounts.get(1))
                .rating2Count(ratingCounts.get(2))
                .rating3Count(ratingCounts.get(3))
                .rating4Count(ratingCounts.get(4))
                .rating5Count(ratingCounts.get(5))
                .distribution(ratingCounts)
                .build();
    }

    /**
     * 標記為有帮助
     */
    @Transactional
    public ReviewDto.ReviewResponse markHelpful(UUID reviewId) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        Map<String, Integer> votes = review.getHelpfulVotes();
        if (votes == null) {
            votes = new HashMap<>();
        }

        String userIdStr = userId.toString();
        int currentVotes = votes.getOrDefault(userIdStr, 0);
        votes.put(userIdStr, currentVotes + 1);

        int totalVotes = votes.values().stream().mapToInt(Integer::intValue).sum();
        review.setHelpfulVotes(votes);
        review.setHelpfulCount(totalVotes);

        review = reviewRepository.save(review);

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 標記評價為已處理
     */
    @Transactional
    public ReviewDto.ReviewResponse markAsHandled(UUID reviewId) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        review.setIsHandled(true);
        review.setHandledAt(Instant.now());
        review.setHandledBy(userRepository.findById(userId).orElse(null));
        review = reviewRepository.save(review);

        log.info("Review marked as handled: reviewId={}, handledBy={}", reviewId, userId);

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 標記評價為未處理
     */
    @Transactional
    public ReviewDto.ReviewResponse markAsUnhandled(UUID reviewId) {
        TenantContext.getCurrentUser(); // Validate user is authenticated

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        review.setIsHandled(false);
        review.setHandledAt(null);
        review.setHandledBy(null);
        review = reviewRepository.save(review);

        log.info("Review marked as unhandled: reviewId={}", reviewId);

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 根據處理狀態取得評價列表
     */
    @Transactional(readOnly = true)
    public ReviewDto.ReviewListResponse getReviewsByHandlingStatus(Boolean isHandled, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));

        Page<Review> reviews = reviewRepository.findByIsHandled(isHandled, pageRequest);

        List<ReviewDto.ReviewResponse> reviewResponses = reviews.getContent().stream()
                .map(r -> toReviewResponse(r, r.getListing()))
                .collect(Collectors.toList());

        return ReviewDto.ReviewListResponse.builder()
                .reviews(reviewResponses)
                .page(page)
                .size(size)
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .averageRating(null)
                .totalReviews(null)
                .build();
    }

    // ========== Sprint 18 US-003: 多維度搜尋與篩選 ==========

    /**
     * 多維度評價搜尋
     *
     * 支援條件：
     * - 關鍵字（搜尋標題或內容）
     * - 評分範圍 (minRating/maxRating)
     * - 日期範圍 (startDate/endDate)
     * - 是否有圖片 (hasImages)
     * - 是否有商家回覆 (hasReply)
     * - 多欄位排序 (sortBy/sortDir)
     *
     * @param criteria 搜尋條件
     * @return 符合條件的評價分頁結果（含平均評分、總數等統計）
     * @throws BusinessException
     *         - E_3000: Listing 不存在
     */
    @Transactional(readOnly = true)
    public ReviewDto.ReviewListResponse searchReviews(ReviewSearchCriteria criteria) {
        // 驗證 Listing 存在
        Listing listing = listingRepository.findById(criteria.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Listing not found"));

        // 驗證評分範圍
        validateRatingRange(criteria.getMinRating(), criteria.getMaxRating());

        // 構建排序
        Sort sort = buildSort(criteria);

        // 構建分頁
        int page = Math.max(0, criteria.getPage());
        int size = Math.min(Math.max(1, criteria.getSize()), DEFAULT_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        // 執行搜尋
        Page<Review> reviews = reviewRepository.searchReviews(
                criteria.getListingId(),
                criteria.getKeyword(),
                criteria.getMinRating(),
                criteria.getMaxRating(),
                criteria.getStartDate(),
                criteria.getEndDate(),
                criteria.getHasImages(),
                criteria.getHasReply(),
                pageRequest);

        // 轉換為 Response
        List<ReviewDto.ReviewResponse> reviewResponses = reviews.getContent().stream()
                .map(r -> toReviewResponse(r, listing))
                .collect(Collectors.toList());

        // 取得統計資訊（基於整個 listing，不限搜尋條件）
        Double avgRating = reviewRepository.getAverageRatingByListingId(criteria.getListingId());
        Integer totalReviews = reviewRepository.countByListingId(criteria.getListingId());

        return ReviewDto.ReviewListResponse.builder()
                .reviews(reviewResponses)
                .page(page)
                .size(size)
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .averageRating(avgRating != null ? avgRating : 0.0)
                .totalReviews(totalReviews)
                .build();
    }

    /**
     * 驗證評分範圍
     */
    private void validateRatingRange(Integer minRating, Integer maxRating) {
        if (minRating != null && (minRating < 1 || minRating > 5)) {
            throw new BusinessException(ErrorCode.E_1095,
                    "minRating must be between 1 and 5");
        }
        if (maxRating != null && (maxRating < 1 || maxRating > 5)) {
            throw new BusinessException(ErrorCode.E_1095,
                    "maxRating must be between 1 and 5");
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new BusinessException(ErrorCode.E_1095,
                    "minRating cannot be greater than maxRating");
        }
    }

    /**
     * 構建排序條件
     */
    private Sort buildSort(ReviewSearchCriteria criteria) {
        ReviewSearchCriteria.SortBy sortBy = criteria.getSortBy() != null
                ? criteria.getSortBy()
                : ReviewSearchCriteria.SortBy.CREATED_AT;
        ReviewSearchCriteria.SortDir sortDir = criteria.getSortDir() != null
                ? criteria.getSortDir()
                : ReviewSearchCriteria.SortDir.DESC;

        Sort.Direction direction = sortDir == ReviewSearchCriteria.SortDir.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, sortBy.getField());
    }

    // ========== 圖片管理方法（Sprint 16 US-006） ==========

    /**
     * 新增單張圖片到評價
     *
     * @param reviewId 評價 ID
     * @param imageUrl 圖片 URL（mediaId）
     * @return 更新後的評價
     * @throws BusinessException
     *         - E_1087: 評價不存在
     *         - E_1091: 非本人操作
     *         - E_1088: 圖片數量超限
     *         - E_1089: 圖片無效
     */
    @Transactional
    public ReviewDto.ReviewResponse addImage(UUID reviewId, String imageUrl) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 權限檢查：只有評價本人可以操作
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1091, "You can only add images to your own review");
        }

        // 驗證圖片有效
        if (!mediaService.existsMediaById(imageUrl)) {
            throw new BusinessException(ErrorCode.E_1089, "Invalid image: " + imageUrl);
        }

        List<String> images = review.getImages() != null ? new ArrayList<>(review.getImages()) : new ArrayList<>();

        // 🔴 驗證數量上限：
        // MAX_REVIEW_IMAGES = 9
        // - 已有 8 張時可新增至 9 張 ✅ (8 + 1 = 9，符合)
        // - 已有 9 張時再新增會到 10 張 ❌ (9 + 1 = 10，超限)
        // 邏輯：size >= 9 阻擋（已達上限）
        if (images.size() >= MAX_REVIEW_IMAGES) {
            throw new BusinessException(ErrorCode.E_1088,
                    String.format(ErrorCode.E_1088.getMessage(), images.size()));
        }

        images.add(imageUrl);
        review.setImages(images);
        review = reviewRepository.save(review);

        log.info("Image added to review: reviewId={}, imageUrl={}, totalImages={}",
                reviewId, imageUrl, images.size());

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 刪除評價的單張圖片
     *
     * @param reviewId 評價 ID
     * @param imageIndex 圖片 index（從 0 開始）
     * @return 更新後的評價
     */
    @Transactional
    public ReviewDto.ReviewResponse removeImage(UUID reviewId, int imageIndex) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 權限檢查
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1091, "You can only remove images from your own review");
        }

        List<String> images = review.getImages() != null ? new ArrayList<>(review.getImages()) : new ArrayList<>();

        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new BusinessException(ErrorCode.E_1090,
                    String.format(ErrorCode.E_1090.getMessage(), imageIndex));
        }

        String removedImage = images.remove(imageIndex);
        review.setImages(images);
        review = reviewRepository.save(review);

        log.info("Image removed from review: reviewId={}, imageIndex={}, removedImage={}, remaining={}",
                reviewId, imageIndex, removedImage, images.size());

        return toReviewResponse(review, review.getListing());
    }

    /**
     * 重新排序評價圖片
     *
     * @param reviewId 評價 ID
     * @param newImageUrls 新的圖片 URL 列表（順序為目標順序）
     * @return 更新後的評價
     */
    @Transactional
    public ReviewDto.ReviewResponse reorderImages(UUID reviewId, List<String> newImageUrls) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1087, "Review not found"));

        // 權限檢查
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1091, "You can only reorder images of your own review");
        }

        // 驗證數量
        validateImageCount(newImageUrls);

        // 驗證內容一致性：newImageUrls 必須是原圖片集合的排列
        List<String> currentImages = review.getImages() != null ? review.getImages() : List.of();
        if (newImageUrls == null || newImageUrls.size() != currentImages.size()) {
            throw new BusinessException(ErrorCode.E_1088,
                    String.format(ErrorCode.E_1088.getMessage(),
                            newImageUrls == null ? 0 : newImageUrls.size()));
        }

        // 確認是相同的圖片集合（透過 Set 比較）
        if (!new java.util.HashSet<>(newImageUrls).equals(new java.util.HashSet<>(currentImages))) {
            throw new BusinessException(ErrorCode.E_1089, "Reorder must contain the same set of images");
        }

        review.setImages(new ArrayList<>(newImageUrls));
        review = reviewRepository.save(review);

        log.info("Review images reordered: reviewId={}, count={}", reviewId, newImageUrls.size());

        return toReviewResponse(review, review.getListing());
    }

    // ========== Helper Methods ==========

    /**
     * 🔴 Sprint 16 US-005: 驗證圖片數量（最多 9 張）
     */
    private void validateImageCount(List<String> images) {
        if (images != null && images.size() > MAX_REVIEW_IMAGES) {
            throw new BusinessException(ErrorCode.E_1088,
                    String.format(ErrorCode.E_1088.getMessage(), images.size()));
        }
    }

    private ReviewDto.ReviewResponse toReviewResponse(Review review, Listing listing) {
        User user = review.getUser();

        return ReviewDto.ReviewResponse.builder()
                .reviewId(review.getId())
                .listingId(listing.getId())
                .listingTitle(listing.getTitle())
                .userId(review.getIsAnonymous() ? null : user.getId())
                .userFullName(review.getIsAnonymous() ? "Anonymous" : user.getFullName())
                .userAvatarUrl(review.getIsAnonymous() ? null : user.getAvatarUrl())
                .orderId(review.getOrderId())
                .bookingId(review.getBookingId())
                .reviewType(review.getReviewType().name())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .images(review.getImages())
                .helpfulCount(review.getHelpfulCount())
                .isAnonymous(review.getIsAnonymous())
                // sellerReply 已遷移到 ReviewReply 表（Sprint 16 US-001）
                // .sellerReply(review.getSellerReply())
                // .sellerRepliedAt(review.getSellerRepliedAt())
                .isHandled(review.getIsHandled())
                .handledAt(review.getHandledAt())
                .handledBy(review.getHandledBy() != null ? review.getHandledBy().getId() : null)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
