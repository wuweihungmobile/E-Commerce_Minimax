package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.core.booking.RoomCalendarService;

/**
 * 日期鎖對真實 Redis 的釋放（Sprint 243，DEF-343）。
 *
 * <p>{@code RoomCalendarService.unlockDateRange} 原本把已帶 {@code lock:} 前綴的鍵傳給 {@code RedisLockService.forceReleaseLock}，
 * 後者自己會再加一次前綴，刪掉的是不存在的 {@code lock:lock:room:…}：日期鎖從來沒被釋放，要等 60 秒 TTL 才消失。
 * 實際後果是預訂建立後 60 秒內，同房源同日期無法再被訂（取消後重訂、改期到重疊日期都回 E-4001「Date range is being modified
 * by another user」）。它存活到 Sprint 243 的原因：{@code RoomCalendarServiceTest} 以 mock 的 {@code RedisLockService} 驗證，
 * 而且<b>期望的就是錯的鍵</b>（測試把缺陷固定成預期）；整合測試的 {@code IntegrationTestConfiguration} 又把 {@code RedisLockService}
 * 換成 mock。只有真實 Redis 看得到鍵有沒有真的被刪。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-CAL-LOCK: 日期鎖對真實 Redis 的取得與釋放（Sprint 243，DEF-343）")
class RoomCalendarLockRealRedisIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2031, 6, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2031, 6, 13); // 3 晚

    @Autowired private RoomCalendarService roomCalendarService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private final UUID roomId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        roomCalendarService.unlockDateRange(roomId, CHECK_IN, CHECK_OUT, null);
    }

    private String dateKey(final LocalDate date) {
        return "lock:room:" + roomId + ":date:" + date;
    }

    @Test
    @DisplayName("鎖定時每一晚在 Redis 都有一個鍵；unlockDateRange 之後這些鍵都真的被刪掉（原本還在，要等 60 秒）")
    void unlock_actuallyDeletesTheDateLocks() {
        String lock = roomCalendarService.lockDateRange(roomId, CHECK_IN, CHECK_OUT);

        assertThat(lock).isNotNull();
        for (LocalDate night = CHECK_IN; night.isBefore(CHECK_OUT); night = night.plusDays(1)) {
            assertThat(redisTemplate.hasKey(dateKey(night))).as("鎖定後 %s 有鎖", night).isTrue();
        }

        roomCalendarService.unlockDateRange(roomId, CHECK_IN, CHECK_OUT, lock);

        for (LocalDate night = CHECK_IN; night.isBefore(CHECK_OUT); night = night.plusDays(1)) {
            assertThat(redisTemplate.hasKey(dateKey(night))).as("解鎖後 %s 的鎖要消失", night).isFalse();
        }
        assertThat(roomCalendarService.lockDateRange(roomId, CHECK_IN, CHECK_OUT))
                .as("解鎖後立刻可以再鎖同一段日期").isNotNull();
    }

    @Test
    @DisplayName("部分日期取鎖失敗時，已取得的鎖也被真的釋放（原本回滾的是不存在的鍵）")
    void partialFailure_releasesTheLocksAlreadyAcquired() {
        // 第三晚已被別人鎖住：前兩晚取到、第三晚失敗，必須把前兩晚放掉
        String thirdNight = dateKey(CHECK_OUT.minusDays(1));
        redisTemplate.opsForValue().set(thirdNight, "someone-else", java.time.Duration.ofSeconds(30));
        try {
            assertThat(roomCalendarService.lockDateRange(roomId, CHECK_IN, CHECK_OUT)).isNull();

            assertThat(redisTemplate.hasKey(dateKey(CHECK_IN))).as("第一晚的鎖已回滾").isFalse();
            assertThat(redisTemplate.hasKey(dateKey(CHECK_IN.plusDays(1)))).as("第二晚的鎖已回滾").isFalse();
            assertThat(redisTemplate.hasKey(thirdNight)).as("別人的鎖不能被動到").isTrue();
        } finally {
            redisTemplate.delete(thirdNight);
        }
    }
}
