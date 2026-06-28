package com.nextkey.ecommerce.domain.model.cms.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nextkey.ecommerce.domain.model.media.MediaCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M15 CMS MediaAsset Entity
 * 媒體資源實體，支援圖片、影片、文檔
 */
@Entity(name = "CmsMediaAsset")
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    private static final long KB = 1024;
    private static final long MB = 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private User uploader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MediaCategory category;

    @Column(name = "category_id", insertable = false, updatable = false)
    private UUID categoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = List.of();

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "title")
    private String title;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

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

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
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
        if (fileSize < KB) {
            return fileSize + " B";
        } else if (fileSize < KB * MB) {
            return (fileSize / KB) + " KB";
        } else {
            return String.format("%.2f MB", fileSize / (KB * MB));
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

    /**
     * 增加使用次數 (Sprint 16 US-005/006 整合用)
     */
    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }

    /**
     * 取得 Tenant ID (供 Service 層使用)
     */
    public UUID getTenantId() {
        return tenant != null ? tenant.getId() : null;
    }
}