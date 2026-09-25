package com.nextkey.ecommerce.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.core.admin.AdminService;
import com.nextkey.ecommerce.core.room.RoomService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 以 {@code Specification} 動態組合的查詢，在真實 PostgreSQL 的可執行性探測（DEF-277 的後續補強）。
 *
 * <p>{@code RepositoryQueryExecutionIntegrationTest} 只執行 Repository 介面<b>自己宣告</b>的方法；
 * {@code JpaSpecificationExecutor.findAll(Specification, Pageable)} 是繼承來的，且 Specification 由 Service 動態組出，
 * 掃描碰不到。全庫共 4 處（{@code RoomService.getRooms}、{@code AdminService.getTenants／getUsers／getAuditLogs}），
 * 屬性路徑（如 {@code root.get("listing").get("tenantId")}）拼錯只會在執行時才爆。
 *
 * <p>本測試對每一處把<b>所有篩選條件都帶上</b>，讓每個 predicate 都被實際組進 SQL。前兩處另有命中資料的正向斷言。
 * 這些 Specification 只在條件有值時才加入 predicate（程式註解亦說明是為了避開 PostgreSQL 對純 null 參數的型別推斷限制），
 * 所以「參數為 null」的問題不適用於此，只需驗證「條件全帶上」這一種組合。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-SPEC-EXEC: Specification 動態查詢在真實資料庫可執行（DEF-277 後續補強）")
class SpecificationQueriesRealDbIntegrationTest {

    @Autowired private RoomService roomService;
    @Autowired private AdminService adminService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("RoomService.getRooms：所有篩選條件（人數、地點、關鍵字、租戶、上架狀態）同時帶上可執行")
    void roomSearchWithAllFilters_executes() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        try {
            assertThat(roomService.getRooms(2, "__spec_probe__", "__spec_probe__", 0, 10, "createdAt", "DESC")
                    .getTotalElements()).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("AdminService.getTenants：狀態＋關鍵字（name/slug）篩選可執行，且命中剛建立的租戶")
    void adminTenantSearchWithAllFilters_findsSeededTenant() {
        final String stamp = String.valueOf(System.nanoTime());
        tenantRepository.save(Tenant.builder()
                .name("Spec Probe Tenant")
                .slug("specprobe-" + stamp)
                .contactEmail("specprobe-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        final AdminDto.TenantListResponse response =
                adminService.getTenants(0, 10, Tenant.TenantStatus.ACTIVE, "SpecProbe-" + stamp);

        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("AdminService.getUsers：租戶＋角色＋狀態＋關鍵字（email/fullName）篩選可執行，且命中剛建立的使用者")
    void adminUserSearchWithAllFilters_findsSeededUser() {
        final String stamp = String.valueOf(System.nanoTime());
        final Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Spec Probe User Tenant")
                .slug("specprobe-user-" + stamp)
                .contactEmail("specprobe-user-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        userRepository.save(User.builder()
                .email("specprobe-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Spec Probe")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());

        final AdminDto.UserListResponse response =
                adminService.getUsers(0, 10, tenant.getId(), "BUYER", "ACTIVE", "SpecProbe-" + stamp);

        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("AdminService.getAuditLogs：操作類型＋時間範圍篩選可執行")
    void adminAuditLogSearchWithAllFilters_executes() {
        final AdminDto.AuditLogListResponse response = adminService.getAuditLogs(0, 10, "__spec_probe__",
                Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z"));

        assertThat(response.getTotalElements()).isZero();
    }
}
