package com.nextkey.ecommerce.core.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * IdempotencyService 跨租戶隔離測試（Sprint 79 US-002，DEF-039）。
 *
 * <p>背景：{@code IdempotencyService} 的 Redis key 目前僅為
 * {@code "idempotency:" + idempotencyKey}，完全沒有依 {@link TenantContext} 做租戶/使用者範圍化。
 * {@code Idempotency-Key} 是由客戶端（{@code BookingController}）透過 HTTP header 提供的
 * UUID v4 字串，格式驗證（{@code isValidUuidV4}）僅檢查格式合法性，不具備任何機密性——不同
 * 租戶的客戶端完全可能（不論是巧合、UUID 產生器熵不足、或用戶端重放）送出相同的
 * Idempotency-Key。屬與既有 {@code RedisCartService.getCartKey(userId, tenantId)} 同一類
 * 「Redis key 必須含租戶/使用者範圍」問題，但 {@code IdempotencyService} 未依此既有前例設計。
 *
 * <p>本測試使用 Mockito 搭配一個真實 {@link HashMap} 模擬 Redis 的 SETNX/GET/SET 語意
 * （而非單純 stub 固定回傳值），如此才能真實重現「同一把 key 在不同租戶情境下互相碰撞」的行為。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService 跨租戶隔離測試（Sprint 79，DEF-039）")
class IdempotencyServiceTenantIsolationTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final Map<String, Object> fakeRedisStore = new HashMap<>();

    private IdempotencyService idempotencyService;

    private void setUpFakeRedis() {
        // 用真實 Map 模擬 Redis SETNX/GET/SET 語意，才能重現跨租戶碰撞
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient()
                .when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    Object value = invocation.getArgument(1);
                    if (fakeRedisStore.containsKey(key)) {
                        return false;
                    }
                    fakeRedisStore.put(key, value);
                    return true;
                });
        org.mockito.Mockito.lenient()
                .when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> fakeRedisStore.get((String) invocation.getArgument(0)));
        org.mockito.Mockito.lenient()
                .doAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    Object value = invocation.getArgument(1);
                    fakeRedisStore.put(key, value);
                    return null;
                })
                .when(valueOperations)
                .set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(Duration.class));
        org.mockito.Mockito.lenient()
                .doAnswer(invocation -> fakeRedisStore.remove((String) invocation.getArgument(0)) != null)
                .when(redisTemplate)
                .delete(org.mockito.ArgumentMatchers.anyString());

        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("紅燈：不同租戶使用相同 Idempotency-Key，B 租戶會拿到 A 租戶儲存的回應（跨租戶資料洩漏）")
    void checkAndMark_sameKeyDifferentTenant_mustNotLeakStoredResponse() {
        setUpFakeRedis();

        UUID tenantA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String sameIdempotencyKey = UUID.randomUUID().toString();
        String tenantASecretResponse = "TENANT_A_BOOKING_RESPONSE";

        // 租戶 A 先建立一筆訂單，完成後把回應存入 idempotency store
        TenantContext.setCurrentTenant(tenantA);
        TenantContext.setCurrentUser(userA);
        boolean firstMark = idempotencyService.checkAndMark(sameIdempotencyKey);
        idempotencyService.markCompleted(sameIdempotencyKey, tenantASecretResponse);
        assertThat(firstMark).isTrue();

        // 租戶 B（完全不同的租戶與使用者）湊巧/重放送出「相同」的 Idempotency-Key
        TenantContext.setCurrentTenant(tenantB);
        TenantContext.setCurrentUser(userB);
        boolean secondMark = idempotencyService.checkAndMark(sameIdempotencyKey);
        Object responseSeenByTenantB = idempotencyService.getStoredResponse(sameIdempotencyKey);

        // 修復後預期：租戶 B 視為全新請求（true），且看不到租戶 A 的回應（null）
        assertThat(secondMark)
                .as("租戶 B 使用相同 Idempotency-Key 應被視為全新請求，不應被租戶 A 的紀錄擋下")
                .isTrue();
        assertThat(responseSeenByTenantB)
                .as("租戶 B 絕對不可以看到租戶 A 的已儲存回應（跨租戶資料洩漏）")
                .isNotEqualTo(tenantASecretResponse);
    }

    @Test
    @DisplayName("同一租戶同一使用者重複送出相同 Idempotency-Key，仍應正確回傳已儲存的回應（既有冪等行為不可回歸）")
    void checkAndMark_sameTenantSameUserRepeat_stillReturnsStoredResponse() {
        setUpFakeRedis();

        UUID tenantA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String response = "BOOKING_RESPONSE";

        TenantContext.setCurrentTenant(tenantA);
        TenantContext.setCurrentUser(userA);
        idempotencyService.checkAndMark(key);
        idempotencyService.markCompleted(key, response);

        // 同一租戶、同一使用者重複呼叫（模擬客戶端重試）
        boolean repeatMark = idempotencyService.checkAndMark(key);
        Object storedResponse = idempotencyService.getStoredResponse(key);

        assertThat(repeatMark).isFalse();
        assertThat(storedResponse).isEqualTo(response);
    }
}
