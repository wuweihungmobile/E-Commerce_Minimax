package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nextkey.ecommerce.shared.constants.AppConstants;
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

    // ========== Sprint 234（DEF-328）：沒有店鋪的使用者不得合用一個限流桶 ==========
    //
    // 真實全棧 E2E 實測：系統租戶（所有一般買家、匿名請求、管理員共用的佔位租戶）的單一桶在兩三個並行 worker 下就被耗盡，
    // 後端日誌出現 `Tenant 0000…0001 exceeded rate limit`，管理員後台等不相干的流程一起收到 429。
    // 以下用 ArgumentCaptor 取出實際送進 Redis 的 key，斷言限流的「單位」。

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    /** 依序送出請求，回傳每一次實際送進 Redis script 的第一個 key（可在同一個測試內多次呼叫，每次只計本次的呼叫）。 */
    @SuppressWarnings("unchecked")
    private List<String> bucketKeysUsedFor(final MockHttpServletRequest... requests) throws Exception {
        clearInvocations(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("1:99:0");
        RateLimitFilter filter = newFilter();
        for (MockHttpServletRequest request : requests) {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        }
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(requests.length))
                .execute(any(RedisScript.class), keys.capture(), any(), any(), any(), any());
        return keys.getAllValues().stream().map(list -> list.get(0)).toList();
    }

    private static MockHttpServletRequest requestFrom(final String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/products");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    @DisplayName("Sprint 234：真正的店鋪租戶仍以租戶為限流單位（key 與使用者、來源 IP 無關）")
    void storeTenant_usesTenantBucket() throws Exception {
        UUID storeTenant = UUID.randomUUID();
        TenantContext.setCurrentTenant(storeTenant);
        TenantContext.setCurrentUser(UUID.randomUUID());

        List<String> keys = bucketKeysUsedFor(requestFrom("10.0.0.1"));

        assertThat(keys).containsExactly("ratelimit:tenant:" + storeTenant);
    }

    @Test
    @DisplayName("Sprint 234：系統租戶下，已登入的使用者各自一個桶，不共用系統租戶那一個")
    void systemTenant_authenticatedUsers_getTheirOwnBuckets() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        TenantContext.setCurrentUser(userA);
        List<String> keysA = bucketKeysUsedFor(requestFrom("10.0.0.1"));
        TenantContext.setCurrentUser(userB);
        List<String> keysB = bucketKeysUsedFor(requestFrom("10.0.0.1"));

        assertThat(keysA).containsExactly("ratelimit:user:" + userA);
        assertThat(keysB).containsExactly("ratelimit:user:" + userB);
        assertThat(keysA).doesNotContain("ratelimit:tenant:" + SYSTEM_TENANT_ID);
    }

    @Test
    @DisplayName("Sprint 234：系統租戶下的匿名請求以來源 IP 為單位：不同 IP 各自一個桶、同一 IP 共用")
    void systemTenant_anonymous_isBucketedBySourceIp() throws Exception {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);

        List<String> keys = bucketKeysUsedFor(requestFrom("10.0.0.1"), requestFrom("10.0.0.2"), requestFrom("10.0.0.1"));

        assertThat(keys).containsExactly("ratelimit:ip:10.0.0.1", "ratelimit:ip:10.0.0.2", "ratelimit:ip:10.0.0.1");
    }
}
