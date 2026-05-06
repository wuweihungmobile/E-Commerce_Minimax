package com.nextkey.ecommerce.core.cms.post;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
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
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostCategoryService 單元測試
 *
 * 測試範圍：
 * - createCategory() - 建立分類
 * - updateCategory() - 更新分類
 * - deleteCategory() - 刪除分類
 * - getCategories() - 取得分類列表
 * - getCategory() - 取得分類詳情
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostCategoryService: 分類管理")
class PostCategoryServiceTest {

    @Mock
    private PostCategoryRepository postCategoryRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PostCategoryService postCategoryService;

    // 測試資料
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
    private static final UUID TEST_POST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");

    private Tenant buildTenant() {
        return Tenant.builder()
                .id(TEST_TENANT_ID)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
    }

    private PostCategory buildCategory() {
        return PostCategory.builder()
                .id(TEST_CATEGORY_ID)
                .tenant(buildTenant())
                .name("Test Category")
                .slug("test-category")
                .description("Test description")
                .sortOrder(0)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    private Post buildPost(PostCategory category) {
        return Post.builder()
                .id(TEST_POST_ID)
                .tenant(buildTenant())
                .author(null)
                .title("Test Post")
                .slug("test-post")
                .content("Test content")
                .category(category)
                .status(Post.PostStatus.DRAFT)
                .tags(new ArrayList<>())
                .viewCount(0)
                .embeds(new ArrayList<>())
                .build();
    }

    // ── createCategory() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("createCategory()")
    class CreateCategory {

        @Test
        @DisplayName("createCategory_success")
        void createCategory_success() {
            // Arrange
            Tenant tenant = buildTenant();
            PostCategory savedCategory = buildCategory();

            M15Dto.CreateCategoryRequest request = M15Dto.CreateCategoryRequest.builder()
                    .name("Test Category")
                    .description("Test description")
                    .sortOrder(1)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(postCategoryRepository.existsByTenantIdAndSlug(eq(TEST_TENANT_ID), anyString())).thenReturn(false);
            when(postCategoryRepository.save(any(PostCategory.class))).thenReturn(savedCategory);

            // Act
            M15Dto.CategoryResponse response = postCategoryService.createCategory(TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Test Category");
            assertThat(response.getSlug()).isEqualTo("test-category");
            verify(postCategoryRepository).save(any(PostCategory.class));
        }

        @Test
        @DisplayName("createCategory_withDefaultSortOrder")
        void createCategory_withDefaultSortOrder() {
            // Arrange
            Tenant tenant = buildTenant();
            PostCategory savedCategory = PostCategory.builder()
                    .id(TEST_CATEGORY_ID)
                    .tenant(tenant)
                    .name("Test Category")
                    .slug("test-category")
                    .description("Test description")
                    .sortOrder(0) // Default sort order
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            M15Dto.CreateCategoryRequest request = M15Dto.CreateCategoryRequest.builder()
                    .name("Test Category")
                    .description("Test description")
                    // No sortOrder provided
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(postCategoryRepository.existsByTenantIdAndSlug(eq(TEST_TENANT_ID), anyString())).thenReturn(false);
            when(postCategoryRepository.save(any(PostCategory.class))).thenReturn(savedCategory);

            // Act
            M15Dto.CategoryResponse response = postCategoryService.createCategory(TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getSortOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("createCategory_tenantNotFound_throwsException")
        void createCategory_tenantNotFound_throwsException() {
            // Arrange
            M15Dto.CreateCategoryRequest request = M15Dto.CreateCategoryRequest.builder()
                    .name("Test Category")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.createCategory(TEST_TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }

        @Test
        @DisplayName("createCategory_duplicateSlug_throwsException")
        void createCategory_duplicateSlug_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            M15Dto.CreateCategoryRequest request = M15Dto.CreateCategoryRequest.builder()
                    .name("Test Category")
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(postCategoryRepository.existsByTenantIdAndSlug(eq(TEST_TENANT_ID), anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.createCategory(TEST_TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4102);
                        assertThat(bex.getMessage()).contains("Category slug already exists");
                    });
        }
    }

    // ── updateCategory() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("updateCategory()")
    class UpdateCategory {

        @Test
        @DisplayName("updateCategory_success")
        void updateCategory_success() {
            // Arrange
            PostCategory existingCategory = buildCategory();
            PostCategory updatedCategory = PostCategory.builder()
                    .id(TEST_CATEGORY_ID)
                    .tenant(existingCategory.getTenant())
                    .name("Updated Category")
                    .slug("updated-category")
                    .description("Updated description")
                    .sortOrder(5)
                    .isActive(true)
                    .createdAt(existingCategory.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

            M15Dto.UpdateCategoryRequest request = M15Dto.UpdateCategoryRequest.builder()
                    .name("Updated Category")
                    .description("Updated description")
                    .sortOrder(5)
                    .build();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));
            when(postCategoryRepository.existsByTenantIdAndSlugAndIdNot(eq(TEST_TENANT_ID), anyString(), eq(TEST_CATEGORY_ID)))
                    .thenReturn(false);
            when(postCategoryRepository.save(any(PostCategory.class))).thenReturn(updatedCategory);

            // Act
            M15Dto.CategoryResponse response = postCategoryService.updateCategory(
                    TEST_CATEGORY_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Updated Category");
            verify(postCategoryRepository).save(any(PostCategory.class));
        }

        @Test
        @DisplayName("updateCategory_nameOnly_success")
        void updateCategory_nameOnly_success() {
            // Arrange
            PostCategory existingCategory = buildCategory();
            PostCategory updatedCategory = PostCategory.builder()
                    .id(TEST_CATEGORY_ID)
                    .tenant(existingCategory.getTenant())
                    .name("New Name")
                    .slug("new-name")
                    .description(existingCategory.getDescription())
                    .sortOrder(existingCategory.getSortOrder())
                    .isActive(true)
                    .createdAt(existingCategory.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

            M15Dto.UpdateCategoryRequest request = M15Dto.UpdateCategoryRequest.builder()
                    .name("New Name")
                    .build();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));
            when(postCategoryRepository.existsByTenantIdAndSlugAndIdNot(eq(TEST_TENANT_ID), anyString(), eq(TEST_CATEGORY_ID)))
                    .thenReturn(false);
            when(postCategoryRepository.save(any(PostCategory.class))).thenReturn(updatedCategory);

            // Act
            M15Dto.CategoryResponse response = postCategoryService.updateCategory(
                    TEST_CATEGORY_ID, TEST_TENANT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("updateCategory_notFound_throwsException")
        void updateCategory_notFound_throwsException() {
            // Arrange
            M15Dto.UpdateCategoryRequest request = M15Dto.UpdateCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.updateCategory(
                    TEST_CATEGORY_ID, TEST_TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4102);
                    });
        }

        @Test
        @DisplayName("updateCategory_wrongTenant_throwsException")
        void updateCategory_wrongTenant_throwsException() {
            // Arrange
            PostCategory existingCategory = buildCategory();
            UUID wrongTenantId = UUID.randomUUID();

            M15Dto.UpdateCategoryRequest request = M15Dto.UpdateCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.updateCategory(
                    TEST_CATEGORY_ID, wrongTenantId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }
    }

    // ── deleteCategory() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("deleteCategory()")
    class DeleteCategory {

        @Test
        @DisplayName("deleteCategory_success")
        void deleteCategory_success() {
            // Arrange
            PostCategory existingCategory = buildCategory();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));
            when(postRepository.findByTenantIdAndCategoryId(eq(TEST_TENANT_ID), eq(TEST_CATEGORY_ID), any()))
                    .thenReturn(Page.empty());

            // Act
            postCategoryService.deleteCategory(TEST_CATEGORY_ID, TEST_TENANT_ID);

            // Assert
            verify(postCategoryRepository).delete(existingCategory);
        }

        @Test
        @DisplayName("deleteCategory_withAssociatedPosts_throwsException")
        void deleteCategory_withAssociatedPosts_throwsException() {
            // Arrange
            PostCategory existingCategory = buildCategory();
            Post associatedPost = buildPost(existingCategory);
            Page<Post> associatedPostsPage = new PageImpl<>(List.of(associatedPost));

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));
            when(postRepository.findByTenantIdAndCategoryId(eq(TEST_TENANT_ID), eq(TEST_CATEGORY_ID), any()))
                    .thenReturn(associatedPostsPage);

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.deleteCategory(TEST_CATEGORY_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4102);
                        assertThat(bex.getMessage()).contains("Cannot delete category with associated posts");
                    });
        }

        @Test
        @DisplayName("deleteCategory_notFound_throwsException")
        void deleteCategory_notFound_throwsException() {
            // Arrange
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.deleteCategory(TEST_CATEGORY_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4102);
                    });
        }

        @Test
        @DisplayName("deleteCategory_wrongTenant_throwsException")
        void deleteCategory_wrongTenant_throwsException() {
            // Arrange
            PostCategory existingCategory = buildCategory();
            UUID wrongTenantId = UUID.randomUUID();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.deleteCategory(TEST_CATEGORY_ID, wrongTenantId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }
    }

    // ── getCategories() Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("getCategories()")
    class GetCategories {

        @Test
        @DisplayName("getCategories_success")
        void getCategories_success() {
            // Arrange
            PostCategory category1 = PostCategory.builder()
                    .id(UUID.randomUUID())
                    .tenant(buildTenant())
                    .name("Category 1")
                    .slug("category-1")
                    .sortOrder(1)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            PostCategory category2 = PostCategory.builder()
                    .id(UUID.randomUUID())
                    .tenant(buildTenant())
                    .name("Category 2")
                    .slug("category-2")
                    .sortOrder(2)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID))
                    .thenReturn(List.of(category1, category2));

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getCategories(TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).hasSize(2);
            assertThat(response.getTotalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("getCategories_emptyList_returnsEmpty")
        void getCategories_emptyList_returnsEmpty() {
            // Arrange
            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID))
                    .thenReturn(List.of());

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getCategories(TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCategories_tenantIsolation")
        void getCategories_tenantIsolation() {
            // Arrange
            UUID otherTenantId = UUID.randomUUID();

            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(otherTenantId))
                    .thenReturn(List.of());

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getCategories(otherTenantId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).isEmpty();
            // Verify different tenant ID was used
            verify(postCategoryRepository).findByTenantIdOrderBySortOrderAsc(otherTenantId);
        }
    }

    // ── getCategory() Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("getCategory()")
    class GetCategory {

        @Test
        @DisplayName("getCategory_success")
        void getCategory_success() {
            // Arrange
            PostCategory category = buildCategory();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(category));

            // Act
            M15Dto.CategoryResponse response = postCategoryService.getCategory(TEST_CATEGORY_ID, TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_CATEGORY_ID);
            assertThat(response.getName()).isEqualTo("Test Category");
        }

        @Test
        @DisplayName("getCategory_notFound_throwsException")
        void getCategory_notFound_throwsException() {
            // Arrange
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.getCategory(TEST_CATEGORY_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4102);
                    });
        }

        @Test
        @DisplayName("getCategory_wrongTenant_throwsException")
        void getCategory_wrongTenant_throwsException() {
            // Arrange
            PostCategory category = buildCategory();
            UUID wrongTenantId = UUID.randomUUID();

            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(category));

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.getCategory(TEST_CATEGORY_ID, wrongTenantId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_4031);
                    });
        }
    }
}