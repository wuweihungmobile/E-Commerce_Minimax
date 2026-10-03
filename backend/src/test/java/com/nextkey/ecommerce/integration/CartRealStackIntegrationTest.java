package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * 購物車對真實服務的契約：錯誤輸入的回應與回應形狀（Sprint 241；真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>為什麼要真實後端：{@code CartControllerE2ETest} 用的 {@code IntegrationTestConfiguration} 把 {@code RedisCartService} 換成
 * 記憶體版的 mock，mock 不會丟出真實服務的例外——Sprint 241 撰寫 API 規格時發現 {@code RedisCartService.addItem} 對三種使用者輸入錯誤
 * （刊登項目不存在、房源沒給日期、退房不晚於入住）丟的是通用的 {@link IllegalArgumentException}，{@code GlobalExceptionHandler} 沒有
 * 專屬處理，落入 {@code handleGenericException}，回 HTTP 500 {@code E-9900「發生未預期的錯誤」}，還在日誌留一筆 ERROR。
 * 使用者的輸入錯誤不該是 5xx。本類別走真實服務，守住它們各自對應的 4xx 與錯誤碼；也驗證 {@code API_M04_Cart.md}（v2.0）
 * 描述的回應形狀（空購物車、依店鋪分組、總件數），避免文件與實作再度漂移。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-CART-REAL: 購物車對真實服務的契約——錯誤輸入的回應與回應形狀（Sprint 241）")
class CartRealStackIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String ITEMS_URL = "/v2/cart/items";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private RedisCartService cartService;

    private UUID buyerId;
    private String buyerToken;
    private UUID storeId;
    private String storeName;
    private UUID roomId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("購物車輸入錯誤測試店 " + stamp).slug("cart-input-" + stamp)
                .contactEmail("cart-input-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        storeId = store.getId();
        storeName = store.getName();
        User owner = userRepository.save(User.builder()
                .email("cart-input-owner-" + stamp + "@example.com").passwordHash("dummy").fullName("店主")
                .role(User.UserRole.STORE_OWNER).status("ACTIVE").tenantId(store.getId()).build());
        roomId = newListing(store, owner, Listing.ListingType.ROOM, "輸入錯誤測試房間", "1000.00");
        productId = newListing(store, owner, Listing.ListingType.PRODUCT, "輸入錯誤測試商品", "100.00");

        buyerId = UUID.randomUUID();
        buyerToken = jwtTokenService.generateAccessToken(
                buyerId, "cart-input-buyer-" + stamp + "@example.com", "BUYER", SYSTEM_TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        cartService.clearCart(buyerId, SYSTEM_TENANT_ID);
    }

    @Test
    @DisplayName("加入不存在的刊登項目 → 404 E-3000（不是 500 E-9900）")
    void addItem_nonexistentListing_is404() throws Exception {
        addItem(Map.of("listingId", UUID.randomUUID(), "quantity", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E-3000"));
    }

    @Test
    @DisplayName("房源沒給日期 → 400 E-4003（不是 500 E-9900）")
    void addItem_roomWithoutDates_is400() throws Exception {
        addItem(Map.of("listingId", roomId, "quantity", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-4003"));
    }

    @Test
    @DisplayName("退房日期不晚於入住日期 → 400 E-4004（不是 500 E-9900）")
    void addItem_roomWithCheckOutNotAfterCheckIn_is400() throws Exception {
        addItem(Map.of("listingId", roomId, "quantity", 1, "startDate", "2031-03-10", "endDate", "2031-03-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-4004"));
    }

    @Test
    @DisplayName("對照：合法的商品與房源（含日期）照常加入，守門沒有擋到正常輸入")
    void addItem_validInput_succeeds() throws Exception {
        addItem(Map.of("listingId", productId, "quantity", 2)).andExpect(status().isOk());
        addItem(Map.of("listingId", roomId, "quantity", 1, "startDate", "2031-03-10", "endDate", "2031-03-12"))
                .andExpect(status().isOk());
    }

    // ── 回應形狀（API_M04_Cart.md v2.0 §3.2、§4.1、§4.2） ─────────────────────

    @Test
    @DisplayName("空購物車：items 空、總件數與金額 0、stores 空陣列、運費／折扣／應付金額 0")
    void getCart_empty_hasZeroTotalsAndNoStores() throws Exception {
        mockMvc.perform(get("/v2/cart").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.stores").isEmpty())
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.finalAmount").value(0))
                .andExpect(jsonPath("$.data.currency").value("TWD"));
    }

    @Test
    @DisplayName("單一店鋪：項目帶店鋪欄位（storeId／storeName／storeActive）、stores[] 依店鋪分組、頂層合計與該店鋪一致")
    void getCart_singleStore_groupsItemsByStore() throws Exception {
        addItem(Map.of("listingId", productId, "quantity", 2)).andExpect(status().isOk());

        mockMvc.perform(get("/v2/cart").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].storeId").value(storeId.toString()))
                .andExpect(jsonPath("$.data.items[0].storeName").value(storeName))
                .andExpect(jsonPath("$.data.items[0].storeActive").value(true))
                .andExpect(jsonPath("$.data.items[0].cartItemKey").value(productId.toString()))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(200.0))
                .andExpect(jsonPath("$.data.stores.length()").value(1))
                .andExpect(jsonPath("$.data.stores[0].storeId").value(storeId.toString()))
                .andExpect(jsonPath("$.data.stores[0].storeActive").value(true))
                .andExpect(jsonPath("$.data.stores[0].itemCount").value(2))
                .andExpect(jsonPath("$.data.stores[0].totalAmount").value(200.0));
    }

    @Test
    @DisplayName("GET /cart/count 是總件數（各項目數量的總和），不是項目數")
    void getCount_isTotalQuantity() throws Exception {
        addItem(Map.of("listingId", productId, "quantity", 2)).andExpect(status().isOk());
        addItem(Map.of("listingId", roomId, "quantity", 1, "startDate", "2031-03-10", "endDate", "2031-03-12"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/cart/count").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    @DisplayName("重複加入同一個刊登項目：累加數量、只有一個項目；ROOM 的日期以最後一次為準（cartItemKey 不含日期）")
    void addItem_sameListingAgain_accumulatesQuantity() throws Exception {
        addItem(Map.of("listingId", roomId, "quantity", 1, "startDate", "2031-03-10", "endDate", "2031-03-12"))
                .andExpect(status().isOk());
        addItem(Map.of("listingId", roomId, "quantity", 2, "startDate", "2031-04-01", "endDate", "2031-04-03"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/cart").header("Authorization", "Bearer " + buyerToken))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].startDate").value("2031-04-01"))
                .andExpect(jsonPath("$.data.items[0].endDate").value("2031-04-03"));
    }

    private ResultActions addItem(final Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(ITEMS_URL)
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 房源要同時設關聯（寫入資料庫的是 tenant／owner 關聯，tenantId／ownerId 只是唯讀影子欄位）。 */
    private UUID newListing(final Tenant store, final User owner, final Listing.ListingType type,
                            final String title, final String price) {
        return listingRepository.save(Listing.builder()
                .tenant(store).owner(owner).listingType(type).title(title)
                .basePrice(new BigDecimal(price)).currency("TWD").status(Listing.ListingStatus.ACTIVE).build()).getId();
    }
}
