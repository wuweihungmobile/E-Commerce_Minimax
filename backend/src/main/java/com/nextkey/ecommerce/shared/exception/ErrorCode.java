package com.nextkey.ecommerce.shared.exception;

public enum ErrorCode {
    // Authentication & Authorization (E-1000s)
    E_1000("E-1000", "Authentication required"),
    E_1001("E-1001", "Invalid credentials"),
    E_1002("E-1002", "Token expired"),
    E_1003("E-1003", "Invalid token"),
    E_1004("E-1004", "Account locked"),
    E_1005("E-1005", "Email already exists"),
    E_1006("E-1006", "User not found"),
    E_1007("E-1007", "Insufficient permissions"),
    E_1008("E-1008", "OAuth account already linked to another user"),

    // Tenant & Multi-tenancy (E-2000s)
    E_2000("E-2000", "Tenant not found"),
    E_2001("E-2001", "Tenant not active"),
    E_2002("E-2002", "Tenant suspended"),
    E_2003("E-2003", "Tenant context ambiguous"),
    E_2004("E-2004", "Feature disabled for tenant"),
    E_2005("E-2005", "Tenant status is not PENDING_REVIEW"),
    E_4031("E-4031", "Not authorized to operate this store"),
    E_4041("E-4041", "Store not found"),

    // Listing & Product (E-3000s)
    E_3000("E-3000", "Listing not found"),
    E_3001("E-3001", "Invalid listing type"),
    E_3002("E-3002", "Listing not active"),
    E_3003("E-3003", "SKU not found"),
    E_3004("E-3004", "Insufficient inventory"),
    E_3005("E-3005", "Duplicate SKU code"),
    E_3006("E-3006", "Category not found"),
    E_3007("E-3007", "Brand not found"),

    // CMS & Posts (E-4100s)
    E_4100("E-4100", "Post not found"),
    E_4101("E-4101", "Post not published"),
    E_4102("E-4102", "Category not found"),
    E_4103("E-4103", "Media not found"),
    E_4104("E-4104", "Embed duplicate listing"),
    E_4105("E-4105", "Embed listing not found"),
    E_4106("E-4106", "Invalid post status transition"),

    // Room & Booking (E-4000s)
    E_4000("E-4000", "Room not found"),
    E_4001("E-4001", "Room calendar conflict"),
    E_4002("E-4002", "Date already booked"),
    E_4003("E-4003", "Invalid date range"),
    E_4004("E-4004", "Check-out must be after check-in"),
    E_4005("E-4005", "Guest count exceeds capacity"),
    E_4006("E-4006", "Booking not found"),
    E_4007("E-4007", "Booking cannot be cancelled"),
    E_4008("E-4008", "Pricing rule conflict"),

    // Order (E-5000s)
    E_5000("E-5000", "Order not found"),
    E_5001("E-5001", "Invalid order status"),
    E_5002("E-5002", "Order cannot be cancelled"),
    E_5003("E-5003", "Order item not found"),
    E_5004("E-5004", "Cart is empty"),
    E_5005("E-5005", "Cart item not found"),
    E_5006("E-5006", "Invalid quantity"),
    E_5007("E-5007", "Promo code invalid"),
    E_5008("E-5008", "Promo code expired"),
    E_5009("E-5009", "Promo code usage limit reached"),
    // 🔴 Sprint 18 US-004 Phase 1: 新增專用錯誤碼，取代 E_5001/E_5005/E_5006 誤用
    E_5010("E-5010", "Booking status invalid"),
    E_5011("E-5011", "Payment status invalid"),
    E_5012("E-5012", "Refund status invalid"),
    E_5013("E-5013", "Settlement statement not found"),
    E_5014("E-5014", "Settlement state transition invalid"),

    // Payment (E-6000s)
    E_6000("E-6000", "Payment not found"),
    E_6001("E-6001", "Payment failed"),
    E_6002("E-6002", "Payment cancelled"),
    E_6003("E-6003", "Payment already processed"),
    E_6004("E-6004", "Invalid payment method"),
    E_6005("E-6005", "Idempotency key already used"),

    // ERP & Inventory (E-7000s)
    E_7000("E-7000", "Supplier not found"),
    E_7001("E-7001", "Purchase order not found"),
    E_7002("E-7002", "Invalid purchase order status"),
    E_7003("E-7003", "Stock movement not found"),
    E_7004("E-7004", "Insufficient stock"),
    E_7005("E-7005", "Invalid stock adjustment"),
    E_7006("E-7006", "Duplicate PO number"),
    E_7007("E-7007", "Purchase order item not found"),
    E_7008("E-7008", "Supplier is inactive"),
    E_7009("E-7009", "Invalid receive quantity"),

    // Logistics (E-7500s)
    E_7500("E-7500", "Logistics not found"),
    E_7501("E-7501", "Active logistics already exists"),
    E_7502("E-7502", "Cannot cancel delivered logistics"),

    // Pricing (E-8000s)
    E_8000("E-8000", "Pricing rule not found"),
    E_8001("E_8001", "Invalid pricing rule config"),
    // 🔴 Sprint 18 US-004 Phase 1: 新增專用錯誤碼，取代 E_8000 誤用
    E_8002("E-8002", "Notification not found"),
    E_8003("E-8003", "Notification template not found"),
    E_8004("E-8004", "CMS page not found"),
    E_8005("E-8005", "CMS banner not found"),

    // Review (E-1080s dedicated to Review module)
    // 🔴 Sprint 16 US-001: 評價重複回覆
    E_1086("E-1086", "Review already has a reply"),
    // 🔴 Sprint 18 US-004 Phase 1: 新增專用錯誤碼，取代 E_8000 誤用（Review 模組）
    E_1087("E-1087", "Review not found"),
    // 🔴 Sprint 16 US-005: 多圖評價 9 張上限
    E_1088("E-1088", "Review images count exceeds the limit: max 9, actual %d"),
    // 🔴 Sprint 16 US-005: 評價圖片無效
    E_1089("E-1089", "Review image is invalid: %s"),
    // 🔴 Sprint 16 US-006: 圖片不存在
    E_1090("E-1090", "Review image not found at index %d"),
    // 🔴 Sprint 16 US-006: 非本人操作
    E_1091("E-1091", "You can only operate on your own review images"),
    // 🔴 Sprint 18 US-004 Phase 1: 新增專用錯誤碼，取代 E_8000 誤用（Booking Review 模組）
    E_1092("E-1092", "Booking review not found"),
    // 🔴 Sprint 18 Buffer Phase 2A: 取代 E_8000 誤用（重複評價）
    E_1093("E-1093", "You have already reviewed this item"),
    E_1094("E-1094", "You have already reviewed this booking"),
    E_1095("E-1095", "Invalid rating range"),

    // Validation (E-9000s)
    E_9000("E-9000", "Validation error"),
    E_9001("E-9001", "Invalid email format"),
    E_9002("E-9002", "Invalid phone format"),
    E_9003("E-9003", "Password too weak"),
    E_9004("E-9004", "Invalid UUID format"),
    E_9005("E-9005", "Required field missing"),
    E_9006("E-9006", "Field too long"),
    E_9007("E-9007", "Invalid date format"),
    E_9008("E-9008", "Value out of range"),

    // Tenant Application (E-4090s) - Note: API doc uses 4091, 4092 but we'll use existing ranges
    E_4091("E-4091", "Store name already in use"),
    E_4092("E-4092", "Store application already exists"),

    // System (E-9900s)
    E_9900("E-9900", "Internal server error"),
    E_9901("E-9901", "Database error"),
    E_9902("E-9902", "Redis error"),
    E_9903("E-9903", "External API error"),
    E_9904("E-9904", "Rate limit exceeded"),
    E_9905("E-9905", "Service unavailable");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getFormattedMessage(final String... args) {
        if (args.length == 0) {
            return message;
        }
        return String.format(message, (Object) args);
    }
}
