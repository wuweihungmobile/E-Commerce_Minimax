package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DEF-093 回歸測試（Sprint 130）：{@code ListingController} 不得洩漏裸實體。
 *
 * <p>{@code Listing.owner} 為 {@code @ManyToOne(fetch = LAZY)} 指向 {@code User}，專案未設定
 * Jackson Hibernate 模組亦未關閉 open-in-view，若 Controller 直接回傳裸 {@code Listing} 實體，
 * 序列化會觸發懶載入並把整個 {@code User}（含 {@code passwordHash}）序列化進回應。
 *
 * 測試範圍：
 * - IT-DEF093-01: GET /v2/listings/:id 回應不含 owner/passwordHash，且保留前端依賴欄位
 * - IT-DEF093-02: GET /v2/listings（分頁清單）回應不含 owner/passwordHash
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-DEF093: ListingController 不得洩漏裸實體（密碼雜湊外洩防護）")
@WithMockUser(username = "buyer", authorities = {"product:read", "room:read"})
class ListingControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListingRepository listingRepository;

    private static final UUID LISTING_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440099");
    private static final UUID TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final String SECRET_PASSWORD_HASH = "$2a$10$SHOULD.NEVER.LEAK.INTO.RESPONSE";

    private Listing buildListingWithOwner() {
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .passwordHash(SECRET_PASSWORD_HASH)
                .fullName("Store Owner")
                .role(User.UserRole.STORE_OWNER)
                .build();

        Listing listing = Listing.builder()
                .tenantId(TENANT_ID)
                .listingType(Listing.ListingType.PRODUCT)
                .title("測試商品")
                .description("測試描述")
                .coverImageUrl("https://example.com/cover.png")
                .status(Listing.ListingStatus.ACTIVE)
                .owner(owner)
                .basePrice(BigDecimal.valueOf(500.00))
                .currency("TWD")
                .tags(List.of("new", "featured"))
                .build();
        listing.setId(LISTING_ID);
        return listing;
    }

    @Test
    @DisplayName("IT-DEF093-01: GET /v2/listings/:id 回應不含 owner/passwordHash，且保留前端依賴欄位")
    void getListing_doesNotLeakOwnerEntity() throws Exception {
        Listing listing = buildListingWithOwner();
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        mockMvc.perform(get("/v2/listings/" + LISTING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.owner").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET_PASSWORD_HASH))))
                .andExpect(jsonPath("$.data.id").value(LISTING_ID.toString()))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.data.listingType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.title").value("測試商品"))
                .andExpect(jsonPath("$.data.basePrice").value(500.00))
                .andExpect(jsonPath("$.data.currency").value("TWD"))
                .andExpect(jsonPath("$.data.tags[0]").value("new"));
    }

    @Test
    @DisplayName("IT-DEF093-02: GET /v2/listings（分頁清單）回應不含 owner/passwordHash")
    void getListings_doesNotLeakOwnerEntity() throws Exception {
        Listing listing = buildListingWithOwner();
        Page<Listing> page = new PageImpl<>(List.of(listing), PageRequest.of(0, 20), 1);
        when(listingRepository.findByStatus(any(), any())).thenReturn(page);

        mockMvc.perform(get("/v2/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].owner").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET_PASSWORD_HASH))))
                .andExpect(jsonPath("$.data.content[0].title").value("測試商品"));
    }
}
