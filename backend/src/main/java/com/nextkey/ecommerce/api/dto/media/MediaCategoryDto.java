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
public class MediaCategoryDto {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private UUID parentId;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MediaCategoryDto> children;
}