package com.nextkey.ecommerce.shared.tenant;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    public static void setCurrentTenant(final UUID tenantId) {
        log.info("[TenantContext] setCurrentTenant: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenant = CURRENT_TENANT.get();
        log.debug("[TenantContext] getCurrentTenant called, returning: {}", tenant);
        return tenant;
    }

    public static void setCurrentUser(final UUID userId) {
        log.info("[TenantContext] setCurrentUser: {}", userId);
        CURRENT_USER.set(userId);
    }

    public static UUID getCurrentUser() {
        UUID user = CURRENT_USER.get();
        log.debug("[TenantContext] getCurrentUser called, returning: {}", user);
        return user;
    }

    public static void clear() {
        log.info("[TenantContext] clear");
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
