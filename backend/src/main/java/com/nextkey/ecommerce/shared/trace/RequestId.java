package com.nextkey.ecommerce.shared.trace;

import org.slf4j.MDC;

/**
 * 請求追蹤 ID（PRD §16.4.1 {@code X-Request-ID}）：HTTP 標頭名稱、日誌 MDC 鍵，以及讀取目前請求 ID 的入口。
 * 值由 {@code RequestIdFilter} 於請求進入時寫入 MDC、離開時清除。
 */
public final class RequestId {

    public static final String HEADER = "X-Request-ID";

    public static final String MDC_KEY = "requestId";

    private RequestId() {
    }

    /** 目前請求的追蹤 ID；不在請求範圍內（排程、單元測試等）時回傳 null。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
