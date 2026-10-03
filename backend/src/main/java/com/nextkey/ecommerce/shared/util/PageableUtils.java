package com.nextkey.ecommerce.shared.util;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 分頁參數安全建構工具（Sprint 165，DEF-216）。
 *
 * <p>Spring Data 的 {@code PageRequest}/{@code AbstractPageRequest} 建構子本身只驗證
 * {@code page >= 0}、{@code size >= 1}，不合法時直接拋出未攔截的 {@code IllegalArgumentException}；
 * 且完全不驗證上限。全庫先前各呼叫點各自用 {@code Math.min(size, N)} 補上限，卻遺漏下限防護，
 * {@code size<=0} 或 {@code page<0} 時仍會讓建構子拋出例外，落入全域例外處理器的 catch-all 變成 500。
 * 此工具統一處理上下限，取代散落各處、容易遺漏下限的內嵌算式。
 */
public final class PageableUtils {

    private PageableUtils() {
    }

    public static PageRequest of(final int page, final int size, final int maxSize) {
        return PageRequest.of(safePage(page), safeSize(size, maxSize));
    }

    public static PageRequest of(final int page, final int size, final int maxSize, final Sort sort) {
        return PageRequest.of(safePage(page), safeSize(size, maxSize), sort);
    }

    /**
     * 由查詢參數建構排序（Sprint 243，DEF-339）。{@code sortBy} 只接受呼叫端列舉的屬性、{@code sortDir} 只接受
     * {@code asc}／{@code desc}（不分大小寫），其餘一律 {@code E-9000}（400）。
     *
     * <p>原本是 {@code Sort.by(Sort.Direction.fromString(sortDir), sortBy)} 直接用使用者輸入：{@code sortDir} 不是 asc／desc 時
     * {@code fromString} 丟未攔截的 {@code IllegalArgumentException}、{@code sortBy} 不是實體屬性時 Spring Data 在執行查詢時丟
     * {@code PropertyReferenceException}，兩者都落入全域處理器的 catch-all，使用者的輸入錯誤變成 500 {@code E-9900}。
     * 也不該讓呼叫者依任意欄位排序（例如含個資的欄位）。
     */
    public static Sort sortOf(final String sortBy, final String sortDir, final Set<String> allowedProperties) {
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDir)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9000, "Invalid sort direction: " + sortDir));
        if (sortBy == null || !allowedProperties.contains(sortBy)) {
            throw new BusinessException(ErrorCode.E_9000,
                    "Invalid sort field: " + sortBy + " (allowed: " + allowedProperties + ")");
        }
        return Sort.by(direction, sortBy);
    }

    private static int safePage(final int page) {
        return Math.max(0, page);
    }

    private static int safeSize(final int size, final int maxSize) {
        return Math.max(1, Math.min(size, maxSize));
    }
}
