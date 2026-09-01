package com.nextkey.ecommerce.core.promo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.CartDto.PromoValidationResult;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCode.DiscountType;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;

/**
 * PromoService 單元測試（Sprint 78 US-001）。
 *
 * <p>背景：{@link PromoService}（3 個 public 方法：validatePromoCode、computeDiscount、
 * incrementUsageCount）是「多 Sprint 測試強化計劃」剩餘模組之一，先前完全沒有單元測試，
 * 只有 {@code M11CartPromoIntegrationTest} 這類需要完整 Spring Context 的整合測試間接涵蓋。
 *
 * <p>擁有權/租戶檢查現況：{@code validatePromoCode(promoCodeStr, tenantId)} 直接透過
 * {@code PromoCodeRepository.findByCodeIgnoreCaseAndTenantId} 以 tenantId 做查詢層級的
 * 租戶隔離（非事後過濾），設計正確——本測試以 {@link ArgumentCaptor} 驗證傳入 Repository 的
 * tenantId 與呼叫端傳入的值一致，確認沒有被忽略或替換。{@code computeDiscount} /
 * {@code incrementUsageCount} 接受呼叫端已查得的 {@code PromoCode} 物件，本身不重複做
 * 租戶檢查——檢視唯一呼叫端 {@code RedisCartService} 後確認：兩者都只會拿到先前
 * {@code validatePromoCode} 已用正確 tenantId 查出的 promo，屬合理的信任邊界（同一次
 * 呼叫鏈內、非跨模組暴露的方法），未發現需要修復的擁有權/租戶檢查缺口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromoService 單元測試（Sprint 78）")
class PromoServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private PromoService promoService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    /** 非免運券情境的運費基數：帶 0 表示「本次不考慮運費」，讓斷言聚焦在商品折扣本身。 */
    private static final BigDecimal NO_SHIPPING = BigDecimal.ZERO;

    private PromoCode.PromoCodeBuilder activePromoBuilder() {
        LocalDateTime now = LocalDateTime.now();
        return PromoCode.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(1))
                .currentUsageCount(0)
                .isActive(true);
    }

    // ========== validatePromoCode ==========

    @Test
    @DisplayName("validatePromoCode：代碼為 null → INVALID，且不查詢 Repository")
    void validatePromoCode_nullCode_returnsInvalidWithoutQuery() {
        PromoValidationResult result = promoService.validatePromoCode(null, TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("INVALID");
        verify(promoCodeRepository, never()).findByCodeIgnoreCaseAndTenantId(any(), any());
    }

    @Test
    @DisplayName("validatePromoCode：代碼為空白字串 → INVALID，且不查詢 Repository")
    void validatePromoCode_blankCode_returnsInvalidWithoutQuery() {
        PromoValidationResult result = promoService.validatePromoCode("   ", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("INVALID");
        verify(promoCodeRepository, never()).findByCodeIgnoreCaseAndTenantId(any(), any());
    }

    @Test
    @DisplayName("validatePromoCode：代碼查無資料（含跨租戶找不到的情境）→ INVALID，且查詢時傳入的 tenantId 與呼叫端一致（租戶隔離由查詢條件保證）")
    void validatePromoCode_notFound_returnsInvalid_andQueriesWithCorrectTenant() {
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.empty());

        PromoValidationResult result = promoService.validatePromoCode(" save10 ", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("INVALID");

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(promoCodeRepository).findByCodeIgnoreCaseAndTenantId(codeCaptor.capture(), tenantCaptor.capture());
        // 驗證代碼有正規化（trim + 轉大寫）才查詢，且租戶條件確實是呼叫端傳入的 tenantId
        assertThat(codeCaptor.getValue()).isEqualTo("SAVE10");
        assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("validatePromoCode：優惠券已停用 → INACTIVE")
    void validatePromoCode_inactive_returnsInactive() {
        PromoCode promo = activePromoBuilder().isActive(false).build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        PromoValidationResult result = promoService.validatePromoCode("SAVE10", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("INACTIVE");
    }

    @Test
    @DisplayName("validatePromoCode：優惠券尚未開始 → NOT_YET_ACTIVE")
    void validatePromoCode_notYetActive_returnsNotYetActive() {
        LocalDateTime now = LocalDateTime.now();
        PromoCode promo = activePromoBuilder()
                .startDate(now.plusDays(1))
                .endDate(now.plusDays(10))
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        PromoValidationResult result = promoService.validatePromoCode("SAVE10", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("NOT_YET_ACTIVE");
    }

    @Test
    @DisplayName("validatePromoCode：優惠券已過期 → EXPIRED")
    void validatePromoCode_expired_returnsExpired() {
        LocalDateTime now = LocalDateTime.now();
        PromoCode promo = activePromoBuilder()
                .startDate(now.minusDays(10))
                .endDate(now.minusDays(1))
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        PromoValidationResult result = promoService.validatePromoCode("SAVE10", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("EXPIRED");
    }

    @Test
    @DisplayName("validatePromoCode：已達使用上限 → USAGE_LIMIT")
    void validatePromoCode_usageLimitReached_returnsUsageLimit() {
        PromoCode promo = activePromoBuilder()
                .maxUsageCount(5)
                .currentUsageCount(5)
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        PromoValidationResult result = promoService.validatePromoCode("SAVE10", TENANT_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidReason()).startsWith("USAGE_LIMIT");
    }

    @Test
    @DisplayName("validatePromoCode：合法有效優惠券 → VALID，並帶回正確的折扣資訊")
    void validatePromoCode_valid_returnsValidResult() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15"))
                .maxDiscountAmount(new BigDecimal("100"))
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        PromoValidationResult result = promoService.validatePromoCode("SAVE10", TENANT_ID);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPromoCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(result.getDiscountValue()).isEqualByComparingTo("15");
        assertThat(result.getMaxDiscount()).isEqualByComparingTo("100");
    }

    // ========== computeDiscount ==========

    @Test
    @DisplayName("computeDiscount：promo 為 null → 0")
    void computeDiscount_nullPromo_returnsZero() {
        assertThat(promoService.computeDiscount(null, BigDecimal.TEN, NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：itemsTotal 為 null → 0")
    void computeDiscount_nullTotalAmount_returnsZero() {
        PromoCode promo = activePromoBuilder().build();
        assertThat(promoService.computeDiscount(promo, null, NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：itemsTotal 為 0 或負數 → 0")
    void computeDiscount_nonPositiveTotalAmount_returnsZero() {
        PromoCode promo = activePromoBuilder().build();
        assertThat(promoService.computeDiscount(promo, BigDecimal.ZERO, NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(promoService.computeDiscount(promo, new BigDecimal("-1"), NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：未達最低消費門檻 → 0")
    void computeDiscount_belowMinPurchase_returnsZero() {
        PromoCode promo = activePromoBuilder()
                .minPurchaseAmount(new BigDecimal("100"))
                .build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("50"), NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：PERCENTAGE 類型正確計算折扣（含四捨五入到小數點後 2 位）")
    void computeDiscount_percentage_computesCorrectly() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15"))
                .build();

        BigDecimal discount = promoService.computeDiscount(promo, new BigDecimal("333.33"), NO_SHIPPING);

        // 333.33 * 15% = 49.9995 → HALF_UP 進位到 50.00
        assertThat(discount).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("computeDiscount：PERCENTAGE 折扣基數為商品小計，運費不參與計算")
    void computeDiscount_percentage_ignoresShippingFee() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();

        // 商品小計 1000、運費 200：若誤把運費併入基數會折 120，正確應只折 100
        assertThat(promoService.computeDiscount(promo, new BigDecimal("1000"), new BigDecimal("200")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("computeDiscount：PERCENTAGE 類型受 maxDiscountAmount 上限限制")
    void computeDiscount_percentage_cappedByMaxDiscount() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("50"))
                .maxDiscountAmount(new BigDecimal("30"))
                .build();

        BigDecimal discount = promoService.computeDiscount(promo, new BigDecimal("1000"), NO_SHIPPING);

        // 1000 * 50% = 500，但上限只有 30
        assertThat(discount).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("computeDiscount：FIXED_AMOUNT 類型取「固定折扣金額」與「消費總額」較小者")
    void computeDiscount_fixedAmount_takesMinOfDiscountAndTotal() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("200"))
                .build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("500"), NO_SHIPPING))
                .isEqualByComparingTo("200.00");
        assertThat(promoService.computeDiscount(promo, new BigDecimal("100"), NO_SHIPPING))
                .isEqualByComparingTo("100.00");
    }

    // ---------- FREE_SHIPPING（Sprint 101 / AI-2435，修復 DEF-045） ----------

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 全額折抵運費（DEF-045：此前一律回 0，買家拿不到任何優惠）")
    void computeDiscount_freeShipping_waivesFullShippingFee() {
        PromoCode promo = freeShippingPromoBuilder().build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("500"), new BigDecimal("120")))
                .isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 折抵金額只跟運費走，商品小計再高也不多折")
    void computeDiscount_freeShipping_doesNotDiscountItems() {
        PromoCode promo = freeShippingPromoBuilder().build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("9999"), new BigDecimal("60")))
                .isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 遇店家滿額免運（運費已為 0）→ 折抵 0，故不需另做疊加排除邏輯")
    void computeDiscount_freeShipping_alreadyFreeShipping_returnsZero() {
        PromoCode promo = freeShippingPromoBuilder().build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("2000"), BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 受 maxDiscountAmount 上限限制（店家可發部分折抵運費的券）")
    void computeDiscount_freeShipping_cappedByMaxDiscount() {
        PromoCode promo = freeShippingPromoBuilder()
                .maxDiscountAmount(new BigDecimal("60"))
                .build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("500"), new BigDecimal("120")))
                .isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 未達最低消費門檻 → 0（門檻一律以商品小計判斷，不含運費）")
    void computeDiscount_freeShipping_belowMinPurchase_returnsZero() {
        PromoCode promo = freeShippingPromoBuilder()
                .minPurchaseAmount(new BigDecimal("1000"))
                .build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("500"), new BigDecimal("120")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeDiscount：FREE_SHIPPING 運費為 null → 0（防禦，不得 NPE）")
    void computeDiscount_freeShipping_nullShippingFee_returnsZero() {
        PromoCode promo = freeShippingPromoBuilder().build();

        assertThat(promoService.computeDiscount(promo, new BigDecimal("500"), null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private PromoCode.PromoCodeBuilder freeShippingPromoBuilder() {
        return activePromoBuilder()
                .discountType(DiscountType.FREE_SHIPPING)
                .discountValue(BigDecimal.ZERO);
    }

    // ========== incrementUsageCount ==========

    @Test
    @DisplayName("incrementUsageCount：使用次數 +1 並儲存")
    void incrementUsageCount_incrementsAndSaves() {
        PromoCode promo = activePromoBuilder().currentUsageCount(3).build();

        promoService.incrementUsageCount(promo);

        assertThat(promo.getCurrentUsageCount()).isEqualTo(4);
        verify(promoCodeRepository, times(1)).save(eq(promo));
    }
}
