package com.nextkey.ecommerce.domain.model.user;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 角色-權限映射
 * 定義每個角色擁有的權限
 */
@Component
public class RolePermissionMapping {

    private static final Map<User.UserRole, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(User.UserRole.class);

    static {
        // ========== GUEST - 訪客，僅有檢視權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.GUEST, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.ROOM_READ,
                Permission.BOOKING_READ
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
                Permission.USER_UPDATE
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
                Permission.USER_UPDATE
        ));

        // ========== STORE_STAFF - 店鋪員工，受限權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.STORE_STAFF, EnumSet.of(
                Permission.PRODUCT_READ,
                Permission.ROOM_READ,
                Permission.ORDER_READ,
                Permission.BOOKING_READ,
                Permission.USER_READ
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
                Permission.USER_DELETE
        ));

        // ========== SUPER_ADMIN - 超級管理員，全部權限 ==========
        ROLE_PERMISSIONS.put(User.UserRole.SUPER_ADMIN, EnumSet.allOf(Permission.class));
    }

    /**
     * 根據角色取得對應的權限集合
     */
    public Set<Permission> getPermissions(User.UserRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }

    /**
     * 檢查角色是否擁有特定權限
     */
    public boolean hasPermission(User.UserRole role, Permission permission) {
        Set<Permission> permissions = getPermissions(role);
        return permissions.contains(permission);
    }

    /**
     * 檢查角色是否擁有特定權限（使用權限字串）
     */
    public boolean hasPermission(User.UserRole role, String permissionCode) {
        return Arrays.stream(Permission.values())
                .filter(p -> p.getCode().equals(permissionCode))
                .findFirst()
                .map(p -> hasPermission(role, p))
                .orElse(false);
    }

    /**
     * 將角色轉換為 Spring Security 使用的GrantedAuthority字符串
     */
    public List<String> getAuthorities(User.UserRole role) {
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