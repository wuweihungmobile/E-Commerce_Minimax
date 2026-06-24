package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.NotificationTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M09 通知模板 Backend API 整合測試
 *
 * 測試範圍：
 * - IT-M09-001: 取得模板列表（分頁）
 * - IT-M09-002: 取得模板詳情
 * - IT-M09-003: 建立通知模板
 * - IT-M09-004: 更新通知模板
 * - IT-M09-005: 刪除通知模板（軟刪除）
 * - IT-M09-006: 渲染模板預覽
 * - IT-M09-007: 依類型篩選模板
 * - IT-M09-008: 依頻道篩選模板
 * - IT-M09-009: 依啟用狀態篩選
 * - IT-M09-010: 變量提取驗證
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M09: M09 通知模板整合測試")
class M09NotificationTemplateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String TEMPLATES_URL = "/v2/notification-templates";
    private static final String DASHBOARD_TEMPLATES_URL = "/v2/dashboard/notification-templates";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private UUID testUserId;
    private String authToken;
    private NotificationTemplate testTemplate;

    @BeforeEach
    void setUp() throws Exception {
        // Mock FeatureToggleService
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        // 建立測試租戶
        if (testTenantId == null) {
            Tenant testTenant = Tenant.builder()
                    .name("Test Tenant for Notification " + System.currentTimeMillis())
                    .slug("test-tenant-notification-" + System.currentTimeMillis())
                    .status(TenantStatus.ACTIVE)
                    .build();
            testTenant = tenantRepository.save(testTenant);
            testTenantId = testTenant.getId();
        }

        // 建立測試用戶
        if (testUserId == null) {
            User testUser = User.builder()
                    .tenantId(testTenantId)
                    .email("notification-test-" + System.currentTimeMillis() + "@example.com")
                    .fullName("Notification Test User")
                    .build();
            testUser = userRepository.save(testUser);
            testUserId = testUser.getId();
        }

        // 建立測試模板
        if (testTemplate == null) {
            testTemplate = NotificationTemplate.builder()
                    .tenantId(testTenantId)
                    .templateCode("TEST_TEMPLATE_" + System.currentTimeMillis())
                    .notificationType(NotificationTemplate.NotificationType.ORDER_CONFIRMED)
                    .channel(NotificationTemplate.NotificationChannel.IN_APP)
                    .name("Test Template")
                    .subject("Test Subject")
                    .contentTemplate("Hello {{user_name}}, your order {{order_id}} is confirmed.")
                    .variables(List.of("user_name", "order_id"))
                    .isActive(true)
                    .priority(0)
                    .createdBy(testUserId)
                    .updatedBy(testUserId)
                    .build();
            testTemplate = notificationTemplateRepository.save(testTemplate);
        }

        // 獲取 auth token
        if (authToken == null) {
            authToken = createTestUserAndGetToken("notification-auth-" + System.currentTimeMillis() + "@example.com");
        }
    }

    private String createTestUserAndGetToken(String email) throws Exception {
        // 建立測試用戶
        String registerJson = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "fullName": "Notification Auth User",
                "tenantId": "%s"
            }
            """, email, TEST_PASSWORD, testTenantId);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

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
    @DisplayName("IT-M09-001: 取得模板列表（分頁）")
    void testGetTemplates() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates", notNullValue()))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @Order(2)
    @DisplayName("IT-M09-002: 取得模板詳情")
    void testGetTemplateDetail() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL + "/" + testTemplate.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testTemplate.getId().toString()))
                .andExpect(jsonPath("$.data.templateCode").value(testTemplate.getTemplateCode()));
    }

    @Test
    @Order(3)
    @DisplayName("IT-M09-003: 建立通知模板")
    void testCreateTemplate() throws Exception {
        String templateCode = "NEW_TEMPLATE_" + System.currentTimeMillis();
        String requestJson = String.format("""
            {
                "templateCode": "%s",
                "notificationType": "ORDER_PAID",
                "channel": "EMAIL",
                "name": "New Test Template",
                "subject": "Payment Confirmation",
                "contentTemplate": "Dear {{user_name}}, your payment for order {{order_id}} is received. Amount: {{total_amount}} {{currency}}.",
                "variables": ["user_name", "order_id", "total_amount", "currency"],
                "isActive": true,
                "priority": 1
            }
            """, templateCode);

        mockMvc.perform(post(DASHBOARD_TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateCode").value(templateCode))
                .andExpect(jsonPath("$.data.name").value("New Test Template"))
                .andExpect(jsonPath("$.data.notificationType").value("ORDER_PAID"));
    }

    @Test
    @Order(4)
    @DisplayName("IT-M09-004: 更新通知模板")
    void testUpdateTemplate() throws Exception {
        String newName = "Updated Template " + System.currentTimeMillis();
        String requestJson = String.format("""
            {
                "name": "%s",
                "contentTemplate": "Updated content with {{new_variable}}",
                "isActive": false
            }
            """, newName);

        mockMvc.perform(put(DASHBOARD_TEMPLATES_URL + "/" + testTemplate.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    @Order(5)
    @DisplayName("IT-M09-005: 刪除通知模板（軟刪除）")
    void testDeleteTemplate() throws Exception {
        // 先建立一個新模板
        NotificationTemplate templateToDelete = NotificationTemplate.builder()
                .tenantId(testTenantId)
                .templateCode("TO_DELETE_" + System.currentTimeMillis())
                .notificationType(NotificationTemplate.NotificationType.ORDER_SHIPPED)
                .channel(NotificationTemplate.NotificationChannel.IN_APP)
                .name("Template To Delete")
                .contentTemplate("Delete me")
                .isActive(true)
                .createdBy(testUserId)
                .build();
        templateToDelete = notificationTemplateRepository.save(templateToDelete);

        mockMvc.perform(delete(DASHBOARD_TEMPLATES_URL + "/" + templateToDelete.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk());

        // 驗證列表中不再出現
        mockMvc.perform(get(TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates[*].id", not(hasItem(templateToDelete.getId().toString()))));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M09-006: 渲染模板預覽")
    void testRenderTemplate() throws Exception {
        String requestJson = String.format("""
            {
                "templateCode": "%s",
                "variables": {
                    "user_name": "張小明",
                    "order_id": "ORD-2024-001"
                }
            }
            """, testTemplate.getTemplateCode());

        mockMvc.perform(post(TEMPLATES_URL + "/render")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", containsString("張小明")))
                .andExpect(jsonPath("$.data.content", containsString("ORD-2024-001")));
    }

    @Test
    @Order(7)
    @DisplayName("IT-M09-007: 依類型篩選模板")
    void testFilterByNotificationType() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("notificationType", "ORDER_CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates[*].notificationType", everyItem(equalTo("ORDER_CONFIRMED"))));
    }

    @Test
    @Order(8)
    @DisplayName("IT-M09-008: 依頻道篩選模板")
    void testFilterByChannel() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("channel", "IN_APP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates[*].channel", everyItem(equalTo("IN_APP"))));
    }

    @Test
    @Order(9)
    @DisplayName("IT-M09-009: 依啟用狀態篩選")
    void testFilterByActiveStatus() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates[*].isActive", everyItem(equalTo(true))));
    }

    @Test
    @Order(10)
    @DisplayName("IT-M09-010: 模板變量自動提取")
    void testVariableExtraction() throws Exception {
        // 測試當建立含有 {{variable}} 語法的模板時，變量會被自動提取
        String requestJson = String.format("""
            {
                "templateCode": "VARIABLE_TEST_%s",
                "notificationType": "ORDER_DELIVERED",
                "channel": "PUSH",
                "name": "Variable Extraction Test",
                "contentTemplate": "Hello {{customer_name}}, your package for order {{order_number}} will be delivered on {{delivery_date}}.",
                "isActive": true
            }
            """, System.currentTimeMillis());

        mockMvc.perform(post(DASHBOARD_TEMPLATES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variables", hasItems("customer_name", "order_number", "delivery_date")));
    }

    @Test
    @Order(11)
    @DisplayName("IT-M09-011: 渲染失敗 - 模板不存在")
    void testRenderTemplateNotFound() throws Exception {
        String requestJson = """
            {
                "templateCode": "NON_EXISTENT_TEMPLATE",
                "variables": {
                    "user_name": "Test"
                }
            }
            """;

        mockMvc.perform(post(TEMPLATES_URL + "/render")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @Order(12)
    @DisplayName("IT-M09-012: 渲染失敗 - 模板未啟用")
    void testRenderInactiveTemplate() throws Exception {
        // 先建立一個停用的模板
        NotificationTemplate inactiveTemplate = NotificationTemplate.builder()
                .tenantId(testTenantId)
                .templateCode("INACTIVE_" + System.currentTimeMillis())
                .notificationType(NotificationTemplate.NotificationType.PAYMENT_FAILED)
                .channel(NotificationTemplate.NotificationChannel.EMAIL)
                .name("Inactive Template")
                .contentTemplate("This template is inactive")
                .isActive(false) // 停用
                .createdBy(testUserId)
                .build();
        inactiveTemplate = notificationTemplateRepository.save(inactiveTemplate);

        String requestJson = String.format("""
            {
                "templateCode": "%s",
                "variables": {}
            }
            """, inactiveTemplate.getTemplateCode());

        mockMvc.perform(post(TEMPLATES_URL + "/render")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());
    }
}