package com.nextkey.ecommerce.core.cms.post;

import com.nextkey.ecommerce.api.dto.M15Dto;
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
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(category));
            when(postRepository.existsBySlug(anyString())).thenReturn(false);
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.createPost(TEST_TENANT_ID, TEST_AUTHOR_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategoryId()).isEqualTo(TEST_CATEGORY_ID);
            verify(postCategoryRepository).findById(TEST_CATEGORY_ID);
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
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(category));
            when(postRepository.save(any(Post.class))).thenReturn(existingPost);
            when(postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(any(UUID.class))).thenReturn(new ArrayList<>());

            // Act
            M15Dto.PostResponse response = postService.updatePost(TEST_POST_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            verify(postCategoryRepository).findById(TEST_CATEGORY_ID);
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
    }
}