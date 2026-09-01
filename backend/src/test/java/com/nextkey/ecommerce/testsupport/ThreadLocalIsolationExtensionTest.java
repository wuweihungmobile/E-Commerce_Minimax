package com.nextkey.ecommerce.testsupport;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.integration.WithErpSecurity;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ThreadLocalIsolationExtension} 的行為測試。
 *
 * <p>這個擴充是 surefire / failsafe {@code reuseForks=true} 的前置條件：
 * 同一個 JVM 依序跑多個測試類別時，任何殘留的 ThreadLocal 都會洩漏給下一個類別。
 * 因此這裡測的不只是「clear 有沒有被呼叫」，而是**為什麼需要它**——
 * 見 {@link #withErpSecurityFactorySetsTenantContextItNeverCleans()}。
 */
class ThreadLocalIsolationExtensionTest {

    private final ThreadLocalIsolationExtension extension = new ThreadLocalIsolationExtension();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("afterEach 清除 TenantContext 與 SecurityContext")
    void afterEachClearsBothThreadLocals() {
        givenBothContextsAreSet();

        extension.afterEach(null);

        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentUser()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("afterAll 清除 TenantContext 與 SecurityContext（類別層級的最後一道防線）")
    void afterAllClearsBothThreadLocals() {
        givenBothContextsAreSet();

        extension.afterAll(null);

        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentUser()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * 記錄本擴充存在的理由：{@code WithErpSecurity} 的 factory 在建立 SecurityContext 時
     * 順帶寫入 {@link TenantContext}，但 Spring Security 的
     * {@code WithSecurityContextTestExecutionListener} 只負責還原 SecurityContext，
     * 對 {@code TenantContext} 一無所知，因此這組 ThreadLocal 沒有任何人會清。
     *
     * <p>在 {@code reuseForks=false} 時由 JVM 邊界擋住；改成 {@code reuseForks=true} 後，
     * 唯一的防線就是本擴充。若日後有人想移除它，這個測試說明會壞掉什麼。
     */
    @Test
    @DisplayName("WithErpSecurity factory 會寫入 TenantContext 且不負責清理——這是本擴充存在的理由")
    void withErpSecurityFactorySetsTenantContextItNeverCleans() {
        TenantContext.clear();
        WithErpSecurity annotation = defaultWithErpSecurity();

        new WithErpSecurity.WithErpSecurityContextFactory().createSecurityContext(annotation);

        assertThat(TenantContext.getCurrentTenant())
                .as("factory 設定了 TenantContext，且沒有任何 listener 會還原它")
                .isEqualTo(UUID.fromString(annotation.tenantId()));

        extension.afterEach(null);

        assertThat(TenantContext.getCurrentTenant())
                .as("本擴充是唯一會清掉它的地方")
                .isNull();
    }

    private void givenBothContextsAreSet() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        TenantContext.setCurrentUser(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", null));
    }

    /** 以 annotation 預設值建立實例，避免依賴任何測試類別上的宣告。 */
    private static WithErpSecurity defaultWithErpSecurity() {
        return Holder.class.getAnnotation(WithErpSecurity.class);
    }

    @WithErpSecurity
    private static final class Holder { }
}
