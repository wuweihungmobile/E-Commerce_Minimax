package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * LoginRateLimitFilter 真 Redis Token Bucket 整合測試（Sprint 168，DEF-220）。
 *
 * <p>比照 {@link RateLimitFilterIntegrationTest} 的手法，刻意不用 {@code @SpringBootTest} +
 * {@code IntegrationTestConfiguration}，改直接連線 {@code make test-db-up} 啟動的真實 Redis。
 *
 * <p>需求：執行前須先 {@code make test-db-up}（啟動 postgres:5432 + redis:6379）。
 */
@DisplayName("IT-LOGIN-RATELIMIT: LoginRateLimitFilter 真 Redis Token Bucket 整合測試")
class LoginRateLimitFilterIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    /** 比照 RateLimitFilterIntegrationTest：token bucket 連續補充演算法對真實時脈敏感，
     *  改用「燒光配額後，接下來一小段 burst 內必然出現至少一次拒絕」驗證容量有被強制執行。 */
    private static final int BURST_AFTER_QUOTA = 5;

    private boolean sendRequest(final LoginRateLimitFilter filter, final String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        request.setServletPath("/v2/auth/login");
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;
        filter.doFilter(request, response, chain);
        return chainCalled[0];
    }

    private boolean anyDeniedWithinBurst(final LoginRateLimitFilter filter, final String remoteAddr,
                                          final int burstSize) throws Exception {
        for (int i = 0; i < burstSize; i++) {
            if (!sendRequest(filter, remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private static final int QUOTA = 30;

    @Test
    @DisplayName("同一來源 IP 燒光 30 次配額後，後續 burst 請求中必然出現拒絕（每分鐘 30 次配額）")
    void ipExhaustsQuota_subsequentBurstIsDenied() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new FixedObjectProvider<>(redisTemplate));
        String ip = "203.0.113." + (int) (Math.random() * 250);

        for (int i = 0; i < QUOTA; i++) {
            sendRequest(filter, ip);
        }

        assertThat(anyDeniedWithinBurst(filter, ip, BURST_AFTER_QUOTA))
                .as("耗盡 30 次配額後，緊接的 burst 請求中應至少出現一次拒絕")
                .isTrue();
    }

    @Test
    @DisplayName("不同來源 IP 的配額互相獨立，IP A 耗盡配額不影響 IP B")
    void differentIps_haveIndependentQuotas() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new FixedObjectProvider<>(redisTemplate));
        String ipA = "203.0.113." + (int) (Math.random() * 250);
        String ipB = "198.51.100." + (int) (Math.random() * 250);

        for (int i = 0; i < QUOTA; i++) {
            sendRequest(filter, ipA);
        }
        assertThat(anyDeniedWithinBurst(filter, ipA, BURST_AFTER_QUOTA))
                .as("IP A 應已耗盡配額")
                .isTrue();

        assertThat(sendRequest(filter, ipB)).as("IP B 的配額不受 IP A 影響").isTrue();
    }
}
