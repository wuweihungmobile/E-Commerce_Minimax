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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    @DisplayName("DEF-344：getRooms 的 sortBy 不在白名單 → 拋 BusinessException(E_9000)，不查詢（先前原樣傳給 JPA 排序，落入 500）")
    @SuppressWarnings("unchecked")
    void getRooms_invalidSortBy_throwsE9000() {
        assertThatThrownBy(() -> roomService.getRooms(null, null, null, 0, 20, "basePrice", "DESC"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_9000);
        verify(roomRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("DEF-344：getRooms 的 sortDir 不是 asc/desc → 拋 BusinessException(E_9000)")
    @SuppressWarnings("unchecked")
    void getRooms_invalidSortDir_throwsE9000() {
        assertThatThrownBy(() -> roomService.getRooms(null, null, null, 0, 20, "createdAt", "sideways"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_9000);
        verify(roomRepository, never()).findAll(any(Specification.class), any(Pageable.class));
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
    @DisplayName("DEF-321(b)（Sprint 252）：updateRoom 設定 isFeatured=true → Listing 寫入並反映於回應")
    void updateRoom_setIsFeaturedTrue_persistsAndReturnsInResponse() {
        Room room = roomWithOpenWindow();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(listingRepository.save(room.getListing())).thenReturn(room.getListing());
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().isFeatured(true).build();

        RoomDto.Response response = roomService.updateRoom(LISTING_ID, request, false);

        assertThat(response.getIsFeatured()).isTrue();
        assertThat(room.getListing().getIsFeatured()).isTrue();
    }

    @Test
    @DisplayName("DEF-321(b)（Sprint 252）：updateRoom 不帶 isFeatured（null）→ 維持原值，不被覆蓋為 false")
    void updateRoom_isFeaturedOmitted_keepsExistingValue() {
        Room room = roomWithOpenWindow();
        room.getListing().setIsFeatured(true);
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        RoomDto.Response response = roomService.updateRoom(LISTING_ID, request, false);

        assertThat(response.getIsFeatured()).isTrue();
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
    @DisplayName("DEF-207（Sprint 261 複核修復）：updateRoom 非 ACTIVE 狀態 + 非法 status 字串"
            + " → 拋 BusinessException(E_9000)，不被 isActivatingListing 內未攔截的 valueOf 搶先炸掉")
    void updateRoom_nonActiveStatusWithInvalidNewStatus_throwsBusinessException() {
        Room room = roomWithOpenWindow();
        room.getListing().setStatus(Listing.ListingStatus.INACTIVE);
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

        assertThatThrownBy(() -> roomService.createRoom(request, false))
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

        RoomDto.Response response = roomService.createRoom(request, false);

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

        assertThatThrownBy(() -> roomService.createRoomFromDashboard(request, false))
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

        RoomDto.Response response = roomService.createRoomFromDashboard(request, false);

        assertThat(response.getTitle()).isEqualTo("Dashboard新房源");
        verify(featureToggleService).checkFeatureEnabled("BOOKING_ENABLED");
    }

    // ───────── Sprint 304（DEF-415）：沒有店鋪的呼叫者不得管理系統租戶（平台自營）的房源 ─────────
    //
    // 沒有店鋪者（含沒有店鋪的 ADMIN）的租戶是系統租戶佔位值，平台自營房源也蓋成同一個值；
    // 舊檢查只比「租戶相等」，所以下列案例的呼叫者與房源「同租戶」——守門要擋的正是這種看起來合法的相等。

    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    private Room systemTenantRoom() {
        Room room = roomWithOpenWindow();
        room.getListing().setTenantId(SYSTEM_TENANT);
        return room;
    }

    @Test
    @DisplayName("DEF-415: updateRoom 沒有店鋪的呼叫者（租戶是系統租戶）改系統租戶房源 → E_1007，且不寫入")
    void updateRoom_storelessCallerOnSystemTenantRoom_throwsForbiddenAndDoesNotSave() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Room room = systemTenantRoom();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().title("hijacked").build();

        assertThatThrownBy(() -> roomService.updateRoom(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(roomRepository, never()).save(any());
        assertThat(room.getListing().getTitle()).as("被擋之後標題不得被改").isEqualTo("Test Room");
    }

    @Test
    @DisplayName("DEF-415: clearOpenWindow 沒有店鋪的呼叫者清系統租戶房源的開放窗 → E_1007，開放窗不變")
    void clearOpenWindow_storelessCallerOnSystemTenantRoom_throwsForbiddenAndKeepsWindow() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Room room = systemTenantRoom();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.clearOpenWindow(LISTING_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(roomRepository, never()).save(any());
        assertThat(room.getOpenUntilDate()).isNotNull();
        assertThat(room.getBookingWindowDays()).isNotNull();
    }

    @Test
    @DisplayName("DEF-415: deleteRoom 沒有店鋪的呼叫者刪系統租戶房源 → E_1007，狀態不變")
    void deleteRoom_storelessCallerOnSystemTenantRoom_throwsForbiddenAndKeepsStatus() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Room room = systemTenantRoom();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.deleteRoom(LISTING_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(listingRepository, never()).save(any());
        assertThat(room.getListing().getStatus()).isEqualTo(Listing.ListingStatus.ACTIVE);
    }

    @Test
    @DisplayName("DEF-415: SUPER_ADMIN（租戶同為系統租戶）仍可管理系統租戶房源——守門不得連平台管理員一起擋")
    void updateRoom_superAdminOnSystemTenantRoom_stillAllowed() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Room room = systemTenantRoom();
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().location("Taichung").build();

        RoomDto.Response response = roomService.updateRoom(LISTING_ID, request, true);

        assertThat(response.getLocation()).isEqualTo("Taichung");
    }

    @Test
    @DisplayName("DEF-415: createRoom 沒有店鋪的呼叫者 → E_1007，且在功能開關、配額、租戶查詢之前就擋下（先判斷再查詢）")
    void createRoom_storelessCaller_throwsForbiddenBeforeAnyLookup() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        RoomDto.CreateRequest request = RoomDto.CreateRequest.builder().title("平台自營房源").build();

        assertThatThrownBy(() -> roomService.createRoom(request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(featureToggleService, never()).checkFeatureEnabled(any());
        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(tenantRepository, never()).findById(any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-415: createRoomFromDashboard 沒有店鋪的呼叫者 → E_1007，且在功能開關、配額、租戶查詢之前就擋下")
    void createRoomFromDashboard_storelessCaller_throwsForbiddenBeforeAnyLookup() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("ROOM").name("平台自營房源").price(java.math.BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> roomService.createRoomFromDashboard(request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(featureToggleService, never()).checkFeatureEnabled(any());
        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(tenantRepository, never()).findById(any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-415: SUPER_ADMIN 仍可建立房源，且蓋成系統租戶（平台自營）")
    void createRoom_superAdminInSystemTenant_createsSystemTenantListing() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                SYSTEM_TENANT, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)).thenReturn(0L);
        when(tenantRepository.findById(SYSTEM_TENANT))
                .thenReturn(Optional.of(Tenant.builder().id(SYSTEM_TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.createRoom(RoomDto.CreateRequest.builder().title("平台自營房源").build(), true);

        ArgumentCaptor<Listing> saved = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(SYSTEM_TENANT);
    }
}
