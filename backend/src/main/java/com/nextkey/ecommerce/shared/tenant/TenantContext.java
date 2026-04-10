package com.nextkey.ecommerce.shared.tenant;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        log.debug("Setting tenant context to: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentUser(UUID userId) {
        log.debug("Setting user context to: {}", userId);
        CURRENT_USER.set(userId);
    }

    public static UUID getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    public static boolean hasUser() {
        return CURRENT_USER.get() != null;
    }
}
