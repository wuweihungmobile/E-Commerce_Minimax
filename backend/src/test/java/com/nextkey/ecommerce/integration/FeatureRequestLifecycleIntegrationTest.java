package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * 功能開關「申請」的生命週期（Sprint 311，DEF-429；真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>核准開店時 {@code AdminService.initializeFeatureToggles} 替六個功能都建預設列，三個需審核的
 * （{@code BOOKING_ENABLED}／{@code DYNAMIC_PRICING_ENABLED}／{@code PROMO_ENABLED}）預設 {@code is_enabled=false}。
 * 先前功能清單把「有列＋需審核＋未啟用」一律判成待審核（{@code PENDING}），所以全新店鋪沒申請過也顯示待審核——
 * 店主端、管理員頁的「N 項功能待審核」都是；兩邊都分不出「店主申請了」與「系統預設建了一列」。現在待審核只認
 * {@code requested_at}（店主送出申請時才寫入；核准、停用、撤回時清除）。
 *
 * <p>固件還原核准開店後的真實狀態：六個功能都有列、三個需審核的是 false、沒有申請時間。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-FEATURE-REQUEST: 功能開關申請的生命週期（Sprint 311，DEF-429）")
class FeatureRequestLifecycleIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final List<String> APPROVAL_FEATURES =
            List.of("BOOKING_ENABLED", "DYNAMIC_PRICING_ENABLED", "PROMO_ENABLED");
    private static final List<String> DEFAULT_ON_FEATURES = List.of("RETAIL_ENABLED", "CMS_ENABLED", "ERP_ENABLED");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant store;
    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        long stamp = System.nanoTime();
        store = tenantRepository.save(Tenant.builder().name("DEF-429 店鋪 " + stamp).slug("def429-" + stamp)
                .contactEmail("def429-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User owner = userRepository.save(User.builder().email("def429-owner-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("owner").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(store.getId()).build());
        tenantMemberRepository.save(TenantMember.builder().tenantId(store.getId()).userId(owner.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER).build());
        User admin = userRepository.save(User.builder().email("def429-admin-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("admin").role(User.UserRole.SUPER_ADMIN).status("ACTIVE").build());
        ownerToken = jwtTokenService.generateAccessToken(owner.getId(), owner.getEmail(), "STORE_OWNER",
                store.getId().toString());
        adminToken = jwtTokenService.generateAccessToken(admin.getId(), admin.getEmail(), "SUPER_ADMIN",
                SYSTEM_TENANT_ID.toString());
        seedDefaultRows();
    }

    /** 還原核准開店的預設列：六個功能都有列，三個免審核的開啟、三個需審核的關閉，沒有申請時間。 */
    private void seedDefaultRows() {
        for (String key : List.of("RETAIL_ENABLED", "BOOKING_ENABLED", "CMS_ENABLED", "ERP_ENABLED",
                "DYNAMIC_PRICING_ENABLED", "PROMO_ENABLED")) {
            boolean on = DEFAULT_ON_FEATURES.contains(key);
            // 測試庫的觸發器在建立租戶時已補了 DYNAMIC_PRICING_ENABLED 的列，其餘要自己補
            int updated = jdbcTemplate.update(
                    "UPDATE tenant_feature_toggles SET is_enabled = ?, requested_at = NULL WHERE tenant_id = ? AND feature_key = ?",
                    on, store.getId(), key);
            if (updated == 0) {
                jdbcTemplate.update("INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, "
                        + "created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, ?, NOW(), NOW())",
                        store.getId(), key, on);
            }
        }
    }

    // ── 測試 ─────────────────────────────────────────────────

    @Test
    @DisplayName("核准開店建的預設列：三個需審核的功能沒人申請過 → INACTIVE（不是待審核），沒有申請時間；店主端與管理員端一致，管理員的待審核筆數是 0")
    void defaultRows_areNotPending() throws Exception {
        Map<String, JsonNode> owner = ownerFeatures();
        Map<String, JsonNode> admin = adminFeatures();

        for (String key : APPROVAL_FEATURES) {
            assertThat(owner.get(key).path("status").asText()).as("店主端 " + key).isEqualTo("INACTIVE");
            assertThat(noValue(owner.get(key).path("requestedAt"))).as("店主端 " + key + " 沒申請過不該有申請時間").isTrue();
            assertThat(admin.get(key).path("status").asText()).as("管理員端 " + key).isEqualTo("INACTIVE");
        }
        for (String key : DEFAULT_ON_FEATURES) {
            assertThat(owner.get(key).path("status").asText()).as(key).isEqualTo("ACTIVE");
        }
        assertThat(pendingCount(admin)).as("管理員頁的『N 項功能待審核』= 需審核且 PENDING 的筆數").isZero();
    }

    @Test
    @DisplayName("店主送出申請 → 回應仍未啟用、列表變 PENDING 並帶申請時間（只有被申請的那一項）；重複申請保留最早的申請時間")
    void request_marksOnlyThatFeaturePending_andRepeatKeepsFirstTime() throws Exception {
        JsonNode response = ownerPut("PROMO_ENABLED", true);
        assertThat(response.path("data").path("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.path("data").path("newState").asBoolean()).isFalse();

        Map<String, JsonNode> owner = ownerFeatures();
        assertThat(owner.get("PROMO_ENABLED").path("status").asText()).isEqualTo("PENDING");
        assertThat(owner.get("PROMO_ENABLED").path("isEnabled").asBoolean()).isFalse();
        String firstRequestedAt = owner.get("PROMO_ENABLED").path("requestedAt").asText();
        assertThat(firstRequestedAt).as("申請時間（格式不假設，只要求有值）").isNotBlank().isNotEqualTo("null");
        assertThat(owner.get("BOOKING_ENABLED").path("status").asText()).as("沒被申請的另一項").isEqualTo("INACTIVE");
        assertThat(owner.get("DYNAMIC_PRICING_ENABLED").path("status").asText()).isEqualTo("INACTIVE");
        assertThat(pendingCount(adminFeatures())).as("管理員頁的待審核筆數").isEqualTo(1);

        ownerPut("PROMO_ENABLED", true);
        assertThat(ownerFeatures().get("PROMO_ENABLED").path("requestedAt").asText())
                .as("重複申請不重置等待時間").isEqualTo(firstRequestedAt);
    }

    @Test
    @DisplayName("管理員核准 → ACTIVE 且申請時間清掉；店主之後關閉是 INACTIVE（不會因殘留的舊申請時間又變成待審核）")
    void adminApprove_clearsRequestedAt_andLaterOwnerDisableIsInactive() throws Exception {
        ownerPut("PROMO_ENABLED", true);
        assertThat(requestedAtInDb("PROMO_ENABLED")).isNotNull();

        adminPut("PROMO_ENABLED", true);
        Map<String, JsonNode> afterApprove = ownerFeatures();
        assertThat(afterApprove.get("PROMO_ENABLED").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(noValue(afterApprove.get("PROMO_ENABLED").path("requestedAt"))).isTrue();
        assertThat(requestedAtInDb("PROMO_ENABLED")).as("資料庫的申請時間欄").isNull();

        JsonNode disable = ownerPut("PROMO_ENABLED", false);
        assertThat(disable.path("data").path("status").asText()).isEqualTo("DISABLED");
        Map<String, JsonNode> afterDisable = ownerFeatures();
        assertThat(afterDisable.get("PROMO_ENABLED").path("status").asText())
                .as("關閉已核准的功能 = 停用，不是又在待審核").isEqualTo("INACTIVE");
        assertThat(pendingCount(adminFeatures())).isZero();
    }

    @Test
    @DisplayName("管理員駁回（對待審核的功能送 enabled=false）→ 回到 INACTIVE、申請時間清掉；店主可以重新申請")
    void adminDecline_returnsToInactive_andOwnerCanRequestAgain() throws Exception {
        ownerPut("BOOKING_ENABLED", true);
        assertThat(ownerFeatures().get("BOOKING_ENABLED").path("status").asText()).isEqualTo("PENDING");

        adminPut("BOOKING_ENABLED", false);
        assertThat(ownerFeatures().get("BOOKING_ENABLED").path("status").asText()).isEqualTo("INACTIVE");
        assertThat(requestedAtInDb("BOOKING_ENABLED")).isNull();

        ownerPut("BOOKING_ENABLED", true);
        assertThat(ownerFeatures().get("BOOKING_ENABLED").path("status").asText()).as("重新申請").isEqualTo("PENDING");
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private JsonNode ownerPut(String feature, boolean enabled) throws Exception {
        return json(put("/v2/dashboard/tenants/features/" + feature).content("{\"enabled\":" + enabled + "}"), ownerToken);
    }

    private JsonNode adminPut(String feature, boolean enabled) throws Exception {
        return json(put("/v2/admin/tenants/" + store.getId() + "/features/" + feature)
                .content("{\"enabled\":" + enabled + "}"), adminToken);
    }

    private Map<String, JsonNode> ownerFeatures() throws Exception {
        return byKey(json(get("/v2/dashboard/tenants/features"), ownerToken));
    }

    private Map<String, JsonNode> adminFeatures() throws Exception {
        return byKey(json(get("/v2/admin/tenants/" + store.getId() + "/features"), adminToken));
    }

    private Map<String, JsonNode> byKey(JsonNode response) {
        Map<String, JsonNode> byKey = new HashMap<>();
        for (JsonNode f : response.path("data").path("features")) {
            byKey.put(f.path("featureKey").asText(), f);
        }
        return byKey;
    }

    /** JSON 欄位沒有值：被省略（MissingNode）或明寫 null。 */
    private static boolean noValue(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    /** 管理員頁的「N 項功能待審核」：requiresAdminReview 且 status = PENDING（見 admin/tenants/[id]/features/page.tsx）。 */
    private long pendingCount(Map<String, JsonNode> features) {
        return features.values().stream()
                .filter(f -> f.path("requiresAdminReview").asBoolean() && "PENDING".equals(f.path("status").asText()))
                .count();
    }

    private Object requestedAtInDb(String featureKey) {
        return jdbcTemplate.queryForObject(
                "SELECT requested_at FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Object.class, store.getId(), featureKey);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token)
            throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        var result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
