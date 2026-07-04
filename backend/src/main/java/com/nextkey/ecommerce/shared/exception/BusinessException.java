package com.nextkey.ecommerce.shared.exception;

public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String details;

    public BusinessException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(final ErrorCode errorCode, final String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(final ErrorCode errorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    public String getFullCode() {
        return errorCode.getCode();
    }

    /**
     * 給前端顯示的訊息（AI-2418 全站英文訊息碼化）：一律回傳 ErrorCode 的中文訊息，
     * 不包含 details/自訂 message 內的動態英文細節（如具體日期、ID）——那些只透過
     * getMessage()（含於例外本身，供伺服器端 log 使用）呈現，不外洩給使用者。
     */
    public String getUserMessage() {
        return errorCode.getMessage();
    }
}