package com.nextkey.ecommerce.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RolePermissionMapping 單元測試（DEF-023：booking:read 權限收斂）。
 *
 * <p>驗證 GUEST 角色不再持有 BOOKING_READ（訪客本質上不應有任何預訂記錄，保留此權限形同讓任何
 * 登入者皆可查詢 booking 相關資料，是 IDOR 的放大面），同時確認其他角色（BUYER 等）既有的
 * BOOKING_READ 權限不受影響（最小爆炸半徑）。
 */
@DisplayName("RolePermissionMapping（DEF-023：GUEST 移除 booking:read）")
class RolePermissionMappingTest {

    private final RolePermissionMapping mapping = new RolePermissionMapping();

    @Test
    @DisplayName("GUEST 不再持有 BOOKING_READ")
    void guest_doesNotHaveBookingRead() {
        assertThat(mapping.hasPermission(User.UserRole.GUEST, Permission.BOOKING_READ)).isFalse();
    }

    @Test
    @DisplayName("GUEST 仍保留 PRODUCT_READ / ROOM_READ（訪客瀏覽權限不受影響）")
    void guest_stillHasReadOnlyBrowsingPermissions() {
        assertThat(mapping.hasPermission(User.UserRole.GUEST, Permission.PRODUCT_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.GUEST, Permission.ROOM_READ)).isTrue();
    }

    @Test
    @DisplayName("BUYER 仍持有 BOOKING_READ（未受 GUEST 調整影響）")
    void buyer_stillHasBookingRead() {
        assertThat(mapping.hasPermission(User.UserRole.BUYER, Permission.BOOKING_READ)).isTrue();
    }

    @Test
    @DisplayName("ADMIN 仍持有 BOOKING_READ（未受 GUEST 調整影響）")
    void admin_stillHasBookingRead() {
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.BOOKING_READ)).isTrue();
    }

    // ── DEF-075（Sprint 129）：dashboard/faq/knowledge/media 角色授權 ──

    @Test
    @DisplayName("STORE_OWNER 對 dashboard/faq/knowledge/media 擁有完整權限")
    void storeOwner_hasFullAccessToDef075Modules() {
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.DASHBOARD_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.FAQ_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.FAQ_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.FAQ_DELETE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.KNOWLEDGE_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.MEDIA_DELETE)).isTrue();
    }

    @Test
    @DisplayName("STORE_STAFF 對 dashboard/faq/knowledge/media 僅唯讀，無寫入權")
    void storeStaff_isReadOnlyForDef075Modules() {
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.DASHBOARD_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.FAQ_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.KNOWLEDGE_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.MEDIA_READ)).isTrue();

        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.FAQ_CREATE)).isFalse();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.KNOWLEDGE_UPDATE)).isFalse();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.MEDIA_DELETE)).isFalse();
    }

    @Test
    @DisplayName("ADMIN 對 dashboard/faq/knowledge/media 擁有完整跨租戶權限")
    void admin_hasFullAccessToDef075Modules() {
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.DASHBOARD_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.FAQ_DELETE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.KNOWLEDGE_DELETE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.MEDIA_DELETE)).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN 經 EnumSet.allOf 自動取得 dashboard/faq/knowledge/media 全部權限")
    void superAdmin_hasAllDef075Permissions() {
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.DASHBOARD_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.FAQ_DELETE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.KNOWLEDGE_DELETE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.MEDIA_DELETE)).isTrue();
    }

    // ── DEF-092（Sprint 130）：cms:*/notification:create 角色授權 ──
    // 角色分派為 Sprint 129 拍板的細緻方案（非 DEF-073/075 的 OWNER全權/STAFF唯讀/ADMIN全權 統一樣板）：
    // cms:read/create/update 比照 PostController 既有 CRUD 開放範圍
    // （STORE_OWNER/STORE_STAFF/SELLER/HOST 皆可，無唯讀限制）；cms:publish 限制較高層級，
    // 僅 STORE_OWNER+ADMIN（含 SUPER_ADMIN）。notification:create 原本也在此列，但
    // DEF-238（Sprint 180）發現 NotificationService.sendNotification 對目標 userId 無租戶範圍
    // 檢查，任一 STORE_OWNER 可對系統內任何使用者發送通知，經拍板收斂為僅限 SUPER_ADMIN。

    @Test
    @DisplayName("STORE_OWNER 對 cms 擁有完整權限，但 notification:create 已收斂為僅限 SUPER_ADMIN（DEF-238）")
    void storeOwner_hasFullAccessToDef092Modules() {
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.CMS_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.CMS_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.CMS_PUBLISH)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.NOTIFICATION_CREATE)).isFalse();
    }

    @Test
    @DisplayName("STORE_STAFF 可 cms:read/create/update（比照 PostController，非唯讀），但無 cms:publish/notification:create")
    void storeStaff_hasCmsCrudButNotPublishOrNotification() {
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.CMS_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.CMS_UPDATE)).isTrue();

        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.CMS_PUBLISH)).isFalse();
        assertThat(mapping.hasPermission(User.UserRole.STORE_STAFF, Permission.NOTIFICATION_CREATE)).isFalse();
    }

    @Test
    @DisplayName("SELLER/HOST 可 cms:read/create/update（比照 PostController），但無 cms:publish/notification:create")
    void sellerAndHost_haveCmsCrudButNotPublishOrNotification() {
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.CMS_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.CMS_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.CMS_PUBLISH)).isFalse();
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.NOTIFICATION_CREATE)).isFalse();

        assertThat(mapping.hasPermission(User.UserRole.HOST, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.HOST, Permission.CMS_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.HOST, Permission.CMS_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.HOST, Permission.CMS_PUBLISH)).isFalse();
        assertThat(mapping.hasPermission(User.UserRole.HOST, Permission.NOTIFICATION_CREATE)).isFalse();
    }

    @Test
    @DisplayName("ADMIN 對 cms 擁有完整跨租戶權限，但 notification:create 已收斂為僅限 SUPER_ADMIN（DEF-238）")
    void admin_hasFullAccessToDef092Modules() {
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.CMS_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.CMS_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.CMS_PUBLISH)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.ADMIN, Permission.NOTIFICATION_CREATE)).isFalse();
    }

    @Test
    @DisplayName("SUPER_ADMIN 經 EnumSet.allOf 自動取得 cms/notification 全部權限")
    void superAdmin_hasAllDef092Permissions() {
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.CMS_READ)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.CMS_PUBLISH)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SUPER_ADMIN, Permission.NOTIFICATION_CREATE)).isTrue();
    }

    // ── DEF-191（Sprint 152）：SELLER 有 order:update 卻無 order:create，
    // 曾導致能把訂單確認到 CONFIRMED 卻無法建立物流單完成出貨。修法是把
    // LogisticsController.createLogistics 的 @PreAuthorize 改用 order:update，
    // 而非在此擴大 SELLER 的權限清單——以下測試鎖住這個刻意的權限矩陣邊界，
    // 避免未來有人「順手」幫 SELLER 加回 ORDER_CREATE 而破壞此決策的依據。

    @Test
    @DisplayName("SELLER 持有 order:update 但不持有 order:create（DEF-191 權限矩陣邊界）")
    void seller_hasOrderUpdateButNotOrderCreate() {
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.ORDER_UPDATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.SELLER, Permission.ORDER_CREATE)).isFalse();
    }

    @Test
    @DisplayName("STORE_OWNER 同時持有 order:create 與 order:update（不受 DEF-191 修法影響）")
    void storeOwner_hasBothOrderCreateAndOrderUpdate() {
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.ORDER_CREATE)).isTrue();
        assertThat(mapping.hasPermission(User.UserRole.STORE_OWNER, Permission.ORDER_UPDATE)).isTrue();
    }
}
