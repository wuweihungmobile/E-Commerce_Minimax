package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DEF-320（Sprint 233）：出貨的設定檔預設值，一律不得是 {@link JwtTokenService} 會接受的密鑰。
 *
 * <p>Sprint 183（DEF-251）只擋了 {@code application.yml} 的那一個預設字串；Sprint 232 發現
 * {@code docker-compose.yml} 與 {@code .env.example} 各自出貨了不同的佔位字串，沒設 {@code JWT_SECRET}
 * 時會被接受、token 以公開字串簽發。{@code JwtTokenServiceTest} 只能列舉「已知的」字串；
 * 這個測試直接<b>讀三個出貨檔案</b>取出它們實際的預設值，日後有人改了某個預設值、換成沒被拒絕的字串，
 * 這裡會失敗，而不是等到有人用 {@code docker compose up} 才發現。
 *
 * <p>只涵蓋「產品設定的預設值」這三個檔案；CI／E2E／測試 compose 裡的 {@code JWT_SECRET} 是明確的測試值，
 * 不是預設值，本來就該被接受。
 */
@DisplayName("Sprint 233: 出貨的 JWT 密鑰預設值全部被拒絕（DEF-320）")
class JwtSecretShippedDefaultsTest {

    private static final Path BASE = Path.of(System.getProperty("basedir", "."));

    private static final Path APPLICATION_YML = BASE.resolve("src/main/resources/application.yml");
    private static final Path DOCKER_COMPOSE = BASE.resolve("../docker-compose.yml");
    private static final Path ENV_EXAMPLE = BASE.resolve("../.env.example");

    /** {@code ${JWT_SECRET:預設值}}（yml）與 {@code ${JWT_SECRET:-預設值}}（compose）。 */
    private static final Pattern PLACEHOLDER_SYNTAX = Pattern.compile("\\$\\{JWT_SECRET:-?([^}]+)}");

    /** {@code .env.example} 的 {@code JWT_SECRET=值}（行首，不含被註解掉的行）。 */
    private static final Pattern ENV_ASSIGNMENT = Pattern.compile("^JWT_SECRET=(.+)$", Pattern.MULTILINE);

    @Test
    @DisplayName("application.yml、docker-compose.yml、.env.example 出貨的預設密鑰，建構 JwtTokenService 一律被拒絕")
    void everyShippedDefault_isRejected() throws IOException {
        Map<String, String> shippedDefaults = new LinkedHashMap<>();
        shippedDefaults.put("backend/src/main/resources/application.yml", extract(PLACEHOLDER_SYNTAX, APPLICATION_YML));
        shippedDefaults.put("docker-compose.yml", extract(PLACEHOLDER_SYNTAX, DOCKER_COMPOSE));
        shippedDefaults.put(".env.example", extract(ENV_ASSIGNMENT, ENV_EXAMPLE));

        // 三個檔案都要找得到預設值；找不到代表檔案或寫法變了，必須由人決定新的預設值是否安全，不能默默通過。
        assertThat(shippedDefaults.values()).hasSize(3).doesNotContainNull();

        shippedDefaults.forEach((file, secret) ->
                assertThatThrownBy(() -> new JwtTokenService(secret, 900_000L, 604_800_000L))
                        .as("%s 出貨的預設密鑰「%s」必須被拒絕（沒設 JWT_SECRET 時不可啟動）", file, secret)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("JWT_SECRET"));
    }

    private static String extract(Pattern pattern, Path file) throws IOException {
        assertThat(file).as("找不到檔案 %s（測試的工作目錄應為 backend/）", file.toAbsolutePath().normalize()).exists();
        Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
        assertThat(matcher.find()).as("%s 找不到 JWT_SECRET 的預設值", file.getFileName()).isTrue();
        return matcher.group(1).trim();
    }
}
