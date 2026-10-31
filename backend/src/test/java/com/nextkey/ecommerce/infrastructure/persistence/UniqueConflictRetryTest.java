package com.nextkey.ecommerce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * {@link UniqueConflictRetry} 的行為契約（DEF-441）。真實 PostgreSQL 上「輸了的那次交易確實中止、重做的交易確實看得到贏家」
 * 由 {@code UniqueConstraintRaceRealStackIntegrationTest} 守；這裡守的是元件本身的重做規則：什麼情況重做、做幾次、什麼情況不做。
 */
@DisplayName("UniqueConflictRetry: 唯一約束競態輸了 → 整個交易回滾並在新交易重做（DEF-441）")
class UniqueConflictRetryTest {

    private PlatformTransactionManager transactionManager;
    private UniqueConflictRetry retry;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(inv -> new SimpleTransactionStatus());
        retry = new UniqueConflictRetry(transactionManager);
    }

    @AfterEach
    void clearTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("沒有衝突：執行一次、提交一次，回傳工作的結果")
    void noConflict_runsOnceAndCommits() {
        String result = retry.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    @DisplayName("第一次輸給唯一約束：第一個交易整個回滾，在第二個新交易重做並提交（兩次是各自獨立的交易）")
    void lostOnce_rollsBackTheFirstTransactionAndRerunsInANewOne() {
        AtomicInteger runs = new AtomicInteger();

        String result = retry.execute(() -> {
            if (runs.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("duplicate key");
            }
            return "second";
        });

        assertThat(result).isEqualTo("second");
        assertThat(runs.get()).isEqualTo(2);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }

    @Test
    @DisplayName("連續輸滿 3 次：不再重做，唯一約束違反原樣往外拋；每一次都是各自回滾的交易，沒有任何一次提交")
    void lostEveryTime_givesUpAfterMaxAttemptsAndRethrows() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            runs.incrementAndGet();
            throw new DataIntegrityViolationException("duplicate key");
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(runs.get()).isEqualTo(UniqueConflictRetry.MAX_ATTEMPTS);
        verify(transactionManager, times(UniqueConflictRetry.MAX_ATTEMPTS)).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("不是唯一約束違反的例外：不重做，原樣往外拋（業務錯誤不該被重試掩蓋）")
    void otherException_isNotRetried() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            runs.incrementAndGet();
            throw new BusinessException(ErrorCode.E_4001, "boom");
        })).isInstanceOf(BusinessException.class);

        assertThat(runs.get()).isEqualTo(1);
        verify(transactionManager, times(1)).rollback(any());
    }

    @Test
    @DisplayName("呼叫時已在交易內：外層交易已被中止，無法重做 → 只執行一次，唯一約束違反原樣往外拋（不假裝能恢復）")
    void alreadyInsideATransaction_doesNotRetry() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            runs.incrementAndGet();
            throw new DataIntegrityViolationException("duplicate key");
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(runs.get()).isEqualTo(1);
    }
}
