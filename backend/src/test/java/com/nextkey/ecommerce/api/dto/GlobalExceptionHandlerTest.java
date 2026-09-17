package com.nextkey.ecommerce.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * GlobalExceptionHandler 單元測試（DEF-223）。
 *
 * <p>{@code mapErrorCodeToStatus} 先前只明確列出 88/136 個 {@link ErrorCode}，其餘 48 個
 * 落入 {@code default -> INTERNAL_SERVER_ERROR}。其中 44 個屬於真正的缺口（如
 * {@code E_5013}「找不到對帳單」被誤回 500 而非 404），另 4 個（E_9900/E_9901/E_9902/E_9906，
 * 皆為系統內部錯誤語意）恰好落在 default 也是正確結果。
 *
 * <p>本測試對全部 136 個 {@link ErrorCode} 逐一斷言期望的 HTTP 狀態碼，取代先前僅明確列出
 * 88 個、其餘靠 {@code default} 隱性承接的寫法（修復後 switch 改為完全窮舉、移除 default，
 * 讓編譯器強制未來新增 {@code ErrorCode} 時必須同步決定狀態碼，否則編譯失敗）。
 */
@DisplayName("DEF-223: GlobalExceptionHandler 缺口 ErrorCode 誤回 500")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static Map<ErrorCode, HttpStatus> expectedStatusByCode() {
        Map<ErrorCode, HttpStatus> expected = new EnumMap<>(ErrorCode.class);

        // 401 UNAUTHORIZED
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_1000, ErrorCode.E_1001, ErrorCode.E_1002,
                ErrorCode.E_1003, ErrorCode.E_1004}) {
            expected.put(code, HttpStatus.UNAUTHORIZED);
        }

        // 404 NOT_FOUND
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_1005, ErrorCode.E_1006, ErrorCode.E_2000,
                ErrorCode.E_2006, ErrorCode.E_3000, ErrorCode.E_3003, ErrorCode.E_3006, ErrorCode.E_3007,
                ErrorCode.E_4000, ErrorCode.E_4006, ErrorCode.E_4041, ErrorCode.E_4100, ErrorCode.E_4101,
                ErrorCode.E_4102, ErrorCode.E_4103, ErrorCode.E_4105, ErrorCode.E_5000, ErrorCode.E_5003,
                ErrorCode.E_5005, ErrorCode.E_5013, ErrorCode.E_5016, ErrorCode.E_6000, ErrorCode.E_7000,
                ErrorCode.E_7001, ErrorCode.E_7003, ErrorCode.E_7007, ErrorCode.E_7500, ErrorCode.E_7501,
                ErrorCode.E_7503, ErrorCode.E_7504, ErrorCode.E_8000, ErrorCode.E_8002, ErrorCode.E_8003,
                ErrorCode.E_8004, ErrorCode.E_8005, ErrorCode.E_8006, ErrorCode.E_8008, ErrorCode.E_1087,
                ErrorCode.E_1090, ErrorCode.E_1092}) {
            expected.put(code, HttpStatus.NOT_FOUND);
        }

        // 403 FORBIDDEN
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_1007, ErrorCode.E_1009, ErrorCode.E_2001,
                ErrorCode.E_2002, ErrorCode.E_2004, ErrorCode.E_4031, ErrorCode.E_7008, ErrorCode.E_8007,
                ErrorCode.E_8009, ErrorCode.E_1091, ErrorCode.E_8011}) {
            expected.put(code, HttpStatus.FORBIDDEN);
        }

        // 409 CONFLICT
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_2003, ErrorCode.E_4106, ErrorCode.E_1008,
                ErrorCode.E_1010, ErrorCode.E_1086, ErrorCode.E_1093, ErrorCode.E_1094, ErrorCode.E_3005,
                ErrorCode.E_4091, ErrorCode.E_4092, ErrorCode.E_5014, ErrorCode.E_7006, ErrorCode.E_8010}) {
            expected.put(code, HttpStatus.CONFLICT);
        }

        // 400 BAD_REQUEST
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_2005, ErrorCode.E_2007, ErrorCode.E_2008,
                ErrorCode.E_2009, ErrorCode.E_3004, ErrorCode.E_4001, ErrorCode.E_4002, ErrorCode.E_4003,
                ErrorCode.E_4004, ErrorCode.E_4005, ErrorCode.E_4007, ErrorCode.E_4104, ErrorCode.E_5002,
                ErrorCode.E_5004, ErrorCode.E_5007, ErrorCode.E_5008, ErrorCode.E_5009, ErrorCode.E_5015,
                ErrorCode.E_5017, ErrorCode.E_5018, ErrorCode.E_5019, ErrorCode.E_6001, ErrorCode.E_6002,
                ErrorCode.E_6003, ErrorCode.E_6006, ErrorCode.E_7004, ErrorCode.E_7502, ErrorCode.E_1097,
                ErrorCode.E_1088, ErrorCode.E_1089, ErrorCode.E_1095, ErrorCode.E_9009,
                ErrorCode.E_9000, ErrorCode.E_9001, ErrorCode.E_9002, ErrorCode.E_9003, ErrorCode.E_9004,
                ErrorCode.E_9005, ErrorCode.E_9006, ErrorCode.E_9007, ErrorCode.E_9008}) {
            expected.put(code, HttpStatus.BAD_REQUEST);
        }

        // 422 UNPROCESSABLE_ENTITY
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_3001, ErrorCode.E_3002, ErrorCode.E_4008,
                ErrorCode.E_5001, ErrorCode.E_5006, ErrorCode.E_6004, ErrorCode.E_6005, ErrorCode.E_7002,
                ErrorCode.E_7010, ErrorCode.E_5010, ErrorCode.E_5011, ErrorCode.E_5012, ErrorCode.E_6009,
                ErrorCode.E_7005, ErrorCode.E_7009, ErrorCode.E_8001}) {
            expected.put(code, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 429 TOO_MANY_REQUESTS
        expected.put(ErrorCode.E_9904, HttpStatus.TOO_MANY_REQUESTS);

        // 503 SERVICE_UNAVAILABLE
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_6007, ErrorCode.E_9903, ErrorCode.E_9905,
                ErrorCode.E_1096, ErrorCode.E_6008, ErrorCode.E_6010}) {
            expected.put(code, HttpStatus.SERVICE_UNAVAILABLE);
        }

        // 500 INTERNAL_SERVER_ERROR
        for (ErrorCode code : new ErrorCode[] {ErrorCode.E_9900, ErrorCode.E_9901, ErrorCode.E_9902,
                ErrorCode.E_9906}) {
            expected.put(code, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return expected;
    }

    private static Stream<ErrorCode> allErrorCodes() {
        return Stream.of(ErrorCode.values());
    }

    @Test
    @DisplayName("測試自身的期望表必須涵蓋全部 ErrorCode，否則測試本身就有缺口")
    void expectedStatusByCode_coversEveryErrorCode() {
        Map<ErrorCode, HttpStatus> expected = expectedStatusByCode();
        assertThat(expected.keySet())
                .as("expectedStatusByCode() 必須明確涵蓋 ErrorCode.values() 的每一個值")
                .containsExactlyInAnyOrder(ErrorCode.values());
    }

    @ParameterizedTest(name = "{0} 應回應 HTTP 狀態碼與其語意一致，而非落入 default 500")
    @MethodSource("allErrorCodes")
    void handleBusinessException_everyErrorCode_mapsToSemanticallyCorrectStatus(ErrorCode errorCode) {
        HttpStatus expectedStatus = expectedStatusByCode().get(errorCode);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(errorCode));

        assertThat(response.getStatusCode())
                .as("%s（%s）不應落入 default 500", errorCode, errorCode.getMessage())
                .isEqualTo(expectedStatus);
        assertThat(response.getBody().getCode()).isEqualTo(errorCode.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(errorCode.getMessage());
    }
}
