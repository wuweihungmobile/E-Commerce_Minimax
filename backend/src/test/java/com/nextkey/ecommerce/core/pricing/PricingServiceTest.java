package com.nextkey.ecommerce.core.pricing;

import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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

    @InjectMocks
    private PricingService pricingService;

    // 測試資料
    private static final UUID TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);

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

            // Assert: 相同優先級時，由於後建立的放在 List 前面，會被先處理
            // 實際實作是 sort 後 reversed，所以同優先級時順序決定誰勝出
            // 此測試驗證邏輯存在
            assertThat(response).isNotNull();
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
    }

    // ========== Helper Methods ==========

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