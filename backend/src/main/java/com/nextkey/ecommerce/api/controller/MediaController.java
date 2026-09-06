package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.media.MediaAssetDto;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaRequest;
import com.nextkey.ecommerce.api.dto.media.UploadMediaRequest;
import com.nextkey.ecommerce.core.media.MediaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 媒體資產 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * 取得媒體列表（分頁/篩選）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<Page<MediaAssetDto>>> getAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String keyword) {
        Page<MediaAssetDto> assets = mediaService.getAssets(page, size, categoryId, mimeType, keyword);
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

    /**
     * 取得媒體詳情
     */
    @GetMapping("/{assetId}")
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<MediaAssetDto>> getAsset(
            @PathVariable UUID assetId) {
        MediaAssetDto asset = mediaService.getAsset(assetId);
        return ResponseEntity.ok(ApiResponse.success(asset));
    }

    /**
     * 上傳媒體
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('media:create')")
    public ResponseEntity<ApiResponse<MediaAssetDto>> uploadAsset(
            @Valid @RequestBody UploadMediaRequest request) {
        MediaAssetDto asset = mediaService.uploadAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Media uploaded successfully", asset));
    }

    /**
     * 上傳媒體（MultipartFile，實際上傳到 S3/MinIO）
     * Sprint 132（DEF-096）：分類/標籤/替代文字/標題皆為選填
     */
    @PostMapping(value = "/upload-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('media:create')")
    public ResponseEntity<ApiResponse<MediaAssetDto>> uploadAssetMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) String title) {
        MediaAssetDto asset = mediaService.uploadAssetMultipart(file, categoryId, tags, altText, title);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Media uploaded successfully", asset));
    }

    /**
     * 取得媒體檔案內容（串流回應，供 &lt;img&gt;/下載使用）
     * Sprint 132（DEF-096）
     */
    @GetMapping("/files/{assetId}")
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable UUID assetId) {
        MediaService.AssetFile file = mediaService.downloadAsset(assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
                .body(new InputStreamResource(file.inputStream()));
    }

    /**
     * 更新媒體
     */
    @PutMapping("/{assetId}")
    @PreAuthorize("hasAuthority('media:update')")
    public ResponseEntity<ApiResponse<MediaAssetDto>> updateAsset(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateMediaRequest request) {
        MediaAssetDto asset = mediaService.updateAsset(assetId, request);
        return ResponseEntity.ok(ApiResponse.success("Media updated successfully", asset));
    }

    /**
     * 刪除媒體（軟刪除）
     */
    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAuthority('media:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @PathVariable UUID assetId) {
        mediaService.deleteAsset(assetId);
        return ResponseEntity.ok(ApiResponse.success("Media deleted successfully", null));
    }

    /**
     * 取得媒體總數
     */
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<Long>> getAssetCount() {
        Long count = mediaService.getAssetCount();
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}