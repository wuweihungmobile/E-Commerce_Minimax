package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaRequest;
import com.nextkey.ecommerce.api.dto.media.UploadMediaRequest;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.media.MediaCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.media.MediaCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M18 媒體中心 Backend API 整合測試
 *
 * 測試範圍：
 * - IT-M18-001: 取得媒體分類列表
 * - IT-M18-002: 建立媒體分類
 * - IT-M18-003: 更新媒體分類
 * - IT-M18-004: 刪除媒體分類
 * - IT-M18-005: 取得媒體列表（分頁）
 * - IT-M18-006: 上傳媒體資產
 * - IT-M18-007: 更新媒體資產
 * - IT-M18-008: 刪除媒體資產（軟刪除）
 * - IT-M18-009: 取得單一媒體詳情
 * - IT-M18-010: 依關鍵字搜尋媒體
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M18: M18 媒體中心整合測試")
class M18MediaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MediaCategoryRepository mediaCategoryRepository;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String MEDIA_URL = "/v2/media";
    private static final String CATEGORIES_URL = "/v2/media/categories";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private String authToken;
    private MediaCategory testCategory;
    private MediaAsset testAsset;

    @BeforeEach
    void setUp() throws Exception {
        // Mock FeatureToggleService
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long timestamp = System.currentTimeMillis();

        // 建立測試租戶 (每次都重新建立,因 @Transactional 會 rollback)
        Tenant testTenant = Tenant.builder()
                .name("Test Tenant for Media " + timestamp)
                .slug("test-tenant-media-" + timestamp)
                .status(TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);
        testTenantId = testTenant.getId();

        // 建立測試用戶並獲取 token
        String email = "media-test-" + timestamp + "-" + UUID.randomUUID() + "@example.com";
        authToken = createTestUserAndGetToken(email);

        // 建立測試分類
        testCategory = MediaCategory.builder()
                .tenant(tenantRepository.findById(testTenantId).orElseThrow())
                .name("Test Category " + timestamp)
                .description("Test Description")
                .sortOrder(0)
                .build();
        testCategory = mediaCategoryRepository.save(testCategory);

        // 建立測試媒體資產
        Tenant tenant = tenantRepository.findById(testTenantId).orElseThrow();
        testAsset = MediaAsset.builder()
                .tenant(tenant)
                .category(testCategory)
                .fileName("test-image.jpg")
                .originalName("test-image.jpg")
                .filePath("/media/test-image.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .fileType(MediaAsset.FileType.IMAGE)
                .tags(List.of("test", "image"))
                .usageCount(0)
                .isDeleted(false)
                .isActive(true)
                .build();
        testAsset = mediaAssetRepository.save(testAsset);
    }

    private String createTestUserAndGetToken(String email) throws Exception {
        // 建立測試用戶 (使用 SELLER 角色以取得 media:* 等權限)
        String registerJson = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "fullName": "Media Test User",
                "tenantId": "%s",
                "userType": "SELLER"
            }
            """, email, TEST_PASSWORD, testTenantId);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // 登入獲取 token
        String loginJson = String.format("""
            {
                "email": "%s",
                "password": "%s"
            }
            """, email, TEST_PASSWORD);

        var loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    // ========== Category Tests ==========

    @Test
    @Order(1)
    @DisplayName("IT-M18-001: 取得媒體分類列表")
    void testGetCategories() throws Exception {
        mockMvc.perform(get(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", notNullValue()))
                .andExpect(jsonPath("$.data[*]", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(2)
    @DisplayName("IT-M18-002: 建立媒體分類")
    void testCreateCategory() throws Exception {
        String categoryName = "New Test Category " + System.currentTimeMillis();
        CreateMediaCategoryRequest request = CreateMediaCategoryRequest.builder()
                .name(categoryName)
                .description("Test category description")
                .sortOrder(1)
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(categoryName))
                .andExpect(jsonPath("$.data.description").value("Test category description"));
    }

    @Test
    @Order(3)
    @DisplayName("IT-M18-003: 更新媒體分類")
    void testUpdateCategory() throws Exception {
        String newName = "Updated Category " + System.currentTimeMillis();
        UpdateMediaCategoryRequest request = UpdateMediaCategoryRequest.builder()
                .name(newName)
                .description("Updated description")
                .build();

        mockMvc.perform(put(CATEGORIES_URL + "/" + testCategory.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName))
                .andExpect(jsonPath("$.data.description").value("Updated description"));
    }

    @Test
    @Order(4)
    @DisplayName("IT-M18-004: 刪除空分類")
    void testDeleteEmptyCategory() throws Exception {
        // 先建立一個空分類
        MediaCategory emptyCategory = MediaCategory.builder()
                .tenant(tenantRepository.findById(testTenantId).orElseThrow())
                .name("Empty Category " + System.currentTimeMillis())
                .sortOrder(99)
                .build();
        emptyCategory = mediaCategoryRepository.save(emptyCategory);

        mockMvc.perform(delete(CATEGORIES_URL + "/" + emptyCategory.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk());
    }

    // ========== Media Asset Tests ==========

    @Test
    @Order(5)
    @DisplayName("IT-M18-005: 取得媒體列表（分頁）")
    void testGetMediaAssets() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M18-006: 上傳媒體資產")
    void testUploadAsset() throws Exception {
        UploadMediaRequest request = UploadMediaRequest.builder()
                .fileName("new-upload.jpg")
                .filePath("/media/new-upload.jpg")
                .fileSize(2048L)
                .mimeType("image/jpeg")
                .categoryId(testCategory.getId())
                .tags(List.of("upload", "test"))
                .build();

        mockMvc.perform(post(MEDIA_URL + "/upload")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("new-upload.jpg"))
                .andExpect(jsonPath("$.data.mimeType").value("image/jpeg"));
    }

    @Test
    @Order(7)
    @DisplayName("IT-M18-007: 更新媒體資產")
    void testUpdateAsset() throws Exception {
        UpdateMediaRequest request = UpdateMediaRequest.builder()
                .tags(List.of("updated", "modified"))
                .altText("Updated alt text")
                .title("Updated title")
                .build();

        mockMvc.perform(put(MEDIA_URL + "/" + testAsset.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[*]", hasItems("updated", "modified")));
    }

    @Test
    @Order(8)
    @DisplayName("IT-M18-008: 刪除媒體資產（軟刪除）")
    void testDeleteAsset() throws Exception {
        // 先上傳一個新資產
        UploadMediaRequest request = UploadMediaRequest.builder()
                .fileName("to-delete.jpg")
                .filePath("/media/to-delete.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .build();

        var uploadResult = mockMvc.perform(post(MEDIA_URL + "/upload")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String assetId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 刪除
        mockMvc.perform(delete(MEDIA_URL + "/" + assetId)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk());

        // 確認列表中不再出現
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(assetId))));
    }

    @Test
    @Order(9)
    @DisplayName("IT-M18-009: 取得單一媒體詳情")
    void testGetAssetDetail() throws Exception {
        mockMvc.perform(get(MEDIA_URL + "/" + testAsset.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testAsset.getId().toString()))
                .andExpect(jsonPath("$.data.fileName").value("test-image.jpg"));
    }

    @Test
    @Order(10)
    @DisplayName("IT-M18-010: 依關鍵字搜尋媒體")
    void testSearchMedia() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()));
    }

    @Test
    @Order(11)
    @DisplayName("IT-M18-011: 依 MIME 類型篩選媒體")
    void testFilterByMimeType() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("mimeType", "image/jpeg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mimeType", everyItem(equalTo("image/jpeg"))));
    }

    @Test
    @Order(12)
    @DisplayName("IT-M18-012: 依分類篩選媒體")
    void testFilterByCategory() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("categoryId", testCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].categoryId", everyItem(equalTo(testCategory.getId().toString()))));
    }
}