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
}
