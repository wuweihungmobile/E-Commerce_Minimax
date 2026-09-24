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
 * 結算「每筆訂單恰好被結算一次」整合測試（DEF-273；真實 PostgreSQL）。
 *
 * <p>過去結算以「下單時間落在該週、且結算產生當下已送達」歸屬訂單：週間下單、下週才送達的訂單，
 * 之後任何一期都撈不到，<b>永遠不會被結算</b>。使用者於 2026-09-24 拍板：訂單記錄它被哪張結算單結算
 * （{@code orders.settled_statement_id}），每次結算納入「所有已完成且尚未結算」的訂單。
 *
 * <p>釋放規則（本測試逐一守住）：結算單 <b>REJECTED</b>（終態、資金未發生）→ 釋放它認領的訂單與折入的調整單，
 * 讓下一期重新結算；<b>FAILED</b>（可用 {@code retryFailedTransfer} 改回 APPROVED 重試撥款）→ 必須保留，
 * 否則重試撥款後同一批訂單又被下一期結算，雙重撥款。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-ONCE: 每筆訂單恰好結算一次（DEF-273）")
class SettlementExactlyOnceIntegrationTest {

    @Autowired private SettlementGenerator settlementGenerator;
    @Autowired private SettlementReviewer settlementReviewer;
    @Autowired private SettlementAdjustmentService settlementAdjustmentService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID buyerId;
    private UUID adminId;

    @BeforeEach
    void seed() {
        String stamp = String.valueOf(System.nanoTime());
        tenantId = tenantRepository.save(Tenant.builder()
                .name("Settle Once Tenant")
                .slug("settle-once-" + stamp)
                .contactEmail("settle-once-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
        buyerId = seedUser("buyer", stamp, User.UserRole.BUYER);
        adminId = seedUser("admin", stamp, User.UserRole.BUYER);
    }

    private UUID seedUser(final String prefix, final String stamp, final User.UserRole role) {
        return userRepository.save(User.builder()
                .email("settle-once-" + prefix + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Settle Once " + prefix)
                .role(role)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();
    }

    private UUID seedOrder(final String status, final String amount, final String createdAtTaipei) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', ?, ?, 0.00, 0.00, 'TWD', ?, ?)
                """, id, tenantId, buyerId, status, new BigDecimal(amount),
                OffsetDateTime.parse(createdAtTaipei), OffsetDateTime.parse(createdAtTaipei));
        return id;
    }

    private void setOrderStatus(final UUID orderId, final String status) {
        jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, orderId);
    }

    private UUID settledStatementOf(final UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT settled_statement_id FROM orders WHERE id = ?", UUID.class, orderId);
    }

    private void setStatementStatus(final UUID statementId, final String status) {
        jdbcTemplate.update("UPDATE settlement_statements SET status = ? WHERE id = ?", status, statementId);
    }

    private SettlementStatement generate(final String start, final String end) {
        return settlementGenerator.generateStatementForTenant(tenantId, LocalDate.parse(start), LocalDate.parse(end));
    }

    @Test
    @DisplayName("週三下單、結算當下仍在配送的 5000 元訂單，下週送達後在下一次結算被結算一次，且之後不再重複結算")
    void lateCompletingOrder_isSettledExactlyOnce() {
        UUID orderId = seedOrder("SHIPPING", "5000.00", "2027-01-06T12:00:00+08:00");

        SettlementStatement w0 = generate("2027-01-04", "2027-01-10");
        assertThat(w0.getTotalOrders()).as("結算當下尚在配送中，本期不納入").isZero();
        assertThat(settledStatementOf(orderId)).isNull();

        setOrderStatus(orderId, "DELIVERED"); // 下週二送達

        SettlementStatement w1 = generate("2027-01-11", "2027-01-17");
        assertThat(w1.getTotalOrders()).as("送達後的第一次結算就必須撈到它（過去永遠撈不到）").isEqualTo(1);
        assertThat(w1.getTotalGmv()).isEqualByComparingTo("5000.00");
        assertThat(settledStatementOf(orderId)).as("訂單記錄它被哪張結算單結算").isEqualTo(w1.getId());

        SettlementStatement w2 = generate("2027-01-18", "2027-01-24");
        assertThat(w2.getTotalOrders()).as("已結算的訂單不可被下一期重複結算").isZero();
        assertThat(w2.getTotalGmv()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("週期結束「之後」才建立的訂單不會被提前結算，留給後面的結算期")
    void orderCreatedAfterPeriodEnd_isNotSettledEarly() {
        UUID early = seedOrder("COMPLETED", "100.00", "2027-01-06T12:00:00+08:00");
        UUID late = seedOrder("COMPLETED", "200.00", "2027-01-12T09:00:00+08:00");

        SettlementStatement w0 = generate("2027-01-04", "2027-01-10");
        assertThat(w0.getTotalOrders()).isEqualTo(1);
        assertThat(w0.getTotalGmv()).isEqualByComparingTo("100.00");
        assertThat(settledStatementOf(late)).isNull();

        SettlementStatement w1 = generate("2027-01-11", "2027-01-17");
        assertThat(w1.getTotalOrders()).isEqualTo(1);
        assertThat(w1.getTotalGmv()).isEqualByComparingTo("200.00");
        assertThat(settledStatementOf(early)).isEqualTo(w0.getId());
        assertThat(settledStatementOf(late)).isEqualTo(w1.getId());
    }

    @Test
    @DisplayName("結算單被駁回（REJECTED）→ 釋放它認領的訂單，下一期重新結算；折入的調整單也還原為 PENDING")
    void rejectedStatement_releasesOrdersAndAdjustments() {
        UUID orderId = seedOrder("COMPLETED", "1000.00", "2027-01-06T12:00:00+08:00");
        UUID previousStatementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-21', DATE '2026-12-27', 1, 500.00, 0.00, 50.00, 450.00,
                        'TWD', 'PAID', NOW(), NOW(), NOW())
                """, previousStatementId, tenantId, "STL-PREV-" + System.nanoTime());
        UUID adjustmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO adjustment_statements
                    (id, tenant_id, order_id, original_statement_id, adjustment_type, amount, status, created_at)
                VALUES (?, ?, ?, ?, 'REFUND_DEDUCTION', -100.00, 'PENDING', NOW())
                """, adjustmentId, tenantId, UUID.randomUUID(), previousStatementId);

        SettlementStatement w0 = generate("2027-01-04", "2027-01-10");
        assertThat(w0.getTotalOrders()).isEqualTo(1);
        assertThat(settledStatementOf(orderId)).isEqualTo(w0.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adjustment_statements WHERE id = ?",
                String.class, adjustmentId)).as("調整單被折入這張結算單").isEqualTo("APPLIED");

        setStatementStatus(w0.getId(), "PENDING_REVIEW");
        settlementReviewer.rejectStatement(w0.getId(), adminId, "金額有疑義", true);

        assertThat(settledStatementOf(orderId)).as("駁回是終態、資金未發生：訂單必須釋放").isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adjustment_statements WHERE id = ?",
                String.class, adjustmentId)).as("折入的調整單必須還原，否則隨死掉的結算單消失").isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT applied_statement_id FROM adjustment_statements WHERE id = ?",
                UUID.class, adjustmentId)).isNull();

        SettlementStatement w1 = generate("2027-01-11", "2027-01-17");
        assertThat(w1.getTotalOrders()).as("被駁回結算單的訂單在下一期重新結算").isEqualTo(1);
        assertThat(settledStatementOf(orderId)).isEqualTo(w1.getId());
        assertThat(w1.getAdjustmentAmount()).as("調整單被下一張結算單重新折入").isEqualByComparingTo("-100.00");
    }

    @Test
    @DisplayName("退款扣在「實際結算這筆訂單」的那張結算單，而不是下單日期落入的那張")
    void refund_isDeductedFromTheStatementThatSettledTheOrder() {
        UUID orderId = seedOrder("SHIPPING", "5000.00", "2027-01-06T12:00:00+08:00");
        SettlementStatement w0 = generate("2027-01-04", "2027-01-10"); // 涵蓋下單日 1/6，但不含這筆（尚在配送）
        setOrderStatus(orderId, "DELIVERED");
        SettlementStatement w1 = generate("2027-01-11", "2027-01-17"); // 實際結算這筆

        settlementAdjustmentService.handleOrderRefund(tenantId, orderId, new BigDecimal("100.00"));

        assertThat(totalRefundsOf(w1.getId())).as("退款必須扣在實際結算它的那張").isEqualByComparingTo("100.00");
        assertThat(totalRefundsOf(w0.getId())).as("下單日期落入、但沒有結算這筆訂單的那張不可被誤扣")
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("尚未結算的訂單退款：不動任何結算單（退款會在該訂單被結算時由 Payment.refundedAmount 帶入）")
    void refundOfUnsettledOrder_touchesNoStatement() {
        UUID orderId = seedOrder("SHIPPING", "5000.00", "2027-01-06T12:00:00+08:00");
        SettlementStatement w0 = generate("2027-01-04", "2027-01-10");

        settlementAdjustmentService.handleOrderRefund(tenantId, orderId, new BigDecimal("100.00"));

        assertThat(totalRefundsOf(w0.getId())).isEqualByComparingTo("0.00");
    }

    private BigDecimal totalRefundsOf(final UUID statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_refunds FROM settlement_statements WHERE id = ?", BigDecimal.class, statementId);
    }

    @Test
    @DisplayName("結算單 FAILED（可重試撥款）→ 訂單維持掛在該結算單，下一期不可重複結算")
    void failedStatement_keepsOrdersAttached() {
        UUID orderId = seedOrder("COMPLETED", "1000.00", "2027-01-06T12:00:00+08:00");
        SettlementStatement w0 = generate("2027-01-04", "2027-01-10");
        setStatementStatus(w0.getId(), "FAILED"); // 撥款失敗；retryFailedTransfer 會把它改回 APPROVED 重試

        SettlementStatement w1 = generate("2027-01-11", "2027-01-17");

        assertThat(w1.getTotalOrders()).as("FAILED 結算單仍可重試撥款，訂單不可被下一期再結算一次").isZero();
        assertThat(settledStatementOf(orderId)).isEqualTo(w0.getId());
    }
}
