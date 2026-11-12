package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 操作者接觸的那一端：<b>真正的 {@code application.yml} ＋ 環境變數 {@code APP_TRUSTED_PROXY_HOPS}</b> 是否真的決定限流用的來源 IP
 * （Sprint 341，DEF-501）。
 *
 * <p>{@link ClientIpResolverTest} 與過濾器的單元測試都直接 {@code new} 出物件、手動傳層數，看不到「yml 的鍵名寫錯」
 * 或「{@code @Value} 與 yml 對不上」——那種錯誤的後果是參數靜默失效（永遠是 0），操作者設了 1 卻什麼都沒發生，
 * 所有訪客照舊共用代理的限流桶。這裡用 Spring 載入真正的 {@code application.yml}，以系統屬性模擬環境變數
 * （yml 裡的 {@code ${APP_TRUSTED_PROXY_HOPS:0}} 對兩者一視同仁），驗證從設定到來源 IP 的整條路。
 */
@DisplayName("ClientIpWiringTest：APP_TRUSTED_PROXY_HOPS 經 application.yml 決定限流的來源 IP（Sprint 341）")
class ClientIpWiringTest {

    private static final String PROXY = "10.0.0.9";
    private static final String VISITOR = "203.0.113.7";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(LoginRateLimitFilter.class)
            .withBean(RateLimitFilter.class);

    /** 經過代理的一次登入請求（對端是代理、X-Forwarded-For 是訪客），回傳登入限流桶實際用的 Redis key。 */
    @SuppressWarnings("unchecked")
    private static String loginBucketKey(final org.springframework.context.ApplicationContext context) throws Exception {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn("1:7:0");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", VISITOR);

        context.getBean(LoginRateLimitFilter.class).doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any(), any(), any(), any());
        return keys.getValue().get(0);
    }

    @Test
    @DisplayName("沒設環境變數（預設）：來源 IP 是對端位址，X-Forwarded-For 不採信——部署現況不變")
    void unset_keepsPeerAddress() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(loginBucketKey(context)).isEqualTo("ratelimit:auth_ip:/v2/auth/login:" + PROXY);
        });
    }

    @Test
    @DisplayName("APP_TRUSTED_PROXY_HOPS=1：登入限流改用代理附加的訪客 IP（yml 鍵名與 @Value 對得上，參數沒有靜默失效）")
    void oneHop_usesForwardedVisitor() {
        runner.withSystemProperties("APP_TRUSTED_PROXY_HOPS=1").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(loginBucketKey(context)).isEqualTo("ratelimit:auth_ip:/v2/auth/login:" + VISITOR);
        });
    }

    @Test
    @DisplayName("匿名限流（RateLimitFilter）讀的是同一個參數：設 1 之後也改用訪客 IP")
    void sameParameterDrivesAnonymousRateLimit() {
        runner.withSystemProperties("APP_TRUSTED_PROXY_HOPS=1").run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn("1:99:0");
            com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(
                    java.util.UUID.fromString(com.nextkey.ecommerce.shared.constants.AppConstants.SYSTEM_TENANT_ID));
            try {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/products");
                request.setRemoteAddr(PROXY);
                request.addHeader("X-Forwarded-For", VISITOR);

                context.getBean(RateLimitFilter.class).doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
                verify(redis).execute(any(RedisScript.class), keys.capture(), any(), any(), any(), any());
                assertThat(keys.getValue().get(0)).isEqualTo("ratelimit:ip:" + VISITOR);
            } finally {
                com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
            }
        });
    }

    @Test
    @DisplayName("設成超出範圍（11）或不是數字：應用程式啟動就失敗，不是默默退回 0 讓操作者以為生效了")
    void invalidValue_failsStartup() {
        runner.withSystemProperties("APP_TRUSTED_PROXY_HOPS=11").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("APP_TRUSTED_PROXY_HOPS");
        });
        runner.withSystemProperties("APP_TRUSTED_PROXY_HOPS=abc").run(context -> assertThat(context).hasFailed());
    }
}
