package com.nextkey.ecommerce.api.filter;

import org.springframework.beans.factory.ObjectProvider;

/**
 * 測試用最小 {@link ObjectProvider} 實作：包一個固定值，供 {@link RateLimitFilter} 這類以
 * {@code ObjectProvider} 表達可選依賴的建構子在不啟動 Spring context 的情況下做單元/整合測試。
 */
final class FixedObjectProvider<T> implements ObjectProvider<T> {

    private final T value;

    FixedObjectProvider(final T value) {
        this.value = value;
    }

    @Override
    public T getObject() {
        return value;
    }

    @Override
    public T getObject(final Object... args) {
        return value;
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public T getIfUnique() {
        return value;
    }
}
