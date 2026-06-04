package com.nextkey.ecommerce.domain.repository;

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
}
