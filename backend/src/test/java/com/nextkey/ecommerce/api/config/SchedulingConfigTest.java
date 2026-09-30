package com.nextkey.ecommerce.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 排程必須真的被啟用（Sprint 219，DEF-305）。
 *
 * <p>全專案曾經從未有過 {@code @EnableScheduling}，三個 {@code @Scheduled} 從第一個 commit 起就沒有自動執行過，
 * 而沒有任何測試會發現——它們的單元／整合測試都是直接呼叫方法。這裡守三件事：開關的行為、每個 {@code @Scheduled}
 * 方法所在的類別都是會被 Spring 掃描到的元件、以及已知的排程沒有悄悄被拿掉。
 */
@DisplayName("DEF-305: 排程必須真的被啟用")
class SchedulingConfigTest {

    private static final String SCHEDULING_PROCESSOR_BEAN =
            "org.springframework.context.annotation.internalScheduledAnnotationProcessor";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    @DisplayName("沒有設定開關 → 預設啟用（正式環境不需要額外設定）")
    void enabledByDefault() {
        runner.run(context -> assertThat(context).hasBean(SCHEDULING_PROCESSOR_BEAN)
                .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("app.scheduling.enabled=true → 啟用")
    void enabledWhenExplicitlyTrue() {
        runner.withPropertyValues("app.scheduling.enabled=true")
                .run(context -> assertThat(context).hasBean(SCHEDULING_PROCESSOR_BEAN));
    }

    @Test
    @DisplayName("app.scheduling.enabled=false → 不註冊任何排程處理器（整合測試 profile 與緊急關閉用）")
    void disabledWhenFalse() {
        runner.withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SCHEDULING_PROCESSOR_BEAN));
    }

    /** 探針：一個帶 @Scheduled 的元件，記下執行它的執行緒名稱。 */
    static class TickProbe {
        static final List<String> THREADS = new CopyOnWriteArrayList<>();
        static final CountDownLatch RAN = new CountDownLatch(1);

        @Scheduled(fixedDelay = 50)
        public void tick() {
            THREADS.add(Thread.currentThread().getName());
            RAN.countDown();
        }
    }

    @Test
    @DisplayName("排程任務真的會被執行，而且跑在專屬的 app-scheduler 池上（不是 WebSocket 的 MessageBroker 池）")
    void scheduledTasksActuallyRunOnDedicatedPool() throws InterruptedException {
        TickProbe.THREADS.clear();
        runner.withBean(TickProbe.class).run(context -> {
            assertThat(TickProbe.RAN.await(10, TimeUnit.SECONDS)).as("排程任務在 10 秒內至少執行一次").isTrue();
            assertThat(TickProbe.THREADS).isNotEmpty()
                    .allSatisfy(name -> assertThat(name).startsWith(SchedulingConfig.THREAD_NAME_PREFIX));
        });
    }

    @Test
    @DisplayName("app.scheduling.enabled=false → 同樣的探針完全不會被執行")
    void scheduledTasksDoNotRunWhenDisabled() {
        TickProbe.THREADS.clear();
        runner.withPropertyValues("app.scheduling.enabled=false").withBean(TickProbe.class).run(context -> {
            Thread.sleep(300);
            assertThat(TickProbe.THREADS).isEmpty();
        });
    }

    @Test
    @DisplayName("SchedulingConfig 本身是 Spring 掃得到的 @Configuration（@SpringBootApplication 的元件掃描會載入它）")
    void configIsDiscoverableByComponentScan() {
        assertThat(scanComponentClassNames()).contains(SchedulingConfig.class.getName());
        assertThat(AnnotatedElementUtils.hasAnnotation(SchedulingConfig.class, Configuration.class)).isTrue();
    }

    @Test
    @DisplayName("每個 @Scheduled 方法所在的類別都是會被掃描到的元件——否則即使啟用排程也不會註冊")
    void everyScheduledMethodLivesInAScannedComponent() throws ClassNotFoundException {
        Set<String> components = scanComponentClassNames();
        List<String> orphans = new ArrayList<>();
        for (String className : scanAllClassNames()) {
            Class<?> type = loadWithoutInit(className);
            if (hasScheduledMethod(type) && !components.contains(className)) {
                orphans.add(className);
            }
        }
        assertThat(orphans).as("有 @Scheduled 方法卻不是 Spring 元件的類別，排程永遠不會執行").isEmpty();
    }

    @Test
    @DisplayName("已知的排程都還在（誰拿掉排程，這裡要先紅）")
    void knownScheduledJobsAreStillDeclared() throws ClassNotFoundException {
        Set<String> declared = new TreeSet<>();
        for (String className : scanAllClassNames()) {
            Class<?> type = loadWithoutInit(className);
            for (Method method : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)) {
                    declared.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertThat(declared).contains(
                "SettlementGenerator.generateWeeklyStatements",
                "NotificationConsumerService.consumeNotifications",
                "NotificationConsumerService.processRetryQueue",
                "OrderTimeoutService.cancelExpiredUnpaidOrders",
                "BookingTimeoutService.cancelExpiredUnpaidBookings");
    }

    private static Class<?> loadWithoutInit(final String className) throws ClassNotFoundException {
        return Class.forName(className, false, SchedulingConfigTest.class.getClassLoader());
    }

    private static boolean hasScheduledMethod(final Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> scanComponentClassNames() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        Set<String> names = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.nextkey.ecommerce")) {
            names.add(definition.getBeanClassName());
        }
        return names;
    }

    /**
     * 主程式所有類別（含非元件），用來找出「有 @Scheduled 卻不是元件」的漏網之魚。
     * 排除 target/test-classes 下的類別（例如本檔的探針），它們不是正式程式。
     */
    private static Set<String> scanAllClassNames() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    final org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        Set<String> names = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.nextkey.ecommerce")) {
            if (!isFromTestClasses(definition)) {
                names.add(definition.getBeanClassName());
            }
        }
        return names;
    }

    private static boolean isFromTestClasses(final BeanDefinition definition) {
        if (definition instanceof org.springframework.context.annotation.ScannedGenericBeanDefinition scanned
                && scanned.getResource() != null) {
            try {
                return scanned.getResource().getURL().toString().contains("/test-classes/");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return false;
    }
}
