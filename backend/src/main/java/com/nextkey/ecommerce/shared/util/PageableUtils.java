package com.nextkey.ecommerce.shared.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    private static int safePage(final int page) {
        return Math.max(0, page);
    }

    private static int safeSize(final int size, final int maxSize) {
        return Math.max(1, Math.min(size, maxSize));
    }
}
