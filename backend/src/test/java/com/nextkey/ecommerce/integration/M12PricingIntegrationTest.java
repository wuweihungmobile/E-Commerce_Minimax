package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M12 動態定價 Backend API 整合測試 (T-M12-06)
 *
 * 測試範圍：
 * - IT-M12-001: 建立規則-成功
 * - IT-M12-002: 建立規則-DYNAMIC_PRICING_ENABLED未啟用
 * - IT-M12-003: 更新規則-成功
 * - IT-M12-004: 刪除規則-成功
 * - IT-M12-005: 查詢規則列表-依房源篩選
 * - IT-M12-006: 價格計算-週末+早鳥
 * - IT-M12-007: 價格計算-長住折扣套用
 * - IT-M12-008: 手動覆蓋-優先於規則
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M12: M12 動態定價 Backend API 整合測試")
// DEF-242/243：改用 WithErpSecurity 取代 WithMockUser——後者的 principal 不是本專案的
// UserPrincipal 型別，PricingController 新增的 @AuthenticationPrincipal UserPrincipal 解析結果
// 恆為 null，呼叫 principal.getRole() 會 NPE（500）。WithErpSecurity 同時提供真實 UserPrincipal
// 與 TenantContext（tenantId 對齊本檔測試資料的 TEST_TENANT_ID），authorities() 保留原本細粒度權限碼。
@WithErpSecurity(tenantId = "550e8400-e29b-41d4-a716-446655440001",
        authorities = {"room:create", "room:read", "room:update", "room:delete"})
class M12PricingIntegrationTest {

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
    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");

    // 測試資料工廠方法
    private PricingDto.CreateRuleRequest buildValidCreateRuleRequest() {
        return PricingDto.CreateRuleRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingDto.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("Weekend 20% up")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }

    private Listing buildMockListing() {
        Listing listing = Listing.builder()
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingType(Listing.ListingType.ROOM)
                .title("Test Room")
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(1000.00))
                .currency("TWD")
                .build();
        listing.setId(ROOM_LISTING_ID);
        return listing;
    }

    private Room buildMockRoom(Listing listing) {
        Room room = Room.builder()
                .listing(listing)
                .location("Taipei")
                .maxGuests(4)
                .build();
        return room;
    }

    private PricingRule buildMockPricingRule(UUID ruleId, PricingDto.PricingRuleType ruleType, String ruleName) {
        return PricingRule.builder()
                .id(ruleId)
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.valueOf(ruleType.name()))
                .ruleName(ruleName)
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }

    // ── IT-M12-001: 建立規則-成功 (P0) ──────────────────────────

    @Test
    @DisplayName("IT-M12-001: 建立規則-成功")
    void createRule_success_returns201() throws Exception {
        PricingDto.CreateRuleRequest request = buildValidCreateRuleRequest();

        UUID savedRuleId = UUID.randomUUID();
        PricingRule savedRule = buildMockPricingRule(savedRuleId, request.getRuleType(), request.getRuleName());

        // DEF-242：createRule 新增了租戶擁有權檢查，需要先查得 listing 才能驗證
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(savedRule);

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ruleId").isNotEmpty())
                .andExpect(jsonPath("$.data.ruleName").value("Weekend 20% up"));
    }

    // ── IT-M12-002: 建立規則-DYNAMIC_PRICING_ENABLED未啟用 (P0) ──

    @Test
    @DisplayName("IT-M12-002: 建立規則-DYNAMIC_PRICING_ENABLED未啟用")
    void createRule_featureNotEnabled_returns403() throws Exception {
        PricingDto.CreateRuleRequest request = buildValidCreateRuleRequest();

        // Mock FeatureToggleService 拋出異常
        doThrow(new com.nextkey.ecommerce.shared.exception.BusinessException(
                com.nextkey.ecommerce.shared.exception.ErrorCode.E_2004,
                "Feature 'DYNAMIC_PRICING_ENABLED' is disabled for this tenant"))
            .when(featureToggleService).checkFeatureEnabled("DYNAMIC_PRICING_ENABLED");

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()); // 403
    }

    // ── IT-M12-003: 更新規則-成功 (P1) ───────────────────────────

    @Test
    @DisplayName("IT-M12-003: 更新規則-成功")
    void updateRule_success_returns200() throws Exception {
        UUID ruleId = UUID.randomUUID();
        PricingRule existingRule = buildMockPricingRule(ruleId,
                PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Old Rule Name");

        PricingDto.UpdateRuleRequest request = PricingDto.UpdateRuleRequest.builder()
                .ruleName("Updated Rule Name")
                .priority(10)
                .build();

        when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existingRule));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(existingRule);

        mockMvc.perform(put(BASE_URL + "/rules/{ruleId}", ruleId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── IT-M12-004: 刪除規則-成功 (P1) ─────────────────────────

    @Test
    @DisplayName("IT-M12-004: 刪除規則-成功")
    void deleteRule_success_returns200() throws Exception {
        UUID ruleId = UUID.randomUUID();
        PricingRule existingRule = buildMockPricingRule(ruleId,
                PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Rule to Delete");

        when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existingRule));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(existingRule);

        mockMvc.perform(delete(BASE_URL + "/rules/{ruleId}", ruleId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── IT-M12-005: 查詢規則列表-依房源篩選 (P1) ──────────────────

    @Test
    @DisplayName("IT-M12-005: 查詢規則列表-依房源篩選")
    // DEF-255：getRules 補上租戶擁有權檢查後需要先查得 listing 才能驗證，理由同 IT-M12-001。
    void getRules_filterByRoomListingId_returnsRules() throws Exception {
        UUID ruleId1 = UUID.randomUUID();
        UUID ruleId2 = UUID.randomUUID();

        List<PricingRule> rules = List.of(
                buildMockPricingRule(ruleId1, PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Weekend Rule"),
                buildMockPricingRule(ruleId2, PricingDto.PricingRuleType.EARLY_BIRD, "Early Bird Rule")
        );

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(rules);

        mockMvc.perform(get(BASE_URL + "/rules")
                        .param("roomListingId", ROOM_LISTING_ID.toString())
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ── IT-M12-006: 價格計算-週末+早鳥 (P0) ─────────────────────

    @Test
    @DisplayName("IT-M12-006: 價格計算-週末+早鳥")
    void calculatePrice_weekendAndEarlyBird_returnsAdjustedPrice() throws Exception {
        // 週五入住，套用早鳥折扣
        LocalDate checkIn = LocalDate.of(2026, 4, 10); // 週五
        LocalDate checkOut = LocalDate.of(2026, 4, 13); // 住了3晚

        Listing listing = buildMockListing();
        Room room = buildMockRoom(listing);

        PricingRule weekendRule = buildMockPricingRule(UUID.randomUUID(),
                PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Weekend Rule");
        PricingRule earlyBirdRule = buildMockPricingRule(UUID.randomUUID(),
                PricingDto.PricingRuleType.EARLY_BIRD, "Early Bird Rule");

        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
        when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                .thenReturn(List.of(weekendRule, earlyBirdRule));

        PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();

        mockMvc.perform(post(BASE_URL + "/calculate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nights").value(3));
    }

    // ── IT-M12-007: 價格計算-長住折扣套用 (P0) ───────────────────

    @Test
    @DisplayName("IT-M12-007: 價格計算-長住折扣套用")
    void calculatePrice_longStayDiscount_appliesDiscount() throws Exception {
        // 入住7天，套用長住折扣
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(7);

        Listing listing = buildMockListing();
        Room room = buildMockRoom(listing);

        PricingRule longStayRule = PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.LONG_STAY)
                .ruleName("Long Stay 20% off")
                .priority(5)
                .config(Map.of("discountPercent", 20.0, "minNights", 3))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();

        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
        when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                .thenReturn(List.of(longStayRule));

        PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();

        mockMvc.perform(post(BASE_URL + "/calculate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nights").value(7));
    }

    // ── IT-M12-008: 手動覆蓋-優先於規則 (P0) ─────────────────────

    @Test
    @DisplayName("IT-M12-008: 手動覆蓋-優先於規則")
    void setCalendarPrice_manualOverride_overridesRules() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(1);

        Listing listing = buildMockListing();
        Room room = buildMockRoom(listing);

        PricingRule manualOverrideRule = PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                .ruleName("Manual price override")
                .priority(100) // 最高優先級
                .config(Map.of("price", 5000.0))
                .validFrom(targetDate)
                .validTo(targetDate)
                .isActive(true)
                .build();

        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(manualOverrideRule);

        PricingDto.SetCalendarPriceRequest request = PricingDto.SetCalendarPriceRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .date(targetDate)
                .price(BigDecimal.valueOf(5000.00))
                .reason("Special event pricing")
                .build();

        mockMvc.perform(post(BASE_URL + "/calendar/price")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.priceType").value("MANUAL"));
    }

    // ── IT-M12-009 ~ 012: 定價規則建立限制與衝突檢核（PRD §5.5.1 / Sprint 95） ──

    @Test
    @DisplayName("IT-M12-009: 建立規則-已達 50 條上限 → 400 E-4001")
    void createRule_ruleLimitExceeded_returns400() throws Exception {
        List<PricingRule> fiftyRules = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            fiftyRules.add(buildMockPricingRule(UUID.randomUUID(),
                    PricingDto.PricingRuleType.SEASONAL, "Existing Rule " + i));
        }
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(fiftyRules);

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidCreateRuleRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-4001"));

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("IT-M12-010: 建立規則-同類型時間重疊未確認覆蓋 → 400 E-4001")
    void createRule_overlappingSameTypeWithoutConfirm_returns400() throws Exception {
        PricingRule existing = buildMockPricingRule(UUID.randomUUID(),
                PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Existing Weekend Rule");

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                .thenReturn(List.of(existing));

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidCreateRuleRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-4001"));

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("IT-M12-011: 建立規則-確認覆蓋後成功建立，舊規則軟刪除")
    void createRule_confirmOverride_returns201AndSoftDeletesOld() throws Exception {
        PricingRule existing = buildMockPricingRule(UUID.randomUUID(),
                PricingDto.PricingRuleType.WEEKDAY_WEEKEND, "Existing Weekend Rule");

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockListing()));
        when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                .thenReturn(List.of(existing));
        when(pricingRuleRepository.save(any(PricingRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PricingDto.CreateRuleRequest request = PricingDto.CreateRuleRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingDto.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("New Weekend Rule")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.3))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .confirmOverride(true)
                .build();

        mockMvc.perform(post(BASE_URL + "/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        org.assertj.core.api.Assertions.assertThat(existing.getIsActive()).isFalse();
        verify(pricingRuleRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("IT-M12-012: 定價日曆預覽-過去日期 → 400 E-4001")
    void getCalendarPreview_pastStartDate_returns400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/calendar")
                        .param("roomListingId", ROOM_LISTING_ID.toString())
                        .param("startDate", LocalDate.now().minusDays(5).toString())
                        .param("endDate", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-4001"));
    }
}