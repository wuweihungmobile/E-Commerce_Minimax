package com.nextkey.ecommerce.core.pricing;

import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PricingService 單元測試 (T-M12-05)
 *
 * 測試範圍：
 * - 價格計算：平假日、週末加成、節日加成、早鳥折扣
 * - 長住折扣：7天、30天
 * - 最後一刻折扣
 * - 優先級邏輯
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PricingService: 價格計算與規則應用")
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private AuditService auditService;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PricingService pricingService;

    // 測試資料
    private static final UUID TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);

    // DEF-242：createRule/updateRule/deleteRule/setCalendarPrice/overridePrice 新增租戶擁有權檢查後
    // 需要呼叫者租戶脈絡；其餘既有測試（價格計算/有效售價）不讀取 TenantContext，設定對其無影響。
    // 清除交由全域註冊的 ThreadLocalIsolationExtension 於每個測試後自動處理，不需手動 @AfterEach。
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setCurrentTenant(TENANT_ID);
        // DEF-414：createRule 需要 Tenant 實體（INSERT 依據的是 .tenant(...) 關聯物件）；不是每個案例都會建立規則
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantOf(TENANT_ID)));
        // DEF-424：規則只在房源／商品所屬店鋪的動態定價開關開啟時套用。預設每個租戶都開啟，讓既有的價格計算案例專注在規則數學；
        // 要驗證開關本身的案例（StoreToggleGatesApplicationTests）會以指定租戶的 stub 覆蓋這個預設。
        lenient().when(featureToggleService.isFeatureEnabledForTenant(any(), eq("DYNAMIC_PRICING_ENABLED")))
                .thenReturn(true);
    }

    private static Tenant tenantOf(final UUID id) {
        Tenant tenant = Tenant.builder().build();
        tenant.setId(id);
        return tenant;
    }

    @Nested
    @DisplayName("UT-M12-001 ~ UT-M12-004: 價格計算基礎")
    class PriceCalculationTests {

        @Test
        @DisplayName("UT-M12-001: 價格計算-平日無折扣 (P0)")
        void calculatePrice_weekdayNoDiscount_returnsBasePrice() {
            // Arrange: 週一入住，無特殊規則
            LocalDate checkIn = LocalDate.of(2026, 4, 6); // 週一
            LocalDate checkOut = LocalDate.of(2026, 4, 8); // 住了2晚

            Room room = buildMockRoom();
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList()); // 無適用規則

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(2);
            assertThat(response.getBaseTotal()).isEqualTo(BASE_PRICE.multiply(BigDecimal.valueOf(2)));
            assertThat(response.getAdjustedTotal()).isEqualTo(response.getBaseTotal());
            assertThat(response.getDiscount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("UT-M12-002: 價格計算-週末加成 (P0)")
        void calculatePrice_weekendAppliesMultiplier() {
            // Arrange: 週五入住，套用週末加成規則
            LocalDate checkIn = LocalDate.of(2026, 4, 10); // 週五
            LocalDate checkOut = LocalDate.of(2026, 4, 13); // 住了3晚 (週五、六、日)

            Room room = buildMockRoom();
            PricingRule weekendRule = buildWeekendRule();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(weekendRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(3);

            // 根據 PricingService 實作：週末加成只套用於週五、週六（週日不算週末）
            // 週五(1200) + 週六(1200) + 週日(1000平日) = 3600
            BigDecimal expectedBase = BASE_PRICE.multiply(BigDecimal.valueOf(3));
            BigDecimal expectedAdjusted = BigDecimal.valueOf(3600); // 1200 + 1200 + 1000

            assertThat(response.getBaseTotal()).isEqualTo(expectedBase);
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }

        @Test
        @DisplayName("UT-M12-003: 價格計算-節日加成 (P0)")
        void calculatePrice_seasonalAppliesMultiplier() {
            // Arrange: 春節期間，套用節日加成
            LocalDate checkIn = LocalDate.of(2026, 1, 28); // 春節
            LocalDate checkOut = LocalDate.of(2026, 1, 30);

            Room room = buildMockRoom();
            PricingRule seasonalRule = buildSeasonalRule();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(seasonalRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(2);
            // 節日倍率 1.5x
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(1.5))
                    .multiply(BigDecimal.valueOf(2));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }

        @Test
        @DisplayName("UT-M12-004: 價格計算-早鳥折扣 (P0)")
        void calculatePrice_earlyBirdAppliesDiscount() {
            // Arrange: 早鳥語意（AI-2401）= 下單日距入住日 >= minDaysAhead(7) 才適用。
            // 明確傳 bookingDate（提前 8 天下單）使測試不受執行當日影響。
            LocalDate checkIn = LocalDate.of(2026, 6, 2);
            LocalDate checkOut = checkIn.plusDays(1);
            LocalDate bookingDate = checkIn.minusDays(8); // 提前 8 天下單，滿足 >= 7

            Room room = buildMockRoom();
            // 規則有效期需涵蓋入住日
            PricingRule earlyBirdRule = buildEarlyBirdRuleWithValidFrom(checkIn.minusDays(30));

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(earlyBirdRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .bookingDate(bookingDate)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(1);
            // 早鳥折扣 15% -> 850
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(0.85));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }
    }

    @Nested
    @DisplayName("UT-M12-005 ~ UT-M12-006: 長住折扣")
    class LongStayDiscountTests {

        @Test
        @DisplayName("UT-M12-005: 價格計算-長住折扣7天 (P1)")
        void calculatePrice_longStay7Days_appliesProgressiveDiscount() {
            // Arrange: 入住7天
            LocalDate checkIn = LocalDate.now().plusDays(10);
            LocalDate checkOut = checkIn.plusDays(7);

            Room room = buildMockRoom();
            PricingRule longStayRule = buildLongStayRule();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(longStayRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(7);
            // 7天入住，折扣為 min(20%, 20% * 7/7) = 20%
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(0.80))
                    .multiply(BigDecimal.valueOf(7));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }

        @Test
        @DisplayName("UT-M12-006: 價格計算-長住折扣30天 (P1)")
        void calculatePrice_longStay30Days_appliesMaxDiscount() {
            // Arrange: 入住30天
            LocalDate checkIn = LocalDate.now().plusDays(10);
            LocalDate checkOut = checkIn.plusDays(30);

            Room room = buildMockRoom();
            PricingRule longStayRule = buildLongStayRule();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(longStayRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(30);
            // 30天入住，折扣為 min(20%, 20% * 30/7) = 20% (capped)
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(0.80))
                    .multiply(BigDecimal.valueOf(30));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }
    }

    @Nested
    @DisplayName("UT-M12-007: 最後一刻折扣")
    class LastMinuteDiscountTests {

        @Test
        @DisplayName("UT-M12-007: 價格計算-最後一刻折扣 (P2)")
        void calculatePrice_lastMinuteDiscount_appliesWhenCheckInSoon() {
            // Arrange: 今天預訂，明天入住 (最後一刻)
            LocalDate checkIn = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(1);

            Room room = buildMockRoom();
            PricingRule lastMinuteRule = buildLastMinuteRule();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(lastMinuteRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getNights()).isEqualTo(1);
            // 最後一刻折扣 25%
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(0.75));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);
        }
    }

    @Nested
    @DisplayName("UT-M12-008 ~ UT-M12-009: 優先級邏輯")
    class PriorityTests {

        @Test
        @DisplayName("UT-M12-008: 優先級-高優先級覆蓋低優先級 (P0)")
        void calculatePrice_highPriorityOverridesLowPriority() {
            // Arrange: 同一天有兩個規則，高優先級（早鳥）勝出。
            // 早鳥語意（AI-2401）以 bookingDate 距入住日計，明確傳提前 8 天使測試穩定。
            LocalDate validFrom = LocalDate.of(2026, 5, 22); // 規則起始日涵蓋 checkIn
            LocalDate checkIn = validFrom.plusDays(8);
            LocalDate checkOut = checkIn.plusDays(1);
            LocalDate bookingDate = checkIn.minusDays(8); // 提前 8 天下單，滿足 minDaysAhead >= 7

            Room room = buildMockRoom();
            PricingRule lowPriorityRule = buildWeekendRuleWithPriority(0);
            PricingRule highPriorityRule = buildEarlyBirdRuleWithValidFrom(validFrom, 10);

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(lowPriorityRule, highPriorityRule)); // 未排序

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .bookingDate(bookingDate)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert: 高優先級的早鳥折扣(85%)勝出，而非週末加成(120%)
            BigDecimal expectedAdjusted = BASE_PRICE.multiply(BigDecimal.valueOf(0.85));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(expectedAdjusted);

            // 驗證應用的規則名稱是早鳥
            assertThat(response.getBreakdown().get(0).getAppliedRuleName())
                    .isEqualTo("Early Bird 15% off");
        }

        @Test
        @DisplayName("UT-M12-009: 優先級-同優先級時後建立覆蓋 (P1)")
        void calculatePrice_samePriority_laterRuleWins() {
            // Arrange: 同優先級的兩個規則，後建立的覆蓋先建立的
            LocalDate checkIn = LocalDate.of(2026, 4, 10);
            LocalDate checkOut = checkIn.plusDays(1);

            Room room = buildMockRoom();

            PricingRule earlierRule = PricingRule.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .ruleName("Earlier Weekend Rule")
                    .priority(5)
                    .config(Map.of("weekendMultiplier", 1.3))
                    .validFrom(checkIn.minusYears(1))
                    .validTo(checkIn.plusYears(1))
                    .isActive(true)
                    .createdAt(checkIn.minusMonths(2).atStartOfDay().toInstant(ZoneOffset.UTC))
                    .build();

            PricingRule laterRule = PricingRule.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .ruleName("Later Weekend Rule")
                    .priority(5) // 相同優先級
                    .config(Map.of("weekendMultiplier", 1.5)) // 更高倍率
                    .validFrom(checkIn.minusYears(1))
                    .validTo(checkIn.plusYears(1))
                    .isActive(true)
                    .createdAt(checkIn.minusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)) // 後建立
                    .build();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(earlierRule, laterRule));

            PricingDto.CalculatePriceRequest request = PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkOut)
                    .build();

            // Act
            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);

            // Assert: 相同優先級（5）時，較晚建立的 laterRule（weekendMultiplier=1.5）覆蓋較早建立的
            // earlierRule（weekendMultiplier=1.3），AI-2407 tie-break 業務語意決策：後建立者優先。
            assertThat(response.getBreakdown().get(0).getAppliedRuleName())
                    .isEqualTo("Later Weekend Rule");
            assertThat(response.getBreakdown().get(0).getAdjustmentValue())
                    .isEqualByComparingTo(BigDecimal.valueOf(50));
        }
    }

    @Nested
    @DisplayName("UT-M12-010 ~ UT-M12-012: bookingDate 提前/臨近天數邊界（AI-2401）")
    class BookingDateBoundaryTests {

        @Test
        @DisplayName("UT-M12-010: 早鳥-剛好達提前天數（daysAhead == minDaysAhead）→ 折扣生效")
        void earlyBird_exactlyMinDaysAhead_applies() {
            LocalDate checkIn = LocalDate.of(2026, 6, 2);
            LocalDate checkOut = checkIn.plusDays(1);
            LocalDate bookingDate = checkIn.minusDays(7); // 剛好 7 天（minDaysAhead=7）

            Room room = buildMockRoom();
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(buildEarlyBirdRuleWithValidFrom(checkIn.minusDays(30))));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(ROOM_LISTING_ID)
                            .checkInDate(checkIn).checkOutDate(checkOut)
                            .bookingDate(bookingDate).build());

            assertThat(response.getAdjustedTotal())
                    .isEqualByComparingTo(BASE_PRICE.multiply(BigDecimal.valueOf(0.85)));
            assertThat(response.getBreakdown().get(0).getAppliedRuleName()).isEqualTo("Early Bird 15% off");
        }

        @Test
        @DisplayName("UT-M12-011: 早鳥-不足提前天數（daysAhead == minDaysAhead - 1）→ 不生效（原價）")
        void earlyBird_oneDayShort_notApplied() {
            LocalDate checkIn = LocalDate.of(2026, 6, 2);
            LocalDate checkOut = checkIn.plusDays(1);
            LocalDate bookingDate = checkIn.minusDays(6); // 只提前 6 天，不足 7

            Room room = buildMockRoom();
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(buildEarlyBirdRuleWithValidFrom(checkIn.minusDays(30))));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(ROOM_LISTING_ID)
                            .checkInDate(checkIn).checkOutDate(checkOut)
                            .bookingDate(bookingDate).build());

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(BASE_PRICE); // 原價
            assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("UT-M12-012: 末班車-超出臨近窗口（daysUntilCheckIn > maxDaysAhead）→ 不生效")
        void lastMinute_beyondWindow_notApplied() {
            LocalDate checkIn = LocalDate.of(2026, 6, 2);
            LocalDate checkOut = checkIn.plusDays(1);
            LocalDate bookingDate = checkIn.minusDays(5); // 距入住 5 天 > maxDaysAhead=3

            Room room = buildMockRoom();
            PricingRule lastMinuteRule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.LAST_MINUTE)
                    .ruleName("Last Minute 25% off").priority(5)
                    .config(Map.of("discountPercent", 25.0, "maxDaysAhead", 3))
                    .validFrom(checkIn.minusDays(30)).validTo(checkIn.plusDays(30)).isActive(true).build();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(lastMinuteRule));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(ROOM_LISTING_ID)
                            .checkInDate(checkIn).checkOutDate(checkOut)
                            .bookingDate(bookingDate).build());

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(BASE_PRICE); // 原價，末班車不適用
        }
    }

    @Nested
    @DisplayName("UT-M12-013 ~ UT-M12-014: 漲價與優雅降級（AI-2406b）")
    class MarkupAndFallbackTests {

        @Test
        @DisplayName("UT-M12-013: 手動覆蓋漲價（price > basePrice）→ adjustedTotal 含漲價")
        void calculatePrice_manualOverrideMarkup_returnsMarkedUpTotal() {
            LocalDate checkIn = LocalDate.of(2026, 4, 6);
            LocalDate checkOut = LocalDate.of(2026, 4, 8); // 2 晚

            Room room = buildMockRoom();
            PricingRule manualRule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                    .ruleName("Manual Override 1500").priority(10)
                    .config(Map.of("price", 1500.0)) // 高於 basePrice 1000 → 漲價
                    .validFrom(checkIn.minusDays(30)).validTo(checkIn.plusDays(30)).isActive(true).build();

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(manualRule));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(ROOM_LISTING_ID)
                            .checkInDate(checkIn).checkOutDate(checkOut).build());

            // 每日 1500 × 2 晚 = 3000（漲價），baseTotal = 2000
            assertThat(response.getBaseTotal()).isEqualByComparingTo(BigDecimal.valueOf(2000));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(BigDecimal.valueOf(3000));
            // discount clamp 非負：漲價時為 0（供 Booking 層改以有號差額表達）
            assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("UT-M12-014: 無 Room 記錄但 listing 存在 → 優雅降級以 basePrice 計價（不 throw）")
        void calculatePrice_noRoomButListingExists_fallsBackToBasePrice() {
            LocalDate checkIn = LocalDate.of(2026, 4, 6);
            LocalDate checkOut = LocalDate.of(2026, 4, 8); // 2 晚

            Listing listing = Listing.builder().basePrice(BASE_PRICE).currency("TWD").tenantId(TENANT_ID).build();
            listing.setId(ROOM_LISTING_ID);

            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.empty());
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(
                    PricingDto.CalculatePriceRequest.builder()
                            .roomListingId(ROOM_LISTING_ID)
                            .checkInDate(checkIn).checkOutDate(checkOut).build());

            assertThat(response).isNotNull();
            assertThat(response.getBaseTotal()).isEqualByComparingTo(BigDecimal.valueOf(2000));
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }
    }

    @Nested
    @DisplayName("UT-M12-015 ~ UT-M12-018: PRODUCT getEffectivePrice 漲價 + 折扣向後相容（AI-2406c）")
    class ProductEffectivePriceTests {

        private static final UUID PRODUCT_LISTING_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");

        private Listing productListing() {
            Listing listing = Listing.builder()
                    .listingType(Listing.ListingType.PRODUCT)
                    .basePrice(BASE_PRICE).currency("TWD").tenantId(TENANT_ID).build();
            listing.setId(PRODUCT_LISTING_ID);
            return listing;
        }

        private PricingRule productRule(PricingRule.PricingRuleType type, Map<String, Object> config) {
            return PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(PRODUCT_LISTING_ID)
                    .ruleType(type).ruleName(type.name() + " product").priority(10)
                    .config(config)
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
        }

        @Test
        @DisplayName("UT-M12-015: MANUAL_OVERRIDE price>base → effectivePrice 漲價")
        void manualOverrideMarkup() {
            LocalDate checkDate = LocalDate.of(2027, 8, 3);
            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(productRule(PricingRule.PricingRuleType.MANUAL_OVERRIDE, Map.of("price", 1300.0))));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(1300)); // 1000→1300 漲價
        }

        @Test
        @DisplayName("UT-M12-016: SEASONAL multiplier>1 → effectivePrice 漲價")
        void seasonalMultiplierMarkup() {
            LocalDate checkDate = LocalDate.of(2027, 8, 3);
            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(productRule(PricingRule.PricingRuleType.SEASONAL, Map.of("multiplier", 1.2))));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(1200)); // 1000×1.2
        }

        @Test
        @DisplayName("UT-M12-017: WEEKDAY_WEEKEND 週末 → 漲價；平日 → 原價")
        void weekdayWeekendMarkup() {
            LocalDate saturday = LocalDate.of(2027, 8, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            LocalDate tuesday = LocalDate.of(2027, 8, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(productRule(
                            PricingRule.PricingRuleType.WEEKDAY_WEEKEND, Map.of("weekendMultiplier", 1.5))));

            assertThat(pricingService.getEffectivePrice(PRODUCT_LISTING_ID, saturday, 1).getEffectivePrice())
                    .isEqualByComparingTo(BigDecimal.valueOf(1500)); // 週末 1000×1.5
            assertThat(pricingService.getEffectivePrice(PRODUCT_LISTING_ID, tuesday, 1).getEffectivePrice())
                    .isEqualByComparingTo(BASE_PRICE); // 平日原價
        }

        @Test
        @DisplayName("UT-M12-018: SEASONAL + discountPercent（S44 向後相容）→ 仍為折扣，不退步")
        void seasonalDiscountPercentBackwardCompat() {
            LocalDate checkDate = LocalDate.of(2027, 8, 3);
            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(productRule(
                            PricingRule.PricingRuleType.SEASONAL, Map.of("discountPercent", 10.0))));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(900)); // 1000×0.9 折扣不退步
        }

        /**
         * Sprint 166（DEF-218）：折扣向後相容分支（見上方 UT-M12-018）直接對 config 的
         * {@code discountPercent} 呼叫 {@code new BigDecimal(discountPct.toString())}，
         * 未比照同檔案其餘所有 config 讀取（{@code getDoubleConfig}）具備的 try/catch 防護。
         * {@code config} 是 {@code Map<String, Object>}，寫入端（{@code PricingController}
         * {@code POST/PUT /v2/dashboard/pricing/rules}）未驗證其內容型別，賣家送入非數字字串
         * （如誤填 "10%"）會被原樣存入。讀取端是 {@code GET /v2/listings/{id}/effective-price}
         * （{@code product:read}/{@code room:read} 即可呼叫，一般買家瀏覽商品即會觸發）與加入
         * 購物車（{@code RedisCartService}），故此缺陷會讓「該商品」對「任何」買家瀏覽/加入購物車
         * 都拋出未攔截的 {@code NumberFormatException}（{@code IllegalArgumentException} 子類別）。
         */
        @Test
        @DisplayName("Sprint 166（DEF-218）：discountPercent 為非數字字串時，getEffectivePrice"
                + "不可讓 NumberFormatException 未攔截往外拋")
        void discountPercentNonNumericString_doesNotThrow() {
            LocalDate checkDate = LocalDate.of(2027, 8, 3);
            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(productRule(
                            PricingRule.PricingRuleType.SEASONAL, Map.of("discountPercent", "10%"))));

            assertThatCode(() -> pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1))
                    .as("非數字格式的 discountPercent 應被安全忽略（回退為原價），而非讓例外未攔截往外拋")
                    .doesNotThrowAnyException();

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1);
            assertThat(resp.getEffectivePrice())
                    .as("解析失敗時應回退為原價，比照 getDoubleConfig 既有的 null-safe 語意")
                    .isEqualByComparingTo(BASE_PRICE);
        }

        @Test
        @DisplayName("UT-M12-019: 同優先級時後建立覆蓋（AI-2407 tie-break，getEffectivePrice）")
        void samePriority_laterRuleWins() {
            LocalDate checkDate = LocalDate.of(2027, 8, 3);

            PricingRule earlierRule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(PRODUCT_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).ruleName("Earlier Seasonal Rule")
                    .priority(10).config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true)
                    .createdAt(checkDate.minusMonths(2).atStartOfDay().toInstant(ZoneOffset.UTC))
                    .build();

            PricingRule laterRule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(PRODUCT_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).ruleName("Later Seasonal Rule")
                    .priority(10) // 相同優先級
                    .config(Map.of("multiplier", 1.5)) // 更高倍率
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true)
                    .createdAt(checkDate.minusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)) // 後建立
                    .build();

            when(listingRepository.findById(PRODUCT_LISTING_ID)).thenReturn(Optional.of(productListing()));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_LISTING_ID))
                    .thenReturn(List.of(earlierRule, laterRule));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(PRODUCT_LISTING_ID, checkDate, 1);

            assertThat(resp.getAppliedRuleType()).isEqualTo("SEASONAL");
            assertThat(resp.getEffectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(1500)); // 1000×1.5（後建立者）
        }
    }

    @Nested
    @DisplayName("UT-M12-020 ~ UT-M12-023: 定價規則建立限制與衝突檢核（PRD §5.5.1 / TC-LO2-M12 / TC-PR）")
    class RuleCreationLimitAndConflictTests {

        // DEF-242：createRule 新增了租戶擁有權檢查，需要先查得 listing 才能驗證。
        @BeforeEach
        void mockListingOwnership() {
            Listing listing = Listing.builder().tenantId(TENANT_ID).build();
            listing.setId(ROOM_LISTING_ID);
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        }

        private PricingDto.CreateRuleRequest.CreateRuleRequestBuilder baseRequest() {
            return PricingDto.CreateRuleRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingDto.PricingRuleType.WEEKDAY_WEEKEND)
                    .ruleName("New Weekend Rule")
                    .config(Map.of("weekendMultiplier", 1.3))
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("TC-LO2-M12-001: 已有 50 條 active 規則時建立第 51 條 → E-4001 RULE_LIMIT_EXCEEDED")
        void createRule_ruleLimitExceeded_throwsE4001() {
            List<PricingRule> fiftyRules = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                fiftyRules.add(PricingRule.builder()
                        .id(UUID.randomUUID())
                        .roomListingId(ROOM_LISTING_ID)
                        .ruleType(PricingRule.PricingRuleType.SEASONAL)
                        .isActive(true)
                        .validFrom(LocalDate.of(2020, 1, 1))
                        .validTo(LocalDate.of(2020, 1, 2))
                        .build());
            }
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(fiftyRules);

            PricingDto.CreateRuleRequest request = baseRequest().build();

            assertThatThrownBy(() -> pricingService.createRule(request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-PR-003/TC-LO2-M12-002: 同類型規則時間重疊且未確認覆蓋 → E-4001 拒絕，舊規則不受影響")
        void createRule_overlappingSameTypeWithoutConfirm_throwsE4001() {
            PricingRule existing = PricingRule.builder()
                    .id(UUID.randomUUID())
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .isActive(true)
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build();
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of(existing));

            PricingDto.CreateRuleRequest request = baseRequest().build();

            assertThatThrownBy(() -> pricingService.createRule(request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            assertThat(existing.getIsActive()).isTrue();
            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-PR-001/002: 確認覆蓋後，重疊的舊規則軟刪除，新規則寫入為 active")
        void createRule_overlappingSameTypeWithConfirm_softDeletesOldAndCreatesNew() {
            PricingRule existing = PricingRule.builder()
                    .id(UUID.randomUUID())
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .isActive(true)
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build();
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of(existing));
            when(pricingRuleRepository.save(any(PricingRule.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PricingDto.CreateRuleRequest request = baseRequest().confirmOverride(true).build();

            PricingDto.RuleResponse response = pricingService.createRule(request, false);

            assertThat(existing.getIsActive()).isFalse();
            assertThat(response.getIsActive()).isTrue();
            verify(pricingRuleRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("TC-PR-004 前置：不重疊的同類型規則各自有效期間，不需確認即可共存")
        void createRule_nonOverlappingSameType_doesNotRequireConfirm() {
            PricingRule existing = PricingRule.builder()
                    .id(UUID.randomUUID())
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .isActive(true)
                    .validFrom(LocalDate.of(2025, 1, 1))
                    .validTo(LocalDate.of(2025, 12, 31))
                    .build();
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of(existing));
            when(pricingRuleRepository.save(any(PricingRule.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PricingDto.CreateRuleRequest request = baseRequest().build();

            PricingDto.RuleResponse response = pricingService.createRule(request, false);

            assertThat(existing.getIsActive()).isTrue();
            assertThat(response.getIsActive()).isTrue();
            verify(pricingRuleRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("DEF-257: createRule 成功後寫入稽核紀錄")
        void createRule_success_recordsAuditLog() {
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            pricingService.createRule(baseRequest().build(), false);

            verify(auditService).record(eq("PRICING_RULE_CREATED"), eq("PRICING_RULE"), any(),
                    eq(TENANT_ID), isNull(), eq("WEEKDAY_WEEKEND"), isNull());
        }
    }

    @Nested
    @DisplayName("DEF-417／DEF-423: 規則建立的檢核範圍，以及引用的房源與商品都要是自己的")
    class RuleCreationScopeTests {

        private final UUID productId = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");
        private final UUID otherStoreProductId = UUID.fromString("770e8400-e29b-41d4-a716-446655440004");
        private final UUID otherTenantId = UUID.randomUUID();

        private Listing listingOf(final UUID listingId, final UUID tenantId) {
            Listing listing = Listing.builder().tenantId(tenantId).build();
            listing.setId(listingId);
            return listing;
        }

        private PricingDto.CreateRuleRequest requestFor(final UUID roomListingId, final UUID listingId) {
            return PricingDto.CreateRuleRequest.builder()
                    .roomListingId(roomListingId)
                    .listingId(listingId)
                    .ruleType(PricingDto.PricingRuleType.SEASONAL)
                    .ruleName("New Seasonal Rule")
                    .config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31))
                    .build();
        }

        private PricingRule activeSeasonal(final UUID roomListingId, final UUID listingId,
                final LocalDate validFrom, final LocalDate validTo) {
            return PricingRule.builder()
                    .id(UUID.randomUUID())
                    .roomListingId(roomListingId)
                    .listingId(listingId)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL)
                    .isActive(true)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .build();
        }

        @Test
        @DisplayName("商品規則（只帶 listingId）：只和這個商品的啟用中規則比較，不再查「沒有房源」的規則池")
        void createRule_productRule_comparesOnlyWithThatProductsRules() {
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listingOf(productId, TENANT_ID)));
            PricingRule overlapping = activeSeasonal(null, productId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId)).thenReturn(List.of(overlapping));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(null, productId), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            // 原缺陷：roomListingId 是 null，衍生查詢變成 room_listing_id IS NULL，範圍成了全平台
            verify(pricingRuleRepository, never()).findByRoomListingIdAndIsActiveTrue(any());
            verify(pricingRuleRepository, never()).findActiveUnboundRulesByTenantId(any());
            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("商品規則：50 條上限以這個商品的規則數計算")
        void createRule_productRule_limitIsCountedForThatProduct() {
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listingOf(productId, TENANT_ID)));
            List<PricingRule> fifty = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                fifty.add(activeSeasonal(null, productId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)));
            }
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId)).thenReturn(fifty);

            assertThatThrownBy(() -> pricingService.createRule(requestFor(null, productId), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("商品規則：確認覆蓋只軟刪除這個商品的重疊規則，不重疊的保留")
        void createRule_productRule_overrideOnlyDeactivatesThatProductsOverlappingRules() {
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listingOf(productId, TENANT_ID)));
            PricingRule overlapping = activeSeasonal(null, productId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            PricingRule separate = activeSeasonal(null, productId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId))
                    .thenReturn(List.of(overlapping, separate));
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.CreateRuleRequest request = PricingDto.CreateRuleRequest.builder()
                    .listingId(productId)
                    .ruleType(PricingDto.PricingRuleType.SEASONAL)
                    .ruleName("New Seasonal Rule")
                    .config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31))
                    .confirmOverride(true)
                    .build();

            pricingService.createRule(request, false);

            assertThat(overlapping.getIsActive()).isFalse();
            assertThat(separate.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("不綁房源也不綁商品的規則：只和呼叫者自己租戶的同類規則比較")
        void createRule_unboundRule_comparesOnlyWithCallerTenantsUnboundRules() {
            PricingRule overlapping = activeSeasonal(null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            when(pricingRuleRepository.findActiveUnboundRulesByTenantId(TENANT_ID)).thenReturn(List.of(overlapping));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(null, null), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            verify(pricingRuleRepository).findActiveUnboundRulesByTenantId(TENANT_ID);
            verify(pricingRuleRepository, never()).findByRoomListingIdAndIsActiveTrue(any());
            verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("房源規則（帶 roomListingId）：維持以這間房源為範圍")
        void createRule_roomRule_comparesOnlyWithThatRoomsRules() {
            when(listingRepository.findById(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(listingOf(ROOM_LISTING_ID, TENANT_ID)));
            PricingRule overlapping = activeSeasonal(ROOM_LISTING_ID, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of(overlapping));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(ROOM_LISTING_ID, null), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4001);

            verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
            verify(pricingRuleRepository, never()).findActiveUnboundRulesByTenantId(any());
        }

        @Test
        @DisplayName("DEF-423: 帶自己的房源 id、夾帶別家店的商品 id → E_1007，不寫入（原本只驗證 roomListingId）")
        void createRule_ownRoomWithOtherStoresProduct_throwsE1007() {
            when(listingRepository.findById(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(listingOf(ROOM_LISTING_ID, TENANT_ID)));
            when(listingRepository.findById(otherStoreProductId))
                    .thenReturn(Optional.of(listingOf(otherStoreProductId, otherTenantId)));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(ROOM_LISTING_ID, otherStoreProductId), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("DEF-423: 帶別家店的房源 id、夾帶自己的商品 id → E_1007，不寫入")
        void createRule_otherStoresRoomWithOwnProduct_throwsE1007() {
            when(listingRepository.findById(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(listingOf(ROOM_LISTING_ID, otherTenantId)));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(ROOM_LISTING_ID, productId), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("DEF-242 對照：只帶別家店的商品 id → E_1007，不寫入")
        void createRule_productOfAnotherStore_throwsE1007() {
            when(listingRepository.findById(otherStoreProductId))
                    .thenReturn(Optional.of(listingOf(otherStoreProductId, otherTenantId)));

            assertThatThrownBy(() -> pricingService.createRule(requestFor(null, otherStoreProductId), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("對照：同時帶自己的房源 id 與自己的商品 id → 可建立（檢查的是擁有權，不是禁止兩個 id 並存）")
        void createRule_ownRoomAndOwnProduct_isAccepted() {
            when(listingRepository.findById(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(listingOf(ROOM_LISTING_ID, TENANT_ID)));
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listingOf(productId, TENANT_ID)));
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(List.of());
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.RuleResponse response = pricingService.createRule(requestFor(ROOM_LISTING_ID, productId), false);

            assertThat(response.getRoomListingId()).isEqualTo(ROOM_LISTING_ID);
            assertThat(response.getListingId()).isEqualTo(productId);
        }
    }

    @Nested
    @DisplayName("UT-M12-024 ~ UT-M12-025: 定價規則更新的日期範圍驗證（DEF-225）")
    class RuleUpdateValidationTests {

        @Test
        @DisplayName("DEF-225: 更新 validTo 早於既有 validFrom → E-4003，不寫入")
        void updateRule_validToBeforeExistingValidFrom_throwsE4003() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId)
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .isActive(true)
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31))
                    .build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            PricingDto.UpdateRuleRequest request = PricingDto.UpdateRuleRequest.builder()
                    .validTo(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(() -> pricingService.updateRule(ruleId, request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4003);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("DEF-225: 更新 validFrom 晚於既有 validTo → E-4003，不寫入")
        void updateRule_validFromAfterExistingValidTo_throwsE4003() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId)
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                    .isActive(true)
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31))
                    .build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            PricingDto.UpdateRuleRequest request = PricingDto.UpdateRuleRequest.builder()
                    .validFrom(LocalDate.of(2026, 12, 1))
                    .build();

            assertThatThrownBy(() -> pricingService.updateRule(ruleId, request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_4003);

            verify(pricingRuleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("DEF-242: 定價規則租戶擁有權檢查")
    class RuleTenantOwnershipTests {

        private final UUID otherTenantId = UUID.randomUUID();

        private Listing listingOf(final UUID tenantId) {
            Listing listing = Listing.builder().tenantId(tenantId).build();
            listing.setId(ROOM_LISTING_ID);
            return listing;
        }

        @Test
        @DisplayName("createRule：賣家對他租戶的 roomListingId 植入定價規則 → E_1007，不寫入")
        void createRule_listingBelongsToDifferentTenant_throwsE1007() {
            // 修復前：完全不驗證 listing 歸屬，本斷言會失敗——他租戶的商品可被植入
            // 例如 MANUAL_OVERRIDE price=0.01 的規則，買家結帳時會被當作權威售價套用。
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listingOf(otherTenantId)));

            PricingDto.CreateRuleRequest request = PricingDto.CreateRuleRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingDto.PricingRuleType.MANUAL_OVERRIDE)
                    .ruleName("Malicious override")
                    .config(Map.of("price", 0.01))
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build();

            assertThatThrownBy(() -> pricingService.createRule(request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("createRule：SUPER_ADMIN 可跨租戶建立規則（擁有權檢查略過，不查詢 listing）")
        void createRule_superAdminBypassesOwnership() {
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of());
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.CreateRuleRequest request = PricingDto.CreateRuleRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingDto.PricingRuleType.SEASONAL)
                    .ruleName("Admin rule")
                    .config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build();

            PricingDto.RuleResponse response = pricingService.createRule(request, true);

            assertThat(response.getRuleName()).isEqualTo("Admin rule");
        }

        @Test
        @DisplayName("updateRule：賣家更新他租戶的規則 → E_1007，不寫入")
        void updateRule_ruleBelongsToDifferentTenant_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId).tenantId(otherTenantId).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                    .isActive(true).validFrom(LocalDate.of(2026, 1, 1)).validTo(LocalDate.of(2026, 12, 31))
                    .build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            PricingDto.UpdateRuleRequest request = PricingDto.UpdateRuleRequest.builder()
                    .config(Map.of("price", 0.01)).build();

            assertThatThrownBy(() -> pricingService.updateRule(ruleId, request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("deleteRule：賣家刪除他租戶的規則 → E_1007，不寫入")
        void deleteRule_ruleBelongsToDifferentTenant_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId).tenantId(otherTenantId).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).isActive(true).build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> pricingService.deleteRule(ruleId, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("setCalendarPrice：賣家對他租戶的房源手動覆蓋日曆價格 → E_1007，不寫入")
        void setCalendarPrice_roomBelongsToDifferentTenant_throwsE1007() {
            Listing listing = listingOf(otherTenantId);
            Room room = Room.builder().listing(listing).build();
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));

            PricingDto.SetCalendarPriceRequest request = PricingDto.SetCalendarPriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .date(LocalDate.of(2026, 6, 1))
                    .price(BigDecimal.valueOf(0.01))
                    .build();

            assertThatThrownBy(() -> pricingService.setCalendarPrice(request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("overridePrice：賣家覆蓋他租戶的規則價格 → E_1007，不寫入")
        void overridePrice_ruleBelongsToDifferentTenant_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId).tenantId(otherTenantId).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).isActive(true).build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> pricingService.overridePrice(ruleId, LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 5), BigDecimal.valueOf(0.01), "malicious", false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        // DEF-255（Sprint 185）：getRules 先前完全沒有租戶擁有權檢查——本檔其餘 5 個寫入方法皆已在
        // DEF-242 補上檢查，唯獨這個讀取/列表方法當時未被同一輪修復觸及，任一持有 room:read/
        // product:read 權限者皆可取得任意租戶完整的定價規則明細。

        @Test
        @DisplayName("🔴 DEF-255：getRules 帶入他租戶的 roomListingId → E_1007，不得列出他租戶定價規則")
        void getRules_roomListingBelongsToDifferentTenant_throwsE1007() {
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listingOf(otherTenantId)));

            assertThatThrownBy(() -> pricingService.getRules(ROOM_LISTING_ID, null, true, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).findByRoomListingIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("🔴 DEF-255：getRules 帶入他租戶的 listingId → E_1007，不得列出他租戶定價規則")
        void getRules_listingBelongsToDifferentTenant_throwsE1007() {
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listingOf(otherTenantId)));

            assertThatThrownBy(() -> pricingService.getRules(null, ROOM_LISTING_ID, true, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("getRules：本租戶的 roomListingId → 放行（續走既有查詢邏輯）")
        void getRules_roomListingBelongsToSameTenant_returnsRules() {
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listingOf(TENANT_ID)));
            PricingRule rule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).isActive(true)
                    .validFrom(LocalDate.of(2026, 1, 1)).validTo(LocalDate.of(2026, 12, 31)).build();
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(List.of(rule));

            List<PricingDto.RuleResponse> response = pricingService.getRules(ROOM_LISTING_ID, null, true, false);

            assertThat(response).hasSize(1);
        }

        @Test
        @DisplayName("getRules：SUPER_ADMIN 可跨租戶查詢（擁有權檢查略過，不查詢 listing）")
        void getRules_superAdminBypassesOwnership() {
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(List.of());

            List<PricingDto.RuleResponse> response = pricingService.getRules(ROOM_LISTING_ID, null, true, true);

            assertThat(response).isEmpty();
            verify(listingRepository, never()).findById(any());
        }
    }

    /**
     * DEF-266：動態定價的計算結果（單晚/總額/有效售價）過去從未捨入至幣別精度，ROOM 側還經 double
     * 運算（{@code 1 - 7.0 / 100 = 0.9299999999999999}）。只靠 Postgres NUMERIC(_, 2) 在寫入時隱性
     * 四捨五入，導致：精確平手值（{@code .xx5}）被浮點雜訊往下捨、API 回應/稽核日誌帶長尾小數、
     * 逐晚明細與總額對不上。本專案其餘金額計算（PromoService／SettlementCalculator）一律
     * {@code setScale(2, HALF_UP)}，定價需與之一致。
     */
    @Nested
    @DisplayName("DEF-266: 動態定價結果須捨入至 2 位小數（HALF_UP），且不含 double 浮點雜訊")
    class MoneyPrecisionTests {

        private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 2);
        private static final LocalDate SATURDAY = LocalDate.of(2027, 3, 6);

        private Room roomWithBasePrice(final String basePrice) {
            Listing listing = Listing.builder()
                    .basePrice(new BigDecimal(basePrice))
                    .currency("TWD")
                    .tenantId(TENANT_ID)
                    .build();
            listing.setId(ROOM_LISTING_ID);
            return Room.builder().listing(listing).build();
        }

        private PricingRule roomRule(final PricingRule.PricingRuleType type, final Map<String, Object> config) {
            return PricingRule.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(type)
                    .ruleName(type.name() + " money-precision")
                    .priority(10)
                    .config(config)
                    .validFrom(LocalDate.of(2027, 1, 1))
                    .validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true)
                    .build();
        }

        private PricingDto.CalculatePriceResponse calculate(final Room room, final PricingRule rule,
                final LocalDate checkIn, final int nights) {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(rule));
            return pricingService.calculatePrice(PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn)
                    .checkOutDate(checkIn.plusDays(nights))
                    .bookingDate(checkIn.minusDays(30))
                    .build());
        }

        @Test
        @DisplayName("ROOM 折扣：精確平手值 1234.50 × 0.93 = 1148.085 須 HALF_UP 為 1148.09，不可被 double 雜訊往下捨成 1148.08")
        void roomDiscount_exactHalfCentTie_roundsHalfUp() {
            // 7% 折扣的倍率在 double 下為 0.9299999999999999（非 0.93），乘出 1148.0849999…，
            // 交給 DB 隱性四捨五入就會變成 1148.08——少收一分錢。
            PricingRule rule = roomRule(PricingRule.PricingRuleType.EARLY_BIRD,
                    Map.of("discountPercent", 7.0, "minDaysAhead", 7));

            PricingDto.CalculatePriceResponse response = calculate(roomWithBasePrice("1234.50"), rule, TUESDAY, 1);

            assertThat(response.getBreakdown().get(0).getAdjustedPrice()).isEqualByComparingTo("1148.09");
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("1148.09");
        }

        @Test
        @DisplayName("ROOM 折扣：非平手值也不可帶長尾小數（1234.00 × 0.93 須為 1147.62，而非 1147.6199999…）")
        void roomDiscount_result_hasNoFloatingNoise() {
            PricingRule rule = roomRule(PricingRule.PricingRuleType.EARLY_BIRD,
                    Map.of("discountPercent", 7.0, "minDaysAhead", 7));

            PricingDto.CalculatePriceResponse response = calculate(roomWithBasePrice("1234.00"), rule, TUESDAY, 1);

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("1147.62");
            assertThat(response.getAdjustedTotal().scale()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("ROOM 長住折扣：逐晚價格與總額皆為 2 位小數，且逐晚加總等於總額（3 晚 × 914.29 = 2742.87）")
        void roomLongStay_nightlyAndTotalAreCents_andSumsMatch() {
            // 20% × 3/7 = 8.5714…% → 每晚 1000 × 0.9142857… = 914.2857…；逐晚各自捨入為 914.29。
            PricingRule rule = roomRule(PricingRule.PricingRuleType.LONG_STAY,
                    Map.of("discountPercent", 20.0, "minNights", 3));

            PricingDto.CalculatePriceResponse response = calculate(roomWithBasePrice("1000"), rule, TUESDAY, 3);

            BigDecimal nightlySum = BigDecimal.ZERO;
            for (PricingDto.PriceBreakdown night : response.getBreakdown()) {
                assertThat(night.getAdjustedPrice()).isEqualByComparingTo("914.29");
                assertThat(night.getAdjustedPrice().scale()).isLessThanOrEqualTo(2);
                nightlySum = nightlySum.add(night.getAdjustedPrice());
            }
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("2742.87");
            assertThat(response.getAdjustedTotal()).isEqualByComparingTo(nightlySum);
            assertThat(response.getDiscount()).isEqualByComparingTo("257.13");
        }

        @Test
        @DisplayName("ROOM 週末加成：百分比顯示值不含浮點雜訊（倍率 1.07 → adjustmentValue 恰為 7，而非 7.000000000000006）")
        void roomWeekendMultiplier_adjustmentValue_hasNoFloatingNoise() {
            PricingRule rule = roomRule(PricingRule.PricingRuleType.WEEKDAY_WEEKEND,
                    Map.of("weekendMultiplier", 1.07));

            PricingDto.CalculatePriceResponse response = calculate(roomWithBasePrice("1000"), rule, SATURDAY, 1);

            PricingDto.PriceBreakdown night = response.getBreakdown().get(0);
            assertThat(night.getAdjustedPrice()).isEqualByComparingTo("1070.00");
            assertThat(night.getAdjustmentValue()).isEqualByComparingTo("7");
            assertThat(night.getAdjustmentValue().stripTrailingZeros().scale())
                    .as("7% 的顯示值不可帶 double 運算殘留的長尾位數")
                    .isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("ROOM 手動覆蓋價：超過 2 位小數的覆蓋價須捨入為 1234.57")
        void roomManualOverride_subCentPrice_isRounded() {
            PricingRule rule = roomRule(PricingRule.PricingRuleType.MANUAL_OVERRIDE,
                    Map.of("price", 1234.567));

            PricingDto.CalculatePriceResponse response = calculate(roomWithBasePrice("1000"), rule, TUESDAY, 1);

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("1234.57");
            assertThat(response.getAdjustedTotal().scale()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("PRODUCT 折扣：99.99 × 0.925 = 92.49075 須捨入為 92.49（購物車單價/小計不可帶 5 位小數）")
        void productDiscount_effectivePrice_isRoundedToCents() {
            Listing listing = Listing.builder()
                    .listingType(Listing.ListingType.PRODUCT)
                    .basePrice(new BigDecimal("99.99")).currency("TWD").tenantId(TENANT_ID).build();
            UUID productId = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");
            listing.setId(productId);
            PricingRule rule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(productId)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).ruleName("product discount").priority(10)
                    .config(Map.of("discountPercent", 7.5))
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listing));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId)).thenReturn(List.of(rule));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(productId, TUESDAY, 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo("92.49");
            assertThat(resp.getEffectivePrice().scale()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("PRODUCT 漲價：99.99 × 1.15 = 114.9885 須捨入為 114.99")
        void productMarkup_effectivePrice_isRoundedToCents() {
            Listing listing = Listing.builder()
                    .listingType(Listing.ListingType.PRODUCT)
                    .basePrice(new BigDecimal("99.99")).currency("TWD").tenantId(TENANT_ID).build();
            UUID productId = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");
            listing.setId(productId);
            PricingRule rule = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(productId)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).ruleName("product markup").priority(10)
                    .config(Map.of("multiplier", 1.15))
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listing));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId)).thenReturn(List.of(rule));

            PricingDto.EffectivePriceResponse resp = pricingService.getEffectivePrice(productId, TUESDAY, 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo("114.99");
            assertThat(resp.getEffectivePrice().scale()).isLessThanOrEqualTo(2);
        }
    }

    /**
     * DEF-268（使用者 2026-09-24 拍板）：定價規則 {@code config} 數值先前完全無範圍驗證——
     * {@code discountPercent=150} 算出 -500.00、{@code multiplier=-2} 算出 -2000.00、{@code "NaN"} 拋
     * NumberFormatException。拍板規則：建立/更新時拒絕（E-8001）＋計算時略過既有的壞規則；
     * 不允許免費（折扣須 0~100% 不含兩端、覆蓋價須 &gt; 0）；加價倍率須 &gt; 0 且 ≤ 10。
     */
    @Nested
    @DisplayName("DEF-268: 定價規則 config 範圍驗證（建立/更新拒絕）")
    class RuleConfigValidationTests {

        // lenient：updateRule 測試不經 listing 擁有權查詢；save 預設回傳入參，使「缺少驗證」的紅燈
        // 乾淨地表現為「沒有拋例外」，而非 mock 回傳 null 造成的 NPE。
        @BeforeEach
        void mockListingOwnershipAndSave() {
            Listing listing = Listing.builder().tenantId(TENANT_ID).build();
            listing.setId(ROOM_LISTING_ID);
            lenient().when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        private PricingDto.CreateRuleRequest requestWithConfig(final Map<String, Object> config) {
            return PricingDto.CreateRuleRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingDto.PricingRuleType.EARLY_BIRD)
                    .ruleName("config range")
                    .config(config)
                    .validFrom(LocalDate.of(2026, 6, 1))
                    .validTo(LocalDate.of(2026, 8, 31))
                    .build();
        }

        @ParameterizedTest(name = "建立規則 {0}={1} → E-8001，不寫入")
        @CsvSource({
            "discountPercent,150", "discountPercent,100", "discountPercent,0", "discountPercent,-20",
            "multiplier,-2", "multiplier,0", "multiplier,10.01",
            "weekendMultiplier,130", "weekendMultiplier,0",
            "price,-100", "price,0",
            "minNights,0", "minDaysAhead,0"
        })
        void createRule_outOfRangeValue_throwsE8001(final String key, final double value) {
            assertThatThrownBy(() -> pricingService.createRule(requestWithConfig(Map.of(key, value)), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_8001);

            verify(pricingRuleRepository, never()).save(any());
        }

        @ParameterizedTest(name = "建立規則 discountPercent=\"{0}\"（非有限數字）→ E-8001，不寫入")
        @CsvSource({"NaN", "Infinity", "-Infinity", "'10%'", "abc"})
        void createRule_nonNumericValue_throwsE8001(final String value) {
            assertThatThrownBy(() -> pricingService.createRule(
                    requestWithConfig(Map.of("discountPercent", value)), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_8001);

            verify(pricingRuleRepository, never()).save(any());
        }

        @ParameterizedTest(name = "建立規則 邊界值 {0}={1} → 允許（不可誤擋合法設定）")
        @CsvSource({
            "discountPercent,99.99", "discountPercent,0.01", "discountPercent,15",
            "multiplier,10", "multiplier,0.5", "weekendMultiplier,1.3",
            "price,0.01", "minNights,1", "minDaysAhead,1"
        })
        void createRule_boundaryValue_isAccepted(final String key, final double value) {
            PricingDto.RuleResponse response = pricingService.createRule(requestWithConfig(Map.of(key, value)), false);

            assertThat(response).isNotNull();
            verify(pricingRuleRepository).save(any(PricingRule.class));
        }

        @Test
        @DisplayName("建立規則 config 含未知鍵（如 startDate/originalRuleId/weekdayMultiplier）→ 不驗證，允許")
        void createRule_unknownKeys_areIgnored() {
            PricingDto.RuleResponse response = pricingService.createRule(requestWithConfig(
                    Map.of("discountPercent", 10.0, "weekdayMultiplier", 1.0, "startDate", "2026-06-01")), false);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("更新規則 config 超出範圍（discountPercent=150）→ E-8001，不寫入")
        void updateRule_outOfRangeConfig_throwsE8001() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.EARLY_BIRD).isActive(true)
                    .config(Map.of("discountPercent", 10.0))
                    .validFrom(LocalDate.of(2026, 6, 1)).validTo(LocalDate.of(2026, 8, 31)).build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            PricingDto.UpdateRuleRequest request = PricingDto.UpdateRuleRequest.builder()
                    .config(Map.of("discountPercent", 150.0)).build();

            assertThatThrownBy(() -> pricingService.updateRule(ruleId, request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_8001);

            verify(pricingRuleRepository, never()).save(any());
            assertThat(existing.getConfig()).as("拒絕時不可已改動既有規則的 config")
                    .containsEntry("discountPercent", 10.0);
        }

        @Test
        @DisplayName("更新規則 config 合法（discountPercent=20）→ 允許並寫入")
        void updateRule_validConfig_isAccepted() {
            UUID ruleId = UUID.randomUUID();
            PricingRule existing = PricingRule.builder()
                    .id(ruleId).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.EARLY_BIRD).isActive(true).priority(1)
                    .config(Map.of("discountPercent", 10.0))
                    .validFrom(LocalDate.of(2026, 6, 1)).validTo(LocalDate.of(2026, 8, 31)).build();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

            pricingService.updateRule(ruleId, PricingDto.UpdateRuleRequest.builder()
                    .config(Map.of("discountPercent", 20.0)).build(), false);

            assertThat(existing.getConfig()).containsEntry("discountPercent", 20.0);
        }
    }

    @Nested
    @DisplayName("DEF-268: 計算時略過資料庫裡既存的壞規則（不可算出負價/免費/例外）")
    class BadStoredRuleSkippedTests {

        private PricingRule storedRule(final PricingRule.PricingRuleType type, final int priority,
                final Map<String, Object> config) {
            return PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(type).ruleName(type.name() + " p" + priority).priority(priority)
                    .config(config)
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
        }

        private PricingDto.CalculatePriceResponse calculateOneNight(final PricingRule... rules) {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockRoom()));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(rules));
            return pricingService.calculatePrice(PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(LocalDate.of(2027, 3, 6)).checkOutDate(LocalDate.of(2027, 3, 7))
                    .bookingDate(LocalDate.of(2027, 2, 1)).build());
        }

        @ParameterizedTest(name = "ROOM 既存壞規則 {0}={1} → 略過，照原價 1000（不可為負價/免費）")
        @CsvSource({
            "discountPercent,150", "discountPercent,100", "multiplier,-2", "weekendMultiplier,130", "price,-100", "price,0"
        })
        void room_badStoredRule_isSkipped_chargesBasePrice(final String key, final double value) {
            PricingRule.PricingRuleType type = switch (key) {
                case "multiplier" -> PricingRule.PricingRuleType.SEASONAL;
                case "weekendMultiplier" -> PricingRule.PricingRuleType.WEEKDAY_WEEKEND;
                case "price" -> PricingRule.PricingRuleType.MANUAL_OVERRIDE;
                default -> PricingRule.PricingRuleType.EARLY_BIRD;
            };

            PricingDto.CalculatePriceResponse response = calculateOneNight(storedRule(type, 10, Map.of(key, value)));

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("1000");
            assertThat(response.getBreakdown().get(0).getAppliedRuleName()).isNull();
        }

        @Test
        @DisplayName("ROOM 既存 discountPercent=\"NaN\" 字串 → 略過，不拋 NumberFormatException，照原價")
        void room_nanStoredRule_doesNotThrow() {
            PricingDto.CalculatePriceResponse response = calculateOneNight(
                    storedRule(PricingRule.PricingRuleType.EARLY_BIRD, 10, Map.of("discountPercent", "NaN")));

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("ROOM 最高優先級規則是壞的 → 略過它，改套用次一優先級的合法規則（10% → 900）")
        void room_badTopPriorityRule_fallsThroughToNextValidRule() {
            PricingDto.CalculatePriceResponse response = calculateOneNight(
                    storedRule(PricingRule.PricingRuleType.EARLY_BIRD, 20, Map.of("discountPercent", 150.0)),
                    storedRule(PricingRule.PricingRuleType.LAST_MINUTE, 5, Map.of("discountPercent", 10.0)));

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("900");
            assertThat(response.getBreakdown().get(0).getAppliedRuleName()).isEqualTo("LAST_MINUTE p5");
        }

        @Test
        @DisplayName("PRODUCT 既存 discountPercent=150 → 略過，有效售價維持原價 1000（不可為負價）")
        void product_badStoredRule_isSkipped() {
            UUID productId = UUID.fromString("770e8400-e29b-41d4-a716-446655440003");
            Listing listing = Listing.builder().listingType(Listing.ListingType.PRODUCT)
                    .basePrice(BASE_PRICE).currency("TWD").tenantId(TENANT_ID).build();
            listing.setId(productId);
            PricingRule bad = PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).listingId(productId)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL).ruleName("bad").priority(10)
                    .config(Map.of("discountPercent", 150.0))
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
            when(listingRepository.findById(productId)).thenReturn(Optional.of(listing));
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(productId)).thenReturn(List.of(bad));

            PricingDto.EffectivePriceResponse resp =
                    pricingService.getEffectivePrice(productId, LocalDate.of(2027, 3, 2), 1);

            assertThat(resp.getEffectivePrice()).isEqualByComparingTo("1000");
            assertThat(resp.getAppliedRuleName()).isNull();
        }
    }

    /**
     * DEF-269：未帶 {@code bookingDate} 時以「今天」計早鳥／末班車資格。過去用 JVM 預設時區
     * （正式環境 UTC）的 {@code LocalDate.now()}，台灣時間每天 00:00～08:00 會少算一天：
     * 早鳥被誤發（提前天數多算 1 天，發出未達門檻的折扣）、末班車漏發。改用營運時區（UTC+8）。
     */
    @Nested
    @DisplayName("DEF-269: 預設下單日以營運時區（UTC+8）計，不受 JVM 時區影響")
    class BusinessDateTests {

        /** UTC 2027-01-31 16:30 = 台灣 2027-02-01 00:30（UTC 日期比營運日期早一天）。 */
        private static final String TAIPEI_2027_02_01_0030 = "2027-01-31T16:30:00Z";

        private PricingRule rule(final PricingRule.PricingRuleType type, final Map<String, Object> config) {
            return PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(type).ruleName(type.name()).priority(10).config(config)
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31))
                    .isActive(true).build();
        }

        /** 不帶 bookingDate（走預設「今天」），台灣現在為 2027-02-01 00:30。 */
        private PricingDto.CalculatePriceResponse calculateOnDefaultBookingDate(
                final PricingRule rule, final LocalDate checkIn) {
            BusinessTime.useClockForTesting(java.time.Clock.fixed(
                    java.time.Instant.parse(TAIPEI_2027_02_01_0030), ZoneOffset.UTC));
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(buildMockRoom()));
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any())).thenReturn(List.of(rule));
            return pricingService.calculatePrice(PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1)).build());
        }

        @Test
        @DisplayName("末班車 maxDaysAhead=0：台灣 2/1 00:30 訂 2/1 入住 → 當日訂當日住，折扣適用（不可因 UTC 仍是 1/31 而漏發）")
        void lastMinute_sameDayInTaipei_applies() {
            PricingDto.CalculatePriceResponse response = calculateOnDefaultBookingDate(
                    rule(PricingRule.PricingRuleType.LAST_MINUTE, Map.of("discountPercent", 10.0, "maxDaysAhead", 0)),
                    LocalDate.of(2027, 2, 1));

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("900");
        }

        @Test
        @DisplayName("早鳥 minDaysAhead=8：台灣 2/1 訂 2/8 入住只提前 7 天 → 不適用（不可用 UTC 1/31 多算成 8 天而誤發折扣）")
        void earlyBird_sevenDaysInTaipei_doesNotMeetEightDayThreshold() {
            PricingDto.CalculatePriceResponse response = calculateOnDefaultBookingDate(
                    rule(PricingRule.PricingRuleType.EARLY_BIRD, Map.of("discountPercent", 10.0, "minDaysAhead", 8)),
                    LocalDate.of(2027, 2, 8));

            assertThat(response.getAdjustedTotal())
                    .as("提前僅 7 天未達 8 天門檻，不應發放早鳥折扣（否則是未賺得的折扣＝少收錢）")
                    .isEqualByComparingTo("1000");
        }
    }

    // ========== Helper Methods ==========

    /**
     * DEF-411（Sprint 303）：沒有店鋪的呼叫者（租戶是系統租戶佔位值）。
     *
     * <p>守門測試刻意用 {@code lenient()} 加 {@code verify(never())}：只斷言「回空」或「拋例外」會被 Mockito
     * 對 Repository 的預設回傳（空 List）掩蓋，守門被拿掉測試照樣綠。
     */
    @Nested
    @DisplayName("DEF-411: 沒有店鋪的呼叫者（系統租戶佔位值）")
    class StorelessCallerTests {

        private final UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

        @BeforeEach
        void callerIsTenantless() {
            TenantContext.setCurrentTenant(systemTenantId);
        }

        private Listing listingOf(final UUID tenantId) {
            Listing listing = Listing.builder().tenantId(tenantId).build();
            listing.setId(ROOM_LISTING_ID);
            return listing;
        }

        @Test
        @DisplayName("getRules 無參數：沒有店鋪的非管理員 → 空，且完全不查資料庫（含 activeOnly）")
        void getRules_noArgs_storelessNonAdmin_returnsEmptyWithoutQuerying() {
            lenient().when(pricingRuleRepository.findByTenantId(systemTenantId))
                    .thenReturn(List.of(buildLongStayRule()));
            lenient().when(pricingRuleRepository.findByTenantIdAndIsActiveTrue(systemTenantId))
                    .thenReturn(List.of(buildLongStayRule()));

            assertThat(pricingService.getRules(null, null, false, false)).isEmpty();
            assertThat(pricingService.getRules(null, null, true, false)).isEmpty();

            verify(pricingRuleRepository, never()).findByTenantId(any());
            verify(pricingRuleRepository, never()).findByTenantIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("getRules 無參數：平台管理員（SUPER_ADMIN）的租戶也是系統租戶，仍列得出系統租戶的規則")
        void getRules_noArgs_superAdmin_listsSystemTenantRules() {
            when(pricingRuleRepository.findByTenantId(systemTenantId)).thenReturn(List.of(buildLongStayRule()));

            assertThat(pricingService.getRules(null, null, false, true)).hasSize(1);
        }

        @Test
        @DisplayName("getRules 無參數：有真正店鋪的呼叫者照常列出自己店鋪的規則")
        void getRules_noArgs_storeOwner_listsOwnTenantRules() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(pricingRuleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(buildLongStayRule()));

            assertThat(pricingService.getRules(null, null, false, false)).hasSize(1);
        }

        @Test
        @DisplayName("getRules 帶房源 id（roomListingId／listingId）：沒有店鋪的呼叫者 → E_1007，先判斷、不查房源也不查規則")
        void getRules_withListingId_storeless_throwsE1007BeforeAnyQuery() {
            // 房源的租戶也是系統租戶——修復前租戶相等檢查會放行；lenient 讓「守門被拿掉」時紅燈來自斷言而不是未使用的 stub
            lenient().when(listingRepository.findById(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(listingOf(systemTenantId)));

            assertThatThrownBy(() -> pricingService.getRules(ROOM_LISTING_ID, null, true, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);
            assertThatThrownBy(() -> pricingService.getRules(null, ROOM_LISTING_ID, true, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(listingRepository, never()).findById(any());
            verify(pricingRuleRepository, never()).findByRoomListingIdAndIsActiveTrue(any());
            verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("getRules 帶房源 id：平台管理員（SUPER_ADMIN，租戶同為系統租戶）仍可查系統租戶房源的規則")
        void getRules_withListingId_superAdmin_stillAllowed() {
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of(buildLongStayRule()));

            assertThat(pricingService.getRules(ROOM_LISTING_ID, null, true, true)).hasSize(1);
        }

        @Test
        @DisplayName("DEF-414: createRule 存檔的規則帶 Tenant 關聯物件（INSERT 依據它，不是影子欄位 tenantId）")
        void createRule_savesRuleWithTenantRelation() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID)).thenReturn(List.of());
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            pricingService.createRule(PricingDto.CreateRuleRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingDto.PricingRuleType.SEASONAL)
                    .ruleName("Store rule")
                    .config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build(), true);

            ArgumentCaptor<PricingRule> saved = ArgumentCaptor.forClass(PricingRule.class);
            verify(pricingRuleRepository).save(saved.capture());
            assertThat(saved.getValue().getTenant())
                    .as("tenant 關聯物件才會被寫進 pricing_rules.tenant_id")
                    .isNotNull()
                    .extracting(Tenant::getId).isEqualTo(TENANT_ID);
        }
    }

    /**
     * DEF-415（Sprint 304）：沒有店鋪的呼叫者（含沒有店鋪的 ADMIN，租戶內角色）不得管理系統租戶（平台自營）的定價規則。
     *
     * <p>呼叫者的租戶與資源的租戶都是系統租戶佔位值——舊的擁有權檢查只比「租戶相等」，會放行。這裡的資源一律備成系統租戶名下，
     * 並以 {@code lenient()} 備妥 Repository：守門被拿掉時，舊的相等檢查放行、操作繼續執行，測試才會紅（而不是被 Mockito
     * 的預設回傳掩蓋成別的例外）。每個案例都同時斷言「不寫入」。
     */
    @Nested
    @DisplayName("DEF-415: 沒有店鋪的呼叫者管不到系統租戶（平台自營）的定價規則")
    class StorelessCallerWriteTests {

        private final UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

        @BeforeEach
        void callerIsTenantless() {
            TenantContext.setCurrentTenant(systemTenantId);
            lenient().when(tenantRepository.findById(systemTenantId)).thenReturn(Optional.of(tenantOf(systemTenantId)));
        }

        private PricingRule systemRule(final UUID ruleId) {
            return PricingRule.builder()
                    .id(ruleId).tenantId(systemTenantId).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).isActive(true)
                    .priority(5).ruleName("平台自營規則")
                    .config(Map.of("price", 100.0))
                    .validFrom(LocalDate.of(2026, 1, 1)).validTo(LocalDate.of(2026, 12, 31)).build();
        }

        private PricingDto.CreateRuleRequest createRequest(final UUID roomListingId) {
            return PricingDto.CreateRuleRequest.builder()
                    .roomListingId(roomListingId)
                    .ruleType(PricingDto.PricingRuleType.SEASONAL)
                    .ruleName("Platform rule")
                    .config(Map.of("multiplier", 1.2))
                    .validFrom(LocalDate.of(2026, 1, 1))
                    .validTo(LocalDate.of(2026, 12, 31))
                    .build();
        }

        @Test
        @DisplayName("createRule 綁系統租戶房源：沒有店鋪的呼叫者 → E_1007，先判斷、不查房源也不寫入")
        void createRule_storelessCaller_systemTenantListing_throwsE1007BeforeAnyQuery() {
            Listing systemListing = Listing.builder().tenantId(systemTenantId).build();
            systemListing.setId(ROOM_LISTING_ID);
            lenient().when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(systemListing));
            lenient().when(pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(ROOM_LISTING_ID))
                    .thenReturn(List.of());
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pricingService.createRule(createRequest(ROOM_LISTING_ID), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(listingRepository, never()).findById(any());
            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("createRule 不綁任何房源：沒有店鋪的呼叫者 → E_1007（不綁房源的規則會蓋成系統租戶，守門不得因沒有房源 id 而略過）")
        void createRule_storelessCaller_noListing_throwsE1007() {
            lenient().when(pricingRuleRepository.findActiveUnboundRulesByTenantId(systemTenantId)).thenReturn(List.of());
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pricingService.createRule(createRequest(null), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("createRule 不綁任何房源：平台管理員（SUPER_ADMIN）仍可建立，且蓋成系統租戶")
        void createRule_superAdmin_noListing_stillAllowedAndStampedWithSystemTenant() {
            // DEF-417：不綁房源也不綁商品的規則只和呼叫者租戶（此處是系統租戶）自己的同類規則比較
            when(pricingRuleRepository.findActiveUnboundRulesByTenantId(systemTenantId)).thenReturn(List.of());
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            pricingService.createRule(createRequest(null), true);

            ArgumentCaptor<PricingRule> saved = ArgumentCaptor.forClass(PricingRule.class);
            verify(pricingRuleRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(systemTenantId);
        }

        @Test
        @DisplayName("updateRule：沒有店鋪的呼叫者更新系統租戶的規則 → E_1007，不寫入")
        void updateRule_storelessCaller_systemTenantRule_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            lenient().when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(systemRule(ruleId)));
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pricingService.updateRule(ruleId,
                    PricingDto.UpdateRuleRequest.builder().priority(99).build(), false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateRule：平台管理員（SUPER_ADMIN）仍可更新系統租戶的規則")
        void updateRule_superAdmin_systemTenantRule_stillAllowed() {
            UUID ruleId = UUID.randomUUID();
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(systemRule(ruleId)));
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.RuleResponse response = pricingService.updateRule(ruleId,
                    PricingDto.UpdateRuleRequest.builder().priority(99).build(), true);

            assertThat(response.getPriority()).isEqualTo(99);
        }

        @Test
        @DisplayName("deleteRule：沒有店鋪的呼叫者停用系統租戶的規則 → E_1007，不寫入")
        void deleteRule_storelessCaller_systemTenantRule_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            PricingRule rule = systemRule(ruleId);
            lenient().when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pricingService.deleteRule(ruleId, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
            assertThat(rule.getIsActive()).as("被擋之後規則仍是啟用中").isTrue();
        }

        @Test
        @DisplayName("overridePrice：沒有店鋪的呼叫者覆蓋系統租戶規則的價格 → E_1007，不寫入")
        void overridePrice_storelessCaller_systemTenantRule_throwsE1007() {
            UUID ruleId = UUID.randomUUID();
            lenient().when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(systemRule(ruleId)));
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pricingService.overridePrice(ruleId, LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 5), BigDecimal.valueOf(0.01), "malicious", false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("setCalendarPrice：沒有店鋪的呼叫者改系統租戶房源的單日價格 → E_1007，不寫入")
        void setCalendarPrice_storelessCaller_systemTenantRoom_throwsE1007() {
            Listing listing = Listing.builder().tenantId(systemTenantId).build();
            listing.setId(ROOM_LISTING_ID);
            lenient().when(roomRepository.findByListingId(ROOM_LISTING_ID))
                    .thenReturn(Optional.of(Room.builder().listing(listing).build()));
            lenient().when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.SetCalendarPriceRequest request = PricingDto.SetCalendarPriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID)
                    .date(LocalDate.of(2026, 6, 1))
                    .price(BigDecimal.valueOf(0.01))
                    .build();

            assertThatThrownBy(() -> pricingService.setCalendarPrice(request, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.E_1007);

            verify(pricingRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("有真正店鋪的呼叫者管理自己店鋪的規則照常（守門看的是有沒有店鋪，不是一律擋）")
        void updateRule_storeOwner_ownTenantRule_stillAllowed() {
            TenantContext.setCurrentTenant(TENANT_ID);
            UUID ruleId = UUID.randomUUID();
            PricingRule own = systemRule(ruleId);
            own.setTenantId(TENANT_ID);
            when(pricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(own));
            when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

            PricingDto.RuleResponse response = pricingService.updateRule(ruleId,
                    PricingDto.UpdateRuleRequest.builder().priority(42).build(), false);

            assertThat(response.getPriority()).isEqualTo(42);
        }
    }

    /**
     * DEF-416（Sprint 303）：帶 roomListingId 且 activeOnly=false 時原本走 {@code findAll()} 全表掃描。
     *
     * <p>Hibernate 產生的測試庫對 rule_type 有 CHECK 約束，放不進列舉以外的值，所以真實 PostgreSQL 的整合測試重現不出
     * 「某一列壞資料讓整個請求 500」；Flyway 建出的資料庫沒有這個約束（V5 種子資料原本就有 {@code WEEKEND_SURCHARGE}，
     * Sprint 305 的 V94 已把它刪除）。因此這裡用 {@code verify(never()).findAll()} 守住「不得全表掃描」這個意圖。
     *
     * <p>Sprint 306（DEF-421）：另一道護欄是 {@code PricingRulesCorruptRowFlywayIntegrationTest}——在 Flyway 實跑的資料庫裡
     * 自己種一列列舉外的 rule_type，走真實 HTTP 驗證房源、商品、店鋪三種列表查詢都不被拖垮（全棧 E2E 在新建的 Flyway 資料庫上
     * 已沒有壞資料列可觸發）。兩者互補：本類別便宜、快；那一支用真實資料庫證明「壞資料列真的會咬人」。
     */
    @Nested
    @DisplayName("DEF-416: 規則列表帶房源 id 時不得全表掃描")
    class RuleListingQueryTests {

        @Test
        @DisplayName("getRules 帶 roomListingId 且 activeOnly=false：用 findByRoomListingId，絕不呼叫 findAll()")
        void getRules_roomListingAllRules_usesDerivedQueryNeverFindAll() {
            Listing listing = Listing.builder().tenantId(TENANT_ID).build();
            listing.setId(ROOM_LISTING_ID);
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
            lenient().when(pricingRuleRepository.findAll()).thenReturn(List.of());
            when(pricingRuleRepository.findByRoomListingId(ROOM_LISTING_ID)).thenReturn(List.of(buildLongStayRule()));

            assertThat(pricingService.getRules(ROOM_LISTING_ID, null, false, false)).hasSize(1);

            verify(pricingRuleRepository, never()).findAll();
        }
    }

    /**
     * DEF-424（Sprint 309，使用者以互動選項裁決）：規則只在「房源／商品所屬店鋪」的動態定價開關開啟時套用。
     *
     * <p>原本購物車與訂房計價查的是<b>呼叫者租戶</b>的開關（購物車使用者都是系統租戶，開關在種子是開啟），所以店鋪的開關只管建立規則；
     * 管理員關閉某店鋪的開關後，它既有的規則仍套用在消費者身上。現在由定價引擎的兩個讀取方法（{@code calculatePrice}、
     * {@code getEffectivePrice}）統一把關，購物車、訂房、價格試算端點自動一致。
     *
     * <p>店鋪 id 與呼叫者租戶（{@code TENANT_ID}）刻意不同，才分得出程式碼查了哪一個租戶的開關；
     * 外層 {@code setUpTenantContext} 預設每個租戶都開啟，這裡以指定租戶的 stub 覆蓋。
     */
    @Nested
    @DisplayName("DEF-424: 規則只在房源／商品所屬店鋪的動態定價開關開啟時套用")
    class StoreToggleGatesApplicationTests {

        private static final String TOGGLE = "DYNAMIC_PRICING_ENABLED";
        private static final UUID STORE_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440009");
        private static final UUID PRODUCT_ID = UUID.fromString("990e8400-e29b-41d4-a716-446655440004");

        private final LocalDate checkIn = LocalDate.of(2026, 4, 6);
        private final LocalDate checkOut = LocalDate.of(2026, 4, 8); // 2 晚
        private final LocalDate productDate = LocalDate.of(2027, 8, 3);

        private Room storeRoom() {
            Listing listing = Listing.builder().basePrice(BASE_PRICE).currency("TWD").tenantId(STORE_ID).build();
            listing.setId(ROOM_LISTING_ID);
            return Room.builder().listing(listing).build();
        }

        private PricingRule roomOverride() {
            return PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(STORE_ID).roomListingId(ROOM_LISTING_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                    .ruleName("Manual Override 1500").priority(10)
                    .config(Map.of("price", 1500.0))
                    .validFrom(checkIn.minusDays(30)).validTo(checkIn.plusDays(30)).isActive(true).build();
        }

        private PricingDto.CalculatePriceRequest roomRequest() {
            return PricingDto.CalculatePriceRequest.builder()
                    .roomListingId(ROOM_LISTING_ID).checkInDate(checkIn).checkOutDate(checkOut).build();
        }

        private Listing storeProduct() {
            Listing listing = Listing.builder().listingType(Listing.ListingType.PRODUCT)
                    .basePrice(BASE_PRICE).currency("TWD").tenantId(STORE_ID).build();
            listing.setId(PRODUCT_ID);
            return listing;
        }

        private PricingRule productOverride() {
            return PricingRule.builder()
                    .id(UUID.randomUUID()).tenantId(STORE_ID).listingId(PRODUCT_ID)
                    .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).ruleName("Product Override 800").priority(10)
                    .config(Map.of("price", 800.0))
                    .validFrom(LocalDate.of(2027, 1, 1)).validTo(LocalDate.of(2027, 12, 31)).isActive(true).build();
        }

        @Test
        @DisplayName("房源：店鋪開關關閉 → 規則一律不套用，回基準價，連規則都不查")
        void room_storeToggleOff_rulesDoNotApply() {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(storeRoom()));
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(false);

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(roomRequest());

            assertThat(response.getBaseTotal()).isEqualByComparingTo("2000");
            assertThat(response.getAdjustedTotal()).as("開關關閉，即使有生效中的規則也不調整").isEqualByComparingTo("2000");
            assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getBreakdown()).hasSize(2).allSatisfy(day -> {
                assertThat(day.getAppliedRuleName()).isNull();
                assertThat(day.getAdjustedPrice()).isEqualByComparingTo(BASE_PRICE);
            });
            verify(pricingRuleRepository, never()).findActiveRulesForDateRange(any(), any(), any());
        }

        @Test
        @DisplayName("房源（對照）：店鋪開關開啟 → 同一條規則照常套用")
        void room_storeToggleOn_rulesApply() {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(storeRoom()));
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(true);
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(roomOverride()));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(roomRequest());

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("3000"); // 1500 × 2 晚
            assertThat(response.getBreakdown()).allSatisfy(day ->
                    assertThat(day.getAppliedRuleName()).isEqualTo("Manual Override 1500"));
        }

        @Test
        @DisplayName("房源：看的是房源所屬店鋪的開關，不是呼叫者租戶的——呼叫者開啟、店鋪關閉 → 不套用")
        void room_callerToggleOnButStoreToggleOff_rulesDoNotApply() {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(storeRoom()));
            // 誘餌：萬一關卡錯誤放行，這條生效中的規則會讓總額變成 3000——「總額仍是 2000」才真的證明規則沒被套用
            // （沒有這條，放行時 repository 回空清單，結果一樣是基準價，斷言就空洞了；Sprint 309 突變 Q3 發現）
            lenient().when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(roomOverride()));
            // 誘餌：呼叫者租戶的開關是開啟的，正確的實作永遠不會讀它（所以用 lenient，下面另以 verify 證明沒讀過）
            lenient().when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, TOGGLE)).thenReturn(true);
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(false);

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(roomRequest());

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("2000");
            verify(featureToggleService, never()).isFeatureEnabled(anyString());
            verify(featureToggleService, never()).isFeatureEnabledForTenant(eq(TENANT_ID), anyString());
        }

        @Test
        @DisplayName("房源：呼叫者租戶的開關關閉、店鋪開啟 → 照常套用（不受呼叫者租戶影響）")
        void room_callerToggleOffButStoreToggleOn_rulesApply() {
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(storeRoom()));
            // 誘餌：呼叫者租戶的開關是關閉的，正確的實作永遠不會讀它（所以用 lenient，下面另以 verify 證明沒讀過）
            lenient().when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, TOGGLE)).thenReturn(false);
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(true);
            when(pricingRuleRepository.findActiveRulesForDateRange(any(), any(), any()))
                    .thenReturn(List.of(roomOverride()));

            PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(roomRequest());

            assertThat(response.getAdjustedTotal()).isEqualByComparingTo("3000");
            verify(featureToggleService, never()).isFeatureEnabled(anyString());
            verify(featureToggleService, never()).isFeatureEnabledForTenant(eq(TENANT_ID), anyString());
        }

        @Test
        @DisplayName("商品：店鋪開關關閉 → 有效售價是基準價，連規則都不查")
        void product_storeToggleOff_rulesDoNotApply() {
            when(listingRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(storeProduct()));
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(false);

            PricingDto.EffectivePriceResponse response = pricingService.getEffectivePrice(PRODUCT_ID, productDate, 1);

            assertThat(response.getBasePrice()).isEqualByComparingTo(BASE_PRICE);
            assertThat(response.getEffectivePrice()).as("開關關閉，規則不套用").isEqualByComparingTo(BASE_PRICE);
            assertThat(response.getAppliedRuleId()).isNull();
            assertThat(response.getAppliedRuleName()).isNull();
            verify(pricingRuleRepository, never()).findByListingIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("商品（對照）：店鋪開關開啟 → 規則照常套用")
        void product_storeToggleOn_rulesApply() {
            when(listingRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(storeProduct()));
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(true);
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_ID))
                    .thenReturn(List.of(productOverride()));

            PricingDto.EffectivePriceResponse response = pricingService.getEffectivePrice(PRODUCT_ID, productDate, 1);

            assertThat(response.getEffectivePrice()).isEqualByComparingTo("800");
            assertThat(response.getAppliedRuleName()).isEqualTo("Product Override 800");
        }

        @Test
        @DisplayName("商品：看的是商品所屬店鋪的開關，不是呼叫者租戶的——呼叫者開啟、店鋪關閉 → 不套用")
        void product_callerToggleOnButStoreToggleOff_rulesDoNotApply() {
            when(listingRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(storeProduct()));
            // 誘餌：萬一關卡錯誤放行，這條生效中的規則會讓有效售價變成 800——「仍是基準價」才真的證明規則沒被套用
            // （理由同房間案例；Sprint 309 突變 Q3 發現）
            lenient().when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_ID))
                    .thenReturn(List.of(productOverride()));
            // 誘餌：呼叫者租戶的開關是開啟的，正確的實作永遠不會讀它（所以用 lenient，下面另以 verify 證明沒讀過）
            lenient().when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, TOGGLE)).thenReturn(true);
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(false);

            PricingDto.EffectivePriceResponse response = pricingService.getEffectivePrice(PRODUCT_ID, productDate, 1);

            assertThat(response.getEffectivePrice()).isEqualByComparingTo(BASE_PRICE);
            verify(featureToggleService, never()).isFeatureEnabled(anyString());
            verify(featureToggleService, never()).isFeatureEnabledForTenant(eq(TENANT_ID), anyString());
        }

        @Test
        @DisplayName("商品：呼叫者租戶的開關關閉、店鋪開啟 → 照常套用（不受呼叫者租戶影響）")
        void product_callerToggleOffButStoreToggleOn_rulesApply() {
            when(listingRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(storeProduct()));
            // 誘餌：呼叫者租戶的開關是關閉的，正確的實作永遠不會讀它（所以用 lenient，下面另以 verify 證明沒讀過）
            lenient().when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, TOGGLE)).thenReturn(false);
            when(featureToggleService.isFeatureEnabledForTenant(STORE_ID, TOGGLE)).thenReturn(true);
            when(pricingRuleRepository.findByListingIdAndIsActiveTrue(PRODUCT_ID))
                    .thenReturn(List.of(productOverride()));

            PricingDto.EffectivePriceResponse response = pricingService.getEffectivePrice(PRODUCT_ID, productDate, 1);

            assertThat(response.getEffectivePrice()).isEqualByComparingTo("800");
            verify(featureToggleService, never()).isFeatureEnabled(anyString());
            verify(featureToggleService, never()).isFeatureEnabledForTenant(eq(TENANT_ID), anyString());
        }
    }

    private Room buildMockRoom() {
        Listing listing = Listing.builder()
                .basePrice(BASE_PRICE)
                .currency("TWD")
                .tenantId(TENANT_ID)
                .build();
        listing.setId(ROOM_LISTING_ID);

        Room room = Room.builder()
                .listing(listing)
                .build();
        return room;
    }

    private PricingRule buildWeekendRule() {
        return buildWeekendRuleWithPriority(5);
    }

    private PricingRule buildWeekendRuleWithPriority(int priority) {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("Weekend 20% up")
                .priority(priority)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now().minusYears(1))
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildSeasonalRule() {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.SEASONAL)
                .ruleName("Chinese New Year 50% up")
                .priority(5)
                .config(Map.of("multiplier", 1.5))
                .validFrom(LocalDate.of(2026, 1, 25))
                .validTo(LocalDate.of(2026, 2, 5))
                .isActive(true)
                .build();
    }

    @SuppressWarnings("unused")
    private PricingRule buildEarlyBirdRule() {
        return buildEarlyBirdRuleWithPriority(5);
    }

    private PricingRule buildEarlyBirdRuleWithPriority(int priority) {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.EARLY_BIRD)
                .ruleName("Early Bird 15% off")
                .priority(priority)
                .config(Map.of("discountPercent", 15.0, "minDaysAhead", 7))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildEarlyBirdRuleWithValidFrom(LocalDate validFrom) {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.EARLY_BIRD)
                .ruleName("Early Bird 15% off")
                .priority(5)
                .config(Map.of("discountPercent", 15.0, "minDaysAhead", 7))
                .validFrom(validFrom)
                .validTo(validFrom.plusYears(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildEarlyBirdRuleWithValidFrom(LocalDate validFrom, int priority) {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.EARLY_BIRD)
                .ruleName("Early Bird 15% off")
                .priority(priority)
                .config(Map.of("discountPercent", 15.0, "minDaysAhead", 7))
                .validFrom(validFrom)
                .validTo(validFrom.plusYears(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildLongStayRule() {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.LONG_STAY)
                .ruleName("Long Stay 20% off")
                .priority(5)
                .config(Map.of("discountPercent", 20.0, "minNights", 3))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }

    private PricingRule buildLastMinuteRule() {
        return PricingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .roomListingId(ROOM_LISTING_ID)
                .ruleType(PricingRule.PricingRuleType.LAST_MINUTE)
                .ruleName("Last Minute 25% off")
                .priority(5)
                .config(Map.of("discountPercent", 25.0, "maxDaysAhead", 3))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build();
    }
}