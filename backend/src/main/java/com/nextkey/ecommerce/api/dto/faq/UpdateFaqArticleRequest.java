package com.nextkey.ecommerce.api.dto.faq;

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
public class UpdateFaqArticleRequest {

    private UUID categoryId;

    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    private String answer;

    @Size(max = 100, message = "Slug must not exceed 100 characters")
    private String slug;

    private Integer sortOrder;

    private Boolean isPinned;

    private Boolean isPublished;
}