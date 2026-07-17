package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * RateLimitFilter 真 Redis Token Bucket 整合測試（Sprint 93，PRD §3.3/§13.4/§16.4.2）。
 *
 * <p>刻意不用 {@code @SpringBootTest} + {@code IntegrationTestConfiguration}：後者將
 * {@code RedisConnectionFactory}/{@code RedisTemplate} 標為 {@code @Primary} mock（供其他不需要真
 * Redis 語意的整合測試使用），若沿用會讓本測試看不到真實的 Lua script 補充/扣除行為，等於沒測到
 * 東西。改為直接建立指向 {@code make test-db-up} 啟動的真實 Redis（localhost:6379）的
 * {@link LettuceConnectionFactory}，不需要完整 Spring context / Postgres。
 *
 * <p>需求：執行前須先 {@code make test-db-up}（啟動 postgres:5432 + redis:6379）。
 */
@DisplayName("IT-RATELIMIT: RateLimitFilter 真 Redis Token Bucket 整合測試")
class RateLimitFilterIntegrationTest {

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 超出容量的額外請求數：因 token bucket 為連續補充，100 次連續呼叫本身耗費的真實時間
     *  就會補充回一點點 token（非 bug，是連續補充演算法的預期行為），故不斷言「恰好第 101 次」
     *  這種對真實時脈敏感的邊界，改用「燒光 100 配額後，接下來一小段 burst 內必然出現至少
     *  一次拒絕」來驗證容量真的有被強制執行，避免測試因執行速度快慢而 flaky。 */
    private static final int BURST_AFTER_QUOTA = 20;

    private boolean sendRequest(final RateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;
        filter.doFilter(request, response, chain);
        return chainCalled[0];
    }

    private boolean anyDeniedWithinBurst(final RateLimitFilter filter, final int burstSize) throws Exception {
        for (int i = 0; i < burstSize; i++) {
            if (!sendRequest(filter)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("同一租戶燒光 100 次配額後，後續 burst 請求中必然出現拒絕（超過每分鐘 100 次配額）")
    void tenantExhaustsQuota_subsequentBurstIsDenied() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new FixedObjectProvider<>(redisTemplate));
        TenantContext.setCurrentTenant(UUID.randomUUID());

        for (int i = 0; i < 100; i++) {
            sendRequest(filter);
        }

        assertThat(anyDeniedWithinBurst(filter, BURST_AFTER_QUOTA))
                .as("耗盡 100 次配額後，緊接的 burst 請求中應至少出現一次拒絕")
                .isTrue();
    }

    @Test
    @DisplayName("不同租戶的配額互相獨立，租戶 A 耗盡配額不影響租戶 B")
    void differentTenants_haveIndependentQuotas() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new FixedObjectProvider<>(redisTemplate));
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantContext.setCurrentTenant(tenantA);
        for (int i = 0; i < 100; i++) {
            sendRequest(filter);
        }
        assertThat(anyDeniedWithinBurst(filter, BURST_AFTER_QUOTA))
                .as("租戶 A 應已耗盡配額")
                .isTrue();

        TenantContext.setCurrentTenant(tenantB);
        assertThat(sendRequest(filter)).as("租戶 B 的配額不受租戶 A 影響").isTrue();
    }
}
