package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.settlement.TransferService;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;
import com.nextkey.ecommerce.domain.model.settlement.Transfer.TransferStatus;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * TransferController API 權限測試（Sprint 80，AI-2416 Phase D-2）。
 *
 * <p>比照 DEF-038 教訓：新查詢/操作 API 從第一版就驗證跨租戶存取應被拒絕。
 * {@code TransferService} 以 {@code @MockBean} 取代，本測試專注驗證 Controller 層的角色分支
 * 邏輯（SUPER_ADMIN + tenantId 參數 → 跨租戶查詢；其餘一律呼叫 current-tenant 查詢）與權限守門。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-Transfer: TransferController 對帳查詢與重試 API (Sprint 80 AI-2416)")
class TransferControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @MockBean
    private JwtTokenService jwtTokenService;

    private static final UUID OWN_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final UUID STATEMENT_ID = UUID.randomUUID();

    private Authentication authAs(final String role, final String... extraAuthorities) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "user@example.com", role, OWN_TENANT_ID.toString());
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        for (String a : extraAuthorities) {
            authorities.add(new SimpleGrantedAuthority(a));
        }
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private Transfer sampleTransfer(UUID tenantId) {
        return Transfer.builder()
                .id(UUID.randomUUID())
                .settlementStatementId(STATEMENT_ID)
                .tenantId(tenantId)
                .transferAmount(new BigDecimal("9000.00"))
                .currency("TWD")
                .status(TransferStatus.COMPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("IT-Transfer-001: ADMIN 查詢 /v2/transfers 呼叫 current-tenant 查詢（不受 tenantId 參數影響）")
    void getTransfers_asAdminWithOtherTenantIdParam_ignoresParamAndScopesToOwnTenant() throws Exception {
        when(transferService.getTransfersForCurrentTenant(any()))
                .thenReturn(new PageImpl<>(List.of(sampleTransfer(OWN_TENANT_ID)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v2/transfers")
                        .param("tenantId", OTHER_TENANT_ID.toString())
                        .with(authentication(authAs("ADMIN", "order:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transfers[0].tenantId").value(OWN_TENANT_ID.toString()));

        org.mockito.Mockito.verify(transferService, org.mockito.Mockito.never())
                .getTransfersForTenant(eq(OTHER_TENANT_ID), any());
    }

    @Test
    @DisplayName("IT-Transfer-002: SUPER_ADMIN 帶 tenantId 可查詢任意租戶")
    void getTransfers_asSuperAdminWithTenantIdParam_queriesOtherTenant() throws Exception {
        when(transferService.getTransfersForTenant(eq(OTHER_TENANT_ID), any()))
                .thenReturn(new PageImpl<>(List.of(sampleTransfer(OTHER_TENANT_ID)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v2/transfers")
                        .param("tenantId", OTHER_TENANT_ID.toString())
                        .with(authentication(authAs("SUPER_ADMIN", "order:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transfers[0].tenantId").value(OTHER_TENANT_ID.toString()));
    }

    @Test
    @DisplayName("IT-Transfer-003: 無 order:read 權限應被拒絕（403）")
    void getTransfers_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/v2/transfers").with(authentication(authAs("BUYER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🔴 IT-Transfer-004: ADMIN 重試他租戶 transfer 應被拒絕（403）")
    void retryTransfer_asAdminCrossTenant_forbidden() throws Exception {
        when(transferService.retryFailedTransfer(eq(STATEMENT_ID), eq(false)))
                .thenThrow(new BusinessException(ErrorCode.E_1007, "No permission to access this tenant's transfer records"));

        mockMvc.perform(post("/v2/admin/transfers/{statementId}/retry", STATEMENT_ID)
                        .with(csrf())
                        .with(authentication(authAs("ADMIN", "admin:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-Transfer-005: SUPER_ADMIN 重試任意租戶 transfer 成功")
    void retryTransfer_asSuperAdmin_success() throws Exception {
        Transfer retried = sampleTransfer(OTHER_TENANT_ID);
        when(transferService.retryFailedTransfer(eq(STATEMENT_ID), eq(true))).thenReturn(retried);

        mockMvc.perform(post("/v2/admin/transfers/{statementId}/retry", STATEMENT_ID)
                        .with(csrf())
                        .with(authentication(authAs("SUPER_ADMIN", "admin:write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
