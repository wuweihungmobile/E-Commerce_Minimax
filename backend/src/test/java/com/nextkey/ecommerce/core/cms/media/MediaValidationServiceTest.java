package com.nextkey.ecommerce.core.cms.media;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MediaValidationService 單元測試
 *
 * 測試範圍：
 * - validateFileSize() - 驗證檔案大小
 * - determineFileType() - 判斷檔案類型
 * - validateMimeType() - 驗證 MIME Type
 * - isImage/isVideo/isDocument() - 類型檢查
 * - getMaxSizeForType() - 取得最大檔案大小
 */
@DisplayName("MediaValidationService: 媒體驗證")
class MediaValidationServiceTest {

    private final MediaValidationService mediaValidationService = new MediaValidationService();

    // 測試資料
    private static final long ONE_MB = 1024 * 1024L;

    // ── validateFileSize() Tests ───────────────────────────────────────

    @Nested
    @DisplayName("validateFileSize()")
    class ValidateFileSize {

        @Test
        @DisplayName("validateFileSize_image_withinLimit_success")
        void validateFileSize_image_withinLimit_success() {
            // 5MB image (limit is 10MB)
            assertThatCode(() ->
                    mediaValidationService.validateFileSize(5 * ONE_MB, "image/jpeg")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateFileSize_image_atLimit_success")
        void validateFileSize_image_atLimit_success() {
            // 10MB image (limit is exactly 10MB)
            assertThatCode(() ->
                    mediaValidationService.validateFileSize(10 * ONE_MB, "image/png")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateFileSize_image_exceedsLimit_throwsException")
        void validateFileSize_image_exceedsLimit_throwsException() {
            // 15MB image (limit is 10MB)
            assertThatThrownBy(() ->
                    mediaValidationService.validateFileSize(15 * ONE_MB, "image/jpeg")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("File size exceeds limit");
                    });
        }

        @Test
        @DisplayName("validateFileSize_video_withinLimit_success")
        void validateFileSize_video_withinLimit_success() {
            // 50MB video (limit is 100MB)
            assertThatCode(() ->
                    mediaValidationService.validateFileSize(50 * ONE_MB, "video/mp4")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateFileSize_video_exceedsLimit_throwsException")
        void validateFileSize_video_exceedsLimit_throwsException() {
            // 150MB video (limit is 100MB)
            assertThatThrownBy(() ->
                    mediaValidationService.validateFileSize(150 * ONE_MB, "video/mp4")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                    });
        }

        @Test
        @DisplayName("validateFileSize_document_withinLimit_success")
        void validateFileSize_document_withinLimit_success() {
            // 3MB PDF (limit is 5MB)
            assertThatCode(() ->
                    mediaValidationService.validateFileSize(3 * ONE_MB, "application/pdf")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateFileSize_document_exceedsLimit_throwsException")
        void validateFileSize_document_exceedsLimit_throwsException() {
            // 10MB PDF (limit is 5MB)
            assertThatThrownBy(() ->
                    mediaValidationService.validateFileSize(10 * ONE_MB, "application/pdf")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                    });
        }

        @Test
        @DisplayName("validateFileSize_unsupportedType_throwsException")
        void validateFileSize_unsupportedType_throwsException() {
            // Unknown type
            assertThatThrownBy(() ->
                    mediaValidationService.validateFileSize(1 * ONE_MB, "application/x-unknown")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                    });
        }
    }

    // ── determineFileType() Tests ──────────────────────────────────────

    @Nested
    @DisplayName("determineFileType()")
    class DetermineFileType {

        @ParameterizedTest
        @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp"})
        @DisplayName("determineFileType_imageTypes_returnsIMAGE")
        void determineFileType_imageTypes_returnsIMAGE(String mimeType) {
            assertThat(mediaValidationService.determineFileType(mimeType))
                    .isEqualTo(MediaAsset.FileType.IMAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"video/mp4", "video/quicktime", "video/x-msvideo"})
        @DisplayName("determineFileType_videoTypes_returnsVIDEO")
        void determineFileType_videoTypes_returnsVIDEO(String mimeType) {
            assertThat(mediaValidationService.determineFileType(mimeType))
                    .isEqualTo(MediaAsset.FileType.VIDEO);
        }

        @Test
        @DisplayName("determineFileType_pdf_returnsDOCUMENT")
        void determineFileType_pdf_returnsDOCUMENT() {
            assertThat(mediaValidationService.determineFileType("application/pdf"))
                    .isEqualTo(MediaAsset.FileType.DOCUMENT);
        }

        @Test
        @DisplayName("determineFileType_unsupportedType_throwsException")
        void determineFileType_unsupportedType_throwsException() {
            assertThatThrownBy(() ->
                    mediaValidationService.determineFileType("application/x-executable")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("Unsupported file type");
                    });
        }
    }

    // ── validateMimeType() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("validateMimeType()")
    class ValidateMimeType {

        @Test
        @DisplayName("validateMimeType_allowedImage_noException")
        void validateMimeType_allowedImage_noException() {
            assertThatCode(() ->
                    mediaValidationService.validateMimeType("image/jpeg")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateMimeType_allowedVideo_noException")
        void validateMimeType_allowedVideo_noException() {
            assertThatCode(() ->
                    mediaValidationService.validateMimeType("video/mp4")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateMimeType_allowedDocument_noException")
        void validateMimeType_allowedDocument_noException() {
            assertThatCode(() ->
                    mediaValidationService.validateMimeType("application/pdf")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateMimeType_unsupported_throwsException")
        void validateMimeType_unsupported_throwsException() {
            assertThatThrownBy(() ->
                    mediaValidationService.validateMimeType("application/x-executable")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                    });
        }
    }

    // ── isImage/isVideo/isDocument() Tests ─────────────────────────────

    @Nested
    @DisplayName("Type Check Methods")
    class TypeCheckMethods {

        @Test
        @DisplayName("isImage_jpeg_returnsTrue")
        void isImage_jpeg_returnsTrue() {
            assertThat(mediaValidationService.isImage("image/jpeg")).isTrue();
        }

        @Test
        @DisplayName("isImage_mp4_returnsFalse")
        void isImage_mp4_returnsFalse() {
            assertThat(mediaValidationService.isImage("video/mp4")).isFalse();
        }

        @Test
        @DisplayName("isVideo_mp4_returnsTrue")
        void isVideo_mp4_returnsTrue() {
            assertThat(mediaValidationService.isVideo("video/mp4")).isTrue();
        }

        @Test
        @DisplayName("isVideo_jpeg_returnsFalse")
        void isVideo_jpeg_returnsFalse() {
            assertThat(mediaValidationService.isVideo("image/jpeg")).isFalse();
        }

        @Test
        @DisplayName("isDocument_pdf_returnsTrue")
        void isDocument_pdf_returnsTrue() {
            assertThat(mediaValidationService.isDocument("application/pdf")).isTrue();
        }

        @Test
        @DisplayName("isDocument_jpeg_returnsFalse")
        void isDocument_jpeg_returnsFalse() {
            assertThat(mediaValidationService.isDocument("image/jpeg")).isFalse();
        }
    }

    // ── getMaxSizeForType() Tests ────────────────────────────────────────

    @Nested
    @DisplayName("getMaxSizeForType()")
    class GetMaxSizeForType {

        @Test
        @DisplayName("getMaxSizeForType_image_returns10MB")
        void getMaxSizeForType_image_returns10MB() {
            assertThat(mediaValidationService.getMaxSizeForType("image/jpeg"))
                    .isEqualTo(10 * ONE_MB);
        }

        @Test
        @DisplayName("getMaxSizeForType_video_returns100MB")
        void getMaxSizeForType_video_returns100MB() {
            assertThat(mediaValidationService.getMaxSizeForType("video/mp4"))
                    .isEqualTo(100 * ONE_MB);
        }

        @Test
        @DisplayName("getMaxSizeForType_pdf_returns5MB")
        void getMaxSizeForType_pdf_returns5MB() {
            assertThat(mediaValidationService.getMaxSizeForType("application/pdf"))
                    .isEqualTo(5 * ONE_MB);
        }

        @Test
        @DisplayName("getMaxSizeForType_unknown_returns0")
        void getMaxSizeForType_unknown_returns0() {
            assertThat(mediaValidationService.getMaxSizeForType("application/x-unknown"))
                    .isEqualTo(0);
        }
    }

    // ── Allowed Types Getters Tests ─────────────────────────────────────

    @Nested
    @DisplayName("Allowed Types Getters")
    class AllowedTypesGetters {

        @Test
        @DisplayName("getAllowedImageTypes_returns4Types")
        void getAllowedImageTypes_returns4Types() {
            List<String> types = mediaValidationService.getAllowedImageTypes();
            assertThat(types).hasSize(4);
            assertThat(types).contains("image/jpeg", "image/png", "image/gif", "image/webp");
        }

        @Test
        @DisplayName("getAllowedVideoTypes_returns3Types")
        void getAllowedVideoTypes_returns3Types() {
            List<String> types = mediaValidationService.getAllowedVideoTypes();
            assertThat(types).hasSize(3);
            assertThat(types).contains("video/mp4", "video/quicktime", "video/x-msvideo");
        }

        @Test
        @DisplayName("getAllowedDocumentTypes_returns1Type")
        void getAllowedDocumentTypes_returns1Type() {
            List<String> types = mediaValidationService.getAllowedDocumentTypes();
            assertThat(types).hasSize(1);
            assertThat(types).contains("application/pdf");
        }
    }

    // ── validateActualContent() Tests（DEF-099：magic bytes 與宣稱 Content-Type 比對）──

    @Nested
    @DisplayName("validateActualContent()")
    class ValidateActualContent {

        private MockMultipartFile file(String contentType, byte[] content) {
            return new MockMultipartFile("file", "test", contentType, content);
        }

        @Test
        @DisplayName("validateActualContent_jpegMatchesDeclaredType_noException")
        void validateActualContent_jpegMatchesDeclaredType_noException() {
            byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 'J', 'F', 'I', 'F'};
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("image/jpeg", jpeg), "image/jpeg")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_pngMatchesDeclaredType_noException")
        void validateActualContent_pngMatchesDeclaredType_noException() {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("image/png", png), "image/png")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_gifMatchesDeclaredType_noException")
        void validateActualContent_gifMatchesDeclaredType_noException() {
            byte[] gif = "GIF89a-fake-image-data".getBytes();
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("image/gif", gif), "image/gif")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_webpMatchesDeclaredType_noException")
        void validateActualContent_webpMatchesDeclaredType_noException() {
            byte[] webp = "RIFF____WEBPVP8 ".getBytes();
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("image/webp", webp), "image/webp")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_pdfMatchesDeclaredType_noException")
        void validateActualContent_pdfMatchesDeclaredType_noException() {
            byte[] pdf = "%PDF-1.4\n%rest-of-file".getBytes();
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("application/pdf", pdf), "application/pdf")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_mp4MatchesDeclaredType_noException")
        void validateActualContent_mp4MatchesDeclaredType_noException() {
            byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("video/mp4", mp4), "video/mp4")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_quicktimeMatchesDeclaredType_noException")
        void validateActualContent_quicktimeMatchesDeclaredType_noException() {
            byte[] mov = {0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' ', 0, 0, 0, 0};
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("video/quicktime", mov), "video/quicktime")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_aviMatchesDeclaredType_noException")
        void validateActualContent_aviMatchesDeclaredType_noException() {
            byte[] avi = "RIFF____AVI LIST".getBytes();
            assertThatCode(() ->
                    mediaValidationService.validateActualContent(file("video/x-msvideo", avi), "video/x-msvideo")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateActualContent_fakeContentDeclaredAsJpeg_throwsException")
        void validateActualContent_fakeContentDeclaredAsJpeg_throwsException() {
            byte[] html = "<script>alert(1)</script>".getBytes();
            assertThatThrownBy(() ->
                    mediaValidationService.validateActualContent(file("image/jpeg", html), "image/jpeg")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("does not match declared type");
                    });
        }

        @Test
        @DisplayName("validateActualContent_pngBytesDeclaredAsJpeg_throwsException")
        void validateActualContent_pngBytesDeclaredAsJpeg_throwsException() {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
            assertThatThrownBy(() ->
                    mediaValidationService.validateActualContent(file("image/jpeg", png), "image/jpeg")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));
        }

        @Test
        @DisplayName("validateActualContent_mp4BytesDeclaredAsQuicktime_throwsException")
        void validateActualContent_mp4BytesDeclaredAsQuicktime_throwsException() {
            byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
            assertThatThrownBy(() ->
                    mediaValidationService.validateActualContent(file("video/quicktime", mp4), "video/quicktime")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));
        }

        @Test
        @DisplayName("validateActualContent_emptyFile_throwsException")
        void validateActualContent_emptyFile_throwsException() {
            assertThatThrownBy(() ->
                    mediaValidationService.validateActualContent(file("image/jpeg", new byte[0]), "image/jpeg")
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));
        }
    }
}