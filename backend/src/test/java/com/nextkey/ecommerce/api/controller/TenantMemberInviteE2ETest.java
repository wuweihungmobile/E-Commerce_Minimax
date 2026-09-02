package com.nextkey.ecommerce.api.controller;

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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 店鋪成員邀請確認制端到端 E2E 測試（PRD §7.4/§8.2.3/§9.11，Sprint 98）
 *
 * 重新全面比對 PRD 全文發現的第六個缺口：PRD schema 定義 tenant_members.status
 * (INVITED/ACTIVE/REMOVED) 且 API 表格寫「邀請成員」，但實際上 StoreOwner 新增員工是
 * 單方直接生效，被邀請人沒有接受/拒絕的機會。本測試驗證修復後的邀請→確認兩階段流程。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-M17-Invite: 店鋪成員邀請確認制端到端流程")
class TenantMemberInviteE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static final String BASE_URL = "/v2";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    private UUID createTenantWithOwner(UUID ownerId) {
        Tenant tenant = Tenant.builder()
                .name("邀請測試店鋪 " + System.currentTimeMillis())
                .slug("invite-e2e-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        tenant = tenantRepository.save(tenant);

        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenant.getId())
                .userId(ownerId)
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .status(TenantMember.MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
        return tenant.getId();
    }

    private String createUserAndGetToken(User.UserRole role) {
        String email = uniqueEmail(role.name().toLowerCase());
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(role)
                .status("ACTIVE")
                .build();
        user = userRepository.save(user);
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), role.name(), null);
    }

    private UUID currentUserId(String token) {
        return jwtTokenService.getUserId(token);
    }

    @Test
    @DisplayName("IT-M17-INV-001: 完整迴路 — 邀請 → 被邀請人可見 → 接受 → 正式成為 ACTIVE 成員")
    void fullLoop_inviteAcceptBecomesActiveMember() throws Exception {
        String ownerToken = createUserAndGetToken(User.UserRole.STORE_OWNER);
        UUID ownerId = currentUserId(ownerToken);
        UUID tenantId = createTenantWithOwner(ownerId);

        String inviteeToken = createUserAndGetToken(User.UserRole.BUYER);
        UUID inviteeId = currentUserId(inviteeToken);

        // StoreOwner 邀請
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("userId", inviteeId.toString(), "role", "STORE_STAFF"))
                .when()
                .post(BASE_URL + "/tenants/" + tenantId + "/members/invite")
                .then()
                .statusCode(201)
                .body("data.status", equalTo("INVITED"));

        // 被邀請人可在自己的待確認邀請列表看到
        given()
                .header("Authorization", "Bearer " + inviteeToken)
                .when()
                .get(BASE_URL + "/tenants/invites/my")
                .then()
                .statusCode(200)
                .body("data.invites.tenantId", hasItem(tenantId.toString()));

        // 被邀請人接受
        String acceptResponse = given()
                .header("Authorization", "Bearer " + inviteeToken)
                .when()
                .post(BASE_URL + "/tenants/" + tenantId + "/members/invite/accept")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("ACTIVE"))
                .extract().asString();

        JsonNode json = objectMapper.readTree(acceptResponse);
        assertThat(json.path("data").path("joinedAt").isNull()).isFalse();

        // 驗證資料庫真的變成 ACTIVE
        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, inviteeId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(TenantMember.MemberStatus.ACTIVE);
        assertThat(member.getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("IT-M17-INV-002: 拒絕邀請 → 狀態轉為 REMOVED，不會成為店鋪成員")
    void declineInvite_doesNotBecomeMember() {
        String ownerToken = createUserAndGetToken(User.UserRole.STORE_OWNER);
        UUID ownerId = currentUserId(ownerToken);
        UUID tenantId = createTenantWithOwner(ownerId);

        String inviteeToken = createUserAndGetToken(User.UserRole.BUYER);
        UUID inviteeId = currentUserId(inviteeToken);

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("userId", inviteeId.toString(), "role", "STORE_STAFF"))
                .when()
                .post(BASE_URL + "/tenants/" + tenantId + "/members/invite")
                .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + inviteeToken)
                .when()
                .post(BASE_URL + "/tenants/" + tenantId + "/members/invite/decline")
                .then()
                .statusCode(200);

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, inviteeId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(TenantMember.MemberStatus.REMOVED);
    }

    @Test
    @DisplayName("IT-M17-INV-003: 移除既有成員 → 軟刪除，成員列表不再顯示")
    void removeMember_softDeletesAndHidesFromList() {
        String ownerToken = createUserAndGetToken(User.UserRole.STORE_OWNER);
        UUID ownerId = currentUserId(ownerToken);
        UUID tenantId = createTenantWithOwner(ownerId);

        String memberToken = createUserAndGetToken(User.UserRole.BUYER);
        UUID memberId = currentUserId(memberToken);
        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenantId).userId(memberId)
                .storeRole(TenantMember.StoreRole.STORE_STAFF)
                .status(TenantMember.MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .delete(BASE_URL + "/tenants/" + tenantId + "/members/" + memberId)
                .then()
                .statusCode(200);

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, memberId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(TenantMember.MemberStatus.REMOVED);

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get(BASE_URL + "/tenants/" + tenantId + "/members")
                .then()
                .statusCode(200)
                .body("data.members.userId", not(hasItem(memberId.toString())));
    }

    @Test
    @DisplayName("IT-M17-INV-004: 曾被移除的成員再次邀請 → 更新既有紀錄重新變為 INVITED")
    void reInviteAfterRemoval_reusesExistingRow() {
        String ownerToken = createUserAndGetToken(User.UserRole.STORE_OWNER);
        UUID ownerId = currentUserId(ownerToken);
        UUID tenantId = createTenantWithOwner(ownerId);

        String memberToken = createUserAndGetToken(User.UserRole.BUYER);
        UUID memberId = currentUserId(memberToken);
        TenantMember removed = tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenantId).userId(memberId)
                .storeRole(TenantMember.StoreRole.STORE_STAFF)
                .status(TenantMember.MemberStatus.REMOVED)
                .joinedAt(Instant.now())
                .build());

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("userId", memberId.toString(), "role", "STORE_STAFF"))
                .when()
                .post(BASE_URL + "/tenants/" + tenantId + "/members/invite")
                .then()
                .statusCode(201)
                .body("data.status", equalTo("INVITED"));

        TenantMember reInvited = tenantMemberRepository.findById(removed.getId()).orElseThrow();
        assertThat(reInvited.getStatus()).isEqualTo(TenantMember.MemberStatus.INVITED);
    }
}
