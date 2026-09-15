package com.nextkey.ecommerce.core.room;

import java.time.LocalTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ListingRepository listingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final FeatureToggleService featureToggleService;

    // Default values
    private static final int DEFAULT_MAX_GUESTS = 2;
    private static final int DEFAULT_ROOM_COUNT = 1;
    private static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(15, 0);
    private static final LocalTime DEFAULT_CHECK_OUT_TIME = LocalTime.of(11, 0);

    @Transactional(readOnly = true)
    public Page<RoomDto.ListResponse> getRooms(
            Integer maxGuests,
            String location,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageableUtils.of(page, size, 100, sort);

        UUID tenantId = TenantContext.getCurrentTenant();

        Specification<Room> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("listing").get("tenantId"), tenantId),
                cb.equal(root.get("listing").get("status"), Listing.ListingStatus.ACTIVE));

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("listing").get("title")), pattern),
                    cb.like(cb.lower(root.get("listing").get("description")), pattern)));
        }
        if (maxGuests != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("maxGuests"), maxGuests));
        }
        if (location != null && !location.isBlank()) {
            String pattern = "%" + location.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern));
        }

        Page<Room> rooms = roomRepository.findAll(spec, pageRequest);

        return rooms.map(this::toListResponse);
    }

    @Transactional(readOnly = true)
    public RoomDto.Response getRoom(UUID listingId) {
        Room room = findRoomByListingId(listingId);
        return toResponse(room);
    }

    @Transactional
    public RoomDto.Response createRoom(RoomDto.CreateRequest request) {
        // 檢查 BOOKING_ENABLED feature toggle - T-DEF-001-02
        featureToggleService.checkFeatureEnabled("BOOKING_ENABLED");

        UUID tenantId = TenantContext.getCurrentTenant();
        // Sprint 147：MAX_ROOMS 數量配額強制執行（PRD §4.4）
        featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS,
                listingRepository.countByTenantIdAndListingTypeAndStatus(
                        tenantId, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE));

        Tenant tenant = fetchTenant(tenantId);
        User owner = fetchOwner();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenant(tenant)
                .tenantId(tenantId)
                .owner(owner)
                .listingType(Listing.ListingType.ROOM)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getBasePrice())
                .currency("TWD")
                .tags(request.getTags())
                .build();

        listing = listingRepository.save(listing);

        // Create Room
        Room room = Room.builder()
                .listing(listing)
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .maxGuests(request.getMaxGuests() != null ? request.getMaxGuests() : DEFAULT_MAX_GUESTS)
                .amenities(request.getAmenities())
                .checkInTime(request.getCheckInTime() != null ? request.getCheckInTime() : DEFAULT_CHECK_IN_TIME)
                .checkOutTime(request.getCheckOutTime() != null ? request.getCheckOutTime() : DEFAULT_CHECK_OUT_TIME)
                .roomCount(request.getRoomCount() != null ? request.getRoomCount() : DEFAULT_ROOM_COUNT)
                .openUntilDate(request.getOpenUntilDate())
                .bookingWindowDays(request.getBookingWindowDays())
                .build();

        room = roomRepository.save(room);
        log.info("Created room with listingId: {}", listing.getId());

        return toResponse(room);
    }

    /**
     * 從 Dashboard 建立 Room (使用 CreateListingRequest)
     * T-DEF-001-01
     */
    @Transactional
    public RoomDto.Response createRoomFromDashboard(com.nextkey.ecommerce.api.dto.CreateListingRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        // Sprint 148（DEF-184）：BOOKING_ENABLED 檢查從 DashboardListingController 搬進 Service 層，
        // 遵循 PRD §4.4「Feature Toggle 驗證...不得在 Controller 層執行」的分層規範
        featureToggleService.checkFeatureEnabled("BOOKING_ENABLED");
        // Sprint 147：MAX_ROOMS 數量配額強制執行（PRD §4.4）
        featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS,
                listingRepository.countByTenantIdAndListingTypeAndStatus(
                        tenantId, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE));

        Tenant tenant = fetchTenant(tenantId);
        User owner = fetchOwner();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenant(tenant)
                .tenantId(tenantId)
                .owner(owner)
                .listingType(Listing.ListingType.ROOM)
                .title(request.getName())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getPrice())
                .currency("TWD")
                .tags(request.getTags())
                .build();

        listing = listingRepository.save(listing);

        // Create Room
        Room room = Room.builder()
                .listing(listing)
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .maxGuests(request.getMaxGuests() != null ? request.getMaxGuests() : DEFAULT_MAX_GUESTS)
                .amenities(request.getAmenities())
                .checkInTime(request.getCheckInTime() != null ? request.getCheckInTime() : DEFAULT_CHECK_IN_TIME)
                .checkOutTime(request.getCheckOutTime() != null ? request.getCheckOutTime() : DEFAULT_CHECK_OUT_TIME)
                .roomCount(request.getRoomCount() != null ? request.getRoomCount() : DEFAULT_ROOM_COUNT)
                .build();

        room = roomRepository.save(room);
        log.info("Created room from dashboard with listingId: {}", listing.getId());

        return toResponse(room);
    }

    @Transactional
    public RoomDto.Response updateRoom(UUID listingId, RoomDto.UpdateRequest request, final boolean isSuperAdmin) {
        Room room = findRoomByListingId(listingId);
        Listing listing = room.getListing();
        checkListingTenantOwnership(listing, isSuperAdmin);

        // Sprint 147：MAX_ROOMS 數量配額強制執行（PRD §4.4）——僅在「由非 ACTIVE 轉入 ACTIVE」時檢查
        if (isActivatingListing(listing.getStatus(), request.getStatus())) {
            featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS,
                    listingRepository.countByTenantIdAndListingTypeAndStatus(
                            listing.getTenantId(), Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE));
        }

        updateListingFromRequest(listing, request);
        updateRoomFromRequest(room, request);

        room = roomRepository.save(room);
        log.info("Updated room with listingId: {}", listingId);

        return toResponse(room);
    }

    private boolean isActivatingListing(final Listing.ListingStatus currentStatus, final String requestedStatus) {
        return requestedStatus != null
                && currentStatus != Listing.ListingStatus.ACTIVE
                && Listing.ListingStatus.ACTIVE == Listing.ListingStatus.valueOf(requestedStatus.toUpperCase());
    }

    private void updateListingFromRequest(Listing listing, RoomDto.UpdateRequest request) {
        boolean updated = false;
        if (request.getTitle() != null) {
            listing.setTitle(request.getTitle());
            updated = true;
        }
        if (request.getDescription() != null) {
            listing.setDescription(request.getDescription());
            updated = true;
        }
        if (request.getCoverImageUrl() != null) {
            listing.setCoverImageUrl(request.getCoverImageUrl());
            updated = true;
        }
        if (request.getBasePrice() != null) {
            listing.setBasePrice(request.getBasePrice());
            updated = true;
        }
        if (request.getTags() != null) {
            listing.setTags(request.getTags());
            updated = true;
        }
        if (request.getStatus() != null) {
            try {
                listing.setStatus(Listing.ListingStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.E_9000, "Invalid status: " + request.getStatus());
            }
            updated = true;
        }
        if (updated) {
            listingRepository.save(listing);
        }
    }

    private void updateRoomFromRequest(Room room, RoomDto.UpdateRequest request) {
        updateRoomLocationFields(room, request);
        updateRoomCapacityFields(room, request);
        updateRoomTimeFields(room, request);
        updateRoomOpenWindowFields(room, request);
    }

    private void updateRoomLocationFields(Room room, RoomDto.UpdateRequest request) {
        if (request.getLocation() != null) {
            room.setLocation(request.getLocation());
        }
        if (request.getLatitude() != null) {
            room.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            room.setLongitude(request.getLongitude());
        }
    }

    private void updateRoomCapacityFields(Room room, RoomDto.UpdateRequest request) {
        if (request.getMaxGuests() != null) {
            room.setMaxGuests(request.getMaxGuests());
        }
        if (request.getAmenities() != null) {
            room.setAmenities(request.getAmenities());
        }
        if (request.getRoomCount() != null) {
            room.setRoomCount(request.getRoomCount());
        }
    }

    private void updateRoomTimeFields(Room room, RoomDto.UpdateRequest request) {
        if (request.getCheckInTime() != null) {
            room.setCheckInTime(request.getCheckInTime());
        }
        if (request.getCheckOutTime() != null) {
            room.setCheckOutTime(request.getCheckOutTime());
        }
    }

    // 開放窗（Sprint 47 AI-2202e）：沿用部分更新慣例（非 null 才更新）；
    // 清除開放窗（改回無限制）需另機制，與既有欄位一致。
    private void updateRoomOpenWindowFields(Room room, RoomDto.UpdateRequest request) {
        if (request.getOpenUntilDate() != null) {
            room.setOpenUntilDate(request.getOpenUntilDate());
        }
        if (request.getBookingWindowDays() != null) {
            room.setBookingWindowDays(request.getBookingWindowDays());
        }
    }

    /**
     * 清除開放窗（Sprint 57 AI-2202f）：`updateRoom` 沿用「非 null 才更新」慣例，無法把
     * openUntilDate/bookingWindowDays 清回 NULL（無限制）；本方法一次性清除兩欄位。
     */
    @Transactional
    public RoomDto.Response clearOpenWindow(final UUID listingId, final boolean isSuperAdmin) {
        Room room = findRoomByListingId(listingId);
        checkListingTenantOwnership(room.getListing(), isSuperAdmin);
        room.setOpenUntilDate(null);
        room.setBookingWindowDays(null);
        room = roomRepository.save(room);
        log.info("Cleared open window for room with listingId: {}", listingId);
        return toResponse(room);
    }

    @Transactional
    public void deleteRoom(final UUID listingId, final boolean isSuperAdmin) {
        Room room = findRoomByListingId(listingId);
        Listing listing = room.getListing();
        checkListingTenantOwnership(listing, isSuperAdmin);

        // Soft delete: set status to DELETED
        listing.setStatus(Listing.ListingStatus.DELETED);
        listingRepository.save(listing);

        log.info("Deleted room with listingId: {}", listingId);
    }

    private Room findRoomByListingId(final UUID listingId) {
        return roomRepository.findByListingId(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000));
    }

    // DEF-041 根因修復（Sprint 84）：Listing.tenantId/ownerId 是 insertable=false 的唯讀影子欄位，
    // 建立時必須實際設定 .tenant(...)/.owner(...) 關聯物件，否則資料庫 tenant_id/owner_id 永遠不會被寫入。
    private Tenant fetchTenant(final UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));
    }

    private User fetchOwner() {
        UUID userId = TenantContext.getCurrentUser();
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
    }

    // DEF-041（Sprint 84）：比照 BookingService.checkListingTenantOwnership 既有模式，
    // 非 SUPER_ADMIN 限自己租戶，SUPER_ADMIN 可跨租戶操作。
    private void checkListingTenantOwnership(final Listing listing, final boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        UUID callerTenantId = TenantContext.getCurrentTenant();
        if (!listing.getTenantId().equals(callerTenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to manage this room listing");
        }
    }

    private RoomDto.Response toResponse(Room room) {
        Listing listing = room.getListing();
        return RoomDto.Response.builder()
                .listingId(listing.getId())
                .tenantId(listing.getTenantId())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .location(room.getLocation())
                .latitude(room.getLatitude())
                .longitude(room.getLongitude())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .tags(listing.getTags())
                .maxGuests(room.getMaxGuests())
                .amenities(room.getAmenities())
                .checkInTime(room.getCheckInTime())
                .checkOutTime(room.getCheckOutTime())
                .roomCount(room.getRoomCount())
                .openUntilDate(room.getOpenUntilDate())
                .bookingWindowDays(room.getBookingWindowDays())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }

    private RoomDto.ListResponse toListResponse(Room room) {
        Listing listing = room.getListing();
        return RoomDto.ListResponse.builder()
                .listingId(listing.getId())
                .title(listing.getTitle())
                .location(room.getLocation())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .maxGuests(room.getMaxGuests())
                .roomCount(room.getRoomCount())
                .createdAt(listing.getCreatedAt())
                .build();
    }
}