package com.nextkey.ecommerce.api.dto.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaAssetDto {

    private UUID id;
    private UUID tenantId;
    private UUID categoryId;
    private String categoryName;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private List<String> tags;
    private Integer usageCount;
    private String altText;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private String url;
}