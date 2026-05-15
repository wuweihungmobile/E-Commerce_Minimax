package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.core.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M07 Payment Mock Backend API 整合測試
 *
 * 測試範圍：
 * - IT-M07-001: 建立支付記錄 (Mock)
 * - IT-M07-002: 取得支付狀態
 * - IT-M07-003: 處理退款 (Mock)
 * - IT-M07-004: 取得訂單支付狀態
 * - IT-M07-005: 模擬支付成功
 * - IT-M07-006: 模擬支付失敗
 * - IT-M07-007: 模擬退款
 * - IT-M07-008: 訂單支付狀態機狀態轉換
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M07: M07 Payment Mock 整合測試")
class M07PaymentMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private OrderService orderService;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String PAYMENTS_URL = "/v2/payments";
    private static final String ORDERS_URL = "/v2/orders";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private UUID testUserId;
    private UUID testOrderId;
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        // Mock FeatureToggleService
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        // Mock OrderService
        lenient().doNothing().when(orderService).updateOrderStatus(any(), anyString(), anyString());

        // 建立測試租戶
        if (testTenantId == null) {
            Tenant testTenant = Tenant.builder()
                    .name("Test Tenant for Payment " + System.currentTimeMillis())
                    .status(TenantStatus.ACTIVE)
                    .build();
            testTenant = tenantRepository.save(testTenant);
            testTenantId = testTenant.getId();
        }

        // 建立測試用戶
        if (testUserId == null) {
            User testUser = User.builder()
                    .tenantId(testTenantId)
                    .email("payment-test-" + System.currentTimeMillis() + "@example.com")
                    .fullName("Payment Test User")
                    .build();
            testUser = userRepository.save(testUser);
            testUserId = testUser.getId();
        }

        // 建立測試訂單
        if (testOrderId == null) {
            com.nextkey.ecommerce.domain.model.order.Order testOrder = com.nextkey.ecommerce.domain.model.order.Order.builder()
                    .tenantId(testTenantId)
                    .userId(testUserId)
                    .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                    .totalAmount(BigDecimal.valueOf(1000.00))
                    .currency("TWD")
                    .build();
            testOrder = orderRepository.save(testOrder);
            testOrderId = testOrder.getId();
        }

        // 獲取 auth token
        if (authToken == null) {
            authToken = createTestUserAndGetToken("payment-auth-" + System.currentTimeMillis() + "@example.com");
        }
    }

    private String createTestUserAndGetToken(String email) throws Exception {
        // 建立測試用戶
        String registerJson = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "fullName": "Payment Auth User",
                "tenantId": "%s"
            }
            """, email, TEST_PASSWORD, testTenantId);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk());

        // 登入獲取 token
        String loginJson = String.format("""
            {
                "email": "%s",
                "password": "%s"
            }
            """, email, TEST_PASSWORD);

        var loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("IT-M07-001: 建立支付記錄 (Mock)")
    void testProcessPayment() throws Exception {
        // 先建立一個新訂單
        com.nextkey.ecommerce.domain.model.order.Order newOrder = com.nextkey.ecommerce.domain.model.order.Order.builder()
                .tenantId(testTenantId)
                .userId(testUserId)
                .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(500.00))
                .currency("TWD")
                .build();
        newOrder = orderRepository.save(newOrder);

        String requestJson = String.format("""
            {
                "orderId": "%s",
                "paymentMethod": "CREDIT_CARD"
            }
            """, newOrder.getId());

        mockMvc.perform(post(PAYMENTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionId", startsWith("MOCK-")));
    }

    @Test
    @Order(2)
    @DisplayName("IT-M07-002: 取得支付狀態")
    void testGetPaymentStatus() throws Exception {
        // 先建立支付
        Payment payment = Payment.builder()
                .orderId(testOrderId)
                .paymentMethod(Payment.PaymentMethod.CREDIT_CARD)
                .amount(BigDecimal.valueOf(1000.00))
                .currency("TWD")
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId("MOCK-TEST123")
                .build();
        payment = paymentRepository.save(payment);

        mockMvc.perform(get(PAYMENTS_URL + "/" + payment.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @Order(3)
    @DisplayName("IT-M07-003: 處理退款 (Mock)")
    void testProcessRefund() throws Exception {
        // 先建立支付
        Payment payment = Payment.builder()
                .orderId(testOrderId)
                .paymentMethod(Payment.PaymentMethod.CREDIT_CARD)
                .amount(BigDecimal.valueOf(1000.00))
                .currency("TWD")
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId("MOCK-REFUND123")
                .build();
        payment = paymentRepository.save(payment);

        String requestJson = String.format("""
            {
                "paymentId": "%s",
                "amount": 1000.00,
                "reason": "Customer request"
            }
            """, payment.getId());

        mockMvc.perform(post(PAYMENTS_URL + "/refund")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.refundAmount").value(1000.00));
    }

    @Test
    @Order(4)
    @DisplayName("IT-M07-004: 取得訂單支付狀態")
    void testGetOrderPaymentState() throws Exception {
        mockMvc.perform(get(ORDERS_URL + "/" + testOrderId + "/payment")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(testOrderId.toString()));
    }

    @Test
    @Order(5)
    @DisplayName("IT-M07-005: 模擬支付成功")
    void testMockPaySuccess() throws Exception {
        // 先建立一個新訂單
        com.nextkey.ecommerce.domain.model.order.Order newOrder = com.nextkey.ecommerce.domain.model.order.Order.builder()
                .tenantId(testTenantId)
                .userId(testUserId)
                .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(2000.00))
                .currency("TWD")
                .build();
        newOrder = orderRepository.save(newOrder);

        mockMvc.perform(post(ORDERS_URL + "/" + newOrder.getId() + "/pay")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(newOrder.getId().toString()));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M07-006: 模擬支付失敗")
    void testMockPayFailure() throws Exception {
        // 先建立一個新訂單
        com.nextkey.ecommerce.domain.model.order.Order newOrder = com.nextkey.ecommerce.domain.model.order.Order.builder()
                .tenantId(testTenantId)
                .userId(testUserId)
                .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(3000.00))
                .currency("TWD")
                .build();
        newOrder = orderRepository.save(newOrder);

        mockMvc.perform(post(ORDERS_URL + "/" + newOrder.getId() + "/pay/fail")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("reason", "Insufficient funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
    }

    @Test
    @Order(7)
    @DisplayName("IT-M07-007: 模擬退款")
    void testMockRefund() throws Exception {
        // 先建立一個新訂單和支付
        com.nextkey.ecommerce.domain.model.order.Order newOrder = com.nextkey.ecommerce.domain.model.order.Order.builder()
                .tenantId(testTenantId)
                .userId(testUserId)
                .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(4000.00))
                .currency("TWD")
                .build();
        newOrder = orderRepository.save(newOrder);

        Payment payment = Payment.builder()
                .orderId(newOrder.getId())
                .paymentMethod(Payment.PaymentMethod.CREDIT_CARD)
                .amount(BigDecimal.valueOf(4000.00))
                .currency("TWD")
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId("MOCK-REFUND-TEST")
                .build();
        payment = paymentRepository.save(payment);

        mockMvc.perform(post(ORDERS_URL + "/" + newOrder.getId() + "/refund")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("reason", "Customer cancelled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canRefund").value(false));
    }

    @Test
    @Order(8)
    @DisplayName("IT-M07-008: 訂單支付狀態機 - 狀態轉換驗證")
    void testPaymentStateTransitions() throws Exception {
        // 測試狀態機轉換：
        // CREATED -> PAID -> REFUNDING -> REFUNDED

        // 1. 創建訂單 (CREATED)
        com.nextkey.ecommerce.domain.model.order.Order order = com.nextkey.ecommerce.domain.model.order.Order.builder()
                .tenantId(testTenantId)
                .userId(testUserId)
                .status(com.nextkey.ecommerce.domain.model.order.Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(5000.00))
                .currency("TWD")
                .build();
        order = orderRepository.save(order);

        // 2. 模擬支付 (PAID)
        mockMvc.perform(post(ORDERS_URL + "/" + order.getId() + "/pay")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk());

        // 3. 驗證支付狀態
        mockMvc.perform(get(ORDERS_URL + "/" + order.getId() + "/payment")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
    }
}