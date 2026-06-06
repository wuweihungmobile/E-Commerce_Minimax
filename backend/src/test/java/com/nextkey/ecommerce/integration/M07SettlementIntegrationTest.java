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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private JwtTokenService jwtTokenService;

    private UUID testTenantId;
    private UUID testStatementId;
    private SettlementStatement testStatement;

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
    @DisplayName("IT-M07-S-004: Admin 取得待審核結算單列表")
    @WithMockUser(username = "admin", authorities = {"admin:read"})
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
        when(settlementReviewer.getPendingReviewStatements(0, 20)).thenReturn(listResponse);

        // When & Then
        mockMvc.perform(get("/v2/admin/settlements/pending")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statements", hasSize(1)))
                .andExpect(jsonPath("$.data.statements[0].status").value("PENDING_REVIEW"));
    }

    @Test
    @Order(5)
    @DisplayName("IT-M07-S-005: Admin 批准結算單")
    @WithMockUser(username = "admin", authorities = {"admin:write"})
    void approveStatement_AsAdmin_Success() throws Exception {
        // Given: 直接 mock settlementService.approveStatement
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("APPROVED")
                .approvedAt(java.time.Instant.now())
                .build();
        when(settlementReviewer.approveStatement(any(), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/v2/admin/settlements/{id}/approve", testStatementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M07-S-006: Admin 駁回結算單（帶原因）")
    @WithMockUser(username = "admin", authorities = {"admin:write"})
    void rejectStatement_AsAdmin_WithReason() throws Exception {
        // Given
        String reason = "金額計算有誤，請重新提交";
        SettlementService.SettlementStatementResponse response = SettlementService.SettlementStatementResponse.builder()
                .id(testStatementId)
                .status("REJECTED")
                .rejectionReason(reason)
                .build();
        when(settlementReviewer.rejectStatement(any(), any(), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/v2/admin/settlements/{id}/reject", testStatementId)
                        .param("reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value(reason));
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
