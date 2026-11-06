package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * FAQ 與知識庫的 slug 是「每間店鋪唯一」，不是「全平台唯一」（Sprint 330，DEF-460；Flyway 建出的資料庫＋完整
 * HTTP／JWT／權限鏈）。
 *
 * <p>背景：{@code V26}／{@code V27} 建 {@code faq_categories}／{@code faq_articles} 時，slug 的 {@code UNIQUE} 沒有命名；
 * {@code V29} 想把它改成每租戶唯一，卻用猜的名字 {@code DROP CONSTRAINT IF EXISTS uk_faq_*_slug}，名字不存在，
 * {@code IF EXISTS} 讓它靜默略過，原本「全平台唯一」的約束因此留了下來。結果：店鋪 B 想用店鋪 A 用過的 slug 建立
 * FAQ 分類或文章，服務層的 {@code existsByTenantIdAndSlug}（只看自己的店鋪）放行，資料庫的全域約束卻擋下，
 * 例外沒有業務錯誤對應，回 {@code 500 E-9900}。
 *
 * <p>為什麼只能用 Flyway 庫：Hibernate 產生的一般整合測試庫由實體註解建表（{@code unique = true}），與 Flyway
 * 庫的約束不同（見記憶 {@code flyway-db-vs-hibernate-test-db-differences}）；正式環境的 schema 來自 Flyway，
 * 真正該守住的是遷移跑完後的結果。做法與 {@code PlatformStatsCorruptRowFlywayIntegrationTest} 相同：每次先砍掉
 * 重建獨立資料庫，讓 Flyway 在 context 啟動時從 V1 實跑到最新版。
 *
 * <p>知識庫分類與文章是對照組：{@code V23} 的約束有命名所以 {@code V29} 刪得掉，{@code V24} 從建表就是每租戶唯一。
 *
 * <p>資料庫 {@code s330_faq_knowledge_slug_per_tenant} 執行完留在測試用 PostgreSQL 裡（Spring 的 context
 * 快取仍持有它的連線），測試用資料庫容器停止時一併消失。需要 {@code make test-db-up}。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 330（DEF-460）: FAQ／知識庫的 slug 每間店鋪各自唯一（Flyway 資料庫）")
class FaqKnowledgeSlugPerTenantFlywayIntegrationTest {

    private static final String BASE_URL =
            System.getProperty("it.db.base-url", "jdbc:postgresql://localhost:5432/");
    private static final String MAINTENANCE_DB = System.getProperty("it.db.name", "nextkeytest");
    private static final String DB_USER = System.getProperty("it.db.user", "koala");
    private static final String DB_PASS = System.getProperty("it.db.pass", "koala5");
    private static final String FLYWAY_DB = "s330_faq_knowledge_slug_per_tenant";

    private static final String FAQ_CATEGORIES_URL = "/v2/faqs/categories";
    private static final String FAQ_ARTICLES_URL = "/v2/faqs";
    private static final String KNOWLEDGE_CATEGORIES_URL = "/v2/knowledge/categories";
    private static final String KNOWLEDGE_ARTICLES_URL = "/v2/knowledge";

    /** slug 屬於店鋪內容的四張表：slug 只該在同一間店鋪內唯一。 */
    private static final List<String> TENANT_SCOPED_SLUG_TABLES = List.of(
            "faq_categories", "faq_articles", "knowledge_categories", "knowledge_articles");

    /** 取出 {@code pg_get_indexdef} 結果裡索引欄位的括號內容，例如 {@code (tenant_id, slug)}。 */
    private static final Pattern INDEX_COLUMNS = Pattern.compile("USING \\w+ \\(([^)]*)\\)");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private String storeAOwnerToken;
    private String storeBOwnerToken;
    private long stamp;

    /** 一次 HTTP 呼叫的結果；{@code id} 是回應 {@code data.id}（建立失敗時為空字串）。 */
    private record Reply(int status, String code, String id, String body) {
        @Override
        public String toString() {
            return "HTTP " + status + " code=" + code + " body=" + body;
        }
    }

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
    void seedTwoStoresWithOwners() {
        stamp = System.nanoTime();
        storeAOwnerToken = seedStoreOwnerToken("a");
        storeBOwnerToken = seedStoreOwnerToken("b");
    }

    private String seedStoreOwnerToken(final String label) {
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S330 slug 測試店 " + label + " " + stamp)
                .slug("s330-slug-" + label + "-" + stamp)
                .contactEmail("s330-slug-" + label + "-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .email("s330-slug-owner-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S330 店主 " + label)
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
        return jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", store.getId().toString());
    }

    // ------------------------------------------------------------------
    // 資料庫實際的唯一約束
    // ------------------------------------------------------------------

    @Test
    @DisplayName("K0 約束：四張內容表都只有（tenant_id, slug）的唯一索引，沒有任何單獨 slug 的全域唯一索引")
    void uniqueIndexes_onTenantScopedContentTables_areNeverGlobalOnSlug() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.relname AS tbl, i.relname AS idx, pg_get_indexdef(x.indexrelid) AS def "
                        + "FROM pg_index x "
                        + "JOIN pg_class c ON c.oid = x.indrelid "
                        + "JOIN pg_class i ON i.oid = x.indexrelid "
                        + "WHERE x.indisunique AND NOT x.indisprimary "
                        + "AND c.relname IN ('faq_categories', 'faq_articles', "
                        + "'knowledge_categories', 'knowledge_articles') "
                        + "ORDER BY c.relname, i.relname");

        List<String> actual = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (String table : TENANT_SCOPED_SLUG_TABLES) {
            List<String> columnLists = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (!table.equals(row.get("tbl"))) {
                    continue;
                }
                Matcher m = INDEX_COLUMNS.matcher(String.valueOf(row.get("def")));
                String columns = m.find() ? m.group(1).replace(" ", "") : "?";
                columnLists.add(columns);
                actual.add(table + "." + row.get("idx") + "(" + columns + ")");
                if ("slug".equals(columns)) {
                    violations.add(table + " 有全域唯一的 slug 索引 " + row.get("idx"));
                }
            }
            if (!columnLists.contains("tenant_id,slug")) {
                violations.add(table + " 缺少（tenant_id, slug）唯一索引");
            }
        }
        assertThat(violations).as("實際的唯一索引：%s", actual).isEmpty();
    }

    // ------------------------------------------------------------------
    // FAQ：跨店鋪同 slug 應該都能建立；同店鋪重複才被擋下
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F1 FAQ 分類：店鋪 A、B 各用同一個 slug 都能建立，各自一列")
    void faqCategory_sameSlugInTwoStores_bothCreated() throws Exception {
        String slug = "f1-" + stamp;

        Reply storeA = postJson(FAQ_CATEGORIES_URL,storeAOwnerToken, Map.of("name", "運送說明", "slug", slug));
        Reply storeB = postJson(FAQ_CATEGORIES_URL,storeBOwnerToken, Map.of("name", "運送說明", "slug", slug));

        assertThat(storeA.status()).as("店鋪 A 先建立（%s）", storeA).isEqualTo(201);
        assertThat(storeB.status()).as("店鋪 B 用同一個 slug 建立，別的店鋪用過不該影響本店（%s）", storeB).isEqualTo(201);
        assertThat(countRows("faq_categories", slug)).as("兩間店鋪各一列").isEqualTo(2);
    }

    @Test
    @DisplayName("F2 FAQ 文章：店鋪 A、B 各用同一個 slug 都能建立，各自一列")
    void faqArticle_sameSlugInTwoStores_bothCreated() throws Exception {
        Reply categoryA = postJson(FAQ_CATEGORIES_URL,storeAOwnerToken,
                Map.of("name", "分類 A", "slug", "f2-cat-a-" + stamp));
        Reply categoryB = postJson(FAQ_CATEGORIES_URL,storeBOwnerToken,
                Map.of("name", "分類 B", "slug", "f2-cat-b-" + stamp));
        assertThat(categoryA.status()).as("前置：店鋪 A 的分類（%s）", categoryA).isEqualTo(201);
        assertThat(categoryB.status()).as("前置：店鋪 B 的分類（%s）", categoryB).isEqualTo(201);
        String slug = "f2-article-" + stamp;

        Reply storeA = postJson(FAQ_ARTICLES_URL,storeAOwnerToken, Map.of(
                "categoryId", categoryA.id(), "question", "怎麼退貨？", "answer", "七天內可退。", "slug", slug));
        Reply storeB = postJson(FAQ_ARTICLES_URL,storeBOwnerToken, Map.of(
                "categoryId", categoryB.id(), "question", "怎麼退貨？", "answer", "七天內可退。", "slug", slug));

        assertThat(storeA.status()).as("店鋪 A 先建立（%s）", storeA).isEqualTo(201);
        assertThat(storeB.status()).as("店鋪 B 用同一個 slug 建立，別的店鋪用過不該影響本店（%s）", storeB).isEqualTo(201);
        assertThat(countRows("faq_articles", slug)).as("兩間店鋪各一列").isEqualTo(2);
    }

    @Test
    @DisplayName("F3 FAQ 分類：同一間店鋪重複用同一個 slug 仍被擋下（4xx），資料庫只有一列")
    void faqCategory_sameSlugTwiceInOneStore_isRejected() throws Exception {
        String slug = "f3-" + stamp;

        Reply first = postJson(FAQ_CATEGORIES_URL,storeAOwnerToken, Map.of("name", "第一次", "slug", slug));
        Reply second = postJson(FAQ_CATEGORIES_URL,storeAOwnerToken, Map.of("name", "第二次", "slug", slug));

        assertThat(first.status()).as("第一次建立（%s）", first).isEqualTo(201);
        assertThat(second.status()).as("同店鋪重複建立必須是 4xx，不能是 5xx（%s）", second).isBetween(400, 499);
        assertThat(countRows("faq_categories", slug)).as("重複的那次沒有寫入").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 知識庫（對照組）：V29 刪得掉 V23 有命名的約束，V24 從建表就是每租戶唯一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("K1 對照組 知識庫分類：店鋪 A、B 各用同一個 slug 都能建立")
    void knowledgeCategory_sameSlugInTwoStores_bothCreated() throws Exception {
        String slug = "k1-" + stamp;

        Reply storeA = postJson(KNOWLEDGE_CATEGORIES_URL,storeAOwnerToken, Map.of("name", "操作手冊", "slug", slug));
        Reply storeB = postJson(KNOWLEDGE_CATEGORIES_URL,storeBOwnerToken, Map.of("name", "操作手冊", "slug", slug));

        assertThat(storeA.status()).as("店鋪 A（%s）", storeA).isEqualTo(201);
        assertThat(storeB.status()).as("店鋪 B（%s）", storeB).isEqualTo(201);
        assertThat(countRows("knowledge_categories", slug)).isEqualTo(2);
    }

    @Test
    @DisplayName("K2 對照組 知識庫文章：店鋪 A、B 各用同一個 slug 都能建立")
    void knowledgeArticle_sameSlugInTwoStores_bothCreated() throws Exception {
        Reply categoryA = postJson(KNOWLEDGE_CATEGORIES_URL,storeAOwnerToken,
                Map.of("name", "分類 A", "slug", "k2-cat-a-" + stamp));
        Reply categoryB = postJson(KNOWLEDGE_CATEGORIES_URL,storeBOwnerToken,
                Map.of("name", "分類 B", "slug", "k2-cat-b-" + stamp));
        assertThat(categoryA.status()).as("前置：店鋪 A 的分類（%s）", categoryA).isEqualTo(201);
        assertThat(categoryB.status()).as("前置：店鋪 B 的分類（%s）", categoryB).isEqualTo(201);
        String slug = "k2-article-" + stamp;

        Reply storeA = postJson(KNOWLEDGE_ARTICLES_URL,storeAOwnerToken, Map.of(
                "categoryId", categoryA.id(), "title", "上架教學", "slug", slug, "content", "內容"));
        Reply storeB = postJson(KNOWLEDGE_ARTICLES_URL,storeBOwnerToken, Map.of(
                "categoryId", categoryB.id(), "title", "上架教學", "slug", slug, "content", "內容"));

        assertThat(storeA.status()).as("店鋪 A（%s）", storeA).isEqualTo(201);
        assertThat(storeB.status()).as("店鋪 B（%s）", storeB).isEqualTo(201);
        assertThat(countRows("knowledge_articles", slug)).isEqualTo(2);
    }

    // ------------------------------------------------------------------

    private Reply postJson(final String url, final String token, final Map<String, ?> payload) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        return new Reply(response.getStatus(), json.path("code").asText(""), json.path("data").path("id").asText(""), body);
    }

    private int countRows(final String table, final String slug) {
        // 表名來自本類別內的常數，不是外部輸入
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE slug = ?", Integer.class, slug);
    }
}
