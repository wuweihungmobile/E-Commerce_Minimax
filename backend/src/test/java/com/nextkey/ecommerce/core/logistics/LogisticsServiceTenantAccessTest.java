package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * LogisticsService 除 createLogistics 之外其餘方法的租戶擁有權檢查（DEF-036）。
 *
 * <p>探查發現：DEF-019 只替 createLogistics 加上 checkOrderTenant，其餘 6 個方法
 * （getLogistics/getLogisticsByOrderId/trackLogistics/getTrackingDetail/updateLogisticsStatus/
 * cancelLogistics）完全沒有租戶擁有權檢查——Controller 層僅要求 order:read/order:update 權限，
 * 任一租戶皆可讀取或竄改他租戶的物流單（跨租戶 IDOR）。cancelLogistics 於
 * {@link LogisticsServiceCancelTest} 另外驗證，本檔涵蓋其餘 5 個方法，比照既有
 * checkOrderTenant 模式修復：本租戶放行、他租戶拒絕（E_1007）、admin 放行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogisticsService 查詢/追蹤/狀態更新的租戶擁有權隔離（DEF-036）")
class LogisticsServiceTenantAccessTest {

    @Mock
    private LogisticsRepository logisticsRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LogisticsProviderFactory logisticsProviderFactory;

    @Mock
    private ProductInventoryService productInventoryService;

    @Mock
    private BuyerNotificationService buyerNotificationService;

    @InjectMocks
    private LogisticsService logisticsService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID logisticsId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOfTenant(final UUID tenantId) {
        return Order.builder().id(orderId).tenantId(tenantId).status(Order.OrderStatus.SHIPPING).build();
    }

    private Logistics logisticsOfOrder() {
        return Logistics.builder()
                .id(logisticsId)
                .orderId(orderId)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .status(Logistics.LogisticsStatus.IN_TRANSIT)
                .build();
    }

    private void setAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== getLogistics ==========

    @Test
    @DisplayName("getLogistics：他租戶讀取他人物流單 → E_1007")
    void getLogistics_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getLogistics：本租戶讀取自己物流單 → 正常回傳")
    void getLogistics_sameTenant_succeeds() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantA);

        assertThatCode(() -> logisticsService.getLogistics(logisticsId)).doesNotThrowAnyException();
    }

    // ========== getLogisticsByOrderId ==========

    @Test
    @DisplayName("getLogisticsByOrderId：他租戶查詢他人訂單物流列表 → E_1007")
    void getLogisticsByOrderId_crossTenant_throwsE1007() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getLogisticsByOrderId：本租戶查詢自己訂單物流列表 → 正常回傳")
    void getLogisticsByOrderId_sameTenant_succeeds() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        when(logisticsRepository.findByOrderId(orderId)).thenReturn(List.of());
        TenantContext.setCurrentTenant(tenantA);

        assertThatCode(() -> logisticsService.getLogisticsByOrderId(orderId)).doesNotThrowAnyException();
    }

    // ========== trackLogistics ==========

    @Test
    @DisplayName("trackLogistics：他租戶追蹤他人物流單 → E_1007")
    void trackLogistics_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.trackLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    // ========== getTrackingDetail ==========

    @Test
    @DisplayName("getTrackingDetail：他租戶查詢他人物流追蹤歷史 → E_1007")
    void getTrackingDetail_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.getTrackingDetail(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    // ========== updateLogisticsStatus ==========

    @Test
    @DisplayName("updateLogisticsStatus：他租戶竄改他人物流狀態 → E_1007（寫入操作，風險更高）")
    void updateLogisticsStatus_crossTenant_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        TenantContext.setCurrentTenant(tenantB);

        assertThatThrownBy(() -> logisticsService.updateLogisticsStatus(logisticsId, Logistics.LogisticsStatus.DELIVERED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("updateLogisticsStatus：admin 跨租戶放行")
    void updateLogisticsStatus_admin_bypassesTenant() {
        Logistics logistics = logisticsOfOrder();
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logistics));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderOfTenant(tenantA)));
        when(logisticsRepository.save(any(Logistics.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setCurrentTenant(tenantB);
        setAdmin();

        assertThatCode(() -> logisticsService.updateLogisticsStatus(logisticsId, Logistics.LogisticsStatus.PICKED_UP))
                .doesNotThrowAnyException();
    }

    // ========== DEF-408：買家本人可「讀」自己訂單的物流（Sprint 301，使用者拍板）==========
    //
    // PRD §10.1.1 路由表寫 /orders/[id]＝「訂單詳情（含物流追蹤）」，但四個讀取方法原本只認「該訂單的店鋪租戶」與 admin；
    // 沒有店鋪的買家的租戶是系統租戶佔位值（DEF-190），所以買家對自己訂單的物流永遠 E_1007。放行的依據是使用者 id
    // （訂單的買家本人），不是租戶——比對租戶會讓任兩個沒有店鋪的買家互通，正是 DEF-190 要防的跨使用者 IDOR。
    // 寫入（建立／更新狀態／取消）不變，仍只限店鋪租戶與 admin。

    private final UUID buyerId = UUID.randomUUID();
    private final UUID otherBuyerId = UUID.randomUUID();

    /** 訂單歸店鋪 {@code tenantA}、買家是 {@code buyer}（真實情境：消費者向店家下單，訂單蓋店鋪租戶，S237）。 */
    private Order orderBoughtBy(final UUID buyer) {
        return Order.builder().id(orderId).tenantId(tenantA).userId(buyer).status(Order.OrderStatus.SHIPPING).build();
    }

    /** 呼叫者是沒有店鋪的買家：租戶是系統租戶佔位值，只有使用者 id 能區分「誰的單」。 */
    private void callAsStorelessBuyer(final UUID userId) {
        TenantContext.setCurrentTenant(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));
        TenantContext.setCurrentUser(userId);
    }

    @Test
    @DisplayName("DEF-408 getLogisticsByOrderId：訂單的買家本人 → 放行（沒有店鋪的買家）")
    void getLogisticsByOrderId_orderOwnerBuyer_succeeds() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));
        when(logisticsRepository.findByOrderId(orderId)).thenReturn(List.of(logisticsOfOrder()));
        callAsStorelessBuyer(buyerId);

        assertThat(logisticsService.getLogisticsByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("🔴 DEF-408 getLogisticsByOrderId：另一位買家（同在系統租戶）讀別人訂單的物流 → 仍 E_1007（DEF-190 跨使用者 IDOR 守門）")
    void getLogisticsByOrderId_otherBuyer_throwsE1007() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));
        callAsStorelessBuyer(otherBuyerId);

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("DEF-408 getLogisticsByOrderId：目前使用者與訂單的買家欄位都是 null → 不得因為 null 相等而放行")
    void getLogisticsByOrderId_bothUserIdsNull_throwsE1007() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(null)));
        TenantContext.setCurrentTenant(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));

        assertThatThrownBy(() -> logisticsService.getLogisticsByOrderId(orderId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("DEF-408 getLogistics：訂單的買家本人 → 放行；另一位買家 → E_1007")
    void getLogistics_buyerOwnerAllowed_otherBuyerDenied() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));

        callAsStorelessBuyer(buyerId);
        assertThatCode(() -> logisticsService.getLogistics(logisticsId)).doesNotThrowAnyException();

        callAsStorelessBuyer(otherBuyerId);
        assertThatThrownBy(() -> logisticsService.getLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("DEF-408 trackLogistics：訂單的買家本人 → 放行；另一位買家 → E_1007")
    void trackLogistics_buyerOwnerAllowed_otherBuyerDenied() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));

        callAsStorelessBuyer(buyerId);
        assertThatCode(() -> logisticsService.trackLogistics(logisticsId)).doesNotThrowAnyException();

        callAsStorelessBuyer(otherBuyerId);
        assertThatThrownBy(() -> logisticsService.trackLogistics(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("DEF-408 getTrackingDetail：訂單的買家本人 → 放行；另一位買家 → E_1007")
    void getTrackingDetail_buyerOwnerAllowed_otherBuyerDenied() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));

        callAsStorelessBuyer(buyerId);
        assertThatCode(() -> logisticsService.getTrackingDetail(logisticsId)).doesNotThrowAnyException();

        callAsStorelessBuyer(otherBuyerId);
        assertThatThrownBy(() -> logisticsService.getTrackingDetail(logisticsId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("🔴 DEF-408 讀取放寬不得外溢到寫入：訂單的買家本人建立物流單 → E_1007")
    void createLogistics_orderOwnerBuyer_throwsE1007() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));
        callAsStorelessBuyer(buyerId);
        LogisticsDto.CreateRequest request = LogisticsDto.CreateRequest.builder()
                .orderId(orderId)
                .logisticsProvider(LogisticsDto.LogisticsProvider.HCT)
                .receiverName("買家").receiverPhone("0912345678").shippingAddress("台北市")
                .build();

        assertThatThrownBy(() -> logisticsService.createLogistics(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("🔴 DEF-408 讀取放寬不得外溢到寫入：訂單的買家本人更新物流狀態 → E_1007")
    void updateLogisticsStatus_orderOwnerBuyer_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));
        callAsStorelessBuyer(buyerId);

        assertThatThrownBy(() -> logisticsService.updateLogisticsStatus(logisticsId, Logistics.LogisticsStatus.DELIVERED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("🔴 DEF-408 讀取放寬不得外溢到寫入：訂單的買家本人取消物流單 → E_1007")
    void cancelLogistics_orderOwnerBuyer_throwsE1007() {
        when(logisticsRepository.findById(logisticsId)).thenReturn(Optional.of(logisticsOfOrder()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderBoughtBy(buyerId)));
        callAsStorelessBuyer(buyerId);

        assertThatThrownBy(() -> logisticsService.cancelLogistics(logisticsId, "買家不想要了"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }
}
