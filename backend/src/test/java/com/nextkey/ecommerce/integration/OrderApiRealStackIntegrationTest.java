package com.nextkey.ecommerce.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * M05 訂單 API 對真實服務的契約（Sprint 243；真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>為什麼要真實服務：{@code API_M05_Order.md}（v2.0）的每個回應形狀、狀態碼、錯誤碼與權限宣稱都來自這裡的實測，不是讀碼推論。
 * Sprint 243 寫規格時以同樣的探針先實測，發現三個使用者可見的缺陷——① 建立訂單／訂房／合併結帳的回應（與冪等重放）
 * 的 {@code tenantId}／{@code userId}／{@code items[].listingId} 是 null（影子欄位，{@code DEF-338}）；
 * ② {@code sortBy}／{@code sortDir} 亂填回 500（{@code DEF-339}）；③ 訂房入住＝退房建出 0 晚 0 元的訂房（{@code DEF-340}）——
 * 既有的 {@code BuyerOrderJourneyE2ETest} 之類都把 Redis 換成 mock 或只檢查 id，看不到。本類別守住修復與文件描述的行為。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-ORDER-API: M05 訂單 API 對真實服務的契約（Sprint 243）")
class OrderApiRealStackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisCartService cartService;

    private ContractFixture fx;

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        cartService.clearCart(fx.buyer.getId(), ContractFixture.SYSTEM_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        cartService.clearCart(fx.buyer.getId(), ContractFixture.SYSTEM_TENANT_ID);
    }

    // ── 建立訂單 ─────────────────────────────────────────────

    @Test
    @DisplayName("建立訂單 201：回應帶店鋪、買家與商品的 id（DEF-338：原本是 null），與 GET 詳情一致；購物車被清掉")
    void createOrder_returnsTheStoreBuyerAndListingIds() throws Exception {
        addProduct(2);

        JsonNode created = json(post("/v2/orders").content(body(orderRequest())), fx.buyerToken, 201);

        org.assertj.core.api.Assertions.assertThat(created.at("/data/tenantId").asText())
                .as("訂單歸屬商品所屬的店鋪，不是買家的系統租戶").isEqualTo(fx.store.getId().toString());
        org.assertj.core.api.Assertions.assertThat(created.at("/data/userId").asText()).isEqualTo(fx.buyer.getId().toString());
        org.assertj.core.api.Assertions.assertThat(created.at("/data/items/0/listingId").asText()).isEqualTo(fx.productId.toString());
        org.assertj.core.api.Assertions.assertThat(created.at("/data/status").asText()).isEqualTo("CREATED");
        org.assertj.core.api.Assertions.assertThat(created.at("/data/orderType").asText()).isEqualTo("PRODUCT");
        org.assertj.core.api.Assertions.assertThat(created.at("/data/totalAmount").decimalValue()).isEqualByComparingTo("200");
        org.assertj.core.api.Assertions.assertThat(created.at("/data/currency").asText()).isEqualTo("TWD");
        org.assertj.core.api.Assertions.assertThat(created.at("/data/items/0/quantity").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(created.at("/data/items/0/listingTitle").asText()).isEqualTo("契約測試商品");

        String orderId = created.at("/data/id").asText();
        get("/v2/orders/" + orderId, fx.buyerToken)
                .andExpect(jsonPath("$.data.tenantId").value(created.at("/data/tenantId").asText()))
                .andExpect(jsonPath("$.data.userId").value(created.at("/data/userId").asText()))
                .andExpect(jsonPath("$.data.items[0].listingId").value(created.at("/data/items/0/listingId").asText()));
        org.assertj.core.api.Assertions.assertThat(cartService.getCart(fx.buyer.getId(), ContractFixture.SYSTEM_TENANT_ID).getItems())
                .as("已結帳的商品從購物車移除").isEmpty();
    }

    @Test
    @DisplayName("Idempotency-Key：第一次 201、重送 200 且回同一張訂單（含非 null 的 tenantId）、不重複建立；格式不是 UUID v4 → 400 E-9004")
    void createOrder_idempotencyKey() throws Exception {
        addProduct(1);
        String key = UUID.randomUUID().toString();

        JsonNode first = json(post("/v2/orders").header("Idempotency-Key", key).content(body(orderRequest())), fx.buyerToken, 201);
        JsonNode replay = json(post("/v2/orders").header("Idempotency-Key", key).content(body(orderRequest())), fx.buyerToken, 200);

        org.assertj.core.api.Assertions.assertThat(replay.at("/data/id").asText()).isEqualTo(first.at("/data/id").asText());
        org.assertj.core.api.Assertions.assertThat(replay.at("/data/tenantId").asText()).isEqualTo(fx.store.getId().toString());
        get("/v2/orders", fx.buyerToken).andExpect(jsonPath("$.data.totalElements").value(1));

        send(post("/v2/orders").header("Idempotency-Key", "not-a-uuid").content(body(orderRequest())), fx.buyerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9004"));
    }

    @Test
    @DisplayName("建立訂單的錯誤：購物車是空的 → 400 E-5004；orderType 不是 PRODUCT／ROOM → 422 E-3001；缺 orderType → 400 E-9000（帶 errors[]）")
    void createOrder_errors() throws Exception {
        send(post("/v2/orders").content(body(orderRequest())), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-5004"));
        send(post("/v2/orders").content(body(Map.of("orderType", "BOGUS"))), fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-3001"));
        send(post("/v2/orders").content(body(Map.of("shippingAddress", "x"))), fx.buyerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9000"))
                .andExpect(jsonPath("$.errors[0].field").value("orderType"));
        send(post("/v2/orders").content(body(orderRequest())), null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("E-1000"));
    }

    // ── 列表與詳情 ───────────────────────────────────────────

    @Test
    @DisplayName("買家訂單列表：Spring Page 形狀；size 上限 100；sortBy／sortDir 亂填 → 400 E-9000（DEF-339：原本 500）；允許的欄位照常排序")
    void list_shapePagingAndSorting() throws Exception {
        createOrder(1);
        String second = createOrder(2);

        get("/v2/orders", fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.content[0].id").value(second))
                .andExpect(jsonPath("$.data.content[0].orderType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.data.content[0].shippingRecipientName").value("測試收件人"))
                .andExpect(jsonPath("$.data.content[0].createdAt", notNullValue()));
        get("/v2/orders?size=1000", fx.buyerToken).andExpect(jsonPath("$.data.size").value(100));
        // 允許的 sortBy／sortDir 回 200。注意：repository 方法名寫死 OrderByCreatedAtDesc，Pageable 的排序只是次要排序，
        // 所以這兩個參數目前實際上不改變順序（DEF-342）——這裡刻意不斷言順序，免得把缺陷固定成正常行為
        get("/v2/orders?sortBy=totalAmount&sortDir=asc", fx.buyerToken).andExpect(status().isOk());

        get("/v2/orders?sortBy=bogus", fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        get("/v2/orders?sortDir=SIDEWAYS", fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        // 實體上真的存在、但不在允許清單的欄位（含個資）也不能拿來排序，不是只擋「不存在的欄位」
        for (String notAllowed : new String[] {"shippingPhone", "shippingAddress", "userId", "notes"}) {
            get("/v2/orders?sortBy=" + notAllowed, fx.buyerToken)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        }
        get("/v2/orders/tenant?sortBy=bogus", fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
    }

    @Test
    @DisplayName("訂單詳情：本人與該店鋪的店主可讀；別的買家 403 E-1007；不存在 404 E-5000；沒登入 401")
    void detail_authorization() throws Exception {
        String orderId = createOrder(1);

        get("/v2/orders/" + orderId, fx.buyerToken).andExpect(status().isOk());
        get("/v2/orders/" + orderId, fx.ownerToken).andExpect(status().isOk());
        get("/v2/orders/" + orderId, fx.adminToken).andExpect(status().isOk());
        get("/v2/orders/" + orderId, fx.otherToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
        get("/v2/orders/" + UUID.randomUUID(), fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-5000"));
        get("/v2/orders/" + orderId, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("店家訂單列表：店主看得到自己店鋪的訂單；沒有店鋪的買家是空頁（不查詢、不報錯）；status 篩選；不認得的 status → 422 E-5001")
    void tenantList() throws Exception {
        String orderId = createOrder(1);

        get("/v2/orders/tenant", fx.ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId));
        get("/v2/orders/tenant?status=PAID", fx.ownerToken).andExpect(jsonPath("$.data.totalElements").value(0));
        get("/v2/orders/tenant?status=CREATED", fx.ownerToken).andExpect(jsonPath("$.data.totalElements").value(1));
        get("/v2/orders/tenant?status=BOGUS", fx.ownerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-5001"));
        get("/v2/orders/tenant", fx.buyerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── 狀態更新、取消與日誌 ─────────────────────────────────

    @Test
    @DisplayName("PATCH /status：不合法的轉換、PAID／REFUNDING／不認得的狀態一律 422 E-5001；缺 targetStatus 400 E-9000；買家 403；付款後店主可推進 CONFIRMED→SHIPPING")
    void patchStatus() throws Exception {
        String orderId = createOrder(1);
        String url = "/v2/orders/" + orderId + "/status";

        for (String target : new String[] {"CONFIRMED", "PAID", "REFUNDING", "BOGUS"}) {
            send(patch(url).content(body(Map.of("targetStatus", target))), fx.ownerToken)
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-5001"));
        }
        send(patch(url).content(body(Map.of())), fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        send(patch(url).content(body(Map.of("targetStatus", "CANCELLED"))), fx.buyerToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));

        send(post("/v2/orders/" + orderId + "/pay"), fx.buyerToken).andExpect(status().isOk());
        send(patch(url).content(body(Map.of("targetStatus", "CONFIRMED"))), fx.ownerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        send(patch(url).content(body(Map.of("targetStatus", "SHIPPING", "reason", "出貨"))), fx.ownerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SHIPPING"));

        get("/v2/orders/" + orderId + "/logs", fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$.data[3].fromStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[3].toStatus").value("SHIPPING"))
                .andExpect(jsonPath("$.data[3].reason").value("出貨"));
        get("/v2/orders/" + orderId + "/logs", fx.otherToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
    }

    @Test
    @DisplayName("取消：只有訂單本人或管理員（店主要用 PATCH status → CANCELLED）；已取消或已出貨 → 400 E-5002；不存在 404 E-5000")
    void cancel() throws Exception {
        String orderId = createOrder(1);

        send(post("/v2/orders/" + orderId + "/cancel"), fx.ownerToken).andExpect(status().isForbidden());
        send(post("/v2/orders/" + orderId + "/cancel"), fx.otherToken).andExpect(status().isForbidden());
        send(post("/v2/orders/" + orderId + "/cancel?reason=changed"), fx.buyerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        send(post("/v2/orders/" + orderId + "/cancel"), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-5002"));
        send(post("/v2/orders/" + UUID.randomUUID() + "/cancel"), fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-5000"));

        String shipped = createOrder(1);
        send(post("/v2/orders/" + shipped + "/pay"), fx.buyerToken).andExpect(status().isOk());
        patchTo(shipped, "CONFIRMED");
        patchTo(shipped, "SHIPPING");
        send(post("/v2/orders/" + shipped + "/cancel"), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-5002"));
    }

    // ── 付款 ─────────────────────────────────────────────────

    @Test
    @DisplayName("付款狀態與 Mock 付款：形狀（canPay／canCancel／canRefund／paymentProvider／storeOpen）、付款成功、重複付款 422 E-5011、別人付款 403、付款失敗只記錄、Stripe 未啟用 400 E-6002")
    void payment() throws Exception {
        String orderId = createOrder(1);
        String base = "/v2/orders/" + orderId;

        get(base + "/payment", fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.canPay").value(true))
                .andExpect(jsonPath("$.data.canCancel").value(true))
                .andExpect(jsonPath("$.data.canRefund").value(false))
                .andExpect(jsonPath("$.data.paymentProvider").value("mock"))
                .andExpect(jsonPath("$.data.storeOpen").value(true))
                .andExpect(jsonPath("$.data.nextValidStates").value("PAID,CANCELLED"))
                .andExpect(jsonPath("$.data.paymentId").value(org.hamcrest.Matchers.nullValue()));

        send(post(base + "/pay"), fx.otherToken).andExpect(status().isForbidden());
        send(post(base + "/pay/fail?reason=card_declined"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.canPay").value(true));
        send(post(base + "/pay/checkout"), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-6002"));
        send(post(base + "/pay"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.canPay").value(false))
                .andExpect(jsonPath("$.data.canRefund").value(true))
                .andExpect(jsonPath("$.data.paidAt", notNullValue()))
                .andExpect(jsonPath("$.data.nextValidStates").value("CONFIRMED,CANCELLED"));
        send(post(base + "/pay"), fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-5011"));
    }

    @Test
    @DisplayName("退款 POST /refund：買家（沒有 order:update）與店主（不是訂單本人）都 403 E-1007，實際上只有管理員能退；部分退款 PARTIALLY_REFUNDED；超額／超過兩位小數 422 E-6009")
    void refund_isEffectivelyAdminOnly() throws Exception {
        String orderId = createOrder(2);
        String base = "/v2/orders/" + orderId;
        send(post(base + "/pay"), fx.buyerToken).andExpect(status().isOk());

        send(post(base + "/refund"), fx.buyerToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
        send(post(base + "/refund"), fx.ownerToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
        send(post(base + "/refund?amount=50&reason=partial"), fx.adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.refundedAmount").value(50.0));
        send(post(base + "/refund?amount=999"), fx.adminToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-6009"));
        send(post(base + "/refund?amount=1.005"), fx.adminToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-6009"));
        send(post(base + "/refund"), fx.adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundedAmount").value(200.0));
    }

    // ── 合併結帳 ─────────────────────────────────────────────

    @Test
    @DisplayName("合併結帳 POST /v2/checkout/mixed：同時建立訂單與訂房（回應帶非 null 的 id，DEF-338）；缺 guestName 400 E-9000；購物車空 400 E-5004")
    void mixedCheckout() throws Exception {
        addProduct(1);
        cartService.addItem(fx.buyer.getId(), ContractFixture.SYSTEM_TENANT_ID, CartDto.AddItemRequest.builder()
                .listingId(fx.roomId).quantity(1)
                .startDate(java.time.LocalDate.now().plusDays(40)).endDate(java.time.LocalDate.now().plusDays(42)).build());
        Map<String, Object> request = new java.util.LinkedHashMap<>(orderRequest());
        request.remove("orderType");
        request.put("guestCount", 2);

        send(post("/v2/checkout/mixed").content(body(Map.of("guestCount", 2))), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("guestName"));
        request.put("guestName", "合併住客");
        send(post("/v2/checkout/mixed").content(body(request)), fx.buyerToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order.status").value("CREATED"))
                .andExpect(jsonPath("$.data.order.tenantId").value(fx.store.getId().toString()))
                .andExpect(jsonPath("$.data.order.items[0].listingId").value(fx.productId.toString()))
                .andExpect(jsonPath("$.data.booking.status").value("CREATED"))
                .andExpect(jsonPath("$.data.booking.tenantId").value(fx.store.getId().toString()))
                .andExpect(jsonPath("$.data.booking.roomListingId").value(fx.roomId.toString()))
                .andExpect(jsonPath("$.data.booking.nightsCount").value(2))
                .andExpect(jsonPath("$.data.totalDiscountAmount").value(0));
        send(post("/v2/checkout/mixed").content(body(request)), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-5004"));
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private Map<String, Object> orderRequest() {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("orderType", "PRODUCT");
        request.put("shippingAddress", "台北市信義區測試路 1 號");
        request.put("shippingRecipientName", "測試收件人");
        request.put("shippingPhone", "0912345678");
        return request;
    }

    private void addProduct(final int quantity) {
        cartService.addItem(fx.buyer.getId(), ContractFixture.SYSTEM_TENANT_ID,
                CartDto.AddItemRequest.builder().listingId(fx.productId).quantity(quantity).build());
    }

    /** 把 {@code quantity} 件商品放進購物車並結帳，回傳訂單 id（金額 = quantity × 100）。 */
    private String createOrder(final int quantity) throws Exception {
        addProduct(quantity);
        return json(post("/v2/orders").content(body(orderRequest())), fx.buyerToken, 201).at("/data/id").asText();
    }

    private void patchTo(final String orderId, final String target) throws Exception {
        send(patch("/v2/orders/" + orderId + "/status").content(body(Map.of("targetStatus", target))), fx.ownerToken)
                .andExpect(status().isOk());
    }

    private String body(final Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private ResultActions get(final String url, final String token) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token);
    }

    private ResultActions send(final MockHttpServletRequestBuilder builder, final String token) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private JsonNode json(final MockHttpServletRequestBuilder builder, final String token, final int expectedStatus)
            throws Exception {
        ResultActions result = send(builder, token).andExpect(status().is(expectedStatus));
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
