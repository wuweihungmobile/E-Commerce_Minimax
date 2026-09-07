package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextkey.ecommerce.core.admin.AdminService;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * 🔴 IT-DEF136-USER: 驗證 {@code User} 實體加上 {@code @DynamicUpdate} 後，
 * 「僅改 lastLoginAt 的整列 save()」（模擬 AuthService.login()）不會把併發轉換的
 * status（模擬 AdminService.updateUserStatus 停權）悄悄復原。
 *
 * <p>為什麼一定要真實 DB + 真執行緒：{@code @DynamicUpdate} 是否真的把 UPDATE 語句限縮成
 * 只含已變更欄位，是 Hibernate 產生 SQL 的實際行為，Mockito mock 掉 repository 完全測不到。
 * 且必須是「同一個交易內」先讀後寫（如同真正的 {@code login()} 方法本身，讀取與寫入共用同一個
 * persistence context），而非跨交易讀取後在新交易 merge 一個已 detached 的物件——後者即使有
 * {@code @DynamicUpdate}，merge 時仍會與資料庫當下值比較而正確偵測出差異，測不出這裡要驗證的
 * 「同一個 session 內的部分欄位髒檢查」行為。因此用兩條真執行緒 + {@link CountDownLatch}
 * 讓「login」執行緒的交易在讀取後暫停、直到「admin 停權」交易真正 commit 完成才恢復寫入。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-DEF136-USER: User @DynamicUpdate 防止 login() 併發覆寫 status")
class UserStatusRaceIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AdminService adminService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("login() 交易讀到停權前的舊快照，於停權 commit 後才 save(lastLoginAt) "
            + "-> status 應維持 SUSPENDED，不被復原為 ACTIVE")
    void loginStaleWrite_doesNotRevertConcurrentlyBannedStatus() throws Exception {
        long stamp = System.nanoTime();
        User seed = new TransactionTemplate(transactionManager).execute(status -> userRepository.save(User.builder()
                .email("racecheck-" + stamp + "@example.com")
                .passwordHash("irrelevant")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build()));
        UUID userId = seed.getId();

        CountDownLatch loginHasRead = new CountDownLatch(1);
        CountDownLatch banHasCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> loginTx = pool.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        // 模擬 AuthService.login()：同一個交易/persistence context 內先讀，
                        // 之後才 setLastLoginAt + save——與真正的 login() 方法結構一致。
                        User user = userRepository.findById(userId).orElseThrow();
                        loginHasRead.countDown();
                        awaitUninterruptibly(banHasCommitted);
                        user.setLastLoginAt(Instant.now());
                        userRepository.save(user);
                    }));

            Future<?> banTx = pool.submit(() -> {
                awaitUninterruptibly(loginHasRead);
                adminService.updateUserStatus(userId, "SUSPENDED");
                banHasCommitted.countDown();
            });

            banTx.get(10, TimeUnit.SECONDS);
            loginTx.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        User finalState = userRepository.findById(userId).orElseThrow();
        assertThat(finalState.getStatus())
                .as("@DynamicUpdate 應讓 login() 的 UPDATE 只含 last_login_at，不覆寫併發轉換的 status")
                .isEqualTo("SUSPENDED");
        assertThat(finalState.getLastLoginAt()).isNotNull();
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
