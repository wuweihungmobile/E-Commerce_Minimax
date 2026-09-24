package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * 結算週期邊界整合測試（DEF-270／DEF-271；真實 PostgreSQL）。
 *
 * <p>週結算的核心不變量：<b>每一筆訂單恰好歸屬一個結算期——不重疊、不留縫</b>。過去期間查詢以
 * {@code atTime(23, 59, 59)} 當上界（漏掉最後一秒的訂單，永遠不被結算），且以 JVM 預設時區解讀邊界
 * （正式容器 UTC 與開發機台灣切出不同的週）。本測試用真實資料庫、精確到微秒的邊界訂單守住這條不變量，
 * 並可在不同 JVM 時區下重跑（{@code JAVA_TOOL_OPTIONS=-Duser.timezone=...}），結果必須相同。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-BOUNDARY: 結算期間邊界（每筆訂單恰好歸屬一期，DEF-270/271）")
class SettlementPeriodBoundaryIntegrationTest {

    @Autowired private SettlementGenerator settlementGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID buyerId;

    @BeforeEach
    void seedTenantAndBuyer() {
        String stamp = String.valueOf(System.nanoTime());
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Settle Boundary Tenant")
                .slug("settle-boundary-" + stamp)
                .contactEmail("settle-boundary-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();
        buyerId = userRepository.save(User.builder()
                .email("settle-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Settle Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();
    }

    private void seedOrder(final String status, final String amount, final String createdAtTaipei) {
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', ?, ?, 0.00, 0.00, 'TWD', ?, ?)
                """, UUID.randomUUID(), tenantId, buyerId, status, new BigDecimal(amount),
                OffsetDateTime.parse(createdAtTaipei), OffsetDateTime.parse(createdAtTaipei));
    }

    @Test
    @DisplayName("台灣週一 00:00 起、次週一 00:00 前的訂單恰好歸屬一期；最後一秒（23:59:59.5）的訂單不可漏掉")
    void everyOrderBelongsToExactlyOnePeriod() {
        // 上一週（12/28~1/3）：週日最後一刻
        seedOrder("COMPLETED", "800.00", "2027-01-03T23:59:59.999999+08:00");
        // 本週（1/4~1/10）：週一 00:00 整、週間、週日 23:59:59.5（最後一秒，DEF-270）
        seedOrder("COMPLETED", "100.00", "2027-01-04T00:00:00+08:00");
        seedOrder("COMPLETED", "1600.00", "2027-01-07T12:00:00+08:00");
        seedOrder("DELIVERED", "200.00", "2027-01-10T23:59:59.500+08:00");
        // 下一週（1/11~1/17）：週一 00:00 整
        seedOrder("COMPLETED", "400.00", "2027-01-11T00:00:00+08:00");

        SettlementStatement previous = settlementGenerator.generateStatementForTenant(
                tenantId, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3));
        SettlementStatement current = settlementGenerator.generateStatementForTenant(
                tenantId, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));
        SettlementStatement next = settlementGenerator.generateStatementForTenant(
                tenantId, LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 17));

        assertThat(current.getTotalOrders()).as("本週：週一 00:00 整、週間、週日 23:59:59.5 共 3 筆").isEqualTo(3);
        assertThat(current.getTotalGmv()).isEqualByComparingTo("1900.00");
        assertThat(previous.getTotalOrders()).as("上一週只含 1/3 23:59:59.999999 那筆").isEqualTo(1);
        assertThat(previous.getTotalGmv()).isEqualByComparingTo("800.00");
        assertThat(next.getTotalOrders()).as("下一週只含 1/11 00:00 整那筆").isEqualTo(1);
        assertThat(next.getTotalGmv()).isEqualByComparingTo("400.00");
        assertThat(previous.getTotalOrders() + current.getTotalOrders() + next.getTotalOrders())
                .as("5 筆訂單各歸屬且僅歸屬一期：不重疊、不留縫").isEqualTo(5);
    }
}
