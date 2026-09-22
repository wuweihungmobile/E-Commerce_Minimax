package com.nextkey.ecommerce.core.cart;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.shared.exception.CartItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisCartService 單元測試
 *
 * 測試範圍：
 * - addItem()
 * - updateItem()
 * - removeItem()
 * - clearCart()
 * - getCart()
 * - getCartItemCount()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisCartService: 購物車管理")
class RedisCartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    @Mock
    private PromoService promoService;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private com.nextkey.ecommerce.core.pricing.PricingService pricingService;

    @Mock
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    @Mock
    private com.nextkey.ecommerce.core.logistics.ShippingTemplateService shippingTemplateService;

    private RedisCartService redisCartService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_LISTING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID TEST_SKU_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

    @BeforeEach
    void setUp() {
        redisCartService = new RedisCartService(redisTemplate, listingRepository, productSkuRepository,
                promoService, promoCodeRepository, pricingService, featureToggleService, shippingTemplateService);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    private Listing buildListing(Listing.ListingType type) {
        return Listing.builder()
                .id(TEST_LISTING_ID)
                .title("Test Listing")
                .coverImageUrl("https://example.com/image.jpg")
                .listingType(type)
                .basePrice(BigDecimal.valueOf(1000))
                .build();
    }

    private ProductSku buildSku() {
        return ProductSku.builder()
                .id(TEST_SKU_ID)
                .productListingId(TEST_LISTING_ID)
                .skuCode("SKU-001")
                .specName("Spec A")
                .priceOverride(BigDecimal.valueOf(1200))
                .build();
    }

    private CartDto.AddItemRequest buildAddItemRequest(UUID listingId, UUID skuId,
                                                        LocalDate startDate, LocalDate endDate, int quantity) {
        return CartDto.AddItemRequest.builder()
                .listingId(listingId)
                .skuId(skuId)
                .startDate(startDate)
                .endDate(endDate)
                .quantity(quantity)
                .build();
    }

    // ── addItem Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("addItem_productListing_success")
        void addItem_productListing_success() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.PRODUCT);
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, null, null, 2);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(hashOperations.get(anyString(), anyString())).thenReturn(null);
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            // Act
            CartDto.AddItemResponse response = redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getItem()).isNotNull();
            assertThat(response.getItem().getQuantity()).isEqualTo(2);
            verify(hashOperations).put(anyString(), anyString(), any(RedisCartService.CartItemData.class));
            verify(redisTemplate).expire(anyString(), any());
        }

        @Test
        @DisplayName("addItem_roomListing_withDates_success")
        void addItem_roomListing_withDates_success() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.ROOM);
            LocalDate startDate = LocalDate.now().plusDays(1);
            LocalDate endDate = LocalDate.now().plusDays(3);
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, startDate, endDate, 1);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(hashOperations.get(anyString(), anyString())).thenReturn(null);
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            // Act
            CartDto.AddItemResponse response = redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getItem().getStartDate()).isEqualTo(startDate);
            assertThat(response.getItem().getEndDate()).isEqualTo(endDate);
        }

        @Test
        @DisplayName("addItem_withSku_useSkuPrice")
        void addItem_withSku_useSkuPrice() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.PRODUCT);
            ProductSku sku = buildSku();
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, TEST_SKU_ID, null, null, 1);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(productSkuRepository.findById(TEST_SKU_ID)).thenReturn(Optional.of(sku));
            when(hashOperations.get(anyString(), anyString())).thenReturn(null);
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            // Act
            CartDto.AddItemResponse response = redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response.getItem().getUnitPrice()).isEqualTo(BigDecimal.valueOf(1200)); // SKU price override
            assertThat(response.getItem().getSkuCode()).isEqualTo("SKU-001");
        }

        @Test
        @DisplayName("DEF-237：skuId 真實存在但不屬於這個 listingId 時，視同未選規格（不得沿用他人 SKU 的價格/代碼，也不得把該 skuId 存入購物車）")
        void addItem_skuBelongsToDifferentListing_treatedAsNoSku() {
            // 情境還原：攻擊者用自己的 listing（TEST_LISTING_ID），搭配從公開的
            // GET /v2/products/{listingId}/skus 查到的「他租戶」真實 SKU UUID（otherListingSku
            // 實際屬於 otherListingId，非 TEST_LISTING_ID）。
            UUID otherListingId = UUID.randomUUID();
            Listing listing = buildListing(Listing.ListingType.PRODUCT);
            ProductSku otherListingSku = ProductSku.builder()
                    .id(TEST_SKU_ID)
                    .productListingId(otherListingId)
                    .skuCode("VICTIM-SKU")
                    .specName("他租戶規格")
                    .priceOverride(BigDecimal.valueOf(1))
                    .build();
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, TEST_SKU_ID, null, null, 1);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(productSkuRepository.findById(TEST_SKU_ID)).thenReturn(Optional.of(otherListingSku));
            when(hashOperations.get(anyString(), anyString())).thenReturn(null);
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            CartDto.AddItemResponse response = redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request);

            // 修復前：會直接採用他租戶 SKU 的價格/代碼，且把他租戶的 skuId 存入購物車項目，
            // 這三個斷言都會失敗。
            assertThat(response.getItem().getSkuId()).isNull();
            assertThat(response.getItem().getSkuCode()).isNull();
            assertThat(response.getItem().getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000)); // 退回 listing 原價
        }

        @Test
        @DisplayName("addItem_existingItem_increasesQuantity")
        void addItem_existingItem_increasesQuantity() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.PRODUCT);
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, null, null, 2);

            RedisCartService.CartItemData existingItem = RedisCartService.CartItemData.builder()
                    .listingId(TEST_LISTING_ID)
                    .quantity(3)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .subtotal(BigDecimal.valueOf(3000))
                    .build();

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(hashOperations.get(anyString(), anyString())).thenReturn(existingItem);
            when(hashOperations.entries(anyString())).thenReturn(Map.of("key", existingItem));

            // Act
            CartDto.AddItemResponse response = redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response.getItem().getQuantity()).isEqualTo(5); // 3 + 2
        }

        @Test
        @DisplayName("addItem_listingNotFound_throwsException")
        void addItem_listingNotFound_throwsException() {
            // Arrange
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, null, null, 1);
            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Listing not found");
        }

        @Test
        @DisplayName("addItem_roomListing_missingDates_throwsException")
        void addItem_roomListing_missingDates_throwsException() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.ROOM);
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, null, null, 1);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));

            // Act & Assert
            assertThatThrownBy(() -> redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Start date and end date are required");
        }

        @Test
        @DisplayName("addItem_roomListing_endDateBeforeStartDate_throwsException")
        void addItem_roomListing_endDateBeforeStartDate_throwsException() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.ROOM);
            LocalDate startDate = LocalDate.now().plusDays(3);
            LocalDate endDate = LocalDate.now().plusDays(1);
            CartDto.AddItemRequest request = buildAddItemRequest(TEST_LISTING_ID, null, startDate, endDate, 1);

            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));

            // Act & Assert
            assertThatThrownBy(() -> redisCartService.addItem(TEST_USER_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }
    }

    // ── updateItem Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("updateItem_success")
        void updateItem_success() {
            // Arrange
            String cartItemKey = TEST_LISTING_ID.toString();
            RedisCartService.CartItemData existingItem = RedisCartService.CartItemData.builder()
                    .listingId(TEST_LISTING_ID)
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .subtotal(BigDecimal.valueOf(2000))
                    .build();

            when(hashOperations.get(anyString(), eq(cartItemKey))).thenReturn(existingItem);
            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(buildListing(Listing.ListingType.PRODUCT)));

            // Act
            CartDto.CartItemResponse response = redisCartService.updateItem(TEST_USER_ID, TEST_TENANT_ID, cartItemKey, 5);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getQuantity()).isEqualTo(5);
            verify(hashOperations).put(anyString(), eq(cartItemKey), any(RedisCartService.CartItemData.class));
        }

        @Test
        @DisplayName("updateItem_itemNotFound_throwsException")
        void updateItem_itemNotFound_throwsException() {
            // Arrange - 使用無效的 cartItemKey (不是 UUID 格式)
            String cartItemKey = "nonexistent-key";
            // 此 stubbing 不會被使用，因為 UUID parsing 就會失敗

            // Act & Assert
            assertThatThrownBy(() -> redisCartService.updateItem(TEST_USER_ID, TEST_TENANT_ID, cartItemKey, 5))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining("Cart item not found");
        }
    }

    // ── removeItem Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("removeItem()")
    class RemoveItem {

        @Test
        @DisplayName("removeItem_success")
        void removeItem_success() {
            // Arrange
            String cartItemKey = TEST_LISTING_ID.toString();
            RedisCartService.CartItemData existingItem = RedisCartService.CartItemData.builder()
                    .listingId(TEST_LISTING_ID)
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .build();

            when(hashOperations.get(anyString(), eq(cartItemKey))).thenReturn(existingItem);

            // Act
            redisCartService.removeItem(TEST_USER_ID, TEST_TENANT_ID, cartItemKey);

            // Assert
            verify(hashOperations).delete(anyString(), eq(cartItemKey));
        }

        @Test
        @DisplayName("removeItem_itemNotFound_throwsException")
        void removeItem_itemNotFound_throwsException() {
            // Arrange - 使用無效的 cartItemKey (不是 UUID 格式)
            String cartItemKey = "nonexistent-key";
            // 此 stubbing 不會被使用，因為 UUID parsing 就會失敗

            // Act & Assert
            assertThatThrownBy(() -> redisCartService.removeItem(TEST_USER_ID, TEST_TENANT_ID, cartItemKey))
                    .isInstanceOf(CartItemNotFoundException.class);
        }
    }

    // ── clearCart Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("clearCart()")
    class ClearCart {

        @Test
        @DisplayName("clearCart_success")
        void clearCart_success() {
            // Act
            redisCartService.clearCart(TEST_USER_ID, TEST_TENANT_ID);

            // Assert
            verify(redisTemplate).delete(anyString());
        }
    }

    // ── getCart Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getCart()")
    class GetCart {

        @Test
        @DisplayName("getCart_emptyCart_returnsEmptyResponse")
        void getCart_emptyCart_returnsEmptyResponse() {
            // Arrange
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            // Act
            CartDto.CartResponse response = redisCartService.getCart(TEST_USER_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).isEmpty();
            assertThat(response.getItemCount()).isEqualTo(0);
            assertThat(response.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("getCart_withItems_returnsCartWithItems")
        void getCart_withItems_returnsCartWithItems() {
            // Arrange
            Listing listing = buildListing(Listing.ListingType.PRODUCT);
            RedisCartService.CartItemData itemData = RedisCartService.CartItemData.builder()
                    .listingId(TEST_LISTING_ID)
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .subtotal(BigDecimal.valueOf(2000))
                    .listingType("PRODUCT")
                    .build();

            Map<Object, Object> entries = new HashMap<>();
            entries.put(TEST_LISTING_ID.toString(), itemData);

            when(hashOperations.entries(anyString())).thenReturn(entries);
            when(listingRepository.findAllById(anySet())).thenReturn(java.util.List.of(listing));

            // Act
            CartDto.CartResponse response = redisCartService.getCart(TEST_USER_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItemCount()).isEqualTo(2);
            assertThat(response.getTotalAmount()).isEqualTo(BigDecimal.valueOf(2000));
        }
    }

    // ── getCartWithPromo 運費預覽 Tests (Sprint 101) ────────────────────

    @Nested
    @DisplayName("getCartWithPromo：運費預覽（Sprint 101 / DEF-045）")
    class GetCartWithPromoShipping {

        private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(60);

        @BeforeEach
        void stubValueOps() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("PRODUCT 購物車 → 回填預估運費，應付金額 = 小計 + 運費")
        void productCart_previewsShippingFee() {
            givenCartWith("PRODUCT", Listing.ListingType.PRODUCT);
            when(shippingTemplateService.calculateFeeForTenant(eq(TEST_TENANT_ID), any()))
                    .thenReturn(SHIPPING_FEE);

            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);

            // 修復前購物車完全不回傳運費，買家在購物車看不到、也對不上結帳實收
            assertThat(response.getShippingFee()).isEqualByComparingTo(SHIPPING_FEE);
            assertThat(response.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2060));
        }

        @Test
        @DisplayName("純 ROOM 購物車 → 運費 0，不得對訂房顯示實體出貨運費")
        void roomOnlyCart_noShippingFee() {
            givenCartWith("ROOM", Listing.ListingType.ROOM);

            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);

            assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(shippingTemplateService, never()).calculateFeeForTenant(any(), any());
        }

        @Test
        @DisplayName("套用免運券 → 折抵金額為運費，應付金額回到商品小計")
        void freeShippingPromo_waivesShippingFee() {
            givenCartWith("PRODUCT", Listing.ListingType.PRODUCT);
            when(shippingTemplateService.calculateFeeForTenant(eq(TEST_TENANT_ID), any()))
                    .thenReturn(SHIPPING_FEE);
            when(valueOperations.get(anyString())).thenReturn("FREESHIP");
            when(promoService.validatePromoCode(eq("FREESHIP"), eq(TEST_TENANT_ID)))
                    .thenReturn(CartDto.PromoValidationResult.valid(
                            "FREESHIP", "FREE_SHIPPING", BigDecimal.ZERO, null));
            com.nextkey.ecommerce.domain.model.promo.PromoCode promo =
                    com.nextkey.ecommerce.domain.model.promo.PromoCode.builder()
                            .id(UUID.randomUUID())
                            .code("FREESHIP")
                            .discountType(
                                    com.nextkey.ecommerce.domain.model.promo.PromoCode.DiscountType.FREE_SHIPPING)
                            .discountValue(BigDecimal.ZERO)
                            .build();
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(eq("FREESHIP"), eq(TEST_TENANT_ID)))
                    .thenReturn(Optional.of(promo));
            // 運費必須傳進折扣計算，否則免運券永遠算不出金額（DEF-045）
            when(promoService.computeDiscount(eq(promo), any(), eq(SHIPPING_FEE))).thenReturn(SHIPPING_FEE);

            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);

            assertThat(response.getAppliedPromoCode()).isEqualTo("FREESHIP");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(SHIPPING_FEE);
            assertThat(response.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }

        private void givenCartWith(String listingType, Listing.ListingType type) {
            Listing listing = buildListing(type);
            RedisCartService.CartItemData itemData = RedisCartService.CartItemData.builder()
                    .listingId(TEST_LISTING_ID)
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .subtotal(BigDecimal.valueOf(2000))
                    .listingType(listingType)
                    .build();
            Map<Object, Object> entries = new HashMap<>();
            entries.put(TEST_LISTING_ID.toString(), itemData);
            when(hashOperations.entries(anyString())).thenReturn(entries);
            when(listingRepository.findAllById(anySet())).thenReturn(java.util.List.of(listing));
        }
    }

    // ── getCartItemCount Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("getCartItemCount()")
    class GetCartItemCount {

        @Test
        @DisplayName("getCartItemCount_emptyCart_returnsZero")
        void getCartItemCount_emptyCart_returnsZero() {
            // Arrange
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            // Act
            int count = redisCartService.getCartItemCount(TEST_USER_ID, TEST_TENANT_ID);

            // Assert
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("getCartItemCount_withItems_returnsTotalQuantity")
        void getCartItemCount_withItems_returnsTotalQuantity() {
            // Arrange
            RedisCartService.CartItemData item1 = RedisCartService.CartItemData.builder()
                    .listingId(UUID.randomUUID())
                    .quantity(3)
                    .unitPrice(BigDecimal.valueOf(100))
                    .build();
            RedisCartService.CartItemData item2 = RedisCartService.CartItemData.builder()
                    .listingId(UUID.randomUUID())
                    .quantity(5)
                    .unitPrice(BigDecimal.valueOf(200))
                    .build();

            Map<Object, Object> entries = new HashMap<>();
            entries.put("key1", item1);
            entries.put("key2", item2);

            when(hashOperations.entries(anyString())).thenReturn(entries);

            // Act
            int count = redisCartService.getCartItemCount(TEST_USER_ID, TEST_TENANT_ID);

            // Assert
            assertThat(count).isEqualTo(8); // 3 + 5
        }
    }

    // ── CartItemData.computeSubtotal Tests ───────────────────────────

    @Nested
    @DisplayName("CartItemData.computeSubtotal()")
    class ComputeSubtotal {

        @Test
        @DisplayName("computeSubtotal_validInputs_returnsCorrectSubtotal")
        void computeSubtotal_validInputs_returnsCorrectSubtotal() {
            BigDecimal unitPrice = BigDecimal.valueOf(100);
            int quantity = 3;
            BigDecimal result = RedisCartService.CartItemData.computeSubtotal(unitPrice, quantity);
            assertThat(result).isEqualTo(BigDecimal.valueOf(300));
        }

        @Test
        @DisplayName("computeSubtotal_nullUnitPrice_returnsZero")
        void computeSubtotal_nullUnitPrice_returnsZero() {
            BigDecimal result = RedisCartService.CartItemData.computeSubtotal(null, 3);
            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("computeSubtotal_nullQuantity_returnsZero")
        void computeSubtotal_nullQuantity_returnsZero() {
            BigDecimal result = RedisCartService.CartItemData.computeSubtotal(BigDecimal.valueOf(100), null);
            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("computeSubtotal_bothNull_returnsZero")
        void computeSubtotal_bothNull_returnsZero() {
            BigDecimal result = RedisCartService.CartItemData.computeSubtotal(null, null);
            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }
    }
}