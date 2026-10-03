package com.nextkey.ecommerce.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * M06 訂房 API 對真實服務的契約（Sprint 243；真實 PostgreSQL＋真實 Redis（日曆鎖）＋完整 HTTP／JWT／權限鏈）。
 *
 * <p>{@code API_M06_Booking.md}（v2.0）的回應形狀、狀態碼、錯誤碼與權限宣稱都來自這裡的實測。Sprint 243 寫規格時以同樣的探針
 * 先實測，發現：建立訂房／合併結帳的回應 {@code tenantId}／{@code userId}／{@code roomListingId} 是 null（{@code DEF-338}）；
 * 入住日＝退房日的請求回 201，建出 {@code nightsCount} 0、{@code totalAmount} 0 的訂房（{@code DEF-340}，購物車對同一情形回 E-4004）；
 * 列表的 {@code sortBy}／{@code sortDir} 亂填回 500（{@code DEF-339}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-BOOKING-API: M06 訂房 API 對真實服務的契約（Sprint 243）")
class BookingApiRealStackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ContractFixture fx;

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
    }

    // ── 可用性與日曆 ─────────────────────────────────────────

    @Test
    @DisplayName("可用性 GET /availability：可訂（晚數、總價）；訂走後 available=false＋BOOKED；退房不晚於入住 → 200 available=false＋INVALID_DATE_RANGE（不是錯誤）；非房源 422 E-3001；不存在 404 E-4000；缺參數 400 E-9005；日期格式不對 400 E-9000")
    void availability() throws Exception {
        String ci = day(30);
        String co = day(32);
        String url = "/v2/bookings/availability?roomListingId=" + fx.roomId + "&checkInDate=" + ci + "&checkOutDate=";

        get(url + co, fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.nightsCount").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(2000.0))
                .andExpect(jsonPath("$.data.currency").value("TWD"))
                .andExpect(jsonPath("$.data.unavailableReason").value(org.hamcrest.Matchers.nullValue()));
        get("/v2/bookings/availability?roomListingId=" + fx.roomId + "&checkInDate=" + co + "&checkOutDate=" + ci, fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason").value("INVALID_DATE_RANGE"));
        get("/v2/bookings/availability?roomListingId=" + fx.roomId + "&checkInDate=" + ci + "&checkOutDate=" + ci, fx.buyerToken)
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason").value("INVALID_DATE_RANGE"));

        book(ci, co, 2, fx.buyerToken);
        get(url + co, fx.otherToken)
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason").value("BOOKED"));

        get("/v2/bookings/availability?roomListingId=" + fx.productId + "&checkInDate=" + ci + "&checkOutDate=" + co, fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-3001"));
        get("/v2/bookings/availability?roomListingId=" + UUID.randomUUID() + "&checkInDate=" + ci + "&checkOutDate=" + co, fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-4000"));
        get("/v2/bookings/availability?roomListingId=" + fx.roomId, fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9005"));
        get("/v2/bookings/availability?roomListingId=" + fx.roomId + "&checkInDate=abc&checkOutDate=" + co, fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
    }

    @Test
    @DisplayName("日曆 GET /calendar：只回「已有記錄」的日期（訂走的是 BOOKED＋bookingId）；區間過大（>92 天）或結束早於開始 → 422 E-3001（沿用的錯誤碼，預設訊息是「無效的刊登類型」，DEF-341）")
    void calendar() throws Exception {
        String ci = day(30);
        String co = day(32);
        String url = "/v2/bookings/calendar?roomListingId=" + fx.roomId + "&startDate=" + ci + "&endDate=" + co;

        get(url, fx.buyerToken).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        String bookingId = book(ci, co, 2, fx.buyerToken).at("/data/id").asText();
        get(url, fx.buyerToken)
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].date").value(ci))
                .andExpect(jsonPath("$.data[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.data[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.data[0].price").value(1000.0));

        get("/v2/bookings/calendar?roomListingId=" + fx.roomId + "&startDate=" + ci + "&endDate=" + day(200), fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-3001"));
        get("/v2/bookings/calendar?roomListingId=" + fx.roomId + "&startDate=" + co + "&endDate=" + ci, fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-3001"));
    }

    // ── 建立 ─────────────────────────────────────────────────

    @Test
    @DisplayName("建立訂房 201：回應帶店鋪、買家與房源的 id（DEF-338：原本是 null），與 GET 詳情一致；狀態 CREATED、付款期限約 24 小時、退款進度 NONE、入住 15:00／退房 11:00")
    void create_returnsTheStoreBuyerAndRoomIds() throws Exception {
        JsonNode created = book(day(30), day(32), 2, fx.buyerToken);

        Assertions.assertThat(created.at("/data/tenantId").asText()).isEqualTo(fx.store.getId().toString());
        Assertions.assertThat(created.at("/data/userId").asText()).isEqualTo(fx.buyer.getId().toString());
        Assertions.assertThat(created.at("/data/roomListingId").asText()).isEqualTo(fx.roomId.toString());
        Assertions.assertThat(created.at("/data/status").asText()).isEqualTo("CREATED");
        Assertions.assertThat(created.at("/data/nightsCount").asInt()).isEqualTo(2);
        Assertions.assertThat(created.at("/data/totalAmount").decimalValue()).isEqualByComparingTo("2000");
        Assertions.assertThat(created.at("/data/refundStatus").asText()).isEqualTo("NONE");
        Assertions.assertThat(created.at("/data/checkInTime").asText()).isEqualTo("15:00:00");
        Assertions.assertThat(created.at("/data/checkOutTime").asText()).isEqualTo("11:00:00");
        Assertions.assertThat(created.at("/data/roomTitle").asText()).isEqualTo("契約測試房間");
        Duration untilDue = Duration.between(Instant.now(), Instant.parse(created.at("/data/paymentDueAt").asText()));
        Assertions.assertThat(untilDue).as("付款期限約 24 小時後").isBetween(Duration.ofHours(23), Duration.ofHours(25));

        String bookingId = created.at("/data/id").asText();
        get("/v2/bookings/" + bookingId, fx.buyerToken)
                .andExpect(jsonPath("$.data.tenantId").value(created.at("/data/tenantId").asText()))
                .andExpect(jsonPath("$.data.userId").value(created.at("/data/userId").asText()))
                .andExpect(jsonPath("$.data.roomListingId").value(created.at("/data/roomListingId").asText()));
    }

    @Test
    @DisplayName("建立訂房的錯誤：日期被訂走 400 E-4001；人數超過上限 400 E-4005；退房不晚於入住（含同一天，DEF-340：原本 201）400 E-4003；過去的入住日／缺欄位 400 E-9000（帶 errors[]）；不是房源 422 E-3001；不存在 404 E-4000")
    void create_errors() throws Exception {
        book(day(30), day(32), 2, fx.buyerToken);

        send(post("/v2/bookings").content(body(bookingRequest(day(31), day(33), 2))), fx.otherToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4001"));
        send(post("/v2/bookings").content(body(bookingRequest(day(50), day(51), 3))), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4005"));
        send(post("/v2/bookings").content(body(bookingRequest(day(60), day(60), 2))), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4003"));
        send(post("/v2/bookings").content(body(bookingRequest(day(62), day(61), 2))), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4003"));
        send(post("/v2/bookings").content(body(bookingRequest(LocalDate.now().minusDays(3).toString(), day(51), 2))), fx.buyerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9000"))
                .andExpect(jsonPath("$.errors[0].field").value("checkInDate"));
        send(post("/v2/bookings").content(body(Map.of("roomListingId", fx.roomId))), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        Map<String, Object> notRoom = bookingRequest(day(70), day(71), 2);
        notRoom.put("roomListingId", fx.productId);
        send(post("/v2/bookings").content(body(notRoom)), fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-3001"));
        notRoom.put("roomListingId", UUID.randomUUID());
        send(post("/v2/bookings").content(body(notRoom)), fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-4000"));

        get("/v2/bookings", fx.buyerToken).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Idempotency-Key：重送回 200 同一筆訂房（含非 null 的 tenantId）、不重複建立；格式不是 UUID v4 → 400 E-9004")
    void create_idempotencyKey() throws Exception {
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = bookingRequest(day(30), day(31), 2);

        JsonNode first = json(post("/v2/bookings").header("Idempotency-Key", key).content(body(request)), fx.buyerToken, 201);
        JsonNode replay = json(post("/v2/bookings").header("Idempotency-Key", key).content(body(request)), fx.buyerToken, 200);

        Assertions.assertThat(replay.at("/data/id").asText()).isEqualTo(first.at("/data/id").asText());
        Assertions.assertThat(replay.at("/data/tenantId").asText()).isEqualTo(fx.store.getId().toString());
        get("/v2/bookings", fx.buyerToken).andExpect(jsonPath("$.data.totalElements").value(1));
        send(post("/v2/bookings").header("Idempotency-Key", "not-a-uuid").content(body(request)), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9004"));
    }

    // ── 列表、詳情與更新 ─────────────────────────────────────

    @Test
    @DisplayName("買家訂房列表：Page 形狀（含 nightsCount、guestName）；sortBy／sortDir 亂填 → 400 E-9000（DEF-339：原本 500）；店家列表 GET /v2/dashboard/bookings：店主看得到、沒有店鋪的買家是空頁")
    void listsAndDashboard() throws Exception {
        String bookingId = book(day(30), day(32), 2, fx.buyerToken).at("/data/id").asText();

        get("/v2/bookings", fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.content[0].roomListingId").value(fx.roomId.toString()))
                .andExpect(jsonPath("$.data.content[0].nightsCount").value(2))
                .andExpect(jsonPath("$.data.content[0].guestName").value("契約住客"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.size").value(20));
        get("/v2/bookings?sortBy=bogus", fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        get("/v2/bookings?sortDir=SIDEWAYS", fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        // 實體上真的存在、但不在允許清單的欄位（含個資）也不能拿來排序，不是只擋「不存在的欄位」
        for (String notAllowed : new String[] {"guestEmail", "guestPhone", "guestName", "specialRequests", "userId"}) {
            get("/v2/bookings?sortBy=" + notAllowed, fx.buyerToken)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));
        }
        get("/v2/dashboard/bookings?sortBy=bogus", fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-9000"));

        get("/v2/dashboard/bookings", fx.ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(bookingId));
        get("/v2/dashboard/bookings", fx.buyerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("訂房詳情：本人、該店鋪的店主與管理員可讀；別的買家 403 E-1007；不存在 404 E-4006")
    void detail_authorization() throws Exception {
        String bookingId = book(day(30), day(32), 2, fx.buyerToken).at("/data/id").asText();

        get("/v2/bookings/" + bookingId, fx.buyerToken).andExpect(status().isOk());
        get("/v2/bookings/" + bookingId, fx.ownerToken).andExpect(status().isOk());
        get("/v2/bookings/" + bookingId, fx.adminToken).andExpect(status().isOk());
        get("/v2/bookings/" + bookingId, fx.otherToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
        get("/v2/bookings/" + UUID.randomUUID(), fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-4006"));
    }

    @Test
    @DisplayName("更新 PUT：買家沒有 booking:update（403）、店主可改住客資料與人數；人數超過上限 E-4005；改成被訂走的日期 E-4001；改成 0 晚 E-4003（DEF-340）；付款後不能再改 422 E-5010")
    void update() throws Exception {
        String bookingId = book(day(30), day(32), 2, fx.buyerToken).at("/data/id").asText();
        book(day(40), day(42), 1, fx.otherToken);
        String url = "/v2/bookings/" + bookingId;

        send(put(url).content(body(Map.of("guestName", "新名字"))), fx.buyerToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-1007"));
        send(put(url).content(body(Map.of("guestName", "店主改的", "guestCount", 1))), fx.ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guestName").value("店主改的"))
                .andExpect(jsonPath("$.data.guestCount").value(1))
                .andExpect(jsonPath("$.data.totalAmount").value(2000.0));
        send(put(url).content(body(Map.of("guestCount", 9))), fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4005"));
        send(put(url).content(body(Map.of("checkInDate", day(40), "checkOutDate", day(42)))), fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4001"));
        send(put(url).content(body(Map.of("checkInDate", day(31), "checkOutDate", day(31)))), fx.ownerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4003"));

        send(post(url + "/pay"), fx.buyerToken).andExpect(status().isOk());
        send(put(url).content(body(Map.of("guestName", "付款後改"))), fx.ownerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-5010"));
    }

    // ── 取消與退款 ───────────────────────────────────────────

    @Test
    @DisplayName("取消 POST /cancel：未付款 → CANCELLED、不退款（NONE）、日期釋放可再訂；已取消 400 E-4007；不存在 404 E-4006；別的買家 403")
    void cancel_unpaid() throws Exception {
        String ci = day(30);
        String co = day(32);
        String bookingId = book(ci, co, 2, fx.buyerToken).at("/data/id").asText();
        String url = "/v2/bookings/" + bookingId + "/cancel";

        send(post(url), fx.otherToken).andExpect(status().isForbidden());
        send(post(url + "?reason=changed"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId").value(bookingId))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.canceledBy").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.refundStatus").value("NONE"))
                .andExpect(jsonPath("$.data.refundAmount").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.canceledAt", notNullValue()));
        send(post(url), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-4007"));
        send(post("/v2/bookings/" + UUID.randomUUID() + "/cancel"), fx.buyerToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("E-4006"));
        book(ci, co, 2, fx.otherToken); // 日期已釋放，別人可以訂
    }

    @Test
    @DisplayName("取消已付款訂房（PRD Q14）：買家在入住前 ≥24 小時取消 → 全額退款（PENDING，等排程退回）；入住不足 24 小時 → 不退款（NONE）；店主代為取消（MERCHANT）一律全額退款")
    void cancel_paid_refundPolicy() throws Exception {
        String early = book(day(30), day(32), 2, fx.buyerToken).at("/data/id").asText();
        send(post("/v2/bookings/" + early + "/pay"), fx.buyerToken).andExpect(status().isOk());
        send(post("/v2/bookings/" + early + "/cancel"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceledBy").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.refundStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.refundAmount").value(2000.0));

        String late = book(day(0), day(1), 2, fx.buyerToken).at("/data/id").asText();
        send(post("/v2/bookings/" + late + "/pay"), fx.buyerToken).andExpect(status().isOk());
        send(post("/v2/bookings/" + late + "/cancel"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("NONE"))
                .andExpect(jsonPath("$.data.refundAmount").value(org.hamcrest.Matchers.nullValue()));

        String byMerchant = book(day(0), day(1), 2, fx.otherToken).at("/data/id").asText();
        send(post("/v2/bookings/" + byMerchant + "/pay"), fx.otherToken).andExpect(status().isOk());
        send(post("/v2/bookings/" + byMerchant + "/cancel"), fx.ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceledBy").value("MERCHANT"))
                .andExpect(jsonPath("$.data.refundStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.refundAmount").value(1000.0));
    }

    // ── 付款 ─────────────────────────────────────────────────

    @Test
    @DisplayName("訂房付款：狀態（走 GET /v2/orders/bookings/{id}/payment）形狀；Mock 付款成功；重複付款 422 E-5011；Stripe 未啟用 400 E-6002；別人付款 403")
    void payment() throws Exception {
        String bookingId = book(day(30), day(32), 2, fx.buyerToken).at("/data/id").asText();

        get("/v2/orders/bookings/" + bookingId + "/payment", fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(bookingId))
                .andExpect(jsonPath("$.data.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.canPay").value(true))
                .andExpect(jsonPath("$.data.nextValidStates").value("PAID,CANCELLED"))
                .andExpect(jsonPath("$.data.paymentProvider").value("mock"))
                .andExpect(jsonPath("$.data.storeOpen").value(true));
        send(post("/v2/bookings/" + bookingId + "/pay"), fx.otherToken).andExpect(status().isForbidden());
        send(post("/v2/bookings/" + bookingId + "/pay/checkout"), fx.buyerToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-6002"));
        send(post("/v2/bookings/" + bookingId + "/pay"), fx.buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.canPay").value(false))
                .andExpect(jsonPath("$.data.nextValidStates").value("CONFIRMED,CANCELLED"));
        send(post("/v2/bookings/" + bookingId + "/pay"), fx.buyerToken)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("E-5011"));
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private static String day(final int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow).toString();
    }

    private Map<String, Object> bookingRequest(final String checkIn, final String checkOut, final int guests) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("roomListingId", fx.roomId);
        request.put("checkInDate", checkIn);
        request.put("checkOutDate", checkOut);
        request.put("guestCount", guests);
        request.put("guestName", "契約住客");
        request.put("guestPhone", "0912345678");
        request.put("guestEmail", "guest@example.com");
        return request;
    }

    private JsonNode book(final String checkIn, final String checkOut, final int guests, final String token) throws Exception {
        return json(post("/v2/bookings").content(body(bookingRequest(checkIn, checkOut, guests))), token, 201);
    }

    private String body(final Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private ResultActions get(final String url, final String token) throws Exception {
        return send(MockMvcRequestBuilders.get(url), token);
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
        ResultActions result = send(builder, token);
        Assertions.assertThat(result.andReturn().getResponse().getStatus())
                .as("回應：%s", result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
