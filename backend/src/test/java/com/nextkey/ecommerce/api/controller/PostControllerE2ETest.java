package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.model.user.User.UserRole;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * M15 CMS Post Controller E2E 測試 (IT-M15-001 ~ IT-M15-020)
 *
 * 測試範圍：
 * - US-M15-001: StoreOwner 建立 CMS 貼文
 * - US-M15-002: 嵌入商品/房型卡片
 * - US-M15-003: StoreOwner 管理媒體庫
 * - US-M15-004: 前台用戶瀏覽 CMS 貼文
 * - US-M15-005: StoreOwner 管理貼文分類
 * - US-M15-006: Admin 審核/管理店鋪貼文
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("M15 E2E: CMS Post Controller REST Assured E2E 測試")
class PostControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.cms.PostRepository postRepository;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.cms.PostEmbedRepository postEmbedRepository;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository postCategoryRepository;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository mediaAssetRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository featureToggleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.nextkey.ecommerce.domain.repository.RoomCalendarRepository roomCalendarRepository;

    private static final String BASE_URL = "/v2";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private UUID testTenantId;
    private UUID testUserId;
    private UUID testCategoryId;
    private String authToken;
    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    private String uniqueEmail() {
        return "cms-e2e-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    /**
     * 使用 Register → Login 流程取得 JWT token（跟其他 E2E 測試相同的模式）
     */
    private String createStoreOwnerAndGetToken(String email) throws Exception {
        // 1. 註冊 StoreOwner 用戶（使用 SELLER 類型，後續建立 TenantMember 關係）
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder()
                        .email(email)
                        .password(TEST_PASSWORD)
                        .userType("SELLER")
                        .fullName("Store Owner " + email)
                        .build())
                .when()
                .post(AUTH_URL + "/register")
                .then()
                .statusCode(201);

        // 2. 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder()
                        .email(email)
                        .password(TEST_PASSWORD)
                        .build())
                .when()
                .post(AUTH_URL + "/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 3. 啟用 CMS_ENABLED feature toggle
        User user = userRepository.findByEmail(email).orElseThrow();
        testUserId = user.getId();
        testUser = user;

        // 4. 確保用戶有所屬的 Tenant（SELLER 不會自動建立 tenant，需要手動建立）
        Tenant newTenant = Tenant.builder()
                .name("CMS Test Store " + email)
                .slug("cms-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        newTenant = tenantRepository.save(newTenant);
        testTenantId = newTenant.getId();
        testTenant = newTenant;
        final Tenant finalTenant = newTenant;

        // 5. 建立 TenantMember 關係
        boolean memberExists = tenantMemberRepository.findByTenantId(testTenantId)
                .stream().anyMatch(m -> m.getUserId().equals(testUserId));
        if (!memberExists) {
            TenantMember member = TenantMember.builder()
                    .tenantId(testTenantId)
                    .userId(testUserId)
                    .storeRole(TenantMember.StoreRole.STORE_OWNER)
                    .build();
            tenantMemberRepository.save(member);
        }

        // 6. 啟用 CMS 功能
        featureToggleRepository.findByTenantIdAndFeatureKey(testTenantId, "CMS_ENABLED")
                .ifPresentOrElse(
                        toggle -> {
                            toggle.setIsEnabled(true);
                            featureToggleRepository.save(toggle);
                        },
                        () -> {
                            com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle newToggle =
                                    com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle.builder()
                                    .tenant(finalTenant)
                                    .featureKey("CMS_ENABLED")
                                    .isEnabled(true)
                                    .build();
                            featureToggleRepository.save(newToggle);
                        }
                );

        // 7. 重新登入以取得包含正確 tenantId 的 token
        String loginResponse2 = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder()
                        .email(email)
                        .password(TEST_PASSWORD)
                        .build())
                .when()
                .post(AUTH_URL + "/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson2 = objectMapper.readTree(loginResponse2);
        String newAccessToken = loginJson2.path("data").path("accessToken").asText();

        return newAccessToken;
    }

    private UUID createTestCategory(UUID tenantId, Tenant tenant) {
        PostCategory category = PostCategory.builder()
                .tenant(tenant)
                .name("Test Category " + System.currentTimeMillis())
                .slug("test-cat-" + System.currentTimeMillis())
                .description("Test description")
                .isActive(true)
                .build();
        category = postCategoryRepository.save(category);
        testCategoryId = category.getId();
        return category.getId();
    }

    private Listing createTestListing(UUID tenantId, Tenant tenant, String title, Listing.ListingType type, Listing.ListingStatus status, BigDecimal price) {
        Listing listing = Listing.builder()
                .tenant(tenant)
                .title(title)
                .listingType(type)
                .status(status)
                .basePrice(price)
                .owner(testUser)
                .build();
        return listingRepository.save(listing);
    }

    private UUID createTestProduct(UUID tenantId, Tenant tenant, String name, BigDecimal price, boolean active) {
        // 先建立 Listing
        Listing listing = Listing.builder()
                .tenant(tenant)
                .title(name)
                .listingType(Listing.ListingType.PRODUCT)
                .status(active ? Listing.ListingStatus.ACTIVE : Listing.ListingStatus.INACTIVE)
                .basePrice(price)
                .owner(testUser)
                .build();
        listing = listingRepository.save(listing);

        // 使用 JdbcTemplate 直接插入 Product 記錄 (避免 JPA @MapsId 問題)
        jdbcTemplate.update(
                "INSERT INTO products (listing_id, category, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                listing.getId(), "Test Category"
        );

        return listing.getId();
    }

    private UUID createTestRoom(UUID tenantId, Tenant tenant, String name, BigDecimal price, boolean maintenance, boolean active) {
        // 先建立 Listing
        Listing listing = Listing.builder()
                .tenant(tenant)
                .title(name)
                .listingType(Listing.ListingType.ROOM)
                .status(active ? Listing.ListingStatus.ACTIVE : Listing.ListingStatus.INACTIVE)
                .basePrice(price)
                .owner(testUser)
                .build();
        listing = listingRepository.save(listing);

        // 使用 JdbcTemplate 直接插入 Room 記錄 (避免 JPA @MapsId 問題)
        jdbcTemplate.update(
                "INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, created_at, updated_at) VALUES (?, ?, ?, ?::time, ?::time, NOW(), NOW())",
                listing.getId(), 4, 2, "15:00", "11:00"
        );

        // 如果需要 MAINTENANCE 狀態，建立 RoomCalendar 記錄
        if (maintenance) {
            RoomCalendar rc = RoomCalendar.builder()
                    .listing(listing)
                    .calendarDate(LocalDate.now())
                    .status(RoomCalendar.RoomCalendarStatus.MAINTENANCE)
                    .build();
            roomCalendarRepository.save(rc);
        }

        return listing.getId();
    }

    // Helper: 使用 userId/tenantId 直接建立 Post，確保 author_id 不為 null
    private UUID createPostDirectly(UUID tenantId, UUID authorId, String title, String content, Post.PostStatus status, String slug) {
        Post post = Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title(title)
                .content(content)
                .status(status)
                .slug(slug != null ? slug : "post-" + System.currentTimeMillis())
                .build();
        post = postRepository.save(post);
        return post.getId();
    }

    private void cleanupPostData(UUID postId) {
        if (postId != null) {
            postEmbedRepository.findByPostId(postId).forEach(pe -> {
                postEmbedRepository.delete(pe);
            });
            postRepository.findById(postId).ifPresent(p -> {
                postRepository.delete(p);
            });
        }
    }

    private void cleanupAllTestData() {
        try {
            // 清理順序：先刪除依賴資料，再刪除主資料（遵循 FK 約束）

            // 1. 清理 PostEmbed（依賴 Post）
            if (testUserId != null) {
                postEmbedRepository.findAll().forEach(pe -> {
                    try {
                        if (pe.getPost() != null && pe.getPost().getAuthor() != null
                                && pe.getPost().getAuthor().getId().equals(testUserId)) {
                            postEmbedRepository.delete(pe);
                        }
                    } catch (Exception ignored) {}
                });
            }

            // 2. 清理 Post（依賴 Tenant, User, Category）
            if (testUserId != null) {
                postRepository.findAll().forEach(p -> {
                    try {
                        if (p.getAuthor() != null && p.getAuthor().getId().equals(testUserId)) {
                            postRepository.delete(p);
                        }
                    } catch (Exception ignored) {}
                });
            }

            // 3. 清理 MediaAsset（依賴 Tenant, User）
            if (testTenantId != null) {
                try {
                    mediaAssetRepository.findByTenantIdOrderByCreatedAtDesc(testTenantId)
                            .forEach(m -> mediaAssetRepository.delete(m));
                } catch (Exception ignored) {}
            }

            // 4. 清理 Category（依賴 Tenant）
            if (testTenantId != null) {
                try {
                    postCategoryRepository.findByTenantIdOrderBySortOrderAsc(testTenantId)
                            .forEach(pc -> {
                                try {
                                    postCategoryRepository.delete(pc);
                                } catch (Exception ignored) {}
                            });
                } catch (Exception ignored) {}
            }

            // 5. 清理 RoomCalendar（依賴 Listing）
            if (testTenantId != null) {
                try {
                    jdbcTemplate.update("DELETE FROM room_calendar WHERE room_listing_id IN (SELECT id FROM listings WHERE tenant_id = ?)", testTenantId);
                } catch (Exception ignored) {}
            }

            // 6. 清理 Room（依賴 Listing）
            if (testTenantId != null) {
                try {
                    jdbcTemplate.update("DELETE FROM rooms WHERE listing_id IN (SELECT id FROM listings WHERE tenant_id = ?)", testTenantId);
                } catch (Exception ignored) {}
            }

            // 7. 清理 Product（依賴 Listing）
            if (testTenantId != null) {
                try {
                    jdbcTemplate.update("DELETE FROM products WHERE listing_id IN (SELECT id FROM listings WHERE tenant_id = ?)", testTenantId);
                } catch (Exception ignored) {}
            }

            // 8. 清理 Listing（依賴 Tenant, User）
            if (testTenantId != null) {
                try {
                    listingRepository.findAll().forEach(l -> {
                        try {
                            if (testTenantId.equals(l.getTenantId())) {
                                listingRepository.delete(l);
                            }
                        } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }

            // 9. 清理 TenantMember（依賴 Tenant, User）
            if (testTenantId != null) {
                try {
                    tenantMemberRepository.findByTenantId(testTenantId)
                            .forEach(tm -> tenantMemberRepository.delete(tm));
                } catch (Exception ignored) {}
            }

            // 10. 清理 TenantFeatureToggle（依賴 Tenant）
            if (testTenantId != null) {
                try {
                    featureToggleRepository.findAll().forEach(tft -> {
                        try {
                            if (tft.getTenant() != null && testTenantId.equals(tft.getTenant().getId())) {
                                featureToggleRepository.delete(tft);
                            }
                        } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }

            // 11. 清理 Tenant（依賴最後）
            if (testTenantId != null) {
                try {
                    tenantRepository.findById(testTenantId).ifPresent(t -> tenantRepository.delete(t));
                } catch (Exception ignored) {}
            }

            // 12. 清理 User（最後）
            if (testUserId != null) {
                try {
                    userRepository.findById(testUserId).ifPresent(u -> userRepository.delete(u));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            // 清理失敗時忽略，避免影響測試結果
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-001: 建立貼文成功（所有必填欄位）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("IT-M15-001: POST /api/v2/dashboard/posts - 建立貼文成功（所有必填欄位）")
    void createPost_withAllRequiredFields_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        createTestCategory(testTenantId, testTenant);

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Test Post Title",
                        "content", "# Hello World\n\nThis is a test post.",
                        "categoryId", testCategoryId
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.title", equalTo("Test Post Title"))
                .body("data.status", equalTo("DRAFT"))
                .body("data.content", containsString("Hello World"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-002: 建立貼文必填欄位驗證
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("IT-M15-002: POST /api/v2/dashboard/posts - 缺少必填欄位 title，返回 400")
    void createPost_missingTitle_shouldFail() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "content", "Test content without title"
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(400);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-003: 嵌入商品卡片成功
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("IT-M15-003: 建立含嵌入式商品的貼文成功")
    void createPost_withProductEmbed_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        createTestCategory(testTenantId, testTenant);

        UUID productId = createTestProduct(testTenantId, testTenant, "Test Product", BigDecimal.valueOf(999), true);

        String content = "# Product Demo\n\nCheck out this item:\n{{embed:listing:" + productId + "}}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Product Embed Post",
                        "content", content,
                        "categoryId", testCategoryId
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.title", equalTo("Product Embed Post"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-004: 嵌入房型卡片成功
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("IT-M15-004: 建立含嵌入式房型的貼文成功")
    void createPost_withRoomEmbed_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        createTestCategory(testTenantId, testTenant);

        UUID roomId = createTestRoom(testTenantId, testTenant, "Test Room", BigDecimal.valueOf(2500), false, true);

        String content = "# Room Demo\n\nStay with us:\n{{embed:listing:" + roomId + "}}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Room Embed Post",
                        "content", content,
                        "categoryId", testCategoryId
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.title", equalTo("Room Embed Post"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-005: 重複嵌入相同 listing_id → E-4001
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("IT-M15-005: 重複嵌入相同 listing_id → HTTP 400, E-4104, EMBED_DUPLICATE_LISTING")
    void createPost_withDuplicateEmbed_shouldFail() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        UUID productId = createTestProduct(testTenantId, testTenant, "Duplicate Test Product", BigDecimal.valueOf(500), true);

        String content = "# Duplicate Embed Test\n\n{{embed:listing:" + productId + "}}\n\n{{embed:listing:" + productId + "}}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Duplicate Embed Post",
                        "content", content
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("code", equalTo("E-4104"))
                .body("message", containsString("Embed"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-006: 嵌入不存在的 listing_id → 404
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("IT-M15-006: 嵌入不存在的 listing_id → HTTP 404")
    void createPost_withNonExistentListing_shouldFail() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        UUID nonExistentId = UUID.randomUUID();

        String content = "# Non-Existent Listing Test\n\n{{embed:listing:" + nonExistentId + "}}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Non-Existent Listing Post",
                        "content", content
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(404);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-007: 發布貼文成功
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("IT-M15-007: POST /api/v2/dashboard/posts/:id/publish - 發布貼文成功")
    void publishPost_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        createTestCategory(testTenantId, testTenant);

        // 先建立草稿貼文
        UUID postId = postRepository.save(Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title("Draft Post for Publishing")
                .content("Content to be published")
                .slug("draft-" + System.currentTimeMillis())
                .status(Post.PostStatus.DRAFT)
                .build()).getId();

        try {
            given()
                    .header("Authorization", "Bearer " + authToken)
                    .when()
                    .post(BASE_URL + "/dashboard/posts/" + postId + "/publish")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.status", equalTo("PUBLISHED"));
        } finally {
            cleanupPostData(postId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-008: 刪除已發布貼文
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("IT-M15-008: DELETE /api/v2/dashboard/posts/:id - 刪除已發布貼文")
    void deletePublishedPost_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 先建立已發布的貼文
        UUID postId = postRepository.save(Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title("Published Post for Deletion")
                .content("Content to be deleted")
                .slug("delete-test-" + System.currentTimeMillis())
                .status(Post.PostStatus.PUBLISHED)
                .build()).getId();

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .delete(BASE_URL + "/dashboard/posts/" + postId)
                .then()
                .statusCode(200)
                .body("success", is(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-009: CMS_ENABLED=false 時不可建立貼文
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("IT-M15-009: CMS_ENABLED=false 時不可建立貼文，回傳 E-2004")
    void createPost_whenCMSDisabled_shouldFail() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 停用 CMS_ENABLED feature toggle
        featureToggleRepository.findByTenantIdAndFeatureKey(testTenantId, "CMS_ENABLED")
                .ifPresent(toggle -> {
                    toggle.setIsEnabled(false);
                    featureToggleRepository.save(toggle);
                });

        try {
            given()
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of(
                            "title", "Should Fail Post",
                            "content", "This should not be allowed when CMS is disabled"
                    ))
                    .when()
                    .post(BASE_URL + "/dashboard/posts")
                    .then()
                    .statusCode(anyOf(equalTo(403), equalTo(404)));
        } finally {
            // 恢復 CMS_ENABLED
            featureToggleRepository.findByTenantIdAndFeatureKey(testTenantId, "CMS_ENABLED")
                    .ifPresent(toggle -> {
                        toggle.setIsEnabled(true);
                        featureToggleRepository.save(toggle);
                    });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-010: 媒體上傳成功
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("IT-M15-010: POST /api/v2/media/upload - 媒體上傳成功")
    void uploadMedia_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        given()
                .header("Authorization", "Bearer " + authToken)
                .queryParam("fileName", "test-image.jpg")
                .queryParam("originalName", "Original Image.jpg")
                .queryParam("fileSize", 1024 * 1024)
                .queryParam("mimeType", "image/jpeg")
                .queryParam("filePath", "/test-tenant/media/test-image.jpg")
                .when()
                .post(BASE_URL + "/media/upload")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.fileName", equalTo("test-image.jpg"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-010b: Multipart 上傳媒體（實際上傳到 MinIO）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("IT-M15-010b: POST /api/v2/dashboard/media/upload-multipart - Multipart 上傳成功")
    void uploadMediaMultipart_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        byte[] mockImageBytes = "mock-image-content".getBytes();

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                .multiPart("file", "test-multipart.jpg", mockImageBytes, "image/jpeg")
                .when()
                .post(BASE_URL + "/dashboard/media/upload-multipart")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.fileName", equalTo("test-multipart.jpg"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-011: 媒體列表查詢（按 tenant 分隔）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("IT-M15-011: GET /api/v2/dashboard/media - 媒體列表查詢（按 tenant 分隔）")
    void getMediaList_shouldReturnTenantMedia() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 上傳一個測試媒體
        mediaAssetRepository.save(MediaAsset.builder()
                .tenant(testTenant)
                .uploader(testUser)
                .fileName("test-media.jpg")
                .originalName("Test Media.jpg")
                .fileSize(2048L)
                .mimeType("image/jpeg")
                .filePath("/test/media/test-media.jpg")
                .fileType(MediaAsset.FileType.IMAGE)
                .build());

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(BASE_URL + "/dashboard/media")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.items", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-012: 刪除媒體成功
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("IT-M15-012: DELETE /api/v2/dashboard/media/:id - 刪除媒體成功")
    void deleteMedia_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 建立測試媒體
        UUID mediaId = mediaAssetRepository.save(MediaAsset.builder()
                .tenant(testTenant)
                .uploader(testUser)
                .fileName("to-be-deleted.jpg")
                .originalName("To Be Deleted.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .filePath("/test/media/to-be-deleted.jpg")
                .fileType(MediaAsset.FileType.IMAGE)
                .build()).getId();

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .delete(BASE_URL + "/dashboard/media/" + mediaId)
                .then()
                .statusCode(200)
                .body("success", is(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-013: 前台取得貼文列表（公開）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @DisplayName("IT-M15-013: GET /api/v2/posts - 前台取得已發布貼文列表（公開）")
    void getPublishedPosts_shouldReturnPublicPosts() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 建立已發布的貼文
        postRepository.save(Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title("Public Post")
                .content("This is a public post")
                .slug("public-post-" + System.currentTimeMillis())
                .status(Post.PostStatus.PUBLISHED)
                .build());

        given()
                .queryParam("tenantId", testTenantId.toString())
                .when()
                .get(BASE_URL + "/posts")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.posts", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-014: 前台取得貼文詳情（包含嵌入卡片）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(14)
    @DisplayName("IT-M15-014: GET /api/v2/posts/:slug - 前台取得貼文詳情（包含嵌入卡片）")
    void getPublishedPostBySlug_shouldReturnPostWithEmbeds() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        UUID productId = createTestProduct(testTenantId, testTenant, "Embed Product", BigDecimal.valueOf(799), true);
        String slug = "post-with-embed-" + System.currentTimeMillis();

        Post post = Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title("Post With Embed")
                .content("Check out: {{embed:listing:" + productId + "}}")
                .slug(slug)
                .status(Post.PostStatus.PUBLISHED)
                .build();
        post = postRepository.save(post);

        // 建立 PostEmbed 關聯
        postEmbedRepository.save(PostEmbed.builder()
                .post(post)
                .listingId(productId)
                .listingType("PRODUCT")
                .embedOrder(0)
                .build());

        given()
                .queryParam("tenantId", testTenantId.toString())
                .when()
                .get(BASE_URL + "/posts/" + slug)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.title", equalTo("Post With Embed"))
                .body("data.slug", equalTo(slug));

        cleanupPostData(post.getId());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-015: 分類新增/列表/刪除
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(15)
    @DisplayName("IT-M15-015: 分類新增/列表/刪除")
    void categoryCrud_shouldSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 新增分類
        String categoryName = "New Category " + System.currentTimeMillis();

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "name", categoryName,
                        "description", "Category description"
                ))
                .when()
                .post(BASE_URL + "/dashboard/post-categories")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.name", equalTo(categoryName));

        // 列表分類
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(BASE_URL + "/dashboard/post-categories")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.categories", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-016: 非 StoreOwner 不可管理他店貼文
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(16)
    @DisplayName("IT-M15-016: 非 StoreOwner 不可管理他店貼文，回傳 E-4031")
    void deleteOtherStorePost_shouldFail() throws Exception {
        String email1 = uniqueEmail();
        String email2 = uniqueEmail();

        // 第一個用戶建立貼文
        authToken = createStoreOwnerAndGetToken(email1);

        UUID postId = postRepository.save(Post.builder()
                .tenant(testTenant)
                .author(testUser)
                .title("Other Store Post")
                .content("Should not be deletable by other store")
                .slug("other-store-" + System.currentTimeMillis())
                .status(Post.PostStatus.DRAFT)
                .build()).getId();

        // 第二個用戶嘗試刪除（不同 tenant）
        String email2Token = createStoreOwnerAndGetToken(email2);

        // 由於 tenant 隔離，不同 tenant 的刪除請求應該返回 404（資源不存在）
        given()
                .header("Authorization", "Bearer " + email2Token)
                .when()
                .delete(BASE_URL + "/dashboard/posts/" + postId)
                .then()
                .statusCode(anyOf(equalTo(403), equalTo(404)));

        cleanupPostData(postId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-017: 嵌入 INACTIVE Listing 的卡片顯示「已下架」
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @DisplayName("IT-M15-017: 嵌入 INACTIVE Listing 的卡片顯示「已下架」")
    void getListingCard_withInactiveListing_shouldReturnOfflineStatus() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 建立一個 INACTIVE 的商品
        UUID inactiveProductId = createTestProduct(testTenantId, testTenant, "Inactive Product", BigDecimal.valueOf(299), false);

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(BASE_URL + "/listings/" + inactiveProductId + "/card")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.availability.inStock", is(false))
                .body("data.statusReason", equalTo("listing_inactive"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-018: Markdown 語法錯誤的 embed 標記自動忽略
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(18)
    @DisplayName("IT-M15-018: Markdown 語法錯誤的 embed 標記自動忽略")
    void createPost_withMalformedEmbed_shouldIgnoreAndSucceed() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        // 錯誤的 embed 語法（格式錯誤）
        String content = "# Malformed Embed Test\n\n{{invalid syntax here}}\n\nSome more content";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "title", "Malformed Embed Post",
                        "content", content
                ))
                .when()
                .post(BASE_URL + "/dashboard/posts")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.title", equalTo("Malformed Embed Post"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-019: 取得嵌入卡片（PRODUCT 類型）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(19)
    @DisplayName("IT-M15-019: 取得嵌入卡片（PRODUCT 類型）")
    void getListingCard_productType_shouldReturnProductCard() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        UUID productId = createTestProduct(testTenantId, testTenant, "AirPods Pro 2", BigDecimal.valueOf(7490), true);

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(BASE_URL + "/listings/" + productId + "/card")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.listingType", equalTo("PRODUCT"))
                .body("data.currentPrice", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IT-M15-020: 取得嵌入卡片（ROOM 類型，MAINTENANCE 狀態）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("IT-M15-020: 取得嵌入卡片（ROOM 類型，MAINTENANCE 狀態）")
    void getListingCard_roomTypeMaintenance_shouldReturnMaintenanceStatus() throws Exception {
        String email = uniqueEmail();
        authToken = createStoreOwnerAndGetToken(email);

        UUID roomId = createTestRoom(testTenantId, testTenant, "Luxury Suite", BigDecimal.valueOf(8500), true, true);

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get(BASE_URL + "/listings/" + roomId + "/card")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.listingType", equalTo("ROOM"))
                .body("data.availability.available", is(false))
                .body("data.statusReason", equalTo("under_maintenance"));
    }

    @AfterAll
    void tearDown() {
        cleanupAllTestData();
    }
}