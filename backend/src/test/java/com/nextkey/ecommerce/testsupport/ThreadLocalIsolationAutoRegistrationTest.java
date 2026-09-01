package com.nextkey.ecommerce.testsupport;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 {@link ThreadLocalIsolationExtension} 確實被 JUnit Platform 自動偵測註冊並生效。
 *
 * <p>{@code ThreadLocalIsolationExtensionTest} 只證明「直接呼叫這個擴充會清乾淨」，
 * 但那不能證明它**真的被註冊**——若 {@code junit-platform.properties} 的
 * {@code autodetection} 設定或 {@code META-INF/services} 註冊檔遺失／打錯字，
 * 擴充會安靜地完全不生效，而所有既有測試依然全綠（因為它們本來就自己清 context）。
 * {@code reuseForks=true} 之下這個無聲失效正是最危險的情況。
 *
 * <p>做法：第一個測試方法**故意**留下未清理的 {@link TenantContext}，
 * 第二個方法斷言它已經是乾淨的。兩個方法之間唯一會清理的就是本擴充的
 * {@code afterEach}，所以擴充一旦沒被註冊，第二個方法立刻失敗。
 *
 * <p>刻意用「同一類別內的方法順序」而非「跨類別順序」來驗證：
 * 前者由 {@code @TestMethodOrder} 保證，與 surefire 的 {@code runOrder} 無關，
 * 不會因日後有人調整執行順序而悄悄失去守衛作用。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ThreadLocalIsolationExtension 自動註冊驗證")
class ThreadLocalIsolationAutoRegistrationTest {

    private static final UUID LEAKED_TENANT = UUID.fromString("00000000-0000-0000-0000-0000000104ff");

    @Test
    @Order(1)
    @DisplayName("故意留下未清理的 TenantContext")
    void deliberatelyLeaksTenantContext() {
        TenantContext.setCurrentTenant(LEAKED_TENANT);

        assertThat(TenantContext.getCurrentTenant()).isEqualTo(LEAKED_TENANT);
        // 刻意不清理——下一個測試方法負責證明擴充有把它清掉
    }

    @Test
    @Order(2)
    @DisplayName("下一個測試方法看到的是乾淨的 TenantContext（擴充確實已註冊）")
    void nextTestSeesCleanTenantContext() {
        assertThat(TenantContext.getCurrentTenant())
                .as("若此處為 %s，代表 ThreadLocalIsolationExtension 沒有被自動偵測註冊——"
                        + "檢查 junit-platform.properties 與 META-INF/services 註冊檔", LEAKED_TENANT)
                .isNull();
    }
}
