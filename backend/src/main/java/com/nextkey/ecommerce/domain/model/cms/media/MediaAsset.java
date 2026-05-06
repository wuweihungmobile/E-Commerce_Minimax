package com.nextkey.ecommerce.domain.model.cms.media;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * M15 CMS MediaAsset Entity
 * 媒體資源實體，支援圖片、影片、文檔
 */
@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    private Integer width;

    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum FileType {
        IMAGE, VIDEO, DOCUMENT
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 取得檔案大小（人類可讀格式）
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return (fileSize / 1024) + " KB";
        } else {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * 檢查是否為圖片
     */
    public boolean isImage() {
        return fileType == FileType.IMAGE;
    }

    /**
     * 檢查是否為影片
     */
    public boolean isVideo() {
        return fileType == FileType.VIDEO;
    }

    /**
     * 檢查是否為文檔
     */
    public boolean isDocument() {
        return fileType == FileType.DOCUMENT;
    }
}