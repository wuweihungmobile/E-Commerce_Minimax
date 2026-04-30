package com.nextkey.ecommerce.core.pricing;

import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    public List<PricingDto.RuleResponse> getRules(UUID roomListingId, Boolean activeOnly) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<PricingRule> rules;

        if (roomListingId != null) {
            rules = activeOnly != null && activeOnly
                    ? pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(roomListingId)
                    : pricingRuleRepository.findAll().stream()
                        .filter(r -> Objects.equals(r.getRoomListingId(), roomListingId))
                        .collect(Collectors.toList());
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
     * 計算價格（帶動態調整）
     */
    @Transactional(readOnly = true)
    public PricingDto.CalculatePriceResponse calculatePrice(PricingDto.CalculatePriceRequest request) {
        Room room = roomRepository.findByListingId(request.getRoomListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Room not found"));

        Listing listing = room.getListing();
        BigDecimal basePrice = listing.getBasePrice();
        String currency = listing.getCurrency();

        LocalDate checkIn = request.getCheckInDate();
        LocalDate checkOut = request.getCheckOutDate();
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        if (nights < 1) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }

        // 取得所有適用的規則
        List<PricingRule> rules = new ArrayList<>(pricingRuleRepository.findActiveRulesForDateRange(
                request.getRoomListingId(), checkIn, checkOut.minusDays(1)));

        // 按優先級排序
        rules.sort(Comparator.comparingInt(PricingRule::getPriority).reversed());

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
                if (isRuleApplicable(rule, current, nights)) {
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

    private boolean isRuleApplicable(PricingRule rule, LocalDate date, int nights) {
        if (!rule.getValidFrom().isAfter(date) && !rule.getValidTo().isBefore(date)) {
            // 根據規則類型進行額外檢查
            switch (rule.getRuleType()) {
                case EARLY_BIRD:
                    // 提前預訂天數檢查
                    long daysAhead = ChronoUnit.DAYS.between(LocalDate.now(), rule.getValidFrom());
                    Integer minDays = (Integer) rule.getConfig().get("minDaysAhead");
                    return minDays == null || daysAhead >= minDays;
                case LONG_STAY:
                    // 入住天數檢查
                    Integer minNights = (Integer) rule.getConfig().get("minNights");
                    return minNights == null || nights >= minNights;
                case LAST_MINUTE:
                    // 最後一刻檢查
                    long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), date);
                    Integer maxDaysAhead = (Integer) rule.getConfig().get("maxDaysAhead");
                    return maxDaysAhead == null || daysUntilCheckIn <= maxDaysAhead;
                default:
                    return true;
            }
        }
        return false;
    }

    private AdjustmentResult calculateAdjustment(PricingRule rule, BigDecimal basePrice, LocalDate date, int nights) {
        AdjustmentResult result = new AdjustmentResult();
        Map<String, Object> config = rule.getConfig();

        switch (rule.getRuleType()) {
            case WEEKDAY_WEEKEND:
                DayOfWeek dow = date.getDayOfWeek();
                boolean isWeekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                if (isWeekend) {
                    Double weekendMultiplier = (Double) config.getOrDefault("weekendMultiplier", 1.2);
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(weekendMultiplier));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf((weekendMultiplier - 1) * 100);
                }
                break;

            case SEASONAL:
                Double seasonalMultiplier = (Double) config.get("multiplier");
                if (seasonalMultiplier != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(seasonalMultiplier));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf((seasonalMultiplier - 1) * 100);
                }
                break;

            case EARLY_BIRD:
                Double earlyBirdDiscount = (Double) config.get("discountPercent");
                if (earlyBirdDiscount != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(1 - earlyBirdDiscount / 100));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf(earlyBirdDiscount);
                }
                break;

            case LONG_STAY:
                // 根據入住天數遞增折扣
                Double longStayDiscount = (Double) config.get("discountPercent");
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
                Double lastMinuteDiscount = (Double) config.get("discountPercent");
                if (lastMinuteDiscount != null) {
                    result.adjustedPrice = basePrice.multiply(BigDecimal.valueOf(1 - lastMinuteDiscount / 100));
                    result.applied = true;
                    result.type = "PERCENTAGE";
                    result.value = BigDecimal.valueOf(lastMinuteDiscount);
                }
                break;

            case MANUAL_OVERRIDE:
                Double overridePrice = (Double) config.get("price");
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

    private static class AdjustmentResult {
        boolean applied;
        BigDecimal adjustedPrice;
        String type;
        BigDecimal value;
    }
}
