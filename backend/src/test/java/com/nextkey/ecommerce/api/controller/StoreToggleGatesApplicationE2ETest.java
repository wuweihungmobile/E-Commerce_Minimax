package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 規則只在「房源／商品所屬店鋪」的動態定價開關開啟時套用（Sprint 309，DEF-424，使用者以互動選項裁決，
 * 真實 PostgreSQL，完整 HTTP＋JWT＋權限鏈）。
 *
 * <p>原本店鋪自己的 {@code DYNAMIC_PRICING_ENABLED} 只管「建立規則」；套用規則時查的是<b>呼叫者租戶</b>的開關。購物車與訂房的使用者
 * （{@code BUYER}）都是沒有店鋪的人，租戶是系統租戶佔位值，它的開關在 Flyway 種子（V1、V6）是開啟，於是管理員關閉某店鋪的開關後，
 * 它既有的規則照樣套用在消費者身上。現在由定價引擎統一以房源／商品所屬店鋪的開關把關。
 *
 * <p>⚠️ <b>同一個語意在兩種資料庫的前提不同</b>：Flyway 資料庫的系統租戶開關是開啟（種子），Hibernate 測試庫的系統租戶開關是
 * <b>關閉</b>（{@code TestDatabaseInitializer}）。所以本類別<b>不依賴系統租戶的開關值</b>，更不去改這個共用的列；改用三種呼叫者
 * 對兩家店鋪比價——呼叫者租戶的開關開啟、關閉（兩個我控制開關的租戶），以及真實消費者的樣子（系統租戶）。
 * 結論必須與呼叫者租戶的開關無關：開關開啟的店鋪對三種呼叫者都套用規則，開關關閉的店鋪對三種呼叫者都回基準價。
 *
 * <p>兩間店鋪各有一間房源、一個商品與一條生效中的規則，開關一開一關。三條消費者看得到價格的路徑都要依<b>店鋪的開關</b>而不同：
 * 商品有效售價、房間價格試算（房間詳情頁）、訂房可用性報價。購物車與訂單金額在 Redis 上，由 Playwright 的
 * {@code at-pricing-cart-checkout-real.spec.ts} 驗證（這裡的整合設定把 Redis 換成 mock）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 309（DEF-424）: 規則只在房源／商品所屬店鋪的動態定價開關開啟時套用")
class StoreToggleGatesApplicationE2ETest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String PRICING_TOGGLE = "DYNAMIC_PRICING_ENABLED";
    private static final BigDecimal PRODUCT_BASE = new BigDecimal("100.00");
    private static final BigDecimal PRODUCT_RULE_PRICE = new BigDecimal("80.00");
    private static final BigDecimal ROOM_BASE = new BigDecimal("1500.00");
    private static final BigDecimal ROOM_RULE_PRICE = new BigDecimal("1200.00");
    private static final String PRODUCT_RULE_NAME = "S309 商品規則";
    private static final String ROOM_RULE_NAME = "S309 房源規則";

    private final LocalDate today = LocalDate.now();
    private final LocalDate checkIn = today.plusDays(3);
    private final LocalDate checkOut = today.plusDays(5); // 2 晚

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private Tenant storeOn;
    private Tenant storeOff;
    private String ownerOnToken;
    private String ownerOffToken;
    /** 呼叫者標籤 → access token。三種呼叫者的租戶開關狀態不同（其中系統租戶的開關值依資料庫而異，本類別不依賴）。 */
    private Map<String, String> callers;
    private String systemConsumerToken;

    private UUID productOn;
    private UUID productOff;
    private UUID roomOn;
    private UUID roomOff;

    @BeforeAll
    void seed() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        long stamp = System.currentTimeMillis();

        storeOn = seedStore("on", stamp, true);
        storeOff = seedStore("off", stamp, false);
        User ownerOn = seedUser(storeOn.getId(), User.UserRole.STORE_OWNER, "owner-on", stamp);
        User ownerOff = seedUser(storeOff.getId(), User.UserRole.STORE_OWNER, "owner-off", stamp);
        ownerOnToken = token(ownerOn, "STORE_OWNER", storeOn.getId());
        ownerOffToken = token(ownerOff, "STORE_OWNER", storeOff.getId());

        // 三種呼叫者：租戶開關開啟、租戶開關關閉（兩個我控制開關的租戶），以及真實消費者（系統租戶）
        Tenant callerOnTenant = seedStore("caller-on", stamp, true);
        Tenant callerOffTenant = seedStore("caller-off", stamp, false);
        callers = new LinkedHashMap<>();
        callers.put("呼叫者租戶開關開啟", buyerToken(callerOnTenant.getId(), "caller-on", stamp));
        callers.put("呼叫者租戶開關關閉", buyerToken(callerOffTenant.getId(), "caller-off", stamp));
        systemConsumerToken = buyerToken(SYSTEM_TENANT_ID, "system", stamp);
        callers.put("沒有店鋪的消費者（系統租戶）", systemConsumerToken);

        productOn = seedListing(storeOn, ownerOn, Listing.ListingType.PRODUCT, "商品 ON", PRODUCT_BASE);
        productOff = seedListing(storeOff, ownerOff, Listing.ListingType.PRODUCT, "商品 OFF", PRODUCT_BASE);
        roomOn = seedListing(storeOn, ownerOn, Listing.ListingType.ROOM, "房源 ON", ROOM_BASE);
        roomOff = seedListing(storeOff, ownerOff, Listing.ListingType.ROOM, "房源 OFF", ROOM_BASE);

        // 規則直接寫入（建立端點會依開關擋下開關關閉的店鋪）：兩間店鋪各有一條生效中的規則
        seedProductRule(storeOn, productOn);
        seedProductRule(storeOff, productOff);
        seedRoomRule(storeOn, roomOn);
        seedRoomRule(storeOff, roomOff);
    }

    @AfterEach
    void restoreToggles() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
        setToggle(storeOn, true);
        setToggle(storeOff, false);
    }

    @AfterAll
    void cleanUp() {
        SecurityContextHolder.clearContext();
        pricingRuleRepository.deleteAll(pricingRuleRepository.findByTenantId(storeOn.getId()));
        pricingRuleRepository.deleteAll(pricingRuleRepository.findByTenantId(storeOff.getId()));
        listingRepository.deleteAllById(List.of(productOn, productOff, roomOn, roomOff));
    }

    // ── 三條消費者看得到價格的路徑（對三種呼叫者都一樣）────────────────────────────

    @Test
    @DisplayName("商品有效售價（effective-price）：開關開啟的店鋪套用規則，開關關閉的店鋪回基準價——與呼叫者租戶的開關無關")
    void productEffectivePrice_followsTheStoresToggle() throws Exception {
        for (Map.Entry<String, String> caller : callers.entrySet()) {
            JsonNode on = effectivePrice(caller.getValue(), productOn);
            JsonNode off = effectivePrice(caller.getValue(), productOff);

            assertThat(on.path("effectivePrice").decimalValue()).as(caller.getKey() + "：開關開啟的店鋪")
                    .isEqualByComparingTo(PRODUCT_RULE_PRICE);
            assertThat(on.path("appliedRuleName").asText()).isEqualTo(PRODUCT_RULE_NAME);
            assertThat(off.path("effectivePrice").decimalValue()).as(caller.getKey() + "：開關關閉的店鋪，規則不套用")
                    .isEqualByComparingTo(PRODUCT_BASE);
            assertThat(off.path("appliedRuleName").isNull() || off.path("appliedRuleName").isMissingNode()).isTrue();
        }
    }

    @Test
    @DisplayName("房間價格試算（listings/{id}/price，房間詳情頁用）：開關開啟的店鋪套用規則，開關關閉的店鋪回基準價——與呼叫者租戶的開關無關")
    void roomPricePreview_followsTheStoresToggle() throws Exception {
        for (Map.Entry<String, String> caller : callers.entrySet()) {
            JsonNode on = roomPrice(caller.getValue(), roomOn);
            JsonNode off = roomPrice(caller.getValue(), roomOff);

            assertThat(on.path("baseTotal").decimalValue()).isEqualByComparingTo("3000");
            assertThat(on.path("adjustedTotal").decimalValue()).as(caller.getKey() + "：開關開啟的店鋪")
                    .isEqualByComparingTo("2400"); // 1200 × 2 晚
            assertThat(off.path("baseTotal").decimalValue()).isEqualByComparingTo("3000");
            assertThat(off.path("adjustedTotal").decimalValue()).as(caller.getKey() + "：開關關閉的店鋪，規則不套用")
                    .isEqualByComparingTo("3000");
        }
    }

    @Test
    @DisplayName("訂房可用性報價（bookings/availability）：開關開啟的店鋪報調整後總價與規則名，開關關閉的店鋪報原價、沒有調整資訊——與呼叫者租戶的開關無關")
    void bookingAvailabilityQuote_followsTheStoresToggle() throws Exception {
        for (Map.Entry<String, String> caller : callers.entrySet()) {
            JsonNode on = availability(caller.getValue(), roomOn);
            JsonNode off = availability(caller.getValue(), roomOff);

            assertThat(on.path("available").asBoolean()).isTrue();
            assertThat(on.path("totalPrice").decimalValue()).as(caller.getKey() + "：開關開啟的店鋪")
                    .isEqualByComparingTo("2400");
            assertThat(on.path("originalTotalPrice").decimalValue()).isEqualByComparingTo("3000");
            assertThat(on.path("appliedRuleName").asText()).isEqualTo(ROOM_RULE_NAME);
            assertThat(on.path("priceAdjustmentType").asText()).isEqualTo("DISCOUNT");

            assertThat(off.path("available").asBoolean()).isTrue();
            assertThat(off.path("totalPrice").decimalValue()).as(caller.getKey() + "：開關關閉的店鋪，報價就是原價")
                    .isEqualByComparingTo("3000");
            assertThat(off.path("originalTotalPrice").isNull()).isTrue();
            assertThat(off.path("appliedRuleName").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("店主自己的價格試算（dashboard/pricing/calculate）也一致：自己店鋪的開關關閉時，預覽就是基準價")
    void ownerPreview_followsTheStoresToggle() throws Exception {
        assertThat(ownerCalculate(ownerOnToken, roomOn).path("adjustedTotal").decimalValue())
                .isEqualByComparingTo("2400");
        assertThat(ownerCalculate(ownerOffToken, roomOff).path("adjustedTotal").decimalValue())
                .as("店主的預覽與消費者看到的一致，不會預覽一個不會被收的折扣價")
                .isEqualByComparingTo("3000");
    }

    // ── 切換立即生效、可逆 ────────────────────────────────────────────────────

    @Test
    @DisplayName("管理員關閉開關立即生效、重新開啟立即恢復（沒有殘留的快取）；開啟原本關閉的店鋪，它的規則開始套用")
    void togglingTakesEffectImmediatelyAndIsReversible() throws Exception {
        String consumer = systemConsumerToken;
        assertThat(effectivePrice(consumer, productOn).path("effectivePrice").decimalValue())
                .isEqualByComparingTo(PRODUCT_RULE_PRICE);

        // 撤銷店鋪 ON 的開關：它既有的規則馬上對消費者失效
        setToggle(storeOn, false);
        assertThat(effectivePrice(consumer, productOn).path("effectivePrice").decimalValue())
                .isEqualByComparingTo(PRODUCT_BASE);
        assertThat(roomPrice(consumer, roomOn).path("adjustedTotal").decimalValue()).isEqualByComparingTo("3000");
        assertThat(availability(consumer, roomOn).path("totalPrice").decimalValue()).isEqualByComparingTo("3000");

        // 重新開啟：同一條規則立刻恢復（規則本身沒有被刪除或停用）
        setToggle(storeOn, true);
        assertThat(effectivePrice(consumer, productOn).path("effectivePrice").decimalValue())
                .isEqualByComparingTo(PRODUCT_RULE_PRICE);
        assertThat(roomPrice(consumer, roomOn).path("adjustedTotal").decimalValue()).isEqualByComparingTo("2400");

        // 開啟原本關閉的店鋪：它的規則開始套用——證明決定因素就是「該店鋪自己的開關」
        setToggle(storeOff, true);
        assertThat(effectivePrice(consumer, productOff).path("effectivePrice").decimalValue())
                .isEqualByComparingTo(PRODUCT_RULE_PRICE);
        assertThat(availability(consumer, roomOff).path("totalPrice").decimalValue()).isEqualByComparingTo("2400");
    }

    // ── 日曆手動改價也受店鋪開關約束（Sprint 311，DEF-426，使用者以互動選項裁決）──────────────

    @Test
    @DisplayName("DEF-426：日曆手動改價（calendar/price、rules/{id}/override）在店主店鋪的動態定價開關關閉時回 403 E-2004、不建立規則；"
            + "同一位店主、同一個房源只翻轉開關就恢復 200")
    void calendarManualOverrideEndpoints_followTheOwnersStoreToggle() throws Exception {
        LocalDate date = today.plusDays(10);
        UUID offRule = roomRuleId(storeOff, roomOff);
        int rulesBefore = pricingRuleRepository.findByTenantId(storeOff.getId()).size();

        // setCalendarPrice 先查 rooms 列（其餘案例只用 listings 列）；只在本案例建立、結束時清掉，不改變其他案例的固件
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 2, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomOff);
        try {
            // 開關關閉：兩個端點都擋下，而且沒有留下任何規則（原本回 200 卻建出一條不會生效的規則）
            assertThat(errorCode(postJson("/v2/dashboard/pricing/calendar/price", ownerOffToken,
                    calendarPriceBody(roomOff, date), 403))).isEqualTo("E-2004");
            assertThat(errorCode(postJson("/v2/dashboard/pricing/rules/" + offRule + "/override", ownerOffToken,
                    overrideBody(date, date.plusDays(2)), 403))).isEqualTo("E-2004");
            assertThat(pricingRuleRepository.findByTenantId(storeOff.getId()))
                    .as("被擋下的請求不可建立規則").hasSize(rulesBefore);

            // 只翻轉開關（其餘完全相同）：同一位店主、同一個房源、同一組請求 → 200，規則建立
            setToggle(storeOff, true);
            postJson("/v2/dashboard/pricing/calendar/price", ownerOffToken, calendarPriceBody(roomOff, date), 200);
            postJson("/v2/dashboard/pricing/rules/" + offRule + "/override", ownerOffToken,
                    overrideBody(date, date.plusDays(2)), 200);
            assertThat(pricingRuleRepository.findByTenantId(storeOff.getId()))
                    .as("開關開啟後兩個端點各建立一條規則").hasSize(rulesBefore + 2);
        } finally {
            jdbcTemplate.update("DELETE FROM rooms WHERE listing_id = ?", roomOff);
        }
    }

    // ── 固件與輔助 ────────────────────────────────────────────────────────────

    private UUID roomRuleId(Tenant store, UUID roomListingId) {
        return pricingRuleRepository.findByTenantId(store.getId()).stream()
                .filter(r -> roomListingId.equals(r.getRoomListingId()) && ROOM_RULE_NAME.equals(r.getRuleName()))
                .findFirst().orElseThrow().getId();
    }

    private Map<String, Object> calendarPriceBody(UUID roomListingId, LocalDate date) {
        return Map.of("roomListingId", roomListingId.toString(), "date", date.toString(),
                "price", 900, "reason", "S311 日曆手動改價");
    }

    private Map<String, Object> overrideBody(LocalDate start, LocalDate end) {
        return Map.of("startDate", start.toString(), "endDate", end.toString(),
                "overridePrice", 900, "reason", "S311 手動覆蓋");
    }

    private String postJson(String path, String token, Object body, int expectedStatus) throws Exception {
        return given().header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(body))
                .when().post(path)
                .then().statusCode(expectedStatus).extract().asString();
    }

    private String errorCode(String json) throws Exception {
        return objectMapper.readTree(json).path("code").asText();
    }

    private Tenant seedStore(String label, long stamp, boolean pricingEnabled) {
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S309 開關測試店 " + label + " " + stamp)
                .slug("s309-toggle-" + label + "-" + stamp)
                .contactEmail("s309-store-" + label + "-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        // 新店鋪的動態定價預設關閉（測試庫的觸發器補預設開關列）；這個前提也要斷言，否則「開關關閉」可能根本沒有開關列
        assertThat(toggleOf(store.getId())).as("新店鋪應已有動態定價開關列且預設關閉").isFalse();
        setToggle(store, pricingEnabled);
        return store;
    }

    private User seedUser(UUID tenantId, User.UserRole role, String label, long stamp) {
        return userRepository.save(User.builder()
                .email("s309-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S309 " + label)
                .role(role)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
    }

    private String token(User user, String role, UUID tenantId) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), role, tenantId.toString());
    }

    private String buyerToken(UUID tenantId, String label, long stamp) {
        return token(seedUser(tenantId, User.UserRole.BUYER, "buyer-" + label, stamp), "BUYER", tenantId);
    }

    private UUID seedListing(Tenant store, User owner, Listing.ListingType type, String title, BigDecimal basePrice) {
        return listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(type).title("S309 " + title)
                .basePrice(basePrice).status(Listing.ListingStatus.ACTIVE).build()).getId();
    }

    private void seedProductRule(Tenant store, UUID listingId) {
        pricingRuleRepository.save(PricingRule.builder()
                .tenant(store).listingId(listingId)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).ruleName(PRODUCT_RULE_NAME).priority(10)
                .config(Map.of("price", PRODUCT_RULE_PRICE.doubleValue()))
                .validFrom(today.minusDays(1)).validTo(today.plusDays(60)).isActive(true).build());
    }

    private void seedRoomRule(Tenant store, UUID roomListingId) {
        pricingRuleRepository.save(PricingRule.builder()
                .tenant(store).roomListingId(roomListingId)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).ruleName(ROOM_RULE_NAME).priority(10)
                .config(Map.of("price", ROOM_RULE_PRICE.doubleValue()))
                .validFrom(today.minusDays(1)).validTo(today.plusDays(60)).isActive(true).build());
    }

    private Boolean toggleOf(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT is_enabled FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Boolean.class, tenantId, PRICING_TOGGLE);
    }

    /** 直接改開關列（等同管理員核准／撤銷；查詢端沒有快取，所以立即生效）。 */
    private void setToggle(Tenant store, boolean enabled) {
        int updated = jdbcTemplate.update(
                "UPDATE tenant_feature_toggles SET is_enabled = ? WHERE tenant_id = ? AND feature_key = ?",
                enabled, store.getId(), PRICING_TOGGLE);
        assertThat(updated).as("店鋪應已有動態定價開關列").isEqualTo(1);
    }

    private JsonNode data(String json) throws Exception {
        return objectMapper.readTree(json).path("data");
    }

    private JsonNode effectivePrice(String token, UUID listingId) throws Exception {
        return data(given().header("Authorization", "Bearer " + token)
                .when().get("/v2/listings/" + listingId + "/effective-price?checkDate=" + today)
                .then().statusCode(200).extract().asString());
    }

    private JsonNode roomPrice(String token, UUID roomListingId) throws Exception {
        return data(given().header("Authorization", "Bearer " + token)
                .when().get("/v2/listings/" + roomListingId + "/price?checkIn=" + checkIn + "&checkOut=" + checkOut)
                .then().statusCode(200).extract().asString());
    }

    private JsonNode availability(String token, UUID roomListingId) throws Exception {
        return data(given().header("Authorization", "Bearer " + token)
                .when().get("/v2/bookings/availability?roomListingId=" + roomListingId
                        + "&checkInDate=" + checkIn + "&checkOutDate=" + checkOut)
                .then().statusCode(200).extract().asString());
    }

    private JsonNode ownerCalculate(String ownerToken, UUID roomListingId) throws Exception {
        Map<String, Object> body = Map.of(
                "roomListingId", roomListingId.toString(),
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString());
        return data(given().header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(body))
                .when().post("/v2/dashboard/pricing/calculate")
                .then().statusCode(200).extract().asString());
    }
}
