package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingReviewDto;
import com.nextkey.ecommerce.core.review.BookingReviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 預訂評價 REST API (BookingReview)
 */
@Slf4j
@RestController
@RequestMapping("/v2/booking-reviews")
@RequiredArgsConstructor
public class BookingReviewController {

    private final BookingReviewService bookingReviewService;

    /**
     * 建立預訂評價
     */
    @PostMapping
    @PreAuthorize("hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<BookingReviewDto.BookingReviewResponse>> createBookingReview(
            @Valid @RequestBody BookingReviewDto.CreateRequest request) {
        log.info("Create booking review: bookingId={}, rating={}", request.getBookingId(), request.getRating());
        BookingReviewDto.BookingReviewResponse response = bookingReviewService.createBookingReview(
                request.getBookingId(),
                request.getRating(),
                request.getTitle(),
                request.getContent(),
                request.getImages(),
                request.getIsAnonymous()
        );
        return ResponseEntity.ok(ApiResponse.success("Booking review created successfully", response));
    }

    /**
     * 房東/店主回覆預訂評價
     */
    @PostMapping("/{reviewId}/reply")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('booking:update')")
    public ResponseEntity<ApiResponse<BookingReviewDto.BookingReviewResponse>> replyToBookingReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody BookingReviewDto.HostReplyRequest request) {
        log.info("Host reply to booking review: reviewId={}", reviewId);
        BookingReviewDto.BookingReviewResponse response = bookingReviewService.replyToBookingReview(
                reviewId, request.getReply());
        return ResponseEntity.ok(ApiResponse.success("Reply added successfully", response));
    }

    /**
     * 取得預訂評價詳情
     */
    @GetMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingReviewDto.BookingReviewResponse>> getBookingReview(
            @PathVariable UUID reviewId) {
        BookingReviewDto.BookingReviewResponse response = bookingReviewService.getBookingReviewById(reviewId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}