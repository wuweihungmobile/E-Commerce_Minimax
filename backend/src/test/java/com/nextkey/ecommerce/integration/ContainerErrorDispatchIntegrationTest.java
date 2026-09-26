package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-281（Sprint 201）：被 Spring Security 防火牆拒絕的請求（路徑含 {@code %0A}、{@code //} 等），
 * 是「呼叫端送了不合法的網址」，必須回 400；先前卻回 401 {@code E-1000}「需要驗證身份」，且內容沒有 requestId。
 *
 * <p>成因：防火牆拒絕後呼叫 {@code sendError(400)}，Servlet 容器隨即對 {@code /error} 做一次 ERROR 分派，
 * 這次分派會重新走過 Security 過濾鏈；此時身分是匿名（JWT 過濾器預設不處理 ERROR 分派）、
 * 又不在 {@code permitAll} 之列，於是被 {@code anyRequest().authenticated()} 擋成 401——連帶有效 token 也一樣。
 *
 * <p>為什麼必須是真實伺服器（{@code RANDOM_PORT}）而不是 MockMvc：ERROR 分派是 Servlet 容器的行為，
 * MockMvc 遇到 {@code sendError} 只會把狀態碼設成 400 就結束，永遠不會再打 {@code /error}，
 * 所以在 MockMvc 下這個缺陷根本重現不了，其他整合測試也因此一直守不到。
 * 用 JDK 內建的 {@link HttpClient}：它不會改寫路徑，{@code %0A} 會原樣送出。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-ERRDISP: 容器層錯誤分派（防火牆拒絕的請求）")
class ContainerErrorDispatchIntegrationTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /**
     * 後端一律要帶的安全標頭（PRD 16.4.1 明列 nosniff 與 X-Frame-Options: DENY；其餘為 SecurityHeaderPolicy 的內容）。
     * 逐值比對而非只看「有沒有」：政策改動時這裡會跟著紅，提醒同步確認。
     */
    private static final Map<String, String> EXPECTED_SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "X-XSS-Protection", "0",
            "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'",
            "Referrer-Policy", "no-referrer",
            "Cache-Control", "no-cache, no-store, max-age=0, must-revalidate",
            "Pragma", "no-cache",
            "Expires", "0");

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpResponse<String> get(final String pathAfterContext) throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + pathAfterContext);
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest(name = "路徑 {0}")
    @ValueSource(strings = {"/v2/x%0AFORGED-LOG-LINE", "/v2//auth/me"})
    @DisplayName("IT-ERRDISP-01: 防火牆拒絕的請求 → 400 + E-9000，而非 401 + E-1000（呼叫端的錯不可被報成認證失敗）")
    void firewallRejectedRequest_is400_notAuthenticationFailure(final String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("success").asBoolean(true)).isFalse();
        assertThat(body.path("code").asText()).isEqualTo("E-9000");
    }

    @Test
    @DisplayName("IT-ERRDISP-02: 內容的 requestId 與回應標頭 X-Request-ID 是同一個值（使用者回報時才對得到日誌）")
    void firewallRejectedRequest_bodyRequestIdMatchesHeader() throws Exception {
        HttpResponse<String> response = get("/v2/x%0AFORGED-LOG-LINE");

        String headerId = response.headers().firstValue("X-Request-ID").orElse(null);
        assertThat(headerId).matches(UUID_PATTERN);
        assertThat(mapper.readTree(response.body()).path("requestId").asText()).isEqualTo(headerId);
    }

    @Test
    @DisplayName("IT-ERRDISP-03: 瀏覽器（Accept 只要 text/html）也拿到 JSON 封包，不是白頁或協商失敗")
    void firewallRejectedRequest_isJsonEvenWhenClientOnlyAcceptsHtml() throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + "/v2/x%0AFORGED-LOG-LINE");
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri).header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("E-9000");
    }

    @Test
    @DisplayName("IT-ERRDISP-04: 放行只限容器內部的 ERROR 分派——直接請求 /error 的未登入者仍是 401（防止放行過寬）")
    void directRequestToErrorPath_unauthenticated_stillRequiresAuthentication() throws Exception {
        HttpResponse<String> response = get("/error");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("E-1000");
    }

    private HttpResponse<String> getWithHeader(final String pathAfterContext, final String name, final String value)
            throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + pathAfterContext);
        return client.send(HttpRequest.newBuilder(uri).header(name, value).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "一般 401（過濾鏈有跑）|/v2/auth/me|401",
            "防火牆拒絕 %0A（容器 ERROR 分派）|/v2/x%0AFORGED-LOG-LINE|400",
            "防火牆拒絕 //（容器 ERROR 分派）|/v2//auth/me|400"})
    @DisplayName("IT-ERRDISP-05: 不論回應走哪條路徑，安全標頭一律齊全且相同（DEF-282：ERROR 分派原本一個都沒有）")
    void everyErrorResponse_carriesTheSameSecurityHeaders(
            final String scenario, final String path, final int expectedStatus) throws Exception {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        // containsExactly：恰好一個值——若日後過濾器與控制器同時寫入而產生重複標頭，這裡會抓到
        EXPECTED_SECURITY_HEADERS.forEach((name, value) -> assertThat(response.headers().allValues(name))
                .as("%s 的 %s", scenario, name).containsExactly(value));
    }

    @Test
    @DisplayName("IT-ERRDISP-06: ERROR 分派的回應與其他回應一樣，只在 HTTPS（含代理轉送）才帶 HSTS")
    void hstsOnErrorDispatch_followsTheSameRuleAsOtherResponses() throws Exception {
        HttpResponse<String> behindTlsProxy = getWithHeader("/v2/x%0AFORGED-LOG-LINE", "X-Forwarded-Proto", "https");
        assertThat(behindTlsProxy.statusCode()).isEqualTo(400);
        assertThat(behindTlsProxy.headers().allValues("Strict-Transport-Security"))
                .containsExactly("max-age=31536000");

        HttpResponse<String> plainHttp = get("/v2/x%0AFORGED-LOG-LINE");
        assertThat(plainHttp.statusCode()).isEqualTo(400);
        assertThat(plainHttp.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
