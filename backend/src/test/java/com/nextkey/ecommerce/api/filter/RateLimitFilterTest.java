package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
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

import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * RateLimitFilter 單元測試（Sprint 93，PRD §3.3/§13.4/§16.4.2）。
 *
 * <p>Lua script 的 token bucket 補充/扣除邏輯本身以真 Redis 驗證，見
 * {@link RateLimitFilterIntegrationTest}；本測試以 Mockito 模擬 script 執行結果，
 * 聚焦驗證 Filter 層的路徑排除、header 設定、429 回應與 Redis 故障 fail-open 行為。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 單元測試（Sprint 93）")
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RateLimitFilter newFilter() {
        return new RateLimitFilter(new FixedObjectProvider<>(redisTemplate));
    }

    @Test
    @DisplayName("未解析出租戶（如 /v2/auth/**）時直接放行，不呼叫 Redis")
    void nullTenant_skipsRateLimitEntirely() throws Exception {
        RateLimitFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("/v2/auth/** 與 /actuator/** 應被 shouldNotFilter 排除，其他路徑不排除")
    void shouldNotFilter_excludesAuthAndActuator() {
        RateLimitFilter filter = newFilter();
        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/v2/auth/login");
        authRequest.setServletPath("/v2/auth/login");
        MockHttpServletRequest actuatorRequest = new MockHttpServletRequest("GET", "/actuator/health");
        actuatorRequest.setServletPath("/actuator/health");
        MockHttpServletRequest orderRequest = new MockHttpServletRequest("GET", "/v2/orders");
        orderRequest.setServletPath("/v2/orders");

        assertThat(filter.shouldNotFilter(authRequest)).isTrue();
        assertThat(filter.shouldNotFilter(actuatorRequest)).isTrue();
        assertThat(filter.shouldNotFilter(orderRequest)).isFalse();
    }

    @Test
    @DisplayName("Token 尚有餘額時放行，並設定 X-RateLimit-* headers")
    void allowedRequest_setsRateLimitHeadersAndContinuesChain() throws Exception {
        RateLimitFilter filter = newFilter();
        TenantContext.setCurrentTenant(UUID.randomUUID());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("1:37:0");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("37");
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("Token 已耗盡時回傳 429，含 Retry-After header 與 E_9904 錯誤內容，且不繼續呼叫 chain")
    void deniedRequest_returns429WithRetryAfterAndStopsChain() throws Exception {
        RateLimitFilter filter = newFilter();
        TenantContext.setCurrentTenant(UUID.randomUUID());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("0:0:600");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
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
        RateLimitFilter filter = newFilter();
        TenantContext.setCurrentTenant(UUID.randomUUID());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis script 回傳 null（如測試環境將 RedisConnectionFactory 整個 mock 掉）時 fail-open，不拋 NPE")
    void redisScriptReturnsNull_failsOpenAndContinuesChain() throws Exception {
        RateLimitFilter filter = newFilter();
        TenantContext.setCurrentTenant(UUID.randomUUID());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("StringRedisTemplate bean 不存在（如 @WebMvcTest 窄切片測試未載入 RedisAutoConfiguration）時直接放行")
    void noRedisTemplateBean_skipsRateLimitEntirely() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new FixedObjectProvider<>(null));
        TenantContext.setCurrentTenant(UUID.randomUUID());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
