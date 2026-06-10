package com.nextkey.ecommerce.core.cms.media;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MediaUploadService 單元測試
 *
 * 測試範圍：
 * - uploadMedia() with MultipartFile - 上傳媒體
 * - uploadMedia() with path string - 上傳媒體（路徑方式）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MediaUploadService: 媒體上傳")
class MediaUploadServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MediaValidationService mediaValidationService;

    @InjectMocks
    private MediaUploadService mediaUploadService;

    // 測試資料
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_UPLOADER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_MEDIA_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");

    private Tenant buildTenant() {
        return Tenant.builder()
                .id(TEST_TENANT_ID)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
    }

    private User buildUploader() {
        return User.builder()
                .id(TEST_UPLOADER_ID)
                .email("uploader@test.com")
                .fullName("Test Uploader")
                .role(User.UserRole.STORE_OWNER)
                .tenantId(TEST_TENANT_ID)
                .build();
    }

    private MediaAsset buildSavedMediaAsset() {
        return MediaAsset.builder()
                .id(TEST_MEDIA_ID)
                .tenant(buildTenant())
                .uploader(buildUploader())
                .fileName("test-file.jpg")
                .originalName("original-file.jpg")
                .filePath("/media/" + TEST_MEDIA_ID + "/test-file.jpg")
                .fileSize(1024 * 1024L) // 1MB
                .mimeType("image/jpeg")
                .fileType(MediaAsset.FileType.IMAGE)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    // ── uploadMedia(MultipartFile) Tests ────────────────────────────────

    @Nested
    @DisplayName("uploadMedia(MultipartFile)")
    class UploadMediaMultipartFile {

        @Test
        @DisplayName("uploadMedia_jpeg_success")
        void uploadMedia_jpeg_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MediaAsset savedMedia = buildSavedMediaAsset();

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-file.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
            when(mediaValidationService.determineFileType("image/jpeg"))
                    .thenReturn(MediaAsset.FileType.IMAGE);
            when(storageService.uploadFile(any(), anyString(), any(), anyLong(), anyString()))
                    .thenReturn("/media/" + TEST_MEDIA_ID + "/test-file.jpg");
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaUploadService.uploadMedia(
                    TEST_TENANT_ID, TEST_UPLOADER_ID, file);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_MEDIA_ID);
            assertThat(response.getFileName()).isEqualTo("test-file.jpg");
            assertThat(response.getFileType()).isEqualTo("IMAGE");

            // Verify file size validation was called
            verify(mediaValidationService).validateFileSize(anyLong(), eq("image/jpeg"));
        }

        @Test
        @DisplayName("uploadMedia_tenantNotFound_throwsException")
        void uploadMedia_tenantNotFound_throwsException() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-file.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    mediaUploadService.uploadMedia(TEST_TENANT_ID, TEST_UPLOADER_ID, file)
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }

        @Test
        @DisplayName("uploadMedia_uploaderNotFound_throwsException")
        void uploadMedia_uploaderNotFound_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-file.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    mediaUploadService.uploadMedia(TEST_TENANT_ID, TEST_UPLOADER_ID, file)
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1006);
                    });
        }

        @Test
        @DisplayName("uploadMedia_fileTooLarge_throwsException")
        void uploadMedia_fileTooLarge_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "large-file.jpg",
                    "image/jpeg",
                    "test image content".getBytes()
            );

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doThrow(new BusinessException(ErrorCode.E_9000, "File size exceeds limit"))
                    .when(mediaValidationService).validateFileSize(anyLong(), eq("image/jpeg"));

            // Act & Assert
            assertThatThrownBy(() ->
                    mediaUploadService.uploadMedia(TEST_TENANT_ID, TEST_UPLOADER_ID, file)
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("File size exceeds limit");
                    });
        }
    }

    // ── uploadMedia(path string) Tests ──────────────────────────────────

    @Nested
    @DisplayName("uploadMedia(path string)")
    class UploadMediaPathString {

        @Test
        @DisplayName("uploadMedia_pathBased_success")
        void uploadMedia_pathBased_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MediaAsset savedMedia = buildSavedMediaAsset();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
            when(mediaValidationService.determineFileType("image/jpeg"))
                    .thenReturn(MediaAsset.FileType.IMAGE);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaUploadService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-file.jpg",
                    "original-file.jpg",
                    1024 * 1024L,
                    "image/jpeg",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_MEDIA_ID);
            assertThat(response.getFileName()).isEqualTo("test-file.jpg");
            assertThat(response.getFileType()).isEqualTo("IMAGE");

            // Verify MediaAsset was saved with correct data
            ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
            verify(mediaAssetRepository).save(captor.capture());

            MediaAsset saved = captor.getValue();
            assertThat(saved.getFileName()).isEqualTo("test-file.jpg");
            assertThat(saved.getOriginalName()).isEqualTo("original-file.jpg");
            assertThat(saved.getFileSize()).isEqualTo(1024 * 1024L);
            assertThat(saved.getMimeType()).isEqualTo("image/jpeg");
            assertThat(saved.getFileType()).isEqualTo(MediaAsset.FileType.IMAGE);
        }

        @Test
        @DisplayName("uploadMedia_pathBased_pdf_success")
        void uploadMedia_pathBased_pdf_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MediaAsset savedMedia = MediaAsset.builder()
                    .id(TEST_MEDIA_ID)
                    .tenant(tenant)
                    .uploader(uploader)
                    .fileName("test-doc.pdf")
                    .originalName("original-doc.pdf")
                    .filePath("/media/" + TEST_MEDIA_ID + "/test-doc.pdf")
                    .fileSize(2 * 1024 * 1024L)
                    .mimeType("application/pdf")
                    .fileType(MediaAsset.FileType.DOCUMENT)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
            when(mediaValidationService.determineFileType("application/pdf"))
                    .thenReturn(MediaAsset.FileType.DOCUMENT);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaUploadService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-doc.pdf",
                    "original-doc.pdf",
                    2 * 1024 * 1024L,
                    "application/pdf",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFileType()).isEqualTo("DOCUMENT");
            assertThat(response.getMimeType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("uploadMedia_pathBased_video_success")
        void uploadMedia_pathBased_video_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MediaAsset savedMedia = MediaAsset.builder()
                    .id(TEST_MEDIA_ID)
                    .tenant(tenant)
                    .uploader(uploader)
                    .fileName("test-video.mp4")
                    .originalName("original-video.mp4")
                    .filePath("/media/" + TEST_MEDIA_ID + "/test-video.mp4")
                    .fileSize(50 * 1024 * 1024L)
                    .mimeType("video/mp4")
                    .fileType(MediaAsset.FileType.VIDEO)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
            when(mediaValidationService.determineFileType("video/mp4"))
                    .thenReturn(MediaAsset.FileType.VIDEO);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaUploadService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-video.mp4",
                    "original-video.mp4",
                    50 * 1024 * 1024L,
                    "video/mp4",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFileType()).isEqualTo("VIDEO");
            assertThat(response.getMimeType()).isEqualTo("video/mp4");
        }

        @Test
        @DisplayName("uploadMedia_pathBased_tenantNotFound_throwsException")
        void uploadMedia_pathBased_tenantNotFound_throwsException() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    mediaUploadService.uploadMedia(
                            TEST_TENANT_ID,
                            TEST_UPLOADER_ID,
                            "test-file.jpg",
                            "original-file.jpg",
                            1024 * 1024L,
                            "image/jpeg",
                            "/media/test-path"
                    )
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }

        @Test
        @DisplayName("uploadMedia_pathBased_unsupportedType_throwsException")
        void uploadMedia_pathBased_unsupportedType_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            doThrow(new BusinessException(ErrorCode.E_9000, "Unsupported file type"))
                    .when(mediaValidationService).determineFileType("application/x-executable");

            // Act & Assert
            assertThatThrownBy(() ->
                    mediaUploadService.uploadMedia(
                            TEST_TENANT_ID,
                            TEST_UPLOADER_ID,
                            "malware.exe",
                            "malware.exe",
                            1024 * 1024L,
                            "application/x-executable",
                            "/media/test-path"
                    )
            )
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("Unsupported file type");
                    });
        }
    }
}