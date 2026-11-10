package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.media.MediaAssetDto;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaRequest;
import com.nextkey.ecommerce.core.media.MediaService;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 公開素材的匿名讀取（Sprint 336，DEF-482），對真實 PostgreSQL、真實 HTTP（{@code RANDOM_PORT}）執行。
 *
 * <p>為什麼要這一層：(1) 「哪些素材能被匿名讀到」的條件（公開、未刪除、店鋪 ACTIVE）寫在 JPQL，mock 的 Repository 看不到它對不對
 * （參見 mock-repository-hides-never-executed-queries）；(2) 這條路徑是整個媒體模組唯一有意不看呼叫者店鋪的讀取，
 * 一定要用<b>沒帶任何憑證</b>的請求去驗，MockMvc 的測試很容易不小心帶著登入狀態；
 * (3) 本機 E2E 守門沒有 MinIO，瀏覽器層級驗不到實際讀圖（見 at-media-upload-precheck 的說明），所以伺服器端的行為要在這裡釘住。
 *
 * <p>儲存層用 {@code @MockBean}（獨立的 Spring context，不影響共用的 {@link IntegrationTestConfiguration} 的 mock）：
 * 物件路徑的店鋪歸屬規則與 {@code StorageService.belongsToTenant} 的真實實作相同（{@code StorageServiceTest} 釘住真實的那個）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("Sprint 336: 公開素材的匿名讀取（DEF-482，真實 PostgreSQL＋真實 HTTP）")
class PublicMediaEndpointIntegrationTest {

    private static final byte[] IMAGE_BYTES = "fake-png-bytes".getBytes();

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private TenantRepository tenantRepository;

    /** 真正的 MediaService（共用設定裡有一個 @Primary 的 mock，這裡用 bean 名稱取真的）。 */
    @Autowired
    @Qualifier("mediaService")
    private MediaService realMediaService;

    @MockBean
    private StorageService storageService;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<UUID> assetIds = new ArrayList<>();
    private final List<UUID> tenantIds = new ArrayList<>();

    @BeforeEach
    void stubStorage() {
        when(storageService.belongsToTenant(anyString(), any(UUID.class)))
                .thenAnswer(inv -> inv.<String>getArgument(0).startsWith(inv.<UUID>getArgument(1) + "/"));
        when(storageService.getObject(anyString())).thenAnswer(inv -> new ByteArrayInputStream(IMAGE_BYTES));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        assetIds.forEach(mediaAssetRepository::deleteById);
        tenantIds.forEach(tenantRepository::deleteById);
    }

    private Tenant newStore(final Tenant.TenantStatus status) {
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.saveAndFlush(Tenant.builder().name("公開素材店 " + stamp).slug("pub-media-" + stamp)
                .contactEmail("pub-media-" + stamp + "@example.com").status(status).build());
        tenantIds.add(tenant.getId());
        return tenant;
    }

    private MediaAsset newAsset(final Tenant store, final boolean isPublic, final boolean isDeleted) {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(MediaAsset.builder().tenant(store)
                .fileName("hero.png").originalName("封面 hero.png")
                .filePath(store.getId() + "/" + UUID.randomUUID() + "-hero.png")
                .fileSize((long) IMAGE_BYTES.length).mimeType("image/png").fileType(MediaAsset.FileType.IMAGE)
                .isPublic(isPublic).isDeleted(isDeleted).build());
        assetIds.add(asset.getId());
        return asset;
    }

    private HttpResponse<byte[]> anonymousGet(final String pathAfterContext) throws Exception {
        URI uri = URI.create("http://localhost:" + port + contextPath + pathAfterContext);
        // 刻意不帶任何標頭：這條路徑的重點就是「沒有憑證」
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String body(final HttpResponse<byte[]> response) {
        return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String header(final HttpResponse<?> response, final String name) {
        return response.headers().firstValue(name).orElse("<沒有 " + name + " 標頭>");
    }

    @Test
    @DisplayName("IT-PM-001: 公開、未刪除、店鋪 ACTIVE 的素材 → 匿名 200，Content-Type 是圖片，內容是儲存層的檔案，帶 5 分鐘公開快取")
    void publicAssetOfActiveStore_isServedToAnonymous() throws Exception {
        MediaAsset asset = newAsset(newStore(Tenant.TenantStatus.ACTIVE), true, false);

        HttpResponse<byte[]> response = anonymousGet("/v2/public/media/" + asset.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        // 比對 MIME 類型本身，不比整個標頭：全域的 server.servlet.encoding.force-response=true（DEF-397，Sprint 296）
        // 會替每個回應補上 ;charset=UTF-8，連圖片也一樣（需要登入的 /v2/media/files/{id} 本來就是這樣）。
        // 瀏覽器對圖片類型會忽略 charset 參數，不影響顯示；這是既有的全域行為，不是這個端點引入的。
        assertThat(header(response, "Content-Type")).startsWith("image/png");
        assertThat(response.body()).isEqualTo(IMAGE_BYTES);
        assertThat(header(response, "Cache-Control").toLowerCase(Locale.ROOT))
                .as("公開圖片會被反覆載入，要能被瀏覽器／CDN 快取；也要明確是 public")
                .contains("public").contains("max-age=300");
        assertThat(header(response, "Content-Disposition")).startsWith("inline");
        assertThat(header(response, "X-Content-Type-Options"))
                .as("使用者上傳的檔案由我們的網域提供，不能讓瀏覽器自行猜測內容類型")
                .isEqualTo("nosniff");
    }

    @Test
    @DisplayName("IT-PM-002: 私有的素材 → 404 E-4103；和『根本不存在』的回應一模一樣（不能拿來探測素材存在與否）")
    void privateAsset_is404_andLooksExactlyLikeAMissingOne() throws Exception {
        MediaAsset privateAsset = newAsset(newStore(Tenant.TenantStatus.ACTIVE), false, false);

        HttpResponse<byte[]> privateResponse = anonymousGet("/v2/public/media/" + privateAsset.getId());
        HttpResponse<byte[]> missingResponse = anonymousGet("/v2/public/media/" + UUID.randomUUID());

        assertThat(privateResponse.statusCode()).isEqualTo(404);
        assertThat(missingResponse.statusCode()).isEqualTo(404);
        assertThat(body(privateResponse)).contains("E-4103");
        assertThat(stripVolatile(body(privateResponse)))
                .as("私有與不存在的回應內容要相同，否則呼叫者能分辨素材存不存在")
                .isEqualTo(stripVolatile(body(missingResponse)));
    }

    /** 回應裡會變的部分（時間戳記、請求路徑裡的素材編號）拿掉再比。 */
    private static String stripVolatile(final String json) {
        return json.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<id>");
    }

    @Test
    @DisplayName("IT-PM-003: 公開但已被刪除（軟刪除）的素材 → 404——刪除要立刻收回，不能因為『曾經公開』而繼續讀得到")
    void publicButDeletedAsset_is404() throws Exception {
        MediaAsset asset = newAsset(newStore(Tenant.TenantStatus.ACTIVE), true, true);

        HttpResponse<byte[]> response = anonymousGet("/v2/public/media/" + asset.getId());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(body(response)).contains("E-4103");
    }

    @Test
    @DisplayName("IT-PM-004: 公開但店鋪不是 ACTIVE（審核中、被拒、停權、終止）→ 404——與店鋪首頁、頁面標題的規則一致")
    void publicAssetOfNonActiveStore_is404() throws Exception {
        for (Tenant.TenantStatus status : Tenant.TenantStatus.values()) {
            if (status == Tenant.TenantStatus.ACTIVE) {
                continue;
            }
            MediaAsset asset = newAsset(newStore(status), true, false);

            HttpResponse<byte[]> response = anonymousGet("/v2/public/media/" + asset.getId());

            assertThat(response.statusCode()).as("店鋪狀態 %s 的公開素材不該被匿名讀到", status).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("IT-PM-005: 儲存路徑不屬於素材自己的店鋪（資料列被改壞）→ 404，不讀別家的檔案（縱深防護）")
    void publicAssetPointingAtAnotherTenantsObject_is404() throws Exception {
        Tenant store = newStore(Tenant.TenantStatus.ACTIVE);
        MediaAsset asset = newAsset(store, true, false);
        asset.setFilePath(UUID.randomUUID() + "/" + UUID.randomUUID() + "-secret.png");
        mediaAssetRepository.saveAndFlush(asset);

        HttpResponse<byte[]> response = anonymousGet("/v2/public/media/" + asset.getId());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("IT-PM-006: 需要登入的 /v2/media/files/{id} 沒有被放寬——就算素材是公開的，匿名打它仍是 401")
    void authenticatedFileEndpoint_stillRequiresLogin_evenForAPublicAsset() throws Exception {
        MediaAsset asset = newAsset(newStore(Tenant.TenantStatus.ACTIVE), true, false);

        HttpResponse<byte[]> response = anonymousGet("/v2/media/files/" + asset.getId());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("IT-PM-007: 切換→讀取→撤回的完整往返：用真的 MediaService 開公開，DTO 的 publicUrl 就是匿名真的讀得到的路徑；撤回後立刻 404")
    void toggleRoundTrip_publicUrlIsTheRealRoute_andRevokeTakesEffectImmediately() throws Exception {
        Tenant store = newStore(Tenant.TenantStatus.ACTIVE);
        MediaAsset asset = newAsset(store, false, false);
        TenantContext.setCurrentTenant(store.getId());

        // 一開始是私有：匿名讀不到
        assertThat(anonymousGet("/v2/public/media/" + asset.getId()).statusCode()).isEqualTo(404);

        MediaAssetDto madePublic = realMediaService.updateAsset(asset.getId(),
                UpdateMediaRequest.builder().isPublic(true).build());
        assertThat(madePublic.getIsPublic()).isTrue();
        assertThat(madePublic.getPublicUrl())
                .as("DTO 給前端的匿名網址要含 context-path，並且就是 PublicMediaController 的路由")
                .isEqualTo(contextPath + "/v2/public/media/" + asset.getId());

        HttpResponse<byte[]> served = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + madePublic.getPublicUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(IMAGE_BYTES);

        MediaAssetDto revoked = realMediaService.updateAsset(asset.getId(),
                UpdateMediaRequest.builder().isPublic(false).build());
        assertThat(revoked.getIsPublic()).isFalse();
        assertThat(revoked.getPublicUrl()).isNull();
        assertThat(anonymousGet("/v2/public/media/" + asset.getId()).statusCode())
                .as("撤回公開後，伺服器端立刻不再提供（已被瀏覽器快取的副本另計，最多 5 分鐘）").isEqualTo(404);
    }
}
