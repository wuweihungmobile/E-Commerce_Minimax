package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.CreateListingRequest;
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.api.dto.RoomDto;
import com.nextkey.ecommerce.core.product.ProductService;
import com.nextkey.ecommerce.core.room.RoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-263 回歸測試（Sprint 189）：{@code DashboardListingController} 的類別級
 * {@code @PreAuthorize("hasAuthority('product:create') or hasAuthority('room:create')")}
 * 是 OR 條件，但實際執行動作卻依 {@code listingType} 分流到型別專屬的
 * {@code ProductService}/{@code RoomService}——先前未在各分支補上對應的專屬權限檢查，
 * 導致僅持有 {@code room:create} 的 HOST 角色可經此端點建立 PRODUCT listing（反之
 * SELLER 可建立 ROOM listing），繞過 {@code ProductController}/{@code RoomController}
 * 各自單一用途端點強制的權限模型。
 *
 * 測試範圍：
 * - IT-DEF263-01: 僅有 room:create 的使用者建立 PRODUCT listing → 403
 * - IT-DEF263-02: 僅有 product:create 的使用者建立 ROOM listing → 403
 * - IT-DEF263-03: 僅有 product:create 的使用者建立 PRODUCT listing → 200（既有合法路徑不可回歸）
 * - IT-DEF263-04: 僅有 room:create 的使用者建立 ROOM listing → 200（既有合法路徑不可回歸）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-DEF263: DashboardListingController 不得允許跨 listingType 的權限繞過")
class DashboardListingControllerAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private RoomService roomService;

    private static final String BASE_URL = "/v2/dashboard/listings";
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001";

    private CreateListingRequest buildRequest(String listingType) {
        return CreateListingRequest.builder()
                .listingType(listingType)
                .name("測試 Listing")
                .price(BigDecimal.valueOf(100))
                .build();
    }

    @Test
    @DisplayName("IT-DEF263-01: 僅有 room:create 的使用者建立 PRODUCT listing → 403")
    @WithErpSecurity(tenantId = TEST_TENANT_ID, role = "HOST", authorities = {"room:create"})
    void createListing_hostWithoutProductCreate_productType_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("PRODUCT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-DEF263-02: 僅有 product:create 的使用者建立 ROOM listing → 403")
    @WithErpSecurity(tenantId = TEST_TENANT_ID, role = "SELLER", authorities = {"product:create"})
    void createListing_sellerWithoutRoomCreate_roomType_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("ROOM"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-DEF263-03: 僅有 product:create 的使用者建立 PRODUCT listing → 200（合法路徑不可回歸）")
    @WithErpSecurity(tenantId = TEST_TENANT_ID, role = "SELLER", authorities = {"product:create"})
    void createListing_sellerWithProductCreate_productType_returns200() throws Exception {
        when(productService.createProductFromDashboard(any(CreateListingRequest.class)))
                .thenReturn(ProductDto.Response.builder().listingId(UUID.randomUUID()).build());

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("PRODUCT"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("IT-DEF263-04: 僅有 room:create 的使用者建立 ROOM listing → 200（合法路徑不可回歸）")
    @WithErpSecurity(tenantId = TEST_TENANT_ID, role = "HOST", authorities = {"room:create"})
    void createListing_hostWithRoomCreate_roomType_returns200() throws Exception {
        when(roomService.createRoomFromDashboard(any(CreateListingRequest.class)))
                .thenReturn(RoomDto.Response.builder().listingId(UUID.randomUUID()).build());

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("ROOM"))))
                .andExpect(status().isOk());
    }
}
