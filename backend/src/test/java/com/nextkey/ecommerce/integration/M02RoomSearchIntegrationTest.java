package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /v2/rooms 搜尋整合測試（DEF-087，Sprint 131）。
 *
 * <p>DEF-087 調查發現：keyword 搜尋分支主體用錯查詢（等同回傳未過濾的全部房源），且
 * location/maxGuests 分支完全沒有 tenantId 過濾（疑似跨租戶資料外洩）。此測試以真實
 * PostgreSQL + 真實 RoomRepository/ListingRepository（不 mock）驗證修復後的 Specification
 * 查詢：keyword 真的過濾、keyword/location 可同時 AND 套用、且所有分支都不會洩漏其他租戶的房源。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("DEF-087: GET /v2/rooms 搜尋整合測試")
class M02RoomSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private RoomRepository roomRepository;

    private static final String ROOMS_URL = "/v2/rooms";

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        long ts = System.currentTimeMillis();
        Tenant tenantA = tenantRepository.save(Tenant.builder()
                .name("Tenant A " + ts).slug("tenant-a-" + ts).status(Tenant.TenantStatus.ACTIVE).build());
        Tenant tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B " + ts).slug("tenant-b-" + ts).status(Tenant.TenantStatus.ACTIVE).build());
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        User ownerA = userRepository.save(User.builder()
                .email("owner-a-" + ts + "@example.com").role(User.UserRole.SELLER).tenantId(tenantAId).build());
        User ownerB = userRepository.save(User.builder()
                .email("owner-b-" + ts + "@example.com").role(User.UserRole.SELLER).tenantId(tenantBId).build());

        // 租戶 A：兩間房，標題/地點皆不同
        seedRoom(tenantA, ownerA, "Cozy Taipei Studio", "near MRT exit", "Taipei", 2);
        seedRoom(tenantA, ownerA, "Kaohsiung Beach House", "ocean view", "Kaohsiung", 6);

        // 租戶 B：故意用相同關鍵字/地點，驗證不會洩漏到租戶 A 的查詢結果
        seedRoom(tenantB, ownerB, "Taipei Skyline Loft", "city center", "Taipei", 4);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void seedRoom(Tenant tenant, User owner, String title, String description, String location, int maxGuests) {
        Listing listing = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.ROOM)
                .title(title)
                .description(description)
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(1000))
                .currency("TWD")
                .build());
        roomRepository.save(Room.builder()
                .listing(listing)
                .location(location)
                .maxGuests(maxGuests)
                .build());
    }

    private Authentication authAsSellerOfTenantA() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "seller-a@example.com", "SELLER", tenantAId.toString());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("room:read"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("keyword 應依 title/description 實際過濾，不再回傳未過濾的全部房源")
    void getRooms_keyword_filtersByTitleOrDescription() throws Exception {
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("keyword", "Studio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Cozy Taipei Studio")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Kaohsiung Beach House"))))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("keyword 命中他租戶同名房源時不應洩漏（跨租戶隔離）")
    void getRooms_keyword_neverLeaksOtherTenant() throws Exception {
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("keyword", "Taipei"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Cozy Taipei Studio")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Taipei Skyline Loft"))))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("keyword 與 location 應可同時套用（AND），而非互斥只認一種")
    void getRooms_keywordAndLocation_bothApplyAsAnd() throws Exception {
        // 符合 keyword 但不符合 location → 應無結果（證明 location 真的有生效，而非被忽略）
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("keyword", "Studio")
                        .param("location", "Kaohsiung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // keyword 與 location 皆符合同一筆 → 應回傳該筆
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("keyword", "Studio")
                        .param("location", "Taipei"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Cozy Taipei Studio")))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("location 篩選不應洩漏他租戶房源（DEF-087 發現的跨租戶缺口修復驗證）")
    void getRooms_location_neverLeaksOtherTenant() throws Exception {
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("location", "Taipei"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Cozy Taipei Studio")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Taipei Skyline Loft"))))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("maxGuests 篩選不應洩漏他租戶房源（DEF-087 發現的跨租戶缺口修復驗證）")
    void getRooms_maxGuests_neverLeaksOtherTenant() throws Exception {
        // 租戶 B 的 "Taipei Skyline Loft" maxGuests=4 與此條件相符，但不屬於租戶 A，不應出現
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA()))
                        .param("maxGuests", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Taipei Skyline Loft"))))
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Kaohsiung Beach House")));
    }

    @Test
    @DisplayName("不帶任何篩選條件時，只回傳當前租戶的房源")
    void getRooms_noFilters_onlyReturnsCurrentTenant() throws Exception {
        mockMvc.perform(get(ROOMS_URL)
                        .with(authentication(authAsSellerOfTenantA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Cozy Taipei Studio")))
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Kaohsiung Beach House")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Taipei Skyline Loft"))))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }
}
