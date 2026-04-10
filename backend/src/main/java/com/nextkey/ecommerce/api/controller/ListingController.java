package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v2/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingRepository listingRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<Page<Listing>>> getListings(
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

        return ResponseEntity.ok(ApiResponse.success(listings));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<Listing>> getListing(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));

        return ResponseEntity.ok(ApiResponse.success(listing));
    }
}
