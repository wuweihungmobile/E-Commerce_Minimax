package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * 一列壞掉的定價規則不得拖垮別人的規則列表（Sprint 306，DEF-421；Flyway 建出的資料庫＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>背景（DEF-416）：{@code PricingService.getRules} 曾用 {@code findAll()} 全表掃描再於記憶體過濾，資料庫裡任何一列
 * （包含別的店鋪的）{@code rule_type} 不在 Java 列舉 {@code PricingRuleType} 內，載入時就丟
 * {@code No enum constant}，整個請求變成 HTTP 500。當年的觸發條件是 {@code V5} 種在每個 Flyway 資料庫的一列
 * {@code WEEKEND_SURCHARGE}；Sprint 305 的 {@code V94} 把那一列刪了，原本靠它才會變紅的 E2E 案例
 * （{@code at-pricing-rules-real}）因此失去觸發條件，退回 {@code findAll()} 也不會再被抓到。
 *
 * <p>本類別把「壞資料列」變成測試自己種的固件，不再依賴遷移留下的資料：在一個 Flyway 實跑 V1 到最新版的獨立資料庫
 * （{@code ddl-auto=validate}）裡，先替一間店鋪種好合法的規則，再替另一間店鋪直接以 SQL 寫入一列列舉外的 {@code rule_type}
 * ——Flyway 建的 {@code pricing_rules.rule_type} 只是 {@code VARCHAR(20)}、沒有 CHECK 約束，寫得進去；Hibernate 產生的
 * 一般整合測試資料庫對列舉欄位有 CHECK，放不進這種值（見 {@code flyway-db-vs-hibernate-test-db-differences}），所以不能用它。
 * 然後以店主身分走真實 HTTP，三種列表查詢都必須照常回自己的規則。
 *
 * <p>第一個案例先斷言固件真的會咬人（{@code findAll()} 讀不出來）：若將來列舉映射改成能容忍未知值，這支測試會在這裡紅，
 * 提醒護欄已經失去觸發條件，而不是靜靜變成永遠通過的測試。
 *
 * <p>資料庫 {@code s306_pricing_corrupt_row} 每次執行都先砍掉重建，執行完留在測試用 PostgreSQL 裡（Spring 的 context 快取
 * 仍持有它的連線，無法在類別結束時刪除）；測試用資料庫容器停止時一併消失。需要 {@code make test-db-up}。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 306（DEF-421）: 別店鋪一列壞掉的定價規則不得讓店主的規則列表變成 500（Flyway 資料庫）")
class PricingRulesCorruptRowFlywayIntegrationTest {

    private static final String BASE_URL =
            System.getProperty("it.db.base-url", "jdbc:postgresql://localhost:5432/");
    private static final String MAINTENANCE_DB = System.getProperty("it.db.name", "nextkeytest");
    private static final String DB_USER = System.getProperty("it.db.user", "koala");
    private static final String DB_PASS = System.getProperty("it.db.pass", "koala5");
    private static final String FLYWAY_DB = "s306_pricing_corrupt_row";

    private static final String RULES_URL = "/v2/dashboard/pricing/rules";
    /** 不在 {@code PricingRuleType} 內的規則類型（V5 當年種下的值）。 */
    private static final String UNKNOWN_RULE_TYPE = "WEEKEND_SURCHARGE";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private String ownerToken;
    private UUID roomListingId;
    private UUID productListingId;
    private UUID roomRuleId;
    private UUID productRuleId;

    /** 在 Spring 載入 context 之前建好空的資料庫，讓 Flyway 在 context 啟動時從 V1 實跑到最新版。 */
    @DynamicPropertySource
    static void useFreshFlywayDatabase(final DynamicPropertyRegistry registry) throws SQLException {
        try (Connection c = DriverManager.getConnection(BASE_URL + MAINTENANCE_DB, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + FLYWAY_DB + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + FLYWAY_DB);
        }
        registry.add("spring.datasource.url", () -> BASE_URL + FLYWAY_DB);
    }

    @BeforeAll
    void seedFixtures() {
        long stamp = System.currentTimeMillis();

        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S306 規則列表測試店 " + stamp)
                .slug("s306-rules-list-" + stamp)
                .contactEmail("s306-rules-list-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .email("s306-rules-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S306 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
        ownerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", store.getId().toString());

        roomListingId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.ROOM).title("S306 規則列表測試房源")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        productListingId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.PRODUCT).title("S306 規則列表測試商品")
                .basePrice(new BigDecimal("300.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        roomRuleId = pricingRuleRepository.save(validRule(store).roomListingId(roomListingId).build()).getId();
        productRuleId = pricingRuleRepository.save(validRule(store).listingId(productListingId).build()).getId();

        // 另一間店鋪的一列壞資料：直接以 SQL 寫入列舉外的 rule_type（Flyway 資料庫沒有 CHECK 約束）
        Tenant otherStore = tenantRepository.save(Tenant.builder()
                .name("S306 另一間店 " + stamp)
                .slug("s306-rules-other-" + stamp)
                .contactEmail("s306-rules-other-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        jdbcTemplate.update("INSERT INTO pricing_rules (tenant_id, rule_type, rule_name, config, valid_from, valid_to) "
                + "VALUES (?, ?, 'S306 壞資料列', '{}'::jsonb, CURRENT_DATE, CURRENT_DATE + 365)",
                otherStore.getId(), UNKNOWN_RULE_TYPE);
    }

    private PricingRule.PricingRuleBuilder validRule(final Tenant store) {
        return PricingRule.builder()
                .tenant(store)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("S306 合法規則")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true);
    }

    @Test
    @DisplayName("固件會咬人：這個資料庫裡 findAll() 載入全表就丟列舉錯誤——下面三個案例才有意義（失去觸發條件時這裡先紅）")
    void fixture_corruptRowMakesFullTableScanFail() {
        assertThatThrownBy(() -> pricingRuleRepository.findAll())
                .as("若 findAll() 讀得出來，壞資料列就不再能抓到『退回全表掃描』的回歸，這支測試要改用新的觸發條件")
                .hasStackTraceContaining(UNKNOWN_RULE_TYPE);
    }

    @Test
    @DisplayName("依房源查詢（預設與 activeOnly）：照常只回這間房源的規則，不被別店鋪的壞資料列拖垮")
    void listByRoomListing_ignoresCorruptRowOfAnotherStore() throws Exception {
        assertThat(listRuleIds("?roomListingId=" + roomListingId)).containsExactly(roomRuleId);
        assertThat(listRuleIds("?roomListingId=" + roomListingId + "&activeOnly=true")).containsExactly(roomRuleId);
    }

    @Test
    @DisplayName("依商品查詢（預設與 activeOnly）：照常只回這個商品的規則")
    void listByListing_ignoresCorruptRowOfAnotherStore() throws Exception {
        assertThat(listRuleIds("?listingId=" + productListingId)).containsExactly(productRuleId);
        assertThat(listRuleIds("?listingId=" + productListingId + "&activeOnly=true")).containsExactly(productRuleId);
    }

    @Test
    @DisplayName("不帶篩選（店鋪層級）：照常回店鋪自己的全部規則")
    void listForTenant_ignoresCorruptRowOfAnotherStore() throws Exception {
        assertThat(listRuleIds("")).containsExactlyInAnyOrder(roomRuleId, productRuleId);
        assertThat(listRuleIds("?activeOnly=true")).containsExactlyInAnyOrder(roomRuleId, productRuleId);
    }

    /** 以店主身分走真實 HTTP 列出規則：必須是 200，回傳規則的 id 清單。 */
    private List<UUID> listRuleIds(final String query) throws Exception {
        String body = mockMvc.perform(get(RULES_URL + query).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<UUID> ids = new ArrayList<>();
        for (JsonNode rule : objectMapper.readTree(body).path("data")) {
            ids.add(UUID.fromString(rule.path("ruleId").asText()));
        }
        return ids;
    }
}
