package com.nextkey.ecommerce.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ListingResponse;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RestController
@RequestMapping("/v2/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingRepository listingRepository;
    private final PricingService pricingService;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<Page<ListingResponse>>> getListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        Page<Listing> listings;

        if (keyword != null && !keyword.isBlank()) {
            if (type != null && !type.isBlank()) {
                Listing.ListingType listingType = Listing.ListingType.valueOf(type.toUpperCase());
                listings = listingRepository.searchByTypeAndKeyword(listingType, keyword, pageRequest);
            } else {
                listings = listingRepository.searchByKeyword(keyword, pageRequest);
            }
        } else if (type != null && !type.isBlank()) {
            Listing.ListingType listingType = Listing.ListingType.valueOf(type.toUpperCase());
            listings = listingRepository.findByListingTypeAndStatus(
                    listingType,
                    Listing.ListingStatus.ACTIVE,
                    pageRequest
            );
        } else {
            listings = listingRepository.findByStatus(Listing.ListingStatus.ACTIVE, pageRequest);
        }

        return ResponseEntity.ok(ApiResponse.success(listings.map(ListingResponse::fromEntity)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<ListingResponse>> getListing(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));

        return ResponseEntity.ok(ApiResponse.success(ListingResponse.fromEntity(listing)));
    }

    /**
     * 動態價格計算端點
     * T-M12-01: GET /api/v2/listings/:id/price?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD
     */
    @GetMapping("/{id}/price")
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<PricingDto.CalculatePriceResponse>> getListingPrice(
            @PathVariable UUID id,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut) {

        log.info("Get listing price: id={}, checkIn={}, checkOut={}", id, checkIn, checkOut);

        PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                .roomListingId(id)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();

        PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{listingId}/effective-price")
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<PricingDto.EffectivePriceResponse>> getEffectivePrice(
            @PathVariable UUID listingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkDate,
            @RequestParam(defaultValue = "1") int stayDays) {

        log.info("Get effective price: listingId={}, checkDate={}, stayDays={}", listingId, checkDate, stayDays);
        PricingDto.EffectivePriceResponse response = pricingService.getEffectivePrice(listingId, checkDate, stayDays);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
