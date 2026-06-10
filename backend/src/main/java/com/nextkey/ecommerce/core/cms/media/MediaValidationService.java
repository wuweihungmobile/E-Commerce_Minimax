package com.nextkey.ecommerce.core.cms.media;

import java.util.List;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * M15 CMS MediaValidationService
 * 媒體驗證服務，處理檔案大小、MIME 類型、格式驗證
 */
@Slf4j
@Service("mediaValidationService")
public class MediaValidationService {

    // 檔案大小限制
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_DOCUMENT_SIZE = 5 * 1024 * 1024; // 5MB

    // 允許的 MIME Types
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/x-msvideo");
    private static final List<String> ALLOWED_DOCUMENT_TYPES = List.of(
            "application/pdf");

    /**
     * 驗證檔案大小
     *
     * @param fileSize 檔案大小 (bytes)
     * @param mimeType MIME 類型
     * @throws BusinessException 如果檔案大小超出限制
     */
    public void validateFileSize(final Long fileSize, final String mimeType) {
        long maxSize = getMaxSizeForType(mimeType);

        if (fileSize > maxSize) {
            throw new BusinessException(ErrorCode.E_9000,
                    String.format("File size exceeds limit: %d > %d", fileSize, maxSize));
        }
    }

    /**
     * 根據 MIME Type 判斷檔案類型
     *
     * @param mimeType MIME 類型
     * @return MediaAsset.FileType
     * @throws BusinessException 如果是不支援的類型
     */
    public MediaAsset.FileType determineFileType(String mimeType) {
        if (ALLOWED_IMAGE_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.IMAGE;
        } else if (ALLOWED_VIDEO_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.VIDEO;
        } else if (ALLOWED_DOCUMENT_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.DOCUMENT;
        } else {
            throw new BusinessException(ErrorCode.E_9000, "Unsupported file type: " + mimeType);
        }
    }

    /**
     * 驗證 MIME Type 是否允許
     *
     * @param mimeType MIME 類型
     * @throws BusinessException 如果是不允許的類型
     */
    public void validateMimeType(final String mimeType) {
        boolean isAllowed = ALLOWED_IMAGE_TYPES.contains(mimeType)
                || ALLOWED_VIDEO_TYPES.contains(mimeType)
                || ALLOWED_DOCUMENT_TYPES.contains(mimeType);

        if (!isAllowed) {
            throw new BusinessException(ErrorCode.E_9000, "Unsupported file type: " + mimeType);
        }
    }

    /**
     * 驗證檔案是否為圖片
     *
     * @param mimeType MIME 類型
     * @return true 如果是圖片
     */
    public boolean isImage(final String mimeType) {
        return ALLOWED_IMAGE_TYPES.contains(mimeType);
    }

    /**
     * 驗證檔案是否為影片
     *
     * @param mimeType MIME 類型
     * @return true 如果是影片
     */
    public boolean isVideo(final String mimeType) {
        return ALLOWED_VIDEO_TYPES.contains(mimeType);
    }

    /**
     * 驗證檔案是否為文件
     *
     * @param mimeType MIME 類型
     * @return true 如果是文件
     */
    public boolean isDocument(final String mimeType) {
        return ALLOWED_DOCUMENT_TYPES.contains(mimeType);
    }

    /**
     * 取得該 MIME 類型允許的最大檔案大小
     *
     * @param mimeType MIME 類型
     * @return 最大檔案大小 (bytes)
     */
    public long getMaxSizeForType(final String mimeType) {
        if (mimeType.startsWith("image/")) {
            return MAX_IMAGE_SIZE;
        } else if (mimeType.startsWith("video/")) {
            return MAX_VIDEO_SIZE;
        } else if (mimeType.equals("application/pdf")) {
            return MAX_DOCUMENT_SIZE;
        } else {
            return 0; // 不支援的類型
        }
    }

    /**
     * 取得允許的圖片 MIME Types
     */
    public List<String> getAllowedImageTypes() {
        return ALLOWED_IMAGE_TYPES;
    }

    /**
     * 取得允許的影片 MIME Types
     */
    public List<String> getAllowedVideoTypes() {
        return ALLOWED_VIDEO_TYPES;
    }

    /**
     * 取得允許的文件 MIME Types
     */
    public List<String> getAllowedDocumentTypes() {
        return ALLOWED_DOCUMENT_TYPES;
    }
}