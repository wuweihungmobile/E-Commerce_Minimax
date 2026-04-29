package com.nextkey.ecommerce.core.cart;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.shared.exception.CartItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
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
    private ListingRepository listingRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    private RedisCartService redisCartService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_LISTING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID TEST_SKU_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

    @BeforeEach
    void setUp() {
        redisCartService = new RedisCartService(redisTemplate, listingRepository, productSkuRepository);
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