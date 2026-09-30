package com.nextkey.ecommerce.infrastructure.mq;

import org.springframework.context.annotation.Configuration;

/**
 * 通知佇列的 Redis key 常數。
 *
 * <p>Sprint 223（DEF-313）：這裡原本還宣告了第二個 {@code redisTemplate} bean，與 {@code RedisConfig} 的同名，
 * 而且序列化器沒有 {@code JavaTimeModule}。{@code allow-bean-definition-overriding} 讓兩者靜默互相覆蓋、後註冊的贏，
 * 打包成 JAR 後贏的是這個——購物車加入商品與帶冪等鍵的訂房都因為 java.time 序列化失敗而回 500。
 * 已刪除：全專案只有 {@code RedisConfig} 的那一個 {@code RedisTemplate<String, Object>}（通知服務只用 List／Value 操作，
 * 不需要另一個序列化器；它們的真實 Redis 測試本來就是用 {@code RedisConfig} 的 template）。
 */
@Configuration
public class RedisStreamConfig {

    public static final String NOTIFICATION_STREAM = "notification:stream";
    public static final String NOTIFICATION_CONSUMER_GROUP = "notification-group";
    public static final String NOTIFICATION_CONSUMER = "notification-consumer";
}