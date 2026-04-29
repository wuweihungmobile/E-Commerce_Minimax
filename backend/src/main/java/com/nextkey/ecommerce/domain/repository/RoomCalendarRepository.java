package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
     * 不帶鎖的查詢 - 用於檢查記錄是否存在（在事务外或需要讀取已提交數據時使用）
     */
    Optional<RoomCalendar> findByRoomListingIdAndCalendarDate(UUID roomListingId, LocalDate calendarDate);

    List<RoomCalendar> findByRoomListingIdAndCalendarDateBetween(UUID roomListingId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT c FROM RoomCalendar c WHERE c.roomListingId = :roomListingId " +
           "AND c.calendarDate BETWEEN :startDate AND :endDate " +
           "AND c.status != 'AVAILABLE'")
    List<RoomCalendar> findUnavailableDates(
            @Param("roomListingId") UUID roomListingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM RoomCalendar c WHERE c.roomListingId = :roomListingId " +
           "AND c.calendarDate >= :startDate " +
           "ORDER BY c.calendarDate ASC")
    List<RoomCalendar> findUpcomingDates(
            @Param("roomListingId") UUID roomListingId,
            @Param("startDate") LocalDate startDate);

    boolean existsByRoomListingIdAndCalendarDateBetweenAndStatusNot(
            UUID roomListingId,
            LocalDate startDate,
            LocalDate endDate,
            RoomCalendar.RoomCalendarStatus status);
}