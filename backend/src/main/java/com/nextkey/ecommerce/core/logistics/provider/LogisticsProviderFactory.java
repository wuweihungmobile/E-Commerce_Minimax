package com.nextkey.ecommerce.core.logistics.provider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LogisticsProviderFactory {

    private final Map<String, LogisticsProvider> providers;

    public LogisticsProviderFactory(List<LogisticsProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(LogisticsProvider::getProviderCode, p -> p));
        log.info("LogisticsProviderFactory initialized with providers: {}", providers.keySet());
    }

    public LogisticsProvider getProvider(String code) {
        LogisticsProvider provider = providers.get(code);
        if (provider == null) {
            throw new BusinessException(ErrorCode.E_7503, "Unknown logistics provider: " + code);
        }
        return provider;
    }
}
