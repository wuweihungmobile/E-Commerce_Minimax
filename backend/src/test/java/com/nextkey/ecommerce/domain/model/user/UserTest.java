package com.nextkey.ecommerce.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DEF-249（Sprint 183）：{@code User.passwordHash} 全域 {@code @JsonIgnore} 防線的直接證明。
 *
 * <p>{@code ArticleVersionController} 先前直接回傳裸 {@code ArticleVersion} 實體，其 LAZY
 * {@code createdBy}/{@code article.author} 兩條關聯鏈皆指向 {@code User}，被 Jackson 序列化時
 * 會把整個 {@code User}（含 {@code passwordHash}）序列化進 API 回應。當時的修法只在
 * {@code ArticleVersionController} 改回傳 DTO（見 {@code KnowledgeBaseServiceTest}），本測試
 * 額外直接證明根因本身（欄位缺 {@code @JsonIgnore}）已被堵住——即使未來又有其他 Controller
 * 重蹈覆轍、不小心序列化了裸 {@code User} 或其關聯，{@code passwordHash} 也不會外洩。
 */
@DisplayName("User 單元測試（Sprint 183，DEF-249）")
class UserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("🔴 DEF-249：Jackson 序列化 User 時，passwordHash 絕不可出現在輸出 JSON 中")
    void serialization_neverIncludesPasswordHash() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("victim@example.com")
                .passwordHash("$2a$10$superSecretHashShouldNeverLeak")
                .fullName("Victim User")
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash");
        assertThat(json).doesNotContain("superSecretHashShouldNeverLeak");
        // 確認斷言本身有效（不是因為欄位剛好是 null 才通過）：email 等其他欄位確實有被序列化出來。
        assertThat(json).contains("victim@example.com");
    }
}
