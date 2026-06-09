package com.nextkey.ecommerce.api.dto.media;

import jakarta.validation.constraints.NotBlank;
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
public class UploadMediaRequest {

    @NotNull(message = "分類 ID 不可為空")
    private UUID categoryId;

    @NotBlank(message = "檔案名稱不可為空")
    private String fileName;

    @NotBlank(message = "原始檔案名稱不可為空")
    private String originalName;

    @NotBlank(message = "檔案路徑不可為空")
    private String filePath;

    @NotNull(message = "檔案大小不可為空")
    private Long fileSize;

    @NotBlank(message = "MIME 類型不可為空")
    private String mimeType;

    private List<String> tags;

    private String altText;

    private String title;
}