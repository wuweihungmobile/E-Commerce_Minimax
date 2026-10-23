package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.response.ValidatableMockMvcResponse;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;

/**
 * 沒有店鋪的 ADMIN 不得管理系統租戶（平台自營）名下的房源、商品、SKU、定價規則與房態日曆
 * （Sprint 304，DEF-415，真實 PostgreSQL，走完整 HTTP＋JWT＋權限＋租戶過濾鏈）。
 *
 * <p>沒有加入任何店鋪的使用者的租戶不是 null，而是系統租戶佔位值（{@link AppConstants#SYSTEM_TENANT_ID}），全體共用；
 * 平台自營的資料（管理員建立的房源、商品、定價規則）也蓋成同一個值。五個服務的擁有權檢查原本只比「資源的租戶 ==
 * 呼叫者的租戶」，於是沒有店鋪的 ADMIN（租戶內角色，持有 {@code room:*}／{@code product:*} 寫入權限）對平台自營資料
 * 等於「擁有者」。使用者於 Sprint 303 收尾裁決「比照 Sprint 234 全部擋下」：平台自營資料只有 SUPER_ADMIN 能管。
 *
 * <p>每個案例固定三件事，避免守門變成「對所有人都擋」或「回 403 但其實資料已經被改」：
 * <ol>
 *   <li>沒有店鋪的 ADMIN 被擋（403／E-1007）。回應訊息分不出是權限註解還是擁有權檢查擋下的（{@code BusinessException}
 *       的細節依 AI-2418 設計不外洩，一律回錯誤碼的預設訊息「權限不足」），所以「擋下的是擁有權檢查」改由<b>同角色的對照組</b>
 *       證明：<b>有店鋪的 ADMIN</b> 對自己店鋪的資源做同一個操作成功——權限註解對 ADMIN 放行，兩個 ADMIN 只差有沒有店鋪
 *       （另外，修復前同一批請求對沒有店鋪的 ADMIN 回 200／201，見計畫書 §4 的修復前實測）；</li>
 *   <li>被擋之後資料庫裡的資料<b>沒有被改</b>（標題、狀態、規則內容、SKU、日曆列都查資料庫，不看回應）；</li>
 *   <li>其他對照組照常運作：SUPER_ADMIN 管系統租戶資料；店主碰別人（系統租戶）的資料仍是 403（跨租戶本來就擋，
 *       守門不得因改寫而放寬）。</li>
 * </ol>
 *
 * <p>功能開關：測試庫的系統租戶預設關閉 {@code BOOKING_ENABLED}／{@code DYNAMIC_PRICING_ENABLED}，建立房源與定價規則會先被
 * 開關擋下（與要驗的檢查無關），所以類別開頭暫時打開、結束還原。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 304（DEF-415）: 沒有店鋪的 ADMIN 管不到系統租戶（平台自營）的房源、商品、定價與日曆")
class PlatformSelfRunWriteIsolationE2ETest {

    private static final String PASSWORD = "SecurePass123!";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String RULES_URL = "/v2/dashboard/pricing/rules";
    private static final String CALENDAR_PRICE_URL = "/v2/dashboard/pricing/calendar/price";
    private static final List<String> TOGGLE_KEYS =
            List.of("BOOKING_ENABLED", "RETAIL_ENABLED", "DYNAMIC_PRICING_ENABLED");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository productSkuRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private String stamp;
    private String titlePrefix;

    /** 沒有店鋪的 ADMIN（租戶內角色，租戶是系統租戶佔位值；持有 room:*／product:* 寫入權限）。 */
    private String storelessAdminToken;
    /** 真實登入的平台管理員（SUPER_ADMIN）。 */
    private String superAdminToken;
    /** 一間真實店鋪的店主。 */
    private String storeOwnerToken;
    /** 同一間店鋪裡角色為 ADMIN 的成員（有店鋪的 ADMIN）。 */
    private String storeAdminToken;

    private User superAdminUser;
    private User storeOwnerUser;
    private Tenant systemTenant;
    private Tenant store;
    private final Map<String, Boolean> originalSystemToggles = new HashMap<>();

    @BeforeAll
    void seedFixtures() throws Exception {
        RestAssuredMockMvc.mockMvc(mockMvc);
        stamp = String.valueOf(System.currentTimeMillis());
        titlePrefix = "S304-" + stamp + "-";

        storelessAdminToken = registerAndLogin("s304-admin-" + stamp + "@example.com", User.UserRole.ADMIN);
        String superEmail = "s304-super-" + stamp + "@example.com";
        superAdminToken = registerAndLogin(superEmail, User.UserRole.SUPER_ADMIN);
        superAdminUser = userRepository.findByEmail(superEmail).orElseThrow();

        systemTenant = tenantRepository.findById(SYSTEM_TENANT_ID).orElseThrow();
        store = tenantRepository.save(Tenant.builder()
                .name("S304 平台自營隔離測試店 " + stamp)
                .slug("s304-self-run-isolation-" + stamp)
                .contactEmail("s304-store-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        storeOwnerUser = userRepository.save(User.builder()
                .email("s304-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S304 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
        storeOwnerToken = jwtTokenService.generateAccessToken(
                storeOwnerUser.getId(), storeOwnerUser.getEmail(), "STORE_OWNER", store.getId().toString());
        User storeAdminUser = userRepository.save(User.builder()
                .email("s304-store-admin-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S304 店鋪 ADMIN")
                .role(User.UserRole.ADMIN)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
        storeAdminToken = jwtTokenService.generateAccessToken(
                storeAdminUser.getId(), storeAdminUser.getEmail(), "ADMIN", store.getId().toString());

        for (String key : TOGGLE_KEYS) {
            originalSystemToggles.put(key, readToggle(SYSTEM_TENANT_ID, key));
            setToggle(SYSTEM_TENANT_ID, key, true);
            setToggle(store.getId(), key, true);
        }
    }

    @AfterAll
    void cleanUp() {
        for (UUID listingId : jdbcTemplate.queryForList(
                "SELECT id FROM listings WHERE title LIKE ?", UUID.class, titlePrefix + "%")) {
            jdbcTemplate.update("DELETE FROM room_calendar WHERE room_listing_id = ?", listingId);
            jdbcTemplate.update("DELETE FROM pricing_rules WHERE room_listing_id = ? OR listing_id = ?",
                    listingId, listingId);
            jdbcTemplate.update("DELETE FROM product_inventory WHERE sku_id IN "
                    + "(SELECT id FROM product_skus WHERE product_listing_id = ?)", listingId);
            jdbcTemplate.update("DELETE FROM product_skus WHERE product_listing_id = ?", listingId);
            jdbcTemplate.update("DELETE FROM rooms WHERE listing_id = ?", listingId);
            jdbcTemplate.update("DELETE FROM products WHERE listing_id = ?", listingId);
            jdbcTemplate.update("DELETE FROM listings WHERE id = ?", listingId);
        }
        jdbcTemplate.update("DELETE FROM pricing_rules WHERE rule_name LIKE ?", titlePrefix + "%");
        originalSystemToggles.forEach((key, enabled) -> setToggle(SYSTEM_TENANT_ID, key, enabled));
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────── 房源（RoomService） ─────────────────────────

    @Test
    @DisplayName("更新房源：沒有店鋪的 ADMIN 改不了平台自營房源（標題不變）；SUPER_ADMIN、店主、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotUpdateSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "upd-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "upd-store");
        String hijacked = titlePrefix + "hijacked";

        forbidden(storelessAdminToken, "PUT", "/v2/rooms/" + systemRoom, "{\"title\":\"" + hijacked + "\"}");
        assertThat(listingTitle(systemRoom))
                .as("被擋之後平台自營房源的標題不得被改（不是只回 403 就算）")
                .isEqualTo(titlePrefix + "room-upd-sys");

        forbidden(storeOwnerToken, "PUT", "/v2/rooms/" + systemRoom, "{\"title\":\"" + hijacked + "\"}");
        assertThat(listingTitle(systemRoom)).as("店主改別人（系統租戶）的房源本來就擋").isEqualTo(titlePrefix + "room-upd-sys");

        send(superAdminToken, "PUT", "/v2/rooms/" + systemRoom, "{\"title\":\"" + titlePrefix + "by-super\"}").statusCode(200);
        assertThat(listingTitle(systemRoom)).as("SUPER_ADMIN 仍可管平台自營房源").isEqualTo(titlePrefix + "by-super");
        send(storeOwnerToken, "PUT", "/v2/rooms/" + storeRoom, "{\"title\":\"" + titlePrefix + "by-owner\"}").statusCode(200);
        assertThat(listingTitle(storeRoom)).isEqualTo(titlePrefix + "by-owner");
        send(storeAdminToken, "PUT", "/v2/rooms/" + storeRoom, "{\"title\":\"" + titlePrefix + "by-store-admin\"}").statusCode(200);
        assertThat(listingTitle(storeRoom))
                .as("有店鋪的 ADMIN 管自己店鋪的房源照常——守門看的是「有沒有店鋪」，不是角色名稱")
                .isEqualTo(titlePrefix + "by-store-admin");
    }

    @Test
    @DisplayName("刪除房源：沒有店鋪的 ADMIN 刪不了平台自營房源（狀態仍是 ACTIVE）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotDeleteSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "del-sys");
        UUID systemRoomForSuper = newRoom(systemTenant, superAdminUser, "del-sys-super");
        UUID storeRoom = newRoom(store, storeOwnerUser, "del-store");

        forbidden(storelessAdminToken, "DELETE", "/v2/rooms/" + systemRoom, null);
        assertThat(listingStatus(systemRoom)).as("被擋之後房源不得被軟刪除").isEqualTo("ACTIVE");
        forbidden(storeOwnerToken, "DELETE", "/v2/rooms/" + systemRoom, null);
        assertThat(listingStatus(systemRoom)).isEqualTo("ACTIVE");

        send(superAdminToken, "DELETE", "/v2/rooms/" + systemRoomForSuper, null).statusCode(200);
        assertThat(listingStatus(systemRoomForSuper)).as("SUPER_ADMIN 仍可刪平台自營房源").isEqualTo("DELETED");
        send(storeAdminToken, "DELETE", "/v2/rooms/" + storeRoom, null).statusCode(200);
        assertThat(listingStatus(storeRoom)).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("清除開放窗：沒有店鋪的 ADMIN 清不了平台自營房源的開放窗；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotClearOpenWindowOfSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "ow-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "ow-store");

        forbidden(storelessAdminToken, "DELETE", "/v2/rooms/" + systemRoom + "/open-window", null);
        assertThat(roomOpenUntil(systemRoom)).as("被擋之後開放窗不得被清掉").isNotNull();

        send(superAdminToken, "DELETE", "/v2/rooms/" + systemRoom + "/open-window", null).statusCode(200);
        assertThat(roomOpenUntil(systemRoom)).as("SUPER_ADMIN 仍可清平台自營房源的開放窗").isNull();
        send(storeAdminToken, "DELETE", "/v2/rooms/" + storeRoom + "/open-window", null).statusCode(200);
        assertThat(roomOpenUntil(storeRoom)).isNull();
    }

    @Test
    @DisplayName("建立房源：沒有店鋪的 ADMIN 不能建出平台自營房源（資料庫沒有新列）；SUPER_ADMIN 蓋系統租戶、有店鋪的 ADMIN 蓋自己店鋪")
    void storelessAdminCannotCreateRoom() {
        String blockedTitle = titlePrefix + "room-create-blocked";
        forbidden(storelessAdminToken, "POST", "/v2/rooms", roomBody(blockedTitle));
        assertThat(countListingsTitled(blockedTitle)).as("被擋之後不得留下任何房源").isZero();

        String bySuper = titlePrefix + "room-create-super";
        send(superAdminToken, "POST", "/v2/rooms", roomBody(bySuper)).statusCode(201);
        assertThat(listingTenantByTitle(bySuper)).as("SUPER_ADMIN 建的房源是平台自營（系統租戶）").isEqualTo(SYSTEM_TENANT_ID);
        String byStoreAdmin = titlePrefix + "room-create-store-admin";
        send(storeAdminToken, "POST", "/v2/rooms", roomBody(byStoreAdmin)).statusCode(201);
        assertThat(listingTenantByTitle(byStoreAdmin)).as("有店鋪的 ADMIN 建的房源蓋自己的店鋪").isEqualTo(store.getId());
    }

    @Test
    @DisplayName("Dashboard 統一建立：沒有店鋪的 ADMIN 建不出房源與商品；SUPER_ADMIN、有店鋪的 ADMIN 照常")
    void storelessAdminCannotCreateListingViaDashboard() {
        String blockedRoom = titlePrefix + "dash-room-blocked";
        String blockedProduct = titlePrefix + "dash-product-blocked";
        forbidden(storelessAdminToken, "POST", "/v2/dashboard/listings", dashboardBody("ROOM", blockedRoom));
        forbidden(storelessAdminToken, "POST", "/v2/dashboard/listings",
                dashboardBody("PRODUCT", blockedProduct));
        assertThat(countListingsTitled(blockedRoom) + countListingsTitled(blockedProduct))
                .as("被擋之後不得留下任何房源或商品").isZero();

        String superRoom = titlePrefix + "dash-room-super";
        String superProduct = titlePrefix + "dash-product-super";
        send(superAdminToken, "POST", "/v2/dashboard/listings", dashboardBody("ROOM", superRoom)).statusCode(200);
        send(superAdminToken, "POST", "/v2/dashboard/listings", dashboardBody("PRODUCT", superProduct)).statusCode(200);
        assertThat(listingTenantByTitle(superRoom)).isEqualTo(SYSTEM_TENANT_ID);
        assertThat(listingTenantByTitle(superProduct)).isEqualTo(SYSTEM_TENANT_ID);

        String storeAdminRoom = titlePrefix + "dash-room-store-admin";
        String storeAdminProduct = titlePrefix + "dash-product-store-admin";
        send(storeAdminToken, "POST", "/v2/dashboard/listings", dashboardBody("ROOM", storeAdminRoom)).statusCode(200);
        send(storeAdminToken, "POST", "/v2/dashboard/listings", dashboardBody("PRODUCT", storeAdminProduct)).statusCode(200);
        assertThat(listingTenantByTitle(storeAdminRoom)).isEqualTo(store.getId());
        assertThat(listingTenantByTitle(storeAdminProduct)).isEqualTo(store.getId());
    }

    // ───────────────────────── 商品與 SKU（ProductService、ProductSkuService） ─────────────────────────

    @Test
    @DisplayName("更新商品：沒有店鋪的 ADMIN 改不了平台自營商品（標題不變）；SUPER_ADMIN、店主、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotUpdateSystemProduct() {
        UUID systemProduct = newProduct(systemTenant, superAdminUser, "upd-sys");
        UUID storeProduct = newProduct(store, storeOwnerUser, "upd-store");
        String hijacked = titlePrefix + "hijacked";

        forbidden(storelessAdminToken, "PUT", "/v2/products/" + systemProduct,
                "{\"title\":\"" + hijacked + "\"}");
        assertThat(listingTitle(systemProduct)).as("被擋之後平台自營商品的標題不得被改")
                .isEqualTo(titlePrefix + "product-upd-sys");
        forbidden(storeOwnerToken, "PUT", "/v2/products/" + systemProduct,
                "{\"title\":\"" + hijacked + "\"}");
        assertThat(listingTitle(systemProduct)).isEqualTo(titlePrefix + "product-upd-sys");

        send(superAdminToken, "PUT", "/v2/products/" + systemProduct, "{\"title\":\"" + titlePrefix + "by-super\"}")
                .statusCode(200);
        assertThat(listingTitle(systemProduct)).as("SUPER_ADMIN 仍可管平台自營商品").isEqualTo(titlePrefix + "by-super");
        send(storeOwnerToken, "PUT", "/v2/products/" + storeProduct, "{\"title\":\"" + titlePrefix + "by-owner\"}")
                .statusCode(200);
        send(storeAdminToken, "PUT", "/v2/products/" + storeProduct, "{\"title\":\"" + titlePrefix + "by-store-admin\"}")
                .statusCode(200);
        assertThat(listingTitle(storeProduct)).as("有店鋪的 ADMIN 管自己店鋪的商品照常")
                .isEqualTo(titlePrefix + "by-store-admin");
    }

    @Test
    @DisplayName("刪除商品：沒有店鋪的 ADMIN 刪不了平台自營商品（狀態仍是 ACTIVE）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotDeleteSystemProduct() {
        UUID systemProduct = newProduct(systemTenant, superAdminUser, "del-sys");
        UUID systemProductForSuper = newProduct(systemTenant, superAdminUser, "del-sys-super");
        UUID storeProduct = newProduct(store, storeOwnerUser, "del-store");

        forbidden(storelessAdminToken, "DELETE", "/v2/products/" + systemProduct, null);
        assertThat(listingStatus(systemProduct)).as("被擋之後商品不得被軟刪除").isEqualTo("ACTIVE");
        forbidden(storeOwnerToken, "DELETE", "/v2/products/" + systemProduct, null);
        assertThat(listingStatus(systemProduct)).isEqualTo("ACTIVE");

        send(superAdminToken, "DELETE", "/v2/products/" + systemProductForSuper, null).statusCode(200);
        assertThat(listingStatus(systemProductForSuper)).as("SUPER_ADMIN 仍可刪平台自營商品").isEqualTo("DELETED");
        send(storeAdminToken, "DELETE", "/v2/products/" + storeProduct, null).statusCode(200);
        assertThat(listingStatus(storeProduct)).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("建立商品：沒有店鋪的 ADMIN 不能建出平台自營商品（資料庫沒有新列）；SUPER_ADMIN 蓋系統租戶、有店鋪的 ADMIN 蓋自己店鋪")
    void storelessAdminCannotCreateProduct() {
        String blockedTitle = titlePrefix + "product-create-blocked";
        forbidden(storelessAdminToken, "POST", "/v2/products", productBody(blockedTitle));
        assertThat(countListingsTitled(blockedTitle)).as("被擋之後不得留下任何商品").isZero();

        String bySuper = titlePrefix + "product-create-super";
        send(superAdminToken, "POST", "/v2/products", productBody(bySuper)).statusCode(201);
        assertThat(listingTenantByTitle(bySuper)).as("SUPER_ADMIN 建的商品是平台自營（系統租戶）").isEqualTo(SYSTEM_TENANT_ID);
        String byStoreAdmin = titlePrefix + "product-create-store-admin";
        send(storeAdminToken, "POST", "/v2/products", productBody(byStoreAdmin)).statusCode(201);
        assertThat(listingTenantByTitle(byStoreAdmin)).as("有店鋪的 ADMIN 建的商品蓋自己的店鋪").isEqualTo(store.getId());
    }

    @Test
    @DisplayName("新增 SKU：沒有店鋪的 ADMIN 對平台自營商品新增不了（資料庫沒有該 SKU）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotCreateSkuOfSystemProduct() {
        UUID systemProduct = newProduct(systemTenant, superAdminUser, "sku-create-sys");
        UUID storeProduct = newProduct(store, storeOwnerUser, "sku-create-store");
        String blockedCode = "S304-" + stamp + "-BLOCKED";

        forbidden(storelessAdminToken, "POST", "/v2/products/" + systemProduct + "/skus",
                "{\"skuCode\":\"" + blockedCode + "\",\"specName\":\"hijack\"}");
        assertThat(countSkus(blockedCode)).as("被擋之後不得留下任何 SKU").isZero();
        forbidden(storeOwnerToken, "POST", "/v2/products/" + systemProduct + "/skus",
                "{\"skuCode\":\"" + blockedCode + "\",\"specName\":\"hijack\"}");
        assertThat(countSkus(blockedCode)).isZero();

        String superCode = "S304-" + stamp + "-SUPER";
        send(superAdminToken, "POST", "/v2/products/" + systemProduct + "/skus",
                "{\"skuCode\":\"" + superCode + "\",\"specName\":\"ok\"}").statusCode(201);
        assertThat(countSkus(superCode)).as("SUPER_ADMIN 仍可管平台自營商品的 SKU").isEqualTo(1);
        String storeAdminCode = "S304-" + stamp + "-STORE-ADMIN";
        send(storeAdminToken, "POST", "/v2/products/" + storeProduct + "/skus",
                "{\"skuCode\":\"" + storeAdminCode + "\",\"specName\":\"ok\"}").statusCode(201);
        assertThat(countSkus(storeAdminCode)).isEqualTo(1);
    }

    @Test
    @DisplayName("更新 SKU：沒有店鋪的 ADMIN 改不了平台自營商品的 SKU（規格名稱不變）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotUpdateSkuOfSystemProduct() {
        UUID systemProduct = newProduct(systemTenant, superAdminUser, "sku-upd-sys");
        UUID storeProduct = newProduct(store, storeOwnerUser, "sku-upd-store");
        UUID systemSku = newSku(systemProduct, "S304-" + stamp + "-U-SYS");
        UUID storeSku = newSku(storeProduct, "S304-" + stamp + "-U-STORE");

        forbidden(storelessAdminToken, "PUT", "/v2/products/" + systemProduct + "/skus/" + systemSku,
                "{\"specName\":\"hijacked\"}");
        assertThat(skuSpecName(systemSku)).as("被擋之後 SKU 不得被改").isEqualTo("原始規格");
        forbidden(storeOwnerToken, "PUT", "/v2/products/" + systemProduct + "/skus/" + systemSku,
                "{\"specName\":\"hijacked\"}");
        assertThat(skuSpecName(systemSku)).isEqualTo("原始規格");

        send(superAdminToken, "PUT", "/v2/products/" + systemProduct + "/skus/" + systemSku,
                "{\"specName\":\"by-super\"}").statusCode(200);
        assertThat(skuSpecName(systemSku)).as("SUPER_ADMIN 仍可管平台自營商品的 SKU").isEqualTo("by-super");
        send(storeAdminToken, "PUT", "/v2/products/" + storeProduct + "/skus/" + storeSku,
                "{\"specName\":\"by-store-admin\"}").statusCode(200);
        assertThat(skuSpecName(storeSku)).isEqualTo("by-store-admin");
    }

    // ───────────────────────── 房態日曆維護（RoomCalendarService） ─────────────────────────

    @Test
    @DisplayName("標記維護：沒有店鋪的 ADMIN 對平台自營房源標不了（沒有任何日曆列變成 MAINTENANCE）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotMarkMaintenanceOnSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "mt-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "mt-store");
        String range = maintenanceBody(LocalDate.now().plusDays(40), LocalDate.now().plusDays(42));

        forbidden(storelessAdminToken, "POST", "/v2/dashboard/rooms/" + systemRoom + "/maintenance", range);
        assertThat(maintenanceDays(systemRoom)).as("被擋之後不得有任何日期被標成維護（否則房源當天就不可訂）").isZero();
        forbidden(storeOwnerToken, "POST", "/v2/dashboard/rooms/" + systemRoom + "/maintenance", range);
        assertThat(maintenanceDays(systemRoom)).isZero();

        send(superAdminToken, "POST", "/v2/dashboard/rooms/" + systemRoom + "/maintenance", range).statusCode(200);
        assertThat(maintenanceDays(systemRoom)).as("SUPER_ADMIN 仍可管平台自營房源的日曆").isEqualTo(2);
        send(storeAdminToken, "POST", "/v2/dashboard/rooms/" + storeRoom + "/maintenance", range).statusCode(200);
        assertThat(maintenanceDays(storeRoom)).isEqualTo(2);
    }

    @Test
    @DisplayName("解除維護：沒有店鋪的 ADMIN 對平台自營房源解除不了（維護中的日期仍是 MAINTENANCE）；SUPER_ADMIN、有店鋪的 ADMIN 照常")
    void storelessAdminCannotUnmarkMaintenanceOnSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "unmt-sys");
        LocalDate start = LocalDate.now().plusDays(50);
        LocalDate end = LocalDate.now().plusDays(52);
        send(superAdminToken, "POST", "/v2/dashboard/rooms/" + systemRoom + "/maintenance", maintenanceBody(start, end))
                .statusCode(200);
        assertThat(maintenanceDays(systemRoom)).as("前置：SUPER_ADMIN 先標好維護").isEqualTo(2);

        forbidden(storelessAdminToken, "DELETE",
                "/v2/dashboard/rooms/" + systemRoom + "/maintenance?startDate=" + start + "&endDate=" + end, null);
        assertThat(maintenanceDays(systemRoom)).as("被擋之後維護中的日期不得被解除").isEqualTo(2);

        send(superAdminToken, "DELETE",
                "/v2/dashboard/rooms/" + systemRoom + "/maintenance?startDate=" + start + "&endDate=" + end, null)
                .statusCode(200);
        assertThat(maintenanceDays(systemRoom)).as("SUPER_ADMIN 仍可解除").isZero();

        UUID storeRoom = newRoom(store, storeOwnerUser, "unmt-store");
        send(storeAdminToken, "POST", "/v2/dashboard/rooms/" + storeRoom + "/maintenance", maintenanceBody(start, end))
                .statusCode(200);
        send(storeAdminToken, "DELETE",
                "/v2/dashboard/rooms/" + storeRoom + "/maintenance?startDate=" + start + "&endDate=" + end, null)
                .statusCode(200);
        assertThat(maintenanceDays(storeRoom)).as("有店鋪的 ADMIN 對自己店鋪的房源標記與解除維護照常").isZero();
    }

    // ───────────────────────── 定價規則與日曆改價（PricingService） ─────────────────────────

    @Test
    @DisplayName("建立規則（綁房源）：沒有店鋪的 ADMIN 對平台自營房源植不了規則（資料庫沒有新列）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotCreateRuleOnSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "rule-create-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "rule-create-store");
        String blockedName = titlePrefix + "rule-blocked";

        forbidden(storelessAdminToken, "POST", RULES_URL, ruleBody(systemRoom, blockedName, 1));
        assertThat(countRulesNamed(blockedName))
                .as("被擋之後不得留下規則（規則會在買家結帳時被當成權威售價套用）").isZero();
        forbidden(storeOwnerToken, "POST", RULES_URL, ruleBody(systemRoom, blockedName, 1));
        assertThat(countRulesNamed(blockedName)).isZero();

        send(superAdminToken, "POST", RULES_URL, ruleBody(systemRoom, titlePrefix + "rule-super", 2)).statusCode(201);
        assertThat(countRulesNamed(titlePrefix + "rule-super")).as("SUPER_ADMIN 仍可管平台自營房源的規則").isEqualTo(1);
        send(storeAdminToken, "POST", RULES_URL, ruleBody(storeRoom, titlePrefix + "rule-store-admin", 3)).statusCode(201);
        assertThat(countRulesNamed(titlePrefix + "rule-store-admin")).isEqualTo(1);
    }

    @Test
    @DisplayName("建立規則（不綁房源）：沒有店鋪的 ADMIN 也建不出平台層級規則；SUPER_ADMIN、有店鋪的 ADMIN 照常")
    void storelessAdminCannotCreateUnboundRule() {
        String blockedName = titlePrefix + "rule-unbound-blocked";

        forbidden(storelessAdminToken, "POST", RULES_URL, ruleBody(null, blockedName, 4));
        assertThat(countRulesNamed(blockedName))
                .as("不綁房源的規則會蓋成系統租戶：守門不得因「沒有房源 id 可查」而被略過").isZero();

        send(superAdminToken, "POST", RULES_URL, ruleBody(null, titlePrefix + "rule-unbound-super", 5)).statusCode(201);
        assertThat(countRulesNamed(titlePrefix + "rule-unbound-super")).as("SUPER_ADMIN 仍可建立平台層級規則").isEqualTo(1);

        String storeAdminRule = titlePrefix + "rule-unbound-store-admin";
        send(storeAdminToken, "POST", RULES_URL, ruleBody(null, storeAdminRule, 6)).statusCode(201);
        assertThat(ruleTenantByName(storeAdminRule))
                .as("有店鋪的 ADMIN 建立的不綁房源規則蓋自己的店鋪：守門看的是有沒有店鋪，不是有沒有房源 id")
                .isEqualTo(store.getId());
    }

    @Test
    @DisplayName("更新規則：沒有店鋪的 ADMIN 改不了平台自營規則（名稱與優先序不變）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotUpdateSystemRule() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "rule-upd-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "rule-upd-store");
        UUID systemRule = newRule(systemTenant, systemRoom, titlePrefix + "rule-upd-sys");
        UUID storeRule = newRule(store, storeRoom, titlePrefix + "rule-upd-store");

        forbidden(storelessAdminToken, "PUT", RULES_URL + "/" + systemRule,
                "{\"ruleName\":\"hijacked\",\"priority\":99}");
        assertThat(ruleName(systemRule)).as("被擋之後規則名稱不得被改").isEqualTo(titlePrefix + "rule-upd-sys");
        assertThat(rulePriority(systemRule)).as("被擋之後規則優先序不得被改").isEqualTo(5);
        forbidden(storeOwnerToken, "PUT", RULES_URL + "/" + systemRule, "{\"ruleName\":\"hijacked\"}");
        assertThat(ruleName(systemRule)).isEqualTo(titlePrefix + "rule-upd-sys");

        send(superAdminToken, "PUT", RULES_URL + "/" + systemRule, "{\"priority\":7}").statusCode(200);
        assertThat(rulePriority(systemRule)).as("SUPER_ADMIN 仍可管平台自營規則").isEqualTo(7);
        send(storeAdminToken, "PUT", RULES_URL + "/" + storeRule, "{\"priority\":8}").statusCode(200);
        assertThat(rulePriority(storeRule)).isEqualTo(8);
    }

    @Test
    @DisplayName("停用規則：沒有店鋪的 ADMIN 停用不了平台自營規則（仍是啟用中）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotDeactivateSystemRule() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "rule-del-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "rule-del-store");
        UUID systemRule = newRule(systemTenant, systemRoom, titlePrefix + "rule-del-sys");
        UUID systemRuleForSuper = newRule(systemTenant, systemRoom, titlePrefix + "rule-del-sys-super");
        UUID storeRule = newRule(store, storeRoom, titlePrefix + "rule-del-store");

        forbidden(storelessAdminToken, "DELETE", RULES_URL + "/" + systemRule, null);
        assertThat(ruleActive(systemRule)).as("被擋之後規則不得被停用").isTrue();
        forbidden(storeOwnerToken, "DELETE", RULES_URL + "/" + systemRule, null);
        assertThat(ruleActive(systemRule)).isTrue();

        send(superAdminToken, "DELETE", RULES_URL + "/" + systemRuleForSuper, null).statusCode(200);
        assertThat(ruleActive(systemRuleForSuper)).as("SUPER_ADMIN 仍可停用平台自營規則").isFalse();
        send(storeAdminToken, "DELETE", RULES_URL + "/" + storeRule, null).statusCode(200);
        assertThat(ruleActive(storeRule)).isFalse();
    }

    @Test
    @DisplayName("覆蓋價格：沒有店鋪的 ADMIN 對平台自營規則覆蓋不了（沒有新的 MANUAL_OVERRIDE 規則）；SUPER_ADMIN、有店鋪的 ADMIN 管自己的照常")
    void storelessAdminCannotOverridePriceOfSystemRule() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "ovr-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "ovr-store");
        UUID systemRule = newRule(systemTenant, systemRoom, titlePrefix + "ovr-rule-sys");
        UUID storeRule = newRule(store, storeRoom, titlePrefix + "ovr-rule-store");
        String body = "{\"startDate\":\"" + LocalDate.now().plusDays(60) + "\",\"endDate\":\"" + LocalDate.now().plusDays(61)
                + "\",\"overridePrice\":1.00,\"reason\":\"" + titlePrefix + "override\"}";

        forbidden(storelessAdminToken, "POST", RULES_URL + "/" + systemRule + "/override", body);
        assertThat(countManualOverrides(systemRoom))
                .as("被擋之後不得有覆蓋規則（把售價壓成 1 元會在買家結帳時被套用）").isZero();
        forbidden(storeOwnerToken, "POST", RULES_URL + "/" + systemRule + "/override", body);
        assertThat(countManualOverrides(systemRoom)).isZero();

        send(superAdminToken, "POST", RULES_URL + "/" + systemRule + "/override", body).statusCode(200);
        assertThat(countManualOverrides(systemRoom)).as("SUPER_ADMIN 仍可覆蓋平台自營規則").isEqualTo(1);
        send(storeAdminToken, "POST", RULES_URL + "/" + storeRule + "/override", body).statusCode(200);
        assertThat(countManualOverrides(storeRoom)).isEqualTo(1);
    }

    @Test
    @DisplayName("日曆改價：沒有店鋪的 ADMIN 對平台自營房源改不了單日價格（沒有新的 MANUAL_OVERRIDE 規則）；SUPER_ADMIN、有店鋪的 ADMIN 照常")
    void storelessAdminCannotSetCalendarPriceOfSystemRoom() {
        UUID systemRoom = newRoom(systemTenant, superAdminUser, "cal-sys");
        UUID storeRoom = newRoom(store, storeOwnerUser, "cal-store");
        String date = LocalDate.now().plusDays(70).toString();

        forbidden(storelessAdminToken, "POST", CALENDAR_PRICE_URL, calendarPriceBody(systemRoom, date));
        assertThat(countManualOverrides(systemRoom)).as("被擋之後不得有改價規則").isZero();
        forbidden(storeOwnerToken, "POST", CALENDAR_PRICE_URL, calendarPriceBody(systemRoom, date));
        assertThat(countManualOverrides(systemRoom)).isZero();

        send(superAdminToken, "POST", CALENDAR_PRICE_URL, calendarPriceBody(systemRoom, date)).statusCode(200);
        assertThat(countManualOverrides(systemRoom)).as("SUPER_ADMIN 仍可改平台自營房源的單日價格").isEqualTo(1);
        send(storeAdminToken, "POST", CALENDAR_PRICE_URL, calendarPriceBody(storeRoom, date)).statusCode(200);
        assertThat(countManualOverrides(storeRoom)).isEqualTo(1);
    }

    // ───────────────────────── 請求與斷言輔助 ─────────────────────────

    private MockMvcRequestSpecification authenticated(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON_VALUE);
    }

    private ValidatableMockMvcResponse send(String token, String method, String url, String body) {
        MockMvcRequestSpecification spec = authenticated(token);
        if (body != null) {
            spec = spec.body(body);
        }
        return switch (method) {
            case "POST" -> spec.when().post(url).then();
            case "PUT" -> spec.when().put(url).then();
            case "DELETE" -> spec.when().delete(url).then();
            default -> throw new IllegalArgumentException(method);
        };
    }

    /**
     * 403＋E-1007。注意回應訊息<b>分不出</b>是權限註解還是擁有權檢查擋下的：{@code BusinessException} 的細節依 AI-2418
     * 設計不外洩，回應一律是錯誤碼的預設訊息「權限不足」，與權限註解的 AccessDenied 回應相同。所以「擋下的是擁有權檢查」
     * 由<b>同角色的對照組</b>證明：有店鋪的 ADMIN 對自己店鋪的資源做同一個操作成功（權限註解對 ADMIN 放行），
     * 兩個 ADMIN 之間只差有沒有店鋪。
     */
    private void forbidden(String token, String method, String url, String body) {
        send(token, method, url, body)
                .statusCode(403)
                .body("code", equalTo("E-1007"));
    }

    private static String roomBody(String title) {
        return "{\"title\":\"" + title + "\",\"location\":\"Taipei\",\"basePrice\":2000.00,\"maxGuests\":2}";
    }

    private static String productBody(String title) {
        return "{\"title\":\"" + title + "\",\"category\":\"test\",\"basePrice\":199.00}";
    }

    private static String dashboardBody(String listingType, String name) {
        return "{\"listingType\":\"" + listingType + "\",\"name\":\"" + name + "\",\"price\":199.00,"
                + "\"category\":\"test\",\"location\":\"Taipei\"}";
    }

    private static String maintenanceBody(LocalDate start, LocalDate end) {
        return "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }

    private static String calendarPriceBody(UUID roomListingId, String date) {
        return "{\"roomListingId\":\"" + roomListingId + "\",\"date\":\"" + date + "\",\"price\":1.00,\"reason\":\"s304\"}";
    }

    /** 每條規則用不同的有效期間，避免同類型時間重疊檢查把對照組的建立擋下（與要驗的守門無關）。 */
    private static String ruleBody(UUID roomListingId, String name, int offsetYears) {
        LocalDate from = LocalDate.now().plusYears(10L + offsetYears);
        return "{" + (roomListingId != null ? "\"roomListingId\":\"" + roomListingId + "\"," : "")
                + "\"ruleType\":\"SEASONAL\",\"ruleName\":\"" + name + "\",\"priority\":1,"
                + "\"config\":{\"multiplier\":1.1},\"validFrom\":\"" + from + "\",\"validTo\":\"" + from.plusDays(3) + "\"}";
    }

    // ───────────────────────── 固件 ─────────────────────────

    /** Listing 與 Room／Product 要在同一個交易裡存（{@code @MapsId} 會對 Listing 串聯 persist，跨交易會是 detached）。 */
    private UUID newRoom(Tenant tenant, User owner, String suffix) {
        return transactionTemplate.execute(status -> {
            Listing listing = listingRepository.save(Listing.builder().tenant(tenant).owner(owner)
                    .listingType(Listing.ListingType.ROOM).title(titlePrefix + "room-" + suffix)
                    .basePrice(new BigDecimal("2000.00")).status(Listing.ListingStatus.ACTIVE).build());
            roomRepository.save(Room.builder().listing(listing).location("Taipei").maxGuests(2)
                    .openUntilDate(LocalDate.now().plusDays(365)).build());
            return listing.getId();
        });
    }

    private UUID newProduct(Tenant tenant, User owner, String suffix) {
        return transactionTemplate.execute(status -> {
            Listing listing = listingRepository.save(Listing.builder().tenant(tenant).owner(owner)
                    .listingType(Listing.ListingType.PRODUCT).title(titlePrefix + "product-" + suffix)
                    .basePrice(new BigDecimal("199.00")).status(Listing.ListingStatus.ACTIVE).build());
            productRepository.save(Product.builder().listing(listing).category("test").build());
            return listing.getId();
        });
    }

    private UUID newSku(UUID productListingId, String skuCode) {
        Listing listing = listingRepository.findById(productListingId).orElseThrow();
        return productSkuRepository.save(ProductSku.builder().listing(listing).skuCode(skuCode)
                .specName("原始規格").status("ACTIVE").build()).getId();
    }

    private UUID newRule(Tenant tenant, UUID roomListingId, String name) {
        return pricingRuleRepository.save(PricingRule.builder()
                .tenant(tenant)
                .roomListingId(roomListingId)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName(name)
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build()).getId();
    }

    // ───────────────────────── 直接查資料庫（不看回應） ─────────────────────────

    private String listingTitle(UUID listingId) {
        return jdbcTemplate.queryForObject("SELECT title FROM listings WHERE id = ?", String.class, listingId);
    }

    private String listingStatus(UUID listingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, listingId);
    }

    private int countListingsTitled(String title) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listings WHERE title = ?", Integer.class, title);
    }

    private UUID listingTenantByTitle(String title) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM listings WHERE title = ?", UUID.class, title);
    }

    private LocalDate roomOpenUntil(UUID listingId) {
        return jdbcTemplate.queryForObject(
                "SELECT open_until_date FROM rooms WHERE listing_id = ?", LocalDate.class, listingId);
    }

    private int countSkus(String skuCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_skus WHERE sku_code = ?", Integer.class, skuCode);
    }

    private String skuSpecName(UUID skuId) {
        return jdbcTemplate.queryForObject("SELECT spec_name FROM product_skus WHERE id = ?", String.class, skuId);
    }

    private int maintenanceDays(UUID roomListingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_calendar WHERE room_listing_id = ? AND status = 'MAINTENANCE'",
                Integer.class, roomListingId);
    }

    private int countRulesNamed(String ruleName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pricing_rules WHERE rule_name = ?", Integer.class, ruleName);
    }

    private UUID ruleTenantByName(String ruleName) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM pricing_rules WHERE rule_name = ?", UUID.class, ruleName);
    }

    private String ruleName(UUID ruleId) {
        return jdbcTemplate.queryForObject("SELECT rule_name FROM pricing_rules WHERE id = ?", String.class, ruleId);
    }

    private int rulePriority(UUID ruleId) {
        return jdbcTemplate.queryForObject("SELECT priority FROM pricing_rules WHERE id = ?", Integer.class, ruleId);
    }

    private boolean ruleActive(UUID ruleId) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT is_active FROM pricing_rules WHERE id = ?", Boolean.class, ruleId));
    }

    private int countManualOverrides(UUID roomListingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pricing_rules WHERE room_listing_id = ? AND rule_type = 'MANUAL_OVERRIDE'",
                Integer.class, roomListingId);
    }

    // ───────────────────────── 帳號與功能開關 ─────────────────────────

    /**
     * 註冊並登入。{@code promoteTo} 註冊後直接在資料庫改角色再登入——簽發的 token 與租戶是真實登入流程算出來的，
     * 不是測試手工組的（沒有店鋪的 ADMIN 與 SUPER_ADMIN 的租戶都是系統租戶佔位值）。
     */
    private String registerAndLogin(String email, User.UserRole promoteTo) throws Exception {
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(PASSWORD).userType("BUYER").build())
                .when().post("/v2/auth/register")
                .then().statusCode(201);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(promoteTo);
        userRepository.save(user);
        String loginResponse = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(PASSWORD).build())
                .when().post("/v2/auth/login")
                .then().statusCode(200).extract().asString();
        JsonNode data = objectMapper.readTree(loginResponse).path("data");
        assertThat(data.path("user").path("tenantId").asText())
                .as("沒有店鋪的 %s 登入簽發的租戶是系統租戶佔位值", promoteTo)
                .isEqualTo(SYSTEM_TENANT_ID.toString());
        return data.path("accessToken").asText();
    }

    private boolean readToggle(UUID tenantId, String key) {
        List<Boolean> rows = jdbcTemplate.queryForList(
                "SELECT is_enabled FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Boolean.class, tenantId, key);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    private void setToggle(UUID tenantId, String key, boolean enabled) {
        int updated = jdbcTemplate.update(
                "UPDATE tenant_feature_toggles SET is_enabled = ? WHERE tenant_id = ? AND feature_key = ?",
                enabled, tenantId, key);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO tenant_feature_toggles "
                    + "(id, tenant_id, feature_key, is_enabled, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    UUID.randomUUID(), tenantId, key, enabled);
        }
    }
}
