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
 * ListingCardService 單元測試（DEF-321c／DEF-321d）。
 *
 * <p>DEF-321c：部落格嵌入卡片的 {@code ctaUrl} 先前指向 {@code /products/{id}}／{@code /rooms/{id}}，
 * 但前端實際的刊登詳情頁僅有 {@code /listings/[id]}，兩者皆為死連結。此測試鎖住修復後的行為。
 *
 * <p>DEF-321d：PRD §6.6.3 規格——Listing 非 ACTIVE 時導向店鋪（非失效連結）；
 * ROOM 類型 MAINTENANCE 時導向客服且無定價。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListingCardService 單元測試 (DEF-321c/d)")
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
        return listingOf(type, Listing.ListingStatus.ACTIVE, "test-tenant");
    }

    private Listing listingOf(final Listing.ListingType type, final Listing.ListingStatus status,
            final String tenantSlug) {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Test Tenant").slug(tenantSlug).build();
        return Listing.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .listingType(type)
                .title("Test Listing")
                .basePrice(new BigDecimal("100.00"))
                .status(status)
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

    @Test
    @DisplayName("getListingCard：PRODUCT 已下架（INACTIVE）時導向店鋪，不是失效的 /listings/{id}")
    void getListingCard_inactiveProduct_ctaUrlPointsToStore() {
        Listing listing = listingOf(Listing.ListingType.PRODUCT, Listing.ListingStatus.INACTIVE, "acme-shop");
        when(listingRepository.findByIdWithTenant(listing.getId())).thenReturn(Optional.of(listing));
        when(productInventoryRepository.existsByListingId(listing.getId())).thenReturn(false);

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listing.getId());

        assertThat(response.getCtaUrl()).isEqualTo("/stores/acme-shop");
        assertThat(response.getStatusReason()).isEqualTo("listing_unavailable");
        assertThat(response.getAvailability().getAvailable()).isFalse();
    }

    @Test
    @DisplayName("getListingCard：ROOM 已下架（DELETED）時導向店鋪，不是失效的 /listings/{id}")
    void getListingCard_deletedRoom_ctaUrlPointsToStore() {
        Listing listing = listingOf(Listing.ListingType.ROOM, Listing.ListingStatus.DELETED, "acme-shop");
        when(listingRepository.findByIdWithTenant(listing.getId())).thenReturn(Optional.of(listing));

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listing.getId());

        assertThat(response.getCtaUrl()).isEqualTo("/stores/acme-shop");
        assertThat(response.getStatusReason()).isEqualTo("listing_unavailable");
    }

    @Test
    @DisplayName("getListingCard：ROOM 維護中（MAINTENANCE）時導向客服、無定價，依 PRD §6.6.3")
    void getListingCard_roomUnderMaintenance_ctaUrlIsContactAndPriceIsNull() {
        Listing listing = listingOf(Listing.ListingType.ROOM);
        when(listingRepository.findByIdWithTenant(listing.getId())).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.existsByListingIdAndCalendarDateBetweenAndStatus(
                eq(listing.getId()), any(LocalDate.class), any(LocalDate.class),
                eq(RoomCalendar.RoomCalendarStatus.MAINTENANCE)))
                .thenReturn(true);

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listing.getId());

        assertThat(response.getCtaUrl()).isEqualTo("/contact");
        assertThat(response.getCurrentPrice()).isNull();
        assertThat(response.getStatusReason()).isEqualTo("under_maintenance");
        assertThat(response.getAvailability().getAvailable()).isFalse();
    }
}
