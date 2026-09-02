package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.core.cms.post.PostService;
import com.nextkey.ecommerce.core.faq.FaqService;
import com.nextkey.ecommerce.core.knowledge.KnowledgeBaseService;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.faq.FaqArticle;
import com.nextkey.ecommerce.domain.model.faq.FaqCategory;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.domain.repository.faq.FaqArticleRepository;
import com.nextkey.ecommerce.domain.repository.faq.FaqCategoryRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeArticleRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeCategoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 瀏覽數遞增的持久化與併發正確性整合測試（Sprint 106，DEF-055；真實 PostgreSQL）。
 *
 * <p>三處 {@code incrementViewCount} 實作完全同型（{@code findById} → 記憶體 +1 →
 * {@code save()}），對應的 {@code Post} / {@code FaqArticle} / {@code KnowledgeArticle}
 * 都沒有 {@code @Version}，所以競態的失效模式是**靜默丟失更新**而非拋例外。
 * 這是 Sprint 105 全專案掃描中**併發度最高**的路徑（每次瀏覽都會走），
 * 且 {@code KnowledgeArticleRepository.findPopularByCategoryId} 以 {@code viewCount DESC}
 * 取熱門文章，少計會直接讓排名失真。
 *
 * <p>{@code postViewIsPersistedAfterASingleView} 刻意是**單執行緒**的：
 * {@code PostService.getPublishedPostBySlug} 標的是 {@code @Transactional(readOnly = true)}，
 * 需要先確認「一次瀏覽到底有沒有寫進 DB」，才知道 Post 這一路的失效是「少計」還是「全無」。
 *
 * <p>既有的 {@code FaqServiceTest.incrementViewCount_incrementsAndSaves} 與
 * {@code KnowledgeBaseServiceTest.incrementViewCount_incrementsAndSaves} 都 mock 掉 repository、
 * 斷言「記憶體物件上的數字對不對」——正是在缺陷存在時照樣全綠的斷言。
 *
 * <p><b>Sprint 107 / DEF-057</b> 另加入兩個租戶隔離案例。Sprint 106 修 DEF-055 時發現
 * {@code KnowledgeBaseService.incrementViewCount} 是該類別唯一沒有租戶範圍的方法，
 * 造成同一個類別對「他租戶的文章」反應不一致：列表看不到、詳情 404，
 * 但**瀏覽數端點回 200 並且真的 +1**——一篇你被禁止閱讀的文章，你可以幫它衝瀏覽數，
 * 而瀏覽數正是 {@code findPopularByCategoryId} 的排序欄位。語意由使用者於 Sprint 107 拍板：
 * 收斂為與同類別其餘端點一致（跨租戶即查無文章）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-VIEW: 瀏覽數遞增的持久化與併發（Sprint 106 / DEF-055）")
class ViewCountConcurrencyIntegrationTest {

    /** 併發瀏覽的執行緒數。 */
    private static final int VIEWERS = 10;

    @Autowired private PostService postService;
    @Autowired private FaqService faqService;
    @Autowired private KnowledgeBaseService knowledgeBaseService;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private FaqCategoryRepository faqCategoryRepository;
    @Autowired private FaqArticleRepository faqArticleRepository;
    @Autowired private KnowledgeCategoryRepository knowledgeCategoryRepository;
    @Autowired private KnowledgeArticleRepository knowledgeArticleRepository;

    private Tenant tenant;
    private User author;
    private long stamp;

    @BeforeEach
    void setUp() {
        stamp = System.nanoTime();
        tenant = seedTenant("own");

        author = userRepository.save(User.builder()
                .email("view-author-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("View Author")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());
    }

    // ── Seeding ──────────────────────────────────────────────────────

    private Tenant seedTenant(final String tag) {
        long tenantStamp = System.nanoTime();
        return tenantRepository.save(Tenant.builder()
                .name("View Count Tenant " + tag)
                .slug("view-" + tag + "-" + tenantStamp)
                .contactEmail("view-" + tag + "-" + tenantStamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
    }

    private Post seedPublishedPost() {
        return postRepository.save(Post.builder()
                .tenant(tenant)
                .author(author)
                .title("View Count Post")
                .slug("view-post-" + stamp)
                .content("body")
                .status(Post.PostStatus.PUBLISHED)
                .build());
    }

    private UUID seedFaqArticle() {
        FaqCategory category = faqCategoryRepository.save(FaqCategory.builder()
                .tenantId(tenant.getId())
                .name("General")
                .slug("faq-cat-" + stamp)
                .build());
        return faqArticleRepository.save(FaqArticle.builder()
                .tenantId(tenant.getId())
                .category(category)
                .question("Q?")
                .answer("A.")
                .slug("faq-art-" + stamp)
                .build()).getId();
    }

    private UUID seedKnowledgeArticle() {
        KnowledgeCategory category = knowledgeCategoryRepository.save(KnowledgeCategory.builder()
                .tenantId(tenant.getId())
                .name("General")
                .slug("kb-cat-" + stamp)
                .build());
        return knowledgeArticleRepository.save(KnowledgeArticle.builder()
                .tenant(tenant)
                .category(category)
                .author(author)
                .title("KB Article")
                .slug("kb-art-" + stamp)
                .content("body")
                .status(KnowledgeArticle.ArticleStatus.PUBLISHED)
                .build()).getId();
    }

    /** 直接讀 DB，繞過任何持久化上下文快取——記憶體物件上的數字不能作數。 */
    private int viewCountOf(final String table, final UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    /** 起跑槍 + N 執行緒，各自獨立交易；未預期例外分類收集而非吞掉。 */
    private Map<String, Integer> runConcurrently(final Runnable action) throws Exception {
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(VIEWERS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>(VIEWERS);
        try {
            for (int i = 0; i < VIEWERS; i++) {
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        TenantContext.setCurrentTenant(tenant.getId());
                        action.run();
                    } catch (RuntimeException e) {
                        unexpected.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    } finally {
                        TenantContext.clear();
                    }
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<?> f : results) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        return unexpected;
    }

    // ── M15 CMS Post ─────────────────────────────────────────────────

    @Test
    @DisplayName("單次瀏覽貼文 → view_count 必須真的寫進 DB（readOnly 交易不得靜默丟棄）")
    void postViewIsPersistedAfterASingleView() {
        Post post = seedPublishedPost();

        postService.getPublishedPostBySlug(post.getSlug(), tenant.getId());

        assertThat(viewCountOf("posts", post.getId()))
                .as("getPublishedPostBySlug 是 @Transactional(readOnly = true)，"
                        + "Hibernate 在唯讀交易下是 FlushMode.MANUAL；若遞增未被 flush，"
                        + "這一路的瀏覽數就不是「少計」而是「完全沒計」")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("10 執行緒同時瀏覽貼文 → view_count 為 10，不因讀後寫互相覆蓋")
    void postViewCountSurvivesConcurrentViews() throws Exception {
        Post post = seedPublishedPost();

        Map<String, Integer> unexpected =
                runConcurrently(() -> postService.getPublishedPostBySlug(post.getSlug(), tenant.getId()));

        assertThat(unexpected).as("瀏覽不應拋出技術性例外。本次結果：%s", unexpected).isEmpty();
        assertThat(viewCountOf("posts", post.getId()))
                .as("%s 次瀏覽必須累計 %s，少於此數即為讀後寫的靜默漏計", VIEWERS, VIEWERS)
                .isEqualTo(VIEWERS);
    }

    // ── M18 FAQ ──────────────────────────────────────────────────────

    @Test
    @DisplayName("10 執行緒同時瀏覽 FAQ 文章 → view_count 為 10")
    void faqViewCountSurvivesConcurrentViews() throws Exception {
        UUID articleId = seedFaqArticle();

        Map<String, Integer> unexpected =
                runConcurrently(() -> faqService.incrementViewCount(articleId));

        assertThat(unexpected).as("瀏覽不應拋出技術性例外。本次結果：%s", unexpected).isEmpty();
        assertThat(viewCountOf("faq_articles", articleId))
                .as("%s 次瀏覽必須累計 %s，少於此數即為讀後寫的靜默漏計", VIEWERS, VIEWERS)
                .isEqualTo(VIEWERS);
    }

    // ── M18 知識庫 ────────────────────────────────────────────────────

    @Test
    @DisplayName("10 執行緒同時瀏覽知識庫文章 → view_count 為 10（熱門排名依賴此值）")
    void knowledgeViewCountSurvivesConcurrentViews() throws Exception {
        UUID articleId = seedKnowledgeArticle();

        Map<String, Integer> unexpected =
                runConcurrently(() -> knowledgeBaseService.incrementViewCount(articleId));

        assertThat(unexpected).as("瀏覽不應拋出技術性例外。本次結果：%s", unexpected).isEmpty();
        assertThat(viewCountOf("knowledge_articles", articleId))
                .as("findPopularByCategoryId 以 viewCount DESC 取熱門文章，"
                        + "%s 次瀏覽少計會直接讓排名失真", VIEWERS)
                .isEqualTo(VIEWERS);
    }

    // ── M18 知識庫：租戶隔離（Sprint 107 / DEF-057）────────────────────

    @Test
    @DisplayName("他租戶使用者遞增知識庫文章瀏覽數 → 查無文章，且數字不得變動")
    void knowledgeViewCountRejectsCrossTenantIncrement() {
        UUID articleId = seedKnowledgeArticle();
        Tenant otherTenant = seedTenant("other");

        try {
            TenantContext.setCurrentTenant(otherTenant.getId());
            assertThatThrownBy(() -> knowledgeBaseService.incrementViewCount(articleId))
                    .as("同類別的列表看不到、詳情 404，瀏覽數端點沒有理由回 200")
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4000);
        } finally {
            TenantContext.clear();
        }

        assertThat(viewCountOf("knowledge_articles", articleId))
                .as("viewCount 是 findPopularByCategoryId 的排序欄位；"
                        + "他租戶若能遞增，就能操縱本租戶的熱門排名")
                .isZero();
    }

    @Test
    @DisplayName("本租戶使用者遞增知識庫文章瀏覽數 → 正常 +1（收斂租戶範圍不得誤傷正常路徑）")
    void knowledgeViewCountStillWorksForOwnTenant() {
        UUID articleId = seedKnowledgeArticle();

        try {
            TenantContext.setCurrentTenant(tenant.getId());
            knowledgeBaseService.incrementViewCount(articleId);
        } finally {
            TenantContext.clear();
        }

        assertThat(viewCountOf("knowledge_articles", articleId)).isEqualTo(1);
    }
}
