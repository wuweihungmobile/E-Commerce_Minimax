package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * LoginRateLimitFilter 單元測試（Sprint 168，DEF-220）。
 *
 * <p>Lua script 的 token bucket 補充/扣除邏輯本身以真 Redis 驗證，見
 * {@link LoginRateLimitFilterIntegrationTest}；本測試以 Mockito 模擬 script 執行結果，
 * 聚焦驗證 Filter 層的路徑排除、429 回應與 Redis 故障 fail-open 行為，比照
 * {@link RateLimitFilterTest} 的測試手法。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginRateLimitFilter 單元測試（Sprint 168，DEF-220）")
class LoginRateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginRateLimitFilter newFilter() {
        return new LoginRateLimitFilter(new FixedObjectProvider<>(redisTemplate));
    }

    @Test
    @DisplayName("非登入路徑（如 GET /v2/orders）應被 shouldNotFilter 排除，不呼叫 Redis")
    void nonLoginPath_skipsRateLimitEntirely() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.setServletPath("/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("GET /v2/auth/login（非 POST）應被排除，不呼叫 Redis")
    void loginPathWithNonPostMethod_skipsRateLimitEntirely() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("shouldNotFilter：只有 POST /v2/auth/login 不被排除")
    void shouldNotFilter_onlyExcludesPostLogin() {
        LoginRateLimitFilter filter = newFilter();
        MockHttpServletRequest loginPost = new MockHttpServletRequest("POST", "/v2/auth/login");
        loginPost.setServletPath("/v2/auth/login");
        MockHttpServletRequest loginGet = new MockHttpServletRequest("GET", "/v2/auth/login");
        loginGet.setServletPath("/v2/auth/login");
        MockHttpServletRequest registerPost = new MockHttpServletRequest("POST", "/v2/auth/register");
        registerPost.setServletPath("/v2/auth/register");

        assertThat(filter.shouldNotFilter(loginPost)).isFalse();
        assertThat(filter.shouldNotFilter(loginGet)).isTrue();
        assertThat(filter.shouldNotFilter(registerPost)).isTrue();
    }

    @Test
    @DisplayName("Token 尚有餘額時放行，不回傳 429")
    void allowedRequest_continuesChain() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("1:7:0");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Token 已耗盡時回傳 429，含 Retry-After header 與 E_9904 錯誤內容，且不繼續呼叫 chain")
    void deniedRequest_returns429WithRetryAfterAndStopsChain() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("0:0:600");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("E-9904");
    }

    @Test
    @DisplayName("Redis 故障（DataAccessException）時 fail-open，放行請求且不拋例外")
    void redisFailure_failsOpenAndContinuesChain() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis script 回傳 null 時 fail-open，不拋 NPE")
    void redisScriptReturnsNull_failsOpenAndContinuesChain() throws Exception {
        LoginRateLimitFilter filter = newFilter();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("StringRedisTemplate bean 不存在時直接放行")
    void noRedisTemplateBean_skipsRateLimitEntirely() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new FixedObjectProvider<>(null));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
