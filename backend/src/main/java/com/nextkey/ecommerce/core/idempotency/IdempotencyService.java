package com.nextkey.ecommerce.core.idempotency;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotency-Key 服務
 * 確保 HTTP POST 操作的冪等性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /**
     * 檢查並標記 Idempotency-Key
     * @return true 如果是新請求（已標記），false 如果是重複請求（已存在）
     */
    public boolean checkAndMark(final String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, "PROCESSING", IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(result)) {
            log.info("Idempotency key marked as PROCESSING: {}", idempotencyKey);
            return true; // 新請求
        }

        log.info("Idempotency key already exists: {}", idempotencyKey);
        return false; // 重複請求
    }

    /**
     * 標記 Idempotency-Key 為完成狀態並儲存回應
     */
    public void markCompleted(final String idempotencyKey, final Object response) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, response, IDEMPOTENCY_TTL);
        log.info("Idempotency key marked as COMPLETED: {}", idempotencyKey);
    }

    /**
     * 取得已儲存的回應（用於重複請求）
     */
    @SuppressWarnings("unchecked")
    public <T> T getStoredResponse(final String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 檢查 Idempotency-Key 是否仍在處理中
     */
    public boolean isStillProcessing(final String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        Object value = redisTemplate.opsForValue().get(key);
        return "PROCESSING".equals(value);
    }

    /**
     * 刪除 Idempotency-Key（用於錯誤恢復）
     */
    public void remove(final String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.delete(key);
        log.info("Idempotency key removed: {}", idempotencyKey);
    }

    /**
     * 驗證 UUID v4 格式
     */
    public boolean isValidUuidV4(final String key) {
        if (key == null || key.length() != 36) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(key);
            // UUID v4 的 version 必須是 4
            return uuid.version() == 4;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}