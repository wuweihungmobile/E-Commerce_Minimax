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
     * 計算折扣金額。
     *
     * <p>Sprint 101（AI-2435）起改為單一 3 參數簽章，刻意不保留原本不需傳運費的 2 參數多載：
     * {@code FREE_SHIPPING} 的折扣基數是運費而非商品小計，若留下舊多載，呼叫端會在毫無徵兆的情況下
     * 拿到 0 折扣——DEF-045 正是這樣產生的（原實作直接回傳 {@code BigDecimal.ZERO} 並註明
     * 「免運費由物流模組處理」，但物流模組從未處理，選此型別的券買家拿不到任何優惠）。
     *
     * @param promo       優惠券；{@code null} 表示未套用
     * @param itemsTotal  商品小計（不含運費），為最低消費門檻的判斷基數，
     *                    亦為 PERCENTAGE / FIXED_AMOUNT 的折扣基數
     * @param shippingFee 該筆訂單運費，FREE_SHIPPING 的折扣基數；購物車預覽與結帳必須傳入
     *                    同一套運費算法（{@code ShippingTemplateService.calculateFeeForTenant}）的結果，
     *                    否則會重演「購物車顯示 ≠ 實收」
     * @return 折扣金額（含 maxDiscountAmount 上限），一律 scale 2
     */
    public BigDecimal computeDiscount(PromoCode promo, BigDecimal itemsTotal, BigDecimal shippingFee) {
        if (promo == null || itemsTotal == null || itemsTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 檢查最低消費門檻（一律以商品小計為準，不含運費）
        if (promo.getMinPurchaseAmount() != null &&
                itemsTotal.compareTo(promo.getMinPurchaseAmount()) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = rawDiscountByType(promo, itemsTotal, shippingFee);

        // 套用最高折扣上限
        if (promo.getMaxDiscountAmount() != null &&
                discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
            discount = promo.getMaxDiscountAmount();
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 依券別計算尚未套用 maxDiscountAmount 上限的原始折扣。
     *
     * <p>抽為獨立方法而非內嵌於 {@link #computeDiscount}：內嵌會讓後者的 NPath 分支複雜度
     * 衝到 288，超過 checkstyle 上限 200。
     */
    private BigDecimal rawDiscountByType(PromoCode promo, BigDecimal itemsTotal, BigDecimal shippingFee) {
        return switch (promo.getDiscountType()) {
            case PERCENTAGE -> itemsTotal
                    .multiply(promo.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promo.getDiscountValue().min(itemsTotal);
            // 全額折抵運費（使用者裁定 2026-09-01）：店家已設滿額免運時運費本為 0，
            // 券自然算出 0 折扣，因此不需要另做「與滿額免運疊加」的排除邏輯
            case FREE_SHIPPING -> shippingFee != null ? shippingFee.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        };
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