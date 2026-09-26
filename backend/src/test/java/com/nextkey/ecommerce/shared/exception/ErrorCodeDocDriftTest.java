package com.nextkey.ecommerce.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.api.dto.GlobalExceptionHandler;

/**
 * Sprint 203（DEF-280）：{@code docs/02_architecture/API_Error_Codes.md} 第 4 節的錯誤碼對照表，必須與程式碼逐列一致。
 *
 * <p>為什麼要守：PRD 的錯誤碼表與實作在 Sprint 203 之前已經分歧（19 個重疊碼字串中 18 個意義不同），
 * 而人工雙處記帳的文件遲早再漂移。這裡以 {@link ErrorCode} 的 code／訊息，加上 {@link GlobalExceptionHandler}
 * 實際回的 HTTP 狀態碼（透過真的丟 {@link BusinessException} 去問，而不是重抄它的 switch）為準。
 *
 * <p>文件缺檔、缺列、多列、內容不同都會失敗，失敗訊息直接列出可照抄的表格列。
 */
@DisplayName("Sprint 203: 錯誤碼文件與程式碼一致")
class ErrorCodeDocDriftTest {

    private static final Path DOC = Path.of(System.getProperty("basedir", "."),
            "..", "docs", "02_architecture", "API_Error_Codes.md");

    private static final String SECTION_START = "## 4.";
    private static final String SECTION_END = "## 5.";
    private static final Pattern ROW = Pattern.compile("^\\| (E-\\d{4}) \\| (\\d{3}) \\| (.*) \\|$");

    @Test
    @DisplayName("第 4 節每一列的 HTTP 狀態碼與預設訊息，都與 ErrorCode／GlobalExceptionHandler 相同")
    void errorCodeTableMatchesCode() throws IOException {
        Map<String, String> expected = expectedRows();
        Map<String, String> documented = documentedRows();

        List<String> problems = new ArrayList<>();
        expected.forEach((code, row) -> {
            if (!documented.containsKey(code)) {
                problems.add("文件缺少：" + tableRow(code, row));
            } else if (!documented.get(code).equals(row)) {
                problems.add("內容不同：文件為 " + tableRow(code, documented.get(code)) + "\n            應為 " + tableRow(code, row));
            }
        });
        documented.keySet().stream()
                .filter(code -> !expected.containsKey(code))
                .forEach(code -> problems.add("文件多出（程式碼已沒有這個碼）：" + tableRow(code, documented.get(code))));

        assertThat(problems)
                .as("API_Error_Codes.md 第 4 節與程式碼不一致，請同步更新文件：\n%s", String.join("\n", problems))
                .isEmpty();
    }

    /** code → 「HTTP 狀態碼 | 預設訊息」。真的丟例外去問 handler，狀態碼就不會是抄來的第二份。 */
    private static Map<String, String> expectedRows() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Map<String, String> rows = new TreeMap<>();
        for (ErrorCode errorCode : ErrorCode.values()) {
            int status = handler.handleBusinessException(new BusinessException(errorCode)).getStatusCode().value();
            rows.put(errorCode.getCode(), status + " | " + errorCode.getMessage());
        }
        // 兩個 enum 常數共用同一個 code 字串時 put 會靜默覆蓋，這裡大聲失敗
        assertThat(rows).as("ErrorCode 的 code 字串必須唯一").hasSize(ErrorCode.values().length);
        return rows;
    }

    /** 只讀第 4 節：第 5 節的 PRD 對照例表也以 {@code | E-XXXX |} 開頭，不能混進來。 */
    private static Map<String, String> documentedRows() throws IOException {
        List<String> lines = Files.readAllLines(DOC);
        int start = indexOfLineStartingWith(lines, SECTION_START);
        int end = indexOfLineStartingWith(lines, SECTION_END);
        assertThat(start).as("找不到「%s」開頭的章節", SECTION_START).isNotNegative();
        assertThat(end).as("找不到「%s」開頭的章節", SECTION_END).isGreaterThan(start);

        Map<String, String> rows = new TreeMap<>();
        for (String line : lines.subList(start, end)) {
            Matcher matcher = ROW.matcher(line);
            if (matcher.matches()) {
                String previous = rows.put(matcher.group(1), matcher.group(2) + " | " + matcher.group(3));
                assertThat(previous).as("文件第 4 節的 %s 重複出現", matcher.group(1)).isNull();
            }
        }
        return rows;
    }

    private static int indexOfLineStartingWith(final List<String> lines, final String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static String tableRow(final String code, final String statusAndMessage) {
        return "| " + code + " | " + statusAndMessage + " |";
    }
}
