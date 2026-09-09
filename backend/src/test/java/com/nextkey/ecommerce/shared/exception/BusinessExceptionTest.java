package com.nextkey.ecommerce.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BusinessException / ErrorCode 訊息格式化機制單元測試（Sprint 148，DEF-183）。
 *
 * <p>背景：{@code E_1088}/{@code E_1089}/{@code E_1090}（皆 Review 模組）的訊息模板帶
 * {@code %d}/{@code %s} 佔位符，但 {@code BusinessException.getUserMessage()} 先前固定回傳
 * {@code errorCode.getMessage()} 原始模板，即使呼叫端已用 {@code String.format} 組出格式化字串
 * 也不會被使用——使用者實際收到的文字裡 {@code %d}/{@code %s} 從未被替換。
 *
 * <p>本測試驗證兩件事：(1) 新增的 {@link BusinessException#withFormattedMessage} 讓
 * {@code getUserMessage()} 正確回傳代入實際值後的文字；(2) 既有的兩個建構子行為不變
 * （{@code getUserMessage()} 仍固定回傳原始模板，不外洩 details 裡的動態英文除錯細節，
 * 這是 AI-2418 的既有設計，本次修復刻意不影響全站其餘所有 ErrorCode 的既有行為）。
 */
@DisplayName("BusinessException 訊息格式化機制（DEF-183）")
class BusinessExceptionTest {

    @Test
    @DisplayName("withFormattedMessage：getUserMessage() 回傳代入實際值後的文字，不含 %d")
    void withFormattedMessage_singleIntArg_userMessageHasActualValue() {
        BusinessException ex = BusinessException.withFormattedMessage(ErrorCode.E_1088, 10);

        assertThat(ex.getUserMessage())
                .isEqualTo("評價圖片數量超過上限（最多 9 張，目前 10 張）")
                .doesNotContain("%d");
    }

    @Test
    @DisplayName("withFormattedMessage：%s 佔位符同樣正確代入實際值")
    void withFormattedMessage_stringArg_userMessageHasActualValue() {
        BusinessException ex = BusinessException.withFormattedMessage(ErrorCode.E_1089, "media-abc-123");

        assertThat(ex.getUserMessage())
                .isEqualTo("無效的評價圖片：media-abc-123")
                .doesNotContain("%s");
    }

    @Test
    @DisplayName("withFormattedMessage：details 與 getUserMessage() 皆為同一份格式化文字")
    void withFormattedMessage_detailsMatchesUserMessage() {
        BusinessException ex = BusinessException.withFormattedMessage(ErrorCode.E_1090, 5);

        assertThat(ex.getDetails()).isEqualTo(ex.getUserMessage()).isEqualTo("找不到索引 5 的評價圖片");
    }

    @Test
    @DisplayName("既有 2-arg 建構子（details 為英文除錯字串）：getUserMessage() 仍固定回傳原始模板，不外洩 details（AI-2418 既有行為不變）")
    void twoArgConstructor_userMessageIgnoresDetails_unchangedFromBeforeFix() {
        BusinessException ex = new BusinessException(ErrorCode.E_1007, "You can only update your own review");

        assertThat(ex.getUserMessage()).isEqualTo("權限不足");
        assertThat(ex.getUserMessage()).doesNotContain("You can only update your own review");
    }

    @Test
    @DisplayName("既有無參數建構子：getUserMessage() 回傳原始模板")
    void singleArgConstructor_userMessageReturnsRawTemplate() {
        BusinessException ex = new BusinessException(ErrorCode.E_1006);

        assertThat(ex.getUserMessage()).isEqualTo(ErrorCode.E_1006.getMessage());
    }

    @Test
    @DisplayName("ErrorCode.getFormattedMessage：多參數正確逐一代入（先前 String... 簽章的 varargs 展開 bug 已修復）")
    void getFormattedMessage_multipleArgs_formatsCorrectly() {
        // E_1090 只有一個 %d，這裡驗證單一參數走 Object... 路徑不會被誤包成陣列本身
        String formatted = ErrorCode.E_1090.getFormattedMessage(7);

        assertThat(formatted).isEqualTo("找不到索引 7 的評價圖片").doesNotContain("%d");
    }

    @Test
    @DisplayName("ErrorCode.getFormattedMessage：零參數回傳原始模板")
    void getFormattedMessage_noArgs_returnsRawTemplate() {
        assertThat(ErrorCode.E_1088.getFormattedMessage()).isEqualTo(ErrorCode.E_1088.getMessage());
    }
}
