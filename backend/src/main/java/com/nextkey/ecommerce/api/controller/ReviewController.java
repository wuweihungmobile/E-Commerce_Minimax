package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 評價 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 建立評價
     */
    @PostMapping
    @PreAuthorize("hasAuthority('order:create') or hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> createReview(
            @Valid @RequestBody ReviewDto.CreateRequest request) {
        log.info("Create review: listingId={}, rating={}", request.getListingId(), request.getRating());
        ReviewDto.ReviewResponse response = reviewService.createReview(request);
        return ResponseEntity.ok(ApiResponse.success("Review created successfully", response));
    }

    /**
     * 更新評價
     */
    @PutMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDto.UpdateRequest request) {
        log.info("Update review: reviewId={}", reviewId);
        ReviewDto.ReviewResponse response = reviewService.updateReview(reviewId, request);
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully", response));
    }

    /**
     * 刪除評價
     */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID reviewId) {
        log.info("Delete review: reviewId={}", reviewId);
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }

    /**
     * 取得 Listing 評價列表
     */
    @GetMapping("/listing/{listingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewListResponse>> getReviewsByListing(
            @PathVariable UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer minRating) {
        ReviewDto.ReviewListResponse response = reviewService.getReviewsByListingId(listingId, page, size, minRating);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得用戶評價列表
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewListResponse>> getUserReviews(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ReviewDto.ReviewListResponse response = reviewService.getUserReviews(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得評價統計
     */
    @GetMapping("/listing/{listingId}/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDto.RatingStats>> getRatingStats(
            @PathVariable UUID listingId) {
        ReviewDto.RatingStats response = reviewService.getRatingStats(listingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 賣家回覆
     */
    @PostMapping("/{reviewId}/reply")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> replyToReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDto.SellerReplyRequest request) {
        log.info("Seller reply to review: reviewId={}", reviewId);
        ReviewDto.ReviewResponse response = reviewService.replyToReview(reviewId, request);
        return ResponseEntity.ok(ApiResponse.success("Reply added successfully", response));
    }

    /**
     * 標記為有幫助
     */
    @PostMapping("/{reviewId}/helpful")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> markHelpful(@PathVariable UUID reviewId) {
        ReviewDto.ReviewResponse response = reviewService.markHelpful(reviewId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
