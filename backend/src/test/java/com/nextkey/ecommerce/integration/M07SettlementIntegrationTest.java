package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.core.settlement.SettlementCalculator;
import com.nextkey.ecommerce.core.settlement.SettlementGenerator;
import com.nextkey.ecommerce.core.settlement.SettlementMapper;
import com.nextkey.ecommerce.core.settlement.SettlementReviewer;
import com.nextkey.ecommerce.core.settlement.SettlementService;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.CreditNoteRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.filter.UserPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M07 Settlement 整合測試
 *
 * 測試範圍（Sprint 16 US-004 / Retro AI-004）：
 * - IT-M07-S-001: 商家查詢結算單列表（GET /v2/settlements）
 * - IT-M07-S-002: 商家查詢結算單詳情（GET /v2/settlements/{id}）
 * - IT-M07-S-003: 商家提交審核（PUT /v2/settlements/{id}/submit）
 * - IT-M07-S-004: Admin 取得待審核列表（GET /v2/admin/settlements/pending）
 * - IT-M07-S-005: Admin 批准結算單（PUT /v2/admin/settlements/{id}/approve）
 * - IT-M07-S-006: Admin 駁回結算單（PUT /v2/admin/settlements/{id}/reject）
 * - IT-M07-S-007: 跨租戶隔離測試
 * - IT-M07-S-008: 狀態機無效轉換測試
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M07: M07 Settlement 整合測試 (Sprint 16 US-004)")
class M07SettlementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementGenerator settlementGenerator;

    @MockBean
    private SettlementCalculator settlementCalculator;

    @MockBean
    private SettlementMapper settlementMapper;

    @MockBean
    private SettlementReviewer settlementReviewer;

    @MockBean
    private SettlementStatementRepository settlementStatementRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CreditNoteRepository creditNoteRepository;

    @MockBean
    private JwtTokenService jwtTokenService;

    private UUID testTenantId;
    private UUID testStatementId;
    private SettlementStatement testStatement;

    /**
     * 比照 {@code TransferControllerE2ETest}：Controller 需要 {@code @AuthenticationPrincipal UserPrincipal}，
     * {@code @WithMockUser} 的預設 principal 型別不符會被注入 null，改用手動建構的 Authentication。
     */
    private Authentication authAs(final String role, final String... extraAuthorities) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", role, testTenantId != null ? testTenantId.toString() : null);
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        for (String a : extraAuthorities) {
            authorities.add(new SimpleGrantedAuthority(a));
        }
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testStatementId = UUID.randomUUID();

        // 建立測試 Tenant
        Tenant tenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(tenant));

        // 建立測試結算單
        testStatement = SettlementStatement.builder()
                .id(testStatementId)
                .tenant(tenant)
                .tenantId(testTenantId)
                .statementNumber("STL-12345678-20260601")
                .periodStart(java.time.LocalDate.of(2026, 6, 1))
                .periodEnd(java.time.LocalDate.of(2026, 6, 7))
                .totalOrders(10)
                .totalGmv(new BigDecimal("100000.00"))
                .totalRefunds(new BigDecimal("5000.00"))
                .commissionAmount(new BigDecimal("10000.00"))
                .netSettlementAmount(new BigDecimal("85000.00"))
                .currency("TWD")
                .status(SettlementStatus.PENDING)
                .generatedAt(java.time.Instant.now())
                .build();
        when(settlementStatementRepository.findById(testStatementId))
                .thenReturn(Optional.of(testStatement));
        when(settlementStatementRepository.findByIdAndTenantId(testStatementId, testTenantId))
                .thenReturn(Optional.of(testStatement));
    }

    // ========== 商家查詢結算單 ==========

    @Test
    @Order(1)
    @DisplayName("IT-M07-S-001: StoreOwner 查詢自己租戶的結算單列表")
    @WithMockUser(username = "storeowner", authorities = {"order:read", "tenant:read"})
    void getSettlements_AsStoreOwner_ReturnsTenantStatements() throws Exception {
        // Given
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .statementNumber("STL-12345678-20260601")
                .totalGmv(new BigDecimal("100000.00"))
                .netSettlementAmount(new BigDecimal("85000.00"))
                .status("PENDING")
                .build();
        SettlementService.SettlementStatementListResponse listResponse =
                SettlementService.SettlementStatementListResponse.builder()
                .statements(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .build();
        when(settlementGenerator.getStatementsByTenant(0, 20)).thenReturn(listResponse);

        // When & Then
        mockMvc.perform(get("/v2/settlements")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statements", hasSize(1)))
                .andExpect(jsonPath("$.data.statements[0].statementNumber").value("STL-12345678-20260601"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @Order(2)
    @DisplayName("IT-M07-S-002: StoreOwner 查詢結算單詳情")
    @WithMockUser(username = "storeowner", authorities = {"order:read"})
    void getSettlementById_AsStoreOwner_ReturnsStatement() throws Exception {
        // Given
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .statementNumber("STL-12345678-20260601")
                .totalGmv(new BigDecimal("100000.00"))
                .netSettlementAmount(new BigDecimal("85000.00"))
                .status("PENDING")
                .build();
        when(settlementGenerator.getStatementById(testStatementId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/v2/settlements/{id}", testStatementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statementNumber").value("STL-12345678-20260601"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @Order(3)
    @DisplayName("IT-M07-S-003: 商家提交結算單審核")
    @WithMockUser(username = "storeowner", authorities = {"order:read"})
    void submitForReview_AsStoreOwner_Success() throws Exception {
        // Given
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("PENDING_REVIEW")
                .build();
        when(settlementReviewer.submitForReview(testStatementId)).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/v2/settlements/{id}/submit", testStatementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
    }

    // ========== Admin 審核 API ==========

    @Test
    @Order(4)
    @DisplayName("IT-M07-S-004: Admin 取得待審核結算單列表（僅自己租戶，Sprint 81 DEF-040 修復）")
    void getPendingReviewStatements_AsAdmin_ReturnsList() throws Exception {
        // Given
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("PENDING_REVIEW")
                .build();
        SettlementService.SettlementStatementListResponse listResponse =
                SettlementService.SettlementStatementListResponse.builder()
                .statements(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .build();
        when(settlementReviewer.getPendingReviewStatements(0, 20, false, null)).thenReturn(listResponse);

        // When & Then
        mockMvc.perform(get("/v2/admin/settlements/pending")
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(authAs("ADMIN", "admin:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statements", hasSize(1)))
                .andExpect(jsonPath("$.data.statements[0].status").value("PENDING_REVIEW"));
    }

    @Test
    @Order(5)
    @DisplayName("IT-M07-S-005: Admin 批准結算單（自己租戶）")
    void approveStatement_AsAdmin_Success() throws Exception {
        // Given: 直接 mock settlementService.approveStatement
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("APPROVED")
                .approvedAt(java.time.Instant.now())
                .build();
        when(settlementReviewer.approveStatement(any(), any(), eq(false)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/v2/admin/settlements/{id}/approve", testStatementId)
                        .with(authentication(authAs("ADMIN", "admin:write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M07-S-006: Admin 駁回結算單（帶原因，自己租戶）")
    void rejectStatement_AsAdmin_WithReason() throws Exception {
        // Given
        String reason = "金額計算有誤，請重新提交";
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("REJECTED")
                .rejectionReason(reason)
                .build();
        when(settlementReviewer.rejectStatement(any(), any(), any(), eq(false)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/v2/admin/settlements/{id}/reject", testStatementId)
                        .param("reason", reason)
                        .with(authentication(authAs("ADMIN", "admin:write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value(reason));
    }

    @Test
    @Order(9)
    @DisplayName("🔴 IT-M07-S-009: ADMIN 批准他租戶結算單應被拒絕（403，Sprint 81 DEF-040 修復）")
    void approveStatement_AsAdminCrossTenant_Forbidden() throws Exception {
        when(settlementReviewer.approveStatement(any(), any(), eq(false)))
                .thenThrow(new com.nextkey.ecommerce.shared.exception.BusinessException(
                        com.nextkey.ecommerce.shared.exception.ErrorCode.E_1007,
                        "No permission to access this tenant's settlement statement"));

        mockMvc.perform(put("/v2/admin/settlements/{id}/approve", testStatementId)
                        .with(authentication(authAs("ADMIN", "admin:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("IT-M07-S-010: SUPER_ADMIN 批准任意租戶結算單成功（Sprint 81 DEF-040 修復）")
    void approveStatement_AsSuperAdmin_Success() throws Exception {
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("APPROVED")
                .approvedAt(java.time.Instant.now())
                .build();
        when(settlementReviewer.approveStatement(any(), any(), eq(true)))
                .thenReturn(response);

        mockMvc.perform(put("/v2/admin/settlements/{id}/approve", testStatementId)
                        .with(authentication(authAs("SUPER_ADMIN", "admin:write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    // ========== 結算單逆轉雙重授權（Sprint 86，PRD §6.2.1）==========

    @Test
    @Order(11)
    @DisplayName("IT-M07-S-011: SUPER_ADMIN 發起 PAID 結算單逆轉成功")
    void initiateReversal_asSuperAdmin_success() throws Exception {
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("REVERSAL_PENDING")
                .build();
        when(settlementMapper.toStatementResponse(any())).thenReturn(response);
        when(userRepository.findById(any())).thenReturn(Optional.of(
                com.nextkey.ecommerce.domain.model.user.User.builder().id(UUID.randomUUID()).build()));
        testStatement.setStatus(SettlementStatus.PAID);

        mockMvc.perform(post("/v2/admin/settlements/{id}/reverse/initiate", testStatementId)
                        .param("reason", "客戶投訴要求全額退款")
                        .with(authentication(authAs("SUPER_ADMIN", "settlement:reverse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVERSAL_PENDING"));
    }

    @Test
    @Order(12)
    @DisplayName("IT-M07-S-012: CFO 確認 SUPER_ADMIN 發起的逆轉成功（雙重授權）")
    void confirmReversal_asCfoAfterSuperAdminInitiated_success() throws Exception {
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("REVERSED")
                .build();
        when(settlementMapper.toStatementResponse(any())).thenReturn(response);
        when(userRepository.findById(any())).thenReturn(Optional.of(
                com.nextkey.ecommerce.domain.model.user.User.builder().id(UUID.randomUUID()).build()));
        testStatement.setStatus(SettlementStatus.REVERSAL_PENDING);
        testStatement.setReversalInitiatedByRole("SUPER_ADMIN");
        testStatement.setReversalReason("客戶投訴要求全額退款");

        mockMvc.perform(post("/v2/admin/settlements/{id}/reverse/confirm", testStatementId)
                        .with(authentication(authAs("CFO", "settlement:reverse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
    }

    @Test
    @Order(13)
    @DisplayName("🔴 IT-M07-S-013: SUPER_ADMIN 確認自己發起的逆轉應被拒絕（同角色雙重授權檢查）")
    void confirmReversal_sameSuperAdminRole_rejected() throws Exception {
        testStatement.setStatus(SettlementStatus.REVERSAL_PENDING);
        testStatement.setReversalInitiatedByRole("SUPER_ADMIN");

        mockMvc.perform(post("/v2/admin/settlements/{id}/reverse/confirm", testStatementId)
                        .with(authentication(authAs("SUPER_ADMIN", "settlement:reverse"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("IT-M07-S-014: 非 SUPER_ADMIN/CFO 角色發起逆轉應被拒絕（403，缺少 settlement:reverse 權限）")
    void initiateReversal_asStoreOwner_forbidden() throws Exception {
        mockMvc.perform(post("/v2/admin/settlements/{id}/reverse/initiate", testStatementId)
                        .param("reason", "測試")
                        .with(authentication(authAs("SELLER", "order:read"))))
                .andExpect(status().isForbidden());
    }

    // ========== 權限測試 ==========

    @Test
    @Order(7)
    @DisplayName("IT-M07-S-007: 非 Admin 角色無法批准結算單 (403)")
    @WithMockUser(username = "storeowner", authorities = {"order:read"})
    void approveStatement_AsNonAdmin_Forbidden() throws Exception {
        // When & Then
        mockMvc.perform(put("/v2/admin/settlements/{id}/approve", testStatementId))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("IT-M07-S-008: 結算單詳情跨租戶禁止訪問（404）")
    @WithMockUser(username = "storeowner", authorities = {"order:read"})
    void getStatementById_CrossTenant_NotFound() throws Exception {
        // Given: 模擬跨租戶的 statementId 在當前 tenant 找不到
        UUID otherTenantStatementId = UUID.randomUUID();
        when(settlementGenerator.getStatementById(otherTenantStatementId))
                .thenThrow(new com.nextkey.ecommerce.shared.exception.BusinessException(
                        com.nextkey.ecommerce.shared.exception.ErrorCode.E_5005,
                        "Settlement statement not found"));

        // When & Then: E_5005 映射到 404 NOT_FOUND
        mockMvc.perform(get("/v2/settlements/{id}", otherTenantStatementId))
                .andExpect(status().isNotFound());
    }
}
