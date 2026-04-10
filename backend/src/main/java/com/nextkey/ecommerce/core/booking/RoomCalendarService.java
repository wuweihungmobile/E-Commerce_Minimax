package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 房源日曆服務
 * 處理房源的可用日期和定價
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCalendarService {

    private final RoomCalendarRepository roomCalendarRepository;
    private final RedisLockService redisLockService;

    private static final int CALENDAR_GENERATE_DAYS = 365; // 生成未來 365 天

    /**
     * 檢查日期範圍是否可用
     */
    public boolean isDateRangeAvailable(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<RoomCalendar> calendars = roomCalendarRepository
                .findByRoomListingIdAndCalendarDateBetween(roomListingId, checkIn, checkOut.minusDays(1));

        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() != RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                return false;
            }
        }

        return true;
    }

    /**
     * 鎖定日期範圍（用於預訂流程）
     * @return lockValue 如果成功，null 如果失敗（日期已被鎖）
     */
    public String lockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        String lockKey = "room:" + roomListingId + ":dates:" + checkIn + ":" + checkOut;
        return redisLockService.tryAcquireLockWithWait(lockKey, 10);
    }

    /**
     * 釋放日期範圍鎖
     */
    public void unlockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut, String lockValue) {
        String lockKey = "room:" + roomListingId + ":dates:" + checkIn + ":" + checkOut;
        redisLockService.releaseLock(lockKey, lockValue);
    }

    /**
     * 預訂日期（標記為已預訂）
     */
    @Transactional
    public void bookDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut, UUID bookingId) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        for (LocalDate date : dates) {
            RoomCalendar calendar = roomCalendarRepository
                    .findByRoomListingIdAndCalendarDate(roomListingId, date)
                    .orElseGet(() -> createCalendarEntry(roomListingId, date));

            if (calendar.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED) {
                throw new IllegalStateException("Date already booked: " + date);
            }

            calendar.setStatus(RoomCalendar.RoomCalendarStatus.BOOKED);
            calendar.setBookingId(bookingId);
            roomCalendarRepository.save(calendar);
        }

        log.info("Booked date range: room={}, checkIn={}, checkOut={}, bookingId={}",
                roomListingId, checkIn, checkOut, bookingId);
    }

    /**
     * 釋放預訂（取消預訂時呼叫）
     */
    @Transactional
    public void releaseDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<RoomCalendar> calendars = roomCalendarRepository
                .findByRoomListingIdAndCalendarDateBetween(roomListingId, checkIn, checkOut.minusDays(1));

        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED) {
                calendar.setStatus(RoomCalendar.RoomCalendarStatus.AVAILABLE);
                calendar.setBookingId(null);
                roomCalendarRepository.save(calendar);
            }
        }

        log.info("Released date range: room={}, checkIn={}, checkOut={}", roomListingId, checkIn, checkOut);
    }

    /**
     * 取得房源日曆（某段時間範圍）
     */
    @Transactional(readOnly = true)
    public List<RoomCalendar> getCalendarRange(UUID roomListingId, LocalDate start, LocalDate end) {
        return roomCalendarRepository.findByRoomListingIdAndCalendarDateBetween(roomListingId, start, end);
    }

    /**
     * 設定日期價格
     */
    @Transactional
    public void setDatePrice(UUID roomListingId, LocalDate date, BigDecimal price) {
        RoomCalendar calendar = roomCalendarRepository
                .findByRoomListingIdAndCalendarDate(roomListingId, date)
                .orElseGet(() -> createCalendarEntry(roomListingId, date));

        calendar.setPrice(price);
        roomCalendarRepository.save(calendar);

        log.info("Set date price: room={}, date={}, price={}", roomListingId, date, price);
    }

    /**
     * 批次設定日期價格
     */
    @Transactional
    public void setDatePriceBulk(UUID roomListingId, List<LocalDate> dates, BigDecimal price) {
        for (LocalDate date : dates) {
            setDatePrice(roomListingId, date, price);
        }
    }

    /**
     * 封鎖日期（不可預訂）
     */
    @Transactional
    public void blockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<RoomCalendar> calendars = roomCalendarRepository
                .findByRoomListingIdAndCalendarDateBetween(roomListingId, checkIn, checkOut.minusDays(1));

        for (RoomCalendar calendar : calendars) {
            calendar.setStatus(RoomCalendar.RoomCalendarStatus.BLOCKED);
            roomCalendarRepository.save(calendar);
        }

        log.info("Blocked date range: room={}, checkIn={}, checkOut={}", roomListingId, checkIn, checkOut);
    }

    /**
     * 取消封鎖日期
     */
    @Transactional
    public void unblockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<RoomCalendar> calendars = roomCalendarRepository
                .findByRoomListingIdAndCalendarDateBetween(roomListingId, checkIn, checkOut.minusDays(1));

        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() == RoomCalendar.RoomCalendarStatus.BLOCKED) {
                calendar.setStatus(RoomCalendar.RoomCalendarStatus.AVAILABLE);
                roomCalendarRepository.save(calendar);
            }
        }

        log.info("Unblocked date range: room={}, checkIn={}, checkOut={}", roomListingId, checkIn, checkOut);
    }

    /**
     * 生成日期列表
     */
    private List<LocalDate> generateDateRange(LocalDate start, LocalDate end) {
        return start.datesUntil(end.plusDays(1))
                .collect(Collectors.toList());
    }

    /**
     * 建立日曆條目
     */
    private RoomCalendar createCalendarEntry(UUID roomListingId, LocalDate date) {
        return RoomCalendar.builder()
                .roomListingId(roomListingId)
                .calendarDate(date)
                .status(RoomCalendar.RoomCalendarStatus.AVAILABLE)
                .build();
    }
}