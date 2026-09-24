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
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.core.pricing.PricingService;
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
import com.nextkey.ecommerce.shared.time.BusinessTime;

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
    private final PricingService pricingService;
    private final FeatureToggleService featureToggleService;
    private final ShippingTemplateService shippingTemplateService;

    private static final String CART_KEY_PREFIX = AppConstants.REDIS_CART_PREFIX;
    private static final Duration CART_TTL = Duration.ofDays(30); // 購物車保留 30 天

    /**
     * 加入購物車
     */
    public CartDto.AddItemResponse addItem(UUID userId, UUID tenantId, CartDto.AddItemRequest request) {
        String cartKey = getCartKey(userId, tenantId);

        // 檢查商品是否存在
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found: " + request.getListingId()));

        BigDecimal unitPrice = listing.getBasePrice();
        String skuCode = null;
        String specName = null;

        // 如果有 SKU，獲取 SKU 資訊。DEF-237：先前只用 skuId 單獨查表，從未驗證此 SKU 是否真的
        // 屬於 request.getListingId()——可用自己的 listing 搭配他租戶的真實 SKU UUID（可從公開的
        // GET /v2/products/{listingId}/skus 查到）加入購物車，經 OrderService.buildProductOrder
        // 結帳後任意竄改他租戶庫存。不屬於此 listing 的 skuId 視同「SKU 不存在」，比照既有的
        // 「skuId 找不到」容錯語意（不拋例外、靜默退回無規格），不引入新的失敗模式。
        UUID effectiveSkuId = request.getSkuId();
        if (effectiveSkuId != null) {
            ProductSku sku = productSkuRepository.findById(effectiveSkuId).orElse(null);
            if (sku != null && request.getListingId().equals(sku.getProductListingId())) {
                unitPrice = sku.getPriceOverride() != null ? sku.getPriceOverride() : unitPrice;
                skuCode = sku.getSkuCode();
                specName = sku.getSpecName();
            } else {
                effectiveSkuId = null;
            }
        }

        String itemKey = getItemKey(request.getListingId(), effectiveSkuId);

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
                .skuId(effectiveSkuId)
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
            // 使用回應（可能已套動態定價折扣）之 subtotal，使總額與顯示一致（AI-2403）
            totalAmount = totalAmount.add(itemResponse.getSubtotal());
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

        BigDecimal shippingFee = previewShippingFee(cart, tenantId);
        BigDecimal discount = promoService.computeDiscount(promo, cart.getTotalAmount(), shippingFee);
        BigDecimal finalAmount = cart.getTotalAmount().add(shippingFee).subtract(discount);

        // 4. 更新 Redis 中的優惠券標記
        String promoKey = getPromoKey(userId, tenantId);
        redisTemplate.opsForValue().set(promoKey, promoCode.toUpperCase(), CART_TTL);

        log.info("Applied promo code: {} to cart: {}, discount: {}", promoCode, cart.getCartId(), discount);

        return CartDto.ApplyPromoResponse.builder()
                .appliedPromoCode(promoCode.toUpperCase())
                .shippingFee(shippingFee)
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
     * 取得購物車目前已套用的促銷碼（Sprint 100）。
     *
     * <p>供結帳流程（{@code OrderService}）取得券碼後自行重新驗證用——刻意不回傳折扣金額：
     * {@link #getCartWithPromo} 的折扣是 fallback-tolerant 的顯示用計算（券失效時靜默回退原價），
     * 不可作為收款依據，訂單金額必須以結帳當下重新驗證的結果為準（PRD §9.5.1）。
     *
     * @return 已套用的促銷碼；未套用時為 {@code null}
     */
    public String getAppliedPromoCode(UUID userId, UUID tenantId) {
        Object savedPromoCode = redisTemplate.opsForValue().get(getPromoKey(userId, tenantId));
        return savedPromoCode == null ? null : savedPromoCode.toString();
    }

    /**
     * 取得購物車含優惠券資訊
     */
    public CartDto.CartResponse getCartWithPromo(UUID userId, UUID tenantId) {
        CartDto.CartResponse cart = getCart(userId, tenantId);

        // Sprint 101（AI-2435）：運費預覽與是否套券無關，一律計算並回填，
        // 使購物車顯示的應付金額與結帳實收同構（此前購物車完全不顯示運費）
        BigDecimal shippingFee = previewShippingFee(cart, tenantId);
        cart.setShippingFee(shippingFee);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setFinalAmount(cart.getTotalAmount().add(shippingFee));

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
                        BigDecimal discount = promoService.computeDiscount(
                                promo, cart.getTotalAmount(), shippingFee);
                        cart.setDiscountAmount(discount);
                        cart.setFinalAmount(cart.getTotalAmount().add(shippingFee).subtract(discount));
                    }
                }
            } catch (RuntimeException e) {
                // Promo discount fallback：折扣計算失敗不影響購物車讀取，cart 仍以原價回傳
                log.warn("Failed to calculate promo discount for cart: {}", cart.getCartId(), e);
            }
        }

        return cart;
    }

    // ========== Helper Methods ==========

    /**
     * 預估購物車運費（Sprint 101 / AI-2435）。
     *
     * <p>基數刻意只取 PRODUCT 項目小計，與 {@code OrderService.createOrderFromCart} 一致
     * （後者只結 PRODUCT 項目，ROOM 另行結帳）。純 ROOM 或空購物車直接回 0——否則 FIXED 型
     * 運費模板會對沒有實體出貨的訂房購物車顯示一筆固定運費。
     */
    private BigDecimal previewShippingFee(CartDto.CartResponse cart, UUID tenantId) {
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal productSubtotal = cart.getItems().stream()
                .filter(item -> "PRODUCT".equals(item.getListingType()))
                .map(CartDto.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (productSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return shippingTemplateService.calculateFeeForTenant(tenantId, productSubtotal);
    }

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

        BigDecimal unitPrice = item.getUnitPrice();
        BigDecimal subtotal = item.getSubtotal();
        BigDecimal originalUnitPrice = null;
        BigDecimal discountAmount = null;
        String appliedRuleName = null;
        String priceAdjustmentType = null;

        // 動態定價（PRODUCT）：DYNAMIC_PRICING_ENABLED 開啟且規則調整後單價 ≠ 現價時，
        // 以調整後單價重算 unitPrice/subtotal（讀取時算，Redis 只存 basePrice 避免 stale，AI-2403）。
        // AI-2406c：放寬含漲價（effectivePrice > 現價亦計入）；discountAmount 為有號差額
        // （正=折扣、負=加價），priceAdjustmentType 明示方向。ROOM 不走此路徑（其計價於 booking，S43/S46）。
        PricingDto.EffectivePriceResponse adjustment = tryProductAdjustment(item, listing);
        if (adjustment != null) {
            originalUnitPrice = unitPrice;
            unitPrice = adjustment.getEffectivePrice();
            subtotal = CartItemData.computeSubtotal(unitPrice, item.getQuantity());
            discountAmount = originalUnitPrice.subtract(unitPrice)
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            appliedRuleName = adjustment.getAppliedRuleName();
            priceAdjustmentType = adjustmentDirection(originalUnitPrice, unitPrice);
        }

        return CartDto.CartItemResponse.builder()
                .cartItemKey(itemKey)
                .listingId(item.getListingId())
                .listingName(title)
                .coverImageUrl(coverImageUrl)
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .specName(item.getSpecName())
                .quantity(item.getQuantity())
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .listingType(item.getListingType())
                .addedAt(item.getAddedAt())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .originalUnitPrice(originalUnitPrice)
                .discountAmount(discountAmount)
                .appliedRuleName(appliedRuleName)
                .priceAdjustmentType(priceAdjustmentType)
                .build();
    }

    /**
     * PRODUCT 動態定價調整試算（AI-2403 折扣；AI-2406c 放寬含漲價）：toggle 開啟、為 PRODUCT、
     * 且規則調整後單價 ≠ 現存單價時回結果（含折扣 effectivePrice&lt;現價 或漲價 &gt;現價），
     * 否則回 null（呼叫端 fallback 原價，向後相容）。計算失敗降級為不套用，避免阻斷購物車。
     * 以「今日」為規則有效期基準（PRODUCT 無入住日概念）。
     */
    private PricingDto.EffectivePriceResponse tryProductAdjustment(CartItemData item, Listing listing) {
        if (listing == null
                || !"PRODUCT".equals(item.getListingType())
                || !featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")) {
            return null;
        }
        try {
            PricingDto.EffectivePriceResponse eff = pricingService.getEffectivePrice(
                    item.getListingId(), BusinessTime.today(), 1);
            // AI-2406c：閘門由 `< 0`（只折扣）放寬為 `!= 0`（含漲價），對齊 ROOM S46
            boolean hasAdjustment = eff.getEffectivePrice() != null
                    && item.getUnitPrice() != null
                    && eff.getEffectivePrice().compareTo(item.getUnitPrice()) != 0;
            return hasAdjustment ? eff : null;
        } catch (RuntimeException e) {
            log.warn("Product dynamic pricing failed for listing={}, fallback to base price: {}",
                    item.getListingId(), e.getMessage());
            return null;
        }
    }

    /** 調整方向（AI-2406c，對齊 S46 BookingService）：正=折扣、負=加價、0/ null=無。 */
    private String adjustmentDirection(BigDecimal originalUnitPrice, BigDecimal adjustedUnitPrice) {
        if (originalUnitPrice == null || adjustedUnitPrice == null) {
            return "NONE";
        }
        int cmp = adjustedUnitPrice.compareTo(originalUnitPrice);
        if (cmp < 0) {
            return "DISCOUNT";
        }
        if (cmp > 0) {
            return "MARKUP";
        }
        return "NONE";
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