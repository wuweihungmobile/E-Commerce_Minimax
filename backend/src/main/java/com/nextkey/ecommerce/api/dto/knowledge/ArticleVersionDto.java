package com.nextkey.ecommerce.api.dto.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DEF-249（Sprint 183）：{@code ArticleVersionController} 先前直接回傳裸 {@code ArticleVersion}
 * 實體，其 {@code createdBy}（LAZY {@code User}）與 {@code article.author}（同樣 LAZY
 * {@code User}）兩條關聯鏈會被 Jackson 序列化出去，外洩 {@code User.passwordHash}。改回傳此
 * 純量欄位 DTO，比照 {@link KnowledgeArticleDto} 既有的 {@code authorId}/{@code authorName}
 * 命名慣例，不夾帶任何實體關聯物件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleVersionDto {

    private UUID id;
    private UUID articleId;
    private Integer versionNumber;
    private String title;
    private String content;
    private List<String> tags;
    private UUID categoryId;
    private String categoryName;
    private Boolean isPublished;
    private Boolean isPinned;
    private Integer sortOrder;
    private UUID tenantId;
    private UUID createdById;
    private String createdByName;
    private Instant createdAt;
}
