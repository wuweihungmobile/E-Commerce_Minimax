package com.nextkey.ecommerce.core.tenant;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 店鋪能不能被下單的單一判斷（Sprint 239，使用者拍板）。
 *
 * <p>訂單與訂房歸屬商品／房源所屬的店鋪（Sprint 236～237，DEF-319）之後，店鋪的狀態才變得相關：原本訂單蓋成買家的租戶
 * （沒有店鋪的消費者是系統租戶，一定是 ACTIVE），停權或關閉的店鋪不會出現在結帳路徑上。現在只有 {@link Tenant.TenantStatus#ACTIVE}
 * 的店鋪能被下單（待審核、已駁回、停權、終止都不行），避免消費者把款項付給停權的店鋪。
 *
 * <p>只擋「建立」：既有訂單與訂房的付款、取消、退款不受影響——店鋪停權後，消費者仍要能取消與退款。
 */
public final class StoreCheckoutGuard {

    private StoreCheckoutGuard() {
    }

    public static boolean isOpen(final Tenant store) {
        return store != null && store.getStatus() == Tenant.TenantStatus.ACTIVE;
    }

    public static void requireOpen(final Tenant store) {
        if (!isOpen(store)) {
            throw new BusinessException(ErrorCode.E_2010);
        }
    }
}
