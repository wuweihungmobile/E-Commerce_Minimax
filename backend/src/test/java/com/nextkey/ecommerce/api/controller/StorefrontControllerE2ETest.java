package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 店鋪前台公開端點真實 PostgreSQL 整合測試（DEF-321(b)，Sprint 252）。
 *
 * <p>重點驗證 PRD §12.5／§12.7「Guest（未登入）可瀏覽」：全部請求皆不帶
 * {@code Authorization} 標頭，確認真正匿名也能通過 {@code SecurityConfig} 的
 * {@code /v2/public/**} permitAll 規則（這正是本輪新增此端點的原因——既有
 * {@code /v2/listings} 對匿名請求會回 401）。同時驗證只回傳 {@code ACTIVE} 店鋪
 * 與上架項目，不洩漏其他租戶或下架/待審核資料。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(IntegrationTestConfiguration.class)
@DisplayName("IT-STOREFRONT: 店鋪前台公開端點（DEF-321b）")
class StorefrontControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    private static final String BASE_URL = "/v2/public/stores";

    private Tenant activeTenant;
    private Tenant otherTenant;
    private User owner;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);

        final String stamp = String.valueOf(System.nanoTime());
        activeTenant = tenantRepository.save(Tenant.builder()
                .name("Storefront Test Store").slug("storefront-active-" + stamp)
                .description("A test storefront").logoUrl("https://example.com/logo.png")
                .contactEmail("storefront-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE).build());
        otherTenant = tenantRepository.save(Tenant.builder()
                .name("Other Store").slug("storefront-other-" + stamp)
                .contactEmail("storefront-other-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE).build());
        owner = userRepository.save(User.builder()
                .email("storefront-owner-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Storefront Owner")
                .role(User.UserRole.STORE_OWNER).status("ACTIVE").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UUID seedListing(final Tenant tenant, final Listing.ListingType type,
            final Listing.ListingStatus status, final boolean featured, final String title) {
        return listingRepository.save(Listing.builder()
                .tenant(tenant).owner(owner).listingType(type).title(title)
                .basePrice(new BigDecimal("100.00")).currency("TWD")
                .status(status).isFeatured(featured).build()).getId();
    }

    @Test
    @DisplayName("GET /{slug}：匿名請求（無 Authorization 標頭）對 ACTIVE 店鋪回 200 並附正確欄位")
    void getStoreProfile_activeTenant_anonymousAccess_returnsProfile() {
        given()
                .when()
                .get(BASE_URL + "/" + activeTenant.getSlug())
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.slug", equalTo(activeTenant.getSlug()))
                .body("data.name", equalTo("Storefront Test Store"))
                .body("data.description", equalTo("A test storefront"))
                .body("data.logoUrl", equalTo("https://example.com/logo.png"));
    }

    @Test
    @DisplayName("GET /{slug}：店鋪為 SUSPENDED → 404（不洩漏「店鋪存在但被停權」）")
    void getStoreProfile_suspendedTenant_returns404() {
        Tenant suspended = tenantRepository.save(Tenant.builder()
                .name("Suspended Store").slug("storefront-suspended-" + System.nanoTime())
                .contactEmail("suspended-" + System.nanoTime() + "@example.com")
                .status(Tenant.TenantStatus.SUSPENDED).build());

        given()
                .when()
                .get(BASE_URL + "/" + suspended.getSlug())
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("GET /{slug}：slug 不存在 → 404，與 SUSPENDED 回應一致（不可枚舉店鋪狀態）")
    void getStoreProfile_unknownSlug_returns404() {
        given()
                .when()
                .get(BASE_URL + "/this-slug-does-not-exist-" + System.nanoTime())
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("GET /{slug}/listings：只回傳本店 ACTIVE 上架項目，不含他店或下架項目")
    void getStoreListings_onlyReturnsOwnActiveListings() {
        seedListing(activeTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, false, "本店上架商品");
        seedListing(activeTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.DRAFT, false, "本店草稿商品");
        seedListing(otherTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, false, "他店上架商品");

        given()
                .when()
                .get(BASE_URL + "/" + activeTenant.getSlug() + "/listings")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("本店上架商品"));
    }

    @Test
    @DisplayName("GET /{slug}/listings?type=ROOM：只回傳指定類型")
    void getStoreListings_filterByType_onlyReturnsThatType() {
        seedListing(activeTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, false, "商品A");
        seedListing(activeTenant, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE, false, "房源A");

        given()
                .when()
                .get(BASE_URL + "/" + activeTenant.getSlug() + "/listings?type=ROOM")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("房源A"))
                .body("data.content[0].listingType", equalTo("ROOM"));
    }

    @Test
    @DisplayName("GET /{slug}/listings?featured=true：只回傳精選項目")
    void getStoreListings_featuredFilter_onlyReturnsFeatured() {
        seedListing(activeTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, true, "精選商品");
        seedListing(activeTenant, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, false, "一般商品");

        given()
                .when()
                .get(BASE_URL + "/" + activeTenant.getSlug() + "/listings?featured=true")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("精選商品"));
    }

    @Test
    @DisplayName("GET /{slug}/listings：店鋪不存在 → 404")
    void getStoreListings_unknownSlug_returns404() {
        given()
                .when()
                .get(BASE_URL + "/this-slug-does-not-exist-" + System.nanoTime() + "/listings")
                .then()
                .statusCode(404);
    }
}
