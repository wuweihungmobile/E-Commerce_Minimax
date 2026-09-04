package com.nextkey.ecommerce.core.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.api.dto.CartDto.PromoValidationResult;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

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
    private final PromoCodeUsageRepository promoCodeUsageRepository;

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
     * 原子佔用一次總量額度（Sprint 102，DEF-046）。
     *
     * <p>取代原本的 {@code incrementUsageCount(PromoCode)}——後者以「讀出物件 → 加 1 → save」
     * 兩段式更新，與呼叫端稍早的 {@code isUsageLimitReached()} 檢查之間存在讀後寫窗口，
     * 併發結帳可雙雙通過檢查而使限量券超發。刻意**不**保留舊簽章：舊方法的回傳型別是
     * {@code void}，呼叫端無從得知額度是否真的取得，留著只會讓下一位呼叫者在毫無徵兆下
     * 重新引入同一個競態（Sprint 101 移除 {@code computeDiscount} 舊多載的同一個理由）。
     *
     * <p>注意：本方法以資料庫敘述直接更新，傳入的 {@code promo} 物件其
     * {@code currentUsageCount} 呼叫後即為過期值，不可再用於顯示或判斷。
     *
     * @param promo 已通過前置驗證的優惠券
     * @return {@code true} 表示成功佔用一次額度；{@code false} 表示已達 {@code max_usage_count}
     */
    public boolean tryConsumeUsageQuota(PromoCode promo) {
        int affected = promoCodeRepository.incrementUsageCountIfWithinLimit(promo.getId());
        if (affected == 0) {
            log.warn("Promo usage quota exhausted at commit: code={}, id={}",
                    promo.getCode(), promo.getId());
            return false;
        }
        log.info("Promo usage quota consumed: code={}, id={}", promo.getCode(), promo.getId());
        return true;
    }

    /**
     * 原子退還一次總量額度（Sprint 102，DEF-046）。
     *
     * <p>訂單取消時呼叫，與 {@link #tryConsumeUsageQuota} 對稱。
     *
     * @param promoCodeId 優惠券 ID
     */
    public void releaseUsageQuota(UUID promoCodeId) {
        int affected = promoCodeRepository.decrementUsageCount(promoCodeId);
        if (affected == 0) {
            log.warn("Promo usage quota release affected no row: id={}", promoCodeId);
            return;
        }
        log.info("Promo usage quota released: id={}", promoCodeId);
    }

    /**
     * 結帳當下的促銷碼驗證（PRD §9.5.1）。
     *
     * <p>Sprint 126（DEF-048 擴大範圍）從 {@code OrderService}／{@code BookingService} 兩份
     * 幾乎逐行相同的 private 方法抽到這裡共用——新增第三個呼叫者（合併結帳）若繼續各自複製一份，
     * 會變成三份幾乎相同的邏輯。依 PRD 明訂順序驗證：1. 存在且 ACTIVE → 2. 有效時間範圍 →
     * 3. 使用上限 → 4. 每人限用次數（{@code max_usage_per_user} 自 V20 建表即存在，
     * 但在 Sprint 100 之前全庫沒有任何程式碼讀取它）。
     *
     * <p>驗證失敗一律拒絕結帳而非靜默改以原價成立：買家在購物車／結帳頁看到的是折扣後金額，
     * 若此處靜默回退原價，等同在買家不知情下多收款。
     *
     * @return 通過驗證的促銷碼；{@code null} 表示未套用促銷碼
     */
    public PromoCode resolveValidPromoForCheckout(
            final String appliedPromoCode, final UUID tenantId, final UUID userId) {
        if (appliedPromoCode == null || appliedPromoCode.isBlank()) {
            return null;
        }
        String normalized = appliedPromoCode.trim().toUpperCase(java.util.Locale.ROOT);

        PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseAndTenantId(normalized, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5007,
                        "Promo code no longer available at checkout: " + normalized));

        if (!promo.getIsActive()) {
            throw new BusinessException(ErrorCode.E_5007, "Promo code is inactive: " + normalized);
        }
        if (promo.isNotYetActive()) {
            throw new BusinessException(ErrorCode.E_5007, "Promo code is not yet active: " + normalized);
        }
        if (promo.isExpired()) {
            throw new BusinessException(ErrorCode.E_5008, "Promo code expired before checkout: " + normalized);
        }
        if (promo.isUsageLimitReached()) {
            throw new BusinessException(ErrorCode.E_5009, "Promo code usage limit reached: " + normalized);
        }

        // 每人限用次數：這裡是「提早失敗、給精確訊息」的前置檢查，真正的把關在
        // tryConsumeUsageQuota 取得行鎖之後的重查（見呼叫端的 commit 階段）。
        if (perUserLimitReached(promo, userId)) {
            throw new BusinessException(ErrorCode.E_5009,
                    "Promo code per-user usage limit reached: " + normalized);
        }

        return promo;
    }

    /**
     * 該買家對該促銷碼是否已用盡 {@code max_usage_per_user} 額度。
     *
     * <p>僅計 {@code ACTIVE} 的用券紀錄；取消退還後的 {@code REVOKED} 不佔額度。
     * {@code null} 表示不限每人次數。Sprint 126 從 {@code OrderService}／{@code BookingService}
     * 抽到這裡共用，理由同 {@link #resolveValidPromoForCheckout}。
     */
    public boolean perUserLimitReached(final PromoCode promo, final UUID userId) {
        if (promo.getMaxUsagePerUser() == null) {
            return false;
        }
        long usedByUser = promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                promo.getId(), userId, PromoCodeUsage.UsageStatus.ACTIVE);
        return usedByUser >= promo.getMaxUsagePerUser();
    }

    /**
     * 依券別計算已套用 {@code maxDiscountAmount} 上限、且不超過應付總額的折扣金額
     * （PRD §9.5.1 步驟 5：折扣後金額不得為負）。
     *
     * <p>Sprint 126 從 {@code OrderService.applyPromoDiscount} 拆出的純計算部分，供
     * {@code OrderService}／{@code BookingService}／合併結帳三處呼叫端共用同一套「折扣不超額」
     * 規則，避免各自重寫一份上限判斷。
     *
     * @param itemsTotal  商品／訂房小計（不含運費），為 {@link #computeDiscount} 的折扣基數之一
     * @param shippingFee 運費（訂房固定傳 {@code BigDecimal.ZERO}），為 FREE_SHIPPING 的折扣基數
     */
    public BigDecimal computeCappedDiscount(
            final PromoCode promo, final BigDecimal itemsTotal, final BigDecimal shippingFee) {
        if (promo == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = computeDiscount(promo, itemsTotal, shippingFee);
        BigDecimal grossAmount = itemsTotal.add(shippingFee);
        if (discount.compareTo(grossAmount) > 0) {
            discount = grossAmount;
        }
        return discount;
    }

    /**
     * Order 側取消時評估是否釋放用券額度（PRD §2630「優惠券：若已使用促銷碼，則退還」）。
     *
     * <p>單一類型用券紀錄（{@code bookingId} 為 null）：立即 REVOKED＋釋放額度，行為與
     * Sprint 100～124 完全相同。合併結帳用券紀錄（{@code bookingId} 非 null，Sprint 126，
     * DEF-048 擴大範圍，使用者拍板「兩邊都取消才退還」）：本次呼叫只標記
     * {@code orderReleasedAt}；只有 {@code bookingReleasedAt} 也已非 null（Booking 側先前
     * 已取消）才真正 REVOKED＋釋放額度，否則保留 {@code ACTIVE}，讓尚未取消那一側的交易
     * 繼續受這筆用券紀錄保護（{@link #perUserLimitReached} 仍計入這筆 {@code ACTIVE} 紀錄，
     * 避免額度被「只取消一半」提早釋放）。
     *
     * @return {@code true} 表示本次呼叫確實釋放了額度；{@code false} 表示只記錄了本側取消、
     *         額度尚未釋放（合併結帳的另一側仍在使用中）
     */
    public boolean releaseOrderSide(final PromoCodeUsage usage) {
        if (usage.getBookingId() == null) {
            revokeAndReleaseQuota(usage);
            return true;
        }
        usage.setOrderReleasedAt(Instant.now());
        if (usage.getBookingReleasedAt() != null) {
            revokeAndReleaseQuota(usage);
            return true;
        }
        promoCodeUsageRepository.save(usage);
        return false;
    }

    /** Booking 側取消時評估是否釋放用券額度。語意與 {@link #releaseOrderSide} 完全對稱。 */
    public boolean releaseBookingSide(final PromoCodeUsage usage) {
        if (usage.getOrderId() == null) {
            revokeAndReleaseQuota(usage);
            return true;
        }
        usage.setBookingReleasedAt(Instant.now());
        if (usage.getOrderReleasedAt() != null) {
            revokeAndReleaseQuota(usage);
            return true;
        }
        promoCodeUsageRepository.save(usage);
        return false;
    }

    private void revokeAndReleaseQuota(final PromoCodeUsage usage) {
        usage.setStatus(PromoCodeUsage.UsageStatus.REVOKED);
        usage.setRevokedAt(Instant.now());
        promoCodeUsageRepository.save(usage);
        // Sprint 102（DEF-046）：改為原子相對遞減。原本的「讀出 → 減 1 → save」在兩筆用同一張券
        // 的交易同時取消時會互相覆蓋，額度只退還一次，買家永久少一次可用額度。
        releaseUsageQuota(usage.getPromoCodeId());
    }

    /** {@link #allocateDiscount} 的回傳值：分攤到 PRODUCT／ROOM 兩側各自的折扣金額。 */
    public record DiscountAllocation(BigDecimal productDiscount, BigDecimal roomDiscount) {
    }

    /**
     * 合併結帳（PRODUCT+ROOM 一次結清，Sprint 126，DEF-048 擴大範圍）把單一張券算出的
     * {@code totalDiscount} 分攤到 PRODUCT／ROOM 兩側（使用者已拍板）：
     *
     * <ul>
     *   <li>{@code FREE_SHIPPING}：只有 PRODUCT 側有運費，折扣 100% 歸 PRODUCT，
     *       ROOM 側恆為 0——pro-rata 分攤在此不適用，ROOM 從未產生運費，分一部分給它沒有意義。</li>
     *   <li>{@code PERCENTAGE}／{@code FIXED_AMOUNT}：依小計權重比例分攤（pro-rata）。數學上可證
     *       PERCENTAGE 逐邊獨立算與先合併算再依權重分攤，結果恆相等；FIXED_AMOUNT 依權重分攤是
     *       業界慣用做法。</li>
     * </ul>
     *
     * <p>由於 {@code totalDiscount} 已由 {@link #computeCappedDiscount} 保證不超過
     * {@code productSubtotal + roomSubtotal}，且兩側權重和為 1，分攤後兩側金額必然分別不超過
     * 各自的小計，不需要額外逐邊上限檢查。餘數（四捨五入誤差）歸 ROOM 側，避免一分錢誤差。
     *
     * @param productSubtotal PRODUCT 側小計（不含運費）
     * @param roomSubtotal    ROOM 側小計（訂房總額，訂房無運費）
     */
    public DiscountAllocation allocateDiscount(final PromoCode promo, final BigDecimal totalDiscount,
            final BigDecimal productSubtotal, final BigDecimal roomSubtotal) {
        if (totalDiscount == null || totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return new DiscountAllocation(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (promo != null && promo.getDiscountType() == PromoCode.DiscountType.FREE_SHIPPING) {
            return new DiscountAllocation(totalDiscount, BigDecimal.ZERO);
        }
        BigDecimal combinedTotal = productSubtotal.add(roomSubtotal);
        if (combinedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new DiscountAllocation(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal productShare = totalDiscount.multiply(productSubtotal)
                .divide(combinedTotal, 2, RoundingMode.HALF_UP);
        BigDecimal roomShare = totalDiscount.subtract(productShare);
        return new DiscountAllocation(productShare, roomShare);
    }
}