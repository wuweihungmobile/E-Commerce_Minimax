package com.nextkey.ecommerce.core.booking;

import java.math.BigDecimal;

import com.nextkey.ecommerce.api.dto.PricingDto;

/**
 * 訂房單元測試共用固件：定價引擎的「沒有任何規則生效」回應（DEF-424，Sprint 309）。
 *
 * <p>動態定價開關改由 {@code PricingService} 以<b>房源所屬店鋪</b>把關後，{@code BookingService} 不再自己判斷開關，
 * 一律向引擎查詢。這些單元測試以 mock 取代引擎，測的不是定價，所以預設「引擎回基準價」——與店鋪開關關閉、
 * 或沒有規則生效時引擎的實際結果相同：{@code adjustedTotal == baseTotal}，沒有逐日 breakdown，
 * {@code BookingService} 因此不套用任何調整。
 *
 * <p>沒有 stub 的 mock 會回 {@code null}，而正式環境的引擎永遠不會回 {@code null}，所以這個固件不是遷就測試的防禦性程式，
 * 而是讓 mock 的行為貼近真實的引擎。
 */
final class BookingPricingFixtures {

    private BookingPricingFixtures() {
    }

    /** 引擎的「沒有任何規則生效」回應：調整前後總額相同、沒有逐日 breakdown。 */
    static PricingDto.CalculatePriceResponse noAdjustment() {
        return PricingDto.CalculatePriceResponse.builder()
                .baseTotal(BigDecimal.ZERO)
                .adjustedTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .build();
    }
}
