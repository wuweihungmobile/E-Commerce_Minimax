package com.nextkey.ecommerce.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.cart.RedisCartService;

/**
 * Sprint 237（DEF-319 同店結帳）：購物車促銷碼端點的 {@code storeId} 要確實傳到服務層。
 *
 * <p>為什麼要有：促銷碼屬於店鋪，三個端點（套用、驗證、移除）的 {@code storeId} 是選填的 query／body 欄位，
 * 傳遞錯誤（例如忘了傳、傳成買家租戶）不會有編譯錯誤，只會讓消費者的優惠券默默驗證不到。
 * 這個測試把服務層換成 mock，只看控制器把哪些值交給它。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartController: 促銷碼端點的 storeId 傳遞（Sprint 237）")
class CartControllerStoreParamTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STORE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESOLVED_STORE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private RedisCartService cartService;
    @InjectMocks private CartController controller;

    private UserPrincipal consumer() {
        return new UserPrincipal(USER_ID, "consumer@example.com", "BUYER", BUYER_TENANT.toString());
    }

    @Test
    @DisplayName("套用促銷碼：request 的 storeId 與買家租戶一起交給服務層（不是只帶買家租戶）")
    void applyPromo_passesTheRequestedStore() {
        CartDto.ApplyPromoRequest request = CartDto.ApplyPromoRequest.builder().promoCode("SAVE").storeId(STORE).build();
        when(cartService.applyPromoCode(USER_ID, BUYER_TENANT, STORE, "SAVE"))
                .thenReturn(CartDto.ApplyPromoResponse.builder().storeId(STORE).appliedPromoCode("SAVE").build());

        var response = controller.applyPromo(consumer(), request);

        assertThat(response.getBody().getData().getStoreId()).isEqualTo(STORE);
        verify(cartService).applyPromoCode(USER_ID, BUYER_TENANT, STORE, "SAVE");
    }

    @Test
    @DisplayName("套用促銷碼：沒指定店鋪時 storeId 是 null（由服務層依購物車決定，不是控制器猜）")
    void applyPromo_withoutStore_passesNull() {
        CartDto.ApplyPromoRequest request = CartDto.ApplyPromoRequest.builder().promoCode("SAVE").build();
        when(cartService.applyPromoCode(USER_ID, BUYER_TENANT, null, "SAVE"))
                .thenReturn(CartDto.ApplyPromoResponse.builder().appliedPromoCode("SAVE").build());

        controller.applyPromo(consumer(), request);

        verify(cartService).applyPromoCode(USER_ID, BUYER_TENANT, null, "SAVE");
    }

    @Test
    @DisplayName("移除促銷碼：先由服務層決定是哪一家店鋪，再移除那一家的券")
    void removePromo_removesTheResolvedStoresPromo() {
        when(cartService.resolveStoreId(USER_ID, BUYER_TENANT, STORE)).thenReturn(RESOLVED_STORE);

        controller.removePromo(consumer(), STORE);

        verify(cartService).removePromoCode(USER_ID, BUYER_TENANT, RESOLVED_STORE);
    }

    @Test
    @DisplayName("驗證促銷碼：在服務層決定的店鋪驗證，不是買家租戶")
    void validatePromo_validatesInTheResolvedStore() {
        when(cartService.resolveStoreIdForValidation(USER_ID, BUYER_TENANT, STORE)).thenReturn(RESOLVED_STORE);
        when(cartService.validatePromoCode("SAVE", RESOLVED_STORE))
                .thenReturn(CartDto.PromoValidationResult.valid("SAVE", "FIXED_AMOUNT", java.math.BigDecimal.TEN, null));

        var response = controller.validatePromo(consumer(), "SAVE", STORE);

        assertThat(response.getBody().getData().isValid()).isTrue();
        verify(cartService).validatePromoCode("SAVE", RESOLVED_STORE);
    }
}
