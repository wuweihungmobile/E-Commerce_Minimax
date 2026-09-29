package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 同一張訂單有多筆付款紀錄時（Sprint 220，DEF-309）：先「模擬付款失敗」再「付款成功」是 Mock 模式前端的正常操作
 * （訂單詳情頁兩顆按鈕），會在 {@code payments} 留下同一 {@code order_id} 的兩列。
 * {@code payments.order_id} 沒有唯一約束，而 {@code PaymentRepository.findByOrderId} 回傳單一 {@code Optional}。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-PAY-MULTI: 同一訂單多筆付款紀錄（Sprint 220）")
class MultiplePaymentRowsIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private com.nextkey.ecommerce.core.settlement.SettlementGenerator settlementGenerator;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private RedisCartService cartService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private FeatureToggleService featureToggleService;

    private Tenant tenant;
    private User buyer;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder().name("Multi Pay Tenant").slug("multi-pay-" + stamp)
                .contactEmail("multi-pay-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User seller = userRepository.save(User.builder().email("multi-pay-seller-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Seller").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        buyer = userRepository.save(User.builder().email("multi-pay-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        listingId = listingRepository.save(Listing.builder().tenant(tenant).owner(seller)
                .listingType(Listing.ListingType.PRODUCT).title("Multi Pay Product")
                .basePrice(new BigDecimal("100.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("付款失敗一次再付款成功 → 付款狀態端點不可出錯，且回報的是成功的那一筆")
    void failedThenSuccessfulPayment_stateEndpointStillWorks() {
        UUID orderId = buyerPlacesOrder();
        paymentStateService.mockPaymentFailure(orderId, "first attempt failed");
        paymentStateService.mockPaymentSuccess(orderId);
        assertThat(paymentRows(orderId)).as("同一張訂單兩列付款紀錄").hasSize(2);

        OrderPaymentStateDto state = paymentStateService.getOrderPaymentState(orderId);

        assertThat(state.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(state.getOrderStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("週結算：已完成的訂單有失敗＋成功（含部分退款）兩筆付款 → 結算單照常產生，退款取成功那一筆")
    void settlement_orderWithFailedAndRefundedSuccessPayments_generatesStatement() {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount, shipping_fee, "
                + "discount_amount, currency, created_at, updated_at) VALUES (?, ?, ?, 'PRODUCT', 'COMPLETED', 1000.00, "
                + "0.00, 0.00, 'TWD', NOW() - INTERVAL '10 days', NOW())", orderId, tenant.getId(), buyer.getId());
        insertPayment(orderId, "FAILED", "0.00", "20 minutes");
        insertPayment(orderId, "SUCCESS", "100.00", "10 minutes");

        java.time.LocalDate today = com.nextkey.ecommerce.shared.time.BusinessTime.today();
        var statement = settlementGenerator.generateStatementForTenant(tenant.getId(), today.minusDays(6), today);

        assertThat(statement.getTotalOrders()).isEqualTo(1);
        assertThat(statement.getTotalRefunds()).as("扣除的是成功那一筆的部分退款 100").isEqualByComparingTo("100.00");
    }

    private void insertPayment(final UUID orderId, final String status, final String refunded, final String age) {
        jdbcTemplate.update("INSERT INTO payments (id, order_id, payment_method, amount, currency, status, transaction_id, "
                + "refunded_amount, created_at, updated_at) VALUES (?, ?, 'MOCK', 1000.00, 'TWD', ?, ?, ?, "
                + "NOW() - CAST(? AS INTERVAL), NOW())", UUID.randomUUID(), orderId, status, "MOCK-" + UUID.randomUUID(),
                new BigDecimal(refunded), age);
    }

    private UUID buyerPlacesOrder() {
        asBuyer();
        cartService.clearCart(buyer.getId(), tenant.getId());
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", skuId, listingId, "SKU-MULTI-" + System.nanoTime());
        jdbcTemplate.update("INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, "
                + "updated_at) VALUES (?, 100, 0, 10, 0, NOW())", skuId);
        cartService.addItem(buyer.getId(), tenant.getId(), CartDto.AddItemRequest.builder()
                .listingId(listingId).skuId(skuId).quantity(1).build());
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區信義路五段 7 號");
        request.setShippingRecipientName("測試買家");
        request.setShippingPhone("0912345678");
        return orderService.createOrderFromCart(request).getId();
    }

    private List<String> paymentRows(final UUID orderId) {
        return jdbcTemplate.queryForList("SELECT status FROM payments WHERE order_id = ? ORDER BY created_at",
                String.class, orderId);
    }

    private void asBuyer() {
        TenantContext.setCurrentUser(buyer.getId());
        TenantContext.setCurrentTenant(tenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(buyer.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(buyer.getId().toString(), null, authorities));
    }
}
