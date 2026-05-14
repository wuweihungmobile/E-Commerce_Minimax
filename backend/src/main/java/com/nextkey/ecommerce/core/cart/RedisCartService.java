package com.nextkey.ecommerce.core.cart;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.CartEmptyException;
import com.nextkey.ecommerce.shared.exception.CartItemNotFoundException;
import com.nextkey.ecommerce.shared.exception.PromoCodeInvalidException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 購物車服務
 * 使用 Redis Hash 儲存購物車資料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ListingRepository listingRepository;
    private final ProductSkuRepository productSkuRepository;
    private final PromoService promoService;
    private final PromoCodeRepository promoCodeRepository;

    private static final String CART_KEY_PREFIX = AppConstants.REDIS_CART_PREFIX;
    private static final Duration CART_TTL = Duration.ofDays(30); // 購物車保留 30 天

    /**
     * 加入購物車
     */
    public CartDto.AddItemResponse addItem(UUID userId, UUID tenantId, CartDto.AddItemRequest request) {
        String cartKey = getCartKey(userId, tenantId);
        String itemKey = getItemKey(request.getListingId(), request.getSkuId());

        // 檢查商品是否存在
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found: " + request.getListingId()));

        BigDecimal unitPrice = listing.getBasePrice();
        String skuCode = null;
        String specName = null;

        // 如果有 SKU，獲取 SKU 資訊
        if (request.getSkuId() != null) {
            ProductSku sku = productSkuRepository.findById(request.getSkuId()).orElse(null);
            if (sku != null) {
                unitPrice = sku.getPriceOverride() != null ? sku.getPriceOverride() : unitPrice;
                skuCode = sku.getSkuCode();
                specName = sku.getSpecName();
            }
        }

        // ROOM 類型房源需要日期驗證
        if (listing.getListingType() == Listing.ListingType.ROOM) {
            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new IllegalArgumentException("Start date and end date are required for ROOM listing");
            }
            if (!request.getEndDate().isAfter(request.getStartDate())) {
                throw new IllegalArgumentException("End date must be after start date");
            }
        }

        // 檢查是否已有相同商品在購物車
        Object existingItem = redisTemplate.opsForHash().get(cartKey, itemKey);
        int newQuantity = request.getQuantity();

        if (existingItem != null) {
            // 更新數量
            CartItemData existing = (CartItemData) existingItem;
            newQuantity += existing.quantity;
        }

        // 創建購物車項目
        BigDecimal subtotal = CartItemData.computeSubtotal(unitPrice, newQuantity);
        CartItemData item = CartItemData.builder()
                .listingId(request.getListingId())
                .skuId(request.getSkuId())
                .skuCode(skuCode)
                .specName(specName)
                .quantity(newQuantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .listingType(listing.getListingType().name())
                .addedAt(Instant.now())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        // 儲存到 Redis
        redisTemplate.opsForHash().put(cartKey, itemKey, item);
        redisTemplate.expire(cartKey, CART_TTL);

        // 計算返回結果
        CartDto.CartItemResponse itemResponse = toCartItemResponse(itemKey, item, listing);
        int totalItems = getTotalItemsCount(cartKey);

        log.info("Added item to cart: userId={}, listingId={}, quantity={}", userId, request.getListingId(), newQuantity);

        return CartDto.AddItemResponse.builder()
                .success(true)
                .item(itemResponse)
                .totalItemsInCart(totalItems)
                .message("Item added to cart successfully")
                .build();
    }

    /**
     * 更新購物車項目數量
     */
    public CartDto.CartItemResponse updateItem(UUID userId, UUID tenantId, UUID listingId, UUID skuId, int quantity) {
        String cartKey = getCartKey(userId, tenantId);
        String itemKey = getItemKey(listingId, skuId);

        Object existingItem = redisTemplate.opsForHash().get(cartKey, itemKey);
        if (existingItem == null) {
            throw new CartItemNotFoundException("Cart item not found: " + itemKey);
        }

        CartItemData item = (CartItemData) existingItem;
        item.setQuantity(quantity);
        item.setSubtotal(CartItemData.computeSubtotal(item.getUnitPrice(), quantity));

        redisTemplate.opsForHash().put(cartKey, itemKey, item);
        redisTemplate.expire(cartKey, CART_TTL);

        // 獲取 listing 資訊
        Listing listing = listingRepository.findById(listingId).orElse(null);
        CartDto.CartItemResponse response = toCartItemResponse(itemKey, item, listing);

        log.info("Updated cart item: userId={}, listingId={}, quantity={}", userId, listingId, quantity);
        return response;
    }

    /**
     * 更新購物車項目數量 (使用 cartItemKey)
     */
    public CartDto.CartItemResponse updateItem(UUID userId, UUID tenantId, String cartItemKey, int quantity) {
        // 嘗試解析 cartItemKey，格式可能是 listingId[:skuId[:startDate:endDate]]
        try {
            String[] parts = cartItemKey.split(":");
            UUID listingId = UUID.fromString(parts[0]);
            UUID skuId = parts.length > 1 && !parts[1].isEmpty() ? UUID.fromString(parts[1]) : null;
            return updateItem(userId, tenantId, listingId, skuId, quantity);
        } catch (IllegalArgumentException e) {
            throw new CartItemNotFoundException("Cart item not found: " + cartItemKey);
        }
    }

    /**
     * 移除購物車項目
     */
    public void removeItem(UUID userId, UUID tenantId, UUID listingId, UUID skuId) {
        String cartKey = getCartKey(userId, tenantId);
        String itemKey = getItemKey(listingId, skuId);

        // 檢查項目是否存在
        Object existingItem = redisTemplate.opsForHash().get(cartKey, itemKey);
        if (existingItem == null) {
            throw new CartItemNotFoundException("Cart item not found: " + itemKey);
        }

        redisTemplate.opsForHash().delete(cartKey, itemKey);
        log.info("Removed item from cart: userId={}, listingId={}", userId, listingId);
    }

    /**
     * 移除購物車項目 (使用 cartItemKey)
     */
    public void removeItem(UUID userId, UUID tenantId, String cartItemKey) {
        // 嘗試解析 cartItemKey，格式可能是 listingId[:skuId]
        try {
            String[] parts = cartItemKey.split(":");
            UUID listingId = UUID.fromString(parts[0]);
            UUID skuId = parts.length > 1 && !parts[1].isEmpty() ? UUID.fromString(parts[1]) : null;
            removeItem(userId, tenantId, listingId, skuId);
        } catch (IllegalArgumentException e) {
            throw new CartItemNotFoundException("Cart item not found: " + cartItemKey);
        }
    }

    /**
     * 清除用戶購物車
     */
    public void clearCart(UUID userId, UUID tenantId) {
        String cartKey = getCartKey(userId, tenantId);
        redisTemplate.delete(cartKey);
        log.info("Cleared cart: userId={}", userId);
    }

    /**
     * 獲取用戶購物車
     */
    public CartDto.CartResponse getCart(UUID userId, UUID tenantId) {
        String cartKey = getCartKey(userId, tenantId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);

        if (entries.isEmpty()) {
            return CartDto.CartResponse.builder()
                    .userId(userId)
                    .cartId(cartKey)
                    .items(Collections.emptyList())
                    .itemCount(0)
                    .totalAmount(BigDecimal.ZERO)
                    .currency("TWD")
                    .updatedAt(Instant.now())
                    .build();
        }

        // 獲取所有 listing IDs
        Set<UUID> listingIds = entries.values().stream()
                .map(item -> (CartItemData) item)
                .map(CartItemData::getListingId)
                .collect(Collectors.toSet());

        // 批量獲取 listing 資訊
        Map<UUID, Listing> listingMap = new HashMap<>();
        listingRepository.findAllById(listingIds).forEach(listing -> listingMap.put(listing.getId(), listing));

        // 轉換為回應物件
        List<CartDto.CartItemResponse> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String itemKey = (String) entry.getKey();
            CartItemData item = (CartItemData) entry.getValue();
            Listing listing = listingMap.get(item.getListingId());
            CartDto.CartItemResponse itemResponse = toCartItemResponse(itemKey, item, listing);
            items.add(itemResponse);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        return CartDto.CartResponse.builder()
                .userId(userId)
                .cartId(cartKey)
                .items(items)
                .itemCount(getTotalItemsCount(cartKey))
                .totalAmount(totalAmount)
                .currency("TWD")
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * 獲取購物車項目數量
     */
    public int getCartItemCount(UUID userId, UUID tenantId) {
        String cartKey = getCartKey(userId, tenantId);
        return getTotalItemsCount(cartKey);
    }

    /**
     * 套用優惠券至購物車
     */
    public CartDto.ApplyPromoResponse applyPromoCode(UUID userId, UUID tenantId, String promoCode) {
        // 1. 驗證購物車不為空
        CartDto.CartResponse cart = getCart(userId, tenantId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cannot apply promo to empty cart");
        }

        // 2. 驗證優惠券
        CartDto.PromoValidationResult validation = promoService.validatePromoCode(promoCode, tenantId);
        if (!validation.isValid()) {
            throw new PromoCodeInvalidException(promoCode, validation.getInvalidReason());
        }

        // 3. 取得 PromoCode 實體並計算折扣
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseAndTenantId(
                promoCode.trim().toUpperCase(), tenantId
        ).orElseThrow(() -> new PromoCodeInvalidException(promoCode, "INVALID"));

        BigDecimal discount = promoService.computeDiscount(promo, cart.getTotalAmount());
        BigDecimal finalAmount = cart.getTotalAmount().subtract(discount);

        // 4. 更新 Redis 中的優惠券標記
        String promoKey = getPromoKey(userId, tenantId);
        redisTemplate.opsForValue().set(promoKey, promoCode.toUpperCase(), CART_TTL);

        log.info("Applied promo code: {} to cart: {}, discount: {}", promoCode, cart.getCartId(), discount);

        return CartDto.ApplyPromoResponse.builder()
                .appliedPromoCode(promoCode.toUpperCase())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .discountType(validation.getDiscountType())
                .discountValue(validation.getDiscountValue())
                .build();
    }

    /**
     * 移除購物車優惠券
     */
    public void removePromoCode(UUID userId, UUID tenantId) {
        String promoKey = getPromoKey(userId, tenantId);
        redisTemplate.delete(promoKey);
        log.info("Removed promo code from cart: userId={}", userId);
    }

    /**
     * 驗證優惠券（不套用）
     */
    public CartDto.PromoValidationResult validatePromoCode(String promoCode, UUID tenantId) {
        return promoService.validatePromoCode(promoCode, tenantId);
    }

    /**
     * 取得購物車含優惠券資訊
     */
    public CartDto.CartResponse getCartWithPromo(UUID userId, UUID tenantId) {
        CartDto.CartResponse cart = getCart(userId, tenantId);

        // 檢查是否有已套用的優惠券
        String promoKey = getPromoKey(userId, tenantId);
        Object savedPromoCode = redisTemplate.opsForValue().get(promoKey);

        if (savedPromoCode != null) {
            cart.setAppliedPromoCode((String) savedPromoCode);

            // 重新計算折扣
            try {
                CartDto.PromoValidationResult validation = promoService.validatePromoCode(
                        (String) savedPromoCode, tenantId);
                if (validation.isValid()) {
                    PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseAndTenantId(
                            (String) savedPromoCode, tenantId
                    ).orElse(null);
                    if (promo != null) {
                        BigDecimal discount = promoService.computeDiscount(promo, cart.getTotalAmount());
                        cart.setDiscountAmount(discount);
                        cart.setFinalAmount(cart.getTotalAmount().subtract(discount));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to calculate promo discount for cart: {}", cart.getCartId(), e);
            }
        }

        return cart;
    }

    // ========== Helper Methods ==========

    private String getCartKey(UUID userId, UUID tenantId) {
        return CART_KEY_PREFIX + userId.toString() + ":" + tenantId.toString();
    }

    private String getItemKey(UUID listingId, UUID skuId) {
        if (skuId != null) {
            return listingId.toString() + ":" + skuId.toString();
        }
        return listingId.toString();
    }

    private String getPromoKey(UUID userId, UUID tenantId) {
        return CART_KEY_PREFIX + "promo:" + userId.toString() + ":" + tenantId.toString();
    }

    private int getTotalItemsCount(String cartKey) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        return entries.values().stream()
                .map(item -> (CartItemData) item)
                .mapToInt(CartItemData::getQuantity)
                .sum();
    }

    private CartDto.CartItemResponse toCartItemResponse(String itemKey, CartItemData item, Listing listing) {
        String title = listing != null ? listing.getTitle() : "Unknown";
        String coverImageUrl = listing != null ? listing.getCoverImageUrl() : null;

        return CartDto.CartItemResponse.builder()
                .cartItemKey(itemKey)
                .listingId(item.getListingId())
                .listingName(title)
                .coverImageUrl(coverImageUrl)
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .specName(item.getSpecName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .listingType(item.getListingType())
                .addedAt(item.getAddedAt())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .build();
    }

    /**
     * 購物車項目資料結構
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CartItemData {
        private UUID listingId;
        private UUID skuId;
        private String skuCode;
        private String specName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal; // Stored in Redis for serialization
        private String listingType;
        private Instant addedAt;
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;

        /**
         * 計算小計（僅用於建構時）
         */
        public static BigDecimal computeSubtotal(BigDecimal unitPrice, Integer quantity) {
            if (unitPrice == null || quantity == null) {
                return BigDecimal.ZERO;
            }
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}