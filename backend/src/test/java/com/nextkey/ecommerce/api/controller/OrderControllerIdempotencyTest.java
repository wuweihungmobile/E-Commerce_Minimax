package com.nextkey.ecommerce.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.idempotency.IdempotencyService;
import com.nextkey.ecommerce.core.order.OrderService;

/**
 * OrderController: 建立訂單的 Idempotency-Key 支援（DEF-285，Sprint 208）。
 *
 * <p>Sprint 207 全庫寫入端點盤點發現 {@code POST /v2/orders} 是金流關鍵路徑裡唯一完全沒有冪等
 * 保護的端點。本測試比照 {@code BookingController.createBooking}／{@code CheckoutController
 * .checkoutMixedCart} 既有邏輯（未變更，僅套用到新端點），純以 mock 驗證控制器分支，不啟動
 * Spring context／Redis——冪等鍵的 Redis 語意已由 {@code IdempotencyServiceTest} 覆蓋，
 * 這裡只驗證控制器怎麼呼叫它。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderController: 建立訂單的 Idempotency-Key（DEF-285）")
class OrderControllerIdempotencyTest {

    @Mock private OrderService orderService;
    @Mock private IdempotencyService idempotencyService;

    private OrderController controller;

    private static final String VALID_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, idempotencyService);
    }

    private OrderDto.CreateRequest request() {
        return new OrderDto.CreateRequest();
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-001: 沒帶 Idempotency-Key → 直接建單（向後相容），不碰 idempotencyService")
    void noKey_createsOrderDirectly() {
        OrderDto.OrderResponse order = OrderDto.OrderResponse.builder().build();
        when(orderService.createOrderFromCart(any())).thenReturn(order);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = controller.createOrder(null, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isSameAs(order);
        verify(idempotencyService, never()).checkAndMark(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-002: 空白 Idempotency-Key（比照有帶但全空白）→ 視為沒帶")
    void blankKey_createsOrderDirectly() {
        OrderDto.OrderResponse order = OrderDto.OrderResponse.builder().build();
        when(orderService.createOrderFromCart(any())).thenReturn(order);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = controller.createOrder("   ", request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService, never()).checkAndMark(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-003: 非 UUID v4 格式 → 400 E-9004，不建單")
    void invalidKeyFormat_returns400WithoutCreatingOrder() {
        when(idempotencyService.isValidUuidV4("not-a-uuid")).thenReturn(false);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                controller.createOrder("not-a-uuid", request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("E-9004");
        verify(orderService, never()).createOrderFromCart(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-004: 新的合法 key → 建單成功後標記完成（cache 住回應）")
    void newValidKey_createsOrderAndMarksCompleted() {
        when(idempotencyService.isValidUuidV4(VALID_KEY)).thenReturn(true);
        when(idempotencyService.checkAndMark(VALID_KEY)).thenReturn(true);
        OrderDto.OrderResponse order = OrderDto.OrderResponse.builder().build();
        when(orderService.createOrderFromCart(any())).thenReturn(order);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                controller.createOrder(VALID_KEY, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService).markCompleted(VALID_KEY, order);
        verify(idempotencyService, never()).remove(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-005: 重複 key 且已有快取回應 → 直接回快取，不重新建單（真正的去重效果）")
    void duplicateKeyWithStoredResponse_returnsCachedWithoutCreatingOrder() {
        when(idempotencyService.isValidUuidV4(VALID_KEY)).thenReturn(true);
        when(idempotencyService.checkAndMark(VALID_KEY)).thenReturn(false);
        OrderDto.OrderResponse cached = OrderDto.OrderResponse.builder().build();
        when(idempotencyService.<OrderDto.OrderResponse>getStoredResponse(VALID_KEY)).thenReturn(cached);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                controller.createOrder(VALID_KEY, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isSameAs(cached);
        verify(orderService, never()).createOrderFromCart(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-006: 重複 key 但仍在處理中（尚無快取）→ 409 E-6005")
    void duplicateKeyStillProcessing_returns409() {
        when(idempotencyService.isValidUuidV4(VALID_KEY)).thenReturn(true);
        when(idempotencyService.checkAndMark(VALID_KEY)).thenReturn(false);
        when(idempotencyService.<OrderDto.OrderResponse>getStoredResponse(VALID_KEY)).thenReturn(null);

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                controller.createOrder(VALID_KEY, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("E-6005");
        verify(orderService, never()).createOrderFromCart(any());
    }

    @Test
    @DisplayName("UT-ORDER-IDEM-007: 建單過程拋例外 → 清除 key（讓客戶端可用同一把 key 重試），例外照樣往外拋")
    void createOrderThrows_removesKeySoClientCanRetry() {
        when(idempotencyService.isValidUuidV4(VALID_KEY)).thenReturn(true);
        when(idempotencyService.checkAndMark(VALID_KEY)).thenReturn(true);
        when(orderService.createOrderFromCart(any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> controller.createOrder(VALID_KEY, request()))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyService).remove(VALID_KEY);
        verify(idempotencyService, never()).markCompleted(any(), any());
    }
}
