package com.nextkey.ecommerce.core.cms.post;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostEmbedRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostService 單元測試
 *
 * 測試範圍：
 * - createPost() - 建立貼文（含嵌入解析）
 * - updatePost() - 更新貼文
 * - deletePost() - 刪除貼文（僅 DRAFT 可刪除）
 * - publishPost() - 發布貼文
 * - unpublishPost() - 下架貼文
 * - getPost() - 取得貼文
 * - getPosts() - 取得貼文列表
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService: 貼文管理")
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostEmbedRepository postEmbedRepository;

    @Mock
    private PostCategoryRepository postCategoryRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeatureToggleService featureToggleService;

    @InjectMocks
    private PostService postService;

    // 測試資料
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_AUTHOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_POST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID TEST_CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
    private static final UUID TEST_LISTING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440005");

    private Tenant buildTenant() {
        return Tenant.builder()
                .id(TEST_TENANT_ID)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
    }

    private User buildAuthor() {
        return User.builder()
                .id(TEST_AUTHOR_ID)
                .email("author@test.com")
                .fullName("Test Author")
                .role(User.UserRole.STORE_OWNER)
                .tenantId(TEST_TENANT_ID)
                .build();
    }

    private Post buildPost(Post.PostStatus status) {
        Post post = Post.builder()
                .id(TEST_POST_ID)
                .tenant(buildTenant())
                .author(buildAuthor())
                .title("Test Post")
                .slug("test-post")
                .content("Test content")
                .status(status)
                .tags(new ArrayList<>())
                .viewCount(0)
                .embeds(new ArrayList<>())
                .build();
        return post;
    }

    private PostCategory buildCategory() {
        return PostCategory.builder()
                .id(TEST_CATEGORY_ID)
                .tenant(buildTenant())
                .name("Test Category")
                .slug("test-category")
                .isActive(true)
                .build();
    }

    private Listing buildActiveListing() {
        return Listing.builder()
                .id(TEST_LISTING_ID)
                .tenant(buildTenant())
                .owner(buildAuthor())
                .title("Test Listing")
                .listingType(Listing.ListingType.PRODUCT)
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(100))
                .build();
    }

    // ── createPost() Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("createPost()")
    class CreatePost {

        @Test
        @DisplayName("createPost_success")
        void createPost_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Post savedPost = buildPost(Post.PostStatus.DRAFT);
            savedPost.setCategory(null);

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Test Post");
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("createPost_withCategory_success")
        void createPost_withCategory_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            PostCategory category = buildCategory();
            Post savedPost = buildPost(Post.PostStatus.DRAFT);
            savedPost.setCategory(category);

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .categoryId(TEST_CATEGORY_ID)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postCategoryRepository.findByIdAndTenantId(TEST_CATEGORY_ID, TEST_TENANT_ID)).thenReturn(Optional.of(category));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategoryId()).isEqualTo(TEST_CATEGORY_ID);
            verify(postCategoryRepository).findByIdAndTenantId(TEST_CATEGORY_ID, TEST_TENANT_ID);
        }

        @Test
        @DisplayName("DEF-240：createPost 帶入他租戶的 categoryId 時應拒絕（找不到分類），不得把貼文掛到他租戶分類下")
        void createPost_categoryBelongsToDifferentTenant_throwsException() {
            Tenant tenant = buildTenant();
            User author = buildAuthor();

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Cross Tenant Post")
                    .content("Test content")
                    .categoryId(TEST_CATEGORY_ID)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            // 修復前：postCategoryRepository.findById(TEST_CATEGORY_ID) 不分租戶，他租戶的分類一樣查得到。
            // 修復後：findByIdAndTenantId 在分類屬於他租戶時回傳空。
            when(postCategoryRepository.findByIdAndTenantId(TEST_CATEGORY_ID, TEST_TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4102);
        }

        @Test
        @DisplayName("createPost_withEmbed_success")
        void createPost_withEmbed_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Listing listing = buildActiveListing();
            Post savedPost = buildPost(Post.PostStatus.DRAFT);
            savedPost.setContent("Check out {{embed:listing:" + TEST_LISTING_ID + "}}");

            PostEmbed embed = PostEmbed.builder()
                    .id(UUID.randomUUID())
                    .post(savedPost)
                    .listingId(TEST_LISTING_ID)
                    .listingType("PRODUCT")
                    .embedOrder(0)
                    .build();

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Check out {{embed:listing:" + TEST_LISTING_ID + "}}")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
                Post p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(TEST_POST_ID);
                }
                return p;
            });
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(List.of(embed));
            // No need to mock deleteByPostId as it returns void
            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(postEmbedRepository.save(any(PostEmbed.class))).thenReturn(embed);

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getEmbeds()).hasSize(1);
            verify(listingRepository).findById(TEST_LISTING_ID);
        }

        @Test
        @DisplayName("createPost_withAutoPublish_success")
        void createPost_withAutoPublish_success() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Post savedPost = buildPost(Post.PostStatus.PUBLISHED);

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .autoPublish(true)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        }

        @Test
        @DisplayName("Sprint 147: createPost 帶 autoPublish 但 MAX_POSTS 已達上限 → 拋 BusinessException，不再多存一次")
        void createPost_autoPublishOverQuota_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Post draftPost = buildPost(Post.PostStatus.DRAFT);

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .autoPublish(true)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(draftPost);
            when(postRepository.countByTenantIdAndStatus(TEST_TENANT_ID, Post.PostStatus.PUBLISHED))
                    .thenReturn((long) AppConstants.QUOTA_MAX_POSTS);
            doThrow(new BusinessException(ErrorCode.E_2009))
                    .when(featureToggleService)
                    .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_POSTS, (long) AppConstants.QUOTA_MAX_POSTS);

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class);
            // 只有一開始建立 DRAFT 的那次 save，配額檢查擋下後不會再多存一次「已發布」版本
            verify(postRepository, times(1)).save(any(Post.class));
        }

        @Test
        @DisplayName("createPost_tenantNotFound_throwsException")
        void createPost_tenantNotFound_throwsException() {
            // Arrange
            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }

        @Test
        @DisplayName("createPost_authorNotFound_throwsException")
        void createPost_authorNotFound_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Test content")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1006);
                    });
        }

        @Test
        @DisplayName("createPost_duplicateEmbed_throwsE4104")
        void createPost_duplicateEmbed_throwsE4104() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Listing listing = buildActiveListing();

            // Content with duplicate listing IDs
            String contentWithDuplicate = "First {{embed:listing:" + TEST_LISTING_ID + "}} and second {{embed:listing:" + TEST_LISTING_ID + "}}";

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content(contentWithDuplicate)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
                Post p = invocation.getArgument(0);
                if (p.getId() == null) {
                    // For tests, assign a UUID if null
                    p.setId(TEST_POST_ID);
                }
                return p;
            });
            // No need to mock deleteByPostId as it returns void
            when(listingRepository.findById(TEST_LISTING_ID)).thenReturn(Optional.of(listing));
            when(postEmbedRepository.save(any(PostEmbed.class))).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4104);
                    });
        }

        @Test
        @DisplayName("createPost_embedListingNotFound_throwsE4105")
        void createPost_embedListingNotFound_throwsE4105() {
            // Arrange
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            UUID nonExistentListingId = UUID.randomUUID();

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("Check out {{embed:listing:" + nonExistentListingId + "}}")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
                Post p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(TEST_POST_ID);
                }
                return p;
            });
            // No need to mock deleteByPostId as it returns void
            when(listingRepository.findById(nonExistentListingId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4105);
                    });
        }

        // ── DEF-105（Sprint 154）：title/content 危險標記偵測 ───────────
        //
        // 改採「偵測即拒絕」而非消毒改寫：因為前台目前把 content 當純文字顯示
        // （見 frontend/src/app/blog/[slug]/page.tsx renderContentWithEmbeds），
        // 消毒函式庫的 HTML 實體編碼副作用（&→&amp; 等）會讓正常內容顯示成亂碼。

        @Test
        @DisplayName("DEF-105: createPost_titleWithScriptTag_throwsE9009")
        void createPost_titleWithScriptTag_throwsE9009() {
            // Arrange
            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Hello <script>alert(1)</script>")
                    .content("Test content")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9009);
                    });
            // 驗證是在碰任何 repository 前就短路拒絕，不會意外建立 tenant/author 查詢副作用
            verifyNoInteractions(tenantRepository, postRepository);
        }

        @Test
        @DisplayName("DEF-105: createPost_contentWithEventHandlerAttribute_throwsE9009")
        void createPost_contentWithEventHandlerAttribute_throwsE9009() {
            // Arrange
            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("<img src=x onerror=alert(document.cookie)>")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9009);
                    });
        }

        @Test
        @DisplayName("DEF-105: createPost_contentWithJavascriptUriMarkdownLink_throwsE9009")
        void createPost_contentWithJavascriptUriMarkdownLink_throwsE9009() {
            // Arrange: Markdown 連結語法 [text](javascript:...) 不含 HTML 角括號標籤，
            // 但若未來加上 markdown-to-HTML 渲染會變成可執行的 <a href="javascript:...">，
            // 故此檢查刻意不侷限於 <script> 等標籤，同時比對危險 URI scheme
            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Test Post")
                    .content("[點我看優惠](javascript:alert(document.cookie))")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9009);
                    });
        }

        @Test
        @DisplayName("DEF-105: createPost_plainProseWithAmpersandAndAngleBrackets_notFalselyRejected")
        void createPost_plainProseWithAmpersandAndAngleBrackets_notFalselyRejected() {
            // Arrange：反例守衛——確保防禦性檢查不會誤傷含常見符號的合法內容。
            // 這正是本輪選擇「偵測即拒絕」而非「消毒改寫」的理由：後者會靜默把
            // & 改寫成 &amp;，讓「Q&A」這類再平常不過的標題永久顯示成亂碼。
            Tenant tenant = buildTenant();
            User author = buildAuthor();
            Post savedPost = buildPost(Post.PostStatus.DRAFT);

            M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                    .title("Q&A: 常見問題")
                    .content("溫度必須 < 100 度，濕度 > 50%，服務由 Tom & Jerry 提供")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findById(TEST_AUTHOR_ID)).thenReturn(Optional.of(author));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            verify(postRepository).save(any(Post.class));
        }
    }

    // ── updatePost() Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("updatePost()")
    class UpdatePost {

        @Test
        @DisplayName("updatePost_success")
        void updatePost_success() {
            // Arrange
            Post existingPost = buildPost(Post.PostStatus.DRAFT);
            M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                    .title("Updated Title")
                    .content("Updated content")
                    .build();

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(existingPost));
            when(postRepository.save(any(Post.class))).thenReturn(existingPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());
            when(postRepository.existsBySlugAndIdNot(anyString(), any(UUID.class))).thenReturn(false);

            // Act
            M15Dto.PostResponse response = postService.updatePost(TEST_POST_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("updatePost_withCategory_success")
        void updatePost_withCategory_success() {
            // Arrange
            Post existingPost = buildPost(Post.PostStatus.DRAFT);
            PostCategory category = buildCategory();
            M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                    .categoryId(TEST_CATEGORY_ID)
                    .build();

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(existingPost));
            when(postCategoryRepository.findByIdAndTenantId(TEST_CATEGORY_ID, TEST_TENANT_ID)).thenReturn(Optional.of(category));
            when(postRepository.save(any(Post.class))).thenReturn(existingPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.updatePost(TEST_POST_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            verify(postCategoryRepository).findByIdAndTenantId(TEST_CATEGORY_ID, TEST_TENANT_ID);
        }

        @Test
        @DisplayName("updatePost_notFound_throwsException")
        void updatePost_notFound_throwsException() {
            // Arrange
            M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                    .title("Updated Title")
                    .build();

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.updatePost(TEST_POST_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4100);
                    });
        }

        @Test
        @DisplayName("updatePost_wrongTenant_throwsException")
        void updatePost_wrongTenant_throwsException() {
            // Arrange
            Post existingPost = buildPost(Post.PostStatus.DRAFT);
            UUID wrongTenantId = UUID.randomUUID();

            M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                    .title("Updated Title")
                    .build();

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(existingPost));

            // Act & Assert
            assertThatThrownBy(() -> postService.updatePost(TEST_POST_ID, wrongTenantId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }

        @Test
        @DisplayName("DEF-105: updatePost_contentWithScriptTag_throwsE9009AndDoesNotSave")
        void updatePost_contentWithScriptTag_throwsE9009AndDoesNotSave() {
            // Arrange
            Post existingPost = buildPost(Post.PostStatus.DRAFT);
            M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                    .content("<iframe src=\"https://evil.example.com\"></iframe>")
                    .build();

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(existingPost));

            // Act & Assert
            assertThatThrownBy(() -> postService.updatePost(TEST_POST_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_9009);
                    });
            // 驗證拒絕發生在改任何欄位之前，既有內容不會被部分覆寫
            verify(postRepository, never()).save(any(Post.class));
            assertThat(existingPost.getContent()).isEqualTo("Test content");
        }
    }

    // ── publishPost() Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("publishPost()")
    class PublishPost {

        @Test
        @DisplayName("publishPost_success")
        void publishPost_success() {
            // Arrange
            Post draftPost = buildPost(Post.PostStatus.DRAFT);
            Post publishedPost = buildPost(Post.PostStatus.PUBLISHED);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(draftPost));
            when(postRepository.save(any(Post.class))).thenReturn(publishedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.publishPost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("PUBLISHED");
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("publishPost_alreadyPublished_throwsException")
        void publishPost_alreadyPublished_throwsException() {
            // Arrange
            Post publishedPost = buildPost(Post.PostStatus.PUBLISHED);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(publishedPost));

            // Act & Assert
            assertThatThrownBy(() -> postService.publishPost(TEST_POST_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4106);
                        assertThat(bex.getMessage()).contains("already published");
                    });
        }

        @Test
        @DisplayName("Sprint 147: publishPost 已達 MAX_POSTS 配額 → 拋 BusinessException，不寫入")
        void publishPost_quotaExceeded_throwsAndDoesNotSave() {
            // Arrange
            Post draftPost = buildPost(Post.PostStatus.DRAFT);
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(draftPost));
            when(postRepository.countByTenantIdAndStatus(TEST_TENANT_ID, Post.PostStatus.PUBLISHED))
                    .thenReturn((long) AppConstants.QUOTA_MAX_POSTS);
            doThrow(new BusinessException(ErrorCode.E_2009))
                    .when(featureToggleService)
                    .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_POSTS, (long) AppConstants.QUOTA_MAX_POSTS);

            // Act & Assert
            assertThatThrownBy(() -> postService.publishPost(TEST_POST_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class);
            verify(postRepository, never()).save(any(Post.class));
            assertThat(draftPost.getStatus()).isEqualTo(Post.PostStatus.DRAFT);
        }

        @Test
        @DisplayName("Sprint 147: publishPost 配額未達上限 → 正常發布，且以正確數量呼叫配額檢查")
        void publishPost_underQuota_checksQuotaAndPublishes() {
            // Arrange
            Post draftPost = buildPost(Post.PostStatus.DRAFT);
            Post publishedPost = buildPost(Post.PostStatus.PUBLISHED);
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(draftPost));
            when(postRepository.countByTenantIdAndStatus(TEST_TENANT_ID, Post.PostStatus.PUBLISHED)).thenReturn(5L);
            when(postRepository.save(any(Post.class))).thenReturn(publishedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            postService.publishPost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            verify(featureToggleService).checkQuotaNotExceeded(AppConstants.QUOTA_MAX_POSTS, 5L);
        }
    }

    // ── unpublishPost() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("unpublishPost()")
    class UnpublishPost {

        @Test
        @DisplayName("unpublishPost_success")
        void unpublishPost_success() {
            // Arrange
            Post publishedPost = buildPost(Post.PostStatus.PUBLISHED);
            Post unpublishedPost = buildPost(Post.PostStatus.DRAFT);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(publishedPost));
            when(postRepository.save(any(Post.class))).thenReturn(unpublishedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.unpublishPost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("DRAFT");
            verify(postRepository).save(any(Post.class));
        }

        @Test
        @DisplayName("unpublishPost_notPublished_throwsException")
        void unpublishPost_notPublished_throwsException() {
            // Arrange
            Post draftPost = buildPost(Post.PostStatus.DRAFT);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(draftPost));

            // Act & Assert
            assertThatThrownBy(() -> postService.unpublishPost(TEST_POST_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4106);
                        assertThat(bex.getMessage()).contains("not published");
                    });
        }
    }

    // ── deletePost() Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("deletePost()")
    class DeletePost {

        @Test
        @DisplayName("deletePost_draftSuccess")
        void deletePost_draftSuccess() {
            // Arrange
            Post draftPost = buildPost(Post.PostStatus.DRAFT);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(draftPost));

            // Act
            postService.deletePost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            verify(postRepository).delete(draftPost);
        }

        @Test
        @DisplayName("deletePost_archivedSuccess")
        void deletePost_archivedSuccess() {
            // Arrange
            Post archivedPost = buildPost(Post.PostStatus.ARCHIVED);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(archivedPost));

            // Act
            postService.deletePost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            verify(postRepository).delete(archivedPost);
        }

        @Test
        @DisplayName("deletePost_notFound_throwsException")
        void deletePost_notFound_throwsException() {
            // Arrange
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.deletePost(TEST_POST_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4100);
                    });
        }
    }

    // ── getPost() Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("getPost()")
    class GetPost {

        @Test
        @DisplayName("getPost_success")
        void getPost_success() {
            // Arrange
            Post post = buildPost(Post.PostStatus.DRAFT);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.getPost(TEST_POST_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_POST_ID);
        }
    }

    // ── getPosts() Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("getPosts()")
    class GetPosts {

        @Test
        @DisplayName("getPosts_withPagination_success")
        void getPosts_withPagination_success() {
            // Arrange
            Post post = buildPost(Post.PostStatus.DRAFT);
            Page<Post> postPage = new PageImpl<>(List.of(post));

            when(postRepository.findByTenantId(eq(TEST_TENANT_ID), any(Pageable.class))).thenReturn(postPage);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostListResponse response = postService.getPosts(TEST_TENANT_ID, 0, 10, null);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getPosts()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getPosts_withStatusFilter_success")
        void getPosts_withStatusFilter_success() {
            // Arrange
            Post post = buildPost(Post.PostStatus.DRAFT);
            Page<Post> postPage = new PageImpl<>(List.of(post));

            when(postRepository.findByTenantIdAndStatus(eq(TEST_TENANT_ID), eq(Post.PostStatus.DRAFT), any(Pageable.class)))
                    .thenReturn(postPage);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostListResponse response = postService.getPosts(TEST_TENANT_ID, 0, 10, Post.PostStatus.DRAFT);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getPosts()).hasSize(1);
        }

        @Test
        @DisplayName("Sprint 164: size 帶超大值時，實際查詢頁面大小上限為 100（而非無界，避免資源耗盡）")
        void getPosts_hugeSize_cappedAt100() {
            when(postRepository.findByTenantId(eq(TEST_TENANT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            postService.getPosts(TEST_TENANT_ID, 0, 999999999, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(postRepository).findByTenantId(eq(TEST_TENANT_ID), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        }
    }

    // ── getPublishedPosts() Tests（Sprint 164）───────────────────────────

    @Nested
    @DisplayName("getPublishedPosts()")
    class GetPublishedPosts {

        @Test
        @DisplayName("Sprint 164: size 帶超大值時，實際查詢頁面大小上限為 100（公開端點，無需登入即可觸發）")
        void getPublishedPosts_hugeSize_cappedAt100() {
            when(postRepository.findPublishedByTenantId(eq(TEST_TENANT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            postService.getPublishedPosts(TEST_TENANT_ID, 0, 999999999);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(postRepository).findPublishedByTenantId(eq(TEST_TENANT_ID), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        }
    }

    // ── getPublishedPostBySlug() Tests ─────────────────────────────────

    /**
     * Sprint 106 / DEF-055 的守衛。修復前這條路徑是
     * 「{@code post.incrementViewCount()} → {@code save()}」，而方法又標成
     * {@code @Transactional(readOnly = true)}——唯讀交易下 Hibernate 是
     * {@code FlushMode.MANUAL}，遞增**永遠不會被寫進 DB**（紅燈實測：單執行緒
     * 瀏覽一次仍為 0）。這裡守住「委派給原子敘述、不再走 save()」，
     * 實際的持久化與併發正確性由 {@code ViewCountConcurrencyIntegrationTest} 驗證。
     */
    @Nested
    @DisplayName("getPublishedPostBySlug()")
    class GetPublishedPostBySlug {

        @Test
        @DisplayName("getPublishedPostBySlug_incrementsViewCountAtomically")
        void getPublishedPostBySlug_incrementsViewCountAtomically() {
            // Arrange
            Post post = buildPost(Post.PostStatus.PUBLISHED);
            when(postRepository.findByTenantIdAndSlug(TEST_TENANT_ID, "test-post"))
                    .thenReturn(Optional.of(post));
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(TEST_POST_ID))
                    .thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.getPublishedPostBySlug("test-post", TEST_TENANT_ID);

            // Assert
            verify(postRepository).incrementViewCount(TEST_POST_ID);
            // 守衛：一旦有人改回讀後寫，這兩行會立刻失敗
            verify(postRepository, never()).save(any(Post.class));
            assertThat(post.getViewCount())
                    .as("實體仍在持久化上下文中，碰了 setter 會讓髒檢查在提交時整列寫回、覆蓋原子遞增")
                    .isZero();
            assertThat(response.getViewCount())
                    .as("回應須包含本次瀏覽（前台顯示「N 次瀏覽」），維持修復前的回應語意")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("getPublishedPostBySlug_draftPost_throwsE4101")
        void getPublishedPostBySlug_draftPost_throwsE4101() {
            // Arrange
            Post post = buildPost(Post.PostStatus.DRAFT);
            when(postRepository.findByTenantIdAndSlug(TEST_TENANT_ID, "test-post"))
                    .thenReturn(Optional.of(post));

            // Act & Assert
            assertThatThrownBy(() -> postService.getPublishedPostBySlug("test-post", TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4101);
            verify(postRepository, never()).incrementViewCount(any(UUID.class));
        }
    }
}