package com.nextkey.ecommerce.core.media;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.nextkey.ecommerce.core.cms.media.MediaValidationService;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.media.MediaCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.media.MediaCategoryRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * {@link MediaService} 單元測試（Sprint 132，DEF-096）。
 *
 * <p>此 Service 先前完全沒有測試覆蓋——僅涵蓋本輪新增的真實檔案上傳（uploadAssetMultipart）
 * 與檔案串流（downloadAsset）兩個方法，既有的分類/資產 CRUD 方法非本輪範圍不補測。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService 單元測試（Sprint 132）")
class MediaServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private MediaCategoryRepository mediaCategoryRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MediaValidationService mediaValidationService;

    @InjectMocks
    private MediaService mediaService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID UPLOADER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUser(UPLOADER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Tenant buildTenant() {
        return Tenant.builder().id(TENANT_ID).name("Test Tenant").build();
    }

    private User buildUploader() {
        return User.builder().id(UPLOADER_ID).email("uploader@test.com").fullName("Uploader").build();
    }

    private MediaCategory buildCategory() {
        return MediaCategory.builder().id(CATEGORY_ID).tenant(buildTenant()).name("Banners").build();
    }

    // ── uploadAssetMultipart ──────────────────────────────────────

    @Test
    @DisplayName("uploadAssetMultipart：無分類上傳，實際寫入 StorageService 並建立 MediaAsset")
    void uploadAssetMultipart_noCategory_uploadsAndSavesAsset() {
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "content".getBytes());

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(userRepository.findById(UPLOADER_ID)).thenReturn(Optional.of(buildUploader()));
        doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
        when(mediaValidationService.determineFileType("image/png")).thenReturn(MediaAsset.FileType.IMAGE);
        when(storageService.uploadFile(any(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(TENANT_ID + "/uuid-banner.png");
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset asset = inv.getArgument(0);
            asset.setId(ASSET_ID);
            return asset;
        });

        var dto = mediaService.uploadAssetMultipart(file, null, List.of("promo"), "alt text", "Banner");

        assertThat(dto.getId()).isEqualTo(ASSET_ID);
        assertThat(dto.getFileName()).isEqualTo("banner.png");
        assertThat(dto.getFilePath()).isEqualTo(TENANT_ID + "/uuid-banner.png");
        assertThat(dto.getTags()).containsExactly("promo");
        assertThat(dto.getCategoryId()).isNull();

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        org.mockito.Mockito.verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getUploader().getId()).isEqualTo(UPLOADER_ID);
        assertThat(captor.getValue().getCategory()).isNull();
    }

    @Test
    @DisplayName("uploadAssetMultipart：帶分類上傳，關聯分類且不拋錯")
    void uploadAssetMultipart_withCategory_associatesCategory() {
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "content".getBytes());

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(userRepository.findById(UPLOADER_ID)).thenReturn(Optional.of(buildUploader()));
        when(mediaCategoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID))
                .thenReturn(Optional.of(buildCategory()));
        doNothing().when(mediaValidationService).validateFileSize(anyLong(), anyString());
        when(mediaValidationService.determineFileType("image/png")).thenReturn(MediaAsset.FileType.IMAGE);
        when(storageService.uploadFile(any(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(TENANT_ID + "/uuid-banner.png");
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = mediaService.uploadAssetMultipart(file, CATEGORY_ID, null, null, null);

        assertThat(dto.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(dto.getCategoryName()).isEqualTo("Banners");
    }

    @Test
    @DisplayName("uploadAssetMultipart：分類不存在拋出 E_4000")
    void uploadAssetMultipart_categoryNotFound_throwsE4000() {
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "content".getBytes());

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(userRepository.findById(UPLOADER_ID)).thenReturn(Optional.of(buildUploader()));
        when(mediaCategoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.uploadAssetMultipart(file, CATEGORY_ID, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4000));
    }

    @Test
    @DisplayName("uploadAssetMultipart：檔案超出大小限制時拋出驗證例外，不寫入儲存層")
    void uploadAssetMultipart_fileTooLarge_throwsAndNeverUploads() {
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", "content".getBytes());

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(userRepository.findById(UPLOADER_ID)).thenReturn(Optional.of(buildUploader()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.E_9000, "File size exceeds limit"))
                .when(mediaValidationService).validateFileSize(anyLong(), anyString());

        assertThatThrownBy(() -> mediaService.uploadAssetMultipart(file, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));

        org.mockito.Mockito.verifyNoInteractions(storageService);
    }

    // ── downloadAsset ──────────────────────────────────────

    @Test
    @DisplayName("downloadAsset：以資產儲存的完整路徑向 StorageService 取檔，而非重新推導路徑")
    void downloadAsset_returnsStreamUsingStoredFilePath() {
        MediaAsset asset = MediaAsset.builder()
                .id(ASSET_ID)
                .tenant(buildTenant())
                .fileName("banner.png")
                .originalName("banner.png")
                .filePath(TENANT_ID + "/uuid-banner.png")
                .fileSize(100L)
                .mimeType("image/png")
                .fileType(MediaAsset.FileType.IMAGE)
                .build();

        when(mediaAssetRepository.findActiveByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset));
        when(storageService.belongsToTenant(TENANT_ID + "/uuid-banner.png", TENANT_ID)).thenReturn(true);
        when(storageService.getObject(TENANT_ID + "/uuid-banner.png"))
                .thenReturn(new ByteArrayInputStream("bytes".getBytes()));

        MediaService.AssetFile result = mediaService.downloadAsset(ASSET_ID);

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.fileName()).isEqualTo("banner.png");
    }

    @Test
    @DisplayName("downloadAsset：資產不存在或不屬於本租戶時拋出 E_4000")
    void downloadAsset_notFound_throwsE4000() {
        when(mediaAssetRepository.findActiveByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.downloadAsset(ASSET_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4000));
    }

    @Test
    @DisplayName("downloadAsset：filePath 未落在本租戶前綴下時拒絕讀取（DEF-101 IDOR 防護），且從不呼叫 StorageService.getObject")
    void downloadAsset_filePathNotOwnedByTenant_throwsE4000AndNeverReadsObject() {
        MediaAsset asset = MediaAsset.builder()
                .id(ASSET_ID)
                .tenant(buildTenant())
                .fileName("victim.png")
                .originalName("victim.png")
                .filePath(UUID.randomUUID() + "/uuid-victim.png") // 另一租戶的物件路徑
                .fileSize(100L)
                .mimeType("image/png")
                .fileType(MediaAsset.FileType.IMAGE)
                .build();

        when(mediaAssetRepository.findActiveByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.downloadAsset(ASSET_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4000));

        org.mockito.Mockito.verify(storageService, org.mockito.Mockito.never()).getObject(anyString());
    }

    // ── uploadAsset(UploadMediaRequest) ──────────────────────────────────

    @Test
    @DisplayName("uploadAsset：filePath 屬於本租戶且物件存在時成功建立 MediaAsset")
    void uploadAsset_validFilePath_createsAsset() {
        com.nextkey.ecommerce.api.dto.media.UploadMediaRequest request =
                com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.builder()
                        .fileName("banner.png")
                        .originalName("banner.png")
                        .filePath(TENANT_ID + "/uuid-banner.png")
                        .fileSize(100L)
                        .mimeType("image/png")
                        .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(storageService.belongsToTenant(TENANT_ID + "/uuid-banner.png", TENANT_ID)).thenReturn(true);
        when(storageService.objectExists(TENANT_ID + "/uuid-banner.png")).thenReturn(true);
        when(mediaValidationService.determineFileType("image/png")).thenReturn(MediaAsset.FileType.IMAGE);
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset asset = inv.getArgument(0);
            asset.setId(ASSET_ID);
            return asset;
        });

        var dto = mediaService.uploadAsset(request);

        assertThat(dto.getId()).isEqualTo(ASSET_ID);
        assertThat(dto.getFilePath()).isEqualTo(TENANT_ID + "/uuid-banner.png");
        org.mockito.Mockito.verify(mediaValidationService).validateFileSize(100L, "image/png");
    }

    @Test
    @DisplayName("🔴 DEF-254：uploadAsset 指定不在白名單內的 mimeType（如 text/html）→ 拋出 E_9000，"
            + "不得靜默落到 DOCUMENT 類型（先前用不拋錯的私有 inferFileType，任意字串皆放行）")
    void uploadAsset_disallowedMimeType_throwsE9000() {
        com.nextkey.ecommerce.api.dto.media.UploadMediaRequest request =
                com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.builder()
                        .fileName("payload.html")
                        .originalName("payload.html")
                        .filePath(TENANT_ID + "/uuid-payload.html")
                        .fileSize(100L)
                        .mimeType("text/html")
                        .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(mediaValidationService.determineFileType("text/html"))
                .thenThrow(new BusinessException(ErrorCode.E_9000, "Unsupported file type: text/html"));

        assertThatThrownBy(() -> mediaService.uploadAsset(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));

        org.mockito.Mockito.verify(mediaAssetRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("🔴 DEF-254：uploadAsset 指定超過大小上限的 fileSize → 拋出 E_9000，"
            + "不得繞過 uploadAssetMultipart 既有的大小驗證")
    void uploadAsset_fileSizeExceedsLimit_throwsE9000() {
        com.nextkey.ecommerce.api.dto.media.UploadMediaRequest request =
                com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.builder()
                        .fileName("huge.png")
                        .originalName("huge.png")
                        .filePath(TENANT_ID + "/uuid-huge.png")
                        .fileSize(999_999_999L)
                        .mimeType("image/png")
                        .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.E_9000, "File size exceeds limit"))
                .when(mediaValidationService).validateFileSize(999_999_999L, "image/png");

        assertThatThrownBy(() -> mediaService.uploadAsset(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));

        org.mockito.Mockito.verify(mediaAssetRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("uploadAsset：filePath 不屬於本租戶時拋出 E_9000（DEF-101 IDOR 防護），且從不寫入資料庫")
    void uploadAsset_filePathNotOwnedByTenant_throwsE9000AndNeverSaves() {
        com.nextkey.ecommerce.api.dto.media.UploadMediaRequest request =
                com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.builder()
                        .fileName("victim.png")
                        .originalName("victim.png")
                        .filePath(UUID.randomUUID() + "/uuid-victim.png")
                        .fileSize(100L)
                        .mimeType("image/png")
                        .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));

        assertThatThrownBy(() -> mediaService.uploadAsset(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));

        org.mockito.Mockito.verify(mediaAssetRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("uploadAsset：filePath 屬於本租戶但物件實際不存在於儲存層時拋出 E_9000")
    void uploadAsset_filePathBelongsButObjectMissing_throwsE9000() {
        com.nextkey.ecommerce.api.dto.media.UploadMediaRequest request =
                com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.builder()
                        .fileName("ghost.png")
                        .originalName("ghost.png")
                        .filePath(TENANT_ID + "/uuid-ghost.png")
                        .fileSize(100L)
                        .mimeType("image/png")
                        .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(storageService.belongsToTenant(TENANT_ID + "/uuid-ghost.png", TENANT_ID)).thenReturn(true);
        when(storageService.objectExists(TENANT_ID + "/uuid-ghost.png")).thenReturn(false);

        assertThatThrownBy(() -> mediaService.uploadAsset(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));
    }

    // ── createCategory（Sprint 143，DEF-160） ────────────────────

    @Test
    @DisplayName("createCategory：根層級併發 TOCTOU 撞上 DB 唯一約束 → 轉譯為 E_3001，而非原始 500")
    void createCategory_concurrentDuplicateRootName_translatesToE3001() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(mediaCategoryRepository.existsByTenantIdAndNameAndParentIsNull(TENANT_ID, "Banners")).thenReturn(false);
        when(mediaCategoryRepository.saveAndFlush(any(MediaCategory.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest request =
                com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest.builder()
                        .name("Banners")
                        .build();

        assertThatThrownBy(() -> mediaService.createCategory(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_3001));
    }

    @Test
    @DisplayName("createCategory：子層級併發 TOCTOU 撞上 DB 唯一約束 → 轉譯為 E_3001")
    void createCategory_concurrentDuplicateChildName_translatesToE3001() {
        MediaCategory parent = buildCategory();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(mediaCategoryRepository.existsByTenantIdAndNameAndParentId(TENANT_ID, "Sub", CATEGORY_ID)).thenReturn(false);
        when(mediaCategoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(parent));
        when(mediaCategoryRepository.saveAndFlush(any(MediaCategory.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest request =
                com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest.builder()
                        .name("Sub").parentId(CATEGORY_ID)
                        .build();

        assertThatThrownBy(() -> mediaService.createCategory(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_3001));
    }
}
