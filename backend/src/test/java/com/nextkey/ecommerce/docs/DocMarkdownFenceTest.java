package com.nextkey.ecommerce.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sprint 295（DEF-399 (a)）：文件裡的「編號標題」（{@code ## 7. …}、{@code ### 6.7 …}）不得落在沒有語言標記的程式碼圍欄內。
 *
 * <p>為什麼要守：Sprint 292 發現 {@code E-Commerce_PRD_v1.0_Final.md} 有一個沒有對應開頭的孤兒 {@code ```} 圍欄，
 * 讓其後所有圍欄的開／關角色對調，§6.7～§7.5 共 10 個標題（含 §7.3 RBAC 權限矩陣）在 GitHub／VS Code 預覽中
 * 被吞進程式碼區塊，至少 7 週沒人發現——在編輯器裡讀原始碼完全看不出來。
 *
 * <p>規則是由那次缺陷的成因推得的：孤兒圍欄造成的「吞掉區間」一定從一個<b>沒有語言標記</b>的圍欄開始
 * （它原本是某個區塊的結尾），所以只檢查開頭沒有 info string 的圍欄；有語言標記的（{@code ```bash}、
 * {@code ```markdown}）是刻意的範例區塊，裡面出現編號的 {@code # 1. 步驟} 註解是合法的。標題只認「編號標題」，
 * 因為 shell／PromQL 區塊裡的 {@code # 註解} 沒有編號（{@code SETTLEMENT_JOB_RUNBOOK.md} 就有，不能誤報）。
 *
 * <p>圍欄的開關遵循 CommonMark：開頭是 0～3 個空白加上 3 個以上的同一種字元（反引號或波浪號），反引號圍欄的
 * info string 不得含反引號；結尾必須是同一種字元、長度不短於開頭，且後面只能有空白（<b>不能帶 info string</b>）。
 */
@DisplayName("Sprint 295: 文件的編號標題不得被程式碼圍欄吞掉")
class DocMarkdownFenceTest {

    private static final Path REPO = Path.of(System.getProperty("basedir", "."), "..").normalize();

    /**
     * 已知有孤兒圍欄、但因為是被取代的歷史版本而刻意不修的快照（Sprint 292 的決定）。
     * {@link #knownSnapshotsStillViolate()} 保證這份名單不會過期：一旦修好，測試會要求把它移除。
     */
    private static final Set<String> KNOWN_HISTORICAL_SNAPSHOTS =
            Set.of("docs/01_requirements/E-Commerce_PRD_v0.9_R02_Final.md");

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^ {0,3}#{1,6} +\\d+(?:\\.\\d+)*(?:[.、:：]|\\s).*$");

    @Test
    @DisplayName("docs/ 與 README.md 的 Markdown 沒有編號標題被無語言標記的圍欄吞掉（已知歷史快照除外）")
    void noNumberedHeadingSwallowedByBareFence() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : markdownFiles()) {
            String relative = relativePath(file);
            if (KNOWN_HISTORICAL_SNAPSHOTS.contains(relative)) {
                continue;
            }
            for (String hit : numberedHeadingsInsideBareFences(readLines(file))) {
                problems.add(relative + " " + hit);
            }
        }

        assertThat(problems)
                .as("下列編號標題被程式碼圍欄吞掉了（在 GitHub／VS Code 預覽會顯示成程式碼）。"
                        + "通常是某處多了或少了一個 ```，請從第一個被吞的標題往回找沒有成對的圍欄：\n%s",
                        String.join("\n", problems))
                .isEmpty();
    }

    @Test
    @DisplayName("已知歷史快照名單必須仍然違規——否則代表已修好，請從名單移除")
    void knownSnapshotsStillViolate() throws IOException {
        for (String relative : KNOWN_HISTORICAL_SNAPSHOTS) {
            Path file = REPO.resolve(relative);
            assertThat(file).as("名單中的檔案不存在：%s", relative).exists();
            assertThat(numberedHeadingsInsideBareFences(readLines(file)))
                    .as("%s 已不再有被吞掉的編號標題，請從 KNOWN_HISTORICAL_SNAPSHOTS 移除", relative)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("孤兒結尾圍欄：其後的編號標題被吞進程式碼區塊（Sprint 292 的 PRD 缺陷重現）")
    void strayFenceSwallowsNumberedHeadings() {
        List<String> lines = List.of(
                "### 6.6 M15 內容管理",
                "- 規格說明（純敘述，沒有程式碼）",
                "```",                          // 孤兒：前面沒有對應的開頭
                "",
                "### 6.7 M16 進銷存管理",          // 被吞
                "**[Specification] 一體化庫存連動**",
                "```",                          // 原本的開頭，現在變成結尾
                "B 端入庫流程",
                "```",                          // 原本的結尾，現在變成開頭
                "## 7. 使用者角色");              // 被吞

        assertThat(numberedHeadingsInsideBareFences(lines))
                .containsExactly("第 5 行：### 6.7 M16 進銷存管理", "第 10 行：## 7. 使用者角色");
    }

    @Test
    @DisplayName("圍欄成對：編號標題都在圍欄外，沒有違規")
    void balancedFencesProduceNoViolation() {
        List<String> lines = List.of(
                "## 1. 概述",
                "```",
                "code",
                "```",
                "## 2. 設計",
                "```json",
                "{}",
                "```",
                "### 2.1 細節");

        assertThat(numberedHeadingsInsideBareFences(lines)).isEmpty();
    }

    @Test
    @DisplayName("有語言標記的圍欄是刻意的範例區塊：裡面的編號標題不算違規")
    void numberedHeadingInsideLanguageFenceIsLegitimateExample() {
        List<String> lines = List.of(
                "```markdown",
                "## 1. 範例章節",
                "```");

        assertThat(numberedHeadingsInsideBareFences(lines)).isEmpty();
    }

    @Test
    @DisplayName("沒有編號的 # 註解（例如 PromQL）不算標題，不誤報")
    void unnumberedCommentInsideBareFenceIsNotFlagged() {
        List<String> lines = List.of(
                "```",
                "# 結算單生成成功率",
                "rate(settlement_generation_success_total[5m])",
                "```");

        assertThat(numberedHeadingsInsideBareFences(lines)).isEmpty();
    }

    @Test
    @DisplayName("結尾圍欄不能帶 info string：巢狀的 ```json 不會關閉外層，裡面的編號標題仍被吞")
    void closingFenceCannotCarryInfoString() {
        List<String> lines = List.of(
                "```",
                "```json",                      // 帶 info string：只是內容，不是結尾
                "## 2. 仍在圍欄內",
                "```");                         // 這個才關閉

        assertThat(numberedHeadingsInsideBareFences(lines)).containsExactly("第 3 行：## 2. 仍在圍欄內");
    }

    @Test
    @DisplayName("波浪號圍欄與較長的結尾圍欄：結尾長度不得短於開頭")
    void tildeFenceAndClosingLength() {
        List<String> lines = List.of(
                "~~~~",
                "## 3. 被吞",
                "~~~",                          // 長度 3 < 開頭 4：不是結尾
                "## 4. 仍被吞",
                "~~~~~",                        // 長度 5 >= 4：結尾
                "## 5. 圍欄外");

        assertThat(numberedHeadingsInsideBareFences(lines))
                .containsExactly("第 2 行：## 3. 被吞", "第 4 行：## 4. 仍被吞");
    }

    @Test
    @DisplayName("info string 含反引號的行（行內程式碼）與縮排 4 格以上都不是圍欄")
    void inlineCodeAndIndentedLinesAreNotFences() {
        List<String> lines = List.of(
                "``` 移除孤兒 ``` 圍欄",                // 以 ``` 開頭，但 info string 含反引號＝行內程式碼，不開啟圍欄
                "## 1. 仍在圍欄外",
                "    ```",                             // 縮排 4 格：縮排程式碼，不是圍欄
                "## 2. 仍在圍欄外");

        assertThat(numberedHeadingsInsideBareFences(lines)).isEmpty();
    }

    /** 回傳「第 N 行：標題」。只計開頭沒有 info string 的圍欄內、且有編號的 ATX 標題。 */
    static List<String> numberedHeadingsInsideBareFences(final List<String> lines) {
        List<String> found = new ArrayList<>();
        char fenceChar = 0;
        int fenceLength = 0;
        boolean bare = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher fence = FENCE.matcher(line);
            boolean fenceLine = fence.matches();
            if (fenceChar == 0) {
                if (fenceLine && opens(fence)) {
                    fenceChar = fence.group(1).charAt(0);
                    fenceLength = fence.group(1).length();
                    bare = fence.group(2).isBlank();
                }
                continue;
            }
            if (fenceLine && closes(fence, fenceChar, fenceLength)) {
                fenceChar = 0;
                continue;
            }
            if (bare && NUMBERED_HEADING.matcher(line).matches()) {
                found.add("第 " + (i + 1) + " 行：" + line.strip());
            }
        }
        return found;
    }

    /** 反引號圍欄的 info string 不得含反引號（否則是行內程式碼，不是圍欄）。 */
    private static boolean opens(final Matcher fence) {
        return fence.group(1).charAt(0) != '`' || !fence.group(2).contains("`");
    }

    /** 結尾圍欄：同一種字元、長度不短於開頭、後面只能有空白（不能帶 info string）。 */
    private static boolean closes(final Matcher fence, final char fenceChar, final int fenceLength) {
        return fence.group(1).charAt(0) == fenceChar
                && fence.group(1).length() >= fenceLength
                && fence.group(2).isBlank();
    }

    private static List<Path> markdownFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        Path readme = REPO.resolve("README.md");
        if (Files.exists(readme)) {
            files.add(readme);
        }
        try (Stream<Path> walk = Files.walk(REPO.resolve("docs"))) {
            walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(files::add);
        }
        assertThat(files).as("找不到任何 Markdown 文件，REPO 定位可能錯了：%s", REPO).hasSizeGreaterThan(10);
        return files;
    }

    private static String relativePath(final Path file) {
        return REPO.relativize(file.normalize()).toString().replace('\\', '/');
    }

    private static List<String> readLines(final Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
