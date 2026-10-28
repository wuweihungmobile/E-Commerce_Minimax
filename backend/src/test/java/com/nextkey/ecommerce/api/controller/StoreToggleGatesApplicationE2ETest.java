package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
import io.restassured.module.mockmvc.response.MockMvcResponse;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;

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
    private static final String RULES_PATH = "/v2/dashboard/pricing/rules";
    private static final String CALENDAR_PRICE_PATH = "/v2/dashboard/pricing/calendar/price";

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
    /**
     * 平台管理員（SUPER_ADMIN）的 token（DEF-435）。管理員請求的 {@code TenantContext} 不是 token 裡的租戶：有 {@code X-Tenant-ID}
     * 標頭就用標頭指定的租戶，沒有就是系統租戶（{@code TenantContextFilter.resolveEffectiveTenantId}）。所以用標頭指向兩個
     * 我控制開關的租戶，來代表「管理員自己這一側的開關」一開一關。
     */
    private String adminToken;
    private UUID adminTenantOn;
    private UUID adminTenantOff;

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

        // DEF-435：平台管理員代操作。系統租戶的開關值依資料庫而異（本類別的消費者案例不依賴也不去改），所以用兩個我控制開關的租戶
        // （以 X-Tenant-ID 標頭指向）來代表「管理員自己這一側的開關」；真實設定（沒帶標頭、系統租戶開關開啟）另有一個案例暫時打開還原。
        adminToken = token(seedUser(SYSTEM_TENANT_ID, User.UserRole.SUPER_ADMIN, "admin", stamp),
                "SUPER_ADMIN", SYSTEM_TENANT_ID);
        adminTenantOn = callerOnTenant.getId();
        adminTenantOff = callerOffTenant.getId();

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
        insertRoomRow(roomOff);
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
            deleteRoomRows(roomOff);
        }
    }

    // ── 管理員代操作看的是『房源所屬店鋪』的開關（Sprint 312，DEF-435，使用者以互動選項裁決「改檢查店鋪」）──────

    @Test
    @DisplayName("DEF-435：管理員代操作（建立規則、日曆改價、覆蓋價格）的結果只取決於房源所屬店鋪的開關，"
            + "與管理員自己租戶的開關無關——兩位管理員（自己租戶開關一開一關）× 兩間店鋪 × 三個端點")
    void superAdminDelegation_followsTheTargetStoresToggle() throws Exception {
        LocalDate date = today.plusDays(10);
        UUID offRule = roomRuleId(storeOff, roomOff);
        int offRulesBefore = pricingRuleRepository.findByRoomListingId(roomOff).size();
        Map<String, UUID> admins = new LinkedHashMap<>(); // 管理員自己這一側的租戶（X-Tenant-ID 標頭）
        admins.put("管理員租戶開關開啟", adminTenantOn);
        admins.put("管理員租戶開關關閉", adminTenantOff);
        List<String> mismatches = new ArrayList<>();

        // setCalendarPrice 先查 rooms 列：兩間房源都補，結束時刪
        insertRoomRow(roomOff);
        insertRoomRow(roomOn);
        try {
            // 舊式規則：蓋著『關閉的租戶』、卻綁在開啟店鋪的房源上——DEF-436 之前管理員經 POST /rules 建的規則就是這個樣子（蓋請求租戶），
            // 也是上線前留下的舊資料的樣子。店鋪開啟時覆蓋它，才分得出關卡看的是『規則上蓋的租戶』還是『房源所屬店鋪』（前者會誤擋）
            UUID legacyRule = seedLegacyStampedRule(roomOn, adminTenantOff);
            int n = 0; // 每個格子用不同的有效期間與日期，避免同類型重疊檢查把與開關無關的格子擋掉
            for (Map.Entry<String, UUID> admin : admins.entrySet()) {
                for (boolean storeEnabled : new boolean[] {false, true}) {
                    UUID room = storeEnabled ? roomOn : roomOff;
                    String cell = admin.getKey() + "／店鋪" + (storeEnabled ? "開啟" : "關閉");
                    String blocked = "403 E-2004";
                    String ruleName = "S312 管理員規則 " + (++n);
                    expectOutcome(mismatches, cell + "／建立規則", RULES_PATH, admin.getValue(),
                            ruleRequestBody(room, null, ruleName, n), storeEnabled ? "201" : blocked);
                    expectOutcome(mismatches, cell + "／日曆改價", CALENDAR_PRICE_PATH, admin.getValue(),
                            calendarPriceBody(room, date.plusDays(n)), storeEnabled ? "200" : blocked);
                    UUID rule = storeEnabled ? legacyRule : offRule;
                    expectOutcome(mismatches, cell + "／覆蓋價格", RULES_PATH + "/" + rule + "/override", admin.getValue(),
                            overrideBody(date.plusDays(n), date.plusDays(n + 1)), storeEnabled ? "200" : blocked);
                }
            }
            assertThat(mismatches).as("每個格子的實際結果都應與房源所屬店鋪的開關一致").isEmpty();
            assertThat(pricingRuleRepository.findByRoomListingId(roomOff))
                    .as("店鋪關閉時被擋下的請求不可建立規則").hasSize(offRulesBefore);
        } finally {
            deleteRoomRows(roomOff, roomOn);
            deleteTestCreatedRules();
        }
    }

    @Test
    @DisplayName("DEF-435：建立規則同時帶房源與商品 id 時，每個 id 所屬店鋪的開關都要開啟（比照 DEF-423：每個帶入的 id 都驗證）")
    void superAdminCreateRule_everySuppliedIdsStoreMustBeEnabled() throws Exception {
        List<String> mismatches = new ArrayList<>();
        try {
            // 管理員自己這一側的開關刻意取與預期相反的一邊：任何一格錯看呼叫者租戶，結果都會翻成相反
            expectOutcome(mismatches, "房源開啟＋商品關閉", RULES_PATH, adminTenantOn,
                    ruleRequestBody(roomOn, productOff, "S312 房源開＋商品關", 1), "403 E-2004");
            expectOutcome(mismatches, "房源關閉＋商品開啟", RULES_PATH, adminTenantOn,
                    ruleRequestBody(roomOff, productOn, "S312 房源關＋商品開", 2), "403 E-2004");
            expectOutcome(mismatches, "房源開啟＋商品開啟", RULES_PATH, adminTenantOff,
                    ruleRequestBody(roomOn, productOn, "S312 房源開＋商品開", 3), "201");
            assertThat(mismatches).as("兩個 id 所屬店鋪的開關都開啟才放行").isEmpty();
        } finally {
            deleteTestCreatedRules();
        }
    }

    @Test
    @DisplayName("DEF-435：正式環境的樣子（管理員沒帶 X-Tenant-ID、系統租戶的動態定價開關開啟，Flyway 種子即如此）——"
            + "替開關關閉的店鋪操作被擋（原本得到成功卻不生效），開關開啟的店鋪照常")
    void superAdminOnSystemTenant_followsTheTargetStoresToggle() throws Exception {
        LocalDate date = today.plusDays(10);
        UUID offRule = roomRuleId(storeOff, roomOff);
        UUID onRule = roomRuleId(storeOn, roomOn);
        int offRulesBefore = pricingRuleRepository.findByRoomListingId(roomOff).size();
        List<String> mismatches = new ArrayList<>();

        // 測試庫的系統租戶開關預設關閉，Flyway 資料庫是開啟；暫時打開來還原正式環境的設定，結束時還原
        Boolean originalSystemToggle = setSystemTenantPricingToggle(true);
        insertRoomRow(roomOff);
        insertRoomRow(roomOn);
        try {
            String blocked = "403 E-2004";
            expectOutcome(mismatches, "店鋪關閉／建立規則", RULES_PATH, null,
                    ruleRequestBody(roomOff, null, "S312 系統租戶管理員規則 1", 1), blocked);
            expectOutcome(mismatches, "店鋪關閉／日曆改價", CALENDAR_PRICE_PATH, null,
                    calendarPriceBody(roomOff, date), blocked);
            expectOutcome(mismatches, "店鋪關閉／覆蓋價格", RULES_PATH + "/" + offRule + "/override", null,
                    overrideBody(date, date.plusDays(1)), blocked);
            expectOutcome(mismatches, "店鋪開啟／建立規則", RULES_PATH, null,
                    ruleRequestBody(roomOn, null, "S312 系統租戶管理員規則 2", 2), "201");
            expectOutcome(mismatches, "店鋪開啟／日曆改價", CALENDAR_PRICE_PATH, null,
                    calendarPriceBody(roomOn, date.plusDays(2)), "200");
            expectOutcome(mismatches, "店鋪開啟／覆蓋價格", RULES_PATH + "/" + onRule + "/override", null,
                    overrideBody(date.plusDays(2), date.plusDays(3)), "200");
            assertThat(mismatches).as("管理員自己這一側的開關開啟時，結果仍只取決於房源所屬店鋪的開關").isEmpty();
            assertThat(pricingRuleRepository.findByRoomListingId(roomOff))
                    .as("店鋪關閉時被擋下的請求不可建立規則").hasSize(offRulesBefore);
        } finally {
            deleteRoomRows(roomOff, roomOn);
            deleteTestCreatedRules();
            restoreSystemTenantPricingToggle(originalSystemToggle);
        }
    }

    @Test
    @DisplayName("DEF-435：管理員對不存在的房源建立規則 → 404 E-4000，而且沒有留下規則")
    void superAdminCreateRule_unknownListing_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        try {
            assertThat(outcome(RULES_PATH, adminTenantOn, ruleRequestBody(unknown, null, "S312 不存在的房源", 4)))
                    .isEqualTo("404 E-4000");
            assertThat(pricingRuleRepository.findByRoomListingId(unknown)).as("不存在的房源不可有規則").isEmpty();
        } finally {
            jdbcTemplate.update("DELETE FROM pricing_rules WHERE room_listing_id = ?", unknown);
        }
    }

    // ── 管理員建立的規則蓋房源所屬店鋪（Sprint 313，DEF-436，使用者以互動選項裁決『蓋房源所屬店鋪』）──────

    @Test
    @DisplayName("DEF-436：管理員在店鋪房源上建立的規則蓋房源所屬店鋪（不看請求租戶），該店店主列得到、改得動、停用得了；"
            + "不綁房源的規則仍蓋請求租戶")
    void superAdminCreateRule_isStampedWithTheListingsStore_soTheOwnerCanManageIt() throws Exception {
        String storeId = storeOn.getId().toString();
        String unboundName = "S313 不綁房源的規則";
        List<String> mismatches = new ArrayList<>();
        // 管理員沒帶標頭是系統租戶，測試庫的系統租戶開關預設關閉；暫時打開來還原正式環境的設定，結束時還原
        Boolean originalSystemToggle = setSystemTenantPricingToggle(true);
        try {
            // 蓋章不看請求租戶：沒帶標頭（系統租戶）與標頭指向『關閉的租戶』兩種請求，規則都蓋房源所屬店鋪
            JsonNode byDefault = postAsAdminData(RULES_PATH, null,
                    ruleRequestBody(roomOn, null, "S313 蓋章（沒帶標頭）", 11));
            JsonNode byHeader = postAsAdminData(RULES_PATH, adminTenantOff,
                    ruleRequestBody(roomOn, null, "S313 蓋章（標頭指向關閉的租戶）", 12));
            String ruleId = byDefault.path("ruleId").asText();
            expectEquals(mismatches, "回應的 tenantId（沒帶標頭）", storeId, byDefault.path("tenantId").asText());
            expectEquals(mismatches, "資料庫 tenant_id（沒帶標頭）", storeId, ruleTenantInDb(ruleId));
            expectEquals(mismatches, "資料庫 tenant_id（標頭指向關閉的租戶）", storeId,
                    ruleTenantInDb(byHeader.path("ruleId").asText()));

            // 該店店主：列得到、改得動、停用得了
            String list = given().header("Authorization", "Bearer " + ownerOnToken)
                    .when().get(RULES_PATH + "?roomListingId=" + roomOn).then().extract().asString();
            expectEquals(mismatches, "店主列得到這條規則", true, !ruleId.isEmpty() && list.contains(ruleId));
            expectEquals(mismatches, "店主 PUT", "200", outcomeOf(given().header("Authorization", "Bearer " + ownerOnToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE).body("{\"priority\":7}")
                    .when().put(RULES_PATH + "/" + ruleId)));
            expectEquals(mismatches, "店主 DELETE", "200", outcomeOf(given().header("Authorization", "Bearer " + ownerOnToken)
                    .when().delete(RULES_PATH + "/" + ruleId)));
            expectEquals(mismatches, "店主停用後的 is_active", false, ruleActiveInDb(ruleId));

            // 不綁房源／商品：沒有店鋪可蓋，仍蓋請求租戶（系統租戶）
            JsonNode unbound = postAsAdminData(RULES_PATH, null, ruleRequestBody(null, null, unboundName, 25));
            expectEquals(mismatches, "不綁房源的規則：資料庫 tenant_id", SYSTEM_TENANT_ID.toString(),
                    ruleTenantInDb(unbound.path("ruleId").asText()));

            assertThat(mismatches).as("管理員建立的規則蓋房源所屬店鋪，店主管得到").isEmpty();
        } finally {
            deleteTestCreatedRules();
            jdbcTemplate.update("DELETE FROM pricing_rules WHERE rule_name = ?", unboundName);
            restoreSystemTenantPricingToggle(originalSystemToggle);
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

    /**
     * 蓋著 {@code stampedTenantId}、卻綁在別間店鋪房源上的規則（有效期在很遠的未來，不影響其他案例的價格）：DEF-436 之前管理員經
     * {@code POST /rules} 建的規則就是這個樣子，也是上線前留下的舊資料的樣子。由 {@link #deleteTestCreatedRules} 清掉。
     */
    private UUID seedLegacyStampedRule(UUID roomListingId, UUID stampedTenantId) {
        Tenant stamped = tenantRepository.findById(stampedTenantId).orElseThrow();
        return pricingRuleRepository.save(PricingRule.builder()
                .tenant(stamped).roomListingId(roomListingId)
                .ruleType(PricingRule.PricingRuleType.MANUAL_OVERRIDE).ruleName("S313 舊式規則（蓋著關閉的租戶）").priority(1)
                .config(Map.of("price", ROOM_RULE_PRICE.doubleValue()))
                .validFrom(today.plusYears(30)).validTo(today.plusYears(30).plusDays(3)).isActive(true).build()).getId();
    }

    /** 建立規則的請求本文；每條規則用不同的有效期間（{@code offsetYears}），避免同類型時間重疊檢查把與開關無關的案例擋下。 */
    private Map<String, Object> ruleRequestBody(UUID roomListingId, UUID listingId, String name, int offsetYears) {
        LocalDate from = today.plusYears(10L + offsetYears);
        Map<String, Object> body = new LinkedHashMap<>();
        if (roomListingId != null) {
            body.put("roomListingId", roomListingId.toString());
        }
        if (listingId != null) {
            body.put("listingId", listingId.toString());
        }
        body.put("ruleType", "SEASONAL");
        body.put("ruleName", name);
        body.put("priority", 1);
        body.put("config", Map.of("multiplier", 1.1));
        body.put("validFrom", from.toString());
        body.put("validTo", from.plusDays(3).toString());
        return body;
    }

    /**
     * 以平台管理員身分送出 POST 並回傳「狀態碼」或「狀態碼 錯誤碼」；不在這裡斷言，讓呼叫端一次比對整張結果表。
     * {@code asTenant} 非 null 時帶 {@code X-Tenant-ID} 標頭（決定管理員請求的 TenantContext），null 是沒帶標頭（系統租戶）。
     */
    private String outcome(String path, UUID asTenant, Object body) throws Exception {
        return outcomeOf(postAsAdmin(path, asTenant, body));
    }

    private String outcomeOf(MockMvcResponse response) throws Exception {
        int status = response.statusCode();
        return status >= 400 ? status + " " + errorCode(response.asString()) : String.valueOf(status);
    }

    /** 以管理員身分 POST 並回傳回應的 {@code data}（建立失敗時是空節點，讓呼叫端把差異記成不符，而不是丟例外）。 */
    private JsonNode postAsAdminData(String path, UUID asTenant, Object body) throws Exception {
        return objectMapper.readTree(postAsAdmin(path, asTenant, body).asString()).path("data");
    }

    private String ruleTenantInDb(String ruleId) {
        return ruleId.isEmpty() ? "（沒有建立成功）" : jdbcTemplate.queryForObject(
                "SELECT tenant_id::text FROM pricing_rules WHERE id = ?::uuid", String.class, ruleId);
    }

    private Boolean ruleActiveInDb(String ruleId) {
        return ruleId.isEmpty() ? null : jdbcTemplate.queryForObject(
                "SELECT is_active FROM pricing_rules WHERE id = ?::uuid", Boolean.class, ruleId);
    }

    private void expectEquals(List<String> mismatches, String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            mismatches.add(what + "：預期 " + expected + "，實際 " + actual);
        }
    }

    private MockMvcResponse postAsAdmin(String path, UUID asTenant, Object body) throws Exception {
        MockMvcRequestSpecification request = given().header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(body));
        if (asTenant != null) {
            request = request.header("X-Tenant-ID", asTenant.toString());
        }
        return request.when().post(path);
    }

    private void expectOutcome(List<String> mismatches, String cell, String path, UUID asTenant, Object body,
            String expected) throws Exception {
        String actual = outcome(path, asTenant, body);
        if (!expected.equals(actual)) {
            mismatches.add(cell + "：預期 " + expected + "，實際 " + actual);
        }
    }

    /** 把系統租戶的動態定價開關設成 {@code enabled}，回傳原本的值（原本沒有開關列時回 null，還原時刪掉我補的列）。 */
    private Boolean setSystemTenantPricingToggle(boolean enabled) {
        List<Boolean> rows = jdbcTemplate.queryForList(
                "SELECT is_enabled FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Boolean.class, SYSTEM_TENANT_ID, PRICING_TOGGLE);
        if (rows.isEmpty()) {
            jdbcTemplate.update("INSERT INTO tenant_feature_toggles "
                    + "(id, tenant_id, feature_key, is_enabled, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    UUID.randomUUID(), SYSTEM_TENANT_ID, PRICING_TOGGLE, enabled);
            return null;
        }
        jdbcTemplate.update("UPDATE tenant_feature_toggles SET is_enabled = ? WHERE tenant_id = ? AND feature_key = ?",
                enabled, SYSTEM_TENANT_ID, PRICING_TOGGLE);
        return rows.get(0);
    }

    private void restoreSystemTenantPricingToggle(Boolean original) {
        if (original == null) {
            jdbcTemplate.update("DELETE FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                    SYSTEM_TENANT_ID, PRICING_TOGGLE);
        } else {
            jdbcTemplate.update("UPDATE tenant_feature_toggles SET is_enabled = ? WHERE tenant_id = ? AND feature_key = ?",
                    original, SYSTEM_TENANT_ID, PRICING_TOGGLE);
        }
    }

    /** setCalendarPrice 先查 rooms 列，而這個類別的固件只種了 listings 列；需要的案例自己補、結束時用 deleteRoomRows 刪。 */
    private void insertRoomRow(UUID roomListingId) {
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 2, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
    }

    private void deleteRoomRows(UUID... roomListingIds) {
        for (UUID id : roomListingIds) {
            jdbcTemplate.update("DELETE FROM rooms WHERE listing_id = ?", id);
        }
    }

    /** 刪掉案例建立的規則：綁在四個固件房源／商品上、而且不是種子規則的全部（含失敗案例的殘留）。 */
    private void deleteTestCreatedRules() {
        jdbcTemplate.update("DELETE FROM pricing_rules WHERE (room_listing_id IN (?, ?) OR listing_id IN (?, ?)) "
                + "AND rule_name NOT IN (?, ?)", roomOn, roomOff, productOn, productOff, ROOM_RULE_NAME, PRODUCT_RULE_NAME);
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
