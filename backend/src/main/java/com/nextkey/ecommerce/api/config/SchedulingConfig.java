package com.nextkey.ecommerce.api.config;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 啟用 {@code @Scheduled}（Sprint 219，DEF-305）。
 *
 * <p>全專案在這之前從未有過 {@code @EnableScheduling}：Spring 不會自行處理 {@code @Scheduled}，所以每週一的結算單
 * （{@code SettlementGenerator}）、通知佇列的消費與重試（{@code NotificationConsumerService}）都從未自動執行。
 * 沒有任何測試或環境曾驗證「排程真的有被註冊」，這個缺口因此存活到第 219 個 Sprint。
 *
 * <p>{@code app.scheduling.enabled}（預設 true，環境變數 {@code APP_SCHEDULING_ENABLED}）是總開關：整合測試 profile
 * 關掉它，避免背景任務碰到測試資料或 mock 的 Redis；緊急時也可在正式環境關閉。多個後端實例同時執行是安全的——
 * 佇列消費用 Redis 原子 pop、未付款訂單取消與結算單生成各有 CAS／冪等檢查。
 *
 * <p>使用專屬的執行緒池，不共用 WebSocket 的 {@code messageBrokerTaskScheduler}：專案有 STOMP 設定時，Spring Boot 不會再建立
 * 自己的 {@code taskScheduler}，{@code @Scheduled} 會落到 STOMP 的排程器上（冒煙測試實測：執行緒名為
 * {@code MessageBroker-N}，池大小＝CPU 核數，容器限 1 核時只有 1 條）。通知消費者每輪最多阻塞 1 秒、週結算可能跑很久，
 * 共用會拖慢 WebSocket 心跳。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig implements SchedulingConfigurer {

    /** 專屬排程器的執行緒名前綴；測試以它確認排程任務確實跑在這個池上。 */
    public static final String THREAD_NAME_PREFIX = "app-scheduler-";

    @Value("${app.scheduling.pool-size:4}")
    private int poolSize;

    private ThreadPoolTaskScheduler scheduler;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar registrar) {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        // 關閉時不等排程任務跑完：消費者的阻塞式 pop 最多 1 秒，其餘任務各有冪等保護，重跑安全
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }

    @PreDestroy
    void shutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
