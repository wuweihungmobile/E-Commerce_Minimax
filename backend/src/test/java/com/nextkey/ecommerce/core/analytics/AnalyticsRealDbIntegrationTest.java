package com.nextkey.ecommerce.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.AnalyticsDto;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 賣家儀表板／營收統計的真實 PostgreSQL 整合測試（DEF-272）。
 *
 * <p><b>為什麼一定要真實資料庫</b>：{@code AnalyticsService} 過去以 {@code LocalDateTime} 綁定 {@code Order.createdAt}
 * （{@code Instant} 欄位）的查詢參數，Hibernate 6 直接拋 {@code QueryArgumentException}
 * （型別不符）——{@code getDashboardStats}／{@code getRevenueStats} 在真實資料庫<b>每次都失敗</b>。
 * 但所有既有測試都 mock 了 {@code OrderRepository}，這個缺陷因此從未被抓到。本測試以精確到微秒的邊界訂單，
 * 同時守住「查得到」與「營運日（UTC+8）邊界正確、相鄰兩日不重疊不留縫」。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-ANALYTICS: 儀表板與營收統計在真實資料庫可用，且以營運日（UTC+8）切界（DEF-272）")
class AnalyticsRealDbIntegrationTest {

    @Autowired private AnalyticsService analyticsService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID buyerId;

    @BeforeEach
    void seed() {
        // 台灣 2027-01-15 12:00 = UTC 04:00；「今天」為 2027-01-15，昨天 1/14，明天 1/16
        BusinessTime.useClockForTesting(Clock.fixed(Instant.parse("2027-01-15T04:00:00Z"), ZoneOffset.UTC));
        String stamp = String.valueOf(System.nanoTime());
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Analytics IT Tenant")
                .slug("analytics-it-" + stamp)
                .contactEmail("analytics-it-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();
        buyerId = userRepository.save(User.builder()
                .email("analytics-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Analytics Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();

        seedOrder("COMPLETED", "100.00", "2027-01-15T00:00:00+08:00");            // 今天，日界起點整
        seedOrder("PAID", "200.00", "2027-01-15T23:59:59.500+08:00");            // 今天，最後一秒
        seedOrder("CANCELLED", "1600.00", "2027-01-15T10:00:00+08:00");          // 今天，已取消（不計營收）
        seedOrder("COMPLETED", "400.00", "2027-01-14T23:59:59.999999+08:00");    // 昨天，最後一刻
        seedOrder("COMPLETED", "800.00", "2027-01-16T00:00:00+08:00");          // 明天 00:00 整（不屬於今天）
        TenantContext.setCurrentTenant(tenantId);
    }

    private void seedOrder(final String status, final String amount, final String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', ?, ?, 0.00, 0.00, 'TWD', ?, ?)
                """, UUID.randomUUID(), tenantId, buyerId, status, new BigDecimal(amount),
                OffsetDateTime.parse(createdAt), OffsetDateTime.parse(createdAt));
    }

    @Test
    @DisplayName("getDashboardStats：不拋例外，今日/昨日/本月訂單數與營收以營運日切界")
    void dashboardStats_countsByBusinessDay() {
        AnalyticsDto.DashboardStats stats = analyticsService.getDashboardStats();

        assertThat(stats.getTodayOrders()).as("今天 00:00 整、10:00（已取消）、23:59:59.5 共 3 筆").isEqualTo(3);
        assertThat(stats.getTodayRevenue()).as("已取消不計營收：100 + 200").isEqualByComparingTo("300.00");
        assertThat(stats.getYesterdayOrders()).as("昨天 23:59:59.999999 那筆").isEqualTo(1);
        assertThat(stats.getYesterdayRevenue()).isEqualByComparingTo("400.00");
        assertThat(stats.getMonthOrders()).as("1/1 ~ 今天：昨天 1 筆 + 今天 3 筆；明天 00:00 整那筆不算").isEqualTo(4);
    }

    @Test
    @DisplayName("getRevenueStats（1/14~1/15，按日）：不拋例外，營收與分桶以營運日切界")
    void revenueStats_bucketsByBusinessDay() {
        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(AnalyticsDto.AnalyticsRequest.builder()
                .startDate(LocalDate.of(2027, 1, 14)).endDate(LocalDate.of(2027, 1, 15)).granularity("DAY").build());

        assertThat(stats.getTotalOrders()).as("1/14 一筆 + 1/15 三筆；1/16 00:00 整那筆不算").isEqualTo(4);
        assertThat(stats.getTotalRevenue()).as("PAID/COMPLETED 等計入，已取消不計：400 + 100 + 200")
                .isEqualByComparingTo("700.00");
        assertThat(stats.getDailyRevenue().getData()).hasSize(2);
        assertThat(stats.getDailyRevenue().getData().get(0).getRevenue()).isEqualByComparingTo("400.00");
        assertThat(stats.getDailyRevenue().getData().get(0).getOrderCount()).isEqualTo(1);
        assertThat(stats.getDailyRevenue().getData().get(1).getRevenue()).isEqualByComparingTo("300.00");
        assertThat(stats.getDailyRevenue().getData().get(1).getOrderCount()).isEqualTo(3);
    }
}
