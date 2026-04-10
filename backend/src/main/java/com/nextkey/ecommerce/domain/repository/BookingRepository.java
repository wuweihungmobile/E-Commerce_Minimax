package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.order.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(UUID bookingId, UUID userId);

    Optional<Booking> findByIdAndTenantId(UUID bookingId, UUID tenantId);
}