package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.order.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * 併發防護（Sprint 221）：條件式狀態轉換（{@code WHERE status = expectedStatus}），取代「讀狀態→判斷→
     * setStatus→save」。與 {@code OrderRepository.updateStatusIfCurrent} 同一模式。回傳受影響列數：1 代表本次成功轉換，
     * 0 代表狀態已被另一併發呼叫搶先轉換，呼叫端應拒絕本次請求（同一筆訂房被併發付款兩次，僅一邊該視為成功）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.status = :newStatus WHERE b.id = :id AND b.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") Booking.BookingStatus expectedStatus,
            @Param("newStatus") Booking.BookingStatus newStatus);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(UUID bookingId, UUID userId);

    Optional<Booking> findByIdAndTenantId(UUID bookingId, UUID tenantId);

    boolean existsByUserIdAndStatusNotIn(UUID userId, List<Booking.BookingStatus> statuses);
}