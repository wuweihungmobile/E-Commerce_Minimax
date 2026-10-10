package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * DEF-321 (a) / Sprint 248：以 email 查詢可邀請的使用者（{@code GET /v2/tenants/:id/members/lookup}）。
 *
 * <p>邀請表單只知道對方 email，{@link com.nextkey.ecommerce.core.tenant.TenantService#inviteMember}
 * 需要 userId（PRD §9.11 只定義 userId 版本），此端點在真實 PostgreSQL 下驗證 email→userId 解析與
 * 權限鏈（僅該店鋪 StoreOwner）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("Sprint 248: 以 email 查詢可邀請的使用者（members/lookup）")
class TenantMemberLookupIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("店主查詢已註冊的 email → 200，回傳對應 userId/displayName")
    void lookup_ownerFindsExistingUserByEmail_returnsCandidate() throws Exception {
        Store store = newStore();
        User candidate = newUser("lookup-candidate");

        String body = given().header("Authorization", "Bearer " + store.ownerToken())
                .when().get("/v2/tenants/" + store.tenantId() + "/members/lookup?email=" + candidate.getEmail())
                .then().statusCode(200).extract().asString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("userId").asText()).isEqualTo(candidate.getId().toString());
        assertThat(data.path("email").asText()).isEqualTo(candidate.getEmail());
    }

    @Test
    @DisplayName("email 從未註冊過 → 403（E-2001，與 inviteMember 既有的「找不到使用者」同碼）")
    void lookup_emailNotRegistered_returnsForbiddenWithE2001() throws Exception {
        Store store = newStore();

        String body = given().header("Authorization", "Bearer " + store.ownerToken())
                .when().get("/v2/tenants/" + store.tenantId() + "/members/lookup?email=nobody-" + System.nanoTime() + "@example.com")
                .then().statusCode(403).extract().asString();

        assertThat(objectMapper.readTree(body).path("code").asText()).isEqualTo("E-2001");
    }

    @Test
    @DisplayName("呼叫者不是這間店的 StoreOwner → 403（E-4031）")
    void lookup_callerNotOwnerOfThisStore_returnsForbidden() throws Exception {
        Store storeA = newStore();
        Store storeB = newStore();
        User candidate = newUser("lookup-other-owner");

        String body = given().header("Authorization", "Bearer " + storeB.ownerToken())
                .when().get("/v2/tenants/" + storeA.tenantId() + "/members/lookup?email=" + candidate.getEmail())
                .then().statusCode(403).extract().asString();

        assertThat(objectMapper.readTree(body).path("code").asText()).isEqualTo("E-4031");
    }

    // ───────────────────────── 輔助 ─────────────────────────

    private record Store(UUID tenantId, String ownerToken) { }

    private Store newStore() {
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("成員查詢測試店 " + stamp)
                .slug("member-lookup-" + stamp)
                .contactEmail("member-lookup-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User owner = newUser("member-lookup-owner");
        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenant.getId())
                .userId(owner.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .status(TenantMember.MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
        String ownerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", tenant.getId().toString());
        return new Store(tenant.getId(), ownerToken);
    }

    private User newUser(final String label) {
        return userRepository.save(User.builder()
                .email(label + "-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy")
                .fullName("測試使用者 " + label)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build());
    }
}
