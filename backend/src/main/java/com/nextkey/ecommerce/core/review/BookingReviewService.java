package com.nextkey.ecommerce.core.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.review.BookingReview;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 預訂評價服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingReviewService {

    private final BookingReviewRepository bookingReviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 建立預訂評價
     */
    @Transactional
    public BookingReview createBookingReview(UUID bookingId, Integer rating, String title,
                                              String content, List<String> images, Boolean isAnonymous) {
        UUID userId = TenantContext.getCurrentUser();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Booking not found"));

        // 檢查是否已評價
        if (bookingReviewRepository.findByBookingIdAndUserId(bookingId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.E_8000, "You have already reviewed this booking");
        }

        BookingReview review = BookingReview.builder()
                .booking(booking)
                .user(userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.E_1006, "User not found")))
                .rating(rating)
                .title(title)
                .content(content)
                .images(images)
                .isAnonymous(isAnonymous != null ? isAnonymous : false)
                .isVisible(true)
                .build();

        review = bookingReviewRepository.save(review);

        log.info("Booking review created: reviewId={}, bookingId={}, rating={}",
                review.getId(), bookingId, rating);

        return review;
    }

    /**
     * 房東回覆評價
     */
    @Transactional
    public BookingReview replyToBookingReview(UUID reviewId, String reply) {
        UUID userId = TenantContext.getCurrentUser();

        BookingReview review = bookingReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Booking review not found"));

        // 檢查是否是房東
        Booking booking = review.getBooking();
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "Only the host can reply");
        }

        review.setHostReply(reply);
        review.setHostRepliedAt(Instant.now());
        review = bookingReviewRepository.save(review);

        log.info("Host replied to booking review: reviewId={}", reviewId);

        return review;
    }

    /**
     * 取得預訂評價列表
     */
    @Transactional(readOnly = true)
    public Page<BookingReview> getBookingReviews(UUID bookingId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));
        return bookingReviewRepository.findByBookingIdAndIsVisibleTrueOrderByCreatedAtDesc(bookingId, pageRequest);
    }

    /**
     * 刪除評價（軟刪除）
     */
    @Transactional
    public void deleteBookingReview(UUID reviewId) {
        UUID userId = TenantContext.getCurrentUser();

        BookingReview review = bookingReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Booking review not found"));

        // 檢查是否是本人或是管理員
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "You can only delete your own review");
        }

        review.setIsVisible(false);
        bookingReviewRepository.save(review);

        log.info("Booking review deleted (soft): reviewId={}", reviewId);
    }
}