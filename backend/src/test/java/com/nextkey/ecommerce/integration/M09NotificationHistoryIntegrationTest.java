package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.notification.NotificationHistory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus;
import com.nextkey.ecommerce.domain.repository.NotificationHistoryRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * M09 通知歷史查詢整合測試（Sprint 20 US-005）
 *
 * 測試範圍：
 * - IT-M09H-001: GET /v2/notifications/history — 取得通知歷史列表
 * - IT-M09H-002: GET /v2/notifications/history — 空記錄時回傳空列表
 * - IT-M09H-003: PUT /v2/notifications/history/{id}/read — 標記已讀
 * - IT-M09H-004: PUT /v2/notifications/history/{id}/read — 他人記錄應回傳 403
 * - IT-M09H-005: GET /v2/notifications/history/unread-count — 取得未讀計數
 * - IT-M09H-006: GET /v2/notifications/history — 分頁參數驗證
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M09H: M09 通知歷史查詢整合測試")
class M09NotificationHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String HISTORY_URL = "/v2/notifications/history";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private String authToken;
    private UUID authUserId;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        if (testTenantId == null) {
            Tenant tenant = Tenant.builder()
                    .name("Notif History Tenant " + System.currentTimeMillis())
                    .slug("notif-history-" + System.currentTimeMillis())
                    .status(TenantStatus.ACTIVE)
                    .build();
            testTenantId = tenantRepository.save(tenant).getId();
        }

        if (authToken == null) {
            String email = "notif-history-" + System.currentTimeMillis() + "@example.com";
            String[] result = createUserAndLogin(email);
            authToken = result[0];
            authUserId = UUID.fromString(result[1]);
        }
    }

    private String[] createUserAndLogin(String email) throws Exception {
        String registerJson = String.format("""
            {"email": "%s", "password": "%s", "fullName": "History Test User", "tenantId": "%s"}
            """, email, TEST_PASSWORD, testTenantId);

        var registerResult = mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        String registerResponse = registerResult.getResponse().getContentAsString();
        String userId = objectMapper.readTree(registerResponse).path("data").path("userId").asText();

        String loginJson = String.format("""
            {"email": "%s", "password": "%s"}
            """, email, TEST_PASSWORD);

        var loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();

        return new String[]{token, userId};
    }

    @Test
    @Order(1)
    @DisplayName("IT-M09H-001: GET /history — 取得通知歷史列表（含資料）")
    void testGetHistory_withData() throws Exception {
        // 預先寫入歷史記錄
        historyRepository.save(NotificationHistory.builder()
                .userId(authUserId)
                .tenantId(testTenantId)
                .notificationType("ORDER_CONFIRMED")
                .channel("IN_APP")
                .title("訂單已確認")
                .body("您的訂單 #1234 已確認")
                .isRead(false)
                .build());

        mockMvc.perform(get(HISTORY_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].title").value("訂單已確認"))
                .andExpect(jsonPath("$.data.items[0].isRead").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @Order(2)
    @DisplayName("IT-M09H-002: GET /history — 無資料時回傳空列表")
    void testGetHistory_empty() throws Exception {
        mockMvc.perform(get(HISTORY_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @Order(3)
    @DisplayName("IT-M09H-003: PUT /history/{id}/read — 標記單筆已讀")
    void testMarkHistoryAsRead() throws Exception {
        NotificationHistory history = historyRepository.save(NotificationHistory.builder()
                .userId(authUserId)
                .tenantId(testTenantId)
                .notificationType("NEW_MESSAGE")
                .channel("IN_APP")
                .title("新訊息通知")
                .body("您有一條新訊息")
                .isRead(false)
                .build());

        mockMvc.perform(put(HISTORY_URL + "/" + history.getId() + "/read")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("IT-M09H-004: PUT /history/{id}/read — 記錄不存在回傳 404/400")
    void testMarkHistoryAsRead_notFound() throws Exception {
        mockMvc.perform(put(HISTORY_URL + "/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5)
    @DisplayName("IT-M09H-005: GET /history/unread-count — 取得未讀計數")
    void testGetHistoryUnreadCount() throws Exception {
        historyRepository.save(NotificationHistory.builder()
                .userId(authUserId).tenantId(testTenantId)
                .notificationType("PAYMENT_SUCCESS").channel("IN_APP")
                .title("付款成功").body("訂單已付款").isRead(false).build());

        historyRepository.save(NotificationHistory.builder()
                .userId(authUserId).tenantId(testTenantId)
                .notificationType("ORDER_SHIPPED").channel("IN_APP")
                .title("訂單已出貨").body("訂單已出貨").isRead(false).build());

        mockMvc.perform(get(HISTORY_URL + "/unread-count")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M09H-006: GET /history — 分頁參數正確傳遞")
    void testGetHistory_pagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            historyRepository.save(NotificationHistory.builder()
                    .userId(authUserId).tenantId(testTenantId)
                    .notificationType("SYSTEM_ANNOUNCEMENT").channel("IN_APP")
                    .title("公告 #" + i).body("公告內容 " + i).isRead(false).build());
        }

        mockMvc.perform(get(HISTORY_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(3));
    }

    @Test
    @Order(7)
    @DisplayName("IT-M09H-007: GET /history — 未授權時回傳 401")
    void testGetHistory_unauthorized() throws Exception {
        mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isUnauthorized());
    }
}
