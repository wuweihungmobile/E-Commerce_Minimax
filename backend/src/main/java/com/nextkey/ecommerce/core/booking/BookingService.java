package com.nextkey.ecommerce.core.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.order.OrderStateMachine;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentTenant;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 預訂服務
 * 處理民宿預訂的建立、更新、取消等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final RoomRepository roomRepository;
    @SuppressWarnings("unused")
    private final RoomCalendarRepository roomCalendarRepository;
    private final RoomCalendarService roomCalendarService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    // Default check-in/out times
    private static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(15, 0);
    private static final LocalTime DEFAULT_CHECK_OUT_TIME = LocalTime.of(11, 0);

    /**
     * 檢查日期範圍可用性
     */
    public BookingDto.AvailabilityResponse checkAvailability(BookingDto.AvailabilityRequest request) {
        Listing listing = listingRepository.findById(request.getRoomListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000));

        if (listing.getListingType() != Listing.ListingType.ROOM) {
            throw new BusinessException(ErrorCode.E_3001, "Listing is not a room");
        }

        // 檢查日期範圍
        if (request.getCheckOutDate().isBefore(request.getCheckInDate()) ||
            request.getCheckOutDate().isEqual(request.getCheckInDate())) {
            return BookingDto.AvailabilityResponse.builder()
                    .available(false)
                    .roomListingId(request.getRoomListingId())
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .unavailableReason("Check-out must be after check-in")
                    .build();
        }

        // 檢查每個日期是否可用
        List<RoomCalendar> calendars = roomCalendarService.getCalendarRange(
                request.getRoomListingId(),
                request.getCheckInDate(),
                request.getCheckOutDate().minusDays(1)
        );

        boolean available = true;
        String unavailableReason = null;
        BigDecimal totalPrice = BigDecimal.ZERO;

        List<BookingDto.CalendarResponse> calendarDetails = calendars.stream()
                .map(c -> BookingDto.CalendarResponse.builder()
                        .date(c.getCalendarDate())
                        .status(c.getStatus().name())
                        .price(c.getPrice() != null ? c.getPrice() : listing.getBasePrice())
                        .bookingId(c.getBookingId())
                        .build())
                .collect(Collectors.toList());

        // 如果日曆日期不夠，檢查是否有 BLOCKED 狀態
        long expectedDays = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        if (calendars.size() < expectedDays) {
            // 有些日期沒有日曆記錄，預設為 AVAILABLE
            for (long i = 0; i < expectedDays; i++) {
                LocalDate date = request.getCheckInDate().plusDays(i);
                boolean found = calendars.stream().anyMatch(c -> c.getCalendarDate().equals(date));
                if (!found) {
                    // 預設價格
                    totalPrice = totalPrice.add(listing.getBasePrice());
                }
            }
        }

        // 檢查是否有不可用的日期
        available = checkCalendarAvailability(calendars, listing, totalPrice);
        if (!available) {
            unavailableReason = findFirstUnavailableReason(calendars);
        }

        long nightsCount = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());

        return BookingDto.AvailabilityResponse.builder()
                .available(available)
                .roomListingId(request.getRoomListingId())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .nightsCount((int) nightsCount)
                .totalPrice(totalPrice)
                .currency(listing.getCurrency())
                .calendarDetails(available ? calendarDetails : null)
                .unavailableReason(unavailableReason)
                .build();
    }

    private boolean checkCalendarAvailability(List<RoomCalendar> calendars, Listing listing, BigDecimal totalPrice) {
        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() != RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                return false;
            }
            totalPrice.add(calendar.getPrice() != null ? calendar.getPrice() : listing.getBasePrice());
        }
        return true;
    }

    private String findFirstUnavailableReason(List<RoomCalendar> calendars) {
        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() != RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                return "Date " + calendar.getCalendarDate() + " is " + calendar.getStatus().name().toLowerCase();
            }
        }
        return null;
    }

    /**
     * 建立預訂
     */
    @Transactional
    public BookingDto.BookingResponse createBooking(BookingDto.CreateRequest request, String idempotencyKey) {
        UUID userId = getCurrentUser();
        UUID tenantId = getCurrentTenant();

        // 驗證房源
        Listing listing = listingRepository.findById(request.getRoomListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Room not found"));

        if (listing.getListingType() != Listing.ListingType.ROOM) {
            throw new BusinessException(ErrorCode.E_3001, "Listing is not a room");
        }

        if (!"ACTIVE".equals(listing.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_3002, "Room not active");
        }

        Room room = roomRepository.findByListingId(request.getRoomListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Room data not found"));

        // 檢查客人數量
        if (request.getGuestCount() > room.getMaxGuests()) {
            throw new BusinessException(ErrorCode.E_4005, "Guest count exceeds capacity: max " + room.getMaxGuests());
        }

        // 檢查日期
        if (request.getCheckOutDate().isBefore(request.getCheckInDate())) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }

        // 嘗試鎖定日期範圍（NO_WAIT 策略，立即返回）
        // 如果無法立即獲取鎖，表示有並發請求在處理，拋出衝突異常
        String lockValue = roomCalendarService.lockDateRange(
                request.getRoomListingId(),
                request.getCheckInDate(),
                request.getCheckOutDate()
        );

        if (lockValue == null) {
            throw new BusinessException(ErrorCode.E_4001, "Date range is being modified by another user");
        }

        BookingDto.BookingResponse response;
        try {
            // 檢查可用性
            if (!roomCalendarService.isDateRangeAvailable(
                    request.getRoomListingId(),
                    request.getCheckInDate(),
                    request.getCheckOutDate())) {
                throw new BusinessException(ErrorCode.E_4001, "Date range no longer available");
            }

            // 取得用戶和租戶
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
            var tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

            // 計算晚數和總金額
            long nightsCount = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
            BigDecimal totalAmount = calculateTotalAmount(
                    request.getRoomListingId(),
                    request.getCheckInDate(),
                    request.getCheckOutDate()
            );

            // 建立預訂
            var booking = com.nextkey.ecommerce.domain.model.order.Booking.builder()
                    .tenant(tenant)
                    .user(user)
                    .roomListing(listing)
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .guestCount(request.getGuestCount())
                    .status(com.nextkey.ecommerce.domain.model.order.Booking.BookingStatus.CREATED)
                    .totalAmount(totalAmount)
                    .guestName(request.getGuestName())
                    .guestPhone(request.getGuestPhone())
                    .guestEmail(request.getGuestEmail())
                    .specialRequests(request.getSpecialRequests())
                    .build();

            booking = bookingRepository.save(booking);

            // 更新日曆（這裡會拋出異常如果日期已被預訂）
            roomCalendarService.bookDateRange(
                    request.getRoomListingId(),
                    request.getCheckInDate(),
                    request.getCheckOutDate(),
                    booking.getId()
            );

            log.info("Booking created: id={}, user={}, room={}, checkIn={}, checkOut={}",
                    booking.getId(), userId, request.getRoomListingId(),
                    request.getCheckInDate(), request.getCheckOutDate());

            response = toBookingResponse(booking, listing, room, nightsCount);

            // 強制 flush 確保數據庫變更被 commit
            bookingRepository.flush();

        } finally {
            // 釋放鎖（在 flush/commit 完成之後）
            roomCalendarService.unlockDateRange(
                    request.getRoomListingId(),
                    request.getCheckInDate(),
                    request.getCheckOutDate(),
                    lockValue
            );
        }

        return response;
    }

    /**
     * 取得用戶預訂列表
     */
    @Transactional(readOnly = true)
    public Page<BookingDto.BookingListResponse> getUserBookings(int page, int size, String sortBy, String sortDir) {
        UUID userId = getCurrentUser();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        Page<com.nextkey.ecommerce.domain.model.order.Booking> bookings =
                bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        // 批次查詢房型標題，避免 N+1（逐筆查 listing）
        List<UUID> listingIds = bookings.getContent().stream()
                .map(com.nextkey.ecommerce.domain.model.order.Booking::getRoomListingId)
                .distinct()
                .collect(Collectors.toList());
        Map<UUID, String> titleMap = listingRepository.findAllById(listingIds).stream()
                .collect(Collectors.toMap(
                        Listing::getId,
                        listing -> listing.getTitle() != null ? listing.getTitle() : "Unknown"));

        return bookings.map(booking ->
                toBookingListResponse(booking, titleMap.get(booking.getRoomListingId())));
    }

    /**
     * 取得預訂詳情
     */
    @Transactional(readOnly = true)
    public BookingDto.BookingResponse getBooking(UUID bookingId) {
        com.nextkey.ecommerce.domain.model.order.Booking booking = findBookingById(bookingId);
        Listing listing = listingRepository.findById(booking.getRoomListingId()).orElse(null);
        Room room = listing != null ? roomRepository.findByListingId(listing.getId()).orElse(null) : null;
        long nightsCount = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        return toBookingResponse(booking, listing, room, nightsCount);
    }

    /**
     * 更新預訂
     */
    @Transactional
    public BookingDto.BookingResponse updateBooking(UUID bookingId, BookingDto.UpdateRequest request) {
        com.nextkey.ecommerce.domain.model.order.Booking booking = findBookingById(bookingId);

        // 檢查是否可更新
        if (booking.getStatus() != com.nextkey.ecommerce.domain.model.order.Booking.BookingStatus.CREATED &&
            booking.getStatus() != com.nextkey.ecommerce.domain.model.order.Booking.BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.E_5010, "Booking cannot be updated in current status");
        }

        // 如果更改日期，需要釋放舊日期並預訂新日期
        boolean dateChanged = false;
        if (request.getCheckInDate() != null || request.getCheckOutDate() != null) {
            dateChanged = handleDateChange(booking, request);
        }

        // 更新其他欄位
        updateBookingFields(booking, request);

        // 如果日期變更，重新計算金額
        if (dateChanged) {
            recalculateAndBookDateRange(booking);
        }

        booking = bookingRepository.save(booking);
        log.info("Booking updated: id={}", bookingId);

        return buildBookingResponse(booking);
    }

    private boolean handleDateChange(com.nextkey.ecommerce.domain.model.order.Booking booking, BookingDto.UpdateRequest request) {
        LocalDate newCheckIn = request.getCheckInDate() != null ? request.getCheckInDate() : booking.getCheckInDate();
        LocalDate newCheckOut = request.getCheckOutDate() != null ? request.getCheckOutDate() : booking.getCheckOutDate();

        if (!newCheckIn.equals(booking.getCheckInDate()) || !newCheckOut.equals(booking.getCheckOutDate())) {
            return processDateRangeChange(booking, newCheckIn, newCheckOut);
        }
        return false;
    }

    private boolean processDateRangeChange(com.nextkey.ecommerce.domain.model.order.Booking booking,
            LocalDate newCheckIn, LocalDate newCheckOut) {
        // 釋放舊日期
        roomCalendarService.releaseDateRange(
                booking.getRoomListingId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        // 鎖定新日期
        String lockValue = roomCalendarService.lockDateRange(booking.getRoomListingId(), newCheckIn, newCheckOut);
        if (lockValue == null) {
            throw new BusinessException(ErrorCode.E_4001, "New date range is not available");
        }

        try {
            // 檢查新日期是否可用
            if (!roomCalendarService.isDateRangeAvailable(booking.getRoomListingId(), newCheckIn, newCheckOut)) {
                throw new BusinessException(ErrorCode.E_4001, "New date range is not available");
            }

            booking.setCheckInDate(newCheckIn);
            booking.setCheckOutDate(newCheckOut);
            return true;
        } finally {
            roomCalendarService.unlockDateRange(booking.getRoomListingId(), newCheckIn, newCheckOut, lockValue);
        }
    }

    private void updateBookingFields(com.nextkey.ecommerce.domain.model.order.Booking booking, BookingDto.UpdateRequest request) {
        if (request.getGuestCount() != null) {
            booking.setGuestCount(request.getGuestCount());
        }
        if (request.getGuestName() != null) {
            booking.setGuestName(request.getGuestName());
        }
        if (request.getGuestPhone() != null) {
            booking.setGuestPhone(request.getGuestPhone());
        }
        if (request.getGuestEmail() != null) {
            booking.setGuestEmail(request.getGuestEmail());
        }
        if (request.getSpecialRequests() != null) {
            booking.setSpecialRequests(request.getSpecialRequests());
        }
    }

    private void recalculateAndBookDateRange(com.nextkey.ecommerce.domain.model.order.Booking booking) {
        BigDecimal totalAmount = calculateTotalAmount(
                booking.getRoomListingId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );
        booking.setTotalAmount(totalAmount);

        // 更新日曆
        roomCalendarService.bookDateRange(
                booking.getRoomListingId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getId()
        );
    }

    private BookingDto.BookingResponse buildBookingResponse(com.nextkey.ecommerce.domain.model.order.Booking booking) {
        Listing listing = listingRepository.findById(booking.getRoomListingId()).orElse(null);
        Room room = listing != null ? roomRepository.findByListingId(listing.getId()).orElse(null) : null;
        long nightsCount = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        return toBookingResponse(booking, listing, room, nightsCount);
    }

    /**
     * 取消預訂
     */
    @Transactional
    public void cancelBooking(final UUID bookingId, final String reason) {
        com.nextkey.ecommerce.domain.model.order.Booking booking = findBookingById(bookingId);

        // 檢查是否可取消
        if (!OrderStateMachine.canCancel(booking.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_4007, "Booking cannot be cancelled");
        }

        // 釋放日曆
        roomCalendarService.releaseDateRange(
                booking.getRoomListingId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        booking.setStatus(com.nextkey.ecommerce.domain.model.order.Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        log.info("Booking cancelled: id={}, reason={}", bookingId, reason);
    }

    // ========== Helper Methods ==========

    private com.nextkey.ecommerce.domain.model.order.Booking findBookingById(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4006));
    }

    private BigDecimal calculateTotalAmount(final UUID roomListingId, final LocalDate checkIn, final LocalDate checkOut) {
        BigDecimal total = BigDecimal.ZERO;
        List<RoomCalendar> calendars = roomCalendarService.getCalendarRange(roomListingId, checkIn, checkOut.minusDays(1));

        for (RoomCalendar calendar : calendars) {
            total = total.add(calendar.getPrice() != null ? calendar.getPrice() : BigDecimal.ZERO);
        }

        // 如果有些日期沒有設定價格，使用 listing 的 basePrice
        long expectedDays = ChronoUnit.DAYS.between(checkIn, checkOut);
        Listing listing = listingRepository.findById(roomListingId).orElse(null);
        if (listing != null && calendars.size() < expectedDays) {
            long missingDays = expectedDays - calendars.size();
            total = total.add(listing.getBasePrice().multiply(BigDecimal.valueOf(missingDays)));
        }

        return total;
    }

    private BookingDto.BookingResponse toBookingResponse(
            com.nextkey.ecommerce.domain.model.order.Booking booking,
            Listing listing,
            Room room,
            long nightsCount) {

        String title = listing != null ? listing.getTitle() : "Unknown";
        String coverImageUrl = listing != null ? listing.getCoverImageUrl() : null;
        LocalTime checkInTime = room != null ? room.getCheckInTime() : DEFAULT_CHECK_IN_TIME;
        LocalTime checkOutTime = room != null ? room.getCheckOutTime() : DEFAULT_CHECK_OUT_TIME;

        return BookingDto.BookingResponse.builder()
                .id(booking.getId())
                .tenantId(booking.getTenantId())
                .userId(booking.getUserId())
                .roomListingId(booking.getRoomListingId())
                .roomTitle(title)
                .coverImageUrl(coverImageUrl)
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .guestCount(booking.getGuestCount())
                .status(booking.getStatus().name())
                .totalAmount(booking.getTotalAmount())
                .currency("TWD")
                .guestName(booking.getGuestName())
                .guestPhone(booking.getGuestPhone())
                .guestEmail(booking.getGuestEmail())
                .specialRequests(booking.getSpecialRequests())
                .nightsCount((int) nightsCount)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private BookingDto.BookingListResponse toBookingListResponse(
            com.nextkey.ecommerce.domain.model.order.Booking booking,
            String roomTitle) {
        long nightsCount = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());

        return BookingDto.BookingListResponse.builder()
                .id(booking.getId())
                .roomListingId(booking.getRoomListingId())
                .roomTitle(roomTitle != null ? roomTitle : "Unknown")
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .guestCount(booking.getGuestCount())
                .status(booking.getStatus().name())
                .totalAmount(booking.getTotalAmount())
                .currency("TWD")
                .nightsCount((int) nightsCount)
                .createdAt(booking.getCreatedAt())
                .build();
    }
}