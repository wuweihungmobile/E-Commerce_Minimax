package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M12 動態定價 — Product Listing 延伸整合測試 (Sprint 22 US-001)
 *
 * 測試範圍：
 * - IT-M12-009: 商品商家使用 product:create 建立定價規則（listingId）
 * - IT-M12-010: 商品商家使用 product:read 查詢 listingId 規則列表
 * - IT-M12-011: BUYER（無 product:create/room:create 權限）建立規則 → 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M12-Product: M12 動態定價 Product Listing 延伸整合測試")
class M12PricingProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PricingRuleRepository pricingRuleRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String BASE_URL = "/v2/dashboard/pricing";
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final UUID LISTING_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");

    private PricingDto.CreateRuleRequest buildProductPricingRuleRequest() {
        return PricingDto.CreateRuleRequest.builder()
                .listingId(LISTING_ID)
                .ruleType(PricingDto.PricingRuleType.SEASONAL)
                .ruleName("節慶促銷 10% 折扣")
                .priority(3)
                .config(Map.of("discountPercent", 10))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusMonths(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildMockProductPricingRule(UUID ruleId) {
        return PricingRule.builder()
                .id(ruleId)
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingId(LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.SEASONAL)
                .ruleName("節慶促銷 10% 折扣")
                .priority(3)
                .config(Map.of("discountPercent", 10))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusMonths(1))
                .isActive(true)
                .build();
    }

    // ── IT-M12-009: 商品商家建立 Product 定價規則（product:create）──

    @Test
    @DisplayName("IT-M12-009: 商品商家使用 product:create 建立定價規則（listingId）")
    // DEF-242/243：改用 WithErpSecurity 取代 WithMockUser，理由同 M12PricingIntegrationTest
    // ——WithMockUser 的 principal 非 UserPrincipal 型別，會讓新增的 @AuthenticationPrincipal
    // 解析為 null 而 NPE（500）。
    @WithErpSecurity(tenantId = "550e8400-e29b-41d4-a716-446655440001",
            authorities = {"product:create", "product:read", "product:update", "product:delete"})
    void createRule_withProductAuthority_andListingId_returns201() throws Exception {
        PricingDto.CreateRuleRequest request = buildProductPricingRuleRequest();
        UUID savedRuleId = UUID.randomUUID();
        PricingRule savedRule = buildMockProductPricingRule(savedRuleId);

        // DEF-242：createRule 新增了租戶擁有權檢查，需要先查得 listing 才能驗證
        Listing listing = Listing.builder().tenantId(UUID.fromString(TEST_TENANT_ID)).build();
        listing.setId(LISTING_ID);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(savedRule);

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ruleId").isNotEmpty())
                .andExpect(jsonPath("$.data.listingId").value(LISTING_ID.toString()));
    }

    // ── IT-M12-010: 商品商家查詢 listingId 規則列表 ──────────────

    @Test
    @DisplayName("IT-M12-010: 商品商家使用 product:read 查詢 listingId 規則列表")
    @WithMockUser(username = "seller", authorities = {"product:read"})
    void getRules_withListingId_returnsRuleList() throws Exception {
        UUID ruleId = UUID.randomUUID();
        PricingRule rule = buildMockProductPricingRule(ruleId);

        when(pricingRuleRepository.findByListingId(LISTING_ID)).thenReturn(List.of(rule));

        mockMvc.perform(get(BASE_URL + "/rules")
                        .param("listingId", LISTING_ID.toString())
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].listingId").value(LISTING_ID.toString()));
    }

    // ── IT-M12-011: BUYER 建立定價規則 → 403 ──────────────────────

    @Test
    @DisplayName("IT-M12-011: BUYER（無 product:create/room:create 權限）建立規則 → 403")
    @WithMockUser(username = "buyer", authorities = {"order:create"})
    void createRule_withBuyerAuthority_returns403() throws Exception {
        PricingDto.CreateRuleRequest request = buildProductPricingRuleRequest();

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
