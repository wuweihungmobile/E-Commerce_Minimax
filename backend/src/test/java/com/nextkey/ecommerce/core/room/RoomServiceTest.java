package com.nextkey.ecommerce.core.room;

import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
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
import static org.mockito.Mockito.when;

/**
 * RoomService 單元測試——開放窗清除機制（Sprint 57 AI-2202f）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService: 開放窗清除機制")
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    @InjectMocks
    private RoomService roomService;

    private static final UUID LISTING_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440004");

    private Room roomWithOpenWindow() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .title("Test Room")
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

        RoomDto.Response response = roomService.clearOpenWindow(LISTING_ID);

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

        roomService.clearOpenWindow(LISTING_ID);

        assertThat(room.getLocation()).isEqualTo("Taipei");
    }

    @Test
    @DisplayName("UT-ROOM-003: clearOpenWindow 找不到房源 → 拋 BusinessException")
    void clearOpenWindow_roomNotFound_throws() {
        when(roomRepository.findByListingId(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.clearOpenWindow(LISTING_ID))
                .isInstanceOf(BusinessException.class);
    }
}
