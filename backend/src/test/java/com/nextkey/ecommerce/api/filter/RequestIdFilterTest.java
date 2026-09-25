package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nextkey.ecommerce.shared.trace.RequestId;

/**
 * DEF-280：PRD §16.4.1 要求所有回應帶 {@code X-Request-ID}。此值會寫進日誌，所以「呼叫端帶入的值
 * 必須經過驗證」是這個 filter 的核心行為——測試鎖定的是這個意圖，而不只是「有設定標頭」。
 */
@DisplayName("DEF-280: RequestIdFilter")
class RequestIdFilterTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private MockHttpServletResponse run(final MockHttpServletRequest request, final FilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("呼叫端沒帶 X-Request-ID 時產生 UUID")
    void generatesUuidWhenAbsent() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest(), (req, res) -> { });

        assertThat(response.getHeader(RequestId.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("呼叫端帶入合法的 X-Request-ID 時原樣沿用（利於跨系統串接）")
    void keepsValidInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestId.HEADER, "abc-123_DEF.456");

        MockHttpServletResponse response = run(request, (req, res) -> { });

        assertThat(response.getHeader(RequestId.HEADER)).isEqualTo("abc-123_DEF.456");
    }

    @ParameterizedTest(name = "不安全的帶入值會被丟棄並重新產生：[{0}]")
    @ValueSource(strings = {
            "",
            "has space",
            "abc\r\nX-Injected: 1",
            "abc\nFAKE LOG LINE",
            "<script>alert(1)</script>",
            "中文",
    })
    @DisplayName("含空白／換行／特殊字元的帶入值不可進入日誌與回應")
    void replacesUnsafeInboundId(final String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestId.HEADER, unsafe);

        MockHttpServletResponse response = run(request, (req, res) -> { });

        assertThat(response.getHeader(RequestId.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("超過 64 字元的帶入值被丟棄，恰 64 字元則保留")
    void enforcesLengthLimit() throws Exception {
        MockHttpServletRequest tooLong = new MockHttpServletRequest();
        tooLong.addHeader(RequestId.HEADER, "a".repeat(65));
        MockHttpServletRequest boundary = new MockHttpServletRequest();
        boundary.addHeader(RequestId.HEADER, "a".repeat(64));

        assertThat(run(tooLong, (req, res) -> { }).getHeader(RequestId.HEADER)).matches(UUID_PATTERN);
        assertThat(run(boundary, (req, res) -> { }).getHeader(RequestId.HEADER)).isEqualTo("a".repeat(64));
    }

    @Test
    @DisplayName("處理期間 MDC 有值且與回應標頭一致，結束後清除（不可殘留到下一個請求）")
    void mdcIsSetDuringChainAndClearedAfter() throws Exception {
        AtomicReference<String> seenInChain = new AtomicReference<>();

        MockHttpServletResponse response = run(new MockHttpServletRequest(),
                (req, res) -> seenInChain.set(RequestId.current()));

        assertThat(seenInChain.get()).isNotNull().isEqualTo(response.getHeader(RequestId.HEADER));
        assertThat(RequestId.current()).isNull();
    }

    @Test
    @DisplayName("下游拋例外時 MDC 仍會清除（執行緒會被池化重用）")
    void mdcIsClearedWhenChainThrows() {
        assertThatThrownBy(() -> run(new MockHttpServletRequest(), (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(RequestId.current()).isNull();
    }

    @Test
    @DisplayName("下游回 401（如 Security 擋下）時標頭仍在——標頭在進入過濾鏈前就已設定")
    void headerSurvivesDownstreamErrorStatus() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest(),
                (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(RequestId.HEADER)).matches(UUID_PATTERN);
    }
}
