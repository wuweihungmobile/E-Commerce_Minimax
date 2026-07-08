package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * BookingService.getCalendarForOwner 單元測試（Sprint 83，PRD P0：房東後台 90 天定價日曆預覽）。
 *
 * <p>驗證租戶擁有權檢查：非 SUPER_ADMIN 僅能檢視自己租戶房源的定價日曆，SUPER_ADMIN 可跨租戶。
 * 資料內容本身沿用既有 {@code getCalendar} 邏輯，不重複驗證（已由 {@code BookingServiceOpenWindowTest} 等覆蓋）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService: 房東後台定價日曆擁有權檢查 (Sprint 83)")
class BookingServiceOwnerCalendarTest {

    @Mock private com.nextkey.ecommerce.domain.repository.BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomRepository roomRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomCalendarRepository roomCalendarRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private com.nextkey.ecommerce.domain.repository.TenantRepository tenantRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.UserRepository userRepository;
    @Mock private PricingService pricingService;
    @Mock private FeatureToggleService featureToggleService;

    @InjectMocks private BookingService bookingService;

    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final UUID OWN_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Listing roomListing(final UUID tenantId) {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .tenantId(tenantId)
                .basePrice(BASE_PRICE)
                .currency("TWD")
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing.setId(ROOM_LISTING_ID);
        return listing;
    }

    @Test
    @DisplayName("🔴 非 SUPER_ADMIN 檢視他租戶房源定價日曆應被拒絕")
    void getCalendarForOwner_crossTenantNonSuperAdmin_denied() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(OWN_TENANT_ID);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(89);

        assertThatThrownBy(() -> bookingService.getCalendarForOwner(ROOM_LISTING_ID, start, end, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("非 SUPER_ADMIN 檢視自己租戶房源定價日曆應成功")
    void getCalendarForOwner_ownTenant_succeeds() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing(OWN_TENANT_ID)));
        TenantContext.setCurrentTenant(OWN_TENANT_ID);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(5);
        when(roomCalendarService.getCalendarRange(ROOM_LISTING_ID, start, end)).thenReturn(Collections.emptyList());
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().build()));

        List<BookingDto.CalendarResponse> result = bookingService.getCalendarForOwner(ROOM_LISTING_ID, start, end, false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN 可跨租戶檢視任意房源定價日曆")
    void getCalendarForOwner_superAdmin_bypassesTenantCheck() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(OWN_TENANT_ID);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(5);
        when(roomCalendarService.getCalendarRange(ROOM_LISTING_ID, start, end)).thenReturn(Collections.emptyList());
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().build()));

        List<BookingDto.CalendarResponse> result = bookingService.getCalendarForOwner(ROOM_LISTING_ID, start, end, true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("房源不存在拋出 E_4000")
    void getCalendarForOwner_listingNotFound_throwsE4000() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.empty());
        TenantContext.setCurrentTenant(OWN_TENANT_ID);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(5);

        assertThatThrownBy(() -> bookingService.getCalendarForOwner(ROOM_LISTING_ID, start, end, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4000));
    }
}
