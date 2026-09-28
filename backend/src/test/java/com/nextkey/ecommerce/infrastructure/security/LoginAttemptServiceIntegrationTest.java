package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Sprint 214（DEF-293）：登入嘗試計數的 Lua script 在真實 Redis 上的語意。
 *
 * <p>單元測試用 mock 的 {@code StringRedisTemplate}，看不到 script 實際做了什麼；計數的原子性、TTL 是否設定、
 * 是否被延長、遺留的無 TTL key 會不會自行補上，都只有真實 Redis 才驗證得了。
 * 需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-LOGIN-ATTEMPT: 登入嘗試計數 script 的 Redis 語意（真 Redis）")
class LoginAttemptServiceIntegrationTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60;
    private static final int CONCURRENT_REQUESTS = 16;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private LoginAttemptService service;
    private String email;
    private String key;

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

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(redisTemplate);
        email = "attempt-it-" + UUID.randomUUID() + "@example.com";
        key = "login_attempt:" + email;
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("依序計入：前 5 次不鎖定、第 6 次起鎖定")
    void sequentialAttemptsLockOnSixth() {
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            assertThat(service.countAttemptAndCheckLocked(email)).as("第 %d 次嘗試", i).isFalse();
        }
        assertThat(service.countAttemptAndCheckLocked(email)).as("第 6 次嘗試").isTrue();
        assertThat(service.countAttemptAndCheckLocked(email)).as("第 7 次嘗試").isTrue();
    }

    @Test
    @DisplayName("第一次計入就設定 15 分鐘 TTL")
    void firstAttemptSetsFifteenMinuteTtl() {
        service.countAttemptAndCheckLocked(email);

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).as("TTL 秒數").isBetween(WINDOW_SECONDS - 5, WINDOW_SECONDS);
    }

    @Test
    @DisplayName("後續計入不延長窗口：鎖定窗口從第一次嘗試起算，攻擊者不能靠持續嘗試讓鎖定永不結束")
    void subsequentAttemptsDoNotExtendWindow() {
        service.countAttemptAndCheckLocked(email);
        redisTemplate.expire(key, 100, TimeUnit.SECONDS);

        service.countAttemptAndCheckLocked(email);

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).as("TTL 秒數（不可被重設回 900）").isLessThanOrEqualTo(100L);
    }

    @Test
    @DisplayName("遺留的無 TTL 計數 key 會在下一次計入時補上 TTL，不會變成永久鎖定")
    void keyStrandedWithoutTtlGetsTtlOnNextAttempt() {
        // 舊版「先 INCR、再另一個指令 EXPIRE」若在兩者之間失敗（連線中斷、程序被終止），
        // 就會留下永不過期的計數；達門檻後帳號連正確密碼都無法登入，且永遠沒有機會歸零。
        redisTemplate.opsForValue().set(key, "3");
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).as("前置條件：key 沒有 TTL").isEqualTo(-1L);

        service.countAttemptAndCheckLocked(email);

        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).as("計入後應補上 TTL")
                .isBetween(WINDOW_SECONDS - 5, WINDOW_SECONDS);
    }

    @Test
    @DisplayName("resetAttempts 歸零後，計數重新從 1 開始：先前已鎖定的帳號解除")
    void resetAttemptsClearsLock() {
        for (int i = 0; i <= MAX_ATTEMPTS; i++) {
            service.countAttemptAndCheckLocked(email);
        }
        assertThat(service.countAttemptAndCheckLocked(email)).as("前置條件：已鎖定").isTrue();

        service.resetAttempts(email);

        assertThat(service.countAttemptAndCheckLocked(email)).as("歸零後的第 1 次").isFalse();
    }

    @Test
    @DisplayName("16 個執行緒同時計入：恰好 5 個未鎖定、11 個鎖定（每輪重複 20 次）")
    void concurrentCountingAllowsExactlyThresholdAttempts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int round = 0; round < 20; round++) {
                redisTemplate.delete(key);
                CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_REQUESTS);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                    Callable<Boolean> task = () -> {
                        startLine.await();
                        return service.countAttemptAndCheckLocked(email);
                    };
                    futures.add(pool.submit(task));
                }
                int notLocked = 0;
                for (Future<Boolean> future : futures) {
                    if (!future.get()) {
                        notLocked++;
                    }
                }
                assertThat(notLocked).as("round %d：未鎖定的請求數", round).isEqualTo(MAX_ATTEMPTS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
