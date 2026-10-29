package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * 店鋪的一般成員（STORE_STAFF）對店主自助端點的權限（Sprint 316，DEF-438；真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>{@code TenantController} 九個店主自助端點（API_M17 §2.1）的 {@code @PreAuthorize} 只放行 SCOPE_store:*（死權限）、
 * ROLE_STORE_OWNER、ROLE_ADMIN；店員的 JWT 角色是 STORE_STAFF，所以在<b>方法安全層</b>就被擋下，回 403 E-1007，
 * 到不了 Service 層「是不是該租戶的店主」那一道檢查（E-4031）。API_M17 在 Sprint 316 之前把店員的結果寫成 E-4031，
 * 這個類別把實測值釘住，文件才有東西可以對照。
 *
 * <p>兩個案例成對：店員被擋；店主做<b>同樣</b>的九個請求都成功——後者證明店員被擋的原因是角色，不是路徑或請求本文寫錯
 * （方法安全層在參數綁定之後才執行，請求本文不合法會先得 400，永遠看不到 403）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-TENANT-STAFF: 店鋪一般成員（STORE_STAFF）對店主自助端點的權限（Sprint 316，DEF-438）")
class TenantStaffAccessIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    /** 方法安全層（{@code @PreAuthorize}）擋下時的錯誤碼；Service 層「不是該租戶店主」才是 E-4031。 */
    private static final String DENIED_BY_METHOD_SECURITY = "E-1007";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;

    private long stamp;
    private Tenant store;
    private User staff;
    private User target;
    private User candidate;
    private String staffToken;
    private String ownerToken;

    /** 一個店主自助端點：標籤（對應 API_M17 的章節）、方法、路徑、請求本文，以及店主做同一個請求應得的狀態碼。 */
    private record Call(String label, HttpMethod method, String path, Object body, int ownerStatus) { }

    @BeforeEach
    void seed() {
        stamp = System.nanoTime();
        store = tenantRepository.save(Tenant.builder().name("DEF-438 店鋪 " + stamp).slug("def438-" + stamp)
                .contactEmail("def438-" + stamp + "@tenant.com").status(Tenant.TenantStatus.ACTIVE).build());
        User owner = member(user("owner", User.UserRole.STORE_OWNER, store.getId()), TenantMember.StoreRole.STORE_OWNER);
        staff = member(user("staff", User.UserRole.STORE_STAFF, store.getId()), TenantMember.StoreRole.STORE_STAFF);
        target = member(user("target", User.UserRole.STORE_STAFF, store.getId()), TenantMember.StoreRole.STORE_STAFF);
        candidate = user("candidate", User.UserRole.BUYER, SYSTEM_TENANT_ID);
        ownerToken = token(owner, "STORE_OWNER");
        staffToken = token(staff, "STORE_STAFF");
    }

    @Test
    @DisplayName("店員（本店的有效 STORE_STAFF）呼叫九個店主自助端點：一律 403 E-1007——方法安全層擋下，到不了 Service 層的店主檢查（E-4031）")
    void staff_isRejectedByMethodSecurityOnEveryOwnerEndpoint() throws Exception {
        // 前提：店員真的是本店的有效成員——否則「被擋」可能只是因為他不屬於這間店鋪
        assertThat(tenantMemberRepository.existsByTenantIdAndUserIdAndStatus(
                store.getId(), staff.getId(), TenantMember.MemberStatus.ACTIVE))
                .as("前提：店員是本店的有效成員").isTrue();

        List<String> mismatches = new ArrayList<>();
        for (Call call : calls()) {
            MockHttpServletResponse response = send(call, staffToken);
            String code = codeOf(response);
            if (response.getStatus() != 403 || !DENIED_BY_METHOD_SECURITY.equals(code)) {
                mismatches.add(call.label() + "：預期 403 " + DENIED_BY_METHOD_SECURITY
                        + "，實際 " + response.getStatus() + " " + code);
            }
        }
        assertThat(mismatches).as("店員 x 九個端點的結果表").isEmpty();
    }

    @Test
    @DisplayName("對照組：店主做同樣的九個請求都成功——證明店員被擋的原因是角色，不是路徑或請求本文寫錯")
    void owner_succeedsOnTheSameRequests() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (Call call : calls()) {
            MockHttpServletResponse response = send(call, ownerToken);
            if (response.getStatus() != call.ownerStatus()) {
                mismatches.add(call.label() + "：預期 " + call.ownerStatus()
                        + "，實際 " + response.getStatus() + " " + codeOf(response));
            }
        }
        assertThat(mismatches).as("店主 x 九個端點的對照結果").isEmpty();
    }

    // ── 輔助 ─────────────────────────────────────────────────

    /** 九個端點，順序同 API_M17 §2.1；有狀態變化的（邀請、改角色、移除）放在後面，店主的對照案例依序執行。 */
    private List<Call> calls() {
        String base = "/v2/tenants/" + store.getId();
        return List.of(
                new Call("§4.2 GET /v2/tenants/my", HttpMethod.GET, "/v2/tenants/my", null, 200),
                new Call("§4.4 PUT /v2/tenants/{id}", HttpMethod.PUT, base, Map.of("storeName", "改名 " + stamp), 200),
                new Call("§4.5 GET /v2/dashboard/tenants/features", HttpMethod.GET,
                        "/v2/dashboard/tenants/features", null, 200),
                new Call("§4.6 PUT /v2/dashboard/tenants/features/{feature}", HttpMethod.PUT,
                        "/v2/dashboard/tenants/features/CMS_ENABLED", Map.of("enabled", false), 200),
                new Call("§4.7 GET /v2/tenants/{id}/members", HttpMethod.GET, base + "/members", null, 200),
                new Call("§4.8 POST /v2/tenants/{id}/members/invite", HttpMethod.POST, base + "/members/invite",
                        Map.of("userId", candidate.getId().toString(), "role", "STORE_STAFF"), 201),
                new Call("§4.9 GET /v2/tenants/{id}/members/lookup", HttpMethod.GET,
                        base + "/members/lookup?email=" + candidate.getEmail(), null, 200),
                new Call("§4.13 PUT /v2/tenants/{id}/members/{userId}/role", HttpMethod.PUT,
                        base + "/members/" + target.getId() + "/role", Map.of("role", "STORE_STAFF"), 200),
                new Call("§4.14 DELETE /v2/tenants/{id}/members/{userId}", HttpMethod.DELETE,
                        base + "/members/" + target.getId(), null, 200));
    }

    private MockHttpServletResponse send(final Call call, final String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(call.method(), call.path())
                .header("Authorization", "Bearer " + token);
        if (call.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(call.body()));
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    /** 失敗回應的錯誤碼；成功回應沒有 code 欄位。 */
    private String codeOf(final MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        return body.isBlank() ? "(無回應本文)" : objectMapper.readTree(body).path("code").asText("(無 code)");
    }

    private User user(final String label, final User.UserRole role, final UUID tenantId) {
        return userRepository.save(User.builder().email("def438-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy").fullName(label).role(role).status("ACTIVE").tenantId(tenantId).build());
    }

    private User member(final User user, final TenantMember.StoreRole storeRole) {
        tenantMemberRepository.save(TenantMember.builder().tenantId(store.getId()).userId(user.getId())
                .storeRole(storeRole).status(TenantMember.MemberStatus.ACTIVE).build());
        return user;
    }

    private String token(final User user, final String role) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), role, store.getId().toString());
    }
}
