package com.nextkey.ecommerce.api.dto.faq;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaqArticleDto {

    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String question;
    private String answer;
    private String slug;
    private Integer sortOrder;
    private Integer viewCount;
    private Boolean isPinned;
    private Boolean isPublished;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Phase 2-C: 關鍵字高亮欄位
    private String highlightedQuestion;
    private String highlightedAnswer;
}