package com.nextkey.ecommerce.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 正式程式裡不能有兩個 {@code @Bean} 取同一個 bean 名稱（Sprint 223，DEF-313）。
 *
 * <p>{@code application.yml} 曾為了讓「bean 重複」的啟動錯誤消失而開啟 {@code allow-bean-definition-overriding}，
 * 兩個同名的 {@code redisTemplate} 因此靜默互相覆蓋、誰贏取決於註冊順序（見 {@code RedisTemplateBeanWiringTest}）。
 * 該開關已拿掉，同名 bean 會直接讓應用程式啟動失敗；這個測試把同一件事提前到單元測試階段，並指出是哪兩個方法。
 *
 * <p>整合測試設定（{@code IntegrationTestConfiguration}）刻意用同名 bean 覆蓋正式 bean，屬於 test-classes，不在掃描範圍。
 */
@DisplayName("DEF-313: 正式程式的 @Bean 名稱不可重複")
class BeanNameUniquenessTest {

    @Test
    @DisplayName("全部正式程式的 @Bean 方法：沒有任何一個 bean 名稱被兩個方法宣告")
    void noBeanNameIsDeclaredTwice() throws ClassNotFoundException {
        List<Class<?>> mainClasses = new ArrayList<>();
        for (String className : scanMainClassNames()) {
            mainClasses.add(Class.forName(className, false, BeanNameUniquenessTest.class.getClassLoader()));
        }

        Map<String, List<String>> duplicates = duplicatedBeanNames(mainClasses);

        assertThat(duplicates)
                .as("同名 @Bean 會在 allow-bean-definition-overriding 下靜默互相覆蓋，誰贏取決於註冊順序")
                .isEmpty();
    }

    @Test
    @DisplayName("守門自證：兩個 @Bean 方法取同名時，確實會被找出來（避免這支測試變成永遠綠的空殼）")
    void detectorFindsADuplicateWhenThereIsOne() {
        Map<String, List<String>> duplicates = duplicatedBeanNames(List.of(FirstConfig.class, SecondConfig.class));

        assertThat(duplicates).containsOnlyKeys("sharedName");
        assertThat(duplicates.get("sharedName")).hasSize(2);
    }

    @Test
    @DisplayName("守門自證：@Bean 明確指定的名稱也算數（name 與方法名取同一個名稱就是重複）")
    void detectorHonoursExplicitBeanNames() {
        Map<String, List<String>> duplicates = duplicatedBeanNames(List.of(FirstConfig.class, ExplicitNameConfig.class));

        assertThat(duplicates).containsOnlyKeys("sharedName");
    }

    /** 探針：以方法名 sharedName 宣告 bean。 */
    @Configuration
    static class FirstConfig {
        @Bean
        String sharedName() {
            return "first";
        }
    }

    /** 探針：另一個類別用同一個方法名宣告 bean。 */
    @Configuration
    static class SecondConfig {
        @Bean
        String sharedName() {
            return "second";
        }
    }

    /** 探針：方法名不同，但 @Bean(name = ...) 指到同一個名稱。 */
    @Configuration
    static class ExplicitNameConfig {
        @Bean(name = "sharedName")
        String somethingElse() {
            return "third";
        }
    }

    /** 回傳「被兩個以上方法宣告」的 bean 名稱 → 宣告者清單（類別.方法）。 */
    private static Map<String, List<String>> duplicatedBeanNames(final Collection<Class<?>> types) {
        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (Class<?> type : types) {
            for (Method method : type.getDeclaredMethods()) {
                Bean bean = AnnotatedElementUtils.findMergedAnnotation(method, Bean.class);
                if (bean == null) {
                    continue;
                }
                String[] names = bean.name().length > 0 ? bean.name() : bean.value();
                String[] effective = names.length > 0 ? names : new String[] {method.getName()};
                // 一個 @Bean 可有多個別名，第一個是 bean 名稱；別名撞名不在這裡處理
                owners.computeIfAbsent(effective[0], key -> new ArrayList<>())
                        .add(type.getName() + "." + method.getName());
            }
        }
        owners.values().removeIf(declaredBy -> declaredBy.size() < 2);
        return owners;
    }

    /** 正式程式所有類別的名稱（排除 test-classes 底下的測試設定與本檔的探針）。 */
    private static Set<String> scanMainClassNames() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(final AnnotatedBeanDefinition beanDefinition) {
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
        if (definition instanceof ScannedGenericBeanDefinition scanned && scanned.getResource() != null) {
            try {
                return scanned.getResource().getURL().toString().contains("/test-classes/");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return false;
    }
}
