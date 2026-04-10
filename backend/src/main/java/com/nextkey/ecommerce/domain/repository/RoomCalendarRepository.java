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