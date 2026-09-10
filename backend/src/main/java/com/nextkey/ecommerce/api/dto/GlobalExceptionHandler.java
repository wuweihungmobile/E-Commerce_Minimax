package com.nextkey.ecommerce.api.dto;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(final BusinessException ex) {
        log.warn("Business exception: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());

        HttpStatus status = mapErrorCodeToStatus(ex.getErrorCode());

        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getFullCode(), ex.getUserMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(final MethodArgumentNotValidException ex) {
        log.warn("Validation exception: {}", ex.getMessage());

        List<ApiResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ApiResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.E_9000.getCode(),
                        "驗證失敗",
                        fieldErrors
                ));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(final BindException ex) {
        log.warn("Bind exception: {}", ex.getMessage());

        List<ApiResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ApiResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.E_9000.getCode(),
                        "綁定失敗",
                        fieldErrors
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.E_9005.getCode(),
                        "缺少必填參數「" + ex.getParameterName() + "」"
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(final AuthenticationException ex) {
        log.warn("Authentication exception: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.E_1000.getCode(), "需要驗證身份"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(final BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.E_1001.getCode(), "帳號或密碼錯誤"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(final AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.E_1007.getCode(), "權限不足"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(final Exception ex, final WebRequest request) {
        log.error("Unexpected error at {}: ", request.getDescription(true), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.E_9900.getCode(),
                        "發生未預期的錯誤"
                ));
    }

    private HttpStatus mapErrorCodeToStatus(final ErrorCode errorCode) {
        return switch ( errorCode) {
            case E_1000, E_1001, E_1002, E_1003, E_1004 -> HttpStatus.UNAUTHORIZED;
            case E_1005, E_1006, E_2000, E_2006, E_3000, E_3003, E_3006, E_4000, E_4006, E_4100, E_4101, E_4105,
                    E_5000, E_5003, E_5005, E_5016, E_6000, E_7000, E_7001, E_7500, E_7501, E_7503, E_7504,
                    E_8002, E_8006 -> HttpStatus.NOT_FOUND;
            case E_1007, E_2001, E_2002, E_2004, E_4031, E_8007, E_1009 -> HttpStatus.FORBIDDEN;
            case E_2003 -> HttpStatus.CONFLICT;
            case E_3004, E_4001, E_4002, E_4003, E_4004, E_4005, E_4104, E_5002, E_5004, E_5007,
                    E_5008, E_5009, E_5017, E_5018, E_5019,
                    E_6001, E_6002, E_6003, E_6006, E_7004, E_2005, E_2007, E_2008, E_1097 -> HttpStatus.BAD_REQUEST;
            // Sprint 153：E_1008 原本掛在 E_1000~E_1004 這組 UNAUTHORIZED（Sprint 78 stub 時代預留，
            // 從未真正拋出過），語意上「OAuth 帳號已綁定其他使用者」屬於資料衝突而非認證失敗，
            // 移到與 E_4106/E_1010 同組的 CONFLICT，比照 E_2003（已存在類）語意更貼近。
            case E_4106, E_1010, E_1008 -> HttpStatus.CONFLICT;
            case E_3001, E_3002, E_4008, E_5001, E_5006, E_6004, E_6005, E_7002 -> HttpStatus.UNPROCESSABLE_ENTITY;
            case E_9000, E_9001, E_9002, E_9003, E_9004, E_9005, E_9006, E_9007, E_9008 -> HttpStatus.BAD_REQUEST;
            case E_9904 -> HttpStatus.TOO_MANY_REQUESTS;
            case E_6007, E_9903, E_9905, E_1096 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
