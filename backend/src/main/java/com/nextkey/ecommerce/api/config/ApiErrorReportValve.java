package com.nextkey.ecommerce.api.config;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nextkey.ecommerce.shared.trace.RequestId;

/**
 * Tomcat 連接器層錯誤回應改回 JSON 封包（Sprint 206，DEF-283）。
 *
 * <p>成因：路徑含編碼斜線（{@code %2f}）或編碼反斜線（{@code %5C}）的請求，由 Tomcat 連接器<b>在進入 Servlet 之前</b>
 * 就拒絕，回 Tomcat 內建的 HTML 錯誤頁——沒有 JSON 封包、沒有 {@code X-Request-ID}、沒有任何安全標頭，
 * 應用的 Filter、{@code ApiErrorController} 與 {@code SecurityHeaderPolicy} 都碰不到。
 *
 * <p>做法：只換掉 Host 上<b>負責寫錯誤頁的那個 valve</b>。<b>不放寬任何 Tomcat 的拒絕規則</b>（{@code %2f}、{@code %5C}
 * 依舊被 Tomcat 擋在應用之外），只改「拒絕之後回什麼」。前置條件與 Tomcat 原實作一致：狀態碼 &lt; 400、
 * 已經寫過內容、或這個錯誤已回報過，就不動。
 *
 * <p>由 Tomcat 以反射建立，沒有 Spring 注入；註冊見 {@link ApiErrorReportValveCustomizer}。
 */
public class ApiErrorReportValve extends ErrorReportValve {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorReportValve.class);
    private static final int HTTP_BAD_REQUEST = 400;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    protected void report(final Request request, final Response response, final Throwable throwable) {
        if (response.getStatus() < HTTP_BAD_REQUEST || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        try {
            // 請求沒進過 RequestIdFilter，沒有可沿用的 ID，這裡重新產生並同時放進回應標頭與內容
            String requestId = UUID.randomUUID().toString();
            response.setHeader(RequestId.HEADER, requestId);
            SecurityHeaderPolicy.apply(request, response);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");

            // 只記狀態碼與 ID，刻意不記路徑：路徑是呼叫端控制的字串，被拒絕的正是含換行字元等日誌偽造用的寫法
            LOG.warn("Tomcat connector rejected a request: status={} requestId={}", response.getStatus(), requestId);

            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(MAPPER.writeValueAsString(
                        ContainerErrorResponse.forStatus(HttpStatusCode.valueOf(response.getStatus()), requestId)));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            // 與 Tomcat 原實作一致：走到這裡已經無法再回報錯誤，吞掉即可
            LOG.debug("Could not write the connector-level error response", e);
        }
    }
}
