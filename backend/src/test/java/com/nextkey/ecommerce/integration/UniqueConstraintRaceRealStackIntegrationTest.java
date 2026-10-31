package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.settlement.TransferService;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * 唯一約束競態「輸了」的那一方，在真實 PostgreSQL 上要得到乾淨的結果（DEF-441，Sprint 319）。
 *
 * <p>共同背景：PostgreSQL 在交易內任何一個語句失敗後，整個交易進入中止狀態（{@code current transaction is aborted}），
 * 之後的任何語句都會失敗；而且例外穿出 repository 的交易代理時，外層交易也被標成 rollback-only。所以「捕捉
 * {@code DataIntegrityViolationException} 之後在同一個交易裡改讀／重試」在 PostgreSQL 上永遠不成立
 * （{@code DEF-310} 早記過同一個陷阱）。mock Repository 沒有交易中止語意，所以原本的單元測試看不到。
 *
 * <p>做法（真實競態，不靠 mock）：一個「對手」交易先寫入會跟請求撞唯一約束的那一列但<strong>不提交</strong>並停住；
 * 另一個執行緒用 MockMvc 走完整條 HTTP／JWT／權限鏈送出請求，它的 INSERT 會卡在對手的唯一索引項目上
 * （用 {@code pg_stat_activity} 確認真的卡住了，不靠睡眠）；放行對手提交後，請求的 INSERT 才以唯一約束違反失敗。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-UNIQUE-RACE: 唯一約束競態輸了的那一方要得到乾淨的結果（DEF-441）")
class UniqueConstraintRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SettlementStatementRepository settlementRepository;
    @Autowired private TransferService transferService;

    private ContractFixture fx;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        pool = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        // 若案例中途失敗，仍被擋著的對手交易在這裡被中斷並回滾，不在資料庫留下開著的交易
        pool.shutdownNow();
    }

    // ── 標記維護：補缺漏日曆列（RoomCalendarService.fillMissingCalendarEntries）─────────────────

    /**
     * 兩個交易同時為同一間「全新、沒有任何日曆列」的房間的同一天補列：後到的標記維護請求，不能回 500。
     * 它要比照 {@code bookDateRange} 的做法回 {@code 400 E-4001}（該日期正被另一個操作修改，使用者重試即可），
     * 整個交易回滾（沒有留下半套的列）；重試就成功。
     */
    @Test
    @DisplayName("標記維護：補缺漏日曆列時輸給另一個交易（唯一約束衝突）→ 400 E-4001（可重試），不是 500 E-9900；沒有留下半套的列；重試成功")
    void markMaintenance_losingTheMissingRowRace_returnsRetryableE4001() throws Exception {
        String ci = day(120);
        String co = day(122);
        String path = "/v2/dashboard/rooms/" + fx.roomId + "/maintenance";
        String payload = objectMapper.writeValueAsString(Map.of("startDate", ci, "endDate", co));

        // 對手：同一間房、第一晚的列已經寫入但還沒提交
        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                "INSERT INTO room_calendar (id, room_listing_id, calendar_date, status) VALUES (?, ?, ?, 'AVAILABLE')",
                UUID.randomUUID(), fx.roomId, Date.valueOf(ci)));
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(
                    () -> send(post(path).content(payload), fx.ownerToken));
            awaitInsertBlockedOn("room_calendar");
            Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
            rival.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            rival.release();
        }

        Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(400);
        Assertions.assertThat(json(loser).path("code").asText()).as("輸家的回應：%s", body(loser)).isEqualTo("E-4001");
        // 交易整個回滾：只剩對手那一列，第二晚沒有被補出來，也沒有任何一晚被標成維護
        List<Map<String, Object>> rows = calendarRows(fx.roomId, ci, co);
        Assertions.assertThat(rows).as("輸家回滾後房間的日曆列").hasSize(1);
        Assertions.assertThat(rows.get(0).get("status")).isEqualTo("AVAILABLE");

        // 重試：對手已提交，這次拿得到那一列的鎖，兩晚都成為維護中
        MockHttpServletResponse retry = send(post(path).content(payload), fx.ownerToken);
        Assertions.assertThat(retry.getStatus()).as("重試的回應：%s", body(retry)).isEqualTo(200);
        List<Map<String, Object>> after = calendarRows(fx.roomId, ci, co);
        Assertions.assertThat(after).as("重試後房間的日曆列").hasSize(2);
        Assertions.assertThat(after).allSatisfy(night -> Assertions.assertThat(night.get("status")).isEqualTo("MAINTENANCE"));
    }

    // ── 發起聊天：搶輸的人要拿到贏家建立的那一筆對話（ChatService.createConversation，DEF-118）─────────

    /**
     * 兩個「發起聊天」請求同時送出（例如使用者連點兩下按鈕）：兩個都通過「是否已有對話」的檢查，各自 INSERT，輸的那個撞上
     * V81 的部分唯一索引。DEF-118 的本意是「搶輸時比照『已有對話則直接回傳』，回傳贏家的那一筆，不建立第二筆」——所以輸家要回 200
     * 和贏家的對話，不是 500。
     *
     * <p>整合測試資料庫是 Hibernate 建的，沒有 Flyway V81 的索引；這裡建一個只涵蓋本案例發起人的同形索引，收尾移除，
     * 不影響其他測試。
     */
    @Test
    @DisplayName("發起聊天：同一對 (發起人, 對象) 的 active 對話被對手搶先建立（唯一索引衝突）→ 200 且回傳贏家的那一筆對話，不是 500；只剩一筆 active 對話")
    void createConversation_losingTheRace_returnsTheWinnersConversation() throws Exception {
        String indexName = "idx_it_race_conv_" + fx.buyer.getId().toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON conversations (initiator_id, recipient_id) WHERE is_active = true AND initiator_id = '"
                + fx.buyer.getId() + "'");
        try {
            UUID winnersConversation = UUID.randomUUID();
            String payload = objectMapper.writeValueAsString(
                    Map.of("recipientId", fx.other.getId(), "conversationType", "DIRECT"));

            HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                    "INSERT INTO conversations (id, tenant_id, conversation_type, initiator_id, recipient_id, "
                            + "initiator_unread_count, recipient_unread_count, is_active, created_at, updated_at) "
                            + "VALUES (?, ?, 'DIRECT', ?, ?, 0, 0, true, NOW(), NOW())",
                    winnersConversation, ContractFixture.SYSTEM_TENANT_ID, fx.buyer.getId(), fx.other.getId()));
            MockHttpServletResponse loser;
            try {
                Future<MockHttpServletResponse> request = pool.submit(
                        () -> send(post("/v2/chat/conversations").content(payload), fx.buyerToken));
                awaitInsertBlockedOn("conversations");
                Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
                rival.commit();
                loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            } finally {
                rival.release();
            }

            Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(200);
            Assertions.assertThat(json(loser).at("/data/conversationId").asText())
                    .as("輸家要拿到贏家建立的那一筆對話；回應：%s", body(loser)).isEqualTo(winnersConversation.toString());
            Integer active = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM conversations WHERE initiator_id = ? AND recipient_id = ? AND is_active = true",
                    Integer.class, fx.buyer.getId(), fx.other.getId());
            Assertions.assertThat(active).as("同一對的 active 對話只剩一筆").isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
        }
    }

    // ── 建立客服工單：單號撞號要無感重試（SupportTicketService.createTicket，DEF-139）──────────────────

    /**
     * 兩個請求同時建立工單，算出同一個單號（單號是「今天已有幾張」+1，check-then-act）：輸的那個撞上 {@code ticket_number}
     * 的唯一約束。DEF-139 的本意是「單號是系統產生的，重新產生再試，使用者完全無感」——所以輸家要回 200 和下一個單號，不是 500。
     */
    @Test
    @DisplayName("建立客服工單：算出的單號被對手搶先用掉（唯一約束衝突）→ 200 且改用下一個單號（無感重試），不是 500；兩張工單都在")
    void createTicket_ticketNumberTakenByRival_retriesWithTheNextNumber() throws Exception {
        String prefix = "TK-" + java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Long already = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM support_tickets WHERE ticket_number LIKE ?", Long.class, prefix + "%");
        String contested = prefix + String.format("%03d", already + 1);
        String next = prefix + String.format("%03d", already + 2);
        String payload = objectMapper.writeValueAsString(
                Map.of("category", "OTHER", "subject", "撞號測試", "description", "兩個請求同時建立工單"));

        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                "INSERT INTO support_tickets (id, ticket_number, category, subject, description, status, priority, "
                        + "customer_id, created_at, updated_at) VALUES (?, ?, 'OTHER', '對手的工單', '先搶到單號', 'OPEN', "
                        + "'NORMAL', ?, NOW(), NOW())",
                UUID.randomUUID(), contested, fx.other.getId()));
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(
                    () -> send(post("/v2/support/tickets").content(payload), fx.buyerToken));
            awaitInsertBlockedOn("support_tickets");
            Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
            rival.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            rival.release();
        }

        Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(200);
        Assertions.assertThat(json(loser).at("/data/ticketNumber").asText())
                .as("輸家改用下一個單號；回應：%s", body(loser)).isEqualTo(next);
        List<String> numbers = jdbcTemplate.queryForList(
                "SELECT ticket_number FROM support_tickets WHERE ticket_number IN (?, ?) ORDER BY ticket_number",
                String.class, contested, next);
        Assertions.assertThat(numbers).as("對手與輸家的工單都在").containsExactly(contested, next);
    }

    // ── 結算單 transfer 佔位：輸了要乾淨地失敗（TransferService.claimTransferSlotOrExisting）─────────────

    /**
     * 兩個請求同時為同一張結算單建立 transfer：先以唯一索引原子性佔位，輸的那個撞上 {@code idx_transfers_settlement_statement_id}。
     * 原本的 catch 想「回傳既有記錄」而在同一個交易裡重查，PostgreSQL 在唯一約束違反後交易已中止，重查必然失敗。
     *
     * <p>這裡刻意<strong>不</strong>讓輸家改回傳贏家的列：呼叫端 {@code SettlementReviewer.approveStatement} 在自己的交易裡
     * 先把結算單改成 APPROVED 才呼叫本服務，輸家若「成功」並提交，會把贏家已推進到 PAID 的結算單覆寫回 APPROVED（遺失更新）；
     * 讓整個操作乾淨地失敗、輸家的交易回滾才是安全的。所以斷言的是：輸家得到明確的業務錯誤 E-5014（而不是 PostgreSQL
     * 的交易中止錯誤）、資料庫只剩贏家那一筆 transfer（不會撥款兩次）、結算單沒被輸家動到。
     *
     * <p>整合測試資料庫是 Hibernate 建的，沒有 Flyway V64 的索引；這裡建一個只涵蓋本案例結算單的同形索引，收尾移除。
     */
    @Test
    @DisplayName("結算單 transfer 佔位：輸給另一個交易（唯一索引衝突）→ 明確的 E-5014（不是 PostgreSQL 交易中止錯誤）；只剩贏家那一筆 transfer，結算單仍是 APPROVED")
    void createTransfer_losingTheClaimRace_failsCleanlyWithE5014() throws Exception {
        SettlementStatement statement = settlementRepository.save(SettlementStatement.builder()
                .tenant(fx.store).statementNumber("IT-RACE-" + System.nanoTime())
                .periodStart(LocalDate.now().minusDays(7)).periodEnd(LocalDate.now().minusDays(1))
                .netSettlementAmount(new BigDecimal("1000.00")).currency("TWD")
                .status(SettlementStatement.SettlementStatus.APPROVED).build());
        String indexName = "idx_it_race_transfers_" + statement.getId().toString().replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON transfers (settlement_statement_id) WHERE settlement_statement_id = '" + statement.getId() + "'");
        try {
            HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                    "INSERT INTO transfers (id, settlement_statement_id, tenant_id, transfer_amount, currency, status, "
                            + "created_at, updated_at) VALUES (?, ?, ?, 1000.00, 'TWD', 'SKIPPED_ONBOARDING_INCOMPLETE', "
                            + "NOW(), NOW())",
                    UUID.randomUUID(), statement.getId(), fx.store.getId()));
            Throwable loserFailure;
            try {
                Future<Transfer> request = pool.submit(() -> transferService.createTransferForStatement(statement.getId()));
                awaitInsertBlockedOn("transfers");
                Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
                rival.commit();
                loserFailure = org.assertj.core.api.Assertions.catchThrowable(() -> request.get(AWAIT_SECONDS, TimeUnit.SECONDS));
            } finally {
                rival.release();
            }

            Assertions.assertThat(loserFailure).as("輸家應該失敗").isInstanceOf(ExecutionException.class);
            Throwable cause = loserFailure.getCause();
            Assertions.assertThat(cause).as("輸家的失敗原因：%s", cause).isInstanceOf(BusinessException.class);
            Assertions.assertThat(((BusinessException) cause).getErrorCode()).isEqualTo(ErrorCode.E_5014);
            Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM transfers WHERE settlement_statement_id = ?", Integer.class, statement.getId()))
                    .as("只剩贏家那一筆 transfer（不會撥款兩次）").isEqualTo(1);
            Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM settlement_statements WHERE id = ?", String.class, statement.getId()))
                    .as("結算單沒被輸家動到").isEqualTo("APPROVED");
        } finally {
            jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
        }
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private static String day(final int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow).toString();
    }

    private List<Map<String, Object>> calendarRows(final UUID roomId, final String checkIn, final String checkOut) {
        return jdbcTemplate.queryForList(
                "SELECT status, booking_id FROM room_calendar WHERE room_listing_id = ? AND calendar_date >= ? "
                        + "AND calendar_date < ? ORDER BY calendar_date",
                roomId, Date.valueOf(checkIn), Date.valueOf(checkOut));
    }

    private MockHttpServletResponse send(final MockHttpServletRequestBuilder builder, final String token) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static String body(final MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode json(final MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(body(response));
    }

    /**
     * 一個開著、尚未提交的交易：先寫入一列，然後停住，直到 {@link #commit()} 才提交。輸家的 INSERT 因此被它尚未提交的
     * 唯一索引項目擋住。{@link #release()} 是收尾用的（已提交時無作用），確保案例中途失敗也不留下開著的交易。
     */
    private final class HeldTransaction {

        private final CountDownLatch written = new CountDownLatch(1);
        private final CountDownLatch releaseSignal = new CountDownLatch(1);
        private final Future<?> finished;

        HeldTransaction(final Consumer<JdbcTemplate> write) throws Exception {
            finished = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        write.accept(jdbcTemplate);
                        written.countDown();
                        try {
                            releaseSignal.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    });
                } finally {
                    written.countDown();
                }
            });
            Assertions.assertThat(written.await(AWAIT_SECONDS, TimeUnit.SECONDS)).as("對手交易在時限內寫入").isTrue();
            if (finished.isDone()) {
                finished.get();
            }
        }

        void commit() throws Exception {
            releaseSignal.countDown();
            finished.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }

        void release() {
            releaseSignal.countDown();
        }
    }

    private HeldTransaction holdTransaction(final Consumer<JdbcTemplate> write) throws Exception {
        return new HeldTransaction(write);
    }

    /**
     * 輸家的請求已經卡在「等對方交易提交」上：{@code pg_stat_activity} 裡有一條正在等 {@code transactionid} 鎖、
     * 而且正在執行對 {@code table} 的 INSERT。用它取代睡眠——睡多久都不能證明請求真的卡在唯一索引上。
     */
    private void awaitInsertBlockedOn(final String table) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                            + "AND wait_event_type = 'Lock' AND wait_event = 'transactionid' AND query ILIKE ?",
                    Integer.class, "%insert into " + table + "%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等不到對 " + table + " 的 INSERT 卡在另一個交易的唯一索引項目上");
    }
}
