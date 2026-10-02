package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * V89 遷移的資料邏輯（Sprint 237，DEF-319 訂單側）：訂單的租戶回填成其項目所屬房源的店鋪，衍生資料跟著走；
 * 多家店鋪、已被結算單認領、沒有項目的訂單一律不動。
 *
 * <p>「訂單的租戶≠店鋪」在修復前的真實樣貌是：沒有店鋪的消費者的訂單被蓋成系統租戶佔位值。資料上都是「租戶≠項目所屬店鋪」，
 * 本測試以一般租戶代表買家的租戶（測試庫不一定有系統租戶那一列）。
 *
 * <p>整合測試的資料庫是 Hibernate 建的，不會執行 Flyway；這裡直接讀 {@code db/migration/V89__*.sql} 的實際內容，
 * 對種好的資料執行。整個測試在交易內、結束後回滾，不會動到其他測試共用資料庫裡的任何列。
 * （Flyway 本身能不能跑、語法與欄位名稱對不對，由 {@code make validate-schema-doc} 對乾淨資料庫套用所有遷移驗證。）
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 237: V89 遷移——訂單的租戶回填成商品所屬的店鋪")
class BackfillOrderTenantMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V89__Backfill_Order_Tenant_To_Store.sql";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private OrderRepository orderRepository;

    private Tenant newTenant(final String label) {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder()
                .name("V89 " + label + " " + stamp).slug("v89-" + label + "-" + stamp)
                .contactEmail("v89-" + label + "-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newUser(final User.UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .email("v89-" + System.nanoTime() + "@example.com").passwordHash("dummy").role(role).status("ACTIVE").build());
    }

    /** 房源要同時設關聯（寫入資料庫的是 tenant／owner 關聯，tenantId／ownerId 只是唯讀影子欄位）。 */
    private Listing newProduct(final Tenant store, final User owner) {
        return listingRepository.saveAndFlush(Listing.builder()
                .tenant(store).owner(owner)
                .listingType(Listing.ListingType.PRODUCT).title("V89 商品 " + System.nanoTime())
                .basePrice(new BigDecimal("100.00")).status(Listing.ListingStatus.ACTIVE).build());
    }

    private UUID newSku(final Listing product) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", id, product.getId(), "V89-" + id);
        return id;
    }

    private Order newOrder(final Tenant orderTenant, final User buyer, final Listing... products) {
        Order order = Order.builder()
                .tenant(orderTenant).user(buyer).orderType(Listing.ListingType.PRODUCT)
                .status(Order.OrderStatus.COMPLETED).totalAmount(new BigDecimal("100.00")).currency("TWD")
                .items(new ArrayList<>()).build();
        for (Listing product : products) {
            order.addItem(OrderItem.builder().listing(product).quantity(1)
                    .unitPrice(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00")).build());
        }
        return orderRepository.saveAndFlush(order);
    }

    private UUID tenantOfOrder(final Order order) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM orders WHERE id = ?", UUID.class, order.getId());
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    @Test
    @DisplayName("單一店鋪、尚未結算的訂單搬到店鋪；多家店鋪、已被結算單認領、沒有項目、本來就對的訂單一律不動")
    void movesOnlyUnambiguousUnsettledOrders() throws Exception {
        Tenant store = newTenant("store");
        Tenant otherStore = newTenant("other");
        Tenant buyerTenant = newTenant("buyer");
        User owner = newUser(User.UserRole.STORE_OWNER);
        User otherOwner = newUser(User.UserRole.STORE_OWNER);
        User consumer = newUser(User.UserRole.BUYER);
        Listing p1 = newProduct(store, owner);
        Listing p2 = newProduct(store, owner);
        Listing q1 = newProduct(otherStore, otherOwner);

        Order single = newOrder(buyerTenant, consumer, p1, p2);
        Order multi = newOrder(buyerTenant, consumer, p1, q1);
        Order settled = newOrder(buyerTenant, consumer, p1);
        Order alreadyRight = newOrder(store, consumer, p1);
        Order empty = newOrder(buyerTenant, consumer);
        UUID statementId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO settlement_statements (id, tenant_id, statement_number, period_start, period_end, "
                + "created_at, updated_at) VALUES (?, ?, ?, CURRENT_DATE - 14, CURRENT_DATE - 8, NOW(), NOW())",
                statementId, buyerTenant.getId(), "V89-" + statementId.toString().substring(0, 8));
        jdbcTemplate.update("UPDATE orders SET settled_statement_id = ? WHERE id = ?", statementId, settled.getId());

        runMigration();

        assertThat(tenantOfOrder(single)).as("單一店鋪、尚未結算").isEqualTo(store.getId());
        assertThat(tenantOfOrder(multi)).as("項目來自兩家店鋪，無法判定歸屬").isEqualTo(buyerTenant.getId());
        assertThat(tenantOfOrder(settled)).as("已被結算單認領，需財務人工決定").isEqualTo(buyerTenant.getId());
        assertThat(tenantOfOrder(alreadyRight)).as("本來就對").isEqualTo(store.getId());
        assertThat(tenantOfOrder(empty)).as("沒有項目（舊 ROOM 訂單路徑）").isEqualTo(buyerTenant.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, single.getId()))
                .as("只改租戶，不動狀態").isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM orders WHERE id = ?", UUID.class, single.getId()))
                .as("只改租戶，下單人不變（消費者仍看得到自己的訂單）").isEqualTo(consumer.getId());
    }

    @Test
    @DisplayName("衍生資料跟著訂單走：客服工單、退貨單、以訂單為對象的對話改成新租戶；庫存異動改成 SKU 所屬房源的店鋪；沒搬的訂單不受影響")
    void derivedRowsFollowTheirSource() throws Exception {
        Tenant store = newTenant("store");
        Tenant buyerTenant = newTenant("buyer");
        User owner = newUser(User.UserRole.STORE_OWNER);
        User consumer = newUser(User.UserRole.BUYER);
        Listing product = newProduct(store, owner);
        UUID sku = newSku(product);
        Order moved = newOrder(buyerTenant, consumer, product);

        UUID ticket = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO support_tickets (id, tenant_id, ticket_number, category, subject, description, status, "
                + "priority, customer_id, order_id, created_at, updated_at) VALUES (?, ?, ?, 'PRODUCT', 's', 'd', 'OPEN', 'NORMAL', ?, ?, NOW(), NOW())",
                ticket, buyerTenant.getId(), "V89T" + ticket.toString().substring(0, 8), consumer.getId(), moved.getId());
        UUID platformTicket = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO support_tickets (id, tenant_id, ticket_number, category, subject, description, status, "
                + "priority, customer_id, created_at, updated_at) VALUES (?, NULL, ?, 'OTHER', 's', 'd', 'OPEN', 'NORMAL', ?, NOW(), NOW())",
                platformTicket, "V89P" + platformTicket.toString().substring(0, 8), consumer.getId());
        UUID returnRequest = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO return_requests (id, tenant_id, return_number, order_id, customer_id, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'REQUESTED', NOW(), NOW())",
                returnRequest, buyerTenant.getId(), "V89R" + returnRequest.toString().substring(0, 8), moved.getId(), consumer.getId());
        UUID conversation = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO conversations (id, order_id, conversation_type, initiator_id, recipient_id, tenant_id, "
                + "initiator_unread_count, recipient_unread_count, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'DIRECT', ?, ?, ?, 0, 0, TRUE, NOW(), NOW())",
                conversation, moved.getId(), consumer.getId(), owner.getId(), buyerTenant.getId());
        UUID movement = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO stock_movements (id, tenant_id, sku_id, movement_type, quantity, balance_after, "
                + "reference_id, reference_type, created_at) VALUES (?, ?, ?, 'RESERVE', 1, 99, ?, 'ORDER', NOW())",
                movement, buyerTenant.getId(), sku, moved.getId());
        UUID correctMovement = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO stock_movements (id, tenant_id, sku_id, movement_type, quantity, balance_after, created_at) "
                + "VALUES (?, ?, ?, 'ADJUST_PLUS', 5, 104, NOW())", correctMovement, store.getId(), sku);

        runMigration();

        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM support_tickets WHERE id = ?", UUID.class, ticket))
                .as("客服工單跟著訂單").isEqualTo(store.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM support_tickets WHERE id = ?", UUID.class, platformTicket))
                .as("沒有訂單的平台工單（租戶為 null）不動").isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM return_requests WHERE id = ?", UUID.class, returnRequest))
                .as("退貨單跟著訂單").isEqualTo(store.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM conversations WHERE id = ?", UUID.class, conversation))
                .as("以訂單為對象的對話跟著訂單").isEqualTo(store.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM stock_movements WHERE id = ?", UUID.class, movement))
                .as("訂單預扣的庫存異動屬於 SKU 所屬房源的店鋪").isEqualTo(store.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM stock_movements WHERE id = ?", UUID.class, correctMovement))
                .as("本來就對的庫存異動不動").isEqualTo(store.getId());
    }

    @Test
    @DisplayName("可重複執行：第二次不會再改變任何本測試種下的列")
    void isIdempotent() throws Exception {
        Tenant store = newTenant("store");
        Tenant buyerTenant = newTenant("buyer");
        User owner = newUser(User.UserRole.STORE_OWNER);
        User consumer = newUser(User.UserRole.BUYER);
        Listing product = newProduct(store, owner);
        Order moved = newOrder(buyerTenant, consumer, product);

        runMigration();
        assertThat(tenantOfOrder(moved)).isEqualTo(store.getId());
        List<Object[]> before = snapshot();
        runMigration();
        List<Object[]> after = snapshot();

        assertThat(tenantOfOrder(moved)).isEqualTo(store.getId());
        assertThat(after).as("第二次執行不改變任何訂單的租戶").hasSameSizeAs(before);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i)).containsExactly(before.get(i));
        }
    }

    /** 所有訂單的（id, tenant_id）快照，依 id 排序。 */
    private List<Object[]> snapshot() {
        return jdbcTemplate.query("SELECT id, tenant_id FROM orders ORDER BY id",
                (rs, i) -> new Object[] {rs.getObject(1), rs.getObject(2)});
    }
}
