package com.nextkey.ecommerce.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.room.RoomCalendar;

@Repository
public interface RoomCalendarRepository extends JpaRepository<RoomCalendar, UUID> {

    /**
     * 使用 FOR UPDATE NOWAIT 鎖定特定的 RoomCalendar 記錄
     * 如果記錄已被鎖定，立即拋出錯誤而不是等待
     * 適用於並發 booking 衝突檢測
     */
    @Query(value = "SELECT * FROM room_calendar WHERE room_listing_id = :roomListingId AND calendar_date = :calendarDate FOR UPDATE NOWAIT",
           nativeQuery = true)
    Optional<RoomCalendar> findByRoomListingIdAndCalendarDateWithLockNowait(
            @Param("roomListingId") UUID roomListingId,
            @Param("calendarDate") LocalDate calendarDate);

    /**
     * 併發防護（DEF-134/135/136，RoomCalendarService.markMaintenance/releaseDateRange/
     * unmarkMaintenance）：以 {@code FOR UPDATE NOWAIT} 鎖住整段日期範圍的記錄，取代這三個
     * 方法原本呼叫 {@link #findByListingIdAndCalendarDateBetween} 不上鎖查詢後直接
     * 「讀狀態→條件式改欄位→save()」。這三個方法會被併發的 bookDateRange（本檔案既有的逐日
     * NOWAIT 鎖）、或彼此交錯呼叫時，可能造成同一 room_calendar 列的 status/booking_id 靜默
     * 遺失更新。比照本檔案既有的單日 NOWAIT 鎖模式，改為批次版本一次鎖住整段範圍。
     */
    @Query(value = "SELECT * FROM room_calendar WHERE room_listing_id = :roomListingId "
            + "AND calendar_date BETWEEN :startDate AND :endDate FOR UPDATE NOWAIT",
           nativeQuery = true)
    List<RoomCalendar> findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
            @Param("roomListingId") UUID roomListingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 不帶鎖的查詢 - 用於檢查記錄是否存在（在事务外或需要讀取已提交數據時使用）
     */
    @Query("SELECT c FROM RoomCalendar c WHERE c.listing.id = :listingId AND c.calendarDate = :calendarDate")
    Optional<RoomCalendar> findByListingIdAndCalendarDate(
            @Param("listingId") UUID listingId,
            @Param("calendarDate") LocalDate calendarDate);

    @Query("SELECT c FROM RoomCalendar c WHERE c.listing.id = :listingId AND c.calendarDate BETWEEN :startDate AND :endDate")
    List<RoomCalendar> findByListingIdAndCalendarDateBetween(
            @Param("listingId") UUID listingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    default List<RoomCalendar> findByRoomListingIdAndCalendarDateBetween(UUID roomListingId, LocalDate startDate, LocalDate endDate) {
        return findByListingIdAndCalendarDateBetween(roomListingId, startDate, endDate);
    }

    @Query("SELECT c FROM RoomCalendar c WHERE c.listing.id = :listingId " +
           "AND c.calendarDate BETWEEN :startDate AND :endDate " +
           "AND c.status != 'AVAILABLE'")
    List<RoomCalendar> findUnavailableDates(
            @Param("listingId") UUID listingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM RoomCalendar c WHERE c.listing.id = :listingId " +
           "AND c.calendarDate >= :startDate " +
           "ORDER BY c.calendarDate ASC")
    List<RoomCalendar> findUpcomingDates(
            @Param("listingId") UUID listingId,
            @Param("startDate") LocalDate startDate);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM RoomCalendar c " +
           "WHERE c.listing.id = :listingId AND c.calendarDate BETWEEN :startDate AND :endDate " +
           "AND c.status = :status")
    boolean existsByListingIdAndCalendarDateBetweenAndStatus(
            @Param("listingId") UUID listingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") RoomCalendar.RoomCalendarStatus status);

    List<RoomCalendar> findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus status);
}