package com.nextkey.ecommerce.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.core.media.MediaService;
import com.nextkey.ecommerce.core.media.PublicMediaService;

import lombok.RequiredArgsConstructor;

/**
 * 公開素材的匿名讀取端點（Sprint 336，DEF-482）。
 *
 * <p>{@code /v2/public/**} 在 SecurityConfig 本來就是 permitAll，所以這裡不需要登入、也沒有 {@code @PreAuthorize}。
 * 能讀到什麼完全由 {@link PublicMediaService} 決定（公開＋未刪除＋店鋪 ACTIVE）。
 *
 * <p>快取：公開圖片會被公開頁反覆載入，每次都從物件儲存串流經過後端不划算，所以回 {@code Cache-Control: public, max-age=300}。
 * 代價是店主把素材切回私有或刪除之後，已經被瀏覽器／CDN 快取的副本最多還會有 5 分鐘看得到。
 */
@RestController
@RequestMapping("/v2/public/media")
@RequiredArgsConstructor
public class PublicMediaController {

    /** 公開素材的快取秒數：撤回公開後，已被快取的副本最久還看得到這麼久。 */
    static final long CACHE_SECONDS = 300;

    private final PublicMediaService publicMediaService;

    @GetMapping("/{assetId}")
    public ResponseEntity<InputStreamResource> getPublicFile(@PathVariable final UUID assetId) {
        MediaService.AssetFile file = publicMediaService.downloadPublicAsset(assetId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.maxAge(CACHE_SECONDS, TimeUnit.SECONDS).cachePublic())
                .body(new InputStreamResource(file.inputStream()));
    }
}
