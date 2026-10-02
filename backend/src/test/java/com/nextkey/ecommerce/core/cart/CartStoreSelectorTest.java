package com.nextkey.ecommerce.core.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 237（DEF-319 同店結帳）：結帳時決定要結哪一家店鋪。PRD US-008／PC-005：單筆訂單限同一商家。
 * 守的是兩件事：只有一家店鋪時不必多一個參數（既有使用方式不變）；多家店鋪時絕不能「猜一家」，
 * 否則買家會在不知情下替另一家店鋪的商品付款、訂單歸到錯的店鋪。
 */
@DisplayName("Sprint 237: 結帳店鋪選擇（CartStoreSelector）")
class CartStoreSelectorTest {

    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();

    @Test
    @DisplayName("購物車只有一家店鋪、沒指定 → 就是那一家（同一家店的多個項目不重複計算）")
    void singleStore_noRequest_isThatStore() {
        assertThat(CartStoreSelector.select(List.of(STORE_A, STORE_A, STORE_A), null)).isEqualTo(STORE_A);
    }

    @Test
    @DisplayName("多家店鋪、沒指定 → E-5020，不可猜一家")
    void multipleStores_noRequest_isRejected() {
        assertThatThrownBy(() -> CartStoreSelector.select(List.of(STORE_A, STORE_B), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));
    }

    @Test
    @DisplayName("多家店鋪、指定了其中一家 → 那一家")
    void multipleStores_requested_isThatStore() {
        assertThat(CartStoreSelector.select(List.of(STORE_A, STORE_B), STORE_B)).isEqualTo(STORE_B);
    }

    @Test
    @DisplayName("指定的店鋪在購物車裡沒有項目 → E-5004（不可替沒有項目的店鋪建立空訂單）")
    void requestedStoreWithoutItems_isRejected() {
        assertThatThrownBy(() -> CartStoreSelector.select(List.of(STORE_A), STORE_B))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5004));
    }

    @Test
    @DisplayName("購物車沒有任何店鋪的項目 → E-5004")
    void noStores_isRejected() {
        assertThatThrownBy(() -> CartStoreSelector.select(Collections.emptyList(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5004));
    }

    @Test
    @DisplayName("房源已被刪除的項目（storeId 為 null）不參與選擇：與一家店鋪並存時仍視為單一店鋪，只有它時視為空")
    void itemsWithoutStore_areIgnored() {
        assertThat(CartStoreSelector.select(Arrays.asList(null, STORE_A, null), null)).isEqualTo(STORE_A);
        assertThatThrownBy(() -> CartStoreSelector.select(Arrays.asList(null, null), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5004));
    }
}
