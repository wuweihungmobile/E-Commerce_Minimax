package com.nextkey.ecommerce.integration;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

/**
 * M05 訂單／M06 訂房 API 契約測試共用的真實資料（Sprint 243）：一家營業中的店鋪（店主、一件 100 元的商品、
 * 一間每晚 1000 元、最多 2 人的房間）、兩位沒有店鋪的買家、一位平台管理員，以及各自的 JWT。全部走真實資料庫，
 * 使用者與店鋪都是真實存在的列（購物車以外的流程會查使用者與店鋪）。
 */
final class ContractFixture {

    static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    final Tenant store;
    final User owner;
    final User buyer;
    final User other;
    final User admin;
    final String ownerToken;
    final String buyerToken;
    final String otherToken;
    final String adminToken;
    final UUID productId;
    final UUID roomId;

    ContractFixture(final TenantRepository tenants, final UserRepository users, final ListingRepository listings,
                    final JdbcTemplate jdbc, final JwtTokenService jwt) {
        long stamp = System.nanoTime();
        store = tenants.save(Tenant.builder().name("契約測試店鋪 " + stamp).slug("contract-" + stamp)
                .contactEmail("contract-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        owner = user(users, "owner", stamp, User.UserRole.STORE_OWNER, store.getId());
        buyer = user(users, "buyer", stamp, User.UserRole.BUYER, null);
        other = user(users, "other", stamp, User.UserRole.BUYER, null);
        admin = user(users, "admin", stamp, User.UserRole.SUPER_ADMIN, null);
        ownerToken = jwt.generateAccessToken(owner.getId(), owner.getEmail(), "STORE_OWNER", store.getId().toString());
        buyerToken = jwt.generateAccessToken(buyer.getId(), buyer.getEmail(), "BUYER", SYSTEM_TENANT_ID.toString());
        otherToken = jwt.generateAccessToken(other.getId(), other.getEmail(), "BUYER", SYSTEM_TENANT_ID.toString());
        adminToken = jwt.generateAccessToken(admin.getId(), admin.getEmail(), "SUPER_ADMIN", SYSTEM_TENANT_ID.toString());
        productId = listing(listings, Listing.ListingType.PRODUCT, "契約測試商品", "100.00");
        roomId = listing(listings, Listing.ListingType.ROOM, "契約測試房間", "1000.00");
        jdbc.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, created_at, "
                + "updated_at) VALUES (?, 2, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomId);
    }

    private User user(final UserRepository users, final String label, final long stamp, final User.UserRole role,
                      final UUID tenantId) {
        return users.save(User.builder().email("contract-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy").fullName(label).role(role).status("ACTIVE").tenantId(tenantId).build());
    }

    /** 房源要同時設關聯（寫入資料庫的是 tenant／owner 關聯，tenantId／ownerId 只是唯讀影子欄位）。 */
    private UUID listing(final ListingRepository listings, final Listing.ListingType type, final String title,
                         final String price) {
        return listings.save(Listing.builder().tenant(store).owner(owner).listingType(type).title(title)
                .basePrice(new BigDecimal(price)).currency("TWD").status(Listing.ListingStatus.ACTIVE).build()).getId();
    }
}
