package com.nextkey.ecommerce.shared.exception;

public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String details;
    private final String userMessage;

    public BusinessException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
        this.userMessage = null;
    }

    public BusinessException(final ErrorCode errorCode, final String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
        this.userMessage = null;
    }

    public BusinessException(final ErrorCode errorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
        this.userMessage = null;
    }

    private BusinessException(final ErrorCode errorCode, final String formattedMessage, final boolean userFacing) {
        super(errorCode.getMessage() + ": " + formattedMessage);
        this.errorCode = errorCode;
        this.details = formattedMessage;
        this.userMessage = userFacing ? formattedMessage : null;
    }

    /**
     * 建立例外，讓 {@code errorCode} 訊息模板裡的佔位符（{@code %d}/{@code %s}）代入實際值，
     * 且 {@link #getUserMessage()} 直接回傳這份已代入實際值的訊息，而非原始未代入的模板（DEF-183）。
     *
     * <p>與既有的 {@link #BusinessException(ErrorCode, String)} 不同：後者的 {@code details}
     * 只用於伺服器端 log（{@link #getUserMessage()} 固定回傳 {@code errorCode.getMessage()} 原始模板，
     * 依 AI-2418 設計不外洩動態英文除錯細節），本方法則是刻意讓格式化後的文字流向使用者——
     * 僅限訊息模板本身就設計了佔位符、且該動態值本身就適合讓使用者看到的情境（如張數、索引）。
     */
    public static BusinessException withFormattedMessage(final ErrorCode errorCode, final Object... formatArgs) {
        return new BusinessException(errorCode, errorCode.getFormattedMessage(formatArgs), true);
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
     * 給前端顯示的訊息（AI-2418 全站英文訊息碼化）：預設回傳 ErrorCode 的中文訊息，
     * 不包含 details/自訂 message 內的動態英文細節（如具體日期、ID）——那些只透過
     * getMessage()（含於例外本身，供伺服器端 log 使用）呈現，不外洩給使用者。
     *
     * <p>唯一例外（DEF-183）：透過 {@link #withFormattedMessage} 建構的例外，userMessage
     * 已是把佔位符代入實際值後、刻意設計給使用者看的訊息，此時優先回傳它而非原始模板。
     */
    public String getUserMessage() {
        return userMessage != null ? userMessage : errorCode.getMessage();
    }
}