package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.CreateListingRequest;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.product.ProductService;
import com.nextkey.ecommerce.core.room.RoomService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Dashboard 統一 Listing 建立 API
 * 根據 listingType 分別呼叫 ProductService 或 RoomService
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard/listings")
@RequiredArgsConstructor
public class DashboardListingController {

    private final ProductService productService;
    private final RoomService roomService;
    private final FeatureToggleService featureToggleService;

    /**
     * 統一建立 Listing (商品或房間)
     * T-DEF-001-01: POST /v2/dashboard/listings
     *
     * @param request 建立 Listing 請求
     * @return 建立成功的 Listing 回應
     */
    @PostMapping
    @PreAuthorize("hasAuthority('product:create') or hasAuthority('room:create')")
    public ResponseEntity<ApiResponse<Object>> createListing(
            @Valid @RequestBody CreateListingRequest request) {

        log.info("Create listing request: listingType={}, title={}",
                request.getListingType(), request.getName());

        Object response;

        if ("PRODUCT".equalsIgnoreCase(request.getListingType())) {
            // 檢查 RETAIL_ENABLED feature toggle
            featureToggleService.checkFeatureEnabled("RETAIL_ENABLED");

            // 呼叫 ProductService
            response = productService.createProductFromDashboard(request);
        } else if ("ROOM".equalsIgnoreCase(request.getListingType())) {
            // 檢查 BOOKING_ENABLED feature toggle
            featureToggleService.checkFeatureEnabled("BOOKING_ENABLED");

            // 呼叫 RoomService
            response = roomService.createRoomFromDashboard(request);
        } else {
            throw new BusinessException(ErrorCode.E_3001, "Invalid listing type: " + request.getListingType());
        }

        return ResponseEntity.ok(ApiResponse.success("Listing created successfully", response));
    }
}