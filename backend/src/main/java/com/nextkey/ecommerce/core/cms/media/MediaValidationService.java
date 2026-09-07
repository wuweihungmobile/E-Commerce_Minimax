package com.nextkey.ecommerce.core.cms.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 驗證檔案實際內容（magic bytes）是否與宣稱的 MIME Type 相符。
     *
     * <p>{@link MultipartFile#getContentType()} 完全由呼叫端在請求中自行宣告，與檔案實際位元組無關；
     * 本方法讀取檔案開頭位元組比對已知的 magic number，防止偽造 Content-Type 繞過上方的允許清單
     * 與大小分級（DEF-099）。
     *
     * @param file     上傳的檔案
     * @param mimeType 宣稱的 MIME 類型（需已通過 {@link #determineFileType(String)} 檢查）
     * @throws BusinessException 如果實際內容與宣稱類型不符，或讀取檔案失敗
     */
    public void validateActualContent(final MultipartFile file, final String mimeType) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[16];
            int read = inputStream.read(buffer);
            header = read > 0 ? java.util.Arrays.copyOf(buffer, read) : new byte[0];
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.E_9000,
                    "Failed to read file header: " + file.getOriginalFilename());
        }

        if (!matchesMagicBytes(header, mimeType)) {
            throw new BusinessException(ErrorCode.E_9000,
                    "File content does not match declared type: " + mimeType);
        }
    }

    // 已知檔案格式的 magic number（開頭位元組簽章），用於 validateActualContent()
    private static final int BYTE_MASK = 0xFF;
    private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] GIF_SIGNATURE = {'G', 'I', 'F', '8'};
    private static final int[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
    private static final int[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
    private static final int[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final int[] FTYP_SIGNATURE = {'f', 't', 'y', 'p'};
    private static final int[] QUICKTIME_BRAND = {'q', 't', ' ', ' '};
    private static final int[] MOOV_SIGNATURE = {'m', 'o', 'o', 'v'};
    private static final int[] MDAT_SIGNATURE = {'m', 'd', 'a', 't'};
    private static final int[] FREE_ATOM_SIGNATURE = {'f', 'r', 'e', 'e'};
    private static final int[] WIDE_ATOM_SIGNATURE = {'w', 'i', 'd', 'e'};
    private static final int[] AVI_SIGNATURE = {'A', 'V', 'I', ' '};

    private boolean matchesMagicBytes(final byte[] b, final String mimeType) {
        if (mimeType == null) {
            return false;
        }
        switch (mimeType) {
            case "image/jpeg":
                return matchesAt(b, 0, JPEG_SIGNATURE);
            case "image/png":
                return matchesAt(b, 0, PNG_SIGNATURE);
            case "image/gif":
                return matchesAt(b, 0, GIF_SIGNATURE);
            case "image/webp":
                return matchesAt(b, 0, RIFF_SIGNATURE) && matchesAt(b, 8, WEBP_SIGNATURE);
            case "application/pdf":
                return matchesAt(b, 0, PDF_SIGNATURE);
            case "video/mp4":
                return matchesAt(b, 4, FTYP_SIGNATURE) && !matchesAt(b, 8, QUICKTIME_BRAND);
            case "video/quicktime":
                return (matchesAt(b, 4, FTYP_SIGNATURE) && matchesAt(b, 8, QUICKTIME_BRAND))
                        || matchesAt(b, 4, MOOV_SIGNATURE)
                        || matchesAt(b, 4, MDAT_SIGNATURE)
                        || matchesAt(b, 4, FREE_ATOM_SIGNATURE)
                        || matchesAt(b, 4, WIDE_ATOM_SIGNATURE);
            case "video/x-msvideo":
                return matchesAt(b, 0, RIFF_SIGNATURE) && matchesAt(b, 8, AVI_SIGNATURE);
            default:
                return false;
        }
    }

    private boolean matchesAt(final byte[] b, final int offset, final int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & BYTE_MASK) != (expected[i] & BYTE_MASK)) {
                return false;
            }
        }
        return true;
    }
}