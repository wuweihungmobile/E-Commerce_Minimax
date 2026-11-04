package com.nextkey.ecommerce.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.erp.PurchaseOrderService;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * 業務編號撞唯一約束「輸了」的那一方，在真實 PostgreSQL 上要得到乾淨的結果（DEF-297，Sprint 325）。
 *
 * <p>共同背景與做法同 {@link UniqueConstraintRaceRealStackIntegrationTest}（DEF-441）：PostgreSQL 在交易內任何一個語句失敗後，
 * 整個交易進入中止狀態，所以輸家只能把整個交易丟掉、在全新的交易裡重做（{@code UniqueConflictRetry}）。真實競態不靠 mock：
 * 一個「對手」交易先寫入會跟請求撞唯一約束的那一列但<strong>不提交</strong>並停住；另一個執行緒用 MockMvc 走完整條
 * HTTP／JWT／權限鏈送出請求，它的 INSERT 會卡在對手的唯一索引項目上（用 {@code pg_stat_activity} 確認真的卡住了，不靠睡眠）；
 * 放行對手提交後，請求的 INSERT 才以唯一約束違反失敗。
 *
 * <p>採購單號是<b>隨機</b>的（{@code PO-日期-6 位隨機數}），測試無從預知會產生哪個號碼，所以用
 * {@link PurchaseOrderService} 的單號後綴接縫把它換成固定序列：第一次產生對手手上的那個號碼（真實撞號），之後產生全新的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-NUMBER-RACE: 業務編號撞號輸了的那一方要得到乾淨的結果（DEF-297）")
class BusinessNumberRaceRealStackIntegrationTest {

    private static final int AWAIT_SECONDS = 30;
    private static final int SUFFIX_MIN = 100000;
    private static final int SUFFIX_MAX = 999999;
    private static final String PO_PATH = "/v2/dashboard/purchase-orders";
    private static final String SUFFIX_FIELD = "poNumberSuffix";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PurchaseOrderService purchaseOrderService;

    private ContractFixture fx;
    private ExecutorService pool;
    private UUID supplierId;
    private Object realSuffixSource;

    @BeforeEach
    void setUp() {
        fx = new ContractFixture(tenantRepository, userRepository, listingRepository, jdbcTemplate, jwtTokenService);
        pool = Executors.newCachedThreadPool();
        realSuffixSource = ReflectionTestUtils.getField(purchaseOrderService, SUFFIX_FIELD);
        // 建立採購單要 ERP_ENABLED：建立店鋪時系統已自動種好預設開關（ERP_ENABLED 預設啟用），不可再插一筆
        // （同一家店兩筆同鍵開關會讓 FeatureToggleService 的單筆查詢拋 IncorrectResultSizeDataAccessException）。
        // 並且要有屬於這家店的供應商。
        supplierId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO suppliers (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", supplierId, fx.store.getId(), "競態測試供應商");
    }

    @AfterEach
    void tearDown() {
        // 若案例中途失敗，仍被擋著的對手交易在這裡被中斷並回滾，不在資料庫留下開著的交易
        pool.shutdownNow();
        // 還原真正的單號亂數來源，不影響其他測試類別
        ReflectionTestUtils.setField(purchaseOrderService, SUFFIX_FIELD, realSuffixSource);
    }

    // ── 採購單號（PurchaseOrderService.createPurchaseOrder，DEF-297）────────────────────────

    /**
     * 兩個建單請求產生了同一個單號（隨機數撞上）：後到的那一個 INSERT 撞上唯一約束。它要整個交易重做、換一個新單號再試，
     * 回 {@code 201}，而不是 {@code 500 E-9900}；失敗那一次的交易整個回滾，不留下半套的列（沒有孤兒品項、沒有第二張採購單）。
     */
    @Test
    @DisplayName("採購單建立：單號跟對手撞上（唯一約束衝突）→ 整個交易重做、換新單號，201；沒有留下半套的列")
    void createPurchaseOrder_losingTheNumberRace_retriesWithAFreshNumber() throws Exception {
        int taken = freeSuffix(-1);
        int fresh = freeSuffix(taken);
        AtomicInteger generated = useSuffixes(taken, fresh);

        // 對手：同一個單號已經寫入但還沒提交
        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update(
                "INSERT INTO purchase_orders (id, tenant_id, supplier_id, po_number, status, total_amount, currency, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, 'DRAFT', 1.00, 'TWD', NOW(), NOW())",
                UUID.randomUUID(), fx.store.getId(), supplierId, poNumber(taken)));
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(
                    () -> send(post(PO_PATH).content(purchaseOrderPayload()), fx.ownerToken));
            awaitInsertBlockedOn("purchase_orders");
            Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
            rival.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            rival.release();
        }

        Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(201);
        Assertions.assertThat(json(loser).path("data").path("poNumber").asText())
                .as("輸家的回應：%s", body(loser)).isEqualTo(poNumber(fresh));
        Assertions.assertThat(generated.get()).as("單號被產生的次數（第 1 次撞號、第 2 次成功）").isEqualTo(2);
        // 失敗那一次的交易整個回滾：只剩對手那張和重做成功的那張
        List<String> numbers = jdbcTemplate.queryForList(
                "SELECT po_number FROM purchase_orders WHERE tenant_id = ?", String.class, fx.store.getId());
        Assertions.assertThat(numbers).as("這家店的採購單單號").containsExactlyInAnyOrder(poNumber(taken), poNumber(fresh));
        Integer items = jdbcTemplate.queryForObject("SELECT count(*) FROM purchase_order_items i "
                + "JOIN purchase_orders p ON p.id = i.purchase_order_id WHERE p.tenant_id = ?",
                Integer.class, fx.store.getId());
        Assertions.assertThat(items).as("品項只屬於重做成功的那張採購單").isEqualTo(1);
    }

    /** 對照組：沒有撞號時單號只產生一次，格式不變（{@code PO-yyyyMMdd-6 位數}）；讓「多重試一次」之類的過度重試有東西可殺。 */
    @Test
    @DisplayName("採購單建立：沒有撞號時單號只產生一次、格式 PO-{yyyyMMdd}-{6 位數}（對照組）")
    void createPurchaseOrder_withoutCollision_generatesTheNumberOnce() throws Exception {
        int only = freeSuffix(-1);
        AtomicInteger generated = useSuffixes(only);

        MockHttpServletResponse created = send(post(PO_PATH).content(purchaseOrderPayload()), fx.ownerToken);

        Assertions.assertThat(created.getStatus()).as("回應：%s", body(created)).isEqualTo(201);
        String number = json(created).path("data").path("poNumber").asText();
        Assertions.assertThat(number).isEqualTo(poNumber(only)).matches("PO-\\d{8}-\\d{6}");
        Assertions.assertThat(generated.get()).as("單號被產生的次數").isEqualTo(1);
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private static String poNumber(final int suffix) {
        return "PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }

    /** 挑一個今天還沒被任何採購單用掉的後綴（共用資料庫裡其他測試建的採購單也算），且不等於 {@code other}。 */
    private int freeSuffix(final int other) {
        for (;;) {
            int candidate = ThreadLocalRandom.current().nextInt(SUFFIX_MIN, SUFFIX_MAX);
            Integer used = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM purchase_orders WHERE po_number = ?", Integer.class, poNumber(candidate));
            if (candidate != other && used != null && used == 0) {
                return candidate;
            }
        }
    }

    /**
     * 把單號後綴的來源換成固定序列：第 n 次產生回傳 {@code sequence} 的第 n 個，超過就一直回最後一個。
     * 回傳的計數器記錄單號被產生了幾次（也就是交易被做了幾次）。
     */
    private AtomicInteger useSuffixes(final int... sequence) {
        AtomicInteger generated = new AtomicInteger();
        IntSupplier source = () -> sequence[Math.min(generated.getAndIncrement(), sequence.length - 1)];
        ReflectionTestUtils.setField(purchaseOrderService, SUFFIX_FIELD, source);
        return generated;
    }

    private String purchaseOrderPayload() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "supplierId", supplierId,
                "notes", "單號競態測試",
                "items", List.of(Map.of("listingId", fx.productId, "quantity", 2, "unitCost", 50.00))));
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
