package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * M02 房源管理 Backend API 整合測試 (T-M02-03)
 *
 * 測試範圍：
 * - IT-M02-001: 房源上架-成功
 * - IT-M02-003: 房源編輯-更新資訊
 * - IT-M02-004: 房源下架-改為INACTIVE
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M02: M02 房源管理 Backend API 整合測試")
class M02RoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String BASE_URL = "/v2/rooms";
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    /**
     * DEF-041 根因修復（Sprint 84）後 Controller 需要真正的 {@code UserPrincipal}，
     * {@code @WithMockUser} 的預設 principal 型別不符會被注入 null，改用手動建構的 Authentication
     * （比照 {@code M07SettlementIntegrationTest.authAs}）。
     */
    private Authentication authAs() {
        UserPrincipal principal = new UserPrincipal(TEST_USER_ID, "test-user@example.com", "SELLER", TEST_TENANT_ID);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("room:create"));
        authorities.add(new SimpleGrantedAuthority("room:read"));
        authorities.add(new SimpleGrantedAuthority("room:update"));
        authorities.add(new SimpleGrantedAuthority("room:delete"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @BeforeEach
    void setUp() {
        // 🔴 清理 SecurityContext 避免影響其他測試
        SecurityContextHolder.clearContext();
        when(tenantRepository.findById(UUID.fromString(TEST_TENANT_ID)))
                .thenReturn(Optional.of(Tenant.builder().id(UUID.fromString(TEST_TENANT_ID)).build()));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(User.builder().id(TEST_USER_ID).build()));
    }

    @AfterEach
    void tearDown() {
        // 🔴 清理 SecurityContext 避免影響其他測試類
        SecurityContextHolder.clearContext();
    }

    // 測試資料工廠方法
    private RoomDto.CreateRequest buildValidCreateRequest() {
        return RoomDto.CreateRequest.builder()
                .title("Test Room")
                .description("Test Room Description")
                .location("Taipei 101")
                .latitude(25.0330)
                .longitude(121.5654)
                .basePrice(BigDecimal.valueOf(2500.00))
                .coverImageUrl("https://example.com/room.jpg")
                .tags(List.of("city view", "wifi"))
                .maxGuests(4)
                .amenities(List.of("TV", "Air Conditioning"))
                .checkInTime(LocalTime.of(15, 0))
                .checkOutTime(LocalTime.of(11, 0))
                .roomCount(1)
                .build();
    }

    private Listing buildMockListing(UUID listingId, Listing.ListingStatus status) {
        Listing listing = Listing.builder()
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingType(Listing.ListingType.ROOM)
                .title("Test Room")
                .description("Test Room Description")
                .status(status)
                .basePrice(BigDecimal.valueOf(2500.00))
                .currency("TWD")
                .build();
        listing.setId(listingId);
        return listing;
    }

    private Room buildMockRoom(Listing listing) {
        Room room = Room.builder()
                .listing(listing)
                .location("Taipei 101")
                .latitude(25.0330)
                .longitude(121.5654)
                .maxGuests(4)
                .amenities(List.of("TV", "Air Conditioning"))
                .checkInTime(LocalTime.of(15, 0))
                .checkOutTime(LocalTime.of(11, 0))
                .roomCount(1)
                .build();
        return room;
    }

    // ── IT-M02-001: 房源上架-成功 (P0) ───────────────────────────

    @Test
    @DisplayName("IT-M02-001: 房源上架-成功")
    void createRoom_success_returns201() throws Exception {
        RoomDto.CreateRequest request = buildValidCreateRequest();

        UUID savedListingId = UUID.randomUUID();
        Listing savedListing = buildMockListing(savedListingId, Listing.ListingStatus.ACTIVE);
        Room savedRoom = buildMockRoom(savedListing);

        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);
        when(listingRepository.save(any(Listing.class))).thenReturn(savedListing);
        when(roomRepository.findByListingId(any())).thenReturn(Optional.of(savedRoom));

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .with(authentication(authAs()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room created successfully"))
                .andExpect(jsonPath("$.data.listingId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("Test Room"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── IT-M02-003: 房源編輯-更新資訊 (P1) ────────────────────────

    @Test
    @DisplayName("IT-M02-003: 房源編輯-更新資訊")
    void updateRoom_success_returns200() throws Exception {
        UUID listingId = UUID.randomUUID();
        Listing existingListing = buildMockListing(listingId, Listing.ListingStatus.ACTIVE);
        Room existingRoom = buildMockRoom(existingListing);

        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder()
                .title("Updated Room Title")
                .basePrice(BigDecimal.valueOf(3000.00))
                .maxGuests(6)
                .build();

        when(roomRepository.findByListingId(listingId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(existingRoom);
        when(listingRepository.save(any(Listing.class))).thenReturn(existingListing);

        mockMvc.perform(put(BASE_URL + "/{listingId}", listingId)
                        .with(csrf())
                        .with(authentication(authAs()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room updated successfully"));
    }

    // ── IT-M02-004: 房源下架-改為INACTIVE (P1) ──────────────────

    @Test
    @DisplayName("IT-M02-004: 房源下架-改為INACTIVE")
    void deleteRoom_success_returns200() throws Exception {
        UUID listingId = UUID.randomUUID();
        Listing existingListing = buildMockListing(listingId, Listing.ListingStatus.ACTIVE);
        Room existingRoom = buildMockRoom(existingListing);

        when(roomRepository.findByListingId(listingId)).thenReturn(Optional.of(existingRoom));
        when(listingRepository.save(any(Listing.class))).thenReturn(existingListing);

        mockMvc.perform(delete(BASE_URL + "/{listingId}", listingId)
                        .with(csrf())
                        .with(authentication(authAs())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room deleted successfully"));
    }

}