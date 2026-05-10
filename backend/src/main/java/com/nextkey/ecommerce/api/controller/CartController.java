package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.cart.RedisCartService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 購物車 REST API
 * 使用 Redis 儲存購物車資料
 */
@Slf4j
@RestController
@RequestMapping("/v2/cart")
@RequiredArgsConstructor
public class CartController {

    private final RedisCartService cartService;

    /**
     * 取得購物車內容
     */
    @GetMapping
    @PreAuthorize("hasAuthority('cart:read')")
    public ResponseEntity<ApiResponse<CartDto.CartResponse>> getCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID tenantId = getTenantId(principal);
        CartDto.CartResponse cart = cartService.getCart(principal.getUserId(), tenantId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    /**
     * 加入購物車
     */
    @PostMapping("/items")
    @PreAuthorize("hasAuthority('cart:update')")
    public ResponseEntity<ApiResponse<CartDto.AddItemResponse>> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CartDto.AddItemRequest request) {

        UUID tenantId = getTenantId(principal);
        CartDto.AddItemResponse result = cartService.addItem(
                principal.getUserId(), tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", result));
    }

    /**
     * 更新購物車項目數量
     * 使用 cartItemKey (格式: listingId[:skuId[:startDate:endDate]])
     */
    @PutMapping("/items/{cartItemKey}")
    @PreAuthorize("hasAuthority('cart:update')")
    public ResponseEntity<ApiResponse<CartDto.CartItemResponse>> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String cartItemKey,
            @Valid @RequestBody CartDto.UpdateItemRequest request) {

        UUID tenantId = getTenantId(principal);
        CartDto.CartItemResponse item = cartService.updateItem(
                principal.getUserId(), tenantId, cartItemKey, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", item));
    }

    /**
     * 移除購物車項目
     * 使用 cartItemKey (格式: listingId[:skuId[:startDate:endDate]])
     */
    @DeleteMapping("/items/{cartItemKey}")
    @PreAuthorize("hasAuthority('cart:update')")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String cartItemKey) {

        UUID tenantId = getTenantId(principal);
        cartService.removeItem(principal.getUserId(), tenantId, cartItemKey);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", null));
    }

    /**
     * 清空購物車
     */
    @DeleteMapping
    @PreAuthorize("hasAuthority('cart:delete')")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID tenantId = getTenantId(principal);
        cartService.clearCart(principal.getUserId(), tenantId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }

    /**
     * 取得購物車項目數量
     */
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('cart:read')")
    public ResponseEntity<ApiResponse<Integer>> getCartCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID tenantId = getTenantId(principal);
        int count = cartService.getCartItemCount(principal.getUserId(), tenantId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    private UUID getTenantId(final UserPrincipal principal) {
        if (principal.getTenantId() != null) {
            return UUID.fromString(principal.getTenantId());
        }
        // 回退到系統租戶
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}