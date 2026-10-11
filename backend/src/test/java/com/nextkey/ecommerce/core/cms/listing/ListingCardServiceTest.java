package com.nextkey.ecommerce.core.cms.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;

/**
 * ListingCardService 單元測試（DEF-321c）。
 *
 * <p>部落格嵌入卡片的 {@code ctaUrl} 先前指向 {@code /products/{id}}／{@code /rooms/{id}}，
 * 但前端實際的刊登詳情頁僅有 {@code /listings/[id]}，兩者皆為死連結。此測試鎖住修復後的行為。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListingCardService 單元測試 (DEF-321c)")
class ListingCardServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomCalendarRepository roomCalendarRepository;

    @InjectMocks
    private ListingCardService listingCardService;

    private Listing listingOf(final Listing.ListingType type) {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Test Tenant").build();
        return Listing.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .listingType(type)
                .title("Test Listing")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getListingCard：PRODUCT 卡片的 ctaUrl 指向 /listings/{id}，不是 /products/{id}")
    void getListingCard_product_ctaUrlPointsToListingsPath() {
        Listing listing = listingOf(Listing.ListingType.PRODUCT);
        when(listingRepository.findByIdWithTenant(listing.getId())).thenReturn(Optional.of(listing));
        when(productInventoryRepository.existsByListingId(listing.getId())).thenReturn(false);

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listing.getId());

        assertThat(response.getCtaUrl()).isEqualTo("/listings/" + listing.getId());
    }

    @Test
    @DisplayName("getListingCard：ROOM 卡片的 ctaUrl 指向 /listings/{id}，不是 /rooms/{id}")
    void getListingCard_room_ctaUrlPointsToListingsPath() {
        Listing listing = listingOf(Listing.ListingType.ROOM);
        when(listingRepository.findByIdWithTenant(listing.getId())).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.existsByListingIdAndCalendarDateBetweenAndStatus(
                eq(listing.getId()), any(LocalDate.class), any(LocalDate.class),
                eq(RoomCalendar.RoomCalendarStatus.MAINTENANCE)))
                .thenReturn(false);

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listing.getId());

        assertThat(response.getCtaUrl()).isEqualTo("/listings/" + listing.getId());
    }
}
