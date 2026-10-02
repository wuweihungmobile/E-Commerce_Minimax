package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * US-001 (AI-1502): 驗證 BookingService.getUserBookings 的 roomTitle 填充（批次查詢）。
 * 純 Mockito 單元測試，聚焦列表回應的 roomTitle 對應邏輯。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sprint31-US001: BookingService 列表 roomTitle 填充")
class BookingServiceRoomTitleTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomCalendarRepository roomCalendarRepository;
    @Mock
    private RoomCalendarService roomCalendarService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Booking booking(UUID userId, UUID listingId) {
        return Booking.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .roomListingId(listingId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .guestCount(2)
                .status(Booking.BookingStatus.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(5000))
                .createdAt(Instant.now())
                .guestName("Test Guest")
                .build();
    }

    @Test
    @DisplayName("列表回應應填入房型標題（批次查詢 listing）")
    void getUserBookings_populatesRoomTitle() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);

        Page<Booking> page = new PageImpl<>(List.of(booking(userId, listingId)));
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(listingId);
        when(listing.getTitle()).thenReturn("Deluxe Room");
        when(listingRepository.findAllById(anyList())).thenReturn(List.of(listing));

        Page<BookingDto.BookingListResponse> result =
                bookingService.getUserBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRoomTitle()).isEqualTo("Deluxe Room");
    }

    @Test
    @DisplayName("找不到對應 listing 時 roomTitle 退回 Unknown（不為 null）")
    void getUserBookings_missingListing_fallsBackToUnknown() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);

        Page<Booking> page = new PageImpl<>(List.of(booking(userId, listingId)));
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);
        when(listingRepository.findAllById(anyList())).thenReturn(List.of());

        Page<BookingDto.BookingListResponse> result =
                bookingService.getUserBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent().get(0).getRoomTitle()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("Sprint 231（DEF-316）：列表回應應填入訂房人姓名（商家端需要識別是誰訂的）")
    void getUserBookings_populatesGuestName() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);

        Page<Booking> page = new PageImpl<>(List.of(booking(userId, listingId)));
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);
        when(listingRepository.findAllById(anyList())).thenReturn(List.of());

        Page<BookingDto.BookingListResponse> result =
                bookingService.getUserBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent().get(0).getGuestName()).isEqualTo("Test Guest");
    }

    // ========== getTenantBookings（Sprint 231，DEF-316：商家端訂房列表） ==========

    @Test
    @DisplayName("getTenantBookings：走 findByTenantIdOrderByCreatedAtDesc，回應含 roomTitle 與 guestName")
    void getTenantBookings_populatesRoomTitleAndGuestName() {
        UUID tenantId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        Page<Booking> page = new PageImpl<>(List.of(booking(UUID.randomUUID(), listingId)));
        when(bookingRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(page);
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(listingId);
        when(listing.getTitle()).thenReturn("Deluxe Room");
        when(listingRepository.findAllById(anyList())).thenReturn(List.of(listing));

        Page<BookingDto.BookingListResponse> result =
                bookingService.getTenantBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRoomTitle()).isEqualTo("Deluxe Room");
        assertThat(result.getContent().get(0).getGuestName()).isEqualTo("Test Guest");
    }

    @Test
    @DisplayName("getTenantBookings：size 超過上限時裁切為 100（比照 getTenantOrders 既有行為）")
    void getTenantBookings_capsPageSizeAt100() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(bookingRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        bookingService.getTenantBookings(0, 500, "createdAt", "DESC");

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(bookingRepository).findByTenantIdOrderByCreatedAtDesc(eq(tenantId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getTenantBookings：無租戶內容（tenantId 為 null）→ 回空頁、完全不查詢，不拋錯")
    void getTenantBookings_nullTenant_returnsEmptyPageWithoutQuerying() {
        // 未呼叫 TenantContext.setCurrentTenant，getCurrentTenant() 回傳 null
        Page<BookingDto.BookingListResponse> result =
                bookingService.getTenantBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).isEmpty();
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never())
                .findByTenantIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("Sprint 232：沒有店鋪的一般買家（租戶是系統租戶佔位值，不是 null）呼叫 → 回空頁、完全不查詢。"
            + "一般買家訂房蓋的正是這個租戶，查下去就把所有買家的訂房（含訂房人姓名）交給任一登入者")
    void getTenantBookings_systemTenantCaller_returnsEmptyPageWithoutQuerying() {
        TenantContext.setCurrentTenant(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));

        Page<BookingDto.BookingListResponse> result =
                bookingService.getTenantBookings(0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).isEmpty();
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never())
                .findByTenantIdOrderByCreatedAtDesc(any(), any());
    }
}
