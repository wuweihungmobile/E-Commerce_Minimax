package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * DEF-397（Sprint 296）：PRD §16.4.1 規定「所有 Error Response 的 {@code Content-Type} 固定值
 * {@code application/json; charset=utf-8}」，實測卻只有連接器層的拒絕符合；其餘 JSON 回應不帶 charset，
 * {@code SecurityConfig} 的 401 甚至宣告成 {@code ISO-8859-1}（<b>宣告的編碼是錯的</b>，只是訊息剛好是 ASCII 才沒損毀）。
 * 使用者在 Sprint 294 收尾時選擇以全域設定 {@code server.servlet.encoding.force-response=true} 解決。
 *
 * <p>為什麼必須是真實伺服器（{@code RANDOM_PORT}）：{@code CharacterEncodingFilter} 設定的是 Servlet 容器的回應編碼，
 * 401 是 Security 過濾鏈在容器內直接寫出的回應，防火牆與連接器層的拒絕更是容器行為，MockMvc 看不到這些。
 * 用 JDK 內建的 {@link HttpClient}：它不會改寫路徑，{@code %0A}、{@code %2f} 會原樣送出。
 *
 * <p>比對時把 charset 值正規化（小寫、去空白）：charset 的值不分大小寫，連接器層的 {@code ApiErrorReportValve}
 * 寫的是小寫 {@code utf-8}，其餘來源是 Tomcat 依設定寫的 {@code UTF-8}，兩者等價。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-CT: 回應 Content-Type 一律宣告 UTF-8（PRD §16.4.1，DEF-397）")
class ErrorResponseContentTypeIntegrationTest {

    /** PRD §16.4.1 的字面值，已正規化（小寫、無空白）。 */
    private static final String PRD_CONTENT_TYPE = "application/json;charset=utf-8";

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("IT-CT-001: 未登入的 401（SecurityConfig 入口）宣告 UTF-8——修復前是 ISO-8859-1，宣告的編碼是錯的")
    void unauthenticated401_declaresUtf8() throws Exception {
        HttpResponse<String> response = send("/v2/orders");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(contentType(response)).isEqualTo(PRD_CONTENT_TYPE);
    }

    @Test
    @DisplayName("IT-CT-002: MVC 產生的 400（參數型別不符，GlobalExceptionHandler）宣告 UTF-8")
    void mvcBadRequest400_declaresUtf8() throws Exception {
        HttpResponse<String> response = send("/v2/public/listings?size=not-a-number");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).isEqualTo(PRD_CONTENT_TYPE);
    }

    @Test
    @DisplayName("IT-CT-003: 防火牆拒絕的 400（ApiErrorController，ERROR 分派）宣告 UTF-8")
    void firewallRejected400_declaresUtf8() throws Exception {
        HttpResponse<String> response = send("/v2/x%0AFORGED-LOG-LINE");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).isEqualTo(PRD_CONTENT_TYPE);
    }

    @Test
    @DisplayName("IT-CT-004: 連接器層拒絕的 400（ApiErrorReportValve）宣告 UTF-8——原本就符合，防止被改壞")
    void connectorRejected400_declaresUtf8() throws Exception {
        HttpResponse<String> response = send("/v2/x%2fy");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).isEqualTo(PRD_CONTENT_TYPE);
    }

    @Test
    @DisplayName("IT-CT-005: 正常的 200 JSON 也宣告 UTF-8——設定是全域的，不只錯誤回應")
    void ok200Json_declaresUtf8() throws Exception {
        HttpResponse<String> response = send("/v2/public/listings?size=1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).isEqualTo(PRD_CONTENT_TYPE);
    }

    private HttpResponse<String> send(final String pathAfterContext) throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + pathAfterContext);
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 取 Content-Type 並正規化（小寫、去空白），沒有這個標頭就回報明確的訊息而不是 NoSuchElementException。 */
    private static String contentType(final HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).replace(" ", ""))
                .orElse("<沒有 Content-Type 標頭>");
    }
}
