package com.nextkey.ecommerce.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 唯一約束競態輸了之後，把「整個交易」丟掉並在全新的交易裡重做一次（DEF-441）。
 *
 * <p>為什麼不能在同一個交易裡恢復：PostgreSQL 在交易內任何一個語句失敗後，整個交易進入中止狀態
 * （{@code current transaction is aborted}），之後的任何語句都會失敗；而且例外穿出 repository 的交易代理時，外層交易
 * 也被標成 rollback-only。所以「捕捉 {@link DataIntegrityViolationException} 之後在同一個交易裡改讀、重算、重試」
 * 永遠不成立（{@code DEF-310} 早記過同一個陷阱）。mock Repository 沒有交易中止語意，所以單元測試看不到。
 *
 * <p>唯一可行的恢復方式是整個重做：重做時贏家的列已經提交，「是否已存在」的檢查看得到它（READ COMMITTED 每個語句各自一份
 * 快照），或重新計算單號時就會避開它。適用於「重做一次結果必然不同」的場景（以唯一索引當最終仲裁者的建立流程）；
 * 重做必須是冪等的——失敗的那次交易整個回滾，沒有任何殘留。
 *
 * <p>限制：呼叫端自己必須是最外層，也就是呼叫時沒有進行中的交易。已在交易內時外層交易已被中止，無法重做，
 * 所以只執行一次、例外原樣往外拋（行為與沒有這個元件時相同）。
 */
@Slf4j
@Component
public class UniqueConflictRetry {

    /** 含第一次在內最多執行幾次；用完仍輸才把唯一約束違反往外拋（連續輸給不同對手，實務上不會發生）。 */
    static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactionTemplate;

    public UniqueConflictRetry(final PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 在新的交易裡執行 {@code work}；若因唯一約束違反失敗，整個交易回滾後在另一個新交易裡重做。
     * 其他例外不重試，原樣往外拋（交易照常回滾）。
     */
    public <T> T execute(final Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return transactionTemplate.execute(status -> work.get());
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Unique constraint race lost, re-running the whole transaction: attempt={}/{}, cause={}",
                        attempt, MAX_ATTEMPTS, e.getMostSpecificCause().getMessage());
            }
        }
    }
}
