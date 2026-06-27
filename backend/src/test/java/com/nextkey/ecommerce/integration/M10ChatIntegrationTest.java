package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

/**
 * M10 IM — Chat REST API 整合測試 (Sprint 23 US-001/US-002/US-003)
 *
 * 測試範圍：
 * - IT-CHAT-001: POST /v2/chat/conversations → 200 + ConversationResponse
 * - IT-CHAT-002: POST /v2/chat/conversations（重複）→ 200 + 相同對話（冪等）
 * - IT-CHAT-003: GET /v2/chat/conversations → 200 + ConversationListResponse
 * - IT-CHAT-004: POST /v2/chat/messages → 200 + MessageResponse
 * - IT-CHAT-005: GET /v2/chat/conversations/{id}/messages → 200 + MessageListResponse
 * - IT-CHAT-006: PUT /v2/chat/conversations/{id}/read → 200
 * - IT-CHAT-007: DELETE /v2/chat/conversations/{id} → 200
 * - IT-CHAT-008: 未驗證請求 → 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M10: M10 IM Chat REST API 整合測試")
class M10ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String CONVERSATIONS_URL = "/v2/chat/conversations";
    private static final String MESSAGES_URL = "/v2/chat/messages";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private String initiatorToken;
    private UUID initiatorId;
    private UUID recipientId;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        if (testTenantId == null) {
            Tenant tenant = Tenant.builder()
                    .name("Chat IT Tenant " + System.currentTimeMillis())
                    .slug("chat-it-" + System.currentTimeMillis())
                    .status(Tenant.TenantStatus.ACTIVE)
                    .build();
            testTenantId = tenantRepository.save(tenant).getId();
        }

        if (initiatorToken == null) {
            long ts = System.currentTimeMillis();
            String[] initiatorResult = registerAndLogin("chat-init-" + ts + "@example.com", "Chat Initiator");
            initiatorToken = initiatorResult[0];
            initiatorId = UUID.fromString(initiatorResult[1]);

            String[] recipientResult = registerAndLogin("chat-recv-" + ts + "@example.com", "Chat Recipient");
            recipientId = UUID.fromString(recipientResult[1]);
        }
    }

    private String[] registerAndLogin(String email, String fullName) throws Exception {
        String registerJson = """
            {"email": "%s", "password": "%s", "fullName": "%s", "tenantId": "%s"}
            """.formatted(email, TEST_PASSWORD, fullName, testTenantId);

        MvcResult registerResult = mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data").path("userId").asText();

        String loginJson = """
            {"email": "%s", "password": "%s"}
            """.formatted(email, TEST_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        return new String[]{token, userId};
    }

    // ── IT-CHAT-001: 建立對話 ──────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("IT-CHAT-001: POST /v2/chat/conversations → 200 + ConversationResponse")
    void createConversation_authenticated_returns200() throws Exception {
        String body = """
            {"recipientId": "%s", "conversationType": "DIRECT", "initialMessage": "你好！"}
            """.formatted(recipientId);

        mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.data.conversationType").value("DIRECT"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    // ── IT-CHAT-002: 重複建立對話（冪等）────────────────────────────

    @Test
    @Order(2)
    @DisplayName("IT-CHAT-002: POST /v2/chat/conversations（重複）→ 200 + 相同對話")
    void createConversation_duplicate_returnsSameConversation() throws Exception {
        String body = """
            {"recipientId": "%s", "conversationType": "DIRECT"}
            """.formatted(recipientId);

        // 第一次建立
        MvcResult first = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();

        // 第二次應回傳相同 conversationId
        mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(firstId));
    }

    // ── IT-CHAT-003: 取得對話列表 ──────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("IT-CHAT-003: GET /v2/chat/conversations → 200 + ConversationListResponse")
    void getUserConversations_authenticated_returns200() throws Exception {
        // 先建立一個對話
        String createBody = """
            {"recipientId": "%s", "conversationType": "DIRECT"}
            """.formatted(recipientId);

        mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk());

        // 取得列表
        mockMvc.perform(get(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversations").isArray())
                .andExpect(jsonPath("$.data.conversations.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ── IT-CHAT-004: 發送訊息 ──────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("IT-CHAT-004: POST /v2/chat/messages → 200 + MessageResponse")
    void sendMessage_validConversation_returns200() throws Exception {
        // 先建立對話
        String createBody = """
            {"recipientId": "%s", "conversationType": "DIRECT"}
            """.formatted(recipientId);

        MvcResult createResult = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();

        // 發送訊息
        String msgBody = """
            {"conversationId": "%s", "content": "這是測試訊息", "messageType": "TEXT"}
            """.formatted(conversationId);

        mockMvc.perform(post(MESSAGES_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(msgBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value("這是測試訊息"))
                .andExpect(jsonPath("$.data.messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.isRead").value(false));
    }

    // ── IT-CHAT-005: 取得訊息歷史 ─────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("IT-CHAT-005: GET /v2/chat/conversations/{id}/messages → 200 + MessageListResponse")
    void getMessages_existingConversation_returns200() throws Exception {
        // 建立對話 + 發送訊息
        String createBody = """
            {"recipientId": "%s", "conversationType": "DIRECT", "initialMessage": "初始訊息"}
            """.formatted(recipientId);

        MvcResult createResult = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();

        // 取得訊息歷史
        mockMvc.perform(get(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].content").value("初始訊息"));
    }

    // ── IT-CHAT-006: 標記已讀 ─────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("IT-CHAT-006: PUT /v2/chat/conversations/{id}/read → 200")
    void markAsRead_existingConversation_returns200() throws Exception {
        String createBody = """
            {"recipientId": "%s", "conversationType": "DIRECT"}
            """.formatted(recipientId);

        MvcResult createResult = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();

        mockMvc.perform(put(CONVERSATIONS_URL + "/" + conversationId + "/read")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── IT-CHAT-007: 刪除對話 ─────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("IT-CHAT-007: DELETE /v2/chat/conversations/{id} → 200")
    void deleteConversation_existingConversation_returns200() throws Exception {
        String createBody = """
            {"recipientId": "%s", "conversationType": "DIRECT"}
            """.formatted(recipientId);

        MvcResult createResult = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();

        mockMvc.perform(delete(CONVERSATIONS_URL + "/" + conversationId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── IT-CHAT-008: 未驗證 → 401 ─────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("IT-CHAT-008: 未帶 JWT → 401")
    void allEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(CONVERSATIONS_URL))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(CONVERSATIONS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
