package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 非 ACTIVE 店鋪的詳情與更新（Sprint 315，DEF-432③；真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>FRD US-M17-003 邊界條件：店鋪狀態非 ACTIVE 仍允許更新。{@code PUT /v2/tenants/{id}} 本來就不檢查店鋪狀態，但編輯表單要靠
 * {@code GET /v2/tenants/{id}} 預填聯絡信箱、電話、描述與採購審批門檻，而 GET 對非 ACTIVE 一律只回精簡版——店主進得了表單也預填不到。
 * 現在：該店鋪的有效成員（{@code tenant_members.status = ACTIVE}）不論店鋪狀態都拿到完整資料；其他人（匿名、陌生的已登入者、
 * 已被移除的成員、只是受邀還沒接受的人）在非 ACTIVE 時仍是精簡版——GET 是公開端點，不新增任何對外暴露。
 *
 * <p>放寬的是<b>讀取</b>，不是寫入：非店主對非 ACTIVE 店鋪的 {@code PUT} 仍然不行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-TENANT-DETAILS: 非 ACTIVE 店鋪的詳情與更新（Sprint 315，DEF-432③）")
class TenantDetailsNonActiveIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final List<String> STATUSES =
            List.of("ACTIVE", "PENDING_REVIEW", "REJECTED", "SUSPENDED", "TERMINATED");
    private static final List<String> VIEWERS =
            List.of("anonymous", "outsider", "removedMember", "invitedMember", "staff", "owner");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant store;
    private final Map<String, String> tokens = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        long stamp = System.nanoTime();
        store = tenantRepository.save(Tenant.builder().name("DEF-432③ 店鋪 " + stamp).slug("def432-" + stamp)
                .description("店鋪描述 " + stamp).contactEmail("def432-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789").purchaseOrderApprovalThreshold(new BigDecimal("5000.00"))
                .status(Tenant.TenantStatus.ACTIVE).build());

        tokens.put("anonymous", null);
        tokens.put("outsider", token(user("outsider", stamp, User.UserRole.BUYER, null), "BUYER", SYSTEM_TENANT_ID));
        tokens.put("removedMember", token(member(user("removed", stamp, User.UserRole.STORE_STAFF, store.getId()),
                TenantMember.StoreRole.STORE_STAFF, TenantMember.MemberStatus.REMOVED), "STORE_STAFF", store.getId()));
        tokens.put("invitedMember", token(member(user("invited", stamp, User.UserRole.STORE_STAFF, store.getId()),
                TenantMember.StoreRole.STORE_STAFF, TenantMember.MemberStatus.INVITED), "STORE_STAFF", store.getId()));
        tokens.put("staff", token(member(user("staff", stamp, User.UserRole.STORE_STAFF, store.getId()),
                TenantMember.StoreRole.STORE_STAFF, TenantMember.MemberStatus.ACTIVE), "STORE_STAFF", store.getId()));
        tokens.put("owner", token(member(user("owner", stamp, User.UserRole.STORE_OWNER, store.getId()),
                TenantMember.StoreRole.STORE_OWNER, TenantMember.MemberStatus.ACTIVE), "STORE_OWNER", store.getId()));
    }

    // ── 詳情（GET）：狀態 x 觀看者 ───────────────────────────────

    @Test
    @DisplayName("GET /v2/tenants/{id}：ACTIVE 店鋪完整資料公開（門檻只有成員看得到）；非 ACTIVE 店鋪只有本店有效成員（店主、店員）看到完整資料與門檻，匿名、陌生人、已移除、僅受邀都仍是精簡版")
    void detail_visibilityByStatusAndViewer() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (String status : STATUSES) {
            setStatus(status);
            for (String viewer : VIEWERS) {
                String expected = expectedView(status, viewer);
                String actual = viewOf(viewer);
                if (!expected.equals(actual)) {
                    mismatches.add(status + " x " + viewer + "：預期 " + expected + "，實際 " + actual);
                }
            }
        }
        assertThat(mismatches).as("狀態 x 觀看者 的詳情可見度（共 %d 格）", STATUSES.size() * VIEWERS.size()).isEmpty();
    }

    @Test
    @DisplayName("非 ACTIVE 店鋪的完整資料帶出編輯表單要預填的欄位，且狀態照實回報（SUSPENDED）")
    void detail_suspendedStore_ownerSeesTheFieldsTheEditFormPrefills() throws Exception {
        setStatus("SUSPENDED");

        JsonNode data = detailOf("owner");

        assertThat(data.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(data.path("storeName").asText()).isEqualTo(store.getName());
        assertThat(data.path("slug").asText()).isEqualTo(store.getSlug());
        assertThat(data.path("storeDescription").asText()).isEqualTo(store.getDescription());
        assertThat(data.path("contactEmail").asText()).isEqualTo(store.getContactEmail());
        assertThat(data.path("contactPhone").asText()).isEqualTo("+886-123456789");
        assertThat(data.path("purchaseOrderApprovalThreshold").decimalValue()).isEqualByComparingTo("5000");
    }

    // ── 更新（PUT）：放寬的是讀取，不是寫入 ──────────────────────

    @Test
    @DisplayName("PUT /v2/tenants/{id}：店主在每一種非 ACTIVE 狀態都能改店鋪名稱（FRD 邊界條件）；店員、陌生人、已移除、僅受邀、匿名都不行，資料不變；請求帶 status=ACTIVE 也不能改變店鋪狀態（不能自行復權）")
    void update_nonActiveStore_onlyTheOwnerCanWrite() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (String status : STATUSES.subList(1, STATUSES.size())) {
            setStatus(status);
            for (String viewer : VIEWERS) {
                String before = nameInDb();
                String newName = "改名 " + status + " " + viewer;
                int httpStatus = putName(viewer, newName);
                boolean changed = newName.equals(nameInDb());
                boolean ownerCase = "owner".equals(viewer);
                // TenantUpdateRequest 有個選填的 status 欄位，putName 每次都帶 ACTIVE：後端不讀它，店鋪狀態只能走管理員端點
                if (!status.equals(statusInDb())) {
                    mismatches.add(status + " x " + viewer + "：PUT 之後店鋪狀態變成 " + statusInDb()
                            + "（改資料不可改變店鋪狀態，尤其不能自行復權）");
                }
                if (ownerCase && (httpStatus != 200 || !changed)) {
                    mismatches.add(status + " x owner：預期 200 且名稱已改，實際 HTTP " + httpStatus + "、已改=" + changed);
                }
                if (!ownerCase && (httpStatus < 400 || httpStatus >= 500 || changed)) {
                    mismatches.add(status + " x " + viewer + "：預期 4xx 且名稱不變，實際 HTTP " + httpStatus + "、已改=" + changed);
                }
                if (!ownerCase) {
                    assertThat(nameInDb()).as(status + " x " + viewer + " 之後名稱").isEqualTo(before);
                }
            }
        }
        assertThat(mismatches).as("非 ACTIVE 店鋪的 PUT 結果表").isEmpty();
    }

    // ── 輔助 ─────────────────────────────────────────────────

    /** 預期的可見度：ACTIVE 公開（門檻限成員）；非 ACTIVE 只有有效成員看得到完整資料。 */
    private static String expectedView(final String status, final String viewer) {
        boolean activeMember = "staff".equals(viewer) || "owner".equals(viewer);
        if ("ACTIVE".equals(status)) {
            return activeMember ? "full+threshold" : "full";
        }
        return activeMember ? "full+threshold" : "limited";
    }

    /** limited＝沒有聯絡信箱（精簡版）；full＝有聯絡信箱但沒有採購審批門檻；full+threshold＝兩者都有。 */
    private String viewOf(final String viewer) throws Exception {
        JsonNode data = detailOf(viewer);
        if (data.path("contactEmail").isMissingNode() || data.path("contactEmail").isNull()) {
            return "limited";
        }
        boolean hasThreshold = !data.path("purchaseOrderApprovalThreshold").isMissingNode()
                && !data.path("purchaseOrderApprovalThreshold").isNull();
        return hasThreshold ? "full+threshold" : "full";
    }

    private JsonNode detailOf(final String viewer) throws Exception {
        MockHttpServletRequestBuilder request = get("/v2/tenants/" + store.getId());
        MockHttpServletResponse response = send(request, tokens.get(viewer));
        assertThat(response.getStatus()).as("GET 詳情（%s）", viewer).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private int putName(final String viewer, final String newName) throws Exception {
        MockHttpServletRequestBuilder request = put("/v2/tenants/" + store.getId())
                .content(objectMapper.writeValueAsString(Map.of("storeName", newName, "status", "ACTIVE")));
        return send(request, tokens.get(viewer)).getStatus();
    }

    private MockHttpServletResponse send(final MockHttpServletRequestBuilder request, final String token) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private void setStatus(final String status) {
        jdbcTemplate.update("UPDATE tenants SET status = ? WHERE id = ?", status, store.getId());
    }

    private String nameInDb() {
        return jdbcTemplate.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, store.getId());
    }

    private String statusInDb() {
        return jdbcTemplate.queryForObject("SELECT status FROM tenants WHERE id = ?", String.class, store.getId());
    }

    private User user(final String label, final long stamp, final User.UserRole role, final UUID tenantId) {
        return userRepository.save(User.builder().email("def432-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy").fullName(label).role(role).status("ACTIVE").tenantId(tenantId).build());
    }

    private User member(final User user, final TenantMember.StoreRole storeRole, final TenantMember.MemberStatus status) {
        tenantMemberRepository.save(TenantMember.builder().tenantId(store.getId()).userId(user.getId())
                .storeRole(storeRole).status(status).build());
        return user;
    }

    private String token(final User user, final String role, final UUID tenantId) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), role, tenantId.toString());
    }
}
