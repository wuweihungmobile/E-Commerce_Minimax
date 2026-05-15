package com.nextkey.ecommerce.api.dto.media;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMediaRequest {

    @NotNull(message = "分類 ID 不可為空")
    private UUID categoryId;

    private List<String> tags;

    private String altText;

    private String title;
}