package com.nextkey.ecommerce.core.review;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.ReviewDto;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    // Pagination default
    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 建立評價
     */
    @Transactional
    public ReviewDto.ReviewResponse createReview(ReviewDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        // 檢查 Listing 是否存在
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Listing not found"));

        // 檢查是否已評價
        if (request.getOrderId() != null) {
            if (reviewRepository.findByOrderIdAndListingId(request.getOrderId(), request.getListingId()).isPresent()) {
                throw new BusinessException(ErrorCode.E_8000, "You have already reviewed this item");
            }
        }

        // 檢查是否已預訂評價
        if (request.getBookingId() != null) {
            if (reviewRepository.findByBookingIdAndListingId(request.getBookingId(), request.getListingId()).isPresent()) {
                throw new BusinessException(ErrorCode.E_8000, "You have already reviewed this booking");
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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Review not found"));

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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Review not found"));

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
                .averageRating(avgRating != null ? avgRating : 0.0)
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
     * 賣家回覆
     */
    @Transactional
    public ReviewDto.ReviewResponse replyToReview(UUID reviewId, ReviewDto.SellerReplyRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Review not found"));

        // 檢查是否是 Listing 的擁有者
        Listing listing = review.getListing();
        if (listing.getTenantId() == null || !listing.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "Only the listing owner can reply");
        }

        review.setSellerReply(request.getReply());
        review.setSellerRepliedAt(Instant.now());
        review = reviewRepository.save(review);

        log.info("Seller replied to review: reviewId={}", reviewId);

        return toReviewResponse(review, listing);
    }

    /**
     * 標記為有帮助
     */
    @Transactional
    public ReviewDto.ReviewResponse markHelpful(UUID reviewId) {
        UUID userId = TenantContext.getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Review not found"));

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

    // ========== Helper Methods ==========

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
                .sellerReply(review.getSellerReply())
                .sellerRepliedAt(review.getSellerRepliedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
