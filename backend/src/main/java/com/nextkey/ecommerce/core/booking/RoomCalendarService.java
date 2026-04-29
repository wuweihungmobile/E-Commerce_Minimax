package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisLockService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
     * 注意：此方法使用 FOR UPDATE 鎖定日期記錄
     * 如果日期沒有記錄，視為 AVAILABLE，但如果有多個並發請求，
     * 只有第一個請求能成功創建預訂，其他請求會因為日期已被佔用而失敗
     */
    @Transactional
    public boolean isDateRangeAvailable(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        for (LocalDate date : dates) {
            // 使用 FOR UPDATE NOWAIT 嘗試立即獲取鎖
            // 如果記錄已被另一個事務鎖定，會立即拋出 PessimisticLockingFailureException
            try {
                var calendar = roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(roomListingId, date);
                if (calendar.isEmpty()) {
                    log.info("isDateRangeAvailable: Date {} has no calendar record, considered AVAILABLE", date);
                    continue;
                }
                RoomCalendar.RoomCalendarStatus status = calendar.get().getStatus();
                UUID bookingId = calendar.get().getBookingId();
                log.info("isDateRangeAvailable: Date {} has calendar with status={}, bookingId={}", date, status, bookingId);
                if (status != RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                    log.info("isDateRangeAvailable: Date {} is not AVAILABLE, status={}", date, status);
                    return false;
                }
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                // NOWAIT 失敗 - 記錄已被另一個事務鎖定，表示正在被處理
                log.warn("isDateRangeAvailable: Pessimistic lock failed for date {} - record is locked by another transaction", date);
                return false; // 視為不可用
            }
        }

        log.info("isDateRangeAvailable: All dates in range {} to {} are AVAILABLE", checkIn, checkOut);
        return true;
    }

    /**
     * 鎖定日期範圍（用於預訂流程）
     * 鎖粒度：每個日期單獨加鎖，確保同一房源同一日期只能有一個預訂操作
     * @return lockValue 如果成功，null 如果失敗（日期已被鎖）
     */
    public String lockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        // 使用 NO_WAIT 策略 - 不等待，嘗試立即獲取鎖
        return lockDateRangeNoWait(roomListingId, checkIn, checkOut);
    }

    /**
     * 鎖定日期範圍（不等待策略）
     * 如果無法立即獲取所有日期的鎖，則立即返回失敗
     * @return lockValue 如果成功，null 如果失敗（無法立即獲取鎖）
     */
    public String lockDateRangeNoWait(UUID roomListingId, LocalDate checkIn, LocalDate checkOut) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        // 使用統一的 lock key（包含所有日期）
        String unifiedKey = "booking:lock:room:" + roomListingId + ":dates:" + checkIn + ":" + checkOut;

        log.info("lockDateRangeNoWait: Trying to acquire locks for {} dates (NO WAIT)", dates.size());

        // 對每個日期嘗試立即獲取鎖（不等待）
        List<String> acquiredLocks = new ArrayList<>();
        for (LocalDate date : dates) {
            String dateLockKey = "room:" + roomListingId + ":date:" + date;
            String lockValue = redisLockService.tryAcquireLockNoWait(dateLockKey);
            if (lockValue == null) {
                // 獲取失敗，釋放已獲取的所有鎖並立即返回
                log.warn("lockDateRangeNoWait: Failed to acquire lock for date: {}, releasing all acquired locks and returning null", date);
                for (String acquiredKey : acquiredLocks) {
                    redisLockService.forceReleaseLock(acquiredKey); // acquiredKey already includes "lock:" prefix via tryAcquireLockNoWait
                }
                return null; // 立即返回，不等待
            }
            // Store the full key WITH lock: prefix (same as what forceReleaseLock expects)
            acquiredLocks.add("lock:" + dateLockKey);
            log.debug("lockDateRangeNoWait: Acquired lock for date {}", date);
        }

        log.info("lockDateRangeNoWait: Successfully acquired all locks for {}", unifiedKey);
        return unifiedKey;
    }

    /**
     * 鎖定日期範圍（帶超時等待）
     * @param maxWaitSeconds 每個鎖的最大等待時間
     * @return lockValue 如果成功，null 如果失敗（超時）
     */
    public String lockDateRangeWithWait(UUID roomListingId, LocalDate checkIn, LocalDate checkOut, long maxWaitSeconds) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        // 使用統一的 lock key（包含所有日期）
        String unifiedKey = "booking:lock:room:" + roomListingId + ":dates:" + checkIn + ":" + checkOut;

        log.info("lockDateRangeWithWait: Trying to acquire locks for {} dates", dates.size());

        // 對每個日期嘗試獲取鎖（帶等待）
        for (LocalDate date : dates) {
            String dateLockKey = "room:" + roomListingId + ":date:" + date;
            String lockValue = redisLockService.tryAcquireLockWithWaitlong(dateLockKey, maxWaitSeconds);
            if (lockValue == null) {
                // 獲取失敗，釋放已獲取的所有鎖
                log.warn("lockDateRangeWithWait: Failed to acquire lock for date: {}, releasing all acquired locks", date);
                for (LocalDate acquiredDate : dates) {
                    if (acquiredDate.isBefore(date)) {
                        String acquiredKey = "lock:room:" + roomListingId + ":date:" + acquiredDate;
                        redisLockService.forceReleaseLock(acquiredKey);
                    }
                }
                return null;
            }
            log.debug("lockDateRangeWithWait: Acquired lock for date {}", date);
        }

        log.info("lockDateRangeWithWait: Successfully acquired all locks for {}", unifiedKey);
        return unifiedKey;
    }

    /**
     * 釋放日期範圍鎖
     */
    public void unlockDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut, String lockValue) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        // 釋放所有日期的鎖
        for (LocalDate date : dates) {
            String dateLockKey = "lock:room:" + roomListingId + ":date:" + date;
            redisLockService.forceReleaseLock(dateLockKey);
            log.debug("Released lock for date: {}", date);
        }

        // 釋放統一的 lock key（用於追蹤）
        String unifiedKey = "lock:booking:lock:room:" + roomListingId + ":dates:" + checkIn + ":" + checkOut;
        redisLockService.forceReleaseLock(unifiedKey);
    }

    /**
     * 預訂日期（標記為已預訂）
     * 注意：此方法使用 FOR UPDATE 鎖確保並發安全
     */
    @Transactional
    public void bookDateRange(UUID roomListingId, LocalDate checkIn, LocalDate checkOut, UUID bookingId) {
        List<LocalDate> dates = generateDateRange(checkIn, checkOut.minusDays(1));

        for (LocalDate date : dates) {
            // 使用 FOR UPDATE NOWAIT 嘗試立即獲取鎖
            // 如果記錄已被另一個事務鎖定，會立即拋出 org.springframework.dao.PessimisticLockingFailureException
            try {
                var calendarOpt = roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(roomListingId, date);

                log.info("bookDateRange: Querying date {} for room {}, result is empty: {}", date, roomListingId, calendarOpt.isEmpty());

                if (calendarOpt.isEmpty()) {
                    // 如果沒有記錄，嘗試創建新的預訂記錄
                    try {
                        RoomCalendar calendar = createCalendarEntry(roomListingId, date);
                        calendar.setStatus(RoomCalendar.RoomCalendarStatus.BOOKED);
                        calendar.setBookingId(bookingId);
                        roomCalendarRepository.saveAndFlush(calendar);
                        log.info("bookDateRange: Created new BOOKED entry for date {} with bookingId {}", date, bookingId);
                    } catch (DataIntegrityViolationException e) {
                        // 如果因為 unique constraint 失敗，說明另一個請求已經創建了記錄
                        log.warn("bookDateRange: Unique constraint violation for date {}, another booking was created", date);
                        throw new BusinessException(ErrorCode.E_4001,
                                "Date " + date + " is already booked (concurrent booking)");
                    }
                } else {
                    RoomCalendar calendar = calendarOpt.get();
                    RoomCalendar.RoomCalendarStatus status = calendar.getStatus();

                    log.info("bookDateRange: Date {} has existing calendar with status={}, bookingId={}", date, status, calendar.getBookingId());

                    // 如果已經是 BOOKED 狀態，檢查是否是同一個 booking
                    if (status == RoomCalendar.RoomCalendarStatus.BOOKED) {
                        if (calendar.getBookingId() != null && calendar.getBookingId().equals(bookingId)) {
                            // 同一個 booking，idempotent 操作
                            log.debug("bookDateRange: Date {} already booked by same booking {}, skipping", date, bookingId);
                            continue;
                        }
                        // 不同 booking，衝突
                        log.warn("bookDateRange: Date {} is already booked by booking {}", date, calendar.getBookingId());
                        throw new BusinessException(ErrorCode.E_4001,
                                "Date " + date + " is already booked by another booking");
                    }

                    // 如果不是 BOOKED 狀態，更新為 BOOKED
                    calendar.setStatus(RoomCalendar.RoomCalendarStatus.BOOKED);
                    calendar.setBookingId(bookingId);
                    roomCalendarRepository.save(calendar);
                    log.info("bookDateRange: Updated date {} to BOOKED for booking {}", date, bookingId);
                }
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                // NOWAIT 失敗 - 記錄已被另一個事務鎖定
                log.warn("bookDateRange: Pessimistic lock failed for date {} - record is locked by another transaction", date);
                throw new BusinessException(ErrorCode.E_4001,
                        "Date " + date + " is currently being booked by another user");
            }
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