package com.nextkey.ecommerce.api.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMediaCategoryRequest {

    @NotBlank(message = "分類名稱不可為空")
    @Size(max = 100, message = "分類名稱不可超過 100 字")
    private String name;

    private String description;

    private UUID parentId;

    private Integer sortOrder;
}