package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.response.ValidatableMockMvcResponse;

/**
 * 定價規則的「50 條上限」與「同類型期間重疊」檢核只看自己的房源或商品，且規則只能引用自己店鋪的房源與商品
 * （Sprint 307，DEF-417、DEF-423，真實 PostgreSQL，完整 HTTP＋JWT＋權限鏈）。
 *
 * <p>{@code PricingService.createRule} 的檢核原本一律用 {@code findByRoomListingIdAndIsActiveTrue(request.getRoomListingId())}。
 * 商品規則只帶 {@code listingId}、{@code roomListingId} 是 null，Spring Data JPA 的衍生查詢對 null 參數會產生
 * {@code room_listing_id IS NULL}，於是比對範圍變成「全平台所有租戶所有 {@code room_listing_id} 為 null 的啟用中規則」：
 * 上限變成全平台合計 50 條、別家店甚至別的商品的同類型規則都算「重疊」，帶 {@code confirmOverride=true} 時還會把它們軟刪除。
 *
 * <p>同一個方法的擁有權檢查是 {@code checkListingTenantOwnership(roomListingId ?: listingId)}：兩個 id 同時帶入時只驗證
 * 前者，後者（{@code listingId}）沒有任何驗證，規則就能掛到別家店的商品上。購物車與結帳（{@code getEffectivePrice}）
 * 以 {@code listingId} 取規則計算單價，所以這是 DEF-242 的另一條路徑。
 *
 * <p>既有測試都把 Repository 換成 mock（{@code PricingServiceTest}、{@code M12PricingIntegrationTest}），
 * 查詢實際回哪些列從未被執行過；本類別用兩間真實店鋪與真實資料庫量測。每個案例都帶「同一條路徑對自己仍照常運作」的對照
 * （同商品重疊仍擋下、覆蓋仍軟刪除舊規則、自己的兩個 id 仍可同時引用），避免守門變成「全部拒絕」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 307（DEF-417／423）: 定價規則的上限與重疊檢核以自己的房源或商品為範圍，規則只能引用自己店鋪的房源與商品")
class PricingRuleScopeIsolationE2ETest {

    private static final String RULES_URL = "/v2/dashboard/pricing/rules";
    private static final String PRICING_TOGGLE = "DYNAMIC_PRICING_ENABLED";
    private static final BigDecimal PRODUCT_BASE_PRICE = new BigDecimal("100.00");
    /** 規則期間的起點：夠遠，不會與其他測試類別留在共用測試庫裡的規則重疊。 */
    private static final LocalDate WINDOW = LocalDate.now().plusYears(4);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private Tenant storeA;
    private Tenant storeB;
    private User ownerA;
    private User ownerB;
    private String tokenA;
    private String tokenB;

    private UUID roomA1;
    private UUID roomA2;
    private UUID productA1;
    private UUID productA2;
    private UUID productB;

    @BeforeAll
    void seedStores() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        long stamp = System.currentTimeMillis();

        storeA = seedStore("A", stamp);
        storeB = seedStore("B", stamp);
        ownerA = seedOwner(storeA, "A", stamp);
        ownerB = seedOwner(storeB, "B", stamp);
        tokenA = ownerToken(ownerA, storeA);
        tokenB = ownerToken(ownerB, storeB);
    }

    @BeforeEach
    void seedListings() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
        roomA1 = seedListing(storeA, ownerA, Listing.ListingType.ROOM, "房源 A1", new BigDecimal("1500.00"));
        roomA2 = seedListing(storeA, ownerA, Listing.ListingType.ROOM, "房源 A2", new BigDecimal("1500.00"));
        productA1 = seedListing(storeA, ownerA, Listing.ListingType.PRODUCT, "商品 A1", PRODUCT_BASE_PRICE);
        productA2 = seedListing(storeA, ownerA, Listing.ListingType.PRODUCT, "商品 A2", PRODUCT_BASE_PRICE);
        productB = seedListing(storeB, ownerB, Listing.ListingType.PRODUCT, "商品 B", PRODUCT_BASE_PRICE);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        pricingRuleRepository.deleteAll(pricingRuleRepository.findByTenantId(storeA.getId()));
        pricingRuleRepository.deleteAll(pricingRuleRepository.findByTenantId(storeB.getId()));
        listingRepository.deleteAllById(List.of(roomA1, roomA2, productA1, productA2, productB));
    }

    // ── DEF-417：檢核範圍 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("兩間店鋪各自在自己的商品上建立同類型、期間重疊的規則：兩邊都 201，彼此的規則都維持啟用")
    void differentStoresCanHoldOverlappingRulesOfTheSameType() throws Exception {
        UUID ruleA = createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));

        UUID ruleB = createRule(tokenB, seasonal(null, productB, WINDOW.plusDays(2), WINDOW.plusDays(8)));

        assertThat(isActive(ruleA)).as("A 店的規則不受 B 店新增影響").isTrue();
        assertThat(isActive(ruleB)).as("B 店的規則啟用").isTrue();
    }

    @Test
    @DisplayName("別家店帶 confirmOverride=true 也停用不了我的規則（跨租戶寫入）")
    void otherStoreOverrideNeverDeactivatesMyRule() throws Exception {
        UUID ruleA = createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));

        createRule(tokenB, withOverride(seasonal(null, productB, WINDOW.plusDays(2), WINDOW.plusDays(8))));

        assertThat(isActive(ruleA)).as("B 店的覆蓋確認只作用在 B 店自己的商品，A 店的規則仍啟用").isTrue();
    }

    @Test
    @DisplayName("同一家店的兩個商品各有一條同類型、期間重疊的規則：互不衝突")
    void productsOfTheSameStoreHaveIndependentRules() throws Exception {
        UUID rule1 = createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));

        UUID rule2 = createRule(tokenA, seasonal(null, productA2, WINDOW.plusDays(2), WINDOW.plusDays(8)));

        assertThat(isActive(rule1)).isTrue();
        assertThat(isActive(rule2)).isTrue();
    }

    @Test
    @DisplayName("同一家店對商品 A2 帶 confirmOverride=true，不會停用商品 A1 的規則")
    void overrideOnOneProductKeepsTheRulesOfAnotherProductOfTheSameStore() throws Exception {
        UUID rule1 = createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));

        createRule(tokenA, withOverride(seasonal(null, productA2, WINDOW.plusDays(2), WINDOW.plusDays(8))));

        assertThat(isActive(rule1)).as("覆蓋只作用在同一個商品").isTrue();
    }

    @Test
    @DisplayName("對照：同一個商品上期間重疊的同類型規則仍被擋下，確認覆蓋才軟刪除重疊的舊規則，不重疊的保留")
    void sameProductConflictIsStillDetectedAndOverrideStillWorks() throws Exception {
        UUID overlapping = createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));
        UUID separate = createRule(tokenA, seasonal(null, productA1, WINDOW.plusDays(30), WINDOW.plusDays(40)));

        postRule(tokenA, seasonal(null, productA1, WINDOW.plusDays(2), WINDOW.plusDays(8)))
                .statusCode(400).body("code", equalTo("E-4001"));
        assertThat(isActive(overlapping)).as("被拒絕時不動舊規則").isTrue();

        UUID replacement = createRule(tokenA,
                withOverride(seasonal(null, productA1, WINDOW.plusDays(2), WINDOW.plusDays(8))));

        assertThat(isActive(overlapping)).as("確認覆蓋：重疊的舊規則被軟刪除").isFalse();
        assertThat(isActive(separate)).as("期間不重疊的同類型規則保留").isTrue();
        assertThat(isActive(replacement)).isTrue();

        // 已停用的規則不再算數：這一段只和已被軟刪除的舊規則重疊，不和啟用中的 replacement／separate 重疊
        createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(1)));
    }

    @Test
    @DisplayName("對照：房源（ROOM）規則以房源為範圍——同店不同房源互不衝突，同一房源重疊仍擋下")
    void roomRulesAreScopedToTheRoom() throws Exception {
        createRule(tokenA, seasonal(roomA1, null, WINDOW, WINDOW.plusDays(10)));

        createRule(tokenA, seasonal(roomA2, null, WINDOW.plusDays(2), WINDOW.plusDays(8)));

        postRule(tokenA, seasonal(roomA1, null, WINDOW.plusDays(4), WINDOW.plusDays(6)))
                .statusCode(400).body("code", equalTo("E-4001"));
    }

    @Test
    @DisplayName("50 條上限以商品為單位：商品 A1 滿 50 條仍擋下 A1，但不影響商品 A2 與別家店的商品")
    void ruleLimitIsCountedPerProduct() throws Exception {
        seedActiveRules(storeA, productA1, 50);

        postRule(tokenA, seasonal(null, productA1, WINDOW.plusDays(300), WINDOW.plusDays(301)))
                .statusCode(400).body("code", equalTo("E-4001"));
        createRule(tokenA, seasonal(null, productA2, WINDOW, WINDOW.plusDays(10)));
        createRule(tokenB, seasonal(null, productB, WINDOW, WINDOW.plusDays(10)));
    }

    @Test
    @DisplayName("不綁房源也不綁商品的規則只在自己的店鋪內比較：兩家店各一條期間重疊的規則都 201，同一家店重疊才擋下")
    void unboundRulesAreComparedWithinMyStoreOnly() throws Exception {
        createRule(tokenA, seasonal(null, null, WINDOW, WINDOW.plusDays(10)));

        createRule(tokenB, seasonal(null, null, WINDOW.plusDays(2), WINDOW.plusDays(8)));

        postRule(tokenA, seasonal(null, null, WINDOW.plusDays(4), WINDOW.plusDays(6)))
                .statusCode(400).body("code", equalTo("E-4001"));
    }

    @Test
    @DisplayName("不綁房源也不綁商品的規則只和「同一家店、同樣不綁、啟用中」的規則比較：房源規則、商品規則、已停用的規則都不算重疊")
    void unboundRulesIgnoreRoomProductAndInactiveRules() throws Exception {
        createRule(tokenA, seasonal(roomA1, null, WINDOW, WINDOW.plusDays(10)));
        createRule(tokenA, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)));
        pricingRuleRepository.save(PricingRule.builder()
                .tenant(storeA)
                .ruleType(PricingRule.PricingRuleType.SEASONAL)
                .ruleName("S307 已停用的不綁規則")
                .config(Map.of("multiplier", 1.1))
                .validFrom(WINDOW)
                .validTo(WINDOW.plusDays(10))
                .isActive(false)
                .build());

        createRule(tokenA, seasonal(null, null, WINDOW.plusDays(2), WINDOW.plusDays(8)));
    }

    // ── DEF-423：引用的房源與商品都要是自己的 ─────────────────────────────────

    @Test
    @DisplayName("帶自己的房源 id、同時夾帶別家店的商品 id：403，別家商品沒有被掛上規則、有效售價不變")
    void ruleCannotTargetAnotherStoresProductAlongsideMyOwnRoom() throws Exception {
        LocalDate today = LocalDate.now();
        Map<String, Object> smuggled = rule(roomA1, productB, "MANUAL_OVERRIDE", "S307 夾帶",
                Map.of("price", 1), today, today.plusDays(30));

        int status = postRule(tokenA, smuggled).extract().statusCode();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(status).as("夾帶別家店商品 id 的建立請求").isEqualTo(403);
            softly.assertThat(pricingRuleRepository.findByListingId(productB))
                    .as("沒有任何規則掛在別家店的商品上").isEmpty();
            softly.assertThat(effectivePrice(tokenB, productB, today))
                    .as("別家店商品的有效售價（購物車與結帳採用的單價）不變").isEqualByComparingTo(PRODUCT_BASE_PRICE);
        });
    }

    @Test
    @DisplayName("對照：同時帶自己的房源 id 與自己的商品 id 仍是 201（守門檢查的是擁有權，不是禁止兩個 id 並存）")
    void ruleMayReferenceBothOfMyOwnListings() throws Exception {
        createRule(tokenA, seasonal(roomA1, productA1, WINDOW, WINDOW.plusDays(10)));
    }

    @Test
    @DisplayName("對照（DEF-242 原路徑）：只帶別家店的商品 id → 403，沒有規則被掛上去")
    void ruleCannotTargetAnotherStoresProduct() throws Exception {
        postRule(tokenB, seasonal(null, productA1, WINDOW, WINDOW.plusDays(10)))
                .statusCode(403).body("code", equalTo("E-1007"));

        assertThat(pricingRuleRepository.findByListingId(productA1)).as("沒有任何規則掛在別家店的商品上").isEmpty();
    }

    @Test
    @DisplayName("對照（DEF-242 原路徑）：只帶別家店的房源 id → 403，沒有規則被掛上去")
    void ruleCannotTargetAnotherStoresRoom() throws Exception {
        postRule(tokenB, seasonal(roomA1, null, WINDOW, WINDOW.plusDays(10)))
                .statusCode(403).body("code", equalTo("E-1007"));

        assertThat(pricingRuleRepository.findByRoomListingId(roomA1)).as("沒有任何規則掛在別家店的房源上").isEmpty();
    }

    // ── 固件與輔助 ────────────────────────────────────────────────────────────

    private Tenant seedStore(String label, long stamp) {
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S307 定價範圍測試店 " + label + " " + stamp)
                .slug("s307-pricing-scope-" + label.toLowerCase() + "-" + stamp)
                .contactEmail("s307-store-" + label.toLowerCase() + "-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        // 新店鋪的動態定價預設關閉（測試庫的觸發器補預設開關）；此店鋪是本類別專用，不必還原
        int toggled = jdbcTemplate.update(
                "UPDATE tenant_feature_toggles SET is_enabled = TRUE WHERE tenant_id = ? AND feature_key = ?",
                store.getId(), PRICING_TOGGLE);
        assertThat(toggled).as("新店鋪應已有動態定價開關列").isEqualTo(1);
        return store;
    }

    private User seedOwner(Tenant store, String label, long stamp) {
        return userRepository.save(User.builder()
                .email("s307-owner-" + label.toLowerCase() + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S307 店主 " + label)
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
    }

    private String ownerToken(User owner, Tenant store) {
        return jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", store.getId().toString());
    }

    private UUID seedListing(Tenant store, User owner, Listing.ListingType type, String title, BigDecimal basePrice) {
        return listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(type).title("S307 " + title)
                .basePrice(basePrice).status(Listing.ListingStatus.ACTIVE).build()).getId();
    }

    /** 直接寫入 {@code count} 條期間互不重疊、啟用中的商品規則（不走建立端點，避免被重疊檢核擋下）。 */
    private void seedActiveRules(Tenant store, UUID listingId, int count) {
        List<PricingRule> rules = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rules.add(PricingRule.builder()
                    .tenant(store)
                    .listingId(listingId)
                    .ruleType(PricingRule.PricingRuleType.SEASONAL)
                    .ruleName("S307 上限填充 " + i)
                    .config(Map.of("multiplier", 1.1))
                    .validFrom(WINDOW.plusDays(i * 2L))
                    .validTo(WINDOW.plusDays(i * 2L + 1))
                    .isActive(true)
                    .build());
        }
        pricingRuleRepository.saveAll(rules);
    }

    private Map<String, Object> rule(UUID roomListingId, UUID listingId, String ruleType, String ruleName,
            Map<String, Object> config, LocalDate validFrom, LocalDate validTo) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (roomListingId != null) {
            body.put("roomListingId", roomListingId.toString());
        }
        if (listingId != null) {
            body.put("listingId", listingId.toString());
        }
        body.put("ruleType", ruleType);
        body.put("ruleName", ruleName);
        body.put("config", config);
        body.put("validFrom", validFrom.toString());
        body.put("validTo", validTo.toString());
        return body;
    }

    private Map<String, Object> seasonal(UUID roomListingId, UUID listingId, LocalDate validFrom, LocalDate validTo) {
        return rule(roomListingId, listingId, "SEASONAL", "S307 旺季", Map.of("multiplier", 1.2), validFrom, validTo);
    }

    private Map<String, Object> withOverride(Map<String, Object> body) {
        body.put("confirmOverride", true);
        return body;
    }

    private ValidatableMockMvcResponse postRule(String token, Map<String, Object> body) throws Exception {
        return given().header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(body))
                .when().post(RULES_URL)
                .then();
    }

    private UUID createRule(String token, Map<String, Object> body) throws Exception {
        String created = postRule(token, body).statusCode(201).extract().asString();
        return UUID.fromString(objectMapper.readTree(created).path("data").path("ruleId").asText());
    }

    private boolean isActive(UUID ruleId) {
        return Boolean.TRUE.equals(pricingRuleRepository.findById(ruleId).orElseThrow().getIsActive());
    }

    private BigDecimal effectivePrice(String token, UUID listingId, LocalDate checkDate) {
        try {
            String response = given().header("Authorization", "Bearer " + token)
                    .when().get("/v2/listings/" + listingId + "/effective-price?checkDate=" + checkDate)
                    .then().statusCode(200).extract().asString();
            return objectMapper.readTree(response).path("data").path("effectivePrice").decimalValue();
        } catch (Exception e) {
            throw new IllegalStateException("查詢有效售價失敗", e);
        }
    }
}
