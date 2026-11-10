package com.nextkey.ecommerce.core.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 匿名讀取公開素材的條件（Sprint 336，DEF-482）。這裡用 mock 驗證「流程與參數」：只查公開、未刪除、
 * 店鋪 ACTIVE 的素材（過濾條件在 JPQL，真正的語意由 {@code PublicMediaEndpointIntegrationTest} 對真實 PostgreSQL 驗證），
 * 查不到或儲存路徑不屬於素材自己的店鋪，一律是同一個「找不到」，而且不碰儲存層。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sprint 336: PublicMediaService 匿名讀取公開素材")
class PublicMediaServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PublicMediaService publicMediaService;

    private MediaAsset asset(final String filePath) {
        return MediaAsset.builder().id(ASSET_ID).tenant(Tenant.builder().id(TENANT_ID).name("店").build())
                .fileName("hero.png").originalName("封面 hero.png").filePath(filePath).mimeType("image/png")
                .isPublic(true).build();
    }

    @Test
    @DisplayName("找到公開素材 → 回傳儲存層的串流、MIME 與原始檔名；只用素材自己的店鋪驗證儲存路徑")
    void downloadPublicAsset_found_returnsStoredObject() {
        String path = TENANT_ID + "/uuid-hero.png";
        InputStream stream = new ByteArrayInputStream("png-bytes".getBytes());
        when(mediaAssetRepository.findPublicActiveById(ASSET_ID, Tenant.TenantStatus.ACTIVE))
                .thenReturn(Optional.of(asset(path)));
        when(storageService.belongsToTenant(path, TENANT_ID)).thenReturn(true);
        when(storageService.getObject(path)).thenReturn(stream);

        MediaService.AssetFile file = publicMediaService.downloadPublicAsset(ASSET_ID);

        assertThat(file.inputStream()).isSameAs(stream);
        assertThat(file.mimeType()).isEqualTo("image/png");
        assertThat(file.fileName()).isEqualTo("封面 hero.png");
    }

    @Test
    @DisplayName("只查店鋪是 ACTIVE 的素材——停權或審核中的店鋪，圖片不對外（與店鋪首頁、標題的規則一致）")
    void downloadPublicAsset_alwaysAsksForActiveStoresOnly() {
        when(mediaAssetRepository.findPublicActiveById(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicMediaService.downloadPublicAsset(ASSET_ID))
                .isInstanceOf(BusinessException.class);

        verify(mediaAssetRepository).findPublicActiveById(ASSET_ID, Tenant.TenantStatus.ACTIVE);
    }

    @Test
    @DisplayName("查不到（私有、已刪除、店鋪停權、根本不存在都一樣）→ E-4103，而且完全不碰儲存層")
    void downloadPublicAsset_notFound_throwsE4103AndNeverTouchesStorage() {
        when(mediaAssetRepository.findPublicActiveById(ASSET_ID, Tenant.TenantStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicMediaService.downloadPublicAsset(ASSET_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4103));
        verify(storageService, never()).getObject(anyString());
        verify(storageService, never()).belongsToTenant(anyString(), any());
    }

    @Test
    @DisplayName("儲存路徑不屬於素材自己的店鋪（資料列被改壞或被指向別家的物件）→ E-4103，不讀物件（縱深防護，同 DEF-101）")
    void downloadPublicAsset_filePathOfAnotherTenant_throwsE4103AndNeverReadsObject() {
        String foreignPath = UUID.randomUUID() + "/uuid-secret.png";
        when(mediaAssetRepository.findPublicActiveById(ASSET_ID, Tenant.TenantStatus.ACTIVE))
                .thenReturn(Optional.of(asset(foreignPath)));
        when(storageService.belongsToTenant(foreignPath, TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> publicMediaService.downloadPublicAsset(ASSET_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_4103));
        verify(storageService, never()).getObject(anyString());
    }
}
