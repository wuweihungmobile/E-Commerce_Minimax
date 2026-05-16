package com.nextkey.ecommerce.domain.repository.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.review.BookingReview;

@Repository
public interface BookingReviewRepository extends JpaRepository<BookingReview, UUID> {

    Page<BookingReview> findByBookingIdAndIsVisibleTrueOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<BookingReview> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<BookingReview> findByBookingId(UUID bookingId);

    Optional<BookingReview> findByBookingIdAndUserId(UUID bookingId, UUID userId);

    @Query("SELECT AVG(br.rating) FROM BookingReview br WHERE br.bookingId = :bookingId AND br.isVisible = true")
    Double getAverageRatingByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT COUNT(br) FROM BookingReview br WHERE br.bookingId = :bookingId AND br.isVisible = true")
    int countByBookingId(@Param("bookingId") UUID bookingId);
}