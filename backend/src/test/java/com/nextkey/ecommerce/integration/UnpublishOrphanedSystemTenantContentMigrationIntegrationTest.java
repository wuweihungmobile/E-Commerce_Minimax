package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * V90 遷移的資料邏輯（Sprint 242，DEF-333）：系統租戶底下、建立者不是平台管理員（ADMIN、SUPER_ADMIN）的對外公開內容，
 * 下架（不刪除）並逐筆寫稽核紀錄；其他一律不動。
 *
 * <p>整合測試的資料庫是 Hibernate 建的，不會執行 Flyway；這裡直接讀 {@code db/migration/V90__*.sql} 的實際內容，
 * 對種好的資料執行（作法同 V87～V89 的遷移測試）。整個測試在交易內、結束後回滾，不會動到其他測試共用資料庫裡的任何列。
 * Flyway 本身能不能跑、語法對不對，由 {@code make validate-e2e}／{@code make validate-schema-doc} 對乾淨資料庫執行遷移驗證。
 *
 * <p>共用資料庫裡可能有其他測試留下的系統租戶資料（會被遷移一併處理，但隨交易回滾），所以斷言只看本測試種下的列。
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 242: V90 遷移——系統租戶底下建立者不是平台管理員的對外公開內容下架")
class UnpublishOrphanedSystemTenantContentMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V90__Unpublish_Orphaned_System_Tenant_Content.sql";
    private static final String ACTION = "DEF333_UNPUBLISHED_ORPHAN";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;

    private Tenant systemTenant() {
        return tenantRepository.findById(SYSTEM_TENANT_ID).orElseThrow();
    }

    private Tenant newStore() {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder().name("V90 測試店 " + stamp).slug("v90-" + stamp)
                .contactEmail("v90-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newUser(final User.UserRole role) {
        return userRepository.saveAndFlush(User.builder().email("v90-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy").role(role).status("ACTIVE").build());
    }

    private UUID newListing(final Tenant tenant, final User owner, final Listing.ListingType type,
                            final Listing.ListingStatus status) {
        return listingRepository.saveAndFlush(Listing.builder().tenant(tenant).owner(owner).listingType(type)
                .title("V90 " + System.nanoTime()).basePrice(new BigDecimal("100.00")).currency("TWD")
                .status(status).build()).getId();
    }

    private UUID newPost(final UUID tenantId, final User author, final String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO posts (id, tenant_id, author_id, title, slug, status, view_count, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'V90 貼文', ?, ?, 0, NOW(), NOW())",
                id, tenantId, author.getId(), "v90-post-" + id, status);
        return id;
    }

    private UUID newPage(final UUID tenantId, final User author, final String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cms_pages (id, tenant_id, author_id, title, slug, page_type, status, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'V90 頁面', ?, 'CUSTOM', ?, NOW(), NOW())",
                id, tenantId, author == null ? null : author.getId(), "v90-page-" + id, status);
        return id;
    }

    private String status(final String table, final UUID id) {
        return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    private int auditRows(final UUID... ids) {
        int total = 0;
        for (UUID id : ids) {
            total += jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ? AND entity_id = ?",
                    Integer.class, ACTION, id);
        }
        return total;
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8);
        jdbcTemplate.update(sql);
    }

    @Test
    @DisplayName("商品與房源：建立者不是平台管理員（SELLER、HOST、開店後的 STORE_OWNER、BUYER）的上架項目下架；平台管理員、草稿、已下架、已刪除、店鋪租戶的不動")
    void unpublishesOnlyOrphanedActiveListings() throws Exception {
        Tenant system = systemTenant();
        Tenant store = newStore();
        User seller = newUser(User.UserRole.SELLER);
        User host = newUser(User.UserRole.HOST);
        User laterOwner = newUser(User.UserRole.STORE_OWNER);
        User buyer = newUser(User.UserRole.BUYER);
        User admin = newUser(User.UserRole.ADMIN);
        User superAdmin = newUser(User.UserRole.SUPER_ADMIN);

        UUID bySeller = newListing(system, seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);
        UUID byHost = newListing(system, host, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE);
        UUID byLaterOwner = newListing(system, laterOwner, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);
        UUID byBuyer = newListing(system, buyer, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);
        UUID byAdmin = newListing(system, admin, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);
        UUID bySuperAdmin = newListing(system, superAdmin, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE);
        UUID sellerDraft = newListing(system, seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.DRAFT);
        UUID sellerInactive = newListing(system, seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.INACTIVE);
        UUID sellerDeleted = newListing(system, seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.DELETED);
        UUID inStore = newListing(store, seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);

        runMigration();

        for (UUID orphan : List.of(bySeller, byHost, byLaterOwner, byBuyer)) {
            assertThat(status("listings", orphan)).as("孤兒 %s", orphan).isEqualTo("INACTIVE");
        }
        assertThat(status("listings", byAdmin)).as("平台管理員建立的").isEqualTo("ACTIVE");
        assertThat(status("listings", bySuperAdmin)).as("超級管理員建立的").isEqualTo("ACTIVE");
        assertThat(status("listings", sellerDraft)).as("草稿不動").isEqualTo("DRAFT");
        assertThat(status("listings", sellerInactive)).as("本來就下架的不動").isEqualTo("INACTIVE");
        assertThat(status("listings", sellerDeleted)).as("已刪除的不動（不復活）").isEqualTo("DELETED");
        assertThat(status("listings", inStore)).as("店鋪租戶底下的不動（SELLER 角色但有真實店鋪）").isEqualTo("ACTIVE");

        assertThat(auditRows(bySeller, byHost, byLaterOwner, byBuyer)).as("每筆下架各寫一列稽核").isEqualTo(4);
        assertThat(auditRows(byAdmin, bySuperAdmin, sellerDraft, sellerInactive, sellerDeleted, inStore))
                .as("沒被動到的不寫稽核").isZero();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT entity_type, old_value, new_value, tenant_id FROM audit_log WHERE action = ? AND entity_id = ?",
                ACTION, bySeller);
        assertThat(audit.get("entity_type")).isEqualTo("LISTING");
        assertThat(audit.get("old_value")).as("還原依據").isEqualTo("ACTIVE");
        assertThat(audit.get("new_value")).isEqualTo("INACTIVE");
        assertThat(audit.get("tenant_id")).isEqualTo(SYSTEM_TENANT_ID);
    }

    @Test
    @DisplayName("貼文與 CMS 頁面：建立者不是平台管理員的已發布項目退回草稿；平台管理員、草稿、歸檔、店鋪租戶、沒有建立者的 CMS 頁面不動")
    void unpublishesOnlyOrphanedPublishedPostsAndPages() throws Exception {
        Tenant store = newStore();
        User seller = newUser(User.UserRole.SELLER);
        User host = newUser(User.UserRole.HOST);
        User admin = newUser(User.UserRole.ADMIN);

        UUID postBySeller = newPost(SYSTEM_TENANT_ID, seller, "PUBLISHED");
        UUID postByAdmin = newPost(SYSTEM_TENANT_ID, admin, "PUBLISHED");
        UUID postDraft = newPost(SYSTEM_TENANT_ID, seller, "DRAFT");
        UUID postArchived = newPost(SYSTEM_TENANT_ID, seller, "ARCHIVED");
        UUID postInStore = newPost(store.getId(), seller, "PUBLISHED");
        UUID pageByHost = newPage(SYSTEM_TENANT_ID, host, "PUBLISHED");
        UUID pageByAdmin = newPage(SYSTEM_TENANT_ID, admin, "PUBLISHED");
        UUID pagePlatform = newPage(SYSTEM_TENANT_ID, null, "PUBLISHED");
        UUID pageDraft = newPage(SYSTEM_TENANT_ID, host, "DRAFT");
        UUID pageInStore = newPage(store.getId(), host, "PUBLISHED");

        runMigration();

        assertThat(status("posts", postBySeller)).isEqualTo("DRAFT");
        assertThat(status("posts", postByAdmin)).isEqualTo("PUBLISHED");
        assertThat(status("posts", postDraft)).isEqualTo("DRAFT");
        assertThat(status("posts", postArchived)).as("歸檔的不動（不從歸檔變草稿）").isEqualTo("ARCHIVED");
        assertThat(status("posts", postInStore)).isEqualTo("PUBLISHED");
        assertThat(status("cms_pages", pageByHost)).isEqualTo("DRAFT");
        assertThat(status("cms_pages", pageByAdmin)).isEqualTo("PUBLISHED");
        assertThat(status("cms_pages", pagePlatform)).as("沒有建立者的平台頁面不動").isEqualTo("PUBLISHED");
        assertThat(status("cms_pages", pageDraft)).isEqualTo("DRAFT");
        assertThat(status("cms_pages", pageInStore)).isEqualTo("PUBLISHED");

        assertThat(auditRows(postBySeller, pageByHost)).isEqualTo(2);
        assertThat(auditRows(postByAdmin, postDraft, postArchived, postInStore, pageByAdmin, pagePlatform, pageDraft, pageInStore))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT entity_type FROM audit_log WHERE action = ? AND entity_id = ?",
                String.class, ACTION, postBySeller)).isEqualTo("POST");
        assertThat(jdbcTemplate.queryForObject("SELECT entity_type FROM audit_log WHERE action = ? AND entity_id = ?",
                String.class, ACTION, pageByHost)).isEqualTo("CMS_PAGE");
    }

    @Test
    @DisplayName("可重複執行：第二次不會再改狀態、也不會再寫稽核列")
    void isIdempotent() throws Exception {
        User seller = newUser(User.UserRole.SELLER);
        UUID listing = newListing(systemTenant(), seller, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE);
        UUID post = newPost(SYSTEM_TENANT_ID, seller, "PUBLISHED");

        runMigration();
        runMigration();

        assertThat(status("listings", listing)).isEqualTo("INACTIVE");
        assertThat(status("posts", post)).isEqualTo("DRAFT");
        assertThat(auditRows(listing, post)).as("每個項目只有一列稽核").isEqualTo(2);
    }
}
