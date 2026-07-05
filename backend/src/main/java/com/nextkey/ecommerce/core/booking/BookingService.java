package com.nextkey.ecommerce.core.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderStateMachine;
import com.nextkey.ecommerce.core.pricing.PricingService;
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
    private final PricingService pricingService;
    private final FeatureToggleService featureToggleService;

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

        // 開放窗（AI-2202e）：room 缺失時視為無限制（維持現狀）
        Room room = roomRepository.findByListingId(request.getRoomListingId()).orElse(null);

        // 檢查日期範圍
        if (request.getCheckOutDate().isBefore(request.getCheckInDate()) ||
            request.getCheckOutDate().isEqual(request.getCheckInDate())) {
            return BookingDto.AvailabilityResponse.builder()
                    .available(false)
                    .roomListingId(request.getRoomListingId())
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .unavailableReason(BookingDto.AvailabilityReasonCode.INVALID_DATE_RANGE.name())
                    .build();
        }

        // 檢查每個日期是否可用
        List<RoomCalendar> calendars = roomCalendarService.getCalendarRange(
                request.getRoomListingId(),
                request.getCheckInDate(),
                request.getCheckOutDate().minusDays(1)
        );

        List<BookingDto.CalendarResponse> calendarDetails = calendars.stream()
                .map(c -> BookingDto.CalendarResponse.builder()
                        .date(c.getCalendarDate())
                        .status(c.getStatus().name())
                        // room_calendar.price 已停用（AI-2406，恆 NULL）→ 每日基準價一律為 basePrice
                        .price(listing.getBasePrice())
                        .bookingId(c.getBookingId())
                        .build())
                .collect(Collectors.toList());

        // 檢查是否有不可用的日期（無記錄之日視為可預訂，與 getCalendar 一致）
        // 開放窗（AI-2202e）：區間含未開放日 → 不可訂，優先回未開放原因
        LocalDate notOpenNight = firstNotOpenNight(
                room, request.getCheckInDate(), request.getCheckOutDate());
        boolean calendarAvailable = calendars.stream()
                .allMatch(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE);
        boolean available = calendarAvailable && notOpenNight == null;
        String unavailableReason;
        if (notOpenNight != null) {
            unavailableReason = BookingDto.AvailabilityReasonCode.NOT_OPEN_FOR_BOOKING.name();
        } else if (!calendarAvailable) {
            unavailableReason = findFirstUnavailableReason(calendars);
        } else {
            unavailableReason = null;
        }

        long nightsCount = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());

        // 總價：基礎（basePrice × 晚數）+ 動態定價調整（AI-2402 / AI-2406b）。
        // 有規則生效時回調整前價/有號差額/方向/規則名，且 totalPrice 為調整後總價（含漲價）；否則維持既有計價（向後相容）。
        BigDecimal baseTotal = calendarBaseTotal(
                request.getRoomListingId(), request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal totalPrice = baseTotal;
        BigDecimal originalTotalPrice = null;
        BigDecimal discountAmount = null;
        String appliedRuleName = null;
        String priceAdjustmentType = null;
        PricingDto.CalculatePriceResponse dynamic = tryDynamicPricing(
                request.getRoomListingId(), request.getCheckInDate(), request.getCheckOutDate());
        if (dynamic != null) {
            originalTotalPrice = dynamic.getBaseTotal();
            totalPrice = dynamic.getAdjustedTotal();
            // 有號差額（正=折扣、負=加價）；不取 dynamic.getDiscount()（已被 clamp 非負，漲價會失真）
            discountAmount = dynamic.getBaseTotal().subtract(dynamic.getAdjustedTotal());
            appliedRuleName = firstAppliedRuleName(dynamic);
            priceAdjustmentType = adjustmentDirection(dynamic.getBaseTotal(), dynamic.getAdjustedTotal());
        }

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
                .originalTotalPrice(originalTotalPrice)
                .discountAmount(discountAmount)
                .appliedRuleName(appliedRuleName)
                .priceAdjustmentType(priceAdjustmentType)
                .build();
    }

    /**
     * 回傳不可訂原因碼（Sprint 58 AI-2408）：`RoomCalendarStatus` 的 BOOKED/BLOCKED/MAINTENANCE
     * 與 `BookingDto.AvailabilityReasonCode` 同名，直接取 `.name()` 即為 code。
     */
    private String findFirstUnavailableReason(List<RoomCalendar> calendars) {
        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() != RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                return calendar.getStatus().name();
            }
        }
        return null;
    }

    /** 開放窗（AI-2202e）基準日：滾動視窗 booking_window_days 以今日起算。 */
    private LocalDate openWindowReferenceDate() {
        return LocalDate.now();
    }

    /**
     * 回傳訂房區間 [checkIn, checkOut)（逐晚）中第一個超出開放窗（未開放）之日期；無則 null。
     * room 為 null 或未設開放窗（上限 null）時回 null（維持現狀「無記錄=可訂」）。
     */
    private LocalDate firstNotOpenNight(Room room, LocalDate checkIn, LocalDate checkOut) {
        if (room == null) {
            return null;
        }
        LocalDate openUntil = room.resolveOpenUntil(openWindowReferenceDate());
        if (openUntil == null) {
            return null;
        }
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            if (d.isAfter(openUntil)) {
                return d;
            }
        }
        return null;
    }

    /**
     * 訂房寫入前的開放窗守門（AI-2202e）：區間含未開放日 → 擋訂（E_3002）。
     * 與 availability/calendar 同一 {@link Room#resolveOpenUntil} 判斷，確保三層一致。
     */
    private void assertWithinOpenWindow(Room room, LocalDate checkIn, LocalDate checkOut) {
        LocalDate notOpen = firstNotOpenNight(room, checkIn, checkOut);
        if (notOpen != null) {
            throw new BusinessException(ErrorCode.E_3002, "Room is not open for booking on " + notOpen);
        }
    }

    /** 整月日曆查詢允許的最大天數區間（防止過大區間查詢）。 */
    private static final long MAX_CALENDAR_RANGE_DAYS = 92;

    /**
     * 取得房源指定日期區間的日曆狀態（整月日曆用，read-only）。
     * 回傳該區間「已有 room_calendar 記錄」的每日狀態（AVAILABLE/BOOKED/BLOCKED/MAINTENANCE）；
     * 無記錄之日期視為可預訂（由前端以 basePrice 補齊），與 checkAvailability 邏輯一致。
     * 僅查詢既有資料，無 DB/schema 變動。（Sprint 41 US-004 / AI-2202b）
     *
     * @param roomListingId 房源 listing ID
     * @param startDate     起始日（含）
     * @param endDate       結束日（含）
     */
    public List<BookingDto.CalendarResponse> getCalendar(UUID roomListingId, LocalDate startDate, LocalDate endDate) {
        Listing listing = listingRepository.findById(roomListingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000));

        if (listing.getListingType() != Listing.ListingType.ROOM) {
            throw new BusinessException(ErrorCode.E_3001, "Listing is not a room");
        }

        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.E_3001, "endDate must not be before startDate");
        }

        // 含頭含尾的天數；上限防止過大區間查詢
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (rangeDays > MAX_CALENDAR_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.E_3001,
                    "Date range too large (max " + MAX_CALENDAR_RANGE_DAYS + " days)");
        }

        List<RoomCalendar> calendars = roomCalendarService.getCalendarRange(roomListingId, startDate, endDate);

        // 每日動態定價調整（AI-2405b / AI-2406b）：toggle 開啟時以 PricingService 逐日 breakdown 取調整後價。
        // 只對可訂日套、含折扣「或漲價」（與 availability 一致），BOOKED/BLOCKED 日不受影響。
        Map<LocalDate, PricingDto.PriceBreakdown> adjustmentByDate = dailyDiscountMap(roomListingId, startDate, endDate);

        List<BookingDto.CalendarResponse> result = calendars.stream()
                .map(c -> {
                    // room_calendar.price 已停用（AI-2406，恆 NULL）→ 每日基準價一律為 basePrice
                    BigDecimal price = listing.getBasePrice();
                    BigDecimal originalPrice = null;
                    String appliedRuleName = null;
                    String priceAdjustmentType = null;
                    if (c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE) {
                        PricingDto.PriceBreakdown bd = adjustmentByDate.get(c.getCalendarDate());
                        if (bd != null && bd.getAdjustedPrice() != null && bd.getBasePrice() != null
                                && bd.getAdjustedPrice().compareTo(bd.getBasePrice()) != 0) {
                            originalPrice = bd.getBasePrice();
                            price = bd.getAdjustedPrice();
                            appliedRuleName = bd.getAppliedRuleName();
                            priceAdjustmentType = adjustmentDirection(bd.getBasePrice(), bd.getAdjustedPrice());
                        }
                    }
                    return BookingDto.CalendarResponse.builder()
                            .date(c.getCalendarDate())
                            .status(c.getStatus().name())
                            .price(price)
                            .originalPrice(originalPrice)
                            .appliedRuleName(appliedRuleName)
                            .priceAdjustmentType(priceAdjustmentType)
                            .bookingId(c.getBookingId())
                            .build();
                })
                .collect(Collectors.toCollection(ArrayList::new));

        appendNotOpenDays(result, calendars, roomListingId, startDate, endDate, listing.getBasePrice());
        return result;
    }

    /**
     * 開放窗（AI-2202e）：對超過開放上限之「無記錄日」補一筆 NOT_OPEN CalendarResponse。
     * 前端把「未回傳日」當可訂，故未開放日須顯式回傳；開放上限 null（無限制）則不補。
     */
    private void appendNotOpenDays(List<BookingDto.CalendarResponse> result, List<RoomCalendar> calendars,
            UUID roomListingId, LocalDate startDate, LocalDate endDate, BigDecimal basePrice) {
        Room room = roomRepository.findByListingId(roomListingId).orElse(null);
        LocalDate openUntil = room != null ? room.resolveOpenUntil(openWindowReferenceDate()) : null;
        if (openUntil == null) {
            return;
        }
        Set<LocalDate> existingDates = calendars.stream()
                .map(RoomCalendar::getCalendarDate)
                .collect(Collectors.toSet());
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (d.isAfter(openUntil) && !existingDates.contains(d)) {
                result.add(BookingDto.CalendarResponse.builder()
                        .date(d)
                        .status("NOT_OPEN")
                        .price(basePrice)
                        .bookingId(null)
                        .build());
            }
        }
    }

    /**
     * 整月日曆每日折扣 map（AI-2405b）：DYNAMIC_PRICING_ENABLED 開啟時，以 PricingService.calculatePrice
     * 取區間逐日 breakdown（date → PriceBreakdown）。calculatePrice 的 checkOut 為 exclusive，
     * 故傳 endDate.plusDays(1) 以涵蓋 endDate 當日。關閉/計算失敗回空 map（呼叫端不套折扣）。
     */
    private Map<LocalDate, PricingDto.PriceBreakdown> dailyDiscountMap(
            final UUID roomListingId, final LocalDate startDate, final LocalDate endDate) {
        if (!featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")) {
            return Collections.emptyMap();
        }
        try {
            PricingDto.CalculatePriceResponse resp = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(roomListingId)
                            .checkInDate(startDate)
                            .checkOutDate(endDate.plusDays(1))
                            .build());
            if (resp.getBreakdown() == null) {
                return Collections.emptyMap();
            }
            Map<LocalDate, PricingDto.PriceBreakdown> map = new HashMap<>();
            for (PricingDto.PriceBreakdown bd : resp.getBreakdown()) {
                map.put(bd.getDate(), bd);
            }
            return map;
        } catch (BusinessException e) {
            log.warn("Calendar dynamic pricing failed for room={}: {}", roomListingId, e.getMessage());
            return Collections.emptyMap();
        }
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

        // 開放窗（AI-2202e）：區間含未開放日 → 擋訂（與 availability/calendar 三層一致）
        assertWithinOpenWindow(room, request.getCheckInDate(), request.getCheckOutDate());

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
        checkBookingOwnership(booking);
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
        checkBookingOwnership(booking);

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
            // 開放窗（AI-2202e）：新日期含未開放日 → 擋（與 createBooking 一致）
            Room room = roomRepository.findByListingId(booking.getRoomListingId()).orElse(null);
            assertWithinOpenWindow(room, newCheckIn, newCheckOut);

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
        checkBookingOwnership(booking);

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

    /**
     * 預訂擁有權檢查（DEF-023：booking 讀寫擁有權隔離）。
     * 比照 OrderService.getOrder：買家限本人預訂、admin（ROLE_ADMIN/SUPER_ADMIN）放行，
     * 越權回 403/E_1007。杜絕任何登入者查詢/更新他人預訂（IDOR）。
     */
    private void checkBookingOwnership(final com.nextkey.ecommerce.domain.model.order.Booking booking) {
        UUID userId = getCurrentUser();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        if (!isAdmin && !userId.equals(booking.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to access this booking");
        }
    }

    private BigDecimal calculateTotalAmount(final UUID roomListingId, final LocalDate checkIn, final LocalDate checkOut) {
        // 動態定價調整優先（AI-2402 / AI-2406b）：有規則生效時以調整後總價為準（含漲價），與 availability 顯示一致。
        PricingDto.CalculatePriceResponse dynamic = tryDynamicPricing(roomListingId, checkIn, checkOut);
        if (dynamic != null) {
            return dynamic.getAdjustedTotal();
        }
        // 無規則生效（或 toggle 關閉）→ 既有 basePrice × 晚數計價（向後相容）
        return calendarBaseTotal(roomListingId, checkIn, checkOut);
    }

    /**
     * 基準計價：每晚 listing.basePrice × 晚數（AI-2406）。
     * room_calendar.price 手動日價機制已停用（恆 NULL、死碼），故每日基準價一律為 basePrice；
     * 動態折扣另由 tryDynamicPricing（PricingService 規則）套用。
     * 註：舊實作對「有記錄之日」加 room_calendar.price（恆 NULL → 加 ZERO）、對「無記錄之日」加
     * basePrice；由於可訂區間必無既有記錄，兩者對可訂訂房結果等價，此處簡化並修正該潛在低估。
     */
    private BigDecimal calendarBaseTotal(final UUID roomListingId, final LocalDate checkIn, final LocalDate checkOut) {
        Listing listing = listingRepository.findById(roomListingId).orElse(null);
        if (listing == null) {
            return BigDecimal.ZERO;
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return listing.getBasePrice().multiply(BigDecimal.valueOf(nights));
    }

    /**
     * 動態定價試算（AI-2402 / AI-2406b）：DYNAMIC_PRICING_ENABLED 開啟且該區間有規則生效
     * （adjustedTotal ≠ baseTotal，含折扣「或漲價」）時，回傳 PricingService 調整後試算；
     * 否則回 null（呼叫端 fallback 既有 calendar/basePrice 計價，向後相容）。
     *
     * AI-2406b（選項 B）：定價機制真正統一——漲價型規則（週末/旺季加成、MANUAL_OVERRIDE 調高）
     * 自此一併計入訂房總價與可用性顯示，使顯示與收費完全一致（原僅套折扣）。
     * 計算失敗（如無 Room/日期無效）降級為不套用，避免阻斷可用性查詢與訂房。
     * 註：動態定價以 listing.basePrice 為基準。
     */
    private PricingDto.CalculatePriceResponse tryDynamicPricing(final UUID roomListingId,
            final LocalDate checkIn, final LocalDate checkOut) {
        if (!featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")) {
            return null;
        }
        try {
            PricingDto.CalculatePriceResponse resp = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(roomListingId)
                            .checkInDate(checkIn)
                            .checkOutDate(checkOut)
                            .build());
            boolean hasAdjustment = resp.getAdjustedTotal() != null
                    && resp.getBaseTotal() != null
                    && resp.getAdjustedTotal().compareTo(resp.getBaseTotal()) != 0;
            return hasAdjustment ? resp : null;
        } catch (BusinessException e) {
            log.warn("Dynamic pricing calc failed for room={}, fallback to calendar pricing: {}",
                    roomListingId, e.getMessage());
            return null;
        }
    }

    /** 依調整前後總價判斷調整方向（DISCOUNT / MARKUP / NONE），供前端雙向顯示（AI-2406b）。 */
    private static String adjustmentDirection(final BigDecimal original, final BigDecimal adjusted) {
        if (original == null || adjusted == null) {
            return "NONE";
        }
        int cmp = adjusted.compareTo(original);
        if (cmp < 0) {
            return "DISCOUNT";
        }
        if (cmp > 0) {
            return "MARKUP";
        }
        return "NONE";
    }

    /** 取套用的規則名（首個有規則標記的 breakdown）。 */
    private static String firstAppliedRuleName(final PricingDto.CalculatePriceResponse resp) {
        if (resp.getBreakdown() == null) {
            return null;
        }
        return resp.getBreakdown().stream()
                .map(PricingDto.PriceBreakdown::getAppliedRuleName)
                .filter(name -> name != null)
                .findFirst()
                .orElse(null);
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