package com.nextkey.ecommerce.core.cms.media;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MediaService 單元測試
 *
 * 測試範圍：
 * - uploadMedia() - 上傳媒體
 * - getMediaList() - 取得媒體列表
 * - getMedia() - 取得媒體詳情
 * - deleteMedia() - 刪除媒體
 * - isMediaInUse() - 檢查媒體是否被使用
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService: 媒體管理")
class MediaServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private MediaService mediaService;

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

    private MediaAsset buildMediaAsset(MediaAsset.FileType fileType) {
        return MediaAsset.builder()
                .id(TEST_MEDIA_ID)
                .tenant(buildTenant())
                .uploader(buildUploader())
                .fileName("test-file.jpg")
                .originalName("original-file.jpg")
                .filePath("/media/" + TEST_MEDIA_ID + "/test-file.jpg")
                .fileSize(1024 * 1024L) // 1MB
                .mimeType("image/jpeg")
                .fileType(fileType)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    // ── uploadMedia() Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("uploadMedia()")
    class UploadMedia {

        @Test
        @DisplayName("uploadMedia_image_success")
        void uploadMedia_image_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();
            MediaAsset savedMedia = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-file.jpg",
                    "original-file.jpg",
                    1024 * 1024L, // 1MB
                    "image/jpeg",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFileName()).isEqualTo("test-file.jpg");
            assertThat(response.getFileType()).isEqualTo("IMAGE");
            assertThat(response.getId()).isEqualTo(TEST_MEDIA_ID);
            verify(mediaAssetRepository).save(any(MediaAsset.class));
        }

        @Test
        @DisplayName("uploadMedia_video_success")
        void uploadMedia_video_success() {
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
                    .fileSize(50 * 1024 * 1024L) // 50MB
                    .mimeType("video/mp4")
                    .fileType(MediaAsset.FileType.VIDEO)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-video.mp4",
                    "original-video.mp4",
                    50 * 1024 * 1024L, // 50MB
                    "video/mp4",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFileType()).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("uploadMedia_pdf_success")
        void uploadMedia_pdf_success() {
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
                    .fileSize(2 * 1024 * 1024L) // 2MB
                    .mimeType("application/pdf")
                    .fileType(MediaAsset.FileType.DOCUMENT)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMedia);

            // Act
            M15Dto.MediaUploadResponse response = mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-doc.pdf",
                    "original-doc.pdf",
                    2 * 1024 * 1024L, // 2MB
                    "application/pdf",
                    "/media/test-path"
            );

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFileType()).isEqualTo("DOCUMENT");
        }

        @Test
        @DisplayName("uploadMedia_tenantNotFound_throwsException")
        void uploadMedia_tenantNotFound_throwsException() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-file.jpg",
                    "original-file.jpg",
                    1024 * 1024L,
                    "image/jpeg",
                    "/media/test-path"
            ))
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
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "test-file.jpg",
                    "original-file.jpg",
                    1024 * 1024L,
                    "image/jpeg",
                    "/media/test-path"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1006);
                    });
        }

        @Test
        @DisplayName("uploadMedia_imageExceedsLimit_throwsException")
        void uploadMedia_imageExceedsLimit_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));

            // Act & Assert - 15MB image (limit is 10MB)
            assertThatThrownBy(() -> mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "large-image.jpg",
                    "large-image.jpg",
                    15 * 1024 * 1024L,
                    "image/jpeg",
                    "/media/test-path"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("File size exceeds limit");
                    });
        }

        @Test
        @DisplayName("uploadMedia_videoExceedsLimit_throwsException")
        void uploadMedia_videoExceedsLimit_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));

            // Act & Assert - 150MB video (limit is 100MB)
            assertThatThrownBy(() -> mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "large-video.mp4",
                    "large-video.mp4",
                    150 * 1024 * 1024L,
                    "video/mp4",
                    "/media/test-path"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("File size exceeds limit");
                    });
        }

        @Test
        @DisplayName("uploadMedia_unsupportedMimeType_throwsException")
        void uploadMedia_unsupportedMimeType_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User uploader = buildUploader();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_UPLOADER_ID)).thenReturn(Optional.of(uploader));

            // Act & Assert - executable file
            assertThatThrownBy(() -> mediaService.uploadMedia(
                    TEST_TENANT_ID,
                    TEST_UPLOADER_ID,
                    "malware.exe",
                    "malware.exe",
                    1024 * 1024L,
                    "application/x-executable",
                    "/media/test-path"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("Unsupported file type");
                    });
        }
    }

    // ── getMediaList() Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("getMediaList()")
    class GetMediaList {

        @Test
        @DisplayName("getMediaList_allMedia_success")
        void getMediaList_allMedia_success() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);
            Page<MediaAsset> mediaPage = new PageImpl<>(List.of(media));

            when(mediaAssetRepository.findByTenant_Id(eq(TEST_TENANT_ID), any(Pageable.class)))
                    .thenReturn(mediaPage);

            // Act
            M15Dto.MediaListResponse response = mediaService.getMediaList(TEST_TENANT_ID, 0, 10, null);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMediaList_filterByFileType_success")
        void getMediaList_filterByFileType_success() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);
            Page<MediaAsset> mediaPage = new PageImpl<>(List.of(media));

            when(mediaAssetRepository.findByTenant_IdAndFileType(
                    eq(TEST_TENANT_ID), eq(MediaAsset.FileType.IMAGE), any(Pageable.class)))
                    .thenReturn(mediaPage);

            // Act
            M15Dto.MediaListResponse response = mediaService.getMediaList(
                    TEST_TENANT_ID, 0, 10, MediaAsset.FileType.IMAGE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("getMediaList_emptyList_returnsEmpty")
        void getMediaList_emptyList_returnsEmpty() {
            // Arrange
            Page<MediaAsset> emptyPage = new PageImpl<>(List.of());

            when(mediaAssetRepository.findByTenant_Id(eq(TEST_TENANT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            M15Dto.MediaListResponse response = mediaService.getMediaList(TEST_TENANT_ID, 0, 10, null);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getMediaList_tenantIsolation")
        void getMediaList_tenantIsolation() {
            // Arrange
            UUID otherTenantId = UUID.randomUUID();
            Page<MediaAsset> emptyPage = new PageImpl<>(List.of());

            when(mediaAssetRepository.findByTenant_Id(eq(otherTenantId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            M15Dto.MediaListResponse response = mediaService.getMediaList(otherTenantId, 0, 10, null);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getItems()).isEmpty();
            // Verify different tenant ID was used
            verify(mediaAssetRepository).findByTenant_Id(eq(otherTenantId), any(Pageable.class));
        }
    }

    // ── getMedia() Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("getMedia()")
    class GetMedia {

        @Test
        @DisplayName("getMedia_success")
        void getMedia_success() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));

            // Act
            M15Dto.MediaResponse response = mediaService.getMedia(TEST_MEDIA_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_MEDIA_ID);
            assertThat(response.getFileName()).isEqualTo("test-file.jpg");
        }

        @Test
        @DisplayName("getMedia_notFound_throwsException")
        void getMedia_notFound_throwsException() {
            // Arrange
            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> mediaService.getMedia(TEST_MEDIA_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4103);
                    });
        }

        @Test
        @DisplayName("getMedia_wrongTenant_throwsException")
        void getMedia_wrongTenant_throwsException() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);
            UUID wrongTenantId = UUID.randomUUID();

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));

            // Act & Assert
            assertThatThrownBy(() -> mediaService.getMedia(TEST_MEDIA_ID, wrongTenantId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }
    }

    // ── deleteMedia() Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("deleteMedia()")
    class DeleteMedia {

        @Test
        @DisplayName("deleteMedia_success")
        void deleteMedia_success() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));
            when(postRepository.existsByFeaturedImageUrlContaining(anyString())).thenReturn(false);
            doNothing().when(storageService).deleteObject(anyString());

            // Act
            mediaService.deleteMedia(TEST_MEDIA_ID, TEST_TENANT_ID);

            // Assert
            verify(mediaAssetRepository).delete(media);
        }

        @Test
        @DisplayName("deleteMedia_notFound_throwsException")
        void deleteMedia_notFound_throwsException() {
            // Arrange
            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> mediaService.deleteMedia(TEST_MEDIA_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4103);
                    });
        }

        @Test
        @DisplayName("deleteMedia_wrongTenant_throwsException")
        void deleteMedia_wrongTenant_throwsException() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);
            UUID wrongTenantId = UUID.randomUUID();

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));

            // Act & Assert
            assertThatThrownBy(() -> mediaService.deleteMedia(TEST_MEDIA_ID, wrongTenantId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }

        @Test
        @DisplayName("deleteMedia_inUseByPosts_throwsException")
        void deleteMedia_inUseByPosts_throwsException() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));
            when(postRepository.existsByFeaturedImageUrlContaining(anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> mediaService.deleteMedia(TEST_MEDIA_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9000);
                        assertThat(bex.getMessage()).contains("Cannot delete media that is used by posts");
                    });
        }
    }

    // ── isMediaInUse() Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("isMediaInUse()")
    class IsMediaInUse {

        @Test
        @DisplayName("isMediaInUse_returnsTrue")
        void isMediaInUse_returnsTrue() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));
            when(postRepository.existsByFeaturedImageUrlContaining(media.getFilePath())).thenReturn(true);

            // Act
            boolean result = mediaService.isMediaInUse(TEST_MEDIA_ID);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isMediaInUse_returnsFalse")
        void isMediaInUse_returnsFalse() {
            // Arrange
            MediaAsset media = buildMediaAsset(MediaAsset.FileType.IMAGE);

            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.of(media));
            when(postRepository.existsByFeaturedImageUrlContaining(media.getFilePath())).thenReturn(false);

            // Act
            boolean result = mediaService.isMediaInUse(TEST_MEDIA_ID);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isMediaInUse_mediaNotFound_returnsFalse")
        void isMediaInUse_mediaNotFound_returnsFalse() {
            // Arrange
            when(mediaAssetRepository.findById(TEST_MEDIA_ID)).thenReturn(Optional.empty());

            // Act
            boolean result = mediaService.isMediaInUse(TEST_MEDIA_ID);

            // Assert
            assertThat(result).isFalse();
        }
    }
}