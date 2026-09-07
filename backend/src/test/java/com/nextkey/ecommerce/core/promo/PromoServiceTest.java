package com.nextkey.ecommerce.core.promo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * PromoService 單元測試（Sprint 78 US-001）。
 *
 * <p>背景：{@link PromoService}（Sprint 78 當時為 3 個 public 方法：validatePromoCode、
 * computeDiscount、incrementUsageCount；後者已於 Sprint 102 / DEF-046 由
 * tryConsumeUsageQuota + releaseUsageQuota 取代）是「多 Sprint 測試強化計劃」剩餘模組之一，先前完全沒有單元測試，
 * 只有 {@code M11CartPromoIntegrationTest} 這類需要完整 Spring Context 的整合測試間接涵蓋。
 *
 * <p>擁有權/租戶檢查現況：{@code validatePromoCode(promoCodeStr, tenantId)} 直接透過
 * {@code PromoCodeRepository.findByCodeIgnoreCaseAndTenantId} 以 tenantId 做查詢層級的
 * 租戶隔離（非事後過濾），設計正確——本測試以 {@link ArgumentCaptor} 驗證傳入 Repository 的
 * tenantId 與呼叫端傳入的值一致，確認沒有被忽略或替換。{@code computeDiscount} /
 * {@code tryConsumeUsageQuota} 接受呼叫端已查得的 {@code PromoCode} 物件，本身不重複做
 * 租戶檢查——檢視唯一呼叫端 {@code RedisCartService} 後確認：兩者都只會拿到先前
 * {@code validatePromoCode} 已用正確 tenantId 查出的 promo，屬合理的信任邊界（同一次
 * 呼叫鏈內、非跨模組暴露的方法），未發現需要修復的擁有權/租戶檢查缺口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromoService 單元測試（Sprint 78）")
class PromoServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private PromoCodeUsageRepository promoCodeUsageRepository;

    private static final UUID PROMO_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

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

    // ========== tryConsumeUsageQuota / releaseUsageQuota（Sprint 102，DEF-046）==========

    @Test
    @DisplayName("tryConsumeUsageQuota：條件式 UPDATE 影響 1 筆 → 佔用成功")
    void tryConsumeUsageQuota_returnsTrueWhenRowUpdated() {
        PromoCode promo = activePromoBuilder().currentUsageCount(3).build();
        promo.setId(PROMO_ID);
        when(promoCodeRepository.incrementUsageCountIfWithinLimit(PROMO_ID)).thenReturn(1);

        assertThat(promoService.tryConsumeUsageQuota(promo)).isTrue();
    }

    @Test
    @DisplayName("tryConsumeUsageQuota：條件式 UPDATE 影響 0 筆（已達上限）→ 佔用失敗")
    void tryConsumeUsageQuota_returnsFalseWhenNoRowUpdated() {
        PromoCode promo = activePromoBuilder().currentUsageCount(3).build();
        promo.setId(PROMO_ID);
        when(promoCodeRepository.incrementUsageCountIfWithinLimit(PROMO_ID)).thenReturn(0);

        assertThat(promoService.tryConsumeUsageQuota(promo)).isFalse();
    }

    @Test
    @DisplayName("tryConsumeUsageQuota：不得退回讀後寫——只以 id 呼叫原子 UPDATE，絕不 save 實體")
    void tryConsumeUsageQuota_neverFallsBackToReadModifyWrite() {
        PromoCode promo = activePromoBuilder().currentUsageCount(3).build();
        promo.setId(PROMO_ID);
        when(promoCodeRepository.incrementUsageCountIfWithinLimit(PROMO_ID)).thenReturn(1);

        promoService.tryConsumeUsageQuota(promo);

        // 這是 DEF-046 的核心意圖：任何 save(promo) 都代表讀後寫回歸，競態視窗隨之回來
        verify(promoCodeRepository, never()).save(any(PromoCode.class));
        verify(promoCodeRepository, times(1)).incrementUsageCountIfWithinLimit(eq(PROMO_ID));
    }

    @Test
    @DisplayName("releaseUsageQuota：以原子相對遞減退還額度，不 save 實體")
    void releaseUsageQuota_usesAtomicDecrement() {
        when(promoCodeRepository.decrementUsageCount(PROMO_ID)).thenReturn(1);

        promoService.releaseUsageQuota(PROMO_ID);

        verify(promoCodeRepository, times(1)).decrementUsageCount(eq(PROMO_ID));
        verify(promoCodeRepository, never()).save(any(PromoCode.class));
    }

    @Test
    @DisplayName("releaseUsageQuota：找不到該券（影響 0 筆）→ 不拋例外，取消訂單流程不因此中斷")
    void releaseUsageQuota_missingRowDoesNotThrow() {
        when(promoCodeRepository.decrementUsageCount(PROMO_ID)).thenReturn(0);

        promoService.releaseUsageQuota(PROMO_ID);

        verify(promoCodeRepository, times(1)).decrementUsageCount(eq(PROMO_ID));
    }

    // ========== resolveValidPromoForCheckout（Sprint 126，從 OrderService/BookingService 抽出共用）==========
    //
    // 背景：OrderPromoCodeTest／BookingPromoCodeTest 原本以 mock PromoCodeRepository 直接驗證這些
    // 規則（因為邏輯當時就寫在 OrderService/BookingService 裡）；Sprint 126（DEF-048 擴大範圍：
    // 合併結帳）把這段幾乎逐行相同的邏輯抽到 PromoService 共用，兩處呼叫端測試改為 mock PromoService
    // 只驗證委派（見 OrderPromoCodeTest.CheckoutRevalidationTests 的說明），實際規則驗證下沉到這裡。

    @Test
    @DisplayName("resolveValidPromoForCheckout：券碼為 null/空白 → 回傳 null，不查詢 Repository")
    void resolveValidPromoForCheckout_blankCode_returnsNullWithoutQuery() {
        assertThat(promoService.resolveValidPromoForCheckout(null, TENANT_ID, USER_ID)).isNull();
        assertThat(promoService.resolveValidPromoForCheckout("  ", TENANT_ID, USER_ID)).isNull();
        verify(promoCodeRepository, never()).findByCodeIgnoreCaseAndTenantId(any(), any());
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：券碼查無資料 → 拒絕 E_5007")
    void resolveValidPromoForCheckout_notFound_throwsE5007() {
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("save10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5007);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：已停用 → 拒絕 E_5007")
    void resolveValidPromoForCheckout_inactive_throwsE5007() {
        PromoCode promo = activePromoBuilder().isActive(false).build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("SAVE10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5007);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：尚未開始 → 拒絕 E_5007")
    void resolveValidPromoForCheckout_notYetActive_throwsE5007() {
        LocalDateTime now = LocalDateTime.now();
        PromoCode promo = activePromoBuilder().startDate(now.plusDays(1)).endDate(now.plusDays(10)).build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("SAVE10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5007);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：已過期 → 拒絕 E_5008")
    void resolveValidPromoForCheckout_expired_throwsE5008() {
        LocalDateTime now = LocalDateTime.now();
        PromoCode promo = activePromoBuilder().startDate(now.minusDays(10)).endDate(now.minusDays(1)).build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("SAVE10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5008);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：總量已達上限 → 拒絕 E_5009")
    void resolveValidPromoForCheckout_usageLimitReached_throwsE5009() {
        PromoCode promo = activePromoBuilder().maxUsageCount(5).currentUsageCount(5).build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("SAVE10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5009);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：該買家已達每人限用次數 → 拒絕 E_5009")
    void resolveValidPromoForCheckout_perUserLimitReached_throwsE5009() {
        PromoCode promo = activePromoBuilder().maxUsagePerUser(1).build();
        promo.setId(PROMO_ID);
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));
        when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> promoService.resolveValidPromoForCheckout("SAVE10", TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5009);
    }

    @Test
    @DisplayName("resolveValidPromoForCheckout：通過所有驗證 → 回傳該券")
    void resolveValidPromoForCheckout_valid_returnsPromo() {
        PromoCode promo = activePromoBuilder().build();
        promo.setId(PROMO_ID);
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId("SAVE10", TENANT_ID))
                .thenReturn(Optional.of(promo));
        when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(0L);

        assertThat(promoService.resolveValidPromoForCheckout(" save10 ", TENANT_ID, USER_ID)).isEqualTo(promo);
    }

    // ========== perUserLimitReached ==========

    @Test
    @DisplayName("perUserLimitReached：maxUsagePerUser 為 null → 不限次數，永遠 false")
    void perUserLimitReached_unlimitedWhenNull() {
        PromoCode promo = activePromoBuilder().maxUsagePerUser(null).build();

        assertThat(promoService.perUserLimitReached(promo, USER_ID)).isFalse();
        verify(promoCodeUsageRepository, never()).countByPromoCodeIdAndUserIdAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("perUserLimitReached：僅計 ACTIVE 用券紀錄，達到上限才 true")
    void perUserLimitReached_countsOnlyActiveUsages() {
        PromoCode promo = activePromoBuilder().maxUsagePerUser(2).build();
        promo.setId(PROMO_ID);
        when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(2L);

        assertThat(promoService.perUserLimitReached(promo, USER_ID)).isTrue();
    }

    // ========== computeCappedDiscount（Sprint 126，從 OrderService.applyPromoDiscount 抽出）==========

    @Test
    @DisplayName("computeCappedDiscount：promo 為 null → 0")
    void computeCappedDiscount_nullPromo_returnsZero() {
        assertThat(promoService.computeCappedDiscount(null, BigDecimal.TEN, NO_SHIPPING))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeCappedDiscount：折扣未超過應付總額 → 原樣回傳")
    void computeCappedDiscount_withinGross_returnsAsIs() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50"))
                .build();

        // 小計 200 + 運費 0，折扣 50 未超過毛額 200
        assertThat(promoService.computeCappedDiscount(promo, new BigDecimal("200"), NO_SHIPPING))
                .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("computeCappedDiscount：折扣超過應付總額（商品小計+運費）→ 封頂為應付總額，不得為負（PRD §9.5.1 步驟 5）")
    void computeCappedDiscount_exceedsGross_cappedToGross() {
        // FIXED_AMOUNT／FREE_SHIPPING 的原始折扣本就分別受 itemsTotal／shippingFee 限制、
        // 恆不超過毛額（itemsTotal+shippingFee），故現實中唯一可能觸發此處二次封頂的情境是
        // PERCENTAGE 券的折扣率設超過 100%（設定面未禁止，屬防禦性上限而非常態情境）。
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("200"))
                .build();

        // 小計 200 + 運費 60 = 毛額 260，200% 折扣原始值為 400，遠大於毛額
        BigDecimal discount = promoService.computeCappedDiscount(promo, new BigDecimal("200"), new BigDecimal("60"));

        assertThat(discount).isEqualByComparingTo("260.00");
    }

    // ========== releaseOrderSide / releaseBookingSide（Sprint 126，DEF-048 擴大範圍：合併結帳）==========
    //
    // 背景：使用者拍板「合併結帳（一次結清 PRODUCT+ROOM）若事後只取消其中一邊，優惠券額度
    // 兩邊都取消才退還」。單一類型用券紀錄（bookingId 或 orderId 其中一個為 null）行為不變
    // （立即 REVOKED＋釋放額度），合併結帳用券紀錄（兩者皆非 null）則需雙側都取消才真正釋放。

    @Test
    @DisplayName("releaseOrderSide：單一類型用券紀錄（bookingId 為 null）→ 立即 REVOKED 並釋放額度")
    void releaseOrderSide_singleTypeUsage_revokesImmediately() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .build();
        when(promoCodeUsageRepository.updateStatusIfCurrent(eq(usage.getId()),
                eq(PromoCodeUsage.UsageStatus.ACTIVE), eq(PromoCodeUsage.UsageStatus.REVOKED), any()))
                .thenReturn(1);
        when(promoCodeRepository.decrementUsageCount(PROMO_ID)).thenReturn(1);

        boolean released = promoService.releaseOrderSide(usage);

        assertThat(released).isTrue();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.REVOKED);
        assertThat(usage.getRevokedAt()).isNotNull();
        verify(promoCodeRepository).decrementUsageCount(PROMO_ID);
    }

    @Test
    @DisplayName("releaseOrderSide：合併結帳用券紀錄只取消 Order 側 → 不釋放額度，只記錄本側取消時間")
    void releaseOrderSide_combinedUsageOnlyOrderCancelled_doesNotRelease() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .bookingId(BOOKING_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .build();

        boolean released = promoService.releaseOrderSide(usage);

        assertThat(released).isFalse();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usage.getOrderReleasedAt()).isNotNull();
        assertThat(usage.getBookingReleasedAt()).isNull();
        verify(promoCodeUsageRepository).save(usage);
        verify(promoCodeRepository, never()).decrementUsageCount(any());
    }

    @Test
    @DisplayName("releaseOrderSide：合併結帳用券紀錄兩側都已取消（Booking 側先前已取消）→ 這次才真正 REVOKED 並釋放額度")
    void releaseOrderSide_combinedUsageBothSidesCancelled_releases() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .bookingId(BOOKING_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .bookingReleasedAt(Instant.now())
                .build();
        when(promoCodeUsageRepository.updateStatusIfCurrent(eq(usage.getId()),
                eq(PromoCodeUsage.UsageStatus.ACTIVE), eq(PromoCodeUsage.UsageStatus.REVOKED), any()))
                .thenReturn(1);
        when(promoCodeRepository.decrementUsageCount(PROMO_ID)).thenReturn(1);

        boolean released = promoService.releaseOrderSide(usage);

        assertThat(released).isTrue();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.REVOKED);
        verify(promoCodeRepository).decrementUsageCount(PROMO_ID);
    }

    @Test
    @DisplayName("releaseBookingSide：語意對稱 releaseOrderSide——單一類型立即釋放")
    void releaseBookingSide_singleTypeUsage_revokesImmediately() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .bookingId(BOOKING_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .build();
        when(promoCodeUsageRepository.updateStatusIfCurrent(eq(usage.getId()),
                eq(PromoCodeUsage.UsageStatus.ACTIVE), eq(PromoCodeUsage.UsageStatus.REVOKED), any()))
                .thenReturn(1);
        when(promoCodeRepository.decrementUsageCount(PROMO_ID)).thenReturn(1);

        assertThat(promoService.releaseBookingSide(usage)).isTrue();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.REVOKED);
    }

    @Test
    @DisplayName("🔴 releaseBookingSide：併發搶佔（updateStatusIfCurrent 影響 0 列，例如已被另一併發"
            + "呼叫搶先撤銷）-> 不重複釋放額度")
    void releaseBookingSide_concurrentClaim_doesNotReleaseQuotaTwice() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .bookingId(BOOKING_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .build();
        when(promoCodeUsageRepository.updateStatusIfCurrent(eq(usage.getId()),
                eq(PromoCodeUsage.UsageStatus.ACTIVE), eq(PromoCodeUsage.UsageStatus.REVOKED), any()))
                .thenReturn(0);

        assertThat(promoService.releaseBookingSide(usage)).isTrue();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        verify(promoCodeRepository, never()).decrementUsageCount(any());
    }

    @Test
    @DisplayName("releaseBookingSide：合併結帳用券紀錄只取消 Booking 側 → 不釋放額度，只記錄本側取消時間")
    void releaseBookingSide_combinedUsageOnlyBookingCancelled_doesNotRelease() {
        PromoCodeUsage usage = PromoCodeUsage.builder()
                .id(UUID.randomUUID())
                .promoCodeId(PROMO_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .bookingId(BOOKING_ID)
                .status(PromoCodeUsage.UsageStatus.ACTIVE)
                .build();

        assertThat(promoService.releaseBookingSide(usage)).isFalse();
        assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usage.getBookingReleasedAt()).isNotNull();
        verify(promoCodeRepository, never()).decrementUsageCount(any());
    }

    // ========== allocateDiscount（Sprint 126，DEF-048 擴大範圍：合併結帳折扣分攤）==========
    //
    // 背景：使用者拍板合併結帳（PRODUCT+ROOM 一次結清）用一張券時，PERCENTAGE／FIXED_AMOUNT
    // 依小計權重比例分攤（pro-rata）；FREE_SHIPPING 只有 PRODUCT 側有運費，100% 歸 PRODUCT。

    @Test
    @DisplayName("allocateDiscount：totalDiscount 為 0 或 null → 兩側皆 0")
    void allocateDiscount_zeroOrNullDiscount_returnsZeroBoth() {
        PromoCode promo = activePromoBuilder().discountType(DiscountType.FIXED_AMOUNT).build();

        PromoService.DiscountAllocation zero =
                promoService.allocateDiscount(promo, BigDecimal.ZERO, new BigDecimal("300"), new BigDecimal("700"));
        PromoService.DiscountAllocation nullDiscount =
                promoService.allocateDiscount(promo, null, new BigDecimal("300"), new BigDecimal("700"));

        assertThat(zero.productDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.roomDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(nullDiscount.productDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(nullDiscount.roomDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("allocateDiscount：FREE_SHIPPING → 折扣 100% 歸 PRODUCT，ROOM 側恆為 0（ROOM 從未產生運費）")
    void allocateDiscount_freeShipping_allToProduct() {
        PromoCode promo = freeShippingPromoBuilder().build();

        PromoService.DiscountAllocation allocation = promoService.allocateDiscount(
                promo, new BigDecimal("60.00"), new BigDecimal("300"), new BigDecimal("700"));

        assertThat(allocation.productDiscount()).isEqualByComparingTo("60.00");
        assertThat(allocation.roomDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("allocateDiscount：PERCENTAGE 依小計權重比例分攤，結果與逐邊獨立算該百分比完全相等")
    void allocateDiscount_percentage_matchesIndependentPerSideCalculation() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();
        BigDecimal productSubtotal = new BigDecimal("300");
        BigDecimal roomSubtotal = new BigDecimal("700");

        // 先合併算出的總折扣，再依權重分攤
        BigDecimal totalDiscount = promoService.computeDiscount(
                promo, productSubtotal.add(roomSubtotal), BigDecimal.ZERO);
        PromoService.DiscountAllocation allocation =
                promoService.allocateDiscount(promo, totalDiscount, productSubtotal, roomSubtotal);

        // 逐邊獨立算 10%：300*10%=30、700*10%=70——與 pro-rata 分攤結果應完全相等
        assertThat(allocation.productDiscount()).isEqualByComparingTo("30.00");
        assertThat(allocation.roomDiscount()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("allocateDiscount：FIXED_AMOUNT 依小計權重比例分攤（業界慣用做法）")
    void allocateDiscount_fixedAmount_splitsByWeight() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("100"))
                .build();
        // 小計 300+700=1000，折扣 100 依權重分攤：PRODUCT 30%→30、ROOM 70%→70
        PromoService.DiscountAllocation allocation = promoService.allocateDiscount(
                promo, new BigDecimal("100.00"), new BigDecimal("300"), new BigDecimal("700"));

        assertThat(allocation.productDiscount()).isEqualByComparingTo("30.00");
        assertThat(allocation.roomDiscount()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("allocateDiscount：四捨五入產生的餘數歸 ROOM 側，兩側加總恆等於 totalDiscount（不遺失一分錢）")
    void allocateDiscount_roundingRemainder_goesToRoomAndSumsExactly() {
        PromoCode promo = activePromoBuilder()
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10"))
                .build();
        // 小計 100+200=300，PRODUCT 權重 1/3：10 * 100/300 = 3.333... → 四捨五入 3.33
        // ROOM 側取餘數 10 - 3.33 = 6.67，兩者相加剛好等於 10.00，不因四捨五入短少
        PromoService.DiscountAllocation allocation = promoService.allocateDiscount(
                promo, new BigDecimal("10.00"), new BigDecimal("100"), new BigDecimal("200"));

        assertThat(allocation.productDiscount()).isEqualByComparingTo("3.33");
        assertThat(allocation.roomDiscount()).isEqualByComparingTo("6.67");
        assertThat(allocation.productDiscount().add(allocation.roomDiscount()))
                .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("allocateDiscount：合併小計為 0（防禦性邊界，正常流程不會出現）→ 兩側皆 0，不拋除以零例外")
    void allocateDiscount_zeroCombinedTotal_returnsZeroBothWithoutDivideByZero() {
        PromoCode promo = activePromoBuilder().discountType(DiscountType.FIXED_AMOUNT).build();

        PromoService.DiscountAllocation allocation = promoService.allocateDiscount(
                promo, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(allocation.productDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(allocation.roomDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
