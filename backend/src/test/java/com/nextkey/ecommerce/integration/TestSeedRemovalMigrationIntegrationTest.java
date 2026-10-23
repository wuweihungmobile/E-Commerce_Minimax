package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StreamUtils;

/**
 * V94 移除測試種子資料並鎖住已知密碼的帳號（Sprint 305，DEF-418，真實 PostgreSQL，Flyway 實際執行 V1～V94）。
 *
 * <p>為什麼不用一般的整合測試資料庫：它是 Hibernate 產生的 schema，沒有任何遷移、沒有 {@code V5}／{@code V7} 的種子資料
 * （見 {@code flyway-db-vs-hibernate-test-db-differences}）。要驗證「遷移種下的資料被遷移清掉」，必須在 Flyway 建出的資料庫上做。
 * 所以這支測試在測試用 PostgreSQL 裡另建四個獨立資料庫（不碰共用的測試資料庫），各自用 Flyway 套用 V1～V93，
 * 先確認種子資料確實存在（這就是 V94 要處理的問題），再 migrate 到 V94 檢查結果：
 * <ul>
 *   <li><b>乾淨</b>：只剩 {@code admin@nextkey.local}（密碼已鎖）與系統租戶；</li>
 *   <li><b>有相依資料</b>：{@code buyer-test@e2e-test.com} 被 {@code carts}（{@code NO ACTION} 外鍵）參照而刪不掉——
 *       遷移不得失敗、該帳號保留但密碼已鎖、其他測試帳號照刪；</li>
 *   <li><b>房源被其他資料參照</b>：含該房源的測試店鋪整個原封不動（貼文、分類、房源一筆不少），其餘照刪；</li>
 *   <li><b>真實資料</b>：同一個資料庫裡另有真實店鋪與真實使用者時，V94 不得動到它們（不鎖密碼、不刪除）——
 *       這是遷移在已有真實資料的資料庫上最危險的失敗模式，沒有這個案例時「把所有人的密碼都鎖掉」的突變不會被發現；</li>
 *   <li><b>冪等</b>：重複執行 V94 不出錯、狀態不變。</li>
 * </ul>
 * 需要 {@code make test-db-up}（連線設定與 integration-test profile 相同：localhost:5432）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 305（DEF-418）: V94 移除測試種子資料並鎖住已知密碼的帳號（Flyway 實跑 V1～V94）")
class TestSeedRemovalMigrationIntegrationTest {

    private static final String BASE_URL =
            System.getProperty("it.db.base-url", "jdbc:postgresql://localhost:5432/");
    private static final String MAINTENANCE_DB = System.getProperty("it.db.name", "nextkeytest");
    private static final String DB_USER = System.getProperty("it.db.user", "koala");
    private static final String DB_PASS = System.getProperty("it.db.pass", "koala5");

    private static final String CLEAN_DB = "s305_seed_clean";
    private static final String DIRTY_DB = "s305_seed_dirty";
    private static final String LISTING_BLOCKED_DB = "s305_seed_listing_blocked";
    private static final String REAL_DATA_DB = "s305_seed_real_data";
    private static final String NEXTKEY_TEST_TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REAL_TENANT_ID = "99999999-9999-9999-9999-999999999999";
    private static final String REAL_OWNER_EMAIL = "real-owner@example.com";
    private static final String REAL_BUYER_EMAIL = "real-buyer@example.com";
    private static final String REAL_PASSWORD = "RealPass!1";

    private static final String ADMIN_EMAIL = "admin@nextkey.local";
    private static final String SYSTEM_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String BLOCKED_EMAIL = "buyer-test@e2e-test.com";
    private static final List<String> SEEDED_EMAILS = List.of(
            "active-owner@e2e-test.com", ADMIN_EMAIL, "buyer-test@e2e-test.com", "m15-api-owner@test.com",
            "m15-owner@test.com", "pending-owner@e2e-test.com", "test-store-owner@nextkeytest.com");
    private static final List<String> SEEDED_TENANT_SLUGS = List.of(
            "active-test", "m15-api-test-store", "m15-test-store-2", "nextkey-test", "pending-test", "platform");
    /** 曾經（或被宣稱）是這些帳號密碼的候選值；V94 之後任何一個都不得再能登入 admin。 */
    private static final List<String> KNOWN_PASSWORD_CANDIDATES =
            List.of("Test123!", "admin123", "test123", "password", "SecurePass123!");

    // V94 之前的狀態（V1～V93）
    private List<String> cleanUsersBefore;
    private List<String> cleanTenantSlugsBefore;
    private String cleanAdminHashBefore;
    // V94 之後的狀態
    private List<String> cleanUsersAfter;
    private List<String> cleanTenantIdsAfter;
    private String cleanAdminHashAfter;
    private List<String> dirtyUsersAfter;
    private String dirtyHashAfterByEmail;
    private int dirtyCartsAfter;
    private List<String> dirtyTenantIdsAfter;
    private List<String> blockedUsersAfter;
    private List<String> blockedTenantSlugsAfter;
    private List<String> blockedHashesAfter;
    private int blockedCartItemsAfter;
    /** nextkey-test 店鋪底下的 [貼文, 分類, 房源] 筆數。 */
    private List<Integer> blockedTenantDataBefore;
    private List<Integer> blockedTenantDataAfter;
    // 真實資料（V94 之前就存在、不屬於測試種子）
    private String realPasswordHash;
    private List<String> realUsersAfter;
    private List<String> realTenantIdsAfter;
    private List<String> realUserHashesAfter;
    private List<Integer> realTenantDataAfter;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeAll
    void migrateAllDatabases() throws Exception {
        for (String db : List.of(CLEAN_DB, DIRTY_DB, LISTING_BLOCKED_DB, REAL_DATA_DB)) {
            recreateDatabase(db);
            flyway(db, "93").migrate();
        }

        // 乾淨資料庫：先記下 V94 之前的狀態
        cleanUsersBefore = strings(CLEAN_DB, "SELECT email FROM users ORDER BY email");
        cleanTenantSlugsBefore = strings(CLEAN_DB, "SELECT slug FROM tenants ORDER BY slug");
        cleanAdminHashBefore = strings(CLEAN_DB,
                "SELECT password_hash FROM users WHERE email = '" + ADMIN_EMAIL + "'").get(0);

        // 有相依資料的資料庫：buyer-test 被 carts（NO ACTION 外鍵）參照，刪不掉
        execute(DIRTY_DB, "INSERT INTO carts (id, user_id) SELECT gen_random_uuid(), id FROM users "
                + "WHERE email = '" + BLOCKED_EMAIL + "'");

        // 房源被其他資料參照：nextkey-test 店鋪的一間房源被購物車項目參照（cart_items.listing_id 是 NO ACTION 外鍵）
        execute(LISTING_BLOCKED_DB, "WITH c AS (INSERT INTO carts (id, user_id) SELECT gen_random_uuid(), id FROM users "
                + "WHERE email = '" + ADMIN_EMAIL + "' RETURNING id) "
                + "INSERT INTO cart_items (cart_id, listing_id) SELECT c.id, "
                + "(SELECT id FROM listings WHERE tenant_id = '" + NEXTKEY_TEST_TENANT_ID + "' ORDER BY id LIMIT 1) FROM c");
        blockedTenantDataBefore = tenantDataCounts(LISTING_BLOCKED_DB, NEXTKEY_TEST_TENANT_ID);

        // 真實資料：測試種子之外，資料庫裡另有真實店鋪、真實店主（綁該店鋪）與真實買家（系統租戶），各有真實密碼
        realPasswordHash = encoder.encode(REAL_PASSWORD);
        execute(REAL_DATA_DB, "INSERT INTO tenants (id, name, slug) VALUES ('" + REAL_TENANT_ID + "', '真實店鋪', 'real-shop')");
        execute(REAL_DATA_DB, "INSERT INTO users (email, password_hash, tenant_id) VALUES "
                + "('" + REAL_OWNER_EMAIL + "', '" + realPasswordHash + "', '" + REAL_TENANT_ID + "'), "
                + "('" + REAL_BUYER_EMAIL + "', '" + realPasswordHash + "', '" + SYSTEM_TENANT_ID + "')");
        execute(REAL_DATA_DB, "INSERT INTO post_categories (tenant_id, name, slug) VALUES ('" + REAL_TENANT_ID + "', '公告', 'news')");
        execute(REAL_DATA_DB, "INSERT INTO posts (tenant_id, author_id, title, slug) SELECT '" + REAL_TENANT_ID
                + "', id, '開幕', 'opening' FROM users WHERE email = '" + REAL_OWNER_EMAIL + "'");
        execute(REAL_DATA_DB, "INSERT INTO listings (tenant_id, listing_type, title, owner_id, base_price) SELECT '"
                + REAL_TENANT_ID + "', 'ROOM', '真實房源', id, 1000 FROM users WHERE email = '" + REAL_OWNER_EMAIL + "'");

        flyway(CLEAN_DB, "94").migrate();
        flyway(DIRTY_DB, "94").migrate();
        flyway(LISTING_BLOCKED_DB, "94").migrate();
        flyway(REAL_DATA_DB, "94").migrate();

        cleanUsersAfter = strings(CLEAN_DB, "SELECT email FROM users ORDER BY email");
        cleanTenantIdsAfter = strings(CLEAN_DB, "SELECT id::text FROM tenants ORDER BY id");
        // admin 那一列若被誤刪，這裡取不到值（null），交給下面的案例報出可讀的失敗，而不是在 @BeforeAll 索引越界
        cleanAdminHashAfter = strings(CLEAN_DB,
                "SELECT password_hash FROM users WHERE email = '" + ADMIN_EMAIL + "'").stream().findFirst().orElse(null);

        dirtyUsersAfter = strings(DIRTY_DB, "SELECT email FROM users ORDER BY email");
        dirtyHashAfterByEmail = String.join(",", strings(DIRTY_DB,
                "SELECT email || '=' || password_hash FROM users ORDER BY email"));
        dirtyCartsAfter = Integer.parseInt(strings(DIRTY_DB, "SELECT count(*)::text FROM carts").get(0));
        dirtyTenantIdsAfter = strings(DIRTY_DB, "SELECT id::text FROM tenants ORDER BY id");

        blockedUsersAfter = strings(LISTING_BLOCKED_DB, "SELECT email FROM users ORDER BY email");
        blockedTenantSlugsAfter = strings(LISTING_BLOCKED_DB, "SELECT slug FROM tenants ORDER BY slug");
        blockedHashesAfter = strings(LISTING_BLOCKED_DB, "SELECT password_hash FROM users ORDER BY email");
        blockedCartItemsAfter = Integer.parseInt(strings(LISTING_BLOCKED_DB, "SELECT count(*)::text FROM cart_items").get(0));
        blockedTenantDataAfter = tenantDataCounts(LISTING_BLOCKED_DB, NEXTKEY_TEST_TENANT_ID);

        realUsersAfter = strings(REAL_DATA_DB, "SELECT email FROM users ORDER BY email");
        realTenantIdsAfter = strings(REAL_DATA_DB, "SELECT id::text FROM tenants ORDER BY id");
        realUserHashesAfter = strings(REAL_DATA_DB, "SELECT password_hash FROM users WHERE email IN ('"
                + REAL_OWNER_EMAIL + "', '" + REAL_BUYER_EMAIL + "') ORDER BY email");
        realTenantDataAfter = tenantDataCounts(REAL_DATA_DB, REAL_TENANT_ID);
    }

    @AfterAll
    void dropDatabases() throws SQLException {
        dropDatabase(CLEAN_DB);
        dropDatabase(DIRTY_DB);
        dropDatabase(LISTING_BLOCKED_DB);
        dropDatabase(REAL_DATA_DB);
    }

    @Test
    @DisplayName("V94 之前（V1～V93 實跑）：資料庫真的含 7 個測試帳號、6 個租戶，admin 的雜湊符合已知密碼 Test123!——這就是 V94 要處理的問題")
    void beforeV94_flywayBuiltDatabaseHadSeededAccountsAndKnownAdminPassword() {
        assertThat(cleanUsersBefore).containsExactlyElementsOf(SEEDED_EMAILS);
        assertThat(cleanTenantSlugsBefore).containsExactlyElementsOf(SEEDED_TENANT_SLUGS);
        assertThat(encoder.matches("Test123!", cleanAdminHashBefore))
                .as("V7 把 admin 的密碼蓋成 Test123!：這是 V94 之前唯一可證實為已知密碼的帳號")
                .isTrue();
    }

    @Test
    @DisplayName("乾淨資料庫跑完 V94：只剩 admin 與系統租戶，沒有任何測試帳號、測試租戶與其房源、房型、定價規則、貼文、成員")
    void cleanDatabase_afterV94_onlyAdminAndSystemTenantRemain() throws SQLException {
        assertThat(cleanUsersAfter).containsExactly(ADMIN_EMAIL);
        assertThat(cleanTenantIdsAfter).containsExactly(SYSTEM_TENANT_ID);
        for (String table : List.of("tenant_members", "listings", "rooms", "pricing_rules", "posts", "post_embeds")) {
            assertThat(count(CLEAN_DB, "SELECT count(*) FROM " + table))
                    .as("測試租戶底下的 %s 不得殘留", table).isZero();
        }
        assertThat(count(CLEAN_DB, "SELECT count(*) FROM tenant_feature_toggles WHERE tenant_id <> '" + SYSTEM_TENANT_ID + "'"))
                .as("測試租戶的功能開關要清掉").isZero();
        assertThat(count(CLEAN_DB, "SELECT count(*) FROM post_categories WHERE tenant_id <> '" + SYSTEM_TENANT_ID + "'"))
                .as("測試租戶的貼文分類要清掉").isZero();
        assertThat(count(CLEAN_DB, "SELECT count(*) FROM tenant_feature_toggles WHERE tenant_id = '" + SYSTEM_TENANT_ID + "'"))
                .as("系統租戶自己的功能開關必須保留（V94 不得連它一起刪）").isEqualTo(10);
        assertThat(count(CLEAN_DB, "SELECT count(*) FROM post_categories WHERE tenant_id = '" + SYSTEM_TENANT_ID + "'"))
                .as("V14 替系統租戶建立的預設分類必須保留").isEqualTo(1);
    }

    @Test
    @DisplayName("admin 的密碼被鎖：雜湊與 V94 之前不同，是合法的 bcrypt，且已知／常見的候選密碼都登不進去")
    void adminPassword_isLockedAndNoKnownCandidateMatches() {
        assertThat(cleanAdminHashAfter).as("admin 那一列必須還在（只鎖密碼、不刪除；FIRST_ADMIN_SETUP 的設定步驟靠它）").isNotNull();
        assertThat(cleanAdminHashAfter).isNotEqualTo(cleanAdminHashBefore);
        assertThat(cleanAdminHashAfter).hasSize(60).startsWith("$2a$10$");
        for (String candidate : KNOWN_PASSWORD_CANDIDATES) {
            assertThat(encoder.matches(candidate, cleanAdminHashAfter))
                    .as("V94 之後 %s 不得再是 admin 的密碼", candidate).isFalse();
        }
    }

    @Test
    @DisplayName("有相依資料的資料庫：遷移不失敗；被外鍵擋下的帳號保留但密碼已鎖，其他測試帳號與測試租戶照刪，相依資料不受影響")
    void dirtyDatabase_blockedAccountKeptButLockedOthersDeleted() {
        assertThat(dirtyUsersAfter).containsExactly(ADMIN_EMAIL, BLOCKED_EMAIL);
        assertThat(dirtyTenantIdsAfter).containsExactly(SYSTEM_TENANT_ID);
        assertThat(dirtyCartsAfter).as("擋住刪除的相依資料不得被遷移動到").isEqualTo(1);
        for (String entry : dirtyHashAfterByEmail.split(",")) {
            String hash = entry.substring(entry.indexOf('=') + 1);
            assertThat(hash).as("保留下來的帳號密碼也必須是鎖住的：%s", entry.substring(0, entry.indexOf('=')))
                    .isEqualTo(cleanAdminHashAfter);
        }
    }

    @Test
    @DisplayName("房源被其他資料參照（購物車項目）：遷移不失敗；含被擋房源的店鋪與持有該店鋪房源的帳號保留（密碼已鎖），其餘測試租戶與帳號在同一次執行內全部刪除")
    void listingReferencedByOtherData_blockedTenantAndItsOwnersKeptOthersDeleted() {
        assertThat(blockedTenantSlugsAfter)
                .as("只有含被擋房源的 nextkey-test 店鋪保留（它的房源刪不掉，連帶租戶也刪不掉）；其餘 4 個測試租戶照刪")
                .containsExactly("nextkey-test", "platform");
        assertThat(blockedUsersAfter)
                .as("只有持有 nextkey-test 房源的 m15-owner、test-store-owner 保留（listings.owner_id 是 NO ACTION 外鍵）；"
                        + "其他店鋪的房源隨租戶 CASCADE 消失後，它們的主人在同一次執行內就刪得掉（所以租戶要先於帳號刪除）")
                .containsExactly(ADMIN_EMAIL, "m15-owner@test.com", "test-store-owner@nextkeytest.com");
        assertThat(blockedCartItemsAfter).as("擋住刪除的相依資料不得被遷移動到").isEqualTo(1);
        assertThat(blockedHashesAfter).as("保留下來的帳號密碼也必須全部是鎖住的")
                .hasSize(3).containsOnly(cleanAdminHashAfter);
    }

    @Test
    @DisplayName("被擋下的租戶原封不動：nextkey-test 店鋪的貼文、分類、房源一筆都不能少（每個租戶一個子交易，要嘛全刪、要嘛全留）")
    void blockedTenant_keepsAllItsPostsCategoriesAndListings() {
        assertThat(blockedTenantDataBefore)
                .as("固件要有意義：V94 之前這間店鋪確實有貼文、分類與房源（否則下面的比對恆真）")
                .hasSize(3).allMatch(n -> n > 0);
        assertThat(blockedTenantDataAfter)
                .as("店鋪因房源被購物車項目參照而保留時，它的貼文與分類不能先被刪掉——否則留下一間缺了一半資料的店鋪")
                .isEqualTo(blockedTenantDataBefore);
    }

    @Test
    @DisplayName("真實資料不受影響：同一個資料庫裡的真實店鋪、真實使用者（含密碼）、貼文、分類、房源，V94 一筆都不動；只有測試種子被清掉")
    void realData_isUntouchedWhileSeedsAreRemoved() {
        assertThat(realUsersAfter)
                .as("只剩 admin 與兩個真實使用者；7 個測試帳號中除 admin 外都已刪除")
                .containsExactly(ADMIN_EMAIL, REAL_BUYER_EMAIL, REAL_OWNER_EMAIL);
        assertThat(realTenantIdsAfter)
                .as("只剩系統租戶與真實店鋪；5 個測試租戶都已刪除")
                .containsExactly(SYSTEM_TENANT_ID, REAL_TENANT_ID);
        assertThat(realUserHashesAfter).as("真實使用者的密碼雜湊一個字元都不能變（不得被鎖）")
                .hasSize(2).containsOnly(realPasswordHash);
        for (String hash : realUserHashesAfter) {
            assertThat(encoder.matches(REAL_PASSWORD, hash)).as("真實使用者仍能用原密碼登入").isTrue();
        }
        assertThat(realTenantDataAfter).as("真實店鋪的 [貼文, 分類, 房源] 各 1 筆都還在").containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("冪等：再執行一次 V94 不出錯，狀態不變（乾淨、兩種有相依資料、含真實資料的資料庫都一樣）")
    void v94_isIdempotent() throws Exception {
        String v94 = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V94__Remove_Test_Seed_Data_And_Lock_Known_Accounts.sql").getInputStream(),
                StandardCharsets.UTF_8);

        execute(CLEAN_DB, v94);
        execute(DIRTY_DB, v94);
        execute(LISTING_BLOCKED_DB, v94);
        execute(REAL_DATA_DB, v94);
        assertThat(strings(LISTING_BLOCKED_DB, "SELECT email FROM users ORDER BY email")).isEqualTo(blockedUsersAfter);
        assertThat(strings(LISTING_BLOCKED_DB, "SELECT slug FROM tenants ORDER BY slug")).isEqualTo(blockedTenantSlugsAfter);
        assertThat(tenantDataCounts(LISTING_BLOCKED_DB, NEXTKEY_TEST_TENANT_ID)).isEqualTo(blockedTenantDataAfter);

        assertThat(strings(REAL_DATA_DB, "SELECT email FROM users ORDER BY email")).isEqualTo(realUsersAfter);
        assertThat(strings(REAL_DATA_DB, "SELECT id::text FROM tenants ORDER BY id")).isEqualTo(realTenantIdsAfter);
        assertThat(strings(REAL_DATA_DB, "SELECT password_hash FROM users WHERE email IN ('"
                + REAL_OWNER_EMAIL + "', '" + REAL_BUYER_EMAIL + "') ORDER BY email")).isEqualTo(realUserHashesAfter);

        assertThat(strings(CLEAN_DB, "SELECT email FROM users ORDER BY email")).isEqualTo(cleanUsersAfter);
        assertThat(strings(CLEAN_DB, "SELECT id::text FROM tenants ORDER BY id")).isEqualTo(cleanTenantIdsAfter);
        assertThat(strings(DIRTY_DB, "SELECT email FROM users ORDER BY email")).isEqualTo(dirtyUsersAfter);
        assertThat(String.join(",", strings(DIRTY_DB, "SELECT email || '=' || password_hash FROM users ORDER BY email")))
                .isEqualTo(dirtyHashAfterByEmail);
    }

    @Test
    @DisplayName("外鍵以外的錯誤不被吞掉：租戶刪除段（資料表被改名）與帳號刪除段（刪除觸發器丟例外）出現非外鍵錯誤時，遷移都失敗，而不是悄悄略過")
    void nonForeignKeyError_failsTheMigrationLoudly() throws Exception {
        String brokenDb = "s305_seed_broken";
        recreateDatabase(brokenDb);
        try {
            flyway(brokenDb, "93").migrate();

            // 租戶刪除段：V94 引用的資料表不存在（遷移與 schema 不一致）
            execute(brokenDb, "ALTER TABLE post_categories RENAME TO post_categories_renamed");
            assertThatThrownBy(() -> flyway(brokenDb, "94").migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("post_categories");

            // 帳號刪除段：刪除 users 時有觸發器丟出外鍵以外的例外（遷移是交易式的，上一次失敗已整個回滾，這裡可重試）
            execute(brokenDb, "ALTER TABLE post_categories_renamed RENAME TO post_categories");
            execute(brokenDb, "CREATE FUNCTION fail_user_delete() RETURNS trigger LANGUAGE plpgsql "
                    + "AS $$ BEGIN RAISE EXCEPTION 'boom-users'; END $$");
            execute(brokenDb, "CREATE TRIGGER fail_user_delete BEFORE DELETE ON users "
                    + "FOR EACH ROW EXECUTE FUNCTION fail_user_delete()");
            assertThatThrownBy(() -> flyway(brokenDb, "94").migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("boom-users");
        } finally {
            dropDatabase(brokenDb);
        }
    }

    // ───────────────────────── 輔助 ─────────────────────────

    private Flyway flyway(String db, String targetVersion) {
        return Flyway.configure()
                .dataSource(BASE_URL + db, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load();
    }

    private Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(BASE_URL + db, DB_USER, DB_PASS);
    }

    private void recreateDatabase(String db) throws SQLException {
        dropDatabase(db);
        try (Connection c = connect(MAINTENANCE_DB); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + db);
        }
    }

    private void dropDatabase(String db) throws SQLException {
        try (Connection c = connect(MAINTENANCE_DB); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }

    private void execute(String db, String sql) throws SQLException {
        try (Connection c = connect(db); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private List<String> strings(String db, String sql) {
        List<String> rows = new ArrayList<>();
        try (Connection c = connect(db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查詢失敗（需要 make test-db-up）：" + sql, e);
        }
        return rows;
    }

    private int count(String db, String sql) throws SQLException {
        try (Connection c = connect(db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 某個租戶底下的 [貼文, 貼文分類, 房源] 筆數。 */
    private List<Integer> tenantDataCounts(String db, String tenantId) throws SQLException {
        return List.of(
                count(db, "SELECT count(*) FROM posts WHERE tenant_id = '" + tenantId + "'"),
                count(db, "SELECT count(*) FROM post_categories WHERE tenant_id = '" + tenantId + "'"),
                count(db, "SELECT count(*) FROM listings WHERE tenant_id = '" + tenantId + "'"));
    }
}
