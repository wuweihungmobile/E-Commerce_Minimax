package com.nextkey.ecommerce.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Servlet 容器層回給呼叫端的錯誤封包，<strong>整個服務唯一的定義處</strong>（Sprint 206，DEF-283）。
 *
 * <p>兩個地方使用同一份，讓容器層的兩種回應路徑不可能漂移：
 * <ul>
 *   <li>{@code ApiErrorController}：容器的 ERROR 分派（{@code sendError} 之後對 {@code /error} 的內部轉送）；</li>
 *   <li>{@link ApiErrorReportValve}：Tomcat 連接器自己拒絕、根本進不到 Servlet 的請求（{@code %2f}、{@code %5C}）。</li>
 * </ul>
 * 內容刻意不回顯路徑或例外訊息：路徑是呼叫端控制的字串，防火牆與連接器拒絕的正是含換行字元等日誌偽造用的寫法。
 */
public final class ContainerErrorResponse {

    private ContainerErrorResponse() {
    }

    /** 404→{@code E-4041}、其他 4xx→{@code E-9000}、其餘→{@code E-9900}；{@code requestId} 由呼叫端提供。 */
    public static ApiResponse<Void> forStatus(final HttpStatusCode status, final String requestId) {
        ApiResponse<Void> body = ApiResponse.error(codeOf(status), messageOf(status));
        body.setRequestId(requestId);
        return body;
    }

    private static String codeOf(final HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.E_4041.getCode();
        }
        return status.is4xxClientError() ? ErrorCode.E_9000.getCode() : ErrorCode.E_9900.getCode();
    }

    private static String messageOf(final HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "找不到請求的資源";
        }
        return status.is4xxClientError() ? "請求格式錯誤" : "發生未預期的錯誤";
    }
}
