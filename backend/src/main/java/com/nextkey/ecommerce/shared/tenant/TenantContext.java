package com.nextkey.ecommerce.shared.tenant;

import java.util.UUID;

import com.nextkey.ecommerce.shared.constants.AppConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    /**
     * 系統租戶佔位值：沒有加入任何店鋪的使用者（一般買家、尚未開店的 SELLER／HOST）的租戶脈絡，<b>不是 null</b>，
     * 而是這個值，且所有這類使用者共用同一個。一般消費者建立的資料（訂單、訂房、客服工單、退貨申請…）也常蓋成它。
     * 所以「拿呼叫者的租戶去查／去比對」對這類使用者等於「同租戶」——Sprint 232～234 連續找到四處因此外洩的端點。
     */
    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

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

    /**
     * 這個租戶是不是「真正的店鋪租戶」：非 null、且不是系統租戶佔位值。
     *
     * <p>店家層端點（客服工單、退貨審核、結算…）要用「呼叫者的租戶」查詢或比對時，必須先確認它是店鋪租戶；
     * 否則系統租戶的呼叫者（任何一般買家）會被當成「同租戶」，看到、甚至能改所有其他消費者的資料。
     * 注意 {@link #hasTenant()} 對系統租戶也回 true，<b>不能</b>用來做這個判斷。
     */
    public static boolean isStoreTenant(final UUID tenantId) {
        return tenantId != null && !SYSTEM_TENANT.equals(tenantId);
    }

    public static boolean hasUser() {
        return CURRENT_USER.get() != null;
    }
}
