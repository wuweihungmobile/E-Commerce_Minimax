package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaRequest;
import com.nextkey.ecommerce.api.dto.media.UploadMediaRequest;
import com.nextkey.ecommerce.core.media.MediaService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
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

    // MediaService 來自 IntegrationTestConfiguration 的 @Primary mock bean
    @Autowired
    private MediaService mediaService;

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

        // 重新配置 MediaService mock - 使用 lenient() 避免與 IntegrationTestConfiguration 的靜態配置衝突
        // 這些配置會覆蓋 IntegrationTestConfiguration 中的預設值，根據動態的 testTenantId 返回正確資料
        lenient().when(mediaService.getCategories()).thenReturn(
                mediaCategoryRepository.findByTenantIdOrderBySortOrderAsc(testTenantId)
                        .stream().map(this::toMediaCategoryDto).toList()
        );
        lenient().when(mediaService.getCategory(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return mediaCategoryRepository.findById(id).map(this::toMediaCategoryDto).orElse(null);
        });
        lenient().when(mediaService.createCategory(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, CreateMediaCategoryRequest.class);
            if (req == null) return null;
            return com.nextkey.ecommerce.api.dto.media.MediaCategoryDto.builder()
                    .id(UUID.randomUUID())
                    .name(req.getName() != null ? req.getName() : "default")
                    .description(req.getDescription())
                    .tenantId(testTenantId)
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                    .build();
        });
        lenient().when(mediaService.updateCategory(any(UUID.class), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            var req = inv.getArgument(1, UpdateMediaCategoryRequest.class);
            return mediaCategoryRepository.findById(id).map(cat -> {
                if (req != null) {
                    if (req.getName() != null) cat.setName(req.getName());
                    if (req.getDescription() != null) cat.setDescription(req.getDescription());
                }
                return toMediaCategoryDto(cat);
            }).orElse(null);
        });
        lenient().doNothing().when(mediaService).deleteCategory(any(UUID.class));

        lenient().when(mediaService.getAssets(anyInt(), anyInt(), any(), any(), any())).thenReturn(
                mediaAssetRepository.findByTenantIdAndIsDeletedFalse(testTenantId,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                        .map(this::toMediaAssetDto)
        );
        lenient().when(mediaService.getAsset(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return mediaAssetRepository.findActiveByIdAndTenantId(id, testTenantId)
                    .map(this::toMediaAssetDto).orElse(null);
        });
        lenient().when(mediaService.uploadAsset(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, UploadMediaRequest.class);
            if (req == null) return null;
            return com.nextkey.ecommerce.api.dto.media.MediaAssetDto.builder()
                    .id(UUID.randomUUID())
                    .tenantId(testTenantId)
                    .fileName(req.getFileName() != null ? req.getFileName() : "default.jpg")
                    .filePath(req.getFilePath() != null ? req.getFilePath() : "/default/path")
                    .fileSize(req.getFileSize() != null ? req.getFileSize() : 0L)
                    .mimeType(req.getMimeType() != null ? req.getMimeType() : "image/jpeg")
                    .tags(req.getTags() != null ? req.getTags() : List.of())
                    .build();
        });
        lenient().when(mediaService.updateAsset(any(UUID.class), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            var req = inv.getArgument(1, UpdateMediaRequest.class);
            return mediaAssetRepository.findActiveByIdAndTenantId(id, testTenantId).map(asset -> {
                if (req != null && req.getTags() != null) asset.setTags(req.getTags());
                return toMediaAssetDto(asset);
            }).orElse(null);
        });
        lenient().doNothing().when(mediaService).deleteAsset(any(UUID.class));
        lenient().when(mediaService.existsMediaById(anyString())).thenReturn(true);

        // Sprint 132（DEF-096）：真實上傳/檔案串流端點的 HTTP 層測試
        lenient().when(mediaService.uploadAssetMultipart(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            org.springframework.web.multipart.MultipartFile file = inv.getArgument(0);
            // Sprint 132：mock 為 class 層級單例，每個 @BeforeEach 重新 when(...) 時，Mockito 會先
            // 以既有 stub 執行一次「探測呼叫」（此時 any() 對應的實際引數皆為 null），故須防禦 null
            if (file == null) return null;
            UUID categoryId = inv.getArgument(1);
            @SuppressWarnings("unchecked")
            List<String> tags = inv.getArgument(2);
            return com.nextkey.ecommerce.api.dto.media.MediaAssetDto.builder()
                    .id(UUID.randomUUID())
                    .tenantId(testTenantId)
                    .categoryId(categoryId)
                    .fileName(file.getOriginalFilename())
                    .filePath(testTenantId + "/uuid-" + file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .tags(tags != null ? tags : List.of())
                    .build();
        });
        lenient().when(mediaService.downloadAsset(any(UUID.class))).thenAnswer(inv ->
                new MediaService.AssetFile(
                        new java.io.ByteArrayInputStream("mock-file-content".getBytes()),
                        "image/jpeg",
                        "test-image.jpg"));
    }

    private com.nextkey.ecommerce.api.dto.media.MediaCategoryDto toMediaCategoryDto(MediaCategory cat) {
        return com.nextkey.ecommerce.api.dto.media.MediaCategoryDto.builder()
                .id(cat.getId())
                .tenantId(cat.getTenantId())
                .name(cat.getName())
                .description(cat.getDescription())
                .sortOrder(cat.getSortOrder())
                .build();
    }

    private com.nextkey.ecommerce.api.dto.media.MediaAssetDto toMediaAssetDto(MediaAsset asset) {
        return com.nextkey.ecommerce.api.dto.media.MediaAssetDto.builder()
                .id(asset.getId())
                .tenantId(asset.getTenantId())
                .categoryId(asset.getCategory() != null ? asset.getCategory().getId() : null)
                .fileName(asset.getFileName())
                .filePath(asset.getFilePath())
                .fileSize(asset.getFileSize())
                .mimeType(asset.getMimeType())
                .tags(asset.getTags())
                .usageCount(asset.getUsageCount())
                .altText(asset.getAltText())
                .title(asset.getTitle())
                .build();
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
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    @Order(6)
    @DisplayName("IT-M18-006: 上傳媒體資產")
    void testUploadAsset() throws Exception {
        UploadMediaRequest request = UploadMediaRequest.builder()
                .fileName("new-upload.jpg")
                .originalName("New Upload.jpg")
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
                .categoryId(testCategory.getId())
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
    @DisplayName("IT-M18-008: 只更新 tags 不帶 categoryId 應成功（partial update，DEF-085）")
    void testUpdateAssetTagsOnly() throws Exception {
        UpdateMediaRequest request = UpdateMediaRequest.builder()
                .tags(List.of("only-tags"))
                .build();

        mockMvc.perform(put(MEDIA_URL + "/" + testAsset.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[*]", hasItems("only-tags")))
                .andExpect(jsonPath("$.data.categoryId").value(testCategory.getId().toString()));
    }

    @Test
    @Order(9)
    @DisplayName("IT-M18-009: 刪除媒體資產（軟刪除）")
    void testDeleteAsset() throws Exception {
        // 先上傳一個新資產
        UploadMediaRequest request = UploadMediaRequest.builder()
                .fileName("to-delete.jpg")
                .originalName("To Delete.jpg")  // original_name 是必填欄位
                .filePath("/media/to-delete.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .categoryId(testCategory.getId())
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
    @Order(10)
    @DisplayName("IT-M18-010: 取得單一媒體詳情")
    void testGetAssetDetail() throws Exception {
        mockMvc.perform(get(MEDIA_URL + "/" + testAsset.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testAsset.getId().toString()))
                .andExpect(jsonPath("$.data.fileName").value("test-image.jpg"));
    }

    @Test
    @Order(11)
    @DisplayName("IT-M18-011: 依關鍵字搜尋媒體")
    void testSearchMedia() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()));
    }

    @Test
    @Order(12)
    @DisplayName("IT-M18-012: 依 MIME 類型篩選媒體")
    void testFilterByMimeType() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("mimeType", "image/jpeg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].mimeType", everyItem(equalTo("image/jpeg"))));
    }

    @Test
    @Order(13)
    @DisplayName("IT-M18-013: 依分類篩選媒體")
    void testFilterByCategory() throws Exception {
        mockMvc.perform(get(MEDIA_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString())
                        .param("categoryId", testCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].categoryId", everyItem(equalTo(testCategory.getId().toString()))));
    }

    @Test
    @Order(14)
    @DisplayName("IT-M18-014: 上傳媒體（實際二進位檔案，DEF-096）")
    void testUploadAssetMultipart() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "real-upload.png", "image/png", "fake-png-bytes".getBytes());

        mockMvc.perform(multipart(MEDIA_URL + "/upload-multipart")
                        .file(file)
                        .param("categoryId", testCategory.getId().toString())
                        .param("tags", "banner", "promo")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("real-upload.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.categoryId").value(testCategory.getId().toString()))
                .andExpect(jsonPath("$.data.tags[*]", hasItems("banner", "promo")));
    }

    @Test
    @Order(15)
    @DisplayName("IT-M18-015: 取得媒體檔案內容（串流回應，DEF-096）")
    void testGetFile() throws Exception {
        mockMvc.perform(get(MEDIA_URL + "/files/" + testAsset.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-ID", testTenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("mock-file-content".getBytes()));
    }
}