package com.nextkey.ecommerce.core.room;

import com.nextkey.ecommerce.api.dto.RoomDto;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoomService 單元測試——開放窗清除機制（Sprint 57 AI-2202f）+
 * DEF-041 租戶擁有權檢查（Sprint 84）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService: 開放窗清除機制 + 租戶擁有權檢查")
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoomService roomService;

    private static final UUID LISTING_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440004");
    private static final UUID OWNER_TENANT = UUID.fromString("880e8400-e29b-41d4-a716-446655440005");
    private static final UUID OTHER_TENANT = UUID.fromString("880e8400-e29b-41d4-a716-446655440006");

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(OWNER_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Room roomWithOpenWindow() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .title("Test Room")
                .tenantId(OWNER_TENANT)
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing.setId(LISTING_ID);

        Room room = Room.builder()
                .listing(listing)
                .location("Taipei")
                .openUntilDate(LocalDate.now().plusDays(30))
                .bookingWindowDays(14)
                .build();
        room.setListingId(LISTING_ID);
        return room;
    }

    @Test
    @DisplayName("UT-ROOM-001: clearOpenWindow 將 openUntilDate/bookingWindowDays 皆清回 null")
    void clearOpenWindow_resetsBothFieldsToNull() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        RoomDto.Response response = roomService.clearOpenWindow(LISTING_ID, false);

        assertThat(room.getOpenUntilDate()).isNull();
        assertThat(room.getBookingWindowDays()).isNull();
        assertThat(response.getOpenUntilDate()).isNull();
        assertThat(response.getBookingWindowDays()).isNull();
    }

    @Test
    @DisplayName("UT-ROOM-002: clearOpenWindow 不影響其他欄位（如 location）")
    void clearOpenWindow_doesNotAffectOtherFields() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        roomService.clearOpenWindow(LISTING_ID, false);

        assertThat(room.getLocation()).isEqualTo("Taipei");
    }

    @Test
    @DisplayName("UT-ROOM-003: clearOpenWindow 找不到房源 → 拋 BusinessException")
    void clearOpenWindow_roomNotFound_throws() {
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.clearOpenWindow(LISTING_ID, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("UT-ROOM-004: clearOpenWindow 跨租戶（非 SUPER_ADMIN）→ 拋 E_1007")
    void clearOpenWindow_crossTenant_throwsForbidden() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.clearOpenWindow(LISTING_ID, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("UT-ROOM-005: clearOpenWindow 跨租戶但 SUPER_ADMIN → 放行")
    void clearOpenWindow_crossTenantSuperAdmin_allowed() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        RoomDto.Response response = roomService.clearOpenWindow(LISTING_ID, true);

        assertThat(response.getOpenUntilDate()).isNull();
    }

    @Test
    @DisplayName("UT-ROOM-006: updateRoom 同租戶 → 正常更新")
    void updateRoom_sameTenant_updatesSuccessfully() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        RoomDto.Response response = roomService.updateRoom(LISTING_ID, request, false);

        assertThat(response.getLocation()).isEqualTo("Taichung");
    }

    @Test
    @DisplayName("Sprint 161：updateRoom 非法 status 拋出 BusinessException（E_9000），而非未攔截的 IllegalArgumentException")
    void updateRoom_invalidStatus_throwsBusinessException() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().status("NOT_A_REAL_STATUS").build();

        assertThatThrownBy(() -> roomService.updateRoom(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9000);
    }

    @Test
    @DisplayName("UT-ROOM-007: updateRoom 跨租戶（非 SUPER_ADMIN）→ 拋 E_1007，不寫入")
    void updateRoom_crossTenant_throwsForbidden() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        assertThatThrownBy(() -> roomService.updateRoom(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class);
        assertThat(room.getLocation()).isEqualTo("Taipei");
    }

    @Test
    @DisplayName("UT-ROOM-008: updateRoom 跨租戶但 SUPER_ADMIN → 放行")
    void updateRoom_crossTenantSuperAdmin_allowed() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        RoomDto.Response response = roomService.updateRoom(LISTING_ID, request, true);

        assertThat(response.getLocation()).isEqualTo("Taichung");
    }

    @Test
    @DisplayName("UT-ROOM-009: deleteRoom 同租戶 → 正常軟刪除")
    void deleteRoom_sameTenant_deletesSuccessfully() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        lenient().when(listingRepository.save(room.getListing())).thenReturn(room.getListing());

        roomService.deleteRoom(LISTING_ID, false);

        assertThat(room.getListing().getStatus()).isEqualTo(Listing.ListingStatus.DELETED);
    }

    @Test
    @DisplayName("UT-ROOM-010: deleteRoom 跨租戶（非 SUPER_ADMIN）→ 拋 E_1007，不刪除")
    void deleteRoom_crossTenant_throwsForbidden() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.deleteRoom(LISTING_ID, false))
                .isInstanceOf(BusinessException.class);
        assertThat(room.getListing().getStatus()).isEqualTo(Listing.ListingStatus.ACTIVE);
    }

    @Test
    @DisplayName("UT-ROOM-011: deleteRoom 跨租戶但 SUPER_ADMIN → 放行")
    void deleteRoom_crossTenantSuperAdmin_allowed() {
        TenantContext.setCurrentTenant(OTHER_TENANT);
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(listingRepository.save(room.getListing())).thenReturn(room.getListing());

        roomService.deleteRoom(LISTING_ID, true);

        assertThat(room.getListing().getStatus()).isEqualTo(Listing.ListingStatus.DELETED);
    }

    // ========== Sprint 147：MAX_ROOMS 數量配額強制執行 ==========

    @Test
    @DisplayName("Sprint 147: createRoom 已達 MAX_ROOMS 配額 → 拋 BusinessException，未建立 Listing")
    void createRoom_quotaExceeded_doesNotCreateListing() {
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                OWNER_TENANT, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE))
                .thenReturn((long) AppConstants.QUOTA_MAX_ROOMS);
        doThrow(new BusinessException(ErrorCode.E_2009))
                .when(featureToggleService)
                .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS, AppConstants.QUOTA_MAX_ROOMS);

        RoomDto.CreateRequest request = RoomDto.CreateRequest.builder().title("超額房源").build();

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        verify(tenantRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Sprint 147: createRoom 配額未達上限 → 正常建立，且以正確的目前數量呼叫配額檢查")
    void createRoom_underQuota_createsSuccessfullyAndChecksQuota() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                OWNER_TENANT, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE))
                .thenReturn(2L);
        when(tenantRepository.findById(OWNER_TENANT)).thenReturn(Optional.of(Tenant.builder().id(OWNER_TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomDto.CreateRequest request = RoomDto.CreateRequest.builder().title("新房源").build();

        RoomDto.Response response = roomService.createRoom(request);

        assertThat(response.getTitle()).isEqualTo("新房源");
        verify(featureToggleService).checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS, 2L);
    }

    @Test
    @DisplayName("Sprint 147: updateRoom 由 INACTIVE 轉 ACTIVE 且已達配額 → 拋 BusinessException，不寫入")
    void updateRoom_reactivateOverQuota_throwsAndDoesNotSave() {
        Room room = roomWithOpenWindow();
        room.getListing().setStatus(Listing.ListingStatus.INACTIVE);
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                OWNER_TENANT, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE))
                .thenReturn((long) AppConstants.QUOTA_MAX_ROOMS);
        doThrow(new BusinessException(ErrorCode.E_2009))
                .when(featureToggleService)
                .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_ROOMS, AppConstants.QUOTA_MAX_ROOMS);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().status("ACTIVE").build();

        assertThatThrownBy(() -> roomService.updateRoom(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        assertThat(room.getListing().getStatus()).isEqualTo(Listing.ListingStatus.INACTIVE);
    }

    @Test
    @DisplayName("Sprint 147: updateRoom 更新非狀態欄位（維持 ACTIVE）→ 不觸發配額檢查")
    void updateRoom_nonStatusChangeWhileActive_doesNotCheckQuota() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        roomService.updateRoom(LISTING_ID, request, false);

        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(listingRepository, never()).countByTenantIdAndListingTypeAndStatus(any(), any(), any());
    }

    // ========== Sprint 148（DEF-184）：BOOKING_ENABLED 檢查搬移至 Service 層 ==========

    @Test
    @DisplayName("Sprint 148（DEF-184）: createRoomFromDashboard 的 BOOKING_ENABLED 停用 → 拋 BusinessException，未建立 Listing")
    void createRoomFromDashboard_bookingDisabled_throwsAndDoesNotCreateListing() {
        doThrow(new BusinessException(ErrorCode.E_2004, "Feature 'BOOKING_ENABLED' is disabled for this tenant"))
                .when(featureToggleService).checkFeatureEnabled("BOOKING_ENABLED");

        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("ROOM").name("停用開關房源").price(java.math.BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> roomService.createRoomFromDashboard(request))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        verify(listingRepository, never()).countByTenantIdAndListingTypeAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Sprint 148（DEF-184）: createRoomFromDashboard 的 BOOKING_ENABLED 啟用 → 正常建立，且確實檢查了開關")
    void createRoomFromDashboard_bookingEnabled_createsSuccessfullyAndChecksToggle() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                OWNER_TENANT, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE))
                .thenReturn(1L);
        when(tenantRepository.findById(OWNER_TENANT)).thenReturn(Optional.of(Tenant.builder().id(OWNER_TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("ROOM").name("Dashboard新房源").price(java.math.BigDecimal.valueOf(100)).build();

        RoomDto.Response response = roomService.createRoomFromDashboard(request);

        assertThat(response.getTitle()).isEqualTo("Dashboard新房源");
        verify(featureToggleService).checkFeatureEnabled("BOOKING_ENABLED");
    }
}
