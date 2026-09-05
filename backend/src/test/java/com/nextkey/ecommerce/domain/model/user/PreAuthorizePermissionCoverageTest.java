package com.nextkey.ecommerce.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * 防復發守門：@PreAuthorize 引用的權限碼必須真的存在於 {@link Permission} 枚舉（Sprint 128，DEF-073）。
 *
 * <p><b>為什麼需要這支測試</b>：生產環境唯一的授權來源是
 * {@code JwtAuthenticationFilter} → {@code RolePermissionMapping.getAuthorities(role)}，
 * 它只會發出 {@code "ROLE_"+role}、{@code role.name()}，以及 <b>Permission 枚舉裡實際存在的</b>
 * 權限碼。因此只要 {@code @PreAuthorize("hasAuthority('x:y')")} 的 {@code x:y} 不在枚舉裡，
 * 該端點對<b>所有角色（含 SUPER_ADMIN，因為它是 EnumSet.allOf(Permission.class)）</b>永遠回 403。
 *
 * <p><b>為什麼過去抓不到</b>：整合測試以兩種方式繞過真實授權——
 * (1) {@code IntegrationTestConfiguration} 用手寫清單 spy 掉 {@code RolePermissionMapping}；
 * (2) 測試直接寫 {@code @WithMockUser(authorities = {"knowledge:read"})} 偽造權限字串。
 * 兩者都讓「生產永遠 403」的端點在測試中呈現綠燈。
 */
@DisplayName("@PreAuthorize 權限碼涵蓋率守門（DEF-073）")
class PreAuthorizePermissionCoverageTest {

    private static final String CONTROLLER_PACKAGE = "com.nextkey.ecommerce.api.controller";

    /** 擷取 hasAuthority('x') / hasAnyAuthority('x','y') 內的所有單引號字串。 */
    private static final Pattern AUTHORITY_CALL =
            Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    /**
     * 已知尚未修復的孤兒權限碼（DEF-075 已於 Sprint 129 修復，清單保留為空集合以持續守住不再新增）。
     *
     * <p>這些碼同樣「任何角色都拿不到 → 端點永遠 403」。此清單存在的目的
     * 是讓守門測試能對<b>新增</b>的孤兒碼失敗，同時不隱藏既有債務——清單本身就是債務清冊，
     * 修掉一個就從這裡移除一個，不允許只增不減。
     */
    private static final Set<String> KNOWN_UNMAPPED_PENDING_DEF_075 = new TreeSet<>();

    /**
     * 有 SUPER_ADMIN fallback 的孤兒碼：註解形如
     * {@code @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:read')")}。
     *
     * <p>與上面那份清單性質<b>不同</b>——SUPER_ADMIN 仍可到達這些端點，故不是「必定 403」；
     * 真正的問題是「除了 SUPER_ADMIN 以外沒有任何角色到得了」，究竟是刻意的平台專屬設計、
     * 還是漏掉授權，需要產品判斷。列在此處是為了讓<b>新增</b>的同類碼會讓測試失敗。
     *
     * <p>原本的 5 個碼（cms:read/create/update/publish、notification:create）已於 Sprint 130
     * （DEF-092）判定為「忘記授權」並修復，清單保留為空集合以持續守住不再新增同類孤兒碼。
     */
    private static final Set<String> SUPER_ADMIN_FALLBACK_ONLY = new TreeSet<>();

    @Test
    @DisplayName("所有 @PreAuthorize 引用的權限碼都存在於 Permission 枚舉（已知待修者除外）")
    void everyReferencedAuthorityCodeIsDefined() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Reference> unmapped = new LinkedHashMap<>();
        for (Map.Entry<String, Reference> entry : collectReferencedAuthorities().entrySet()) {
            String code = entry.getKey();
            Reference reference = entry.getValue();
            if (defined.contains(code)
                    || reference.hasSuperAdminFallback()
                    || KNOWN_UNMAPPED_PENDING_DEF_075.contains(code)) {
                continue;
            }
            unmapped.put(code, reference);
        }

        assertThat(unmapped)
                .as("這些權限碼沒有任何角色拿得到（含 SUPER_ADMIN，因為它是 EnumSet.allOf(Permission.class)），"
                        + "端點必定 403。請在 Permission 枚舉新增並於 RolePermissionMapping 授予對應角色")
                .isEmpty();
    }

    @Test
    @DisplayName("帶 SUPER_ADMIN fallback 的孤兒碼不得新增（既有 5 個已登記待判斷）")
    void superAdminFallbackOnlyCodesAreUnchanged() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        Set<String> actual = collectReferencedAuthorities().entrySet().stream()
                .filter(e -> !defined.contains(e.getKey()))
                .filter(e -> e.getValue().hasSuperAdminFallback())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(actual)
                .as("除 SUPER_ADMIN 外無人可用的權限碼有變動；新增請先確認是刻意的平台專屬設計")
                .isEqualTo(SUPER_ADMIN_FALLBACK_ONLY);
    }

    @Test
    @DisplayName("notification_template 四個權限碼已納入枚舉（DEF-073 本體）")
    void notificationTemplateCodesAreDefined() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        assertThat(defined).contains(
                "notification_template:read",
                "notification_template:create",
                "notification_template:update",
                "notification_template:delete");
    }

    @Test
    @DisplayName("dashboard/faq/knowledge/media 13 個權限碼已納入枚舉（DEF-075 本體，Sprint 129）")
    void def075CodesAreDefined() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        assertThat(defined).contains(
                "dashboard:read",
                "faq:read", "faq:create", "faq:update", "faq:delete",
                "knowledge:read", "knowledge:create", "knowledge:update", "knowledge:delete",
                "media:read", "media:create", "media:update", "media:delete");
    }

    @Test
    @DisplayName("cms:*/notification:create 5 個權限碼已納入枚舉（DEF-092 本體，Sprint 130）")
    void def092CodesAreDefined() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        assertThat(defined).contains(
                "cms:read", "cms:create", "cms:update", "cms:publish",
                "notification:create");
    }

    @Test
    @DisplayName("待修清單不得包含已修好的碼（避免清單只增不減而失去意義）")
    void pendingListContainsOnlyStillUnmappedCodes() {
        Set<String> defined = Arrays.stream(Permission.values())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        Set<String> alreadyFixed = KNOWN_UNMAPPED_PENDING_DEF_075.stream()
                .filter(defined::contains)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(alreadyFixed)
                .as("這些碼已經定義於 Permission 枚舉，請從 KNOWN_UNMAPPED_PENDING_DEF_075 移除")
                .isEmpty();
    }

    /** 一個權限碼的出現位置，以及該處註解是否留有 SUPER_ADMIN 的替代路徑。 */
    private record Reference(String location, boolean hasSuperAdminFallback) {
        @Override
        public String toString() {
            return location + (hasSuperAdminFallback ? "（有 SUPER_ADMIN fallback）" : "");
        }
    }

    /** 掃描所有 @RestController，回傳 權限碼 → 首次出現位置。 */
    private Map<String, Reference> collectReferencedAuthorities() {
        Map<String, Reference> found = new LinkedHashMap<>();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("無法載入 controller: " + definition.getBeanClassName(), e);
            }

            record(found, controller.getAnnotation(PreAuthorize.class), controller.getSimpleName());
            for (Method method : controller.getDeclaredMethods()) {
                record(found, method.getAnnotation(PreAuthorize.class),
                        controller.getSimpleName() + "#" + method.getName());
            }
        }
        return found;
    }

    private void record(Map<String, Reference> found, PreAuthorize annotation, String location) {
        if (annotation == null) {
            return;
        }
        boolean superAdminFallback = annotation.value().contains("hasRole('SUPER_ADMIN')")
                || annotation.value().contains("hasAuthority('ROLE_SUPER_ADMIN')");
        Matcher call = AUTHORITY_CALL.matcher(annotation.value());
        while (call.find()) {
            Matcher quoted = QUOTED.matcher(call.group(1));
            while (quoted.find()) {
                String code = quoted.group(1);
                // 角色名（無冒號，如 STORE_OWNER）由 getAuthorities 直接發出；
                // SCOPE_* 來自 OAuth2 resource server，皆非 Permission 枚舉的職責。
                if (code.contains(":") && !code.startsWith("SCOPE_")) {
                    found.putIfAbsent(code, new Reference(location, superAdminFallback));
                }
            }
        }
    }
}
