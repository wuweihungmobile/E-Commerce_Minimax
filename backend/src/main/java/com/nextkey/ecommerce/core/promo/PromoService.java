package com.nextkey.ecommerce.core.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.api.dto.CartDto.PromoValidationResult;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 優惠券服務
 * 負責優惠券驗證、折扣計算等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoService {

    private final PromoCodeRepository promoCodeRepository;

    /**
     * 驗證優惠券是否有效
     */
    public PromoValidationResult validatePromoCode(String promoCodeStr, UUID tenantId) {
        if (promoCodeStr == null || promoCodeStr.isBlank()) {
            return PromoValidationResult.invalid("INVALID", "優惠券代碼不得為空");
        }

        PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseAndTenantId(
                promoCodeStr.trim().toUpperCase(), tenantId
        ).orElse(null);

        if (promo == null) {
            log.warn("Promo code not found: {} for tenant: {}", promoCodeStr, tenantId);
            return PromoValidationResult.invalid("INVALID", "優惠券不存在或已失效");
        }

        if (!promo.getIsActive()) {
            log.warn("Promo code is not active: {}", promoCodeStr);
            return PromoValidationResult.invalid("INACTIVE", "優惠券已停用");
        }

        if (promo.isNotYetActive()) {
            log.warn("Promo code is not yet active: {}", promoCodeStr);
            return PromoValidationResult.invalid("NOT_YET_ACTIVE", "優惠券尚未開始");
        }

        if (promo.isExpired()) {
            log.warn("Promo code is expired: {}", promoCodeStr);
            return PromoValidationResult.invalid("EXPIRED", "優惠券已過期");
        }

        if (promo.isUsageLimitReached()) {
            log.warn("Promo code usage limit reached: {}", promoCodeStr);
            return PromoValidationResult.invalid("USAGE_LIMIT", "優惠券已兌換完畢");
        }

        return PromoValidationResult.valid(
                promo.getCode(),
                promo.getDiscountType().name(),
                promo.getDiscountValue(),
                promo.getMaxDiscountAmount()
        );
    }

    /**
     * 計算折扣金額
     */
    public BigDecimal computeDiscount(PromoCode promo, BigDecimal totalAmount) {
        if (promo == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 檢查最低消費門檻
        if (promo.getMinPurchaseAmount() != null &&
                totalAmount.compareTo(promo.getMinPurchaseAmount()) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = switch (promo.getDiscountType()) {
            case PERCENTAGE -> totalAmount
                    .multiply(promo.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promo.getDiscountValue().min(totalAmount);
            case FREE_SHIPPING -> BigDecimal.ZERO; // 免運費由物流模組處理
        };

        // 套用最高折扣上限
        if (promo.getMaxDiscountAmount() != null &&
                discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
            discount = promo.getMaxDiscountAmount();
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 增加優惠券使用次數
     */
    public void incrementUsageCount(PromoCode promo) {
        promo.setCurrentUsageCount(promo.getCurrentUsageCount() + 1);
        promoCodeRepository.save(promo);
        log.info("Incremented usage count for promo: {}, new count: {}",
                promo.getCode(), promo.getCurrentUsageCount());
    }
}