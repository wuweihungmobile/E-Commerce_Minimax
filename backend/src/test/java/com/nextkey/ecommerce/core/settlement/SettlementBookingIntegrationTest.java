package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * 訂房納入結算整合測試（Sprint 247，DEF-353；PRD §6.2.1；真實 PostgreSQL）。
 *
 * <p>核心正確性問題：{@code bookings.refund_status = NONE} 同時代表「從未收款」（逾時取消／買家在
 * {@code CREATED} 取消）與「已收款、依政策不退款」（Q14 入住前 24 小時內取消、no-show）——兩者不能
 * 都被當成商家收益。本測試以真實 DB 逐一守住：COMPLETED 納入、CANCELLED+NONE+有付款納入、
 * CANCELLED+NONE+從未付款排除、CANCELLED+PENDING 退款排除、駁回釋放、跨期退款調整、
 * 與訂單同一張結算單彙總。mock 版（{@code SettlementAdjustmentServiceTest}）已驗證狀態分流邏輯本身，
 * 這裡驗證的是查詢層（{@code BookingRepository}）與完整生成流程的真實行為。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-BOOKING: 訂房納入結算（Sprint 247，DEF-353）")
class SettlementBookingIntegrationTest {

    @Autowired private SettlementGenerator settlementGenerator;
    @Autowired private SettlementReviewer settlementReviewer;
    @Autowired private SettlementAdjustmentService settlementAdjustmentService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID buyerId;
    private UUID adminId;
    private UUID listingId;

    @BeforeEach
    void seed() {
        String stamp = String.valueOf(System.nanoTime());
        tenantId = tenantRepository.save(Tenant.builder()
                .name("Settle Booking Tenant")
                .slug("settle-booking-" + stamp)
                .contactEmail("settle-booking-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
        buyerId = seedUser("buyer", stamp, User.UserRole.BUYER);
        adminId = seedUser("admin", stamp, User.UserRole.BUYER);
        listingId = seedListing(stamp);
    }

    private UUID seedUser(final String prefix, final String stamp, final User.UserRole role) {
        return userRepository.save(User.builder()
                .email("settle-booking-" + prefix + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Settle Booking " + prefix)
                .role(role)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build()).getId();
    }

    private UUID seedListing(final String stamp) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO listings (id, tenant_id, listing_type, title, status, owner_id, base_price, currency)
                VALUES (?, ?, 'ROOM', ?, 'ACTIVE', ?, 1000.00, 'TWD')
                """, id, tenantId, "Settle Booking Room " + stamp, buyerId);
        return id;
    }

    private UUID seedBooking(final String status, final String refundStatus, final String amount,
            final String createdAtTaipei) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO bookings (id, tenant_id, user_id, room_listing_id, check_in_date, check_out_date,
                                      guest_count, status, total_amount, discount_amount, refund_status,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, DATE '2027-02-01', DATE '2027-02-03', 2, ?, ?, 0.00, ?, ?, ?)
                """, id, tenantId, buyerId, listingId, status, new BigDecimal(amount), refundStatus,
                OffsetDateTime.parse(createdAtTaipei), OffsetDateTime.parse(createdAtTaipei));
        return id;
    }

    /** 成功付款（金額與訂房一致，refunded_amount 用 DB 預設 0）。沒有這筆代表「從未收款」。 */
    private void seedSuccessPayment(final UUID bookingId, final String amount) {
        jdbcTemplate.update("""
                INSERT INTO payments (id, booking_id, payment_method, amount, currency, status, transaction_id)
                VALUES (gen_random_uuid(), ?, 'MOCK', ?, 'TWD', 'SUCCESS', ?)
                """, bookingId, new BigDecimal(amount), "settle-booking-" + UUID.randomUUID());
    }

    private UUID settledStatementOfBooking(final UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT settled_statement_id FROM bookings WHERE id = ?", UUID.class, bookingId);
    }

    private void setStatementStatus(final UUID statementId, final String status) {
        jdbcTemplate.update("UPDATE settlement_statements SET status = ? WHERE id = ?", status, statementId);
    }

    private SettlementStatement generate(final String start, final String end) {
        return settlementGenerator.generateStatementForTenant(
                tenantId, java.time.LocalDate.parse(start), java.time.LocalDate.parse(end));
    }

    @Test
    @DisplayName("COMPLETED 訂房（退房完成）納入結算一次，之後不再重複結算")
    void completedBooking_isSettledExactlyOnce() {
        // 刻意不建付款紀錄：COMPLETED 分支本身不要求存在付款（那是 CANCELLED 分支才需要的區分條件），
        // 本測試驗證的正是這個分支邊界。
        UUID bookingId = seedBooking("COMPLETED", "NONE", "3000.00", "2027-02-04T11:00:00+08:00");

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");
        assertThat(w0.getTotalBookings()).isEqualTo(1);
        assertThat(w0.getTotalOrders()).isZero();
        assertThat(w0.getTotalGmv()).isEqualByComparingTo("3000.00");
        assertThat(settledStatementOfBooking(bookingId)).isEqualTo(w0.getId());

        SettlementStatement w1 = generate("2027-02-08", "2027-02-14");
        assertThat(w1.getTotalBookings()).as("已結算的訂房不可被下一期重複結算").isZero();
    }

    @Test
    @DisplayName("CANCELLED+refundStatus=NONE+存在成功付款（Q14 不足 24 小時取消／no-show）→ 已收款視為商家收益納入")
    void cancelledWithNoRefundAndPayment_isSettledAsRevenue() {
        UUID bookingId = seedBooking("CANCELLED", "NONE", "2500.00", "2027-02-04T11:00:00+08:00");
        seedSuccessPayment(bookingId, "2500.00");

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");

        assertThat(w0.getTotalBookings()).isEqualTo(1);
        assertThat(w0.getTotalGmv()).isEqualByComparingTo("2500.00");
        assertThat(settledStatementOfBooking(bookingId)).isEqualTo(w0.getId());
    }

    @Test
    @DisplayName("CANCELLED+refundStatus=NONE+從未收款（逾時取消／買家在 CREATED 自行取消）→ 不可被誤計為商家收益")
    void cancelledNeverPaid_isNotSettled() {
        // 沒有呼叫 seedSuccessPayment：refund_status 同樣停在建構時的預設 NONE，但從未收過錢。
        // 這是 DEF-353 的核心判斷——BookingRepository 的可結算查詢必須排除這種情況。
        UUID bookingId = seedBooking("CANCELLED", "NONE", "1800.00", "2027-02-04T11:00:00+08:00");

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");

        assertThat(w0.getTotalBookings()).as("從未收款的取消訂房不可納入結算").isZero();
        assertThat(w0.getTotalGmv()).isEqualByComparingTo("0.00");
        assertThat(settledStatementOfBooking(bookingId)).as("不被任何結算單認領，留給日後若真的被判定該收款時處理")
                .isNull();
    }

    @Test
    @DisplayName("CANCELLED+refundStatus=PENDING（等待自動退款）→ 不納入結算，款項終將退回買家")
    void cancelledPendingRefund_isNotSettled() {
        UUID bookingId = seedBooking("CANCELLED", "PENDING", "4000.00", "2027-02-04T11:00:00+08:00");
        seedSuccessPayment(bookingId, "4000.00");

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");

        assertThat(w0.getTotalBookings()).isZero();
        assertThat(settledStatementOfBooking(bookingId)).isNull();
    }

    @Test
    @DisplayName("訂房與訂單同一張結算單彙總（PRD §6.2.1：同週期、同抽成），各自分開計數")
    void bookingAndOrder_settleIntoSameStatement() {
        UUID bookingId = seedBooking("COMPLETED", "NONE", "1000.00", "2027-02-04T11:00:00+08:00");
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, user_id, order_type, status, total_amount,
                                    shipping_fee, discount_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', 'COMPLETED', 500.00, 0.00, 0.00, 'TWD', ?, ?)
                """, orderId, tenantId, buyerId,
                OffsetDateTime.parse("2027-02-04T12:00:00+08:00"), OffsetDateTime.parse("2027-02-04T12:00:00+08:00"));

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");

        assertThat(w0.getTotalBookings()).isEqualTo(1);
        assertThat(w0.getTotalOrders()).isEqualTo(1);
        assertThat(w0.getTotalGmv()).as("同一張結算單彙總兩者 GMV").isEqualByComparingTo("1500.00");
        assertThat(settledStatementOfBooking(bookingId)).isEqualTo(w0.getId());
    }

    @Test
    @DisplayName("結算單被駁回（REJECTED）→ 一併釋放它認領的訂房，下一期重新結算")
    void rejectedStatement_releasesBooking() {
        UUID bookingId = seedBooking("COMPLETED", "NONE", "2200.00", "2027-02-04T11:00:00+08:00");

        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");
        assertThat(settledStatementOfBooking(bookingId)).isEqualTo(w0.getId());

        setStatementStatus(w0.getId(), "PENDING_REVIEW");
        settlementReviewer.rejectStatement(w0.getId(), adminId, "金額有疑義", true);

        assertThat(settledStatementOfBooking(bookingId)).as("駁回是終態、資金未發生：訂房必須釋放").isNull();

        SettlementStatement w1 = generate("2027-02-08", "2027-02-14");
        assertThat(w1.getTotalBookings()).as("被駁回結算單的訂房在下一期重新結算").isEqualTo(1);
        assertThat(settledStatementOfBooking(bookingId)).isEqualTo(w1.getId());
    }

    @Test
    @DisplayName("已結算的 no-show 訂房事後被管理員人工退款（DEF-354）→ APPROVED 結算單產生 booking_id 調整單")
    void bookingRefundAfterSettlement_onApprovedStatement_createsAdjustmentWithBookingId() {
        UUID bookingId = seedBooking("CANCELLED", "NONE", "1600.00", "2027-02-04T11:00:00+08:00");
        seedSuccessPayment(bookingId, "1600.00");
        SettlementStatement w0 = generate("2027-02-01", "2027-02-07");
        setStatementStatus(w0.getId(), "APPROVED");

        settlementAdjustmentService.handleBookingRefund(tenantId, bookingId, new BigDecimal("1600.00"));

        java.util.Map<String, Object> adjustment = jdbcTemplate.queryForMap(
                "SELECT booking_id, order_id, amount, status FROM adjustment_statements WHERE original_statement_id = ?",
                w0.getId());
        assertThat(adjustment.get("booking_id")).isEqualTo(bookingId);
        assertThat(adjustment.get("order_id")).as("訂房調整單不可誤填 order_id").isNull();
        assertThat((BigDecimal) adjustment.get("amount")).isEqualByComparingTo("-1600.00");
        assertThat(adjustment.get("status")).isEqualTo("PENDING");
    }
}
