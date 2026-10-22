package com.nextkey.ecommerce.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sprint 295（DEF-399 (b)）：文件技術棧表寫的 Next.js／React 主版本，必須與 {@code frontend/package.json} 一致。
 *
 * <p>為什麼要守：Sprint 292 發現 README、PRD、FRD、SRD 共 7 行寫 {@code Next.js 15}／{@code React 18}，
 * 但 {@code frontend/package.json} 自 2026-05-10 起就是 Next 16.x（當時 16.4.2）、React 19.2.4，
 * 落後了 5 個月沒人發現。
 *
 * <p>只比對「現況」的技術棧行（表格列與架構圖），不掃全文：修訂註記與版本紀錄會合理地提到舊版本
 * （例如「{@code Next.js 15} 校正為 {@code Next.js 16}」），全文比對會誤報。代價是樣式綁定了這幾行目前的寫法——
 * 文件改寫了版本行，測試會報「找不到」，請同步更新 {@link #CLAIMS}；這是刻意的，比靜默失去保護好。
 */
@DisplayName("Sprint 295: 文件技術棧的 Next.js／React 主版本與 frontend/package.json 一致")
class TechStackDocDriftTest {

    private static final Path REPO = Path.of(System.getProperty("basedir", "."), "..").normalize();
    private static final Path PACKAGE_JSON = REPO.resolve("frontend").resolve("package.json");

    private static final String PRD = "docs/01_requirements/E-Commerce_PRD_v1.0_Final.md";
    private static final String FRD = "docs/01_requirements/E-Commerce_FRD_v1.0.md";
    private static final String SRD = "docs/02_architecture/SRD_System_Architecture.md";

    /** 技術棧表的前端列：{@code | 前端 | Next.js 16 (App Router) + …}。 */
    private static final Pattern FRONTEND_ROW = Pattern.compile("^\\| 前端 \\| Next\\.js (\\d+) ");

    /** 一條「某份文件的某種寫法記載了某個套件的主版本」。樣式中每個捕捉群組都必須等於 package.json 的主版本。 */
    private record Claim(String file, String dependency, Pattern pattern) { }

    private static final List<Claim> CLAIMS = List.of(
            new Claim("README.md", "next", FRONTEND_ROW),
            new Claim(PRD, "next", FRONTEND_ROW),
            new Claim(FRD, "next", FRONTEND_ROW),
            new Claim(SRD, "next", Pattern.compile("Frontend \\(Next\\.js (\\d+)\\)")),
            new Claim(SRD, "react", Pattern.compile("React (\\d+) \\+ TypeScript")),
            new Claim(SRD, "next", Pattern.compile("^\\| \\*\\*前端框架\\*\\* \\| Next\\.js (\\d+) \\| (\\d+)\\.x ")),
            new Claim(SRD, "react", Pattern.compile("^\\| \\*\\*前端 UI 框架\\*\\* \\| React \\| (\\d+)\\.x ")));

    @Test
    @DisplayName("README／PRD／FRD／SRD 技術棧行記載的 Next.js 與 React 主版本，等於 frontend/package.json 的主版本")
    void techStackDocsMatchPackageJson() throws IOException {
        int nextMajor = majorOf(declaredVersion("next"));
        int reactMajor = majorOf(declaredVersion("react"));

        List<String> problems = new ArrayList<>();
        for (Claim claim : CLAIMS) {
            int expected = "next".equals(claim.dependency()) ? nextMajor : reactMajor;
            List<String> lines = Files.readAllLines(REPO.resolve(claim.file()), StandardCharsets.UTF_8);
            int matched = 0;
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = claim.pattern().matcher(lines.get(i));
                if (!matcher.find()) {
                    continue;
                }
                matched++;
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    int documented = Integer.parseInt(matcher.group(group));
                    if (documented != expected) {
                        problems.add(String.format("%s 第 %d 行寫 %s %d，但 frontend/package.json 是 %s %d.x：%s",
                                claim.file(), i + 1, claim.dependency(), documented,
                                claim.dependency(), expected, lines.get(i).strip()));
                    }
                }
            }
            if (matched == 0) {
                problems.add(String.format("%s 找不到符合 /%s/ 的技術棧行（文件改寫了版本行？請同步更新 TechStackDocDriftTest.CLAIMS）",
                        claim.file(), claim.pattern()));
            }
        }

        assertThat(problems)
                .as("文件的技術棧版本與 frontend/package.json 不一致，請同步更新文件：\n%s", String.join("\n", problems))
                .isEmpty();
    }

    @Test
    @DisplayName("版本字串取主版本：精確、^、~、>= 寫法都能解析")
    void majorOfParsesCommonRangeForms() {
        assertThat(majorOf("16.4.2")).isEqualTo(16);
        assertThat(majorOf("^19.2.4")).isEqualTo(19);
        assertThat(majorOf("~15.0.0")).isEqualTo(15);
        assertThat(majorOf(">=16.0.0")).isEqualTo(16);
    }

    private static String declaredVersion(final String dependency) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(PACKAGE_JSON, StandardCharsets.UTF_8));
        JsonNode version = root.path("dependencies").path(dependency);
        if (version.isMissingNode()) {
            version = root.path("devDependencies").path(dependency);
        }
        assertThat(version.isMissingNode()).as("%s 的 dependencies／devDependencies 裡找不到 %s", PACKAGE_JSON, dependency).isFalse();
        return version.asText();
    }

    private static int majorOf(final String declaredVersion) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(declaredVersion);
        assertThat(matcher.find()).as("無法從版本字串解析主版本：%s", declaredVersion).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
