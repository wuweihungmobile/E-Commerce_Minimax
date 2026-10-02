package com.nextkey.ecommerce.integration;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.dto.StripeConnectDto;
import com.nextkey.ecommerce.core.tenant.TenantStripeConnectService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 賣家 Stripe Connect Express onboarding API 整合測試（Sprint 53 AI-2413 Phase D-1）。
 * TenantStripeConnectService 邏輯已於 TenantStripeConnectServiceTest 單元覆蓋，
 * 本測試僅驗證 controller 接線（角色授權 + 回應封裝）。
 *
 * 測試範圍：
 * - IT-CONNECT-001: SELLER 發起 onboarding → 200 + onboardingUrl
 * - IT-CONNECT-002: BUYER 發起 onboarding → 403
 * - IT-CONNECT-003: SELLER 查詢狀態 → 200 + 狀態欄位
 * - IT-CONNECT-004: 無 JWT → 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-CONNECT: 賣家 Stripe Connect onboarding API 整合測試")
class M13SellerStripeConnectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantStripeConnectService tenantStripeConnectService;

    private static final String ONBOARDING_URL = "/v2/seller/dashboard/stripe-connect/onboarding";
    private static final String STATUS_URL = "/v2/seller/dashboard/stripe-connect/status";

    @Test
    @DisplayName("IT-CONNECT-001: STORE_OWNER（店主）發起 onboarding → 200 + onboardingUrl")
    @WithMockUser(username = "owner", roles = {"STORE_OWNER"})
    void initiateOnboarding_asStoreOwner_returns200WithUrl() throws Exception {
        when(tenantStripeConnectService.initiateOnboarding(any(UUID.class)))
                .thenReturn(StripeConnectDto.OnboardingResponse.builder()
                        .onboardingUrl("https://connect.stripe.com/setup/e/acct_1/onboarding")
                        .build());

        mockMvc.perform(post(ONBOARDING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.onboardingUrl")
                        .value("https://connect.stripe.com/setup/e/acct_1/onboarding"));
    }

    @Test
    @DisplayName("IT-CONNECT-002: BUYER（非店主）發起 onboarding → 403")
    @WithMockUser(username = "buyer", authorities = {"order:create"})
    void initiateOnboarding_asBuyer_returns403() throws Exception {
        mockMvc.perform(post(ONBOARDING_URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-CONNECT-003: STORE_OWNER（店主）查詢狀態 → 200 + 狀態欄位")
    @WithMockUser(username = "owner", roles = {"STORE_OWNER"})
    void getStatus_asStoreOwner_returns200WithStatus() throws Exception {
        when(tenantStripeConnectService.getAccountStatus(any(UUID.class)))
                .thenReturn(StripeConnectDto.StatusResponse.builder()
                        .accountId("acct_1").onboardingStatus("PENDING")
                        .chargesEnabled(false).payoutsEnabled(false).build());

        mockMvc.perform(get(STATUS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.onboardingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.chargesEnabled").value(false));
    }

    @Test
    @DisplayName("IT-CONNECT-004: 無 JWT → 401")
    void initiateOnboarding_noJwt_returns401() throws Exception {
        mockMvc.perform(post(ONBOARDING_URL))
                .andExpect(status().isUnauthorized());
    }

    // Sprint 235（DEF-327）：原本是 hasRole('SELLER')——自助註冊即得、不建租戶；核准開店後的角色是 STORE_OWNER，
    // 真正的店主反而呼叫不了，只有沒有店鋪的 SELLER 能呼叫（在系統租戶上建立 Connect 帳戶）。

    @Test
    @DisplayName("IT-CONNECT-005: SELLER（自助註冊、沒有店鋪）發起 onboarding → 403，不得呼叫到服務")
    @WithMockUser(username = "seller", roles = {"SELLER"})
    void initiateOnboarding_asSelfRegisteredSeller_returns403() throws Exception {
        mockMvc.perform(post(ONBOARDING_URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tenantStripeConnectService);
    }

    @Test
    @DisplayName("IT-CONNECT-006: SELLER（自助註冊、沒有店鋪）查詢狀態 → 403，不得呼叫到服務")
    @WithMockUser(username = "seller", roles = {"SELLER"})
    void getStatus_asSelfRegisteredSeller_returns403() throws Exception {
        mockMvc.perform(get(STATUS_URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tenantStripeConnectService);
    }
}
