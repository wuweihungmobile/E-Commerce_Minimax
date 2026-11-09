package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * V97 遷移的資料邏輯（Sprint 334，DEF-480）：貼文與貼文分類被存成空字串的 slug 補成 {@code post-<8 碼>}／
 * {@code category-<8 碼>}；其他列（含先前被存成 {@code -<8碼>} 但網址能開的）一律不動。
 *
 * <p>整合測試的資料庫是 Hibernate 建的，不會執行 Flyway；這裡直接讀 {@code db/migration/V97__*.sql} 的實際內容，
 * 對種好的資料執行（作法同 V90 的遷移測試），整個測試在交易內、結束後回滾。Flyway 本身能不能跑，
 * 由 {@code make validate-e2e}／{@code make validate-schema-doc} 對乾淨資料庫執行遷移驗證。
 *
 * <p>posts.slug 全平台唯一，共用資料庫裡若有其他測試殘留的空字串列會讓種資料時撞到唯一約束，所以先把它們
 * 改名挪開（同樣在這個會回滾的交易內）。
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 334: V97 遷移——空字串的貼文／分類 slug 補成後備格式")
class BackfillEmptyPostSlugsMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V97__Backfill_Empty_Post_And_Category_Slugs.sql";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant newStore() {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder().name("V97 測試店 " + stamp).slug("v97-" + stamp)
                .contactEmail("v97-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newAuthor() {
        return userRepository.saveAndFlush(User.builder().email("v97-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy").role(User.UserRole.STORE_OWNER).status("ACTIVE").build());
    }

    private UUID newPost(final Tenant tenant, final User author, final String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO posts (id, tenant_id, author_id, title, slug, status, view_count, created_at, "
                + "updated_at) VALUES (?, ?, ?, '視覺檢查貼文', ?, 'PUBLISHED', 0, NOW(), NOW())",
                id, tenant.getId(), author.getId(), slug);
        return id;
    }

    private UUID newCategory(final Tenant tenant, final String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO post_categories (id, tenant_id, name, slug, sort_order, is_active, created_at, "
                + "updated_at) VALUES (?, ?, '旅遊', ?, 0, true, NOW(), NOW())", id, tenant.getId(), slug);
        return id;
    }

    private String postSlug(final UUID id) {
        return jdbcTemplate.queryForObject("SELECT slug FROM posts WHERE id = ?", String.class, id);
    }

    private String categorySlug(final UUID id) {
        return jdbcTemplate.queryForObject("SELECT slug FROM post_categories WHERE id = ?", String.class, id);
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private void moveAsideLeftoverEmptySlugs() {
        jdbcTemplate.update("UPDATE posts SET slug = 'v97-leftover-' || id WHERE slug = ''");
    }

    @Test
    @DisplayName("空字串的貼文 slug 補成 post-<8碼>；正常 slug 與「-<8碼>」（網址能開）原封不動")
    void backfillsOnlyEmptyPostSlugs() throws Exception {
        moveAsideLeftoverEmptySlugs();
        Tenant store = newStore();
        User author = newAuthor();
        UUID empty = newPost(store, author, "");
        UUID leadingHyphen = newPost(store, author, "-d46b778f");
        UUID normal = newPost(store, author, "summer-sale");

        runMigration();

        assertThat(postSlug(empty)).as("空字串補成後備格式").matches("^post-[0-9a-f]{8}$");
        assertThat(postSlug(leadingHyphen)).as("開頭有連字號但網址能開的，改了會讓已分享的連結失效").isEqualTo("-d46b778f");
        assertThat(postSlug(normal)).isEqualTo("summer-sale");
    }

    @Test
    @DisplayName("不同店鋪各自的空字串分類 slug 都補成 category-<8碼>，彼此不同；正常分類不動")
    void backfillsEmptyCategorySlugsOfEveryStore() throws Exception {
        Tenant storeA = newStore();
        Tenant storeB = newStore();
        UUID emptyA = newCategory(storeA, "");
        UUID emptyB = newCategory(storeB, "");
        UUID normalA = newCategory(storeA, "travel");

        runMigration();

        assertThat(categorySlug(emptyA)).matches("^category-[0-9a-f]{8}$");
        assertThat(categorySlug(emptyB)).matches("^category-[0-9a-f]{8}$");
        assertThat(categorySlug(emptyA)).isNotEqualTo(categorySlug(emptyB));
        assertThat(categorySlug(normalA)).isEqualTo("travel");
    }

    @Test
    @DisplayName("冪等：跑第二次不會再改動已補好的 slug")
    void isIdempotent() throws Exception {
        moveAsideLeftoverEmptySlugs();
        Tenant store = newStore();
        UUID emptyPost = newPost(store, newAuthor(), "");
        UUID emptyCategory = newCategory(store, "");

        runMigration();
        String postAfterFirst = postSlug(emptyPost);
        String categoryAfterFirst = categorySlug(emptyCategory);
        // 先確認第一次真的補了（否則「空字串跑兩次還是空字串」也會讓下面的相等斷言通過）
        assertThat(postAfterFirst).matches("^post-[0-9a-f]{8}$");
        assertThat(categoryAfterFirst).matches("^category-[0-9a-f]{8}$");
        runMigration();

        assertThat(postSlug(emptyPost)).isEqualTo(postAfterFirst);
        assertThat(categorySlug(emptyCategory)).isEqualTo(categoryAfterFirst);
    }
}
