package com.nextkey.ecommerce.core.cart;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.CartItemNotFoundException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private com.nextkey.ecommerce.domain.repository.TenantRepository tenantRepository;

    private RedisCartService redisCartService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    // 購物車的擁有範圍（買家的租戶）。Sprint 237 起運費、促銷碼都以「商品所屬的店鋪」為準，兩者刻意是不同的值，
    // 才分得出程式碼用了哪一個（原本測試把兩者設成同一個值，等於用固件繞過了真實情境：消費者的租戶不是店鋪）
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID STORE_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554400a1");
    private static final UUID OTHER_STORE_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554400a2");
    private static final UUID TEST_LISTING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID TEST_SKU_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

    @BeforeEach
    void setUp() {
        redisCartService = new RedisCartService(redisTemplate, listingRepository, productSkuRepository,
                promoService, promoCodeRepository, pricingService, featureToggleService, shippingTemplateService,
                tenantRepository);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(tenantRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.List<com.nextkey.ecommerce.domain.model.tenant.Tenant> stores = new java.util.ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                stores.add(com.nextkey.ecommerce.domain.model.tenant.Tenant.builder()
                        .id((UUID) id).name("店鋪-" + id)
                        .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.ACTIVE).build());
            }
            return stores;
        });
    }

    private Listing buildListing(Listing.ListingType type) {
        return Listing.builder()
                .id(TEST_LISTING_ID)
                .tenantId(STORE_ID)
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
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Listing not found")
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_3000);
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
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start date and end date are required")
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4003);
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
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End date must be after start date")
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4004);
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
            when(shippingTemplateService.calculateFeeForTenant(eq(STORE_ID), any()))
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
            when(shippingTemplateService.calculateFeeForTenant(eq(STORE_ID), any()))
                    .thenReturn(SHIPPING_FEE);
            when(valueOperations.get(anyString())).thenReturn("FREESHIP");
            when(promoService.validatePromoCode(eq("FREESHIP"), eq(STORE_ID)))
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
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(eq("FREESHIP"), eq(STORE_ID)))
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

    // ── Sprint 237：以店鋪為單位（DEF-319 同店結帳）────────────────────

    /**
     * 為什麼要有這一組：購物車的擁有範圍是買家的租戶（沒有店鋪的消費者＝系統租戶佔位值），但運費模板與促銷碼都是店鋪層的設定。
     * 原本全部以買家的租戶解析，消費者永遠拿不到店鋪的運費設定與優惠券。這裡把「買家租戶」與「店鋪」設成不同的值，
     * 並讓購物車同時有兩家店鋪的商品，才分得出程式碼用的是哪一個、以及兩家店鋪的運費與促銷碼會不會互相干擾。
     */
    @Nested
    @DisplayName("Sprint 237：購物車依店鋪分組，運費與促銷碼以店鋪為單位（DEF-319）")
    class StoreScoped {

        private static final UUID LISTING_A = UUID.fromString("550e8400-e29b-41d4-a716-4466554400b1");
        private static final UUID LISTING_B = UUID.fromString("550e8400-e29b-41d4-a716-4466554400b2");
        private static final BigDecimal FEE_A = BigDecimal.valueOf(60);
        private static final BigDecimal FEE_B = BigDecimal.valueOf(100);

        private final Map<String, Object> valueStore = new HashMap<>();

        @BeforeEach
        void stubValueOps() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().doAnswer(inv -> {
                valueStore.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            }).when(valueOperations).set(anyString(), any(), any(java.time.Duration.class));
            lenient().when(valueOperations.get(anyString())).thenAnswer(inv -> valueStore.get((String) inv.getArgument(0)));
        }

        /** 購物車：店鋪 A 的商品 1 件 1000、店鋪 B 的商品 2 件各 500（小計 1000）。 */
        private void givenTwoStoreCart() {
            Map<Object, Object> entries = new HashMap<>();
            entries.put(LISTING_A.toString(), productItem(LISTING_A, 1, 1000));
            entries.put(LISTING_B.toString(), productItem(LISTING_B, 2, 500));
            when(hashOperations.entries(anyString())).thenReturn(entries);
            when(listingRepository.findAllById(anySet())).thenReturn(java.util.List.of(
                    storeListing(LISTING_A, STORE_ID), storeListing(LISTING_B, OTHER_STORE_ID)));
        }

        private void givenSingleStoreCart() {
            Map<Object, Object> entries = new HashMap<>();
            entries.put(LISTING_A.toString(), productItem(LISTING_A, 1, 1000));
            when(hashOperations.entries(anyString())).thenReturn(entries);
            when(listingRepository.findAllById(anySet())).thenReturn(java.util.List.of(storeListing(LISTING_A, STORE_ID)));
        }

        private RedisCartService.CartItemData productItem(UUID listingId, int quantity, int unitPrice) {
            return RedisCartService.CartItemData.builder()
                    .listingId(listingId).quantity(quantity)
                    .unitPrice(BigDecimal.valueOf(unitPrice)).subtotal(BigDecimal.valueOf((long) quantity * unitPrice))
                    .listingType("PRODUCT").build();
        }

        private Listing storeListing(UUID listingId, UUID storeId) {
            return Listing.builder().id(listingId).tenantId(storeId).title("商品 " + listingId)
                    .listingType(Listing.ListingType.PRODUCT).basePrice(BigDecimal.valueOf(1000)).build();
        }

        private void givenValidPromoInStore(UUID storeId, String code) {
            when(promoService.validatePromoCode(eq(code), eq(storeId))).thenReturn(
                    CartDto.PromoValidationResult.valid(code, "FIXED_AMOUNT", BigDecimal.valueOf(100), null));
            com.nextkey.ecommerce.domain.model.promo.PromoCode promo =
                    com.nextkey.ecommerce.domain.model.promo.PromoCode.builder()
                            .id(UUID.randomUUID()).code(code)
                            .discountType(com.nextkey.ecommerce.domain.model.promo.PromoCode.DiscountType.FIXED_AMOUNT)
                            .discountValue(BigDecimal.valueOf(100)).build();
            lenient().when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(eq(code), eq(storeId)))
                    .thenReturn(Optional.of(promo));
            lenient().when(promoService.computeDiscount(eq(promo), any(), any())).thenReturn(BigDecimal.valueOf(100));
        }

        @Test
        @DisplayName("getCart：每個項目帶出所屬店鋪的 id 與名稱")
        void getCart_itemsCarryTheirStore() {
            givenSingleStoreCart();

            CartDto.CartResponse response = redisCartService.getCart(TEST_USER_ID, TEST_TENANT_ID);

            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItems().get(0).getStoreId()).isEqualTo(STORE_ID);
            assertThat(response.getItems().get(0).getStoreName()).isEqualTo("店鋪-" + STORE_ID);
        }

        @Test
        @DisplayName("Sprint 239：購物車帶出每家店鋪是否營業中——營業中 true、非 ACTIVE（例如停權）false，摘要與項目一致")
        void storeActive_isReportedPerStore() {
            givenTwoStoreCart();
            // 店鋪 A 營業中、店鋪 B 停權（setUp 預設的 findAllById 不帶狀態，這裡覆寫）
            // 用 doReturn 覆寫：when(...).thenReturn(...) 會先以 null 引數呼叫 setUp 裡既有的 thenAnswer，而它會對 null 拋 NPE
            doReturn(java.util.List.of(
                    com.nextkey.ecommerce.domain.model.tenant.Tenant.builder().id(STORE_ID).name("A")
                            .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.ACTIVE).build(),
                    com.nextkey.ecommerce.domain.model.tenant.Tenant.builder().id(OTHER_STORE_ID).name("B")
                            .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.SUSPENDED).build()))
                    .when(tenantRepository).findAllById(any());
            when(shippingTemplateService.calculateFeeForTenant(any(), any())).thenReturn(BigDecimal.ZERO);

            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);

            assertThat(response.getStores()).filteredOn(s -> s.getStoreId().equals(STORE_ID))
                    .singleElement().satisfies(s -> assertThat(s.getStoreActive()).isTrue());
            assertThat(response.getStores()).filteredOn(s -> s.getStoreId().equals(OTHER_STORE_ID))
                    .singleElement().satisfies(s -> assertThat(s.getStoreActive()).isFalse());
            assertThat(response.getItems()).filteredOn(i -> OTHER_STORE_ID.equals(i.getStoreId()))
                    .singleElement().satisfies(i -> assertThat(i.getStoreActive()).isFalse());
        }

        @Test
        @DisplayName("兩家店鋪的商品：各自用自己的運費模板；絕不用買家的租戶查運費模板")
        void twoStores_eachStoreUsesItsOwnShippingTemplate() {
            givenTwoStoreCart();
            when(shippingTemplateService.calculateFeeForTenant(eq(STORE_ID), any())).thenReturn(FEE_A);
            when(shippingTemplateService.calculateFeeForTenant(eq(OTHER_STORE_ID), any())).thenReturn(FEE_B);

            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);

            assertThat(response.getStores()).hasSize(2);
            CartDto.StoreCartSummary a = response.getStores().stream()
                    .filter(s -> STORE_ID.equals(s.getStoreId())).findFirst().orElseThrow();
            CartDto.StoreCartSummary b = response.getStores().stream()
                    .filter(s -> OTHER_STORE_ID.equals(s.getStoreId())).findFirst().orElseThrow();
            assertThat(a.getShippingFee()).isEqualByComparingTo(FEE_A);
            assertThat(a.getTotalAmount()).isEqualByComparingTo("1000");
            assertThat(a.getItemCount()).isEqualTo(1);
            assertThat(b.getShippingFee()).isEqualByComparingTo(FEE_B);
            assertThat(b.getTotalAmount()).isEqualByComparingTo("1000");
            assertThat(b.getItemCount()).isEqualTo(2);
            // 頂層是各店鋪合計
            assertThat(response.getShippingFee()).isEqualByComparingTo("160");
            assertThat(response.getFinalAmount()).isEqualByComparingTo("2160");
            verify(shippingTemplateService, never()).calculateFeeForTenant(eq(TEST_TENANT_ID), any());
        }

        @Test
        @DisplayName("套用促銷碼：在「項目所屬的店鋪」驗證與查券，不是買家的租戶；並以（使用者, 店鋪）為鍵記住")
        void applyPromo_validatesInTheStoreNotTheBuyersTenant() {
            givenSingleStoreCart();
            when(shippingTemplateService.calculateFeeForTenant(eq(STORE_ID), any())).thenReturn(BigDecimal.ZERO);
            givenValidPromoInStore(STORE_ID, "SAVE100");

            CartDto.ApplyPromoResponse response = redisCartService.applyPromoCode(
                    TEST_USER_ID, TEST_TENANT_ID, null, "SAVE100");

            assertThat(response.getStoreId()).isEqualTo(STORE_ID);
            assertThat(response.getAppliedPromoCode()).isEqualTo("SAVE100");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("100");
            verify(promoService, never()).validatePromoCode(any(), eq(TEST_TENANT_ID));
            assertThat(valueStore.keySet()).as("促銷碼的鍵含店鋪").anyMatch(k -> k.contains(STORE_ID.toString()));
            assertThat(redisCartService.getAppliedPromoCode(TEST_USER_ID, TEST_TENANT_ID, STORE_ID)).isEqualTo("SAVE100");
        }

        @Test
        @DisplayName("多家店鋪、沒指定店鋪 → E-5020，不套用也不寫入任何促銷碼")
        void applyPromo_multipleStoresWithoutStore_isRejected() {
            givenTwoStoreCart();

            assertThatThrownBy(() -> redisCartService.applyPromoCode(TEST_USER_ID, TEST_TENANT_ID, null, "SAVE100"))
                    .isInstanceOfSatisfying(com.nextkey.ecommerce.shared.exception.BusinessException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_5020));
            assertThat(valueStore).isEmpty();
            verify(promoService, never()).validatePromoCode(any(), any());
        }

        @Test
        @DisplayName("多家店鋪：促銷碼只套用在指定的店鋪，另一家店鋪的摘要不受影響")
        void applyPromo_toOneStore_doesNotAffectTheOtherStore() {
            givenTwoStoreCart();
            when(shippingTemplateService.calculateFeeForTenant(any(), any())).thenReturn(BigDecimal.ZERO);
            givenValidPromoInStore(OTHER_STORE_ID, "SAVE100");

            redisCartService.applyPromoCode(TEST_USER_ID, TEST_TENANT_ID, OTHER_STORE_ID, "SAVE100");

            assertThat(redisCartService.getAppliedPromoCode(TEST_USER_ID, TEST_TENANT_ID, OTHER_STORE_ID)).isEqualTo("SAVE100");
            assertThat(redisCartService.getAppliedPromoCode(TEST_USER_ID, TEST_TENANT_ID, STORE_ID))
                    .as("另一家店鋪沒有套用促銷碼").isNull();
            CartDto.CartResponse response = redisCartService.getCartWithPromo(TEST_USER_ID, TEST_TENANT_ID);
            CartDto.StoreCartSummary a = response.getStores().stream()
                    .filter(s -> STORE_ID.equals(s.getStoreId())).findFirst().orElseThrow();
            CartDto.StoreCartSummary b = response.getStores().stream()
                    .filter(s -> OTHER_STORE_ID.equals(s.getStoreId())).findFirst().orElseThrow();
            assertThat(a.getAppliedPromoCode()).isNull();
            assertThat(a.getDiscountAmount()).isEqualByComparingTo("0");
            assertThat(b.getAppliedPromoCode()).isEqualTo("SAVE100");
            assertThat(b.getDiscountAmount()).isEqualByComparingTo("100");
            assertThat(response.getAppliedPromoCode()).as("恰好一家店鋪套了券時，頂層回報該券碼").isEqualTo("SAVE100");
        }

        @Test
        @DisplayName("指定的店鋪在購物車裡沒有項目 → E-5004，不驗證促銷碼")
        void applyPromo_storeWithoutItems_isRejected() {
            givenSingleStoreCart();

            assertThatThrownBy(() -> redisCartService.applyPromoCode(
                    TEST_USER_ID, TEST_TENANT_ID, OTHER_STORE_ID, "SAVE100"))
                    .isInstanceOfSatisfying(com.nextkey.ecommerce.shared.exception.BusinessException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_5004));
            verify(promoService, never()).validatePromoCode(any(), any());
        }

        @Test
        @DisplayName("驗證促銷碼（resolveStoreIdForValidation）：明確指定店鋪就用指定的，不要求購物車有該店鋪的項目")
        void resolveStoreForValidation_explicitStore_isUsedAsIs() {
            assertThat(redisCartService.resolveStoreIdForValidation(TEST_USER_ID, TEST_TENANT_ID, OTHER_STORE_ID))
                    .isEqualTo(OTHER_STORE_ID);
            verify(hashOperations, never()).entries(anyString());
        }

        @Test
        @DisplayName("驗證促銷碼：沒指定時用購物車唯一的店鋪；多家店鋪才要求指定（E-5020）")
        void resolveStoreForValidation_singleStore_isThatStore_multipleStores_isRejected() {
            givenSingleStoreCart();
            assertThat(redisCartService.resolveStoreIdForValidation(TEST_USER_ID, TEST_TENANT_ID, null)).isEqualTo(STORE_ID);

            givenTwoStoreCart();
            assertThatThrownBy(() -> redisCartService.resolveStoreIdForValidation(TEST_USER_ID, TEST_TENANT_ID, null))
                    .isInstanceOfSatisfying(com.nextkey.ecommerce.shared.exception.BusinessException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_5020));
        }

        @Test
        @DisplayName("驗證促銷碼：購物車是空的時維持原本的行為——以買家租戶驗證（端點永遠回 200，不能因為沒有店鋪就變成錯誤）")
        void resolveStoreForValidation_emptyCart_fallsBackToTheBuyersTenant() {
            when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

            assertThat(redisCartService.resolveStoreIdForValidation(TEST_USER_ID, TEST_TENANT_ID, null))
                    .isEqualTo(TEST_TENANT_ID);
        }

        @Test
        @DisplayName("移除促銷碼只移除該店鋪的那一個鍵")
        void removePromo_removesOnlyThatStoresKey() {
            redisCartService.removePromoCode(TEST_USER_ID, TEST_TENANT_ID, STORE_ID);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).delete(key.capture());
            assertThat(key.getValue()).contains(STORE_ID.toString()).doesNotContain(OTHER_STORE_ID.toString());
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