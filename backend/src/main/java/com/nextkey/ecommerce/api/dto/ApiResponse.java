package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextkey.ecommerce.shared.trace.RequestId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private List<FieldError> errors;
    private Instant timestamp;

    /**
     * 請求追蹤 ID，與回應標頭 {@code X-Request-ID} 相同（PRD §16.4.1）。只在錯誤回應帶入，讓使用者回報
     * 問題時可直接引用；成功回應不帶，維持既有 payload 不變。所有錯誤都經 {@code error(...)} 工廠方法產生
     * （含 Security 的 401／403 與各限流過濾器的 429），故在此統一取值。
     */
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .requestId(RequestId.current())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now())
                .requestId(RequestId.current())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
