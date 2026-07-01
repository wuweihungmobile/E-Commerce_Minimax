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
}
