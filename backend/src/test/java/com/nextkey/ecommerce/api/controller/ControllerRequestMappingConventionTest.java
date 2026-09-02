package com.nextkey.ecommerce.api.controller;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller 路徑前綴慣例守衛（Sprint 109，DEF-061）。
 *
 * <p><b>為什麼需要這個測試</b>：{@code application.yml} 設定
 * {@code server.servlet.context-path: /api}，所有 controller 的 mapping 都是**相對於**
 * 該 context path。若 class-level {@code @RequestMapping} 自己再寫一次 {@code /api}，
 * 端點就會實際落在 {@code /api/api/v2/**}，而前端（axios baseURL 已含 {@code /api}）
 * 打的是 {@code /api/v2/**} —— 兩者永遠對不上。
 *
 * <p><b>為什麼既有測試抓不到</b>：MockMvc **不套用** {@code server.servlet.context-path}，
 * 所以 {@code TenantControllerE2ETest} 等測試沿用 {@code "/api/v2"} 當 base URL 時一路全綠，
 * 真實部署卻是 404/401。Sprint 109 實測確認：修復前 {@code POST /api/api/v2/tenants/apply}
 * 回 201、{@code /api/v2/tenants/apply} 回 401（與不存在的路徑同一個回應），修復後完全反轉。
 * M17 開店申請長期送不出去、所有 M17 E2E 長期 skip，根因都在這裡。
 *
 * <p>這個守衛是**唯一**能在不啟動真實 HTTP 埠的情況下擋住此類回歸的層級：
 * 它驗證的是意圖（「mapping 相對於 context-path，不可自帶 /api」），而非某支端點的行為。
 */
@DisplayName("Controller 路徑慣例：class-level @RequestMapping 不可自帶 /api 前綴")
class ControllerRequestMappingConventionTest {

    private static final String CONTROLLER_PACKAGE = "com.nextkey.ecommerce.api.controller";

    /** 與 application.yml 的 server.servlet.context-path 一致。 */
    private static final String CONTEXT_PATH = "/api";

    @Test
    @DisplayName("所有 controller 的 class-level mapping 都不以 /api 開頭（否則實際路徑會變成 /api/api/**）")
    void noControllerMappingDuplicatesTheServletContextPath() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> offenders = new ArrayList<>();

        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> controller;
            try {
                controller = Class.forName(className);
            } catch (ClassNotFoundException e) {
                continue;
            }

            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            for (String path : mapping.value()) {
                if (path.equals(CONTEXT_PATH) || path.startsWith(CONTEXT_PATH + "/")) {
                    offenders.add(controller.getSimpleName() + " -> \"" + path + "\"");
                }
            }
        }

        assertThat(offenders)
                .as("這些 controller 的 mapping 自帶了 context-path（%s），實際端點會落在 %s%s/**，"
                        + "前端打不到。請把 class-level @RequestMapping 改成相對路徑（如 \"/v2\"）。"
                        + "注意 MockMvc 不套用 context-path，所以既有 E2E 測試不會失敗——"
                        + "這正是 DEF-061 長期未被發現的原因。",
                        CONTEXT_PATH, CONTEXT_PATH, CONTEXT_PATH)
                .isEmpty();
    }

    @Test
    @DisplayName("守衛本身有效：掃描確實找到了 controller（避免掃不到而空過）")
    void scannerActuallyFindsControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        // 若 package 改名或掃描設定失效，上面那個測試會「因為沒東西可檢查」而恆綠——
        // 這正是本專案反覆遇到的「測試以固件繞過同一段邏輯」失效模式，故明確守住下界。
        assertThat(scanner.findCandidateComponents(CONTROLLER_PACKAGE))
                .as("應掃描到 controller；掃不到代表守衛失效而非通過")
                .hasSizeGreaterThan(20);
    }
}
