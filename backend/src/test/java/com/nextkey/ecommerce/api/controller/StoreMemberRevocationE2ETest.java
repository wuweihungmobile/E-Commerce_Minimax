package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 店鋪成員被移除（或只是受邀）後，登入與換發的 JWT 不得再帶店鋪租戶與員工角色（Sprint 235，DEF-329，真實 PostgreSQL）。
 *
 * <p>JWT 的 {@code tenantId}、{@code role} 是之後所有授權與租戶過濾的唯一來源。修復前
 * {@code AuthService.resolveTenantForUser} 用不看狀態的 {@code findByUserId}，而 {@code removeMember} 只改成員狀態、
 * 不改 {@code user.role}：被移除的店員登入／refresh 仍是 {@code STORE_STAFF}＋店鋪租戶；只是受邀、尚未接受的買家登入
 * 也帶店鋪租戶（真實全棧實測，{@code at-store-member-revocation-real.spec.ts}）。DEF-319 之後店鋪租戶會擁有真實消費者的
 * 訂單與客服工單，這會變成讀取他們資料的路徑，所以必須先修。
 *
 * <p>走完整 HTTP 鏈：邀請／接受／移除都打真實端點，登入與換發也是；斷言的是回傳的 JWT 本身。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("Sprint 235: 店鋪成員被移除或只是受邀，登入／換發的 JWT 不得帶店鋪租戶與員工角色")
class StoreMemberRevocationE2ETest {

    private static final String PASSWORD = "SecurePass123!";
    private static final String SYSTEM_TENANT_ID = AppConstants.SYSTEM_TENANT_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;
    @Autowired private PostCategoryRepository postCategoryRepository;
    @Autowired private PostRepository postRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────── 受邀、尚未接受 ─────────────────────────

    @Test
    @DisplayName("只是受邀、尚未接受：登入後不得帶店鋪租戶，角色仍是 BUYER")
    void invitedButNotAccepted_loginHasNoStoreTenant() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);

        Claims claims = login(staff);

        assertThat(claims.tenantId()).as("尚未接受邀請的人登入後不得帶店鋪租戶").isEqualTo(SYSTEM_TENANT_ID);
        assertThat(claims.role()).isEqualTo("BUYER");
    }

    @Test
    @DisplayName("拒絕邀請之後：登入後同樣不帶店鋪租戶")
    void declinedInvite_loginHasNoStoreTenant() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);
        given().header("Authorization", "Bearer " + login(staff).accessToken())
                .when().post("/v2/tenants/" + store.tenantId() + "/members/invite/decline")
                .then().statusCode(200);

        assertThat(login(staff).tenantId()).isEqualTo(SYSTEM_TENANT_ID);
    }

    // ───────────────────────── 接受邀請（對照組） ─────────────────────────

    @Test
    @DisplayName("接受邀請之後：登入帶店鋪租戶與 STORE_STAFF（合法的對照組，避免守門變成對所有人都降級）")
    void accepted_loginHasStoreTenantAndStaffRole() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);
        accept(store, staff);

        Claims claims = login(staff);

        assertThat(claims.tenantId()).isEqualTo(store.tenantId().toString());
        assertThat(claims.role()).isEqualTo("STORE_STAFF");
    }

    // ───────────────────────── 被移除 ─────────────────────────

    @Test
    @DisplayName("被店主移除之後：重新登入不得再帶店鋪租戶，也不得保留 STORE_STAFF")
    void removed_loginHasNoStoreTenantAndNoStaffRole() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);
        accept(store, staff);
        remove(store, staff);

        Claims claims = login(staff);

        assertThat(claims.tenantId()).as("被移除的成員登入後不得再帶店鋪租戶").isEqualTo(SYSTEM_TENANT_ID);
        assertThat(claims.role()).as("被移除的成員不得保留店員角色").isEqualTo("BUYER");
    }

    @Test
    @DisplayName("被移除之後：用移除前取得的 refresh token 換發，同樣不得帶店鋪租戶與 STORE_STAFF")
    void removed_refreshWithOldRefreshToken_hasNoStoreTenantAndNoStaffRole() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);
        accept(store, staff);
        Claims before = login(staff);
        assertThat(before.tenantId()).as("前提：移除前確實帶店鋪租戶").isEqualTo(store.tenantId().toString());
        remove(store, staff);

        String refreshed = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("refreshToken", before.refreshToken()))
                .when().post("/v2/auth/refresh")
                .then().statusCode(200).extract().asString();
        String accessToken = objectMapper.readTree(refreshed).path("data").path("accessToken").asText();

        assertThat(jwtTokenService.getTenantId(accessToken)).isEqualTo(SYSTEM_TENANT_ID);
        assertThat(jwtTokenService.getRole(accessToken)).isEqualTo("BUYER");
    }

    @Test
    @DisplayName("同時是另一間店鋪的有效店員：被一間店移除後，角色維持 STORE_STAFF，租戶改為仍有效的那間")
    void removedFromOneStoreButStillActiveInAnother_keepsStaffRoleAndOtherTenant() throws Exception {
        Store first = newStore();
        Store second = newStore();
        Member staff = newBuyer();
        invite(first, staff);
        accept(first, staff);
        // 第二間店鋪直接建立有效成員資料（邀請 API 對已是店員的人同樣可用，這裡只求狀態）
        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(second.tenantId())
                .userId(staff.userId())
                .storeRole(TenantMember.StoreRole.STORE_STAFF)
                .status(TenantMember.MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
        remove(first, staff);

        Claims claims = login(staff);

        assertThat(claims.role()).as("仍是別間店鋪的有效店員，不能被降級").isEqualTo("STORE_STAFF");
        assertThat(claims.tenantId()).isEqualTo(second.tenantId().toString());
    }


    @Test
    @DisplayName("遺留資料：修復前就被移除、user.role 仍殘留 STORE_STAFF 的人，貼文不得被建立在原店鋪的租戶下")
    void legacyRemovedStaffWithStaffRole_cannotCreatePostInStoreTenant() throws Exception {
        // PostController 的租戶推導以成員資格為第一來源（不是 JWT）。修復前 removeMember 不收回角色，所以資料庫裡
        // 已經有被移除、user.role 仍是 STORE_STAFF 的人：他們仍通過 @PreAuthorize，成員資格查詢又不看狀態，
        // 貼文就建在原店鋪。這裡把角色改回 STORE_STAFF 來模擬這類遺留資料，驗證「只認有效成員」這道防線本身。
        LegacyRemovedStaff legacy = newLegacyRemovedStaff();

        assertNoPostCreatedInStoreTenant(legacy, login(legacy.member()).accessToken());
    }

    // ───────────────────────── 輔助 ─────────────────────────

    private record Store(UUID tenantId, UUID ownerId, String ownerToken) { }

    private record LegacyRemovedStaff(Store store, Member member, PostCategory storeCategory) { }

    /** 建立「修復前就被移除、user.role 仍殘留 STORE_STAFF」的遺留資料，並在原店鋪建好一個分類。 */
    private LegacyRemovedStaff newLegacyRemovedStaff() throws Exception {
        Store store = newStore();
        Member staff = newBuyer();
        invite(store, staff);
        accept(store, staff);
        remove(store, staff);
        User user = userRepository.findById(staff.userId()).orElseThrow();
        user.setRole(User.UserRole.STORE_STAFF);
        userRepository.save(user);
        Tenant storeTenant = tenantRepository.findById(store.tenantId()).orElseThrow();
        PostCategory category = postCategoryRepository.save(PostCategory.builder()
                .tenant(storeTenant)
                .name("撤銷測試分類 " + System.nanoTime())
                .slug("revoke-cat-" + System.nanoTime())
                .isActive(true)
                .build());
        return new LegacyRemovedStaff(store, staff, category);
    }

    private void assertNoPostCreatedInStoreTenant(final LegacyRemovedStaff legacy, final String token) {
        given().header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("title", "被移除成員的貼文", "content", "不該出現在原店鋪",
                        "categoryId", legacy.storeCategory().getId().toString()))
                .when().post("/v2/dashboard/posts");

        assertThat(postRepository.findByTenantId(legacy.store().tenantId(), Pageable.unpaged()).getContent())
                .as("被移除的成員不得在原店鋪的租戶下建立貼文")
                .isEmpty();
    }

    private record Member(UUID userId, String email) { }

    private record Claims(String accessToken, String refreshToken, String tenantId, String role) { }

    /** 建立一間 ACTIVE 店鋪與店主（STORE_OWNER，ACTIVE 的 OWNER 成員列）。 */
    private Store newStore() {
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("撤銷測試店 " + stamp)
                .slug("revoke-e2e-" + stamp)
                .contactEmail("revoke-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .email("revoke-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .build());
        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenant.getId())
                .userId(owner.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .status(TenantMember.MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
        String ownerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", tenant.getId().toString());
        return new Store(tenant.getId(), owner.getId(), ownerToken);
    }

    /** 真實註冊一位買家（已知密碼，之後可真實登入）。 */
    private Member newBuyer() {
        String email = "revoke-buyer-" + System.nanoTime() + "@example.com";
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(PASSWORD).userType("BUYER").build())
                .when().post("/v2/auth/register")
                .then().statusCode(201);
        return new Member(userRepository.findByEmail(email).orElseThrow().getId(), email);
    }

    private Claims login(final Member member) throws Exception {
        String response = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(member.email()).password(PASSWORD).build())
                .when().post("/v2/auth/login")
                .then().statusCode(200).extract().asString();
        JsonNode data = objectMapper.readTree(response).path("data");
        String accessToken = data.path("accessToken").asText();
        return new Claims(accessToken, data.path("refreshToken").asText(),
                jwtTokenService.getTenantId(accessToken), jwtTokenService.getRole(accessToken));
    }

    private void invite(final Store store, final Member member) {
        inviteAs(store, member, "STORE_STAFF");
    }

    private void inviteAs(final Store store, final Member member, final String role) {
        given().header("Authorization", "Bearer " + store.ownerToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("userId", member.userId().toString(), "role", role))
                .when().post("/v2/tenants/" + store.tenantId() + "/members/invite")
                .then().statusCode(201);
    }

    private void accept(final Store store, final Member member) throws Exception {
        given().header("Authorization", "Bearer " + login(member).accessToken())
                .when().post("/v2/tenants/" + store.tenantId() + "/members/invite/accept")
                .then().statusCode(200);
    }

    private void remove(final Store store, final Member member) {
        given().header("Authorization", "Bearer " + store.ownerToken())
                .when().delete("/v2/tenants/" + store.tenantId() + "/members/" + member.userId())
                .then().statusCode(org.hamcrest.Matchers.lessThan(300));
    }
}
