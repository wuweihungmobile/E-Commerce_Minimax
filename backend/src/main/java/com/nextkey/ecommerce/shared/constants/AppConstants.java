package com.nextkey.ecommerce.shared.constants;

public class AppConstants {

    // System Tenant ID (固定值)
    public static final String SYSTEM_TENANT_ID = "00000000-0000-0000-0000-000000000001";

    // User Roles
    public static final String ROLE_GUEST = "GUEST";
    public static final String ROLE_BUYER = "BUYER";
    public static final String ROLE_SELLER = "SELLER";
    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_STORE_OWNER = "STORE_OWNER";
    public static final String ROLE_STORE_STAFF = "STORE_STAFF";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    // Tenant Status
    public static final String TENANT_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String TENANT_ACTIVE = "ACTIVE";
    public static final String TENANT_REJECTED = "REJECTED";
    public static final String TENANT_SUSPENDED = "SUSPENDED";
    public static final String TENANT_TERMINATED = "TERMINATED";

    // Listing
    public static final String LISTING_TYPE_PRODUCT = "PRODUCT";
    public static final String LISTING_TYPE_ROOM = "ROOM";

    public static final String LISTING_STATUS_DRAFT = "DRAFT";
    public static final String LISTING_STATUS_ACTIVE = "ACTIVE";
    public static final String LISTING_STATUS_INACTIVE = "INACTIVE";
    public static final String LISTING_STATUS_DELETED = "DELETED";

    // Order Status
    public static final String ORDER_CREATED = "CREATED";
    public static final String ORDER_PAID = "PAID";
    public static final String ORDER_CONFIRMED = "CONFIRMED";
    public static final String ORDER_SHIPPING = "SHIPPING";
    public static final String ORDER_DELIVERED = "DELIVERED";
    public static final String ORDER_COMPLETED = "COMPLETED";
    public static final String ORDER_CANCELLED = "CANCELLED";

    // Payment
    public static final String PAYMENT_METHOD_LINE_PAY = "LINE_PAY";
    public static final String PAYMENT_METHOD_CREDIT_CARD = "CREDIT_CARD";
    public static final String PAYMENT_METHOD_MOCK = "MOCK";

    // Feature Toggle Keys
    public static final String FEATURE_RETAIL_ENABLED = "RETAIL_ENABLED";
    public static final String FEATURE_BOOKING_ENABLED = "BOOKING_ENABLED";
    public static final String FEATURE_CMS_ENABLED = "CMS_ENABLED";
    public static final String FEATURE_ERP_ENABLED = "ERP_ENABLED";
    public static final String FEATURE_DYNAMIC_PRICING_ENABLED = "DYNAMIC_PRICING_ENABLED";
    public static final String FEATURE_PROMO_ENABLED = "PROMO_ENABLED";

    // Quota Limits (Sprint 147: MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS 數量配額強制執行，見 PRD §4.4)
    public static final int QUOTA_MAX_PRODUCTS = 100;
    public static final int QUOTA_MAX_ROOMS = 20;
    public static final int QUOTA_MAX_POSTS = 50;

    // Redis Keys
    public static final String REDIS_CART_PREFIX = "cart:";
    public static final String REDIS_LOCK_PREFIX = "lock:";
    public static final String REDIS_TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Validation
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_EMAIL_LENGTH = 255;

    private AppConstants() {
        // Prevent instantiation
    }
}
