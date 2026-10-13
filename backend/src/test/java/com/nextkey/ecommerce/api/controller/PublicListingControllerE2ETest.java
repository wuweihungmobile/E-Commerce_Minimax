package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.util.UUID;

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
 * 跨店鋪公開商品/房源瀏覽真實 PostgreSQL 整合測試（PRD §10.1.1 `/products`、`/rooms`，DEF-358(a)，
 * Sprint 259）。
 *
 * <p>比照 {@link StorefrontControllerE2ETest} 的既有慣例：全部請求皆不帶 {@code Authorization}
 * 標頭，驗證真正匿名也能通過 {@code SecurityConfig} 的 {@code /v2/public/**} permitAll 規則。
 * 重點驗證「跨租戶聚合」（這是本端點與既有 {@code /v2/listings} 的本質差異）且不洩漏停權租戶、
 * 下架/草稿項目。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(IntegrationTestConfiguration.class)
@DisplayName("IT-PUBLIC-LISTINGS: 跨店鋪公開商品/房源瀏覽（DEF-358a）")
class PublicListingControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    private static final String BASE_URL = "/v2/public/listings";

    private Tenant tenantA;
    private Tenant tenantB;
    private User owner;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);

        final String stamp = String.valueOf(System.nanoTime());
        tenantA = tenantRepository.save(Tenant.builder()
                .name("Public Listings Store A").slug("public-listings-a-" + stamp)
                .contactEmail("public-a-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE).build());
        tenantB = tenantRepository.save(Tenant.builder()
                .name("Public Listings Store B").slug("public-listings-b-" + stamp)
                .contactEmail("public-b-" + stamp + "@example.com")
                .status(Tenant.TenantStatus.ACTIVE).build());
        owner = userRepository.save(User.builder()
                .email("public-listings-owner-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Public Listings Owner")
                .role(User.UserRole.STORE_OWNER).status("ACTIVE").build());
    }

    private UUID seedListing(final Tenant tenant, final Listing.ListingType type,
            final Listing.ListingStatus status, final String title) {
        return listingRepository.save(Listing.builder()
                .tenant(tenant).owner(owner).listingType(type).title(title)
                .basePrice(new BigDecimal("100.00")).currency("TWD")
                .status(status).build()).getId();
    }

    @Test
    @DisplayName("匿名請求（無 Authorization 標頭）成功取得跨店鋪列表，含兩家店的商品與店名/slug")
    void getPublicListings_anonymousAccess_aggregatesAcrossTenants() {
        String marker = String.valueOf(System.nanoTime());
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "A店商品" + marker);
        seedListing(tenantB, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "B店商品" + marker);

        given()
                .when()
                .get(BASE_URL + "?keyword=" + marker)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.content", hasSize(2))
                .body("data.content.title", org.hamcrest.Matchers.hasItems("A店商品" + marker, "B店商品" + marker));
    }

    @Test
    @DisplayName("不洩漏 DRAFT／INACTIVE 項目，只回傳 ACTIVE")
    void getPublicListings_excludesNonActiveListings() {
        String marker = String.valueOf(System.nanoTime());
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "上架商品" + marker);
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.DRAFT, "草稿商品" + marker);
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.INACTIVE, "下架商品" + marker);

        given()
                .when()
                .get(BASE_URL + "?keyword=" + marker)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("上架商品" + marker));
    }

    @Test
    @DisplayName("租戶已停權時，其 ACTIVE 項目仍不得出現在跨店鋪列表（比照 StorefrontController 既有原則）")
    void getPublicListings_excludesListingsFromSuspendedTenant() {
        String marker = String.valueOf(System.nanoTime());
        Tenant suspended = tenantRepository.save(Tenant.builder()
                .name("Suspended Store").slug("public-listings-suspended-" + marker)
                .contactEmail("suspended-" + marker + "@example.com")
                .status(Tenant.TenantStatus.SUSPENDED).build());
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "正常店商品" + marker);
        seedListing(suspended, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "停權店商品" + marker);

        given()
                .when()
                .get(BASE_URL + "?keyword=" + marker)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("正常店商品" + marker));
    }

    @Test
    @DisplayName("?type=ROOM 只回傳房源，且每筆帶有所屬店鋪名稱/slug")
    void getPublicListings_filterByType_includesTenantInfo() {
        String marker = String.valueOf(System.nanoTime());
        seedListing(tenantA, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE, "商品" + marker);
        seedListing(tenantA, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE, "房源" + marker);

        given()
                .when()
                .get(BASE_URL + "?type=ROOM&keyword=" + marker)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].title", equalTo("房源" + marker))
                .body("data.content[0].listingType", equalTo("ROOM"))
                .body("data.content[0].tenantName", equalTo(tenantA.getName()))
                .body("data.content[0].tenantSlug", equalTo(tenantA.getSlug()));
    }

    @Test
    @DisplayName("sortBy 帶不在允許清單的欄位 → 400（E-9000），而非未攔截例外造成的 500（DEF-344 同類缺陷，本端點新寫時即避免）")
    void getPublicListings_invalidSortBy_returns400NotInternalServerError() {
        given()
                .when()
                .get(BASE_URL + "?sortBy=ownerId")
                .then()
                .statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("type 帶無效值 → 422（E-3001），而非未攔截例外造成的 500")
    void getPublicListings_invalidType_returns422() {
        given()
                .when()
                .get(BASE_URL + "?type=NOT_A_REAL_TYPE")
                .then()
                .statusCode(422)
                .body("code", equalTo("E-3001"));
    }
}
