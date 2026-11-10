package com.nextkey.ecommerce.core.media;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 匿名讀取「公開」素材（Sprint 336，DEF-482）。
 *
 * <p>為什麼不放進 {@link MediaService}：MediaService 的每個方法都以「呼叫者的店鋪」為邊界（{@code getCurrentTenant()}，
 * DEF-101 的跨租戶防護）；這裡是唯一一條有意不看呼叫者的讀取路徑——匿名者沒有租戶，素材自己的店鋪才是依據。
 * 分成獨立的類別，讓「哪些程式碼不受租戶限制」一目了然，也避免有人順手把這裡的寬鬆條件搬去需要登入的路徑。
 *
 * <p>條件（缺一就當作找不到，一律同一個錯誤，不區分私有、已刪除、店鋪停權或根本不存在，避免被拿來列舉）：
 * 素材是公開的、沒被刪除、所屬店鋪是 ACTIVE。檔案實際的儲存路徑還要屬於素材自己的店鋪
 * （與 {@link MediaService#downloadAsset} 同一個縱深防護，只是租戶換成素材自己的）。
 */
@Service
@RequiredArgsConstructor
public class PublicMediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public MediaService.AssetFile downloadPublicAsset(final UUID assetId) {
        MediaAsset asset = mediaAssetRepository.findPublicActiveById(assetId, Tenant.TenantStatus.ACTIVE)
                .orElseThrow(PublicMediaService::notFound);
        if (!storageService.belongsToTenant(asset.getFilePath(), asset.getTenant().getId())) {
            throw notFound();
        }
        return new MediaService.AssetFile(
                storageService.getObject(asset.getFilePath()), asset.getMimeType(), asset.getOriginalName());
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.E_4103, "Media asset not found");
    }
}
