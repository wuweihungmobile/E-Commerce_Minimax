package com.nextkey.ecommerce.domain.model.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 角色-權限映射
 * 定義每個角色擁有的權限
 */
@Component
public class RolePermissionMapping {

    private static final Map<User.UserRole, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(User.UserRole.class);

    static {
        // ========== GUEST - 訪客，僅有檢視權限 ==========
        // DEF-023：移除 BOOKING_READ——訪客本質上不應有任何預訂記錄，保留此權限形同讓任何登入者
        // 皆可查詢 booking 相關資料，是 IDOR 的放大面。
        ROLE_PERMISSIONS.put(User.UserRole.GUEST, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.ROOM_READ
        ));

        // ========== BUYER - 買家，購物相關權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.BUYER, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.ROOM_READ,
                Permission.ORDER_READ,
                Permission.ORDER_CREATE,
                Permission.ORDER_CANCEL,  // 買家可取消自己的訂單（US-M05-004）
                Permission.BOOKING_READ,
                Permission.BOOKING_CREATE,
                Permission.BOOKING_CANCEL,  // 買家可取消自己的預訂
                Permission.CART_READ,
                Permission.CART_UPDATE,
                Permission.CART_DELETE,
                Permission.USER_READ,
                Permission.USER_UPDATE,
                Permission.SUPPORT_TICKET_READ,   // 買家提交/檢視自己的客服工單（Sprint 91）
                Permission.SUPPORT_TICKET_CREATE
        ));

        // ========== SELLER - 賣家，商品管理 ==========
        ROLE_PERMISSIONS.put(User.UserRole.SELLER, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.PRODUCT_CREATE,
                Permission.PRODUCT_UPDATE,
                Permission.PRODUCT_DELETE,
                Permission.ORDER_READ,
                Permission.ORDER_UPDATE,
                Permission.USER_READ,
                Permission.USER_UPDATE
        ));

        // ========== HOST - 民宿主人，房源管理 ==========
        ROLE_PERMISSIONS.put(User.UserRole.HOST, EnumSet.of(
                Permission.ROOM_READ,
                Permission.ROOM_CREATE,
                Permission.ROOM_UPDATE,
                Permission.ROOM_DELETE,
                Permission.BOOKING_READ,
                Permission.BOOKING_UPDATE,
                Permission.PRICING_READ,
                Permission.PRICING_UPDATE,
                Permission.USER_READ,
                Permission.USER_UPDATE
        ));

        // ========== STORE_OWNER - 店主，店鋪全部權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.STORE_OWNER, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.PRODUCT_CREATE,
                Permission.PRODUCT_UPDATE,
                Permission.PRODUCT_DELETE,
                Permission.ROOM_READ,
                Permission.ROOM_CREATE,
                Permission.ROOM_UPDATE,
                Permission.ROOM_DELETE,
                Permission.ORDER_READ,
                Permission.ORDER_CREATE,
                Permission.ORDER_UPDATE,
                Permission.ORDER_CANCEL,
                Permission.BOOKING_READ,
                Permission.BOOKING_CREATE,
                Permission.BOOKING_UPDATE,
                Permission.BOOKING_CANCEL,
                Permission.TENANT_READ,
                Permission.TENANT_UPDATE,
                Permission.USER_READ,
                Permission.USER_CREATE,
                Permission.USER_UPDATE,
                Permission.SUPPORT_TICKET_READ,   // 店主處理自己租戶的客服工單（Sprint 91）
                Permission.SUPPORT_TICKET_CREATE,
                Permission.SUPPORT_TICKET_UPDATE
        ));

        // ========== STORE_STAFF - 店鋪員工，受限權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.STORE_STAFF, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.ROOM_READ,
                Permission.ORDER_READ,
                Permission.BOOKING_READ,
                Permission.USER_READ,
                Permission.SUPPORT_TICKET_READ,   // 店鋪員工處理自己租戶的客服工單（Sprint 91）
                Permission.SUPPORT_TICKET_CREATE,
                Permission.SUPPORT_TICKET_UPDATE
        ));

        // ========== ADMIN - 管理員，租戶內管理 ==========
        ROLE_PERMISSIONS.put(User.UserRole.ADMIN, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.PRODUCT_CREATE,
                Permission.PRODUCT_UPDATE,
                Permission.PRODUCT_DELETE,
                Permission.PRODUCT_MANAGE_ALL,
                Permission.ROOM_READ,
                Permission.ROOM_CREATE,
                Permission.ROOM_UPDATE,
                Permission.ROOM_DELETE,
                Permission.ROOM_MANAGE_ALL,
                Permission.ORDER_READ,
                Permission.ORDER_CREATE,
                Permission.ORDER_UPDATE,
                Permission.ORDER_CANCEL,
                Permission.ORDER_MANAGE_ALL,
                Permission.BOOKING_READ,
                Permission.BOOKING_CREATE,
                Permission.BOOKING_UPDATE,
                Permission.BOOKING_CANCEL,
                Permission.BOOKING_MANAGE_ALL,
                Permission.TENANT_READ,
                Permission.TENANT_UPDATE,
                Permission.USER_READ,
                Permission.USER_CREATE,
                Permission.USER_UPDATE,
                Permission.USER_DELETE,
                Permission.ADMIN_READ,
                Permission.ADMIN_WRITE,
                Permission.SUPPORT_TICKET_READ,   // 平台客服工單，跨租戶（Sprint 91）
                Permission.SUPPORT_TICKET_CREATE,
                Permission.SUPPORT_TICKET_UPDATE,
                Permission.SUPPORT_TICKET_MANAGE_ALL
        ));

        // ========== CFO - 財務長，僅結算單逆轉雙重授權（Sprint 86） ==========
        ROLE_PERMISSIONS.put(User.UserRole.CFO, EnumSet.of(
                Permission.TENANT_READ,
                Permission.ORDER_READ,
                Permission.ADMIN_READ,
                Permission.SETTLEMENT_REVERSE
        ));

        // ========== SUPER_ADMIN - 超級管理員，全部權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.SUPER_ADMIN, EnumSet.allOf(Permission.class));
    }

    /**
     * 根據角色取得對應的權限集合
     */
    public Set<Permission> getPermissions(final User.UserRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }

    /**
     * 檢查角色是否擁有特定權限
     */
    public boolean hasPermission(final User.UserRole role, final Permission permission) {
        Set<Permission> permissions = getPermissions(role);
        return permissions.contains(permission);
    }

    /**
     * 檢查角色是否擁有特定權限（使用權限字串）
     */
    public boolean hasPermission(final User.UserRole role, final String permissionCode) {
        return Arrays.stream(Permission.values())
                .filter(p -> p.getCode().equals(permissionCode))
                .findFirst()
                .map(p -> hasPermission(role, p))
                .orElse(false);
    }

    /**
     * 將角色轉換為 Spring Security 使用的GrantedAuthority字符串
     */
    public List<String> getAuthorities(final User.UserRole role) {
        Set<Permission> permissions = getPermissions(role);
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + role.name());
        // Also add the role name itself (without ROLE_ prefix) for @PreAuthorize("hasAuthority('STORE_OWNER')")
        authorities.add(role.name());
        for (Permission permission : permissions) {
            authorities.add(permission.getCode());
        }
        return authorities;
    }
}