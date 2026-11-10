package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;

/**
 * 公開貼文端點只供應 ACTIVE 店鋪（Sprint 337，DEF-485），對真實 PostgreSQL、真實 HTTP（{@code RANDOM_PORT}）執行。
 *
 * <p>三個匿名端點 {@code GET /v2/posts}、{@code GET /v2/posts/{slug}}、{@code GET /v2/posts/categories}
 * 原本只過濾貼文本身的狀態（已發布、分類啟用中），不看店鋪狀態：停權店鋪的已發布貼文，直接打 API 仍讀得到
 * （前端頁面先查公開店鋪資料、非 ACTIVE 整頁 404，所以畫面上看不出來）。使用者 2026-11-10 以互動選項拍板：
 * 三個端點都只供應 ACTIVE 店鋪，非 ACTIVE 與「店鋪不存在」一律同一個 404（E-2000），與公開店鋪、商品詳情、公開素材同一做法。
 *
 * <p>為什麼要這一層：(1) 條件是「店鋪現在的狀態」，要用真的資料庫列驗，狀態切換後立刻生效（不是快取）；
 * (2) 這是匿名端點，一定要用<b>沒帶任何憑證</b>的請求驗；(3) 固件<b>先讓每個端點真的會回出內容</b>（貼文已發布、分類啟用中），
 * 所以 404 一定是店鋪狀態造成的，不是因為「沒有資料」。
 *
 * <p>「狀態 × 請求形狀」的矩陣用 {@link SoftAssertions}：每一格都會被量到，失敗時一次列出所有不符的格子
 * （硬斷言在第一格就停，看不出其他端點是否也有同樣的缺口）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("Sprint 337: 公開貼文端點只供應 ACTIVE 店鋪（DEF-485，真實 PostgreSQL＋真實 HTTP）")
class PublicPostsStoreStatusIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCategoryRepository postCategoryRepository;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<UUID> postIds = new ArrayList<>();
    private final List<UUID> categoryIds = new ArrayList<>();
    private final List<UUID> tenantIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        postIds.forEach(postRepository::deleteById);
        categoryIds.forEach(postCategoryRepository::deleteById);
        tenantIds.forEach(tenantRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    /** 一間店鋪＋一位作者＋一個啟用中的分類＋一篇已發布、屬於該分類的貼文：每個端點都會有內容可回。 */
    private record Fixture(Tenant store, PostCategory category, Post post) {
        UUID tenantId() {
            return store.getId();
        }
    }

    private Fixture newFixture(final Tenant.TenantStatus status) {
        long stamp = System.nanoTime();
        Tenant store = tenantRepository.saveAndFlush(Tenant.builder().name("公開貼文店 " + stamp).slug("pub-posts-" + stamp)
                .contactEmail("pub-posts-" + stamp + "@example.com").status(status).build());
        tenantIds.add(store.getId());
        User author = userRepository.saveAndFlush(User.builder().email("pub-posts-" + stamp + "@example.com")
                .passwordHash("dummy").role(User.UserRole.SELLER).status("ACTIVE").build());
        userIds.add(author.getId());
        PostCategory category = postCategoryRepository.saveAndFlush(PostCategory.builder().tenant(store)
                .name("公開分類" + stamp).slug("pub-cat-" + stamp).isActive(true).build());
        categoryIds.add(category.getId());
        Post post = postRepository.saveAndFlush(Post.builder().tenant(store).author(author).category(category)
                .title("公開貼文標題" + stamp).slug("pub-post-" + stamp).content("公開貼文內容 " + stamp)
                .status(Post.PostStatus.PUBLISHED).publishedAt(Instant.now()).build());
        postIds.add(post.getId());
        return new Fixture(store, category, post);
    }

    /**
     * 三個端點的五種請求形狀。依分類篩選與 countView=false 各走不同的程式分支（不同的 Repository 查詢、不同的服務重載），
     * 守門若只放在其中一支，另一支就會漏——所以每個形狀都要單獨驗。
     */
    private static Map<String, String> requests(final UUID tenantId, final String postSlug, final UUID categoryId) {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("列表", "/v2/posts?tenantId=" + tenantId);
        paths.put("依分類篩選的列表", "/v2/posts?tenantId=" + tenantId + "&categoryId=" + categoryId);
        paths.put("單篇", "/v2/posts/" + postSlug + "?tenantId=" + tenantId);
        paths.put("單篇（countView=false）", "/v2/posts/" + postSlug + "?tenantId=" + tenantId + "&countView=false");
        paths.put("公開分類", "/v2/posts/categories?tenantId=" + tenantId);
        return paths;
    }

    private static Map<String, String> requests(final Fixture f) {
        return requests(f.tenantId(), f.post().getSlug(), f.category().getId());
    }

    private HttpResponse<String> anonymousGet(final String pathAfterContext) throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + pathAfterContext);
        // 刻意不帶任何標頭：這幾個端點是匿名公開的，重點就是「沒有憑證」
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 回應裡會變的部分（時間戳記、編號）拿掉再比。 */
    private static String stripVolatile(final String json) {
        return json.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<id>");
    }

    @Test
    @DisplayName("IT-PPS-001（對照組）: ACTIVE 店鋪 → 五種請求都 200，而且真的帶出貼文與分類——證明固件本身會讓端點回出內容")
    void activeStore_servesPostsAndCategories() throws Exception {
        Fixture f = newFixture(Tenant.TenantStatus.ACTIVE);

        Map<String, HttpResponse<String>> responses = new LinkedHashMap<>();
        for (Map.Entry<String, String> request : requests(f).entrySet()) {
            responses.put(request.getKey(), anonymousGet(request.getValue()));
        }

        SoftAssertions soft = new SoftAssertions();
        responses.forEach((name, response) -> soft.assertThat(response.statusCode()).as("ACTIVE 店鋪的「%s」", name).isEqualTo(200));
        soft.assertThat(responses.get("列表").body()).contains(f.post().getSlug());
        soft.assertThat(responses.get("依分類篩選的列表").body()).contains(f.post().getSlug());
        soft.assertThat(responses.get("單篇").body()).contains(f.post().getTitle());
        soft.assertThat(responses.get("單篇（countView=false）").body()).contains(f.post().getTitle());
        soft.assertThat(responses.get("公開分類").body()).contains(f.category().getName());
        soft.assertAll();
    }

    @Test
    @DisplayName("IT-PPS-002: 店鋪不是 ACTIVE（審核中、被拒、停權、終止）→ 五種請求全部 404 E-2000，回應裡沒有貼文或分類的任何內容")
    void nonActiveStore_isNotServed_andLeaksNothing() throws Exception {
        SoftAssertions soft = new SoftAssertions();
        for (Tenant.TenantStatus status : Tenant.TenantStatus.values()) {
            if (status == Tenant.TenantStatus.ACTIVE) {
                continue;
            }
            Fixture f = newFixture(status);

            for (Map.Entry<String, String> request : requests(f).entrySet()) {
                HttpResponse<String> response = anonymousGet(request.getValue());

                String what = "店鋪狀態 " + status + " 的「" + request.getKey() + "」";
                soft.assertThat(response.statusCode()).as(what + "不該被匿名讀到").isEqualTo(404);
                soft.assertThat(response.body()).as(what + "的錯誤碼").contains("E-2000");
                soft.assertThat(response.body()).as(what + "不能洩漏貼文或分類內容")
                        .doesNotContain(f.post().getTitle()).doesNotContain(f.post().getSlug())
                        .doesNotContain(f.category().getName());
            }
        }
        soft.assertAll();
    }

    @Test
    @DisplayName("IT-PPS-003: 非 ACTIVE 店鋪與『根本不存在的店鋪』的回應一模一樣——不能拿來探測店鋪存不存在或被停權")
    void suspendedStore_looksExactlyLikeAMissingOne() throws Exception {
        Fixture f = newFixture(Tenant.TenantStatus.SUSPENDED);
        // 同一個貼文代稱與分類編號，只把店鋪換成不存在的：請求的其餘部分完全相同
        Map<String, String> missing = requests(UUID.randomUUID(), f.post().getSlug(), f.category().getId());

        SoftAssertions soft = new SoftAssertions();
        for (Map.Entry<String, String> request : requests(f).entrySet()) {
            HttpResponse<String> suspended = anonymousGet(request.getValue());
            HttpResponse<String> absent = anonymousGet(missing.get(request.getKey()));

            soft.assertThat(absent.statusCode()).as("不存在的店鋪的「%s」", request.getKey()).isEqualTo(404);
            soft.assertThat(stripVolatile(suspended.body()))
                    .as("「%s」：停權與不存在的回應內容要相同，否則呼叫者能分辨", request.getKey())
                    .isEqualTo(stripVolatile(absent.body()));
        }
        soft.assertAll();
    }

    @Test
    @DisplayName("IT-PPS-004: 讀的是店鋪『現在』的狀態——停權後立刻 404、恢復 ACTIVE 後立刻又讀得到（不是快取，也不是建立時的狀態）")
    void storeStatusChange_takesEffectImmediately() throws Exception {
        Fixture f = newFixture(Tenant.TenantStatus.ACTIVE);
        String listPath = requests(f).get("列表");
        String detailPath = requests(f).get("單篇（countView=false）");
        assertThat(anonymousGet(listPath).statusCode()).isEqualTo(200);

        f.store().setStatus(Tenant.TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(f.store());
        SoftAssertions suspended = new SoftAssertions();
        suspended.assertThat(anonymousGet(listPath).statusCode()).as("停權後的列表").isEqualTo(404);
        suspended.assertThat(anonymousGet(detailPath).statusCode()).as("停權後的單篇").isEqualTo(404);
        suspended.assertAll();

        f.store().setStatus(Tenant.TenantStatus.ACTIVE);
        tenantRepository.saveAndFlush(f.store());
        SoftAssertions restored = new SoftAssertions();
        restored.assertThat(anonymousGet(listPath).statusCode()).as("恢復後的列表").isEqualTo(200);
        restored.assertThat(anonymousGet(detailPath).statusCode()).as("恢復後的單篇").isEqualTo(200);
        restored.assertAll();
    }
}
