package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;
import com.nextkey.ecommerce.domain.model.support.SupportTicket;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.returns.ReturnRequestRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.domain.repository.settlement.TransferRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportMessageRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 沒有店鋪的使用者不得碰到店家層資料（Sprint 234，真實 PostgreSQL，走完整 HTTP＋JWT＋權限＋租戶過濾鏈）。
 *
 * <p>沒有加入任何店鋪的使用者（一般買家）的租戶不是 null，而是系統租戶佔位值
 * （{@link AppConstants#SYSTEM_TENANT_ID}），所有這類使用者共用同一個。而一般消費者建立的資料（訂單、
 * 客服工單、退貨申請）也蓋成這個租戶。店家層端點若直接拿「呼叫者的租戶」去查或比對，任一買家就等於
 * 「同租戶」。Sprint 232 修了訂單與訂房列表；本類別守住同型的其餘四組：客服工單（含以店員身分發言）、
 * 退貨申請、結算單、撥款記錄。BUYER 都持有這些端點要求的權限（support_ticket:read／create、return:read、
 * order:read），所以光靠權限擋不住。
 *
 * <p>資料刻意直接以 repository 蓋成系統租戶，而非走「買家下單→開工單」的流程：Sprint 235 起訂單會改蓋成
 * 商品所屬店鋪的租戶（DEF-319），但這道防線——系統租戶的呼叫者看不到任何店家層資料——與資料怎麼來無關，
 * 測試不該隨之失效。每個案例都有「真正的店鋪店主照常看得到自己的資料」的對照，避免守門變成「對所有人都回空」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 234: 沒有店鋪的使用者看不到店家層客服工單／退貨／結算／撥款")
class SystemTenantIsolationE2ETest {

    private static final String PASSWORD = "SecurePass123!";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    /** 刻意用遠古期間：不會與其他測試（或每週結算）替系統租戶產生的結算單撞到「同租戶同期間」。 */
    private static final LocalDate PERIOD_START = LocalDate.of(2000, 1, 3);
    private static final LocalDate PERIOD_END = LocalDate.of(2000, 1, 9);
    private static final String IMPERSONATION = "我是客服人員，請提供您的信用卡末四碼以便退款";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private SupportTicketRepository supportTicketRepository;
    @Autowired private SupportMessageRepository supportMessageRepository;
    @Autowired private ReturnRequestRepository returnRequestRepository;
    @Autowired private SettlementStatementRepository settlementStatementRepository;
    @Autowired private TransferRepository transferRepository;

    // 兩個真實的、沒有店鋪的消費者
    private String tokenA;
    private String tokenB;
    // 一間真實的店鋪與它的店主（對照組）
    private String storeOwnerToken;

    // 蓋成系統租戶的資料（屬於消費者 A）
    private UUID systemTicketId;
    private UUID systemReturnId;
    private UUID systemStatementId;
    // 蓋成店鋪租戶的資料
    private UUID storeTicketId;
    private UUID storeReturnId;
    private UUID storeStatementId;
    private UUID storeTenantId;
    private UUID storeTransferId;
    private UUID systemTransferId;

    @BeforeAll
    void seedFixtures() throws Exception {
        RestAssuredMockMvc.mockMvc(mockMvc);
        long stamp = System.currentTimeMillis();

        String emailA = "sti-a-" + stamp + "@example.com";
        String emailB = "sti-b-" + stamp + "@example.com";
        tokenA = registerAndLoginTenantless(emailA);
        tokenB = registerAndLoginTenantless(emailB);
        UUID customerA = userRepository.findByEmail(emailA).orElseThrow().getId();

        // ── 系統租戶的資料（一般消費者 A 的）──
        systemTicketId = supportTicketRepository.save(SupportTicket.builder()
                .tenantId(SYSTEM_TENANT_ID)
                .ticketNumber("TKT-S234-SYS-" + stamp)
                .category(SupportTicket.TicketCategory.PRODUCT)
                .subject("S234 消費者 A 的客服工單")
                .description("收到的杯子有裂痕")
                .customerId(customerA)
                .build()).getId();
        systemReturnId = returnRequestRepository.save(ReturnRequest.builder()
                .tenantId(SYSTEM_TENANT_ID)
                .returnNumber("RTN-S234-SYS-" + stamp)
                .orderId(UUID.randomUUID())
                .customerId(customerA)
                .reason("尺寸不合")
                .build()).getId();
        Tenant systemTenant = tenantRepository.findById(SYSTEM_TENANT_ID).orElseThrow();
        SettlementStatement systemStatement = settlementStatementRepository.save(SettlementStatement.builder()
                .tenant(systemTenant)
                .statementNumber("STM-S234-SYS-" + stamp)
                .periodStart(PERIOD_START)
                .periodEnd(PERIOD_END)
                .totalOrders(3)
                .totalGmv(new BigDecimal("12345.00"))
                .build());
        systemStatementId = systemStatement.getId();
        systemTransferId = transferRepository.save(Transfer.builder()
                .settlementStatementId(systemStatementId)
                .tenantId(SYSTEM_TENANT_ID)
                .transferAmount(new BigDecimal("100.00"))
                .build()).getId();

        // ── 一間真實店鋪與它的店主，以及蓋成該店鋪租戶的資料（對照組）──
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S234 隔離測試店 " + stamp)
                .slug("s234-isolation-" + stamp)
                .contactEmail("s234-store-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        storeTenantId = store.getId();
        User owner = userRepository.save(User.builder()
                .email("s234-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S234 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(storeTenantId)
                .build());
        storeOwnerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", storeTenantId.toString());

        storeTicketId = supportTicketRepository.save(SupportTicket.builder()
                .tenantId(storeTenantId)
                .ticketNumber("TKT-S234-STORE-" + stamp)
                .category(SupportTicket.TicketCategory.PRODUCT)
                .subject("S234 店鋪的客服工單")
                .description("店家自己的工單")
                .customerId(customerA)
                .build()).getId();
        storeReturnId = returnRequestRepository.save(ReturnRequest.builder()
                .tenantId(storeTenantId)
                .returnNumber("RTN-S234-STORE-" + stamp)
                .orderId(UUID.randomUUID())
                .customerId(customerA)
                .reason("店家自己的退貨")
                .build()).getId();
        SettlementStatement storeStatement = settlementStatementRepository.save(SettlementStatement.builder()
                .tenant(store)
                .statementNumber("STM-S234-STORE-" + stamp)
                .periodStart(PERIOD_START)
                .periodEnd(PERIOD_END)
                .totalOrders(1)
                .totalGmv(new BigDecimal("999.00"))
                .build());
        storeStatementId = storeStatement.getId();
        storeTransferId = transferRepository.save(Transfer.builder()
                .settlementStatementId(storeStatementId)
                .tenantId(storeTenantId)
                .transferAmount(new BigDecimal("50.00"))
                .build()).getId();
    }

    @AfterAll
    void cleanUp() {
        supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(systemTicketId)
                .forEach(supportMessageRepository::delete);
        supportTicketRepository.deleteById(systemTicketId);
        supportTicketRepository.deleteById(storeTicketId);
        returnRequestRepository.deleteById(systemReturnId);
        returnRequestRepository.deleteById(storeReturnId);
        transferRepository.deleteById(systemTransferId);
        transferRepository.deleteById(storeTransferId);
        settlementStatementRepository.deleteById(systemStatementId);
        settlementStatementRepository.deleteById(storeStatementId);
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

    // ───────────────────────── 客服工單 ─────────────────────────

    @Test
    @DisplayName("客服工單：沒有店鋪的買家呼叫店家層列表 → 空，看不到別的消費者的工單；店主照常看到自己店鋪的")
    void tenantlessBuyerCannotListStoreLevelTickets() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/dashboard/support/tickets?size=100")
                .then().statusCode(200)
                .body("data.tickets", hasSize(0));

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/dashboard/support/tickets?size=100")
                .then().statusCode(200)
                .body("data.tickets.id", hasItem(storeTicketId.toString()))
                .body("data.tickets.id", not(hasItem(systemTicketId.toString())));
    }

    @Test
    @DisplayName("客服工單：沒有店鋪的買家不得讀別人工單的全文與訊息")
    void tenantlessBuyerCannotReadStoreLevelTicketDetail() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/dashboard/support/tickets/" + systemTicketId)
                .then().statusCode(404);

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/dashboard/support/tickets/" + storeTicketId)
                .then().statusCode(200)
                .body("data.id", is(storeTicketId.toString()));
    }

    @Test
    @DisplayName("客服工單：沒有店鋪的買家不得以「店員」身分在別人的工單發言，也不得讀店家層訊息串")
    void tenantlessBuyerCannotImpersonateStaff() {
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"message\":\"" + IMPERSONATION + "\"}")
                .when().post("/v2/dashboard/support/tickets/" + systemTicketId + "/messages")
                .then().statusCode(404);

        assertThat(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(systemTicketId))
                .as("被拒絕的發言不得留下任何訊息列")
                .isEmpty();

        // 受害的消費者自己讀工單：沒有任何店員訊息
        given().header("Authorization", "Bearer " + tokenA)
                .when().get("/v2/support/tickets/" + systemTicketId)
                .then().statusCode(200)
                .body("data.messages", hasSize(0));
    }

    @Test
    @DisplayName("客服工單：消費者仍可讀自己的工單（守門不得過度）")
    void ownerStillReadsOwnTicket() {
        given().header("Authorization", "Bearer " + tokenA)
                .when().get("/v2/support/tickets/" + systemTicketId)
                .then().statusCode(200)
                .body("data.id", is(systemTicketId.toString()));
    }

    // ───────────────────────── 退貨申請 ─────────────────────────

    @Test
    @DisplayName("退貨：沒有店鋪的買家呼叫店家層列表 → 空頁；店主照常看到自己店鋪的")
    void tenantlessBuyerCannotListStoreLevelReturns() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/dashboard/returns?size=100")
                .then().statusCode(200)
                .body("data.content", hasSize(0));

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/dashboard/returns?size=100")
                .then().statusCode(200)
                .body("data.content.id", hasItem(storeReturnId.toString()))
                .body("data.content.id", not(hasItem(systemReturnId.toString())));
    }

    @Test
    @DisplayName("退貨：另一位沒有店鋪的買家不得讀取別人的退貨申請；本人與店主可以")
    void tenantlessBuyerCannotReadOthersReturn() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/returns/" + systemReturnId)
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + tokenA)
                .when().get("/v2/returns/" + systemReturnId)
                .then().statusCode(200)
                .body("data.id", is(systemReturnId.toString()));

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/returns/" + storeReturnId)
                .then().statusCode(200)
                .body("data.id", is(storeReturnId.toString()));
    }

    // ───────────────────────── 結算單 ─────────────────────────

    @Test
    @DisplayName("結算單：沒有店鋪的買家呼叫店家層列表 → 空；店主照常看到自己店鋪的")
    void tenantlessBuyerCannotListSettlementStatements() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/settlements?size=100")
                .then().statusCode(200)
                .body("data.statements", hasSize(0));

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/settlements?size=100")
                .then().statusCode(200)
                .body("data.statements.id", hasItem(storeStatementId.toString()))
                .body("data.statements.id", not(hasItem(systemStatementId.toString())));
    }

    @Test
    @DisplayName("結算單：沒有店鋪的買家不得讀取詳情，也不得把它推進到待審核")
    void tenantlessBuyerCannotReadOrSubmitSettlementStatement() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/settlements/" + systemStatementId)
                .then().statusCode(404);

        given().header("Authorization", "Bearer " + tokenB)
                .when().put("/v2/settlements/" + systemStatementId + "/submit")
                .then().statusCode(404);

        assertThat(settlementStatementRepository.findById(systemStatementId).orElseThrow().getStatus())
                .as("被拒絕的提交不得改動結算單狀態")
                .isEqualTo(SettlementStatus.PENDING);

        // 對照：店主可以讀、也可以提交自己店鋪的結算單
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/settlements/" + storeStatementId)
                .then().statusCode(200)
                .body("data.id", is(storeStatementId.toString()));
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().put("/v2/settlements/" + storeStatementId + "/submit")
                .then().statusCode(200);
        assertThat(settlementStatementRepository.findById(storeStatementId).orElseThrow().getStatus())
                .isEqualTo(SettlementStatus.PENDING_REVIEW);
    }

    // ───────────────────────── 撥款記錄 ─────────────────────────

    @Test
    @DisplayName("撥款記錄：沒有店鋪的買家呼叫列表 → 空；店主照常看到自己店鋪的")
    void tenantlessBuyerCannotListTransfers() {
        given().header("Authorization", "Bearer " + tokenB)
                .when().get("/v2/transfers?size=100")
                .then().statusCode(200)
                .body("data.transfers", hasSize(0));

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/transfers?size=100")
                .then().statusCode(200)
                .body("data.transfers.id", hasItem(storeTransferId.toString()))
                .body("data.transfers.id", not(hasItem(systemTransferId.toString())));
    }

    /** 註冊並登入一個不屬於任何店鋪的 BUYER，回傳 access token。 */
    private String registerAndLoginTenantless(String email) throws Exception {
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(PASSWORD).userType("BUYER").build())
                .when().post("/v2/auth/register")
                .then().statusCode(201);
        String loginResponse = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(PASSWORD).build())
                .when().post("/v2/auth/login")
                .then().statusCode(200).extract().asString();
        return objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
    }
}
