package com.nextkey.ecommerce.testsupport;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 全域 ThreadLocal 隔離擴充。
 *
 * <p>每個測試方法結束後、以及每個測試類別結束後，清除
 * {@link TenantContext} 與 Spring Security {@code SecurityContext} 兩組 ThreadLocal。
 *
 * <p><b>為什麼需要它：</b>surefire / failsafe 設為 {@code reuseForks=true} 後，
 * 同一個 JVM（同一條執行緒）會依序跑多個測試類別，任何未清除的 ThreadLocal
 * 都會洩漏給下一個類別。本專案原本以 {@code reuseForks=false} 迴避此問題
 * ——每類獨立 JVM，但代價是每類都得付一次完整的 Spring context 冷啟動。
 *
 * <p>絕大多數測試已自行在 {@code @AfterEach} 呼叫 clear，唯一的結構性缺口是
 * {@code WithErpSecurity} 的 {@code WithSecurityContextFactory}：它在建立
 * SecurityContext 時順帶設定 {@code TenantContext}，而 Spring Security 的
 * {@code WithSecurityContextTestExecutionListener} 只認得 SecurityContext，
 * 不會、也無從清理 {@code TenantContext}。
 *
 * <p>與其逐一稽核 138 個測試類別並仰賴日後每個新測試都記得清理，
 * 這裡以單一全域擴充讓洩漏在結構上不可能發生。
 *
 * <p>註冊方式為 JUnit Platform 自動偵測（見 {@code junit-platform.properties}
 * 與 {@code META-INF/services/org.junit.jupiter.api.extension.Extension}），
 * 因此適用於全部測試，不需要每個類別加 {@code @ExtendWith}。
 *
 * <p>執行順序：Jupiter 的 {@code AfterEachCallback} 在使用者自訂的
 * {@code @AfterEach} 方法之後才執行，故不會影響既有 teardown 對 context 的斷言或使用。
 */
public class ThreadLocalIsolationExtension implements AfterEachCallback, AfterAllCallback {

    @Override
    public void afterEach(final ExtensionContext context) {
        clearThreadLocals();
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        clearThreadLocals();
    }

    private static void clearThreadLocals() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        // DEF-269：測試可用 BusinessTime.useClockForTesting 固定「現在」；同一 JVM 循序跑全部測試，
        // 故比照 ThreadLocal 由此全域擴充統一還原，讓時鐘洩漏在結構上不可能發生。
        BusinessTime.resetClock();
    }
}
