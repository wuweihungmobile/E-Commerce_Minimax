package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.nextkey.ecommerce.infrastructure.security.AccountTokenService.Purpose;

/**
 * Sprint 204：一次性連結 token 的真 Redis 整合測試（FRD BR-M03-003）。
 *
 * <p>比照 {@code LoginRateLimitFilterIntegrationTest}：不啟動 Spring context，直接連 {@code make test-db-up}
 * 啟動的真實 Redis。需求：執行前須先 {@code make test-db-up}。
 *
 * <p>為什麼要真 Redis：「一次性」「原子單次消耗」「TTL」「不存原文」全是 Redis 行為，用 mock 的 RedisTemplate
 * 只能驗證「我呼叫了 getAndDelete」，驗證不了「兩個併發請求只有一個成功」。
 */
@DisplayName("IT-ACCOUNT-TOKEN: 一次性連結 token 真 Redis 整合測試")
class AccountTokenServiceIntegrationTest {

    private static final int CONCURRENT_CONSUMERS = 16;
    private static final long SECONDS_PER_MINUTE = 60L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static AccountTokenService service;

    private final List<UUID> usersToClean = new ArrayList<>();

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        service = new AccountTokenService(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @AfterEach
    void cleanUp() {
        for (UUID userId : usersToClean) {
            service.invalidate(Purpose.PASSWORD_RESET, userId);
            service.invalidate(Purpose.EMAIL_VERIFY, userId);
            redisTemplate.delete("account_token_cooldown:" + Purpose.PASSWORD_RESET + ":" + userId);
            redisTemplate.delete("account_token_cooldown:" + Purpose.EMAIL_VERIFY + ":" + userId);
        }
        usersToClean.clear();
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        usersToClean.add(userId);
        return userId;
    }

    @Test
    @DisplayName("簽發後可消耗一次，回傳所屬會員；同一個 token 第二次一律無效")
    void issuedTokenIsSingleUse() {
        UUID userId = newUser();
        String token = service.issue(Purpose.PASSWORD_RESET, userId);

        assertThat(service.consume(Purpose.PASSWORD_RESET, token)).contains(userId);
        assertThat(service.consume(Purpose.PASSWORD_RESET, token)).isEmpty();
    }

    @Test
    @DisplayName("16 個執行緒同時消耗同一個 token，恰好 1 個成功（原子單次消耗，不是「先查再刪」）")
    void concurrentConsumeSucceedsExactlyOnce() throws Exception {
        UUID userId = newUser();
        String token = service.issue(Purpose.PASSWORD_RESET, userId);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_CONSUMERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_CONSUMERS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Optional<UUID>>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_CONSUMERS; i++) {
                Callable<Optional<UUID>> task = () -> {
                    ready.countDown();
                    go.await();
                    return service.consume(Purpose.PASSWORD_RESET, token);
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();

            long successes = 0;
            for (Future<Optional<UUID>> future : futures) {
                if (future.get().isPresent()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("重新簽發會使先前的 token 作廢：同一（用途, 會員）同時只有一個有效連結")
    void issuingAgainInvalidatesPreviousToken() {
        UUID userId = newUser();
        String first = service.issue(Purpose.PASSWORD_RESET, userId);
        String second = service.issue(Purpose.PASSWORD_RESET, userId);

        assertThat(service.consume(Purpose.PASSWORD_RESET, first)).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, second)).contains(userId);
    }

    @Test
    @DisplayName("用途互相隔離：重設密碼的 token 不能拿來做 Email 驗證，反之亦然；且不互相作廢")
    void purposesAreIsolated() {
        UUID userId = newUser();
        String resetToken = service.issue(Purpose.PASSWORD_RESET, userId);
        String verifyToken = service.issue(Purpose.EMAIL_VERIFY, userId);

        assertThat(service.consume(Purpose.EMAIL_VERIFY, resetToken)).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, verifyToken)).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, resetToken)).contains(userId);
        assertThat(service.consume(Purpose.EMAIL_VERIFY, verifyToken)).contains(userId);
    }

    @Test
    @DisplayName("Redis 裡不存 token 原文（只存雜湊），且每個 key 都有 TTL 不超過該用途的有效期")
    void rawTokenIsNeverStored_andEveryKeyExpires() {
        UUID userId = newUser();
        String resetToken = service.issue(Purpose.PASSWORD_RESET, userId);
        String verifyToken = service.issue(Purpose.EMAIL_VERIFY, userId);

        Set<String> keys = redisTemplate.keys("account_token*:*" + userId + "*");
        keys.addAll(redisTemplate.keys("account_token:*"));
        for (String key : keys) {
            assertThat(key).doesNotContain(resetToken).doesNotContain(verifyToken);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                assertThat(value).doesNotContain(resetToken).doesNotContain(verifyToken);
            }
        }

        // 我們自己簽發的兩個 token 各對應一個 token key 與一個 latest 指標，逐一驗證 TTL
        Set<String> resetKeys = redisTemplate.keys("account_token*:PASSWORD_RESET:*");
        Set<String> verifyKeys = redisTemplate.keys("account_token*:EMAIL_VERIFY:*");
        assertThat(resetKeys).isNotEmpty();
        assertThat(verifyKeys).isNotEmpty();
        for (String key : resetKeys) {
            assertThat(redisTemplate.getExpire(key)).isBetween(1L, Purpose.PASSWORD_RESET.getTtl().toSeconds());
        }
        for (String key : verifyKeys) {
            assertThat(redisTemplate.getExpire(key)).isBetween(1L, Purpose.EMAIL_VERIFY.getTtl().toSeconds());
        }
        assertThat(Purpose.PASSWORD_RESET.getTtl().toSeconds()).isEqualTo(30 * SECONDS_PER_MINUTE);
    }

    @Test
    @DisplayName("寄信冷卻：同一（用途, 會員）第一次可寄、之後不可；換會員或換用途各自獨立")
    void sendCooldownIsPerPurposeAndUser() {
        UUID userA = newUser();
        UUID userB = newUser();

        assertThat(service.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userA)).isTrue();
        assertThat(service.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userA)).isFalse();
        assertThat(service.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userB)).isTrue();
        assertThat(service.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userA)).isTrue();
    }

    @Test
    @DisplayName("invalidate 使目前有效的 token 作廢")
    void invalidateRevokesCurrentToken() {
        UUID userId = newUser();
        String token = service.issue(Purpose.PASSWORD_RESET, userId);

        service.invalidate(Purpose.PASSWORD_RESET, userId);

        assertThat(service.consume(Purpose.PASSWORD_RESET, token)).isEmpty();
    }

    @Test
    @DisplayName("垃圾輸入（null、空白、過長、亂數）一律回空，不拋例外")
    void garbageInputIsRejectedQuietly() {
        assertThat(service.consume(Purpose.PASSWORD_RESET, null)).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, "")).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, "   ")).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, "x".repeat(129))).isEmpty();
        assertThat(service.consume(Purpose.PASSWORD_RESET, UUID.randomUUID().toString())).isEmpty();
    }
}
