package com.nextkey.ecommerce.api.dto.faq;

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
public class FaqCategoryDto {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String icon;
    private Integer sortOrder;
    private List<FaqArticleDto> articles;
    private Instant createdAt;
    private Instant updatedAt;
}