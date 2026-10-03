package com.nextkey.ecommerce.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Sprint 165（DEF-216）單元測試：確認 {@link PageableUtils} 正確處理 page/size 的
 * 上下限，且不論輸入多離譜都不會拋出例外（取代先前散落各處、遺漏下限防護的
 * {@code Math.min(size, N)} 內嵌算式）。
 */
@DisplayName("PageableUtils: 分頁參數安全建構")
class PageableUtilsTest {

    @Test
    @DisplayName("size 為 0 或負數 → 正規化為最小值 1，不拋例外")
    void of_nonPositiveSize_normalizesToOne() {
        assertThat(PageableUtils.of(0, 0, 100).getPageSize()).isEqualTo(1);
        assertThat(PageableUtils.of(0, -5, 100).getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("page 為負數 → 正規化為 0，不拋例外")
    void of_negativePage_normalizesToZero() {
        assertThat(PageableUtils.of(-1, 20, 100).getPageNumber()).isZero();
        assertThat(PageableUtils.of(Integer.MIN_VALUE, 20, 100).getPageNumber()).isZero();
    }

    @Test
    @DisplayName("size 超過上限 → 截斷為上限值")
    void of_sizeExceedsMax_cappedAtMax() {
        assertThat(PageableUtils.of(0, 999999999, 100).getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("page/size 皆在合法範圍內 → 原樣保留")
    void of_validInput_unchanged() {
        PageRequest result = PageableUtils.of(3, 25, 100);
        assertThat(result.getPageNumber()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("帶 Sort 的多載同樣正規化邊界值且保留排序")
    void ofWithSort_normalizesAndKeepsSort() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest result = PageableUtils.of(-1, 0, 100, sort);
        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("極端組合（page 與 size 皆非法）依然不拋例外")
    void of_bothInvalid_neverThrows() {
        assertThatCode(() -> PageableUtils.of(-100, -100, 100)).doesNotThrowAnyException();
    }

    // ===== sortOf（Sprint 243，DEF-339）=====

    private static final java.util.Set<String> ALLOWED = java.util.Set.of("createdAt", "totalAmount");

    @Test
    @DisplayName("sortOf：允許的欄位與 asc／desc（不分大小寫）→ 依指定欄位與方向排序")
    void sortOf_validInput_buildsTheSort() {
        assertThat(PageableUtils.sortOf("createdAt", "DESC", ALLOWED))
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(PageableUtils.sortOf("totalAmount", "asc", ALLOWED))
                .isEqualTo(Sort.by(Sort.Direction.ASC, "totalAmount"));
        assertThat(PageableUtils.sortOf("totalAmount", "Desc", ALLOWED))
                .isEqualTo(Sort.by(Sort.Direction.DESC, "totalAmount"));
    }

    @Test
    @DisplayName("sortOf：不在允許清單的欄位 → E-9000（不是 Spring Data 到查詢時才丟的 PropertyReferenceException → 500）")
    void sortOf_unknownField_isRejectedWithE9000() {
        for (String field : new String[] {"bogus", "password", "", " ", "createdAt,desc", "user.passwordHash", null}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> PageableUtils.sortOf(field, "DESC", ALLOWED))
                    .isInstanceOf(com.nextkey.ecommerce.shared.exception.BusinessException.class)
                    .extracting(e -> ((com.nextkey.ecommerce.shared.exception.BusinessException) e).getErrorCode())
                    .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_9000);
        }
    }

    @Test
    @DisplayName("sortOf：方向不是 asc／desc → E-9000（不是 Direction.fromString 的 IllegalArgumentException → 500）")
    void sortOf_badDirection_isRejectedWithE9000() {
        for (String direction : new String[] {"SIDEWAYS", "", " ", "up", null}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> PageableUtils.sortOf("createdAt", direction, ALLOWED))
                    .isInstanceOf(com.nextkey.ecommerce.shared.exception.BusinessException.class)
                    .extracting(e -> ((com.nextkey.ecommerce.shared.exception.BusinessException) e).getErrorCode())
                    .isEqualTo(com.nextkey.ecommerce.shared.exception.ErrorCode.E_9000);
        }
    }
}
