package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 訂單重複發起 Stripe 結帳（Sprint 221，DEF-310）：買家在 Stripe 付款頁按返回、回到訂單再按一次「前往付款」，
 * 是正常操作，不是罕見競態。
 *
 * <p>第二次發起時 Stripe 以相同冪等鍵拿回同一個 session，本地要寫的付款紀錄卻撞上 {@code payments.idempotency_key}
 * 唯一索引（V79）。原本的寫法是 {@code try { saveAndFlush } catch (DataIntegrityViolationException) { log.warn }}，
 * 但例外發生在 repository 的交易代理內，會把外層交易標成 rollback-only——捕捉沒有用，請求在提交時以
 * {@code UnexpectedRollbackException}（500）收場。原本的單元測試以 mock 模擬「撞到唯一索引」，看不到交易語意。
 *
 * <p>需要真實資料庫與唯一索引。integration-test profile 以 ddl-auto=update 建表、沒有 Flyway 的索引，所以這裡補上
 * 生產有的那條（只涵蓋本類別的鍵）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-STRIPE-RETRY: 訂單重複發起 Stripe 結帳（Sprint 221，DEF-310）")
class OrderStripeCheckoutRetryIntegrationTest {

    @Autowired private PaymentStateService paymentStateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS it_order_checkout_key_unique ON payments "
                + "(idempotency_key) WHERE idempotency_key LIKE 'ORDER-CHECKOUT-%'");
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.save(Tenant.builder().name("Stripe Retry Tenant").slug("stripe-retry-" + stamp)
                .contactEmail("stripe-retry-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User buyer = userRepository.save(User.builder().email("stripe-retry-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount, shipping_fee, "
                + "discount_amount, currency, created_at, updated_at) VALUES (?, ?, ?, 'PRODUCT', 'CREATED', 500.00, "
                + "0.00, 0.00, 'TWD', NOW(), NOW())", orderId, tenant.getId(), buyer.getId());
        TenantContext.setCurrentUser(buyer.getId());
        String sessionId = "cs_retry_" + UUID.randomUUID();
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId(sessionId)
                        .sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId).status("open").paymentStatus("unpaid").build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("按返回後再按一次「前往付款」→ 第二次發起也成功、拿到同一個 session、只有一列付款紀錄")
    void secondCheckoutStart_returnsTheSameSession_andKeepsOnePaymentRow() {
        CheckoutSessionResponse first = paymentStateService.initiateStripeCheckout(orderId);
        CheckoutSessionResponse second = paymentStateService.initiateStripeCheckout(orderId);

        assertThat(second.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(second.getSessionUrl()).isEqualTo(first.getSessionUrl());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId))
                .as("重複寫入會撞唯一索引").isEqualTo(1);
    }
}
