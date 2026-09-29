package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * 整合測試裡「某角色有哪些權限」必須就是生產的 {@link RolePermissionMapping}（Sprint 217，DEF-304）。
 *
 * <p>{@link IntegrationTestConfiguration} 原本以 spy 覆寫權限表，每個角色回傳一份手抄清單。那份清單長期比生產多給
 * （SELLER／STORE_OWNER／ADMIN 能用購物車、BUYER 有 {@code order:update}…），整合測試於是能以生產不存在的權限通過：
 * DEF-298「一般買家付不了款」在 215 個 Sprint 裡都被它蓋住。這裡守住兩件事：權限表本身沒有被替換，
 * 以及經過真實 filter chain 時，角色拿到的權限與生產一致（以 PRD §7.3「購物車只有 Buyer 能用」為例）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("DEF-304：整合測試的角色權限就是生產權限表")
class IntegrationContextUsesProductionPermissionsIntegrationTest {

    @Autowired
    private RolePermissionMapping rolePermissionMapping;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("權限表不是 mock 或 spy，且每個角色的權限與生產完全相同")
    void permissionMappingIsTheProductionComponent() {
        assertThat(Mockito.mockingDetails(rolePermissionMapping).isMock())
                .as("整合測試不可再替換權限表——手抄清單會讓測試以生產不存在的權限通過（DEF-298／DEF-304）")
                .isFalse();

        RolePermissionMapping production = new RolePermissionMapping();
        for (User.UserRole role : User.UserRole.values()) {
            assertThat(rolePermissionMapping.getAuthorities(role))
                    .as("角色 %s 的權限", role)
                    .containsExactlyInAnyOrderElementsOf(production.getAuthorities(role));
        }
    }

    @Test
    @DisplayName("PRD §7.3：購物車只有 Buyer 能用——買家 200，賣家／店主／管理員經過真實 filter chain 都是 403")
    void onlyBuyerCanUseCart() throws Exception {
        mockMvc.perform(get("/v2/cart").header("Authorization", bearer(User.UserRole.BUYER)))
                .andExpect(status().isOk());

        for (User.UserRole role : new User.UserRole[] {
                User.UserRole.SELLER, User.UserRole.STORE_OWNER, User.UserRole.ADMIN}) {
            mockMvc.perform(get("/v2/cart").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("E-1007"));
        }
    }

    private String bearer(final User.UserRole role) {
        return "Bearer " + jwtTokenService.generateAccessToken(UUID.randomUUID(),
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com", role.name(),
                AppConstants.SYSTEM_TENANT_ID);
    }
}
