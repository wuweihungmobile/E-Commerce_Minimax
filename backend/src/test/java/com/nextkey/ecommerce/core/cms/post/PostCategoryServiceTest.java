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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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

    /** 同店既有的分類：只有編號與名稱重要（名稱檢查看名稱），代稱用編號衍生以免互撞。 */
    private PostCategory buildCategoryNamed(UUID id, String name) {
        return PostCategory.builder()
                .id(id)
                .tenant(buildTenant())
                .name(name)
                .slug("slug-" + id)
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

        // DEF-480（Sprint 334）：全中文名稱的 slug 曾是空字串；(tenant_id, slug) 唯一，所以同一家店第二個
        // 全中文分類會被判成 slug 重複（E-8014）而建立失敗——店家只是想取個中文分類名。
        @Test
        @DisplayName("createCategory_chineseOnlyName_slugFallsBackToCategoryPrefix")
        void createCategory_chineseOnlyName_slugFallsBackToCategoryPrefix() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(postCategoryRepository.existsByTenantIdAndSlug(eq(TEST_TENANT_ID), anyString())).thenReturn(false);
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> {
                PostCategory c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                c.setUpdatedAt(Instant.now());
                return c;
            });

            // Act
            M15Dto.CategoryResponse first = postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name("旅遊").build());
            M15Dto.CategoryResponse second = postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name("美食").build());

            // Assert
            assertThat(first.getSlug()).matches("^category-[0-9a-f]{8}$");
            assertThat(second.getSlug()).matches("^category-[0-9a-f]{8}$");
            assertThat(first.getSlug()).isNotEqualTo(second.getSlug());
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

        // DEF-492（Sprint 338）：名稱不同、只差中文的分類（「旅遊筆記2026」「咖啡手帖2026」）會產生同一個代稱「2026」。
        // 原本第二個起直接 409 E-8014（本案例原本斷言的就是這個）——店主看到的是兩個名稱明明不同、卻建不起來；
        // 現在比照 updateCategory：代稱撞到就接隨機後綴，建立成功。
        @Test
        @DisplayName("DEF-492: createCategory_slugTakenByAnotherName_appendsRandomSuffixInsteadOfFailing")
        void createCategory_slugTakenByAnotherName_appendsRandomSuffixInsteadOfFailing() {
            // Arrange：第一個分類的代稱「2026」還沒被占用，第二個建立時已被占用
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(postCategoryRepository.existsByTenantIdAndSlug(TEST_TENANT_ID, "2026")).thenReturn(false, true);
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> {
                PostCategory c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                c.setUpdatedAt(Instant.now());
                return c;
            });

            // Act
            M15Dto.CategoryResponse first = postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name("旅遊筆記2026").build());
            M15Dto.CategoryResponse second = postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name("咖啡手帖2026").build());

            // Assert：兩個都建立成功；第二個保有自己的名稱，代稱是「2026-<8 碼>」
            assertThat(first.getName()).isEqualTo("旅遊筆記2026");
            assertThat(first.getSlug()).isEqualTo("2026");
            assertThat(second.getName()).isEqualTo("咖啡手帖2026");
            assertThat(second.getSlug()).matches("^2026-[0-9a-f]{8}$");
        }

        /** 同店既有「Food」「美食」時，以下寫法都算同名（忽略大小寫與前後空白，含全形空白 U+3000）。 */
        private static Stream<Arguments> sameNameVariants() {
            return Stream.of(
                    Arguments.of("Food", "Food"),
                    Arguments.of("Food", "food"),
                    Arguments.of("Food", "FOOD"),
                    Arguments.of("Food", "  Food  "),
                    Arguments.of("Food", "　Food　"),
                    Arguments.of("美食", "美食"),
                    Arguments.of("美食", " 美食 "),
                    Arguments.of("美食", "　美食　"),
                    Arguments.of("  Food", "food"));
        }

        // DEF-492（Sprint 338，使用者以互動選項決定「擋下並提示」）：名稱與同店既有分類相同就擋。
        // 現況原本不一致：英文同名會因代稱相同而被擋，全中文同名（代稱是隨機後備）卻能建立；這裡統一成「同名一律擋」。
        @ParameterizedTest(name = "既有「{0}」，要建立「{1}」→ 擋下")
        @MethodSource("sameNameVariants")
        @DisplayName("DEF-492: createCategory_sameNameAsExistingCategory_throwsE8021AndSavesNothing")
        void createCategory_sameNameAsExistingCategory_throwsE8021AndSavesNothing(String existingName, String requestedName) {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID))
                    .thenReturn(List.of(buildCategoryNamed(UUID.randomUUID(), existingName)));

            // Act & Assert
            assertThatThrownBy(() -> postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name(requestedName).build()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8021));
            verify(postCategoryRepository, never()).save(any(PostCategory.class));
        }

        private static Stream<Arguments> similarButDifferentNames() {
            return Stream.of(
                    Arguments.of("美食", "美食家"),
                    Arguments.of("Food", "Foods"),
                    Arguments.of("旅遊筆記2026", "咖啡手帖2026"));
        }

        // 反向守門：只擋「名稱相同」，名稱包含、相近、只差中文的都不能被誤擋（否則會把 DEF-492 要修的情形又擋回去）。
        @ParameterizedTest(name = "既有「{0}」，要建立「{1}」→ 放行")
        @MethodSource("similarButDifferentNames")
        @DisplayName("DEF-492: createCategory_similarButDifferentName_isNotBlocked")
        void createCategory_similarButDifferentName_isNotBlocked(String existingName, String requestedName) {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID))
                    .thenReturn(List.of(buildCategoryNamed(UUID.randomUUID(), existingName)));
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> {
                PostCategory c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                c.setUpdatedAt(Instant.now());
                return c;
            });

            // Act
            M15Dto.CategoryResponse response = postCategoryService.createCategory(TEST_TENANT_ID,
                    M15Dto.CreateCategoryRequest.builder().name(requestedName).build());

            // Assert
            assertThat(response.getName()).isEqualTo(requestedName);
            verify(postCategoryRepository).save(any(PostCategory.class));
        }
    }

    // ── updateCategory() Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("updateCategory()")
    class UpdateCategory {

        @Test
        @DisplayName("updateCategory_nameUnchanged_keepsTheExistingSlug")
        void updateCategory_nameUnchanged_keepsTheExistingSlug() {
            // Arrange：全中文名稱的分類，slug 是當初產生的隨機後備
            PostCategory existingCategory = buildCategory();
            existingCategory.setName("旅遊");
            existingCategory.setSlug("category-0a1b2c3d");
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(existingCategory));
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act：只改描述，名稱照舊一併送出
            postCategoryService.updateCategory(TEST_CATEGORY_ID, TEST_TENANT_ID,
                    M15Dto.UpdateCategoryRequest.builder().name("旅遊").description("新的描述").build());

            // Assert
            assertThat(existingCategory.getSlug()).isEqualTo("category-0a1b2c3d");
        }

        // DEF-492（Sprint 338）：改名也要守「同店名稱不重複」，否則建立時擋住、改名卻能繞過去。
        @Test
        @DisplayName("DEF-492: updateCategory_renameToAnotherCategorysName_throwsE8021AndLeavesTheCategoryUntouched")
        void updateCategory_renameToAnotherCategorysName_throwsE8021AndLeavesTheCategoryUntouched() {
            // Arrange：同店還有一個叫「Food」的分類
            PostCategory current = buildCategory();
            PostCategory other = buildCategoryNamed(UUID.randomUUID(), "Food");
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(current));
            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID)).thenReturn(List.of(current, other));

            // Act & Assert：改名成「 food 」（大小寫與空白不同）→ 擋下，原本的名稱與代稱都沒動
            assertThatThrownBy(() -> postCategoryService.updateCategory(TEST_CATEGORY_ID, TEST_TENANT_ID,
                    M15Dto.UpdateCategoryRequest.builder().name(" food ").build()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8021));
            assertThat(current.getName()).isEqualTo("Test Category");
            assertThat(current.getSlug()).isEqualTo("test-category");
            verify(postCategoryRepository, never()).save(any(PostCategory.class));
        }

        // 檢查要排除分類自己：只改自己名稱的大小寫（food → Food）不能被「與自己同名」擋住。
        @Test
        @DisplayName("DEF-492: updateCategory_changeOnlyTheCaseOfItsOwnName_isAllowed")
        void updateCategory_changeOnlyTheCaseOfItsOwnName_isAllowed() {
            // Arrange
            PostCategory current = buildCategoryNamed(TEST_CATEGORY_ID, "food");
            current.setSlug("food");
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(current));
            when(postCategoryRepository.findByTenantIdOrderBySortOrderAsc(TEST_TENANT_ID)).thenReturn(List.of(current));
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            postCategoryService.updateCategory(TEST_CATEGORY_ID, TEST_TENANT_ID,
                    M15Dto.UpdateCategoryRequest.builder().name("Food").build());

            // Assert
            assertThat(current.getName()).isEqualTo("Food");
            assertThat(current.getSlug()).isEqualTo("food");
        }

        // 名稱沒改就不檢查：這項規則上線前已經存在的同名分類（全中文同名原本能建立）不能因此連描述、排序都改不了。
        @Test
        @DisplayName("DEF-492: updateCategory_nameUnchanged_doesNotRecheckDuplicates_soExistingDuplicatesStayEditable")
        void updateCategory_nameUnchanged_doesNotRecheckDuplicates_soExistingDuplicatesStayEditable() {
            // Arrange：同店已有兩個「旅遊」（規則上線前建立的）
            PostCategory current = buildCategoryNamed(TEST_CATEGORY_ID, "旅遊");
            when(postCategoryRepository.findById(TEST_CATEGORY_ID)).thenReturn(Optional.of(current));
            when(postCategoryRepository.save(any(PostCategory.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act：只改描述，名稱照舊一併送出
            postCategoryService.updateCategory(TEST_CATEGORY_ID, TEST_TENANT_ID,
                    M15Dto.UpdateCategoryRequest.builder().name("旅遊").description("新的描述").build());

            // Assert
            assertThat(current.getDescription()).isEqualTo("新的描述");
            verify(postCategoryRepository, never()).findByTenantIdOrderBySortOrderAsc(any());
        }

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

            // Act & Assert（DEF-388：原誤用 E_4102「找不到分類」，改用 E_8015）
            assertThatThrownBy(() -> postCategoryService.deleteCategory(TEST_CATEGORY_ID, TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_8015);
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

    // ── getActiveCategories() Tests（DEF-358(c)，Sprint 256）───────────

    @Nested
    @DisplayName("getActiveCategories()")
    class GetActiveCategories {

        /**
         * 前台讀取先確認店鋪是 ACTIVE（Sprint 337，DEF-485）。用 lenient：不是每個案例都會走到那一步；
         * 狀態寫死 ACTIVE，所以守門若改查別的狀態，這個 stub 對不上、會讓下面的成功案例紅燈。
         */
        @BeforeEach
        void storeIsActive() {
            lenient().when(tenantRepository.findByIdAndStatus(TEST_TENANT_ID, Tenant.TenantStatus.ACTIVE))
                    .thenReturn(Optional.of(buildTenant()));
        }

        @Test
        @DisplayName("Sprint 337（DEF-485）: 店鋪不是 ACTIVE（或不存在）→ E-2000，而且不讀任何分類")
        void getActiveCategories_nonActiveStore_throwsE2000_andReadsNothing() {
            when(tenantRepository.findByIdAndStatus(TEST_TENANT_ID, Tenant.TenantStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> postCategoryService.getActiveCategories(TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_2000);
            verifyNoInteractions(postCategoryRepository);
        }

        @Test
        @DisplayName("getActiveCategories_success_onlyQueriesActiveOnes")
        void getActiveCategories_success_onlyQueriesActiveOnes() {
            // Arrange
            PostCategory category1 = PostCategory.builder()
                    .id(UUID.randomUUID())
                    .tenant(buildTenant())
                    .name("Category 1")
                    .slug("category-1")
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            when(postCategoryRepository.findByTenantIdAndIsActiveTrue(TEST_TENANT_ID))
                    .thenReturn(List.of(category1));

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getActiveCategories(TEST_TENANT_ID);

            // Assert：公開端點只能呼叫「僅啟用」的查詢方法，不能呼叫會連停用分類一起回傳的
            // findByTenantIdOrderBySortOrderAsc（那是 Dashboard 管理視角專用，見 getCategories()）
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(1);
            verify(postCategoryRepository).findByTenantIdAndIsActiveTrue(TEST_TENANT_ID);
            verify(postCategoryRepository, never()).findByTenantIdOrderBySortOrderAsc(any(UUID.class));
        }

        @Test
        @DisplayName("getActiveCategories_emptyList_returnsEmpty")
        void getActiveCategories_emptyList_returnsEmpty() {
            // Arrange
            when(postCategoryRepository.findByTenantIdAndIsActiveTrue(TEST_TENANT_ID))
                    .thenReturn(List.of());

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getActiveCategories(TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getActiveCategories_tenantIsolation")
        void getActiveCategories_tenantIsolation() {
            // Arrange
            UUID otherTenantId = UUID.randomUUID();
            when(tenantRepository.findByIdAndStatus(otherTenantId, Tenant.TenantStatus.ACTIVE))
                    .thenReturn(Optional.of(buildTenant()));
            when(postCategoryRepository.findByTenantIdAndIsActiveTrue(otherTenantId))
                    .thenReturn(List.of());

            // Act
            M15Dto.CategoryListResponse response = postCategoryService.getActiveCategories(otherTenantId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).isEmpty();
            verify(postCategoryRepository).findByTenantIdAndIsActiveTrue(otherTenantId);
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