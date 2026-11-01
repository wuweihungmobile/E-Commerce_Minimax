package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 核准後的撥款觸發失敗，不可連帶回滾「核准」這個管理員已做的決定（DEF-443，Sprint 321）。
 *
 * <p>設計意圖（{@code SettlementReviewer.approveStatement} 的註解、{@code TransferService} 類別說明、API_M07 §1.2 第 3 點）：
 * 核准與撥款解耦——撥款失敗只記錄，核准照樣成立。修復前的實測（Sprint 320）：{@code createTransferForStatement} 是
 * {@code @Transactional}（加入核准的交易），任何 {@code RuntimeException} 穿出它的交易代理時，核准的交易就被標成
 * rollback-only；{@code approveStatement} 的 {@code try/catch} 吞掉例外也沒用，提交時 {@code UnexpectedRollbackException}，
 * 核准連同稽核整個被回滾、HTTP {@code 500 E-9900}。
 *
 * <p>這裡用 {@code @MockBean} 的 {@link PaymentGatewayFactory} 讓閘道丟出非預期例外（第二個、與 Sprint 320 實測的
 * 「transfer 佔位撞唯一索引」位置與型別都不同的觸發點）。店鋪設成 Connect 帳戶就緒且撥款功能開關開啟，核准後的撥款觸發才會真的
 * 走到閘道（測試租戶預設不開，transfer 會是 {@code SKIPPED_ONBOARDING_INCOMPLETE}）。其餘全部是真的：HTTP／JWT／權限鏈、
 * 真實 PostgreSQL、真實交易。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-SETTLE-PAYOUT-TRIGGER: 核准後撥款觸發失敗不可回滾核准，且之後可重試（DEF-443）")
class SettlementApprovalPayoutTriggerRealStackIntegrationTest {

    private static final String STRIPE = "STRIPE";

    @MockBean private PaymentGatewayFactory paymentGatewayFactory;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ContractFixture fx;
    private String connectAccountId;
    private UUID statementId;

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        connectAccountId = "acct_it_" + System.nanoTime();
        jdbcTemplate.update("UPDATE tenants SET stripe_connect_account_id = ?, connect_onboarding_status = 'COMPLETE', "
                + "connect_charges_enabled = TRUE, connect_payouts_enabled = TRUE WHERE id = ?",
                connectAccountId, fx.store.getId());
        jdbcTemplate.update("INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, created_at, "
                + "updated_at) VALUES (?, ?, 'STRIPE_TRANSFER_ENABLED', TRUE, NOW(), NOW())",
                UUID.randomUUID(), fx.store.getId());
        statementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-12-28', DATE '2027-01-03', 0, 1000.00, 0.00, 100.00, 900.00,
                        'TWD', 'PENDING_REVIEW', NOW(), NOW(), NOW())
                """, statementId, fx.store.getId(), "IT-S321-" + System.nanoTime());
    }

    // ── 核心：非預期的閘道例外 ──────────────────────────────────────

    /**
     * 閘道丟出的不是 {@code BusinessException}（例如連線層的 {@code IllegalStateException}），{@code TransferService}
     * 內建的 FAILED 記錄只接 {@code BusinessException}，所以這個例外會一路穿出 {@code createTransferForStatement}。
     */
    @Test
    @DisplayName("閘道丟出非預期例外：核准照樣成功（200、APPROVED、有審核人與稽核）；不留卡在 PENDING 的 transfer 佔位")
    void unexpectedGatewayFailure_doesNotRollBackTheApproval() throws Exception {
        when(paymentGatewayFactory.createTransfer(eq(STRIPE), eq(connectAccountId), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("模擬閘道連線層當機"));

        MockHttpServletResponse approved = approve();

        State state = stateOf();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("核准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(state.status).as("核准是管理員已做的決定，撥款失敗不可讓它消失：%s", state).isEqualTo("APPROVED");
            softly.assertThat(state.reviewedBy).as("審核人：%s", state).isEqualTo(fx.admin.getId());
            softly.assertThat(state.approvedAudits).as("核准稽核：%s", state).isEqualTo(1);
            softly.assertThat(state.transfers)
                    .as("撥款觸發的寫入要整個回滾，不能留下卡在 PENDING 的佔位（它會擋住之後所有重試）：%s", state).isEmpty();
        });
    }

    /**
     * 非預期失敗之後，結算單停在 {@code APPROVED}、沒有任何 transfer 記錄。管理員必須有辦法補撥款——否則核准成立卻永遠不撥款。
     * 既有的重試端點只接受 FAILED／SKIPPED 的 transfer，所以這個情境也要能走它。
     */
    @Test
    @DisplayName("非預期失敗之後（APPROVED、沒有 transfer）：管理員用既有的重試端點補撥款成功（結算單 PAID、transfer COMPLETED）")
    void afterUnexpectedFailure_adminCanRetryThePayout() throws Exception {
        when(paymentGatewayFactory.createTransfer(eq(STRIPE), eq(connectAccountId), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("模擬閘道連線層當機"));
        MockHttpServletResponse approved = approve();

        // 閘道恢復
        doReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_it_recovered").build())
                .when(paymentGatewayFactory).createTransfer(eq(STRIPE), eq(connectAccountId), anyLong(), any(), any());
        MockHttpServletResponse retried = retry();

        State state = stateOf();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("核准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(retried.getStatus()).as("重試：%s", safeBody(retried)).isEqualTo(200);
            softly.assertThat(state.status).as("補撥款之後：%s", state).isEqualTo("PAID");
            softly.assertThat(state.paid).as("付款時間：%s", state).isTrue();
            softly.assertThat(state.transfers).as("transfer：%s", state)
                    .containsExactly(new TransferRow("COMPLETED", "tr_it_recovered", "900.00", null));
        });
    }

    // ── 對照：本來就正確的路徑 ───────────────────────────────────────

    /** 已知的閘道失敗（Stripe 拒絕等，閘道包成 {@code BusinessException}）本來就走 FAILED 記錄，核准照樣成立。 */
    @Test
    @DisplayName("對照：閘道回報已知失敗（BusinessException）→ transfer／結算單記為 FAILED（可重試）；核准照樣成立")
    void knownGatewayFailure_isRecordedAsFailed() throws Exception {
        when(paymentGatewayFactory.createTransfer(eq(STRIPE), eq(connectAccountId), anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.E_6010, "insufficient platform balance"));

        MockHttpServletResponse approved = approve();

        State state = stateOf();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("核准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("FAILED");
            softly.assertThat(state.reviewedBy).as("審核人：%s", state).isEqualTo(fx.admin.getId());
            softly.assertThat(state.approvedAudits).as("核准稽核：%s", state).isEqualTo(1);
            softly.assertThat(state.transfers).as("transfer：%s", state).extracting(TransferRow::status)
                    .containsExactly("FAILED");
            softly.assertThat(state.transfers).as("transfer：%s", state).extracting(TransferRow::amount)
                    .containsExactly("900.00");
            // 失敗原因是 BusinessException 組出的訊息（錯誤碼的預設說明＋閘道的說明），這裡只要求帶著閘道的說明
            softly.assertThat(state.transfers).as("失敗原因要帶著閘道回報的說明：%s", state).extracting(TransferRow::failureReason)
                    .allMatch(reason -> reason != null && reason.contains("insufficient platform balance"));
        });
    }

    /** 閘道成功：核准後撥款完成，送給 Stripe 的是淨額 90000 分，冪等鍵是結算單 id。 */
    @Test
    @DisplayName("對照：閘道成功 → 核准後撥款完成（結算單 PAID、transfer COMPLETED 帶 Stripe id）；送給 Stripe 的是淨額 90000 分")
    void gatewaySuccess_paysOutAfterApproval() throws Exception {
        when(paymentGatewayFactory.createTransfer(eq(STRIPE), eq(connectAccountId), anyLong(), any(), any()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_it_ok").build());

        MockHttpServletResponse approved = approve();

        State state = stateOf();
        verify(paymentGatewayFactory).createTransfer(STRIPE, connectAccountId, 90000L, "TWD", statementId.toString());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).as("核准：%s", safeBody(approved)).isEqualTo(200);
            softly.assertThat(state.status).as("結算單：%s", state).isEqualTo("PAID");
            softly.assertThat(state.paid).as("付款時間：%s", state).isTrue();
            softly.assertThat(state.reviewedBy).as("審核人：%s", state).isEqualTo(fx.admin.getId());
            softly.assertThat(state.approvedAudits).as("核准稽核：%s", state).isEqualTo(1);
            softly.assertThat(state.transfers).as("transfer：%s", state)
                    .containsExactly(new TransferRow("COMPLETED", "tr_it_ok", "900.00", null));
        });
    }

    // ── 輔助 ─────────────────────────────────────────────────────

    private record TransferRow(String status, String stripeTransferId, String amount, String failureReason) { }

    private record State(String status, UUID reviewedBy, boolean paid, long approvedAudits, List<TransferRow> transfers) { }

    private State stateOf() {
        var row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, paid_at FROM settlement_statements WHERE id = ?", statementId);
        List<TransferRow> transfers = jdbcTemplate.query(
                "SELECT status, stripe_transfer_id, transfer_amount, failure_reason FROM transfers "
                        + "WHERE settlement_statement_id = ? ORDER BY created_at",
                (rs, i) -> new TransferRow(rs.getString("status"), rs.getString("stripe_transfer_id"),
                        money(rs.getBigDecimal("transfer_amount")), rs.getString("failure_reason")),
                statementId);
        Long approvedAudits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'SETTLEMENT_STATEMENT' AND entity_id = ? "
                        + "AND action = 'SETTLEMENT_APPROVED'", Long.class, statementId);
        return new State((String) row.get("status"), (UUID) row.get("reviewed_by"), row.get("paid_at") != null,
                approvedAudits == null ? 0 : approvedAudits, transfers);
    }

    private static String money(final BigDecimal amount) {
        return amount == null ? "<null>" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private MockHttpServletResponse approve() throws Exception {
        return send(put("/v2/admin/settlements/{id}/approve", statementId));
    }

    private MockHttpServletResponse retry() throws Exception {
        return send(post("/v2/admin/transfers/{id}/retry", statementId));
    }

    private MockHttpServletResponse send(final MockHttpServletRequestBuilder builder) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + fx.adminToken);
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static String safeBody(final MockHttpServletResponse response) {
        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            return "<unreadable>";
        }
    }
}
