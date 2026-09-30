package com.nextkey.ecommerce.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.nextkey.ecommerce.infrastructure.mq.RedisStreamConfig;

/**
 * 全專案共用的 {@code RedisTemplate<String, Object>} 必須能序列化 {@code java.time}，且不能由「誰先被註冊」決定
 * （Sprint 223，DEF-313）。
 *
 * <p>背景：{@code RedisConfig} 與 {@code RedisStreamConfig} 各自宣告了一個同名的 {@code redisTemplate} bean，
 * 前者的 ObjectMapper 有 {@code JavaTimeModule}，後者沒有。{@code application.yml} 開了
 * {@code allow-bean-definition-overriding}（2026-06-12 為了讓「bean 重複」的啟動錯誤消失），兩個定義因此靜默互相覆蓋、
 * 後註冊的贏。目錄式 classpath（IDE、{@code mvn test}）按名稱排序掃描，{@code RedisConfig} 後註冊而勝出，所以開發與測試
 * 一切正常；打包成 JAR 後順序取決於 JAR 內項目的排列，實測 {@code RedisStreamConfig} 後註冊而勝出，購物車加入商品
 * （{@code Instant}）與帶冪等鍵的訂房（{@code LocalDate}）都以 500 收場。
 *
 * <p>這個測試把兩種註冊順序都跑一次，不依賴 JAR 的實際順序：兩種順序下取到的 {@code redisTemplate} 都必須能存取 java.time。
 * 全庫的整合測試把 Redis 整個換成 mock，看不到這件事。
 */
@DisplayName("DEF-313: redisTemplate 只能有一個，且要能序列化 java.time")
class RedisTemplateBeanWiringTest {

    /** 與 RedisCartService.CartItemData／冪等鍵儲存的回應同型：非 final 的 POJO，欄位是 LocalDate 與 Instant。 */
    public static class TimedPayload {
        private LocalDate date;
        private Instant at;

        public TimedPayload() {
        }

        TimedPayload(final LocalDate date, final Instant at) {
            this.date = date;
            this.at = at;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(final LocalDate date) {
            this.date = date;
        }

        public Instant getAt() {
            return at;
        }

        public void setAt(final Instant at) {
            this.at = at;
        }
    }

    @Test
    @DisplayName("RedisConfig 先註冊、RedisStreamConfig 後註冊（打包成 JAR 時實測的順序）→ 仍能存取 java.time")
    void redisConfigRegisteredFirst_templateStillHandlesJavaTime() {
        assertTemplateHandlesJavaTime(RedisConfig.class, RedisStreamConfig.class);
    }

    @Test
    @DisplayName("RedisStreamConfig 先註冊、RedisConfig 後註冊（目錄式 classpath 的順序）→ 能存取 java.time")
    void redisStreamConfigRegisteredFirst_templateStillHandlesJavaTime() {
        assertTemplateHandlesJavaTime(RedisStreamConfig.class, RedisConfig.class);
    }

    @SuppressWarnings("unchecked")
    private static void assertTemplateHandlesJavaTime(final Class<?>... configurations) {
        new ApplicationContextRunner()
                // 缺陷出貨時 application.yml 就是這個設定：同名 bean 靜默覆蓋，不會在啟動時報錯
                .withAllowBeanDefinitionOverriding(true)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withUserConfiguration(configurations)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisTemplate<String, Object> template = context.getBean("redisTemplate", RedisTemplate.class);
                    RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();

                    TimedPayload original = new TimedPayload(LocalDate.of(2026, 10, 30),
                            Instant.parse("2026-09-30T10:46:16Z"));
                    Object restored = serializer.deserialize(serializer.serialize(original));

                    assertThat(restored).isInstanceOf(TimedPayload.class);
                    TimedPayload payload = (TimedPayload) restored;
                    assertThat(payload.getDate()).isEqualTo(original.getDate());
                    assertThat(payload.getAt()).isEqualTo(original.getAt());
                });
    }
}
