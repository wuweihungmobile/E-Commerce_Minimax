package com.nextkey.ecommerce.api.dto.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleDto {

    private UUID id;
    private UUID tenantId;
    private UUID categoryId;
    private String categoryName;
    private UUID authorId;
    private String authorName;
    private String title;
    private String slug;
    private String content;
    private String excerpt;
    private String coverImageUrl;
    private String status;
    private Integer viewCount;
    private Boolean isPinned;
    private List<String> tags;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}