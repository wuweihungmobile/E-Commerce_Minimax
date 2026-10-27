package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * 一間店鋪的 {@code status} 值不在列舉內，不得讓管理員的平台統計變成 500（Sprint 307，DEF-422；Flyway 建出的資料庫＋完整
 * HTTP／JWT／權限鏈）。
 *
 * <p>背景：{@code AdminService.getPlatformStats} 原本用 {@code tenantRepository.findAll().stream().filter(ACTIVE).count()}
 * 算活躍店鋪數——把全部租戶載入記憶體，而且載入時只要有一列 {@code tenants.status} 不在 Java 列舉
 * {@code TenantStatus} 內就丟 {@code No enum constant}，整個 {@code GET /v2/admin/stats}（管理後台首頁 {@code /admin}
 * 的資料來源）變成 HTTP 500。Flyway 建的 {@code tenants.status} 只是 {@code VARCHAR(20)}、沒有 CHECK 約束（V1），寫得進去；
 * Hibernate 產生的一般整合測試資料庫對列舉欄位有 CHECK，放不進這種值（見 {@code flyway-db-vs-hibernate-test-db-differences}），
 * 所以不能用它——與 {@code PricingRulesCorruptRowFlywayIntegrationTest}（DEF-421）同一個做法。
 *
 * <p>第一個案例先斷言固件真的會咬人（{@code findAll()} 讀不出來）：若將來列舉映射改成能容忍未知值，這支測試會在這裡紅，
 * 提醒護欄已經失去觸發條件，而不是靜靜變成永遠通過的測試。第二個案例的期望值直接用 SQL 從資料庫量，不寫死數字，
 * 並要求「活躍」嚴格少於「全部」——固件裡有被停權的與狀態壞掉的店鋪，把『活躍數』誤算成『全部』會被抓到。
 *
 * <p>資料庫 {@code s307_platform_stats_corrupt_row} 每次執行都先砍掉重建，執行完留在測試用 PostgreSQL 裡（Spring 的 context
 * 快取仍持有它的連線，無法在類別結束時刪除）；測試用資料庫容器停止時一併消失。需要 {@code make test-db-up}。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 307（DEF-422）: 一間店鋪的狀態值壞掉不得讓管理員的平台統計變成 500（Flyway 資料庫）")
class PlatformStatsCorruptRowFlywayIntegrationTest {

    private static final String BASE_URL =
            System.getProperty("it.db.base-url", "jdbc:postgresql://localhost:5432/");
    private static final String MAINTENANCE_DB = System.getProperty("it.db.name", "nextkeytest");
    private static final String DB_USER = System.getProperty("it.db.user", "koala");
    private static final String DB_PASS = System.getProperty("it.db.pass", "koala5");
    private static final String FLYWAY_DB = "s307_platform_stats_corrupt_row";

    private static final String STATS_URL = "/v2/admin/stats";
    /** 不在 {@code TenantStatus} 內的店鋪狀態。 */
    private static final String UNKNOWN_STATUS = "LEGACY_STATUS";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private String superAdminToken;

    /** 在 Spring 載入 context 之前建好空的資料庫，讓 Flyway 在 context 啟動時從 V1 實跑到最新版。 */
    @DynamicPropertySource
    static void useFreshFlywayDatabase(final DynamicPropertyRegistry registry) throws SQLException {
        try (Connection c = DriverManager.getConnection(BASE_URL + MAINTENANCE_DB, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + FLYWAY_DB + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + FLYWAY_DB);
        }
        registry.add("spring.datasource.url", () -> BASE_URL + FLYWAY_DB);
    }

    @BeforeAll
    void seedFixtures() {
        long stamp = System.currentTimeMillis();

        seedTenant("active-a", stamp, Tenant.TenantStatus.ACTIVE);
        seedTenant("active-b", stamp, Tenant.TenantStatus.ACTIVE);
        seedTenant("suspended", stamp, Tenant.TenantStatus.SUSPENDED);
        // 一列壞資料：直接以 SQL 寫入列舉外的 status（Flyway 資料庫沒有 CHECK 約束）
        jdbcTemplate.update("INSERT INTO tenants (name, slug, contact_email, status) VALUES (?, ?, ?, ?)",
                "S307 壞狀態店 " + stamp, "s307-stats-corrupt-" + stamp,
                "s307-stats-corrupt-" + stamp + "@example.com", UNKNOWN_STATUS);

        UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        User superAdmin = userRepository.save(User.builder()
                .email("s307-stats-super-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S307 平台管理員")
                .role(User.UserRole.SUPER_ADMIN)
                .status("ACTIVE")
                .tenantId(systemTenantId)
                .build());
        superAdminToken = jwtTokenService.generateAccessToken(
                superAdmin.getId(), superAdmin.getEmail(), "SUPER_ADMIN", systemTenantId.toString());
    }

    private void seedTenant(final String label, final long stamp, final Tenant.TenantStatus status) {
        tenantRepository.save(Tenant.builder()
                .name("S307 統計測試店 " + label + " " + stamp)
                .slug("s307-stats-" + label + "-" + stamp)
                .contactEmail("s307-stats-" + label + "-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(status)
                .build());
    }

    @Test
    @DisplayName("固件會咬人：這個資料庫裡 findAll() 載入全部店鋪就丟列舉錯誤——下面的案例才有意義（失去觸發條件時這裡先紅）")
    void fixture_corruptStatusMakesFullTableScanFail() {
        assertThatThrownBy(() -> tenantRepository.findAll())
                .as("若 findAll() 讀得出來，壞資料列就不再能抓到『退回全表載入』的回歸，這支測試要改用新的觸發條件")
                .hasStackTraceContaining(UNKNOWN_STATUS);
    }

    @Test
    @DisplayName("GET /v2/admin/stats：照常 200，活躍店鋪數與店鋪總數都等於資料庫實際的筆數（壞狀態那一列只算進總數）")
    void platformStats_countsFromTheDatabase_evenWithACorruptStatusRow() throws Exception {
        int expectedTotal = jdbcTemplate.queryForObject("SELECT count(*) FROM tenants", Integer.class);
        int expectedActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenants WHERE status = 'ACTIVE'", Integer.class);
        assertThat(expectedActive).as("固件至少有兩間活躍店鋪").isGreaterThanOrEqualTo(2);
        assertThat(expectedActive).as("固件裡有被停權的與狀態壞掉的店鋪，活躍數必須嚴格少於全部").isLessThan(expectedTotal);

        String body = mockMvc.perform(get(STATS_URL).header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("totalTenants").asInt()).as("店鋪總數").isEqualTo(expectedTotal);
        assertThat(data.path("activeTenants").asInt()).as("活躍店鋪數").isEqualTo(expectedActive);
    }
}
