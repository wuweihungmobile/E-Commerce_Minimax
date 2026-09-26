package com.nextkey.ecommerce.api.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.config.SecurityHeaderPolicy;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.trace.RequestId;

/**
 * Sprint 201（DEF-281）：Servlet 容器的錯誤分派（{@code sendError} 之後對 {@code /error} 的內部轉送）統一回
 * {@link ApiResponse} 封包。
 *
 * <p>最常見的來源是 Spring Security 防火牆拒絕不合法的網址（路徑含 {@code %0A}、{@code //} 等）：
 * 它呼叫 {@code sendError(400)}，容器隨後轉送到這裡。沒有這個控制器時，Spring Boot 內建的
 * {@code BasicErrorController} 會回非封包格式的 JSON，瀏覽器請求甚至拿到 HTML 白頁，
 * 與其他所有錯誤回應的形狀不一致；前端解析 {@code code}／{@code requestId} 時會落空。
 *
 * <p>{@code requestId} 取自回應標頭 {@code X-Request-ID}：{@code RequestIdFilter} 在第一輪請求就已寫入，
 * 容器轉送時標頭仍保留；但日誌 MDC 在第一輪結束時已清除，{@link ApiResponse#error} 從 MDC 取得的值是空的，
 * 所以在此補上。
 *
 * <p>安全標頭（Sprint 202，DEF-282）：這條路徑不經過 Spring Security 的 {@code HeaderWriterFilter}，
 * 由此處套用 {@link SecurityHeaderPolicy}——與 {@code SecurityConfig} 用的是同一份，不是複製。
 *
 * <p>只處理「容器內部的 ERROR 分派」——{@code SecurityConfig} 只對該分派類型放行；
 * 直接請求 {@code /error} 的人仍須通過驗證。內容刻意不回顯路徑或例外訊息：路徑是呼叫端控制的字串，
 * 而防火牆拒絕的正是含換行字元等日誌偽造用的寫法。
 */
@RestController
public class ApiErrorController implements ErrorController {

    /**
     * 路徑與 Spring Boot 內建錯誤頁註冊的設定同源（{@code server.error.path}，預設 {@code /error}），
     * 兩者必須一致，容器才轉送得到這裡。
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ApiResponse<Void>> error(final HttpServletRequest request,
                                                   final HttpServletResponse response) {
        // ERROR 分派不會經過 Spring Security 的 HeaderWriterFilter（它沿用 OncePerRequestFilter 預設，
        // 不處理該分派），這條路徑的回應因此原本一個安全標頭都沒有；在此套用與其他回應同一份政策（DEF-282）。
        SecurityHeaderPolicy.apply(request, response);

        HttpStatusCode status = resolveStatus(request);

        ApiResponse<Void> body = ApiResponse.error(codeOf(status), messageOf(status));
        body.setRequestId(response.getHeader(RequestId.HEADER));

        // 明確指定 Content-Type，不隨請求的 Accept 協商：瀏覽器與掃描器的 Accept 五花八門，
        // 錯誤回應不應因此變成另一種格式，甚至協商失敗又觸發一次錯誤。
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** 容器轉送時會帶狀態碼；沒有代表這是一般請求直接打到 /error（例如已登入者手動輸入網址），視為找不到。 */
    private static HttpStatusCode resolveStatus(final HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return attribute instanceof Integer code ? HttpStatusCode.valueOf(code) : HttpStatus.NOT_FOUND;
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
