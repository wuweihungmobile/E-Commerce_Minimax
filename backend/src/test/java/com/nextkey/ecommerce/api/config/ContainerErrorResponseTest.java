package com.nextkey.ecommerce.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

import com.nextkey.ecommerce.api.dto.ApiResponse;

/**
 * Sprint 206（DEF-283）：容器層錯誤封包的狀態碼→錯誤碼對應。
 *
 * <p>這份對應同時被兩條路徑使用（容器 ERROR 分派的 {@code ApiErrorController}、Tomcat 連接器層的
 * {@code ApiErrorReportValve}），所以只需在此驗證一次。整條鏈路的行為見 {@code ContainerErrorDispatchIntegrationTest}。
 */
@DisplayName("Sprint 206: ContainerErrorResponse")
class ContainerErrorResponseTest {

    @ParameterizedTest(name = "HTTP {0} → {1}")
    @CsvSource({
            "404, E-4041, 找不到請求的資源",
            "400, E-9000, 請求格式錯誤",
            "405, E-9000, 請求格式錯誤",
            "414, E-9000, 請求格式錯誤",
            "431, E-9000, 請求格式錯誤",
            "500, E-9900, 發生未預期的錯誤",
            "503, E-9900, 發生未預期的錯誤"})
    @DisplayName("404 → E-4041；其他 4xx → E-9000；其餘 → E-9900，且帶入呼叫端提供的 requestId、不帶 data")
    void mapsStatusToErrorCode(final int status, final String code, final String message) {
        ApiResponse<Void> body = ContainerErrorResponse.forStatus(HttpStatusCode.valueOf(status), "req-123");

        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo(code);
        assertThat(body.getMessage()).isEqualTo(message);
        assertThat(body.getRequestId()).isEqualTo("req-123");
        assertThat(body.getData()).isNull();
    }
}
