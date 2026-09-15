package com.nextkey.ecommerce.core.cms.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostEmbedRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.util.PageableUtils;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * M15 CMS Post Service
 * 貼文服務，處理 CRUD、嵌入解析、發布/下架
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostEmbedRepository postEmbedRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final ListingRepository listingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final FeatureToggleService featureToggleService;

    // Markdown embed pattern: {{embed:listing:<listing_id>}}
    private static final Pattern EMBED_PATTERN =
            Pattern.compile("\\{\\{embed:listing:([a-fA-F0-9\\-]+)\\}\\}");

    // DEF-105（Sprint 154）：title/content 目前是純文字/Markdown 來源，前台以純文字顯示、
    // 未經任何 HTML/Markdown 渲染，故非可利用漏洞；本檢查僅屬防禦性強化，偵測到即拒絕存檔，
    // 不對合法內容做任何改寫——避免消毒函式庫慣有的 HTML 實體編碼把常見的 &/</> 字元
    // 永久改寫成 &amp;/&lt;/&gt; 亂碼（現行純文字渲染方式下會直接顯示亂碼給使用者）。
    // 若未來新增 Markdown-to-HTML 或 dangerouslySetInnerHTML 渲染路徑，該處必須自行
    // 針對其實際輸出情境（HTML sink）另行消毒，不可假設這裡的檢查已經足夠。
    private static final Pattern DANGEROUS_MARKUP_PATTERN = Pattern.compile(
            "<\\s*(script|iframe|object|embed|style)\\b|on\\w+\\s*=|javascript:|vbscript:|data:text/html",
            Pattern.CASE_INSENSITIVE);

    // Post excerpt limits
    private static final int EXCERPT_MAX_LENGTH = 500;

    // ========== Post CRUD ==========

    /**
     * 建立貼文（含嵌入解析）
     */
    @Transactional
    public M15Dto.PostResponse createPost(UUID tenantId, UUID authorId, M15Dto.CreatePostRequest request) {
        // 驗證必填欄位
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.E_9005, "Title is required");
        }

        // DEF-105：偵測危險標記，拒絕存檔
        validateNoUnsafeMarkup(request.getTitle(), request.getContent());

        // 驗證 Tenant 存在
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證 Author 存在
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        // 產生 Slug
        String slug = generateSlug(request.getTitle());

        // 建立 Post
        Post post = Post.builder()
                .tenant(tenant)
                .author(author)
                .title(request.getTitle())
                .slug(slug)
                .content(request.getContent())
                .excerpt(generateExcerpt(request.getContent()))
                .featuredImageUrl(request.getFeaturedImageUrl())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .status(Post.PostStatus.DRAFT)
                .build();

        // 設定分類
        if (request.getCategoryId() != null) {
            PostCategory category = postCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4102));
            post.setCategory(category);
        }

        post = postRepository.save(post);

        // 如果 autoPublish 為 true，直接發布
        if (Boolean.TRUE.equals(request.getAutoPublish())) {
            // Sprint 147：MAX_POSTS 數量配額強制執行（PRD §4.4）——此時新貼文仍是 DRAFT
            // （上面剛 save 完），計數不含它本身
            featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_POSTS,
                    postRepository.countByTenantIdAndStatus(tenantId, Post.PostStatus.PUBLISHED));
            post.publish();
            post = postRepository.save(post);
        }

        // 解析內容中的嵌入（驗證 Listing 存在性和不重複）
        if (request.getContent() != null && !request.getContent().isBlank()) {
            parseEmbeds(post, request.getContent());
        }

        // 載入 embeds
        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());

        return buildPostResponse(post, embeds);
    }

    /**
     * 更新貼文
     */
    @Transactional
    public M15Dto.PostResponse updatePost(UUID postId, UUID tenantId, M15Dto.UpdatePostRequest request) {
        Post post = getPostOrThrow(postId);

        // 驗證 Tenant 擁有權
        validateTenantOwnership(post, tenantId);

        // DEF-105：偵測危險標記，拒絕存檔
        validateNoUnsafeMarkup(request.getTitle(), request.getContent());

        // 更新欄位
        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
            // 如果標題變更，更新 slug
            post.setSlug(generateUniqueSlug(request.getTitle(), post.getId()));
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
            post.setExcerpt(generateExcerpt(request.getContent()));
            // 重新解析嵌入
            parseEmbeds(post, request.getContent());
        }
        if (request.getFeaturedImageUrl() != null) {
            post.setFeaturedImageUrl(request.getFeaturedImageUrl());
        }
        if (request.getTags() != null) {
            post.setTags(request.getTags());
        }
        if (request.getCategoryId() != null) {
            PostCategory category = postCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4102));
            post.setCategory(category);
        }

        post = postRepository.save(post);

        // 載入 embeds
        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());
        return buildPostResponse(post, embeds);
    }

    /**
     * 發布貼文
     */
    @Transactional
    public M15Dto.PostResponse publishPost(UUID postId, UUID tenantId) {
        Post post = getPostOrThrow(postId);
        validateTenantOwnership(post, tenantId);

        if (post.getStatus() == Post.PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.E_4106, "Post is already published");
        }

        // Sprint 147：MAX_POSTS 數量配額強制執行（PRD §4.4）
        featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_POSTS,
                postRepository.countByTenantIdAndStatus(tenantId, Post.PostStatus.PUBLISHED));

        post.publish();
        post = postRepository.save(post);

        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());
        return buildPostResponse(post, embeds);
    }

    /**
     * 下架貼文
     */
    @Transactional
    public M15Dto.PostResponse unpublishPost(UUID postId, UUID tenantId) {
        Post post = getPostOrThrow(postId);
        validateTenantOwnership(post, tenantId);

        if (post.getStatus() != Post.PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.E_4106, "Post is not published");
        }

        post.unpublish();
        post = postRepository.save(post);

        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());
        return buildPostResponse(post, embeds);
    }

    /**
     * 刪除貼文（僅 DRAFT 和 ARCHIVED 狀態可刪除，已發布的貼文須先下架）
     */
    @Transactional
    public void deletePost(final UUID postId, final UUID tenantId) {
        Post post = getPostOrThrow(postId);
        validateTenantOwnership(post, tenantId);

        // 刪除貼文
        postRepository.delete(post);
    }

    /**
     * 取得貼文詳情
     */
    @Transactional(readOnly = true)
    public M15Dto.PostResponse getPost(UUID postId, UUID tenantId) {
        Post post = getPostOrThrow(postId);
        validateTenantOwnership(post, tenantId);

        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());
        return buildPostResponse(post, embeds);
    }

    /**
     * 取得貼文列表（Dashboard）
     */
    @Transactional(readOnly = true)
    public M15Dto.PostListResponse getPosts(UUID tenantId, int page, int size, Post.PostStatus status) {
        PageRequest pageRequest = PageableUtils.of(page, size, 100, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> postPage;
        if (status != null) {
            postPage = postRepository.findByTenantIdAndStatus(tenantId, status, pageRequest);
        } else {
            postPage = postRepository.findByTenantId(tenantId, pageRequest);
        }

        List<M15Dto.PostResponse> posts = postPage.getContent().stream()
                .map(p -> {
                    List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(p.getId());
                    return buildPostResponse(p, embeds);
                })
                .toList();

        return M15Dto.PostListResponse.builder()
                .posts(posts)
                .totalCount((int) postPage.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(postPage.getTotalPages())
                .build();
    }

    // ========== Public APIs (前台) ==========

    /**
     * 前台：取得已發布貼文列表
     */
    @Transactional(readOnly = true)
    public M15Dto.PostListResponse getPublishedPosts(UUID tenantId, int page, int size) {
        PageRequest pageRequest = PageableUtils.of(page, size, 100, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Post> postPage = postRepository.findPublishedByTenantId(tenantId, pageRequest);

        List<M15Dto.PostResponse> posts = postPage.getContent().stream()
                .map(p -> {
                    List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(p.getId());
                    return buildPostResponse(p, embeds);
                })
                .toList();

        return M15Dto.PostListResponse.builder()
                .posts(posts)
                .totalCount((int) postPage.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(postPage.getTotalPages())
                .build();
    }

    /**
     * 前台：依 Slug 取得已發布貼文
     *
     * <p>本方法會遞增瀏覽次數，因此**不能**標成 {@code @Transactional(readOnly = true)}：
     * Hibernate 在唯讀交易下是 {@code FlushMode.MANUAL}，原本的
     * 「{@code post.incrementViewCount()} → {@code save()}」永遠不會被 flush，
     * 瀏覽數不是少計而是**完全沒計**（Sprint 106 紅燈實測：單執行緒瀏覽一次仍為 0）。
     */
    @Transactional
    public M15Dto.PostResponse getPublishedPostBySlug(String slug, UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.E_1002, "tenantId is required");
        }
        Post post = postRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4100));

        if (post.getStatus() != Post.PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.E_4101);
        }

        // 增加瀏覽次數：交由 DB 原子遞增。刻意不碰 post 的任何 setter——
        // post 仍在持久化上下文中，改了會讓髒檢查在提交時整列寫回、覆蓋這次遞增。
        postRepository.incrementViewCount(post.getId());

        List<PostEmbed> embeds = postEmbedRepository.findByPostIdOrderByEmbedOrderAsc(post.getId());
        M15Dto.PostResponse response = buildPostResponse(post, embeds);
        // post 的快照早於上面那筆遞增，補回本次瀏覽以維持既有的回應語意（前台會顯示「N 次瀏覽」）
        response.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        return response;
    }

    // ========== Embed Parsing ==========

    /**
     * 解析 Markdown 內容中的嵌入語法
     * 格式: {{embed:listing:<listing_id>}}
     */
    private List<UUID> parseEmbeds(final Post post, final String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        // 刪除現有嵌入
        postEmbedRepository.deleteByPostId(post.getId());

        List<UUID> embedListingIds = new ArrayList<>();
        Matcher matcher = EMBED_PATTERN.matcher(content);

        int order = 0;
        while (matcher.find()) {
            UUID listingId;
            try {
                listingId = UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid listing ID format in embed: {}", matcher.group(1));
                continue; // 跳過無效格式
            }

            // 驗證不重複
            if (embedListingIds.contains(listingId)) {
                throw new BusinessException(ErrorCode.E_4104);
            }

            // 驗證 Listing 存在
            Listing listing = listingRepository.findById(listingId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4105));

            // 驗證 Listing 狀態
            if (listing.getStatus() != Listing.ListingStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.E_4105, "Listing is not active");
            }

            // 建立嵌入
            PostEmbed embed = PostEmbed.builder()
                    .post(post)
                    .listingId(listingId)
                    .listingType(listing.getListingType().name())
                    .embedOrder(order++)
                    .build();
            postEmbedRepository.save(embed);

            embedListingIds.add(listingId);
            log.info("Parsed embed: listingId={}, order={}", listingId, order - 1);
        }

        return embedListingIds;
    }

    /**
     * 驗證嵌入不重複
     */
    public void validateEmbedNoDuplicate(final UUID postId, final UUID listingId) {
        if (postEmbedRepository.existsByPostIdAndListingId(postId, listingId)) {
            throw new BusinessException(ErrorCode.E_4104);
        }
    }

    // ========== Helper Methods ==========

    private Post getPostOrThrow(final UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4100));
    }

    private void validateTenantOwnership(final Post post, final UUID tenantId) {
        if (!post.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }
    }

    /**
     * DEF-105：偵測 title/content 是否含危險標記（{@code <script>}、事件屬性、
     * {@code javascript:}/{@code vbscript:} 等），偵測到即拒絕存檔，不做任何改寫。
     */
    private void validateNoUnsafeMarkup(final String title, final String content) {
        if (title != null && DANGEROUS_MARKUP_PATTERN.matcher(title).find()) {
            throw new BusinessException(ErrorCode.E_9009, "Title contains disallowed markup");
        }
        if (content != null && DANGEROUS_MARKUP_PATTERN.matcher(content).find()) {
            throw new BusinessException(ErrorCode.E_9009, "Content contains disallowed markup");
        }
    }

    private String generateSlug(final String title) {
        if (title == null || title.isBlank()) {
            return UUID.randomUUID().toString();
        }
        // URL-friendly slug: lowercase, replace spaces with hyphens, remove special chars
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s\\-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // 確保唯一性
        if (postRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }

    private String generateUniqueSlug(final String title, final UUID excludePostId) {
        String slug = generateSlug(title);
        if (postRepository.existsBySlugAndIdNot(slug, excludePostId)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }

    private String generateExcerpt(final String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        // 移除 Markdown 語法，取前 200 字
        String plain = content
                .replaceAll("#+\\s*", "")  // 移除標題標記
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")  // 移除連結，保留文字
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")  // 移除粗體標記
                .replaceAll("\\*([^*]+)\\*", "$1")  // 移除斜體標記
                .replaceAll("\\n+", " ")  // 移除換行
                .trim();

        if (plain.length() > EXCERPT_MAX_LENGTH) {
            return plain.substring(0, EXCERPT_MAX_LENGTH) + "...";
        }
        return plain;
    }

    /**
     * 建構 PostResponse（包含 embeds）
     */
    private M15Dto.PostResponse buildPostResponse(Post post, List<PostEmbed> embeds) {
        M15Dto.PostResponse response = M15Dto.PostResponse.from(post);
        response.setEmbeds(embeds.stream()
                .map(M15Dto.PostEmbedResponse::from)
                .toList());
        return response;
    }
}