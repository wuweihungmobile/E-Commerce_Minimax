package com.nextkey.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分散式鎖服務
 * 用於預訂日曆的並發控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final long DEFAULT_LOCK_TIMEOUT = 30; // seconds
    private static final long LOCK_WAIT_TIMEOUT = 10; // seconds to wait for lock

    /**
     * 嘗試獲取鎖
     * @param resourceId 資源 ID（如 room listing id）
     * @return lockKey 如果成功，null 如果失敗
     */
    public String tryAcquireLock(String resourceId) {
        return tryAcquireLock(resourceId, DEFAULT_LOCK_TIMEOUT);
    }

    /**
     * 嘗試獲取鎖
     * @param resourceId 資源 ID
     * @param timeoutSeconds 鎖過期時間
     * @return lockKey 如果成功，null 如果失敗
     */
    public String tryAcquireLock(String resourceId, long timeoutSeconds) {
        String lockKey = LOCK_PREFIX + resourceId;
        String lockValue = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Duration.ofSeconds(timeoutSeconds)
        );

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: key={}, value={}", lockKey, lockValue);
            return lockValue;
        }

        log.debug("Lock not acquired: key={}", lockKey);
        return null;
    }

    /**
     * 釋放鎖
     * @param resourceId 資源 ID
     * @param lockValue 鎖的值（用於驗證）
     * @return 是否成功釋放
     */
    public boolean releaseLock(String resourceId, String lockValue) {
        String lockKey = LOCK_PREFIX + resourceId;
        Object currentValue = redisTemplate.opsForValue().get(lockKey);

        if (lockValue.equals(currentValue)) {
            Boolean deleted = redisTemplate.delete(lockKey);
            log.debug("Lock released: key={}", lockKey);
            return Boolean.TRUE.equals(deleted);
        }

        log.warn("Lock value mismatch, cannot release: key={}, expected={}, actual={}",
                lockKey, lockValue, currentValue);
        return false;
    }

    /**
     * 嘗試獲取鎖，最多等待一段時間
     * @param resourceId 資源 ID
     * @param maxWaitSeconds 最大等待時間
     * @return lockValue 如果成功，null 如果失敗
     */
    public String tryAcquireLockWithWait(String resourceId, long maxWaitSeconds) {
        long startTime = System.currentTimeMillis();
        long timeout = maxWaitSeconds * 1000;

        while (System.currentTimeMillis() - startTime < timeout) {
            String lockValue = tryAcquireLock(resourceId);
            if (lockValue != null) {
                return lockValue;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(100); // 等待 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        log.warn("Failed to acquire lock within {} seconds: {}", maxWaitSeconds, resourceId);
        return null;
    }

    /**
     * 檢查鎖是否存在
     */
    public boolean isLocked(String resourceId) {
        String lockKey = LOCK_PREFIX + resourceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    /**
     * 延長鎖的過期時間
     */
    public boolean extendLock(String resourceId, String lockValue, long additionalSeconds) {
        String lockKey = LOCK_PREFIX + resourceId;
        Object currentValue = redisTemplate.opsForValue().get(lockKey);

        if (lockValue.equals(currentValue)) {
            return Boolean.TRUE.equals(redisTemplate.expire(lockKey, Duration.ofSeconds(additionalSeconds)));
        }

        return false;
    }
}