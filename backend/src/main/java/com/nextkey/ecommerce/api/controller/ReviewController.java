package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.review.ReviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



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

    /**
     * 標記評價為已處理
     */
    @PutMapping("/{reviewId}/handle")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> markAsHandled(
            @PathVariable UUID reviewId,
            @RequestParam(defaultValue = "true") Boolean handled) {
        log.info("Mark review as handled: reviewId={}, handled={}", reviewId, handled);
        ReviewDto.ReviewResponse response;
        if (handled) {
            response = reviewService.markAsHandled(reviewId);
        } else {
            response = reviewService.markAsUnhandled(reviewId);
        }
        return ResponseEntity.ok(ApiResponse.success("Review handling status updated", response));
    }

    /**
     * 根據處理狀態取得評價列表
     */
    @GetMapping("/managed")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewListResponse>> getReviewsByHandlingStatus(
            @RequestParam(required = false, defaultValue = "false") Boolean isHandled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ReviewDto.ReviewListResponse response = reviewService.getReviewsByHandlingStatus(isHandled, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
