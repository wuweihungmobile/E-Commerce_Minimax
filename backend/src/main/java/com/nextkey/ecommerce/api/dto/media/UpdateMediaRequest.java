package com.nextkey.ecommerce.api.dto.media;

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

    private UUID categoryId;

    private List<String> tags;

    private String altText;

    private String title;

    /** 公開開關（Sprint 336，DEF-482）；null＝不變。 */
    private Boolean isPublic;
}