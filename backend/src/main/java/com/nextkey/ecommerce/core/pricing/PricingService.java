package com.nextkey.ecommerce.core.pricing;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 動態定價服務
 * 支援：平假日、旺季、早鳥、長住優惠、手動覆蓋、最後一刻
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingRuleRepository pricingRuleRepository;
    private final RoomRepository roomRepository;
    private final ListingRepository listingRepository;
    private final RoomCalendarRepository roomCalendarRepository;
    private final FeatureToggleService featureToggleService;

    private static final double TAX_RATE = 1.2;

    /**
     * 建立定價規則
     */
    @Transactional
    public PricingDto.RuleResponse createRule(PricingDto.CreateRuleRequest request) {
        featureToggleService.checkFeatureEnabled("DYNAMIC_PRICING_ENABLED");

        UUID tenantId = TenantContext.getCurrentTenant();

        validateRuleRequest(request);

        PricingRule rule = PricingRule.builder()
                .tenantId(tenantId)
                .roomListingId(request.getRoomListingId())
                .listingId(request.getListingId())
                .ruleType(PricingRule.PricingRuleType.valueOf(request.getRuleType().name()))
                .ruleName(request.getRuleName())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .config(request.getConfig())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        rule = pricingRuleRepository.save(rule);
        log.info("Created pricing rule: ruleId={}, type={}", rule.getId(), rule.getRuleType());

        return toRuleResponse(rule);
    }

    /**
     * 更新定價規則
     */
    @Transactional
    public PricingDto.RuleResponse updateRule(UUID ruleId, PricingDto.UpdateRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Pricing rule not found"));

        if (request.getRuleName() != null) {
            rule.setRuleName(request.getRuleName());
        }
        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }
        if (request.getConfig() != null) {
            rule.setConfig(request.getConfig());
        }
        if (request.getValidFrom() != null) {
            rule.setValidFrom(request.getValidFrom());
        }
        if (request.getValidTo() != null) {
            rule.setValidTo(request.getValidTo());
        }
        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        rule = pricingRuleRepository.save(rule);
        log.info("Updated pricing rule: ruleId={}", ruleId);

        return toRuleResponse(rule);
    }

    /**
     * 取得定價規則列表
     */
    @Transactional(readOnly = true)
    public List<PricingDto.RuleResponse> getRules(UUID roomListingId, UUID listingId, Boolean activeOnly) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<PricingRule> rules;

        if (roomListingId != null) {
            rules = activeOnly != null && activeOnly
                    ? pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(roomListingId)
                    : pricingRuleRepository.findAll().stream()
                        .filter(r -> Objects.equals(r.getRoomListingId(), roomListingId))
                        .collect(Collectors.toList());
        } else if (listingId != null) {
            rules = activeOnly != null && activeOnly
                    ? pricingRuleRepository.findByListingIdAndIsActiveTrue(listingId)
                    : pricingRuleRepository.findByListingId(listingId);
        } else {
            rules = activeOnly != null && activeOnly
                    ? pricingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId)
                    : pricingRuleRepository.findByTenantId(tenantId);
        }

        return rules.stream()
                .map(this::toRuleResponse)
                .collect(Collectors.toList());
    }

    /**
     * 刪除定價規則
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        PricingRule rule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Pricing rule not found"));

        rule.setIsActive(false);
        pricingRuleRepository.save(rule);
        log.info("Deactivated pricing rule: ruleId={}", ruleId);
    }

    /**
     * 取得計價用的 listing 並優雅降級（AI-2406b）：room 記錄缺失時 fallback 以 listing 取基準價
     * （有 listing 即可計價，不因無 room 記錄即 404）；listing 或 basePrice 缺失才視為無法計價，
     * 回明確 4xx（E_4000）而非 NPE→500。呼叫端（BookingService.tryDynamicPricing / dailyDiscountMap）
     * 已 catch BusinessException 降級。
     */
    private Listing resolveListingForPricing(final UUID roomListingId) {
        Room room = roomRepository.findByListingId(roomListingId).orElse(null);
        Listing listing = room != null ? room.getListing()
                : listingRepository.findById(roomListingId).orElse(null);
        if (listing == null || listing.getBasePrice() == null) {
            throw new BusinessException(ErrorCode.E_4000, "Room base price not available");
        }
        return listing;
    }

    /**
     * 計算價格（帶動態調整）
     */
    @Transactional(readOnly = true)
    public PricingDto.CalculatePriceResponse calculatePrice(PricingDto.CalculatePriceRequest request) {
        // 優雅降級（AI-2406b）：見 resolveListingForPricing（room 記錄缺失時 fallback 以 listing 取基準價）。
        Listing listing = resolveListingForPricing(request.getRoomListingId());
        BigDecimal basePrice = listing.getBasePrice();
        String currency = listing.getCurrency();

        LocalDate checkIn = request.getCheckInDate();
        LocalDate checkOut = request.getCheckOutDate();
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        if (nights < 1) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }

        // 下單日期：早鳥/末班車以「下單日 vs 入住日」的提前/臨近天數判斷（AI-2401）。
        // 未提供時以今日計。
        LocalDate bookingDate = request.getBookingDate() != null ? request.getBookingDate() : LocalDate.now();

        // 取得所有適用的規則
        List<PricingRule> rules = new ArrayList<>(pricingRuleRepository.findActiveRulesForDateRange(
                request.getRoomListingId(), checkIn, checkOut.minusDays(1)));

        // 按優先級排序；同優先級時後建立者優先（AI-2407 tie-break 業務語意決策）
        rules.sort(Comparator.comparingInt(PricingRule::getPriority).reversed()
                .thenComparing(PricingRule::getCreatedAt, Comparator.reverseOrder()));

        // 計算每天價格
        List<PricingDto.PriceBreakdown> breakdown = new ArrayList<>();
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalAdjusted = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        LocalDate current = checkIn;
        while (current.isBefore(checkOut)) {
            BigDecimal dayBasePrice = basePrice;
            BigDecimal dayAdjustedPrice = basePrice;
            String appliedRule = null;
            String adjustmentType = null;
            BigDecimal adjustmentValue = BigDecimal.ZERO;

            // 找出適用的規則
            for (PricingRule rule : rules) {
                if (isRuleApplicable(rule, current, checkIn, nights, bookingDate)) {
                    AdjustmentResult adjustment = calculateAdjustment(rule, dayBasePrice, current, nights);
                    if (adjustment.applied) {
                        dayAdjustedPrice = adjustment.adjustedPrice;
                        appliedRule = rule.getRuleName();
                        adjustmentType = adjustment.type;
                        adjustmentValue = adjustment.value;
                        break; // 只應用最高優先級的規則
                    }
                }
            }

            PricingDto.PriceBreakdown dayBreakdown = PricingDto.PriceBreakdown.builder()
                    .date(current)
                    .basePrice(dayBasePrice)
                    .adjustedPrice(dayAdjustedPrice)
                    .appliedRuleName(appliedRule)
                    .adjustmentType(adjustmentType)
                    .adjustmentValue(adjustmentValue)
                    .build();

            breakdown.add(dayBreakdown);
            totalBase = totalBase.add(dayBasePrice);
            totalAdjusted = totalAdjusted.add(dayAdjustedPrice);
            totalDiscount = totalDiscount.add(dayBasePrice.subtract(dayAdjustedPrice));

            current = current.plusDays(1);
        }

        return PricingDto.CalculatePriceResponse.builder()
                .roomListingId(request.getRoomListingId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .nights(nights)
                .baseTotal(totalBase)
                .adjustedTotal(totalAdjusted)
                .discount(totalDiscount.compareTo(BigDecimal.ZERO) > 0 ? totalDiscount : BigDecimal.ZERO)
                .currency(currency)
                .breakdown(breakdown)
                .build();
    }

    /**
     * 設定日曆價格（手動覆蓋）
     */
    @Transactional
    public PricingDto.CalendarPriceResponse setCalendarPrice(PricingDto.SetCalendarPriceRequest request) {
        Room room = roomRepository.findByListingId(request.getRoomListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000));

        // 創建手動覆蓋規則
        PricingRule overrideRule = PricingRule.builder()
                .tenantId(room.getListing().getTenantId())
                .roomListingId(request.getRoomListingId())
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                .ruleName(request.getReason() != null ? request.getReason() : "Manual price override")
                .priority(100) // 最高優先級
                .config(Map.of("price", request.getPrice().doubleValue()))
                .validFrom(request.getDate())
                .validTo(request.getDate())
                .isActive(true)
                .build();

        overrideRule = pricingRuleRepository.save(overrideRule);

        log.info("Set calendar price: listingId={}, date={}, price={}",
                request.getRoomListingId(), request.getDate(), request.getPrice());

        return PricingDto.CalendarPriceResponse.builder()
                .roomListingId(request.getRoomListingId())
                .date(request.getDate())
                .price(request.getPrice())
                .priceType("MANUAL")
                .appliedRuleName(overrideRule.getRuleName())
                .updatedAt(overrideRule.getUpdatedAt())
                .build();
    }

    /**
     * 手動覆蓋價格（T-M12-03）
     * 對指定日期範圍內的價格進行覆蓋
     * 確保 BOOKED 日期不可覆蓋
     */
    @Transactional
    public PricingDto.RuleOverrideResponse overridePrice(UUID ruleId, LocalDate startDate, LocalDate endDate,
                                                          BigDecimal overridePrice, String reason) {
        PricingRule existingRule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Pricing rule not found"));

        UUID roomListingId = existingRule.getRoomListingId();
        UUID tenantId = existingRule.getTenantId();

        // 檢查日期範圍內是否有 BOOKED 的日期
        List<RoomCalendar> calendars = roomCalendarRepository.findByRoomListingIdAndCalendarDateBetween(roomListingId, startDate, endDate);
        for (RoomCalendar calendar : calendars) {
            if (calendar.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED) {
                throw new BusinessException(ErrorCode.E_4002,
                        "Cannot override price for date " + calendar.getCalendarDate() + " - already booked");
            }
        }

        // 創建覆蓋規則
        PricingRule overrideRule = PricingRule.builder()
                .tenantId(tenantId)
                .roomListingId(roomListingId)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                .ruleName(reason != null ? reason : "Price override for " + startDate + " to " + endDate)
                .priority(100) // 最高優先級
                .config(Map.of(
                        "price", overridePrice.doubleValue(),
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString(),
                        "originalRuleId", ruleId.toString()
                ))
                .validFrom(startDate)
                .validTo(endDate)
                .isActive(true)
                .build();

        overrideRule = pricingRuleRepository.save(overrideRule);

        log.info("Override price applied: ruleId={}, roomListingId={}, startDate={}, endDate={}, price={}",
                overrideRule.getId(), roomListingId, startDate, endDate, overridePrice);

        return PricingDto.RuleOverrideResponse.builder()
                .ruleId(overrideRule.getId())
                .roomListingId(roomListingId)
                .startDate(startDate)
                .endDate(endDate)
                .overridePrice(overridePrice)
                .reason(reason)
                .status("APPLIED")
                .createdAt(overrideRule.getCreatedAt())
                .build();
    }

    // ========== Helper Methods ==========

    private void validateRuleRequest(PricingDto.CreateRuleRequest request) {
        if (request.getValidTo().isBefore(request.getValidFrom())) {
            throw new BusinessException(ErrorCode.E_4003, "Valid to date must be after valid from date");
        }
    }

    private boolean isRuleApplicable(PricingRule rule, LocalDate date, LocalDate checkIn, int nights, LocalDate bookingDate) {
        if (!rule.getValidFrom().isAfter(date) && !rule.getValidTo().isBefore(date)) {
            // 根據規則類型進行額外檢查
            switch (rule.getRuleType()) {
                case EARLY_BIRD:
                    // 早鳥：以「下單日 vs 入住日」計提前天數（業界語意，AI-2401）。
                    // minDaysAhead=7 表示需在入住日至少 7 天前下單才適用。
                    long daysAhead = ChronoUnit.DAYS.between(bookingDate, checkIn);
                    Integer minDays = getIntConfig(rule, "minDaysAhead");
                    return minDays == null || daysAhead >= minDays;
                case LONG_STAY:
                    // 入住天數檢查
                    Integer minNights = getIntConfig(rule, "minNights");
                    return minNights == null || nights >= minNights;
                case LAST_MINUTE:
                    // 末班車：以「下單日 vs 入住日」計臨近天數（AI-2401）。
                    // maxDaysAhead=3 表示入住日前 3 天內（含）下單才適用（不含已過期的入住日）。
                    long daysUntilCheckIn = ChronoUnit.DAYS.between(bookingDate, checkIn);
                    Integer maxDaysAhead = getIntConfig(rule, "maxDaysAhead");
                    return maxDaysAhead == null || (daysUntilCheckIn >= 0 && daysUntilCheckIn <= maxDaysAhead);
                default:
                    return true;
            }
        }
        return false;
    }

    /**
     * 從 jsonb config 安全讀取整數（jsonb 反序列化數字型別可能為 Integer/Long/Double，
     * 直接 cast 易 ClassCastException）。無值或無法解析回 null（AI-2401，AC-001-5）。
     */
    private static Integer getIntConfig(PricingRule rule, String key) {
        Object v = configValue(rule, key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 從 jsonb config 安全讀取浮點數（同上，避免 (Double) cast 於 Integer 值時拋錯）。
     */
    private static Double getDoubleConfig(PricingRule rule, String key) {
        Object v = configValue(rule, key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object configValue(PricingRule rule, String key) {
        Map<String, Object> config = rule.getConfig();
        return config == null ? null : config.get(key);
    }

    private AdjustmentResult calculateAdjustment(PricingRule rule, BigDecimal basePrice, LocalDate date, int nights) {
        AdjustmentResult result = new AdjustmentResult();

        switch (rule.getRuleType()) {
            case WEEKDAY_WEEKEND:
                DayOfWeek dow = date.getDayOfWeek();
                boolean isWeekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                if (isWeekend) {
                    Double weekendMultiplier = getDoubleConfig(rule, "weekendMultiplier");
                    if (weekendMultiplier == null) {
                        weekendMultiplier = TAX_RATE;
                    }
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(weekendMultiplier));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf((weekendMultiplier - 1) * 100);
                }
                break;

            case SEASONAL:
                Double seasonalMultiplier = getDoubleConfig(rule, "multiplier");
                if (seasonalMultiplier != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(seasonalMultiplier));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf((seasonalMultiplier - 1) * 100);
                }
                break;

            case EARLY_BIRD:
                Double earlyBirdDiscount = getDoubleConfig(rule, "discountPercent");
                if (earlyBirdDiscount != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(1 - earlyBirdDiscount / 100));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf(earlyBirdDiscount);
                }
                break;

            case LONG_STAY:
                // 根據入住天數遞增折扣
                Double longStayDiscount = getDoubleConfig(rule, "discountPercent");
                if (longStayDiscount != null) {
                    // 入住天數越多，折扣越大（線性遞增，最高為設定值）
                    double actualDiscount = Math.min(longStayDiscount, longStayDiscount * nights / 7.0);
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(1 - actualDiscount / 100));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf(actualDiscount);
                }
                break;

            case LAST_MINUTE:
                Double lastMinuteDiscount = getDoubleConfig(rule, "discountPercent");
                if (lastMinuteDiscount != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(1 - lastMinuteDiscount / 100));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf(lastMinuteDiscount);
                }
                break;

            case MANUAL_OVERRIDE:
                Double overridePrice = getDoubleConfig(rule, "price");
                if (overridePrice != null) {
                    result.adjustedPrice = BigDecimal.valueOf(overridePrice);
                    result.applied = true;
                    result.type = "FIXED_AMOUNT";
                    result.value = result.adjustedPrice.subtract(basePrice);
                }
                break;

            default:
                result.applied = false;
        }

        return result;
    }

    private PricingDto.RuleResponse toRuleResponse(PricingRule rule) {
        return PricingDto.RuleResponse.builder()
                .ruleId(rule.getId())
                .tenantId(rule.getTenantId())
                .roomListingId(rule.getRoomListingId())
                .listingId(rule.getListingId())
                .ruleType(rule.getRuleType().name())
                .ruleName(rule.getRuleName())
                .priority(rule.getPriority())
                .config(rule.getConfig())
                .validFrom(rule.getValidFrom())
                .validTo(rule.getValidTo())
                .isActive(rule.getIsActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    /**
     * 查詢 Listing 的有效售價（Consumer 端 — 依 listingId 定價規則）
     */
    @Transactional(readOnly = true)
    public PricingDto.EffectivePriceResponse getEffectivePrice(UUID listingId, LocalDate checkDate, int stayDays) {
        com.nextkey.ecommerce.domain.model.listing.Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Listing not found"));

        BigDecimal basePrice = listing.getBasePrice();

        List<PricingRule> activeRules = pricingRuleRepository.findByListingIdAndIsActiveTrue(listingId)
                .stream()
                .filter(r -> !checkDate.isBefore(r.getValidFrom()) && !checkDate.isAfter(r.getValidTo()))
                .sorted(Comparator.comparingInt(PricingRule::getPriority).reversed()
                        .thenComparing(PricingRule::getCreatedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (activeRules.isEmpty()) {
            return PricingDto.EffectivePriceResponse.builder()
                    .listingId(listingId)
                    .checkDate(checkDate)
                    .stayDays(stayDays)
                    .basePrice(basePrice)
                    .effectivePrice(basePrice)
                    .build();
        }

        PricingRule bestRule = activeRules.get(0);
        BigDecimal effectivePrice = applyProductRule(bestRule, basePrice, checkDate);

        return PricingDto.EffectivePriceResponse.builder()
                .listingId(listingId)
                .checkDate(checkDate)
                .stayDays(stayDays)
                .basePrice(basePrice)
                .effectivePrice(effectivePrice)
                .appliedRuleType(bestRule.getRuleType().name())
                .appliedRuleId(bestRule.getId())
                .appliedRuleName(bestRule.getRuleName())
                .build();
    }

    /**
     * PRODUCT 單日規則套用（Sprint 48 AI-2406c）：支援漲價型規則（對齊 ROOM calculateAdjustment 的 config key）。
     * - MANUAL_OVERRIDE：config `price`（可高於 basePrice = 漲價，或低於 = 折扣）
     * - WEEKDAY_WEEKEND：config `weekendMultiplier`（依 checkDate 星期五/六/日判定，>1 = 漲價）
     * - SEASONAL：config `multiplier`（>1 = 漲價）
     * 以上未命中時，**向後相容**沿用既有折扣路徑：任何規則含 config `discountPercent` → 折扣（S44 PRODUCT 行為）。
     */
    private BigDecimal applyProductRule(PricingRule rule, BigDecimal basePrice, LocalDate checkDate) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return basePrice;
        }
        BigDecimal markup = applyProductMarkup(rule, basePrice, checkDate);
        if (markup != null) {
            return markup;
        }
        // 向後相容：折扣型（S44 PRODUCT 以 discountPercent 表達折扣，不分 ruleType）
        Object discountPct = config.get("discountPercent");
        if (discountPct != null) {
            BigDecimal pct = new BigDecimal(discountPct.toString());
            BigDecimal hundred = BigDecimal.valueOf(100);
            return basePrice.multiply(BigDecimal.ONE.subtract(pct.divide(hundred)));
        }
        return basePrice;
    }

    /** 漲價型規則計算（回 null 表示此規則非漲價型或缺對應 config，交回 applyProductRule 走折扣/原價）。 */
    private BigDecimal applyProductMarkup(PricingRule rule, BigDecimal basePrice, LocalDate checkDate) {
        switch (rule.getRuleType()) {
            case MANUAL_OVERRIDE:
                Double overridePrice = getDoubleConfig(rule, "price");
                return overridePrice != null ? BigDecimal.valueOf(overridePrice) : null;
            case SEASONAL:
                Double seasonalMultiplier = getDoubleConfig(rule, "multiplier");
                return seasonalMultiplier != null
                        ? basePrice.multiply(BigDecimal.valueOf(seasonalMultiplier)) : null;
            case WEEKDAY_WEEKEND:
                DayOfWeek dow = checkDate.getDayOfWeek();
                boolean isWeekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                Double weekendMultiplier = getDoubleConfig(rule, "weekendMultiplier");
                return (isWeekend && weekendMultiplier != null)
                        ? basePrice.multiply(BigDecimal.valueOf(weekendMultiplier)) : null;
            default:
                return null;
        }
    }

    private static class AdjustmentResult {
        boolean applied;
        BigDecimal adjustedPrice;
        String type;
        BigDecimal value;
    }
}
