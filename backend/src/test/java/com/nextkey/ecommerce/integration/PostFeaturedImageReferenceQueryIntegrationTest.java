package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;

/**
 * {@code PostRepository.existsByTenantIdAndFeaturedImageUrlReferencing} 的查詢語意（Sprint 334，DEF-474），
 * 對真實 PostgreSQL 執行——mock 的 Repository 看不到 JPQL 是否真的正確（參見 mock-repository-hides-never-executed-queries）。
 *
 * <p>三件事必須成立：①只看本店鋪的貼文（別店的封面圖不能擋住這家店刪圖）；②片段是「子字串」比對，
 * 儲存路徑裡常見的 {@code _}、{@code %} 不是萬用字元（舊的 M15 查詢用 LIKE，{@code a_c} 會誤中 {@code abc}）；
 * ③沒有封面圖的貼文（NULL）不會誤判。
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 334: 貼文封面圖引用查詢（真實 PostgreSQL）")
class PostFeaturedImageReferenceQueryIntegrationTest {

    @Autowired private PostRepository postRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant newStore() {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder().name("引用查詢店 " + stamp).slug("ref-" + stamp)
                .contactEmail("ref-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newAuthor() {
        return userRepository.saveAndFlush(User.builder().email("ref-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy").role(User.UserRole.STORE_OWNER).status("ACTIVE").build());
    }

    private void newPost(final Tenant tenant, final User author, final String featuredImageUrl) {
        jdbcTemplate.update("INSERT INTO posts (id, tenant_id, author_id, title, slug, featured_image_url, status, "
                + "view_count, created_at, updated_at) VALUES (?, ?, ?, 'ref', ?, ?, 'DRAFT', 0, NOW(), NOW())",
                UUID.randomUUID(), tenant.getId(), author.getId(), "ref-" + UUID.randomUUID(), featuredImageUrl);
    }

    @Test
    @DisplayName("本店鋪貼文的封面圖網址含該片段 → true；含完整的 M18 檔案網址也成立")
    void ownStorePostReferencingTheFragment_isFound() {
        Tenant store = newStore();
        UUID assetId = UUID.randomUUID();
        newPost(store, newAuthor(), "https://shop.example.com/api/v2/media/files/" + assetId + "?w=800");

        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(store.getId(), assetId.toString()))
                .isTrue();
    }

    @Test
    @DisplayName("別家店鋪的貼文引用同一個片段 → 不算（只看本店鋪）")
    void otherStoresPostsAreIgnored() {
        Tenant mine = newStore();
        Tenant other = newStore();
        UUID assetId = UUID.randomUUID();
        newPost(other, newAuthor(), "/api/v2/media/files/" + assetId);

        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(mine.getId(), assetId.toString()))
                .isFalse();
    }

    @Test
    @DisplayName("片段是子字串比對，不是 LIKE：_ 與 % 不是萬用字元")
    void underscoreAndPercentAreNotWildcards() {
        Tenant store = newStore();
        newPost(store, newAuthor(), "https://cdn.example.com/abc/photo.png");

        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(store.getId(), "a_c")).isFalse();
        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(store.getId(), "a%c")).isFalse();
        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(store.getId(), "abc/photo")).isTrue();
    }

    @Test
    @DisplayName("沒有封面圖（NULL）的貼文、與完全不含該片段的封面圖 → false")
    void nullAndUnrelatedFeaturedImagesDoNotMatch() {
        Tenant store = newStore();
        User author = newAuthor();
        newPost(store, author, null);
        newPost(store, author, "https://cdn.example.com/other.png");

        assertThat(postRepository.existsByTenantIdAndFeaturedImageUrlReferencing(store.getId(),
                UUID.randomUUID().toString())).isFalse();
    }
}
