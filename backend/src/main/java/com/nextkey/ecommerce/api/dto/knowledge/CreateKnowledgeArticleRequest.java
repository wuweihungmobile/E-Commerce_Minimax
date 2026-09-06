package com.nextkey.ecommerce.api.dto.knowledge;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateKnowledgeArticleRequest {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Slug is required")
    @Size(max = 200, message = "Slug must not exceed 200 characters")
    private String slug;

    private String content;

    private String excerpt;

    private String coverImageUrl;

    private List<String> tags;

    private Boolean isPinned;
}