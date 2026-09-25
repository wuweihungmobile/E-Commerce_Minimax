package com.nextkey.ecommerce.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.nextkey.ecommerce.shared.trace.RequestId;

/**
 * DEF-280：錯誤回應帶 requestId、成功回應不帶。
 * 「成功回應不帶」是刻意的：成功 payload 是前端已依賴的契約，不該因追蹤功能而多出欄位。
 */
@DisplayName("DEF-280: ApiResponse requestId")
class ApiResponseRequestIdTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("兩個 error 工廠方法都帶入目前請求的 ID")
    void errorFactoriesCarryCurrentRequestId() {
        MDC.put(RequestId.MDC_KEY, "req-abc");

        assertThat(ApiResponse.error("E-1000", "x").getRequestId()).isEqualTo("req-abc");
        assertThat(ApiResponse.error("E-1000", "x", List.of()).getRequestId()).isEqualTo("req-abc");
    }

    @Test
    @DisplayName("success 工廠方法不帶 requestId，即使 MDC 有值")
    void successDoesNotCarryRequestId() {
        MDC.put(RequestId.MDC_KEY, "req-abc");

        assertThat(ApiResponse.success("data").getRequestId()).isNull();
        assertThat(ApiResponse.success("ok", "data").getRequestId()).isNull();
    }

    @Test
    @DisplayName("不在請求範圍內（MDC 無值）時為 null，不拋例外")
    void nullOutsideRequest() {
        assertThat(ApiResponse.error("E-1000", "x").getRequestId()).isNull();
    }
}
