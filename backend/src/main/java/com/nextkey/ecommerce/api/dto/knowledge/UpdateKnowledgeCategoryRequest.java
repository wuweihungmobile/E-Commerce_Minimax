package com.nextkey.ecommerce.api.dto.knowledge;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKnowledgeCategoryRequest {

    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Slug must not exceed 100 characters")
    private String slug;

    private String description;

    @Size(max = 50, message = "Icon must not exceed 50 characters")
    private String icon;

    private Integer sortOrder;
}