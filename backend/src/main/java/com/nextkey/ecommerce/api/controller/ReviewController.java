package com.nextkey.ecommerce.api.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.nextkey.ecommerce.api.dto.ReviewReplyDto;
import com.nextkey.ecommerce.api.dto.ReviewSearchCriteria;
import com.nextkey.ecommerce.core.review.ReviewReplyService;
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
    private final ReviewReplyService reviewReplyService;

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
     * 多維度評價搜尋（Sprint 18 US-003）
     *
     * 支援條件：
     * - keyword: 關鍵字（搜尋標題或內容）
     * - minRating/maxRating: 評分範圍
     * - startDate/endDate: 日期範圍 (ISO-8601)
     * - hasImages: 是否有圖片
     * - hasReply: 是否有商家回覆
     * - sortBy: 排序欄位 (createdAt/rating/helpfulCount)
     * - sortDir: 排序方向 (ASC/DESC)
     */
    @GetMapping("/listing/{listingId}/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewListResponse>> searchReviews(
            @PathVariable UUID listingId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) Boolean hasImages,
            @RequestParam(required = false) Boolean hasReply,
            @RequestParam(required = false) ReviewSearchCriteria.SortBy sortBy,
            @RequestParam(required = false) ReviewSearchCriteria.SortDir sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Search reviews: listingId={}, keyword={}, minRating={}, maxRating={}, hasImages={}, hasReply={}",
                listingId, keyword, minRating, maxRating, hasImages, hasReply);

        ReviewSearchCriteria criteria = ReviewSearchCriteria.builder()
                .listingId(listingId)
                .keyword(keyword)
                .minRating(minRating)
                .maxRating(maxRating)
                .startDate(startDate)
                .endDate(endDate)
                .hasImages(hasImages)
                .hasReply(hasReply)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .page(page)
                .size(size)
                .build();

        ReviewDto.ReviewListResponse response = reviewService.searchReviews(criteria);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 賣家回覆（新路徑，推薦使用）
     *
     * Sprint 16 US-001: 委派給 ReviewReplyService 處理（從 ReviewService 拆分）
     */
    @PostMapping("/{reviewId}/replies")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ReviewReplyDto.ReplyResponse>> replyToReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewReplyDto.CreateReplyRequest request) {
        log.info("Seller reply to review: reviewId={}", reviewId);
        ReviewReplyDto.ReplyResponse response = reviewReplyService.createReply(reviewId, request);
        return ResponseEntity.ok(ApiResponse.success("Reply added successfully", response));
    }

    /**
     * 取得評價的所有回覆（Sprint 16 US-001）
     */
    @GetMapping("/{reviewId}/replies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewReplyDto.ReplyListResponse>> getReviewReplies(
            @PathVariable UUID reviewId) {
        List<ReviewReplyDto.ReplyResponse> replies = reviewReplyService.getRepliesByReviewId(reviewId);
        ReviewReplyDto.ReplyListResponse response = ReviewReplyDto.ReplyListResponse.builder()
                .replies(replies)
                .totalCount(replies.size())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
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

    // ========== 圖片管理 API（Sprint 16 US-006） ==========

    /**
     * 新增單張圖片到評價
     */
    @PostMapping("/{reviewId}/images")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> addReviewImage(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDto.AddImageRequest request) {
        log.info("Add image to review: reviewId={}, imageUrl={}", reviewId, request.getImageUrl());
        ReviewDto.ReviewResponse response = reviewService.addImage(reviewId, request.getImageUrl());
        return ResponseEntity.ok(ApiResponse.success("Image added successfully", response));
    }

    /**
     * 刪除評價的單張圖片（依 index）
     */
    @DeleteMapping("/{reviewId}/images/{imageIndex}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> removeReviewImage(
            @PathVariable UUID reviewId,
            @PathVariable int imageIndex) {
        log.info("Remove image from review: reviewId={}, imageIndex={}", reviewId, imageIndex);
        ReviewDto.ReviewResponse response = reviewService.removeImage(reviewId, imageIndex);
        return ResponseEntity.ok(ApiResponse.success("Image removed successfully", response));
    }

    /**
     * 重新排序評價圖片
     */
    @PutMapping("/{reviewId}/images/order")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<ReviewDto.ReviewResponse>> reorderReviewImages(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDto.ReorderImagesRequest request) {
        log.info("Reorder images of review: reviewId={}, newCount={}", reviewId,
                request.getImageUrls() != null ? request.getImageUrls().size() : 0);
        ReviewDto.ReviewResponse response = reviewService.reorderImages(reviewId, request.getImageUrls());
        return ResponseEntity.ok(ApiResponse.success("Images reordered successfully", response));
    }
}
