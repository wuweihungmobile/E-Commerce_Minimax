package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 定價規則的三個寫入端點真的寫得進資料庫（Sprint 303，DEF-414，真實 PostgreSQL，完整 HTTP＋JWT 鏈）。
 *
 * <p>{@link PricingRule#getTenantId()} 是 {@code insertable = false} 的唯讀影子欄位，INSERT 實際依據的是
 * {@code tenant} 關聯物件。{@code PricingService} 的 {@code createRule}、{@code setCalendarPrice}、
 * {@code overridePrice} 原本只設影子欄位，{@code pricing_rules.tenant_id}（正式 schema 與測試庫都是 NOT NULL）
 * 收到 NULL，三個端點一律 500。既有測試全部把 Repository 換成 mock（{@code M12PricingIntegrationTest}、
 * {@code PricingServiceTest}），INSERT 從未真的執行過，所以這個缺陷一直沒被看到。
 *
 * <p>本類別只用真實店主走真實 HTTP，並從資料庫讀回那一列，斷言它的租戶是店主的店鋪。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 303（DEF-414）: 定價規則的建立、日曆改價、區間覆蓋都真的寫進資料庫並帶店鋪租戶")
class PricingRulePersistenceE2ETest {

    private static final String RULES_URL = "/v2/dashboard/pricing/rules";
    private static final String CALENDAR_PRICE_URL = "/v2/dashboard/pricing/calendar/price";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private String ownerToken;
    private UUID storeTenantId;
    private UUID roomListingId;
    private UUID seededRuleId;

    @BeforeAll
    void seedFixtures() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        long stamp = System.currentTimeMillis();

        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S303 定價寫入測試店 " + stamp)
                .slug("s303-pricing-persist-" + stamp)
                .contactEmail("s303-persist-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        storeTenantId = store.getId();
        User owner = userRepository.save(User.builder()
                .email("s303-persist-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S303 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(storeTenantId)
                .build());
        ownerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", storeTenantId.toString());

        // 新店鋪的動態定價預設關閉（測試庫的觸發器補預設開關）；此店鋪是本類別專用，不必還原
        int toggled = jdbcTemplate.update(
                "UPDATE tenant_feature_toggles SET is_enabled = TRUE WHERE tenant_id = ? AND feature_key = ?",
                storeTenantId, "DYNAMIC_PRICING_ENABLED");
        assertThat(toggled).as("新店鋪應已有動態定價開關列").isEqualTo(1);

        roomListingId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.ROOM).title("S303 定價寫入測試房源")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())",
                roomListingId);

        seededRuleId = pricingRuleRepository.save(PricingRule.builder()
                .tenant(store)
                .roomListingId(roomListingId)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("S303 既有週末加價")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build()).getId();
    }

    @AfterAll
    void cleanUp() {
        pricingRuleRepository.deleteAll(pricingRuleRepository.findByTenantId(storeTenantId));
        jdbcTemplate.update("DELETE FROM rooms WHERE listing_id = ?", roomListingId);
        listingRepository.deleteById(roomListingId);
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

    @Test
    @DisplayName("建立規則：店主 201，資料庫那一列的租戶是店主的店鋪，且店主列表看得到它")
    void createRulePersistsWithStoreTenant() throws Exception {
        String created = given().header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"roomListingId\":\"" + roomListingId + "\",\"ruleType\":\"SEASONAL\","
                        + "\"ruleName\":\"S303 旺季加價\",\"priority\":3,\"config\":{\"multiplier\":1.4},"
                        + "\"validFrom\":\"" + LocalDate.now().plusYears(2) + "\","
                        + "\"validTo\":\"" + LocalDate.now().plusYears(2).plusDays(10) + "\"}")
                .when().post(RULES_URL)
                .then().statusCode(201)
                .body("data.tenantId", is(storeTenantId.toString()))
                .extract().asString();
        UUID ruleId = UUID.fromString(objectMapper.readTree(created).path("data").path("ruleId").asText());

        PricingRule persisted = pricingRuleRepository.findById(ruleId).orElseThrow();
        assertThat(persisted.getTenantId()).as("資料庫列的 tenant_id 是店主的店鋪").isEqualTo(storeTenantId);
        assertThat(persisted.getRoomListingId()).isEqualTo(roomListingId);

        given().header("Authorization", "Bearer " + ownerToken)
                .when().get(RULES_URL + "?roomListingId=" + roomListingId)
                .then().statusCode(200)
                .body("data.ruleId", hasItem(ruleId.toString()));
    }

    @Test
    @DisplayName("日曆改價：店主 200，產生的手動覆蓋規則（MANUAL_OVERRIDE）寫進資料庫並帶店鋪租戶")
    void setCalendarPricePersistsWithStoreTenant() {
        LocalDate date = LocalDate.now().plusDays(30);

        given().header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"roomListingId\":\"" + roomListingId + "\",\"date\":\"" + date + "\","
                        + "\"price\":1888,\"reason\":\"S303 日曆改價\"}")
                .when().post(CALENDAR_PRICE_URL)
                .then().statusCode(200);

        List<PricingRule> overrides = pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(roomListingId).stream()
                .filter(r -> r.getRuleType() == PricingRule.PricingRuleType.MANUAL_OVERRIDE)
                .filter(r -> "S303 日曆改價".equals(r.getRuleName()))
                .toList();
        assertThat(overrides).as("日曆改價應寫進一條手動覆蓋規則").hasSize(1);
        assertThat(overrides.get(0).getTenantId()).as("資料庫列的 tenant_id 是店主的店鋪").isEqualTo(storeTenantId);
        assertThat(overrides.get(0).getValidFrom()).isEqualTo(date);
    }

    @Test
    @DisplayName("區間覆蓋：店主 200，產生的覆蓋規則寫進資料庫並帶店鋪租戶，且記得原規則")
    void overridePricePersistsWithStoreTenant() {
        LocalDate start = LocalDate.now().plusDays(60);
        LocalDate end = start.plusDays(2);

        given().header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                        + "\"overridePrice\":1999,\"reason\":\"S303 區間覆蓋\"}")
                .when().post(RULES_URL + "/" + seededRuleId + "/override")
                .then().statusCode(200);

        List<PricingRule> overrides = pricingRuleRepository.findByRoomListingIdAndIsActiveTrue(roomListingId).stream()
                .filter(r -> "S303 區間覆蓋".equals(r.getRuleName()))
                .toList();
        assertThat(overrides).as("區間覆蓋應寫進一條覆蓋規則").hasSize(1);
        assertThat(overrides.get(0).getTenantId()).as("資料庫列的 tenant_id 是店主的店鋪").isEqualTo(storeTenantId);
        assertThat(overrides.get(0).getConfig()).containsEntry("originalRuleId", seededRuleId.toString());
    }
}
