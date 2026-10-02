package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * V87 遷移的資料邏輯（Sprint 235，DEF-329）：沒有任何有效（ACTIVE）成員資格、角色卻仍是 STORE_STAFF 的使用者，
 * 收回成 BUYER；其他人一律不動。
 *
 * <p>整合測試的資料庫是 Hibernate 建的，不會執行 Flyway；這裡直接讀 {@code db/migration/V87__*.sql} 的實際內容，
 * 對種好的資料執行。整個測試在交易內、結束後回滾，不會動到其他測試共用資料庫裡的任何列。
 * （Flyway 本身能不能跑、語法對不對，由 {@code make validate-e2e}／{@code make validate-schema} 對乾淨資料庫執行遷移驗證。）
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 235: V87 遷移——沒有有效成員資格的 STORE_STAFF 收回成 BUYER")
class RevokeStaffRoleMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V87__Revoke_Staff_Role_Without_Active_Membership.sql";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMemberRepository tenantMemberRepository;

    private Tenant newTenant() {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder()
                .name("V87 測試店 " + stamp).slug("v87-" + stamp)
                .contactEmail("v87-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newUser(final User.UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .email("v87-" + System.nanoTime() + "@example.com").passwordHash("dummy").role(role).status("ACTIVE").build());
    }

    private void member(final Tenant tenant, final User user, final TenantMember.MemberStatus status) {
        tenantMemberRepository.saveAndFlush(TenantMember.builder()
                .tenantId(tenant.getId()).userId(user.getId())
                .storeRole(TenantMember.StoreRole.STORE_STAFF).status(status).joinedAt(Instant.now()).build());
    }

    private String roleOf(final User user) {
        return jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.getId());
    }

    private int runMigration() throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8);
        return jdbcTemplate.update(sql);
    }

    @Test
    @DisplayName("沒有有效成員資格的 STORE_STAFF（已移除、只剩受邀、完全沒有成員列）收回成 BUYER；有效店員、其他角色一律不動")
    void revokesOnlyStaffWithoutActiveMembership() throws Exception {
        Tenant tenant = newTenant();
        Tenant other = newTenant();
        User removed = newUser(User.UserRole.STORE_STAFF);
        member(tenant, removed, TenantMember.MemberStatus.REMOVED);
        User reinvited = newUser(User.UserRole.STORE_STAFF);
        member(tenant, reinvited, TenantMember.MemberStatus.INVITED);
        User orphan = newUser(User.UserRole.STORE_STAFF);
        User activeStaff = newUser(User.UserRole.STORE_STAFF);
        member(tenant, activeStaff, TenantMember.MemberStatus.ACTIVE);
        User activeElsewhere = newUser(User.UserRole.STORE_STAFF);
        member(tenant, activeElsewhere, TenantMember.MemberStatus.REMOVED);
        member(other, activeElsewhere, TenantMember.MemberStatus.ACTIVE);
        User removedBuyer = newUser(User.UserRole.BUYER);
        member(tenant, removedBuyer, TenantMember.MemberStatus.REMOVED);
        User owner = newUser(User.UserRole.STORE_OWNER);

        runMigration();

        assertThat(roleOf(removed)).as("已移除的店員").isEqualTo("BUYER");
        assertThat(roleOf(reinvited)).as("被移除後重新邀請、尚未接受的人（接受時角色會再設回去）").isEqualTo("BUYER");
        assertThat(roleOf(orphan)).as("角色是店員卻完全沒有成員列的人").isEqualTo("BUYER");
        assertThat(roleOf(activeStaff)).as("有效店員不動").isEqualTo("STORE_STAFF");
        assertThat(roleOf(activeElsewhere)).as("被一間店移除、但仍是另一間店的有效店員").isEqualTo("STORE_STAFF");
        assertThat(roleOf(removedBuyer)).as("本來就是 BUYER").isEqualTo("BUYER");
        assertThat(roleOf(owner)).as("店主不動").isEqualTo("STORE_OWNER");
    }

    @Test
    @DisplayName("可重複執行：第二次不會再更新任何本測試種下的列")
    void isIdempotent() throws Exception {
        Tenant tenant = newTenant();
        User removed = newUser(User.UserRole.STORE_STAFF);
        member(tenant, removed, TenantMember.MemberStatus.REMOVED);

        runMigration();
        int secondRun = runMigration();

        assertThat(roleOf(removed)).isEqualTo("BUYER");
        assertThat(secondRun).as("第二次沒有任何 STORE_STAFF 缺有效成員資格可更新").isZero();
    }
}
