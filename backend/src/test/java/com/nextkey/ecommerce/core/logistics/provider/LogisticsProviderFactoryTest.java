package com.nextkey.ecommerce.core.logistics.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * LogisticsProviderFactory 單元測試（US-003）
 *
 * 測試範圍：
 * - TC-L001: getProvider("HCT") 回傳 HCTLogisticsProvider
 * - TC-L002: getProvider("TCAT") 回傳 TCATLogisticsProvider
 * - TC-L003: getProvider("UNKNOWN") 拋出 BusinessException E_7503
 */
@DisplayName("LogisticsProviderFactory 單元測試（US-003）")
class LogisticsProviderFactoryTest {

    private LogisticsProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LogisticsProviderFactory(List.of(
                new HCTLogisticsProvider(),
                new TCATLogisticsProvider()
        ));
    }

    @Test
    @DisplayName("TC-L001: getProvider(\"HCT\") 回傳 HCTLogisticsProvider")
    void getProvider_hct_returnsHCTProvider() {
        LogisticsProvider provider = factory.getProvider("HCT");

        assertThat(provider).isInstanceOf(HCTLogisticsProvider.class);
        assertThat(provider.getProviderCode()).isEqualTo("HCT");
    }

    @Test
    @DisplayName("TC-L002: getProvider(\"TCAT\") 回傳 TCATLogisticsProvider")
    void getProvider_tcat_returnsTCATProvider() {
        LogisticsProvider provider = factory.getProvider("TCAT");

        assertThat(provider).isInstanceOf(TCATLogisticsProvider.class);
        assertThat(provider.getProviderCode()).isEqualTo("TCAT");
    }

    @Test
    @DisplayName("TC-L003: getProvider(\"UNKNOWN\") 拋出 BusinessException E_7503")
    void getProvider_unknown_throwsBusinessException() {
        assertThatThrownBy(() -> factory.getProvider("UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.E_7503);
                });
    }
}
