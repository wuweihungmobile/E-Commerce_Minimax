package com.nextkey.ecommerce.core.cms.listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.inventory.Inventory;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.InventoryRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * M15 CMS ListingCard Service
 * 嵌入卡片 API 服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingCardService {

    private final ListingRepository listingRepository;
    private final InventoryRepository inventoryRepository;
    @SuppressWarnings("unused")
    private final RoomRepository roomRepository;
    private final RoomCalendarRepository roomCalendarRepository;

    /**
     * 取得嵌入卡片資訊
     * 支援 PRODUCT 和 ROOM 類型
     */
    @Transactional(readOnly = true)
    public M15Dto.ListingCardResponse getListingCard(UUID listingId) {
        Listing listing = listingRepository.findByIdWithTenant(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));

        // 根據類型取得卡片資訊
        if (listing.getListingType() == Listing.ListingType.PRODUCT) {
            return buildProductCard(listing);
        } else if (listing.getListingType() == Listing.ListingType.ROOM) {
            return buildRoomCard(listing);
        } else {
            throw new BusinessException(ErrorCode.E_3001);
        }
    }

    /**
     * 建立商品卡片
     */
    private M15Dto.ListingCardResponse buildProductCard(Listing listing) {
        // 查詢此 listing 的庫存
        Integer availableQty = null;
        Boolean inStock = false;

        var inventories = inventoryRepository.findByListingId(listing.getId());
        if (!inventories.isEmpty()) {
            int totalQty = inventories.stream()
                    .filter(i -> i.getAvailableQty() != null)
                    .mapToInt(Inventory::getAvailableQty)
                    .sum();
            availableQty = totalQty;
            inStock = totalQty > 0;
        }

        // 目前價格（取第一個庫存的有效價格或使用掛牌價）
        BigDecimal currentPrice = listing.getBasePrice();
        // 如果有促銷邏輯，這裡可以整合

        M15Dto.ListingCardResponse response = M15Dto.ListingCardResponse.builder()
                .listingId(listing.getId())
                .listingType(listing.getListingType().name())
                .title(listing.getTitle())
                .coverImageUrl(listing.getCoverImageUrl())
                .basePrice(listing.getBasePrice())
                .currentPrice(currentPrice)
                .currency("TWD")
                .availability(M15Dto.ListingCardResponse.AvailabilityInfo.builder()
                        .inStock(inStock)
                        .availableQty(availableQty)
                        .available(inStock)
                        .build())
                .tenantName(listing.getTenant().getName())
                .ctaUrl("/products/" + listing.getId())
                .isActive(listing.getStatus() == Listing.ListingStatus.ACTIVE)
                .statusReason(listing.getStatus() != Listing.ListingStatus.ACTIVE ? "listing_inactive" : null)
                .build();

        return response;
    }

    /**
     * 建立房型卡片
     */
    private M15Dto.ListingCardResponse buildRoomCard(Listing listing) {
        // 查詢 Room 取得維護狀態
        Boolean available = true;
        String statusReason = null;

        // 檢查 Listing 狀態
        if (listing.getStatus() != Listing.ListingStatus.ACTIVE) {
            available = false;
            statusReason = "listing_inactive";
        } else {
            // 檢查是否有 MAINTENANCE 日曆记录
            // 如果未來的日曆中有 MAINTENANCE 狀態的日期，房型處於維護中狀態
            boolean hasMaintenance = roomCalendarRepository.existsByListingIdAndCalendarDateBetweenAndStatus(
                    listing.getId(),
                    LocalDate.now(),
                    LocalDate.now().plusMonths(3),
                    RoomCalendar.RoomCalendarStatus.MAINTENANCE
            );
            if (hasMaintenance) {
                available = false;
                statusReason = "under_maintenance";
            }
        }

        M15Dto.ListingCardResponse response = M15Dto.ListingCardResponse.builder()
                .listingId(listing.getId())
                .listingType(listing.getListingType().name())
                .title(listing.getTitle())
                .coverImageUrl(listing.getCoverImageUrl())
                .basePrice(listing.getBasePrice())
                .currentPrice(listing.getBasePrice()) // Room 使用動態定價
                .currency("TWD")
                .availability(M15Dto.ListingCardResponse.AvailabilityInfo.builder()
                        .inStock(available)
                        .availableQty(null) // Room 不使用庫存數量
                        .available(available)
                        .build())
                .tenantName(listing.getTenant().getName())
                .ctaUrl("/rooms/" + listing.getId())
                .isActive(listing.getStatus() == Listing.ListingStatus.ACTIVE)
                .statusReason(statusReason)
                .build();

        return response;
    }
}