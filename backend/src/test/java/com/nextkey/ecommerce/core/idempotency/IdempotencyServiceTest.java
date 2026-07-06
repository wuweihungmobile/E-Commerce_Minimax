package com.nextkey.ecommerce.core.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * IdempotencyService 單元測試（Sprint 79 US-002，多 Sprint 測試強化計劃）。
 *
 * <p>涵蓋全部 6 個 public 方法的一般行為；跨租戶隔離的紅燈→綠燈驗證另見
 * {@link IdempotencyServiceTenantIsolationTest}（DEF-039）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService 單元測試（Sprint 79）")
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private IdempotencyService idempotencyService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("checkAndMark：Redis 不存在該 key 時回傳 true 並以 24 小時 TTL 標記 PROCESSING")
    void checkAndMark_newKey_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), eq(Duration.ofHours(24))))
                .thenReturn(true);

        boolean result = idempotencyService.checkAndMark("key-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkAndMark：Redis 已存在該 key 時回傳 false")
    void checkAndMark_existingKey_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);

        boolean result = idempotencyService.checkAndMark("key-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("markCompleted：以 24 小時 TTL 儲存回應內容")
    void markCompleted_storesResponseWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.markCompleted("key-1", "response-body");

        verify(valueOperations).set(anyString(), eq("response-body"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("getStoredResponse：回傳 Redis 中儲存的內容")
    void getStoredResponse_returnsStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("response-body");

        String result = idempotencyService.getStoredResponse("key-1");

        assertThat(result).isEqualTo("response-body");
    }

    @Test
    @DisplayName("isStillProcessing：值為 PROCESSING 時回傳 true")
    void isStillProcessing_whenProcessing_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("PROCESSING");

        assertThat(idempotencyService.isStillProcessing("key-1")).isTrue();
    }

    @Test
    @DisplayName("isStillProcessing：值非 PROCESSING（已完成或不存在）時回傳 false")
    void isStillProcessing_whenNotProcessing_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(idempotencyService.isStillProcessing("key-1")).isFalse();
    }

    @Test
    @DisplayName("remove：刪除 Redis 中對應的 key")
    void remove_deletesKey() {
        idempotencyService.remove("key-1");

        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("isValidUuidV4：合法的 UUID v4 回傳 true")
    void isValidUuidV4_validUuidV4_returnsTrue() {
        String validUuidV4 = UUID.randomUUID().toString();

        assertThat(idempotencyService.isValidUuidV4(validUuidV4)).isTrue();
    }

    @Test
    @DisplayName("isValidUuidV4：null 回傳 false")
    void isValidUuidV4_null_returnsFalse() {
        assertThat(idempotencyService.isValidUuidV4(null)).isFalse();
    }

    @Test
    @DisplayName("isValidUuidV4：長度不符回傳 false")
    void isValidUuidV4_wrongLength_returnsFalse() {
        assertThat(idempotencyService.isValidUuidV4("not-a-uuid")).isFalse();
    }

    @Test
    @DisplayName("isValidUuidV4：非 v4 版本的 UUID（如 v1）回傳 false")
    void isValidUuidV4_nonV4Version_returnsFalse() {
        // UUID v1 範例（version nibble 為 1）
        String uuidV1 = "550e8400-e29b-11d4-a716-446655440000";

        assertThat(idempotencyService.isValidUuidV4(uuidV1)).isFalse();
    }

    @Test
    @DisplayName("isValidUuidV4：格式合法但含非法字元時回傳 false")
    void isValidUuidV4_malformed_returnsFalse() {
        String malformed = "zzzzzzzz-zzzz-4zzz-zzzz-zzzzzzzzzzzz";

        assertThat(idempotencyService.isValidUuidV4(malformed)).isFalse();
    }
}
