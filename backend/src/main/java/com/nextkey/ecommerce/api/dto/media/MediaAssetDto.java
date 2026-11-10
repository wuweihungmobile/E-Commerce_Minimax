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
    /** 需要登入的檔案網址（限本店鋪），媒體中心自己用帶憑證的元件顯示。 */
    private String url;
    /** 公開開關（Sprint 336，DEF-482）。用 Boolean 而不是 boolean：原始型別的 Lombok 取值器是 isPublic()，Jackson 會把屬性名讀成 public。 */
    private Boolean isPublic;
    /** 匿名可讀的網址，只有公開的素材才有；沒有公開就不帶這個欄位。 */
    private String publicUrl;
}