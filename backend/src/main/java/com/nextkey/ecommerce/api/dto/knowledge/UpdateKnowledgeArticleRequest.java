package com.nextkey.ecommerce.api.dto.knowledge;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKnowledgeArticleRequest {

    private UUID categoryId;

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 200, message = "Slug must not exceed 200 characters")
    private String slug;

    private String content;

    private String excerpt;

    private String coverImageUrl;

    private String status;

    private List<String> tags;

    private Boolean isPinned;
}