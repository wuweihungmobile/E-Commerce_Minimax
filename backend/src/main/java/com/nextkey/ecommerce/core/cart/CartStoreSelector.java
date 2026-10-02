package com.nextkey.ecommerce.core.cart;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 決定「這次要結哪一家店鋪」（Sprint 237，DEF-319 同店結帳）。
 *
 * <p>PRD US-008／PC-005：單筆訂單只能包含同一家商家的品項，Phase 1 不支援跨商家訂單；訂單與訂房歸屬商品／房源所屬的店鋪。
 * 購物車本身可以放多家店鋪的項目，但結帳、促銷碼與運費都以「一家店鋪」為單位：
 * <ul>
 *   <li>呼叫端明確指定店鋪：必須是購物車裡真的有項目的店鋪；</li>
 *   <li>沒指定：購物車只有一家店鋪的項目就是那一家（既有單一店鋪的使用方式不必改）；
 *       有多家店鋪就拋 {@link ErrorCode#E_5020}，要求使用者指定店鋪分開結帳。</li>
 * </ul>
 * 不屬於任何店鋪的項目（房源已被刪除，{@code storeId} 為 null）不參與選擇。
 */
public final class CartStoreSelector {

    private CartStoreSelector() {
    }

    public static UUID select(final Collection<UUID> storesInCart, final UUID requestedStoreId) {
        Set<UUID> stores = storesInCart.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedStoreId != null) {
            if (!stores.contains(requestedStoreId)) {
                throw new BusinessException(ErrorCode.E_5004, "No items of the requested store in cart");
            }
            return requestedStoreId;
        }
        if (stores.isEmpty()) {
            throw new BusinessException(ErrorCode.E_5004, "Cart is empty");
        }
        if (stores.size() > 1) {
            throw new BusinessException(ErrorCode.E_5020);
        }
        return stores.iterator().next();
    }
}
