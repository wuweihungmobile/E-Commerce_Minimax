package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M12 動態定價 — effective-price 端點整合測試 (Sprint 22 US-002)
 *
 * 測試範圍：
 * - IT-EP-001: 有定價規則時，effectivePrice 反映折扣（< basePrice）
 * - IT-EP-002: 無定價規則時，effectivePrice = basePrice，appliedRuleType = null
 * - IT-EP-003: 不存在的 listingId → 404
 * - IT-EP-004: MANUAL_OVERRIDE 漲價（AI-2406c）
 * - IT-EP-005: 商品所屬店鋪的動態定價開關關閉 → 規則不套用（DEF-424，Sprint 309）
 *
 * <p>DEF-424（Sprint 309，使用者裁決「關閉後立即失效」）：規則只在<b>商品所屬店鋪</b>的動態定價開關開啟時套用。這個類別先前沒有
 * 替商品所屬的店鋪開啟開關（{@code FeatureToggleService} 是 {@code @MockBean}，預設回 {@code false}），因為舊的
 * {@code effective-price} 端點根本不查開關——開關狀態一直無關緊要，測試因此一直是綠的。現在 {@link #storeHasDynamicPricingEnabled}
 * 明說前提：這家店鋪的動態定價是開啟的；關閉的情形由 IT-EP-005 與 {@code StoreToggleGatesApplicationE2ETest}（真實 PostgreSQL）覆蓋。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-EP: M12 effective-price 端點整合測試")
@WithMockUser(username = "seller", authorities = {"product:read"})
class M12EffectivePriceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private PricingRuleRepository pricingRuleRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final UUID LISTING_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440004");
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String BASE_URL = "/v2/listings/" + LISTING_ID + "/effective-price";
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(500.00);

    /** 前提：商品所屬店鋪的動態定價開關是開啟的（DEF-424）。IT-EP-005 會在案例內覆蓋成關閉。 */
    @BeforeEach
    void storeHasDynamicPricingEnabled() {
        when(featureToggleService.isFeatureEnabledForTenant(UUID.fromString(TEST_TENANT_ID), "DYNAMIC_PRICING_ENABLED"))
                .thenReturn(true);
    }

    private Listing buildMockListing() {
        Listing listing = Listing.builder()
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingType(Listing.ListingType.PRODUCT)
                .title("測試商品")
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BASE_PRICE)
                .currency("TWD")
                .build();
        listing.setId(LISTING_ID);
        return listing;
    }

    private PricingRule buildActiveRule(UUID ruleId, int discountPercent) {
        return PricingRule.builder()
                .id(ruleId)
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingId(LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.SEASONAL)
                .ruleName("節慶折扣")
                .priority(5)
                .config(Map.of("discountPercent", discountPercent))
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .isActive(true)
                .build();
    }

    // ── IT-EP-001: 有規則 → effectivePrice 反映折扣 ──────────────

    @Test
    @DisplayName("IT-EP-001: 有定價規則時，effectivePrice 反映 10% 折扣")
    void getEffectivePrice_withActiveRule_returnsDiscountedPrice() throws Exception {
        UUID ruleId = UUID.randomUUID();
        Listing listing = buildMockListing();
        PricingRule rule = buildActiveRule(ruleId, 10);

        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(pricingRuleRepository.findByListingIdAndIsActiveTrue(LISTING_ID)).thenReturn(List.of(rule));

        mockMvc.perform(get(BASE_URL)
                        .param("checkDate", LocalDate.now().toString())
                        .param("stayDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basePrice").value(500.00))
                .andExpect(jsonPath("$.data.effectivePrice").value(450.00))
                .andExpect(jsonPath("$.data.appliedRuleType").value("SEASONAL"))
                .andExpect(jsonPath("$.data.appliedRuleId").value(ruleId.toString()));
    }

    // ── IT-EP-002: 無規則 → effectivePrice = basePrice ───────────

    @Test
    @DisplayName("IT-EP-002: 無定價規則時，effectivePrice = basePrice，appliedRuleType = null")
    void getEffectivePrice_noActiveRule_returnsBasePrice() throws Exception {
        Listing listing = buildMockListing();

        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(pricingRuleRepository.findByListingIdAndIsActiveTrue(LISTING_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .param("checkDate", LocalDate.now().toString())
                        .param("stayDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basePrice").value(500.00))
                .andExpect(jsonPath("$.data.effectivePrice").value(500.00))
                .andExpect(jsonPath("$.data.appliedRuleType").doesNotExist());
    }

    // ── IT-EP-003: 不存在的 listingId → 404 ──────────────────────

    @Test
    @DisplayName("IT-EP-003: 不存在的 listingId → 404")
    void getEffectivePrice_listingNotFound_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(listingRepository.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v2/listings/" + unknownId + "/effective-price")
                        .param("checkDate", LocalDate.now().toString())
                        .param("stayDays", "1"))
                .andExpect(status().isNotFound());
    }

    // ── IT-EP-004: MANUAL_OVERRIDE 漲價 → effectivePrice > basePrice（AI-2406c）──

    @Test
    @DisplayName("IT-EP-004: MANUAL_OVERRIDE price>base → effectivePrice 漲價（AI-2406c）")
    void getEffectivePrice_withMarkupRule_returnsMarkedUpPrice() throws Exception {
        UUID ruleId = UUID.randomUUID();
        Listing listing = buildMockListing();
        PricingRule markupRule = PricingRule.builder()
                .id(ruleId)
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingId(LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                .ruleName("商品旺季手動調高")
                .priority(10)
                .config(Map.of("price", 650.0)) // 高於 basePrice 500 → 漲價
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .isActive(true)
                .build();

        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(pricingRuleRepository.findByListingIdAndIsActiveTrue(LISTING_ID)).thenReturn(List.of(markupRule));

        mockMvc.perform(get(BASE_URL)
                        .param("checkDate", LocalDate.now().toString())
                        .param("stayDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basePrice").value(500.00))
                .andExpect(jsonPath("$.data.effectivePrice").value(650.00))
                .andExpect(jsonPath("$.data.appliedRuleType").value("MANUAL_OVERRIDE"));
    }

    // ── IT-EP-005: 商品所屬店鋪的動態定價開關關閉 → 規則不套用（DEF-424，Sprint 309）──

    @Test
    @DisplayName("IT-EP-005: 商品所屬店鋪的動態定價開關關閉時，即使有生效中的規則，effectivePrice 仍是 basePrice（DEF-424）")
    void getEffectivePrice_storeToggleDisabled_returnsBasePrice() throws Exception {
        UUID ruleId = UUID.randomUUID();
        when(featureToggleService.isFeatureEnabledForTenant(UUID.fromString(TEST_TENANT_ID), "DYNAMIC_PRICING_ENABLED"))
                .thenReturn(false);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        // 誘餌：萬一關卡錯誤放行，這條規則會讓有效售價變成 450——「仍是 500」才真的證明規則沒被套用
        when(pricingRuleRepository.findByListingIdAndIsActiveTrue(LISTING_ID))
                .thenReturn(List.of(buildActiveRule(ruleId, 10)));

        mockMvc.perform(get(BASE_URL)
                        .param("checkDate", LocalDate.now().toString())
                        .param("stayDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basePrice").value(500.00))
                .andExpect(jsonPath("$.data.effectivePrice").value(500.00))
                .andExpect(jsonPath("$.data.appliedRuleType").doesNotExist())
                .andExpect(jsonPath("$.data.appliedRuleId").doesNotExist());
        verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
    }
}
