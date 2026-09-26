package com.nextkey.ecommerce.api.config;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.HstsHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;

/**
 * 後端回應的安全標頭政策，<strong>整個服務唯一的定義處</strong>（Sprint 202，DEF-282）。
 *
 * <p>兩個地方套用同一份：
 * <ul>
 *   <li>{@link SecurityConfig}：交給 Spring Security 的 {@code HeaderWriterFilter}，涵蓋一般請求的所有回應；</li>
 *   <li>{@code ApiErrorController}：Servlet 容器的 ERROR 分派（{@code sendError} 之後對 {@code /error} 的內部轉送）。
 *       {@code HeaderWriterFilter} 沿用 {@code OncePerRequestFilter} 預設，不處理 ERROR 分派，
 *       所以防火牆拒絕的請求等回應原本完全沒有安全標頭。</li>
 * </ul>
 * 政策放在這裡而不是各處複製，是為了讓兩條路徑不可能漂移：日後要改任何一個標頭，只改這一處。
 *
 * <p>內容與 Spring Security 預設加上 {@link SecurityConfig} 原本的自訂項目完全等價：
 * {@code nosniff}、{@code X-XSS-Protection: 0}、{@code Cache-Control}／{@code Pragma}／{@code Expires}、
 * {@code X-Frame-Options: DENY} 是預設值；CSP、{@code Referrer-Policy} 與 HSTS 的送出條件是本專案補的。
 * 本服務只回 JSON（無 Swagger 或 HTML 頁），{@code default-src 'none'} 不會擋到功能。
 */
public final class SecurityHeaderPolicy {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private static final List<HeaderWriter> WRITERS = List.of(
        new XContentTypeOptionsHeaderWriter(),
        new XXssProtectionHeaderWriter(),
        new CacheControlHeadersWriter(),
        // 不含 includeSubDomains：子網域是否都能走 https 無從驗證，而該指示送出後一年內無法收回
        new HstsHeaderWriter(SecurityHeaderPolicy::isHttpsRequest, HSTS_MAX_AGE_SECONDS, false),
        new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY),
        new ContentSecurityPolicyHeaderWriter(API_CSP),
        new ReferrerPolicyHeaderWriter(ReferrerPolicy.NO_REFERRER));

    private SecurityHeaderPolicy() {
    }

    /** 政策的所有標頭寫入器；不可變，各實作本身無狀態，可跨請求共用。 */
    public static List<HeaderWriter> writers() {
        return WRITERS;
    }

    /** 把整套政策寫進這個回應；給不經 {@code HeaderWriterFilter} 的回應路徑（容器 ERROR 分派）使用。 */
    public static void apply(final HttpServletRequest request, final HttpServletResponse response) {
        WRITERS.forEach(writer -> writer.writeHeaders(request, response));
    }

    /**
     * HSTS 只能在 HTTPS 回應上送（瀏覽器對 http 回應中的 HSTS 一律忽略）。TLS 通常在應用前方的代理／負載平衡器
     * 終止，後端收到的是 http，Spring 預設的 {@code request.isSecure()} 因此恆為 false、HSTS 永遠不送；
     * 這裡同時採信 {@code X-Forwarded-Proto}（多層代理時取第一段）。此標頭可被呼叫端偽造，但偽造只會讓 http
     * 回應多帶一個瀏覽器本來就會忽略的標頭，沒有安全影響。
     */
    static boolean isHttpsRequest(final HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim());
    }
}
