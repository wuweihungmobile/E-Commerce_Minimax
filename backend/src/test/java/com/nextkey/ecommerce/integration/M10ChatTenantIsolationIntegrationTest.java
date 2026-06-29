package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * M10 IM — Chat 跨租戶隔離 + tenant_id 落地整合測試（Sprint 25 US-003 / AI-802 / AC-003-3）。
 *
 * <p>依使用者於 Sprint 25 Planning 確認的方案：對話的存取邊界以 <b>participant-scoping</b>
 * （{@code initiatorId} 或 {@code recipientId}）為準，而非以 {@code TenantContext} 過濾查詢
 * —— 因為聊天本質跨租戶（買方租戶 ↔ 賣方租戶），對話 {@code tenant_id} 歸屬賣方（依關聯實體推導）。
 *
 * <p>本測試證明：
 * <ol>
 *   <li>IT-CHAT-TEN-001: 他租戶且非參與者無法讀取對話訊息（participant-scoping 跨租戶有效）</li>
 *   <li>IT-CHAT-TEN-002: 他租戶非參與者的對話列表不含他人對話</li>
 *   <li>IT-CHAT-TEN-003: 建立的 DIRECT 對話 {@code tenant_id} 正確落地（NOT NULL，退回 System Tenant）</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M10-Tenant: M10 Chat 跨租戶隔離 + tenant_id 落地（US-003 / AC-003-3）")
class M10ChatTenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String CONVERSATIONS_URL = "/v2/chat/conversations";
    private static final String TEST_PASSWORD = "SecurePass123!";
    /** DIRECT 對話（無 listing/order）的 tenant_id 退路，須與 ChatService 推導規則一致。 */
    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    private UUID tenantAId;
    private String initiatorAToken;
    private UUID recipientAId;
    private String recipientAToken;
    private String outsiderBToken;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long ts = System.nanoTime();
        tenantAId = createTenant("chat-iso-a-" + ts);
        UUID tenantBId = createTenant("chat-iso-b-" + ts);

        String[] initiatorA = registerAndLogin("chat-iso-initA-" + ts + "@example.com", "Initiator A", tenantAId);
        initiatorAToken = initiatorA[0];

        String[] recipientA = registerAndLogin("chat-iso-recvA-" + ts + "@example.com", "Recipient A", tenantAId);
        recipientAToken = recipientA[0];
        recipientAId = UUID.fromString(recipientA[1]);

        String[] outsiderB = registerAndLogin("chat-iso-outB-" + ts + "@example.com", "Outsider B", tenantBId);
        outsiderBToken = outsiderB[0];
    }

    // ── IT-CHAT-TEN-001: 他租戶非參與者無法讀取對話訊息 ──────────────────

    @Test
    @DisplayName("IT-CHAT-TEN-001: 參與者可讀訊息（200）；他租戶非參與者不可讀（400, E-9005）")
    void crossTenantNonParticipant_cannotReadMessages() throws Exception {
        String conversationId = createConversation(initiatorAToken, recipientAId, "你好，這是租戶 A 的私密對話");

        // 參與者（recipientA）可讀 → 200（正向控制，證明對話存在且可讀）
        mockMvc.perform(get(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + recipientAToken)
                        .header("X-Tenant-ID", tenantAId.toString())
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1));

        // 他租戶非參與者（outsiderB）不可讀 → 400（E-9005「Conversation not found」，participant-scoping 攔下）
        mockMvc.perform(get(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + outsiderBToken)
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9005"));
    }

    // ── IT-CHAT-TEN-002: 他租戶非參與者的對話列表不含他人對話 ─────────────

    @Test
    @DisplayName("IT-CHAT-TEN-002: 他租戶非參與者的對話列表為空，不含他人對話")
    void crossTenantNonParticipant_conversationListExcludesOthers() throws Exception {
        createConversation(initiatorAToken, recipientAId, "租戶 A 對話內容");

        mockMvc.perform(get(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + outsiderBToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── IT-CHAT-TEN-003: DIRECT 對話 tenant_id 正確落地 ──────────────────

    @Test
    @DisplayName("IT-CHAT-TEN-003: 建立的 DIRECT 對話 tenant_id 落地為 System Tenant（NOT NULL）")
    void createdDirectConversation_persistsSystemTenantId() throws Exception {
        String conversationId = createConversation(initiatorAToken, recipientAId, "DIRECT 對話");

        Conversation persisted = conversationRepository.findById(UUID.fromString(conversationId))
                .orElseThrow(() -> new AssertionError("對話應已持久化: " + conversationId));

        assertThat(persisted.getTenantId())
                .as("DIRECT 對話（無 listing/order）tenant_id 應退回 System Tenant 且不為 null")
                .isEqualTo(SYSTEM_TENANT);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private UUID createTenant(final String slug) {
        Tenant tenant = Tenant.builder()
                .name("Chat Isolation " + slug)
                .slug(slug)
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private String[] registerAndLogin(final String email, final String fullName, final UUID tenantId) throws Exception {
        String registerJson = """
            {"email": "%s", "password": "%s", "fullName": "%s", "tenantId": "%s"}
            """.formatted(email, TEST_PASSWORD, fullName, tenantId);

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

    private String createConversation(final String token, final UUID recipientId, final String initialMessage)
            throws Exception {
        String body = """
            {"recipientId": "%s", "conversationType": "DIRECT", "initialMessage": "%s"}
            """.formatted(recipientId, initialMessage);

        MvcResult result = mockMvc.perform(post(CONVERSATIONS_URL)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", tenantAId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("conversationId").asText();
    }
}
