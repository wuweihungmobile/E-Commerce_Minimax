package com.nextkey.ecommerce.core.cart;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisCartService 動態定價（PRODUCT）單元測試（Sprint 44 US-001 / AI-2403）
 *
 * 驗證 getCart 折扣三路徑（AC-001-5）：
 * - toggle 開啟且有折扣 → 項目 unitPrice/subtotal 為折扣後 + 回原價/折扣額/規則名，總額折扣後
 * - toggle 關閉 → 維持既有 basePrice、不呼叫 PricingService（不退步）
 * - toggle 開啟但無折扣 → 不套用
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisCartService: PRODUCT 動態定價（AI-2403）")
class RedisCartServiceDynamicPricingTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ListingRepository listingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.ProductSkuRepository productSkuRepository;
    @Mock private com.nextkey.ecommerce.core.promo.PromoService promoService;
    @Mock private com.nextkey.ecommerce.domain.repository.PromoCodeRepository promoCodeRepository;
    @Mock private PricingService pricingService;
    @Mock private FeatureToggleService featureToggleService;

    @InjectMocks private RedisCartService cartService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LISTING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private RedisCartService.CartItemData productItem() {
        return RedisCartService.CartItemData.builder()
                .listingId(LISTING_ID)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(500))
                .subtotal(BigDecimal.valueOf(1000))
                .listingType("PRODUCT")
                .addedAt(Instant.EPOCH)
                .build();
    }

    private Listing productListing() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.PRODUCT)
                .title("測試商品")
                .basePrice(BigDecimal.valueOf(500))
                .currency("TWD")
                .build();
        listing.setId(LISTING_ID);
        return listing;
    }

    private void stubCartWithProduct() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Map.of("k1", productItem()));
        when(listingRepository.findAllById(any())).thenReturn(List.of(productListing()));
    }

    private PricingDto.EffectivePriceResponse effective(BigDecimal effectivePrice, String ruleName) {
        return PricingDto.EffectivePriceResponse.builder()
                .listingId(LISTING_ID)
                .basePrice(BigDecimal.valueOf(500))
                .effectivePrice(effectivePrice)
                .appliedRuleType("EARLY_BIRD")
                .appliedRuleName(ruleName)
                .build();
    }

    @Test
    @DisplayName("DEF-269: 商品促銷價以營運時區（UTC+8）的今天查詢有效規則（台灣 2/1 00:30 → 查 2/1，非 UTC 的 1/31）")
    void getCart_productRuleLookup_usesBusinessDate() {
        com.nextkey.ecommerce.shared.time.BusinessTime.useClockForTesting(java.time.Clock.fixed(
                Instant.parse("2027-01-31T16:30:00Z"), java.time.ZoneOffset.UTC));
        stubCartWithProduct();
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(true);
        // 只有以「台灣的今天 2027-02-01」查詢才回傳促銷價；其他日期一律無規則（mock 預設回 null）
        when(pricingService.getEffectivePrice(LISTING_ID, java.time.LocalDate.of(2027, 2, 1), 1))
                .thenReturn(effective(BigDecimal.valueOf(425), "商品促銷 2/1 起"));

        CartDto.CartResponse cart = cartService.getCart(USER_ID, TENANT_ID);

        CartDto.CartItemResponse it = cart.getItems().get(0);
        assertThat(it.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(425));
        assertThat(it.getAppliedRuleName()).isEqualTo("商品促銷 2/1 起");
    }

    @Test
    @DisplayName("UT-CART-DP-001: toggle 開啟且有折扣 → unitPrice/subtotal 折扣後 + 原價/折扣額/規則名")
    void getCart_withProductDiscount_returnsDiscounted() {
        stubCartWithProduct();
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(true);
        when(pricingService.getEffectivePrice(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(effective(BigDecimal.valueOf(425), "商品早鳥 15% off")); // 500 → 425

        CartDto.CartResponse cart = cartService.getCart(USER_ID, TENANT_ID);

        assertThat(cart.getItems()).hasSize(1);
        CartDto.CartItemResponse it = cart.getItems().get(0);
        assertThat(it.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(425));
        assertThat(it.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(850)); // 425 × 2
        assertThat(it.getOriginalUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(it.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(150)); // (500-425)×2
        assertThat(it.getAppliedRuleName()).isEqualTo("商品早鳥 15% off");
        assertThat(it.getPriceAdjustmentType()).isEqualTo("DISCOUNT");
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(850));
    }

    @Test
    @DisplayName("UT-CART-DP-004: toggle 開啟且漲價（AI-2406c）→ unitPrice/subtotal 漲價後 + 有號差額(負)/MARKUP")
    void getCart_withProductMarkup_returnsMarkedUp() {
        stubCartWithProduct();
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(true);
        // 旺季加成：500 → 650（漲價；閘門放寬後計入）
        when(pricingService.getEffectivePrice(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(effective(BigDecimal.valueOf(650), "商品旺季加成 30%"));

        CartDto.CartResponse cart = cartService.getCart(USER_ID, TENANT_ID);

        CartDto.CartItemResponse it = cart.getItems().get(0);
        assertThat(it.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(650));
        assertThat(it.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(1300)); // 650 × 2
        assertThat(it.getOriginalUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        // 有號差額：(500 − 650) × 2 = −300（負=加價）
        assertThat(it.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(-300));
        assertThat(it.getAppliedRuleName()).isEqualTo("商品旺季加成 30%");
        assertThat(it.getPriceAdjustmentType()).isEqualTo("MARKUP");
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1300));
    }

    @Test
    @DisplayName("UT-CART-DP-002: toggle 關閉 → 維持 basePrice、不呼叫 PricingService（不退步）")
    void getCart_toggleDisabled_usesBasePrice() {
        stubCartWithProduct();
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);

        CartDto.CartResponse cart = cartService.getCart(USER_ID, TENANT_ID);

        CartDto.CartItemResponse it = cart.getItems().get(0);
        assertThat(it.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(it.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(it.getOriginalUnitPrice()).isNull();
        assertThat(it.getDiscountAmount()).isNull();
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        verify(pricingService, never()).getEffectivePrice(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("UT-CART-DP-003: toggle 開啟但無折扣（effectivePrice == 現價）→ 不套用")
    void getCart_noDiscount_usesBasePrice() {
        stubCartWithProduct();
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(true);
        when(pricingService.getEffectivePrice(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(effective(BigDecimal.valueOf(500), null)); // 無折扣

        CartDto.CartResponse cart = cartService.getCart(USER_ID, TENANT_ID);

        CartDto.CartItemResponse it = cart.getItems().get(0);
        assertThat(it.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(it.getOriginalUnitPrice()).isNull();
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }
}
