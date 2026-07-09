package com.nextkey.ecommerce.domain.model.user;

/**
 * RBAC 權限枚舉
 * 定義系統中所有的權限
 */
public enum Permission {

    // ========== Product Permissions ==========
    PRODUCT_READ("product:read", "檢視商品"),
    PRODUCT_CREATE("product:create", "建立商品"),
    PRODUCT_UPDATE("product:update", "更新商品"),
    PRODUCT_DELETE("product:delete", "刪除商品"),
    PRODUCT_MANAGE_ALL("product:manage:all", "管理所有商品"),

    // ========== Room Permissions ==========
    ROOM_READ("room:read", "檢視房源"),
    ROOM_CREATE("room:create", "建立房源"),
    ROOM_UPDATE("room:update", "更新房源"),
    ROOM_DELETE("room:delete", "刪除房源"),
    ROOM_MANAGE_ALL("room:manage:all", "管理所有房源"),

    // ========== Order Permissions ==========
    ORDER_READ("order:read", "檢視訂單"),
    ORDER_CREATE("order:create", "建立訂單"),
    ORDER_UPDATE("order:update", "更新訂單"),
    ORDER_CANCEL("order:cancel", "取消訂單"),
    ORDER_MANAGE_ALL("order:manage:all", "管理所有訂單"),

    // ========== Cart Permissions ==========
    CART_READ("cart:read", "檢視購物車"),
    CART_UPDATE("cart:update", "更新購物車"),
    CART_DELETE("cart:delete", "刪除購物車"),

    // ========== Booking Permissions ==========
    BOOKING_READ("booking:read", "檢視預訂"),
    BOOKING_CREATE("booking:create", "建立預訂"),
    BOOKING_UPDATE("booking:update", "更新預訂"),
    BOOKING_CANCEL("booking:cancel", "取消預訂"),
    BOOKING_MANAGE_ALL("booking:manage:all", "管理所有預訂"),

    // ========== Pricing Permissions ==========
    PRICING_READ("pricing:read", "檢視定價"),
    PRICING_UPDATE("pricing:update", "更新定價"),

    // ========== Tenant Permissions ==========
    TENANT_READ("tenant:read", "檢視租戶"),
    TENANT_UPDATE("tenant:update", "更新租戶"),
    TENANT_MANAGE("tenant:manage", "管理租戶"),

    // ========== User Permissions ==========
    USER_READ("user:read", "檢視用戶"),
    USER_CREATE("user:create", "建立用戶"),
    USER_UPDATE("user:update", "更新用戶"),
    USER_DELETE("user:delete", "刪除用戶"),
    USER_MANAGE_ALL("user:manage:all", "管理所有用戶"),

    // ========== Admin Permissions ==========
    ADMIN_PANEL("admin:panel", "進入管理後台"),
    SYSTEM_CONFIG("system:config", "系統設定"),
    ADMIN_READ("admin:read", "檢視管理後台結算審核資料"),
    ADMIN_WRITE("admin:write", "執行管理後台結算審核操作"),

    // ========== Settlement Permissions（Sprint 86） ==========
    SETTLEMENT_REVERSE("settlement:reverse", "結算單逆轉（SuperAdmin/財務長雙重授權）");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}