package com.nextkey.ecommerce.core.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.CmsDto;
import com.nextkey.ecommerce.domain.model.cms.Banner;
import com.nextkey.ecommerce.domain.model.cms.ContentPage;
import com.nextkey.ecommerce.domain.repository.BannerRepository;
import com.nextkey.ecommerce.domain.repository.ContentPageRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * CmsService 單元測試（Sprint 74）
 *
 * <p>探查階段確認 CmsService 全部 11 個 public 方法先前完全零測試覆蓋（單元/整合/E2E 皆無）。
 * 依「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」的角度逐一審視，
 * 發現與 Sprint 68/70/72/73（DEF-023/024/026/027/028/029/030）同一系統性模式的
 * 兩組跨租戶 IDOR：
 *
 * <p>- DEF-032（US-001）：updatePage/publishPage/updateBanner/publishBanner 完全沒有
 *   擁有權/租戶檢查，任一租戶的 cms:update/cms:publish 持有者可竄改/發布其他租戶的頁面或橫幅。
 *   比照 DEF-019/DEF-024/DEF-028 的 tenant-based 模式修復（本租戶 or admin 放行）。
 * <p>- DEF-033（US-002）：getPages/getBanners（Admin 列表）呼叫無租戶過濾的查詢方法
 *   （ContentPageRepository/BannerRepository 已有 findByTenantIdAndStatus... 方法但從未被使用），
 *   任一租戶的 cms:read 持有者會看到系統中所有租戶的頁面/橫幅列表。
 *   比照 DEF-026/DEF-029 模式修復（admin 沿用舊查詢跨租戶總覽，非 admin 改用租戶過濾查詢）。
 *
 * <p>標記為「DEF-03X：...（修復前為紅燈，修復後轉綠）」的測試，於本 Sprint 修復生產程式碼前
 * 實際執行過並確認失敗，證明對應漏洞存在；詳細過程記錄於 DEFERRED_ITEMS_TRACKER.md。
 *
 * <p>DEF-034（Sprint 82）：getPageBySlug / getActiveBanners / recordBannerClick（公開瀏覽端點）
 * 原本完全不做租戶過濾，PO 拍板定位為訪客可瀏覽的公開行銷內容後，比照 PostController 模式
 * 改為要求呼叫端明確傳入 tenantId，SecurityConfig 同步補上 permitAll。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CmsService 單元測試 (Sprint 74)")
class CmsServiceTest {

    @Mock
    private ContentPageRepository contentPageRepository;
    @Mock
    private BannerRepository bannerRepository;

    @InjectMocks
    private CmsService cmsService;

    private static final UUID PAGE_ID = UUID.randomUUID();
    private static final UUID BANNER_ID = UUID.randomUUID();
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ========== Helper Methods ==========

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private ContentPage pageOf(final UUID tenantId, final ContentPage.ContentStatus status) {
        return ContentPage.builder()
                .id(PAGE_ID)
                .title("About Us")
                .slug("about-us")
                .pageType(ContentPage.PageType.ABOUT_US)
                .status(status)
                .tenantId(tenantId)
                .isIndexable(true)
                .sortOrder(0)
                .build();
    }

    private Banner bannerOf(final UUID tenantId, final Banner.BannerStatus status) {
        return Banner.builder()
                .id(BANNER_ID)
                .title("Summer Sale")
                .imageUrl("https://example.com/banner.png")
                .bannerType(Banner.BannerType.PROMOTION)
                .position(Banner.BannerPosition.HOME_TOP)
                .status(status)
                .tenantId(tenantId)
                .impressionCount(0)
                .clickCount(0)
                .sortOrder(0)
                .build();
    }

    // ========== createPage ==========

    @Test
    @DisplayName("createPage：建立頁面成功，tenantId/authorId 取自當前 context，狀態為 DRAFT")
    void createPage_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUser(USER_ID);
        when(contentPageRepository.findBySlug("about-us")).thenReturn(Optional.empty());
        when(contentPageRepository.save(any(ContentPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.CreatePageRequest request = CmsDto.CreatePageRequest.builder()
                .title("About Us").slug("about-us").pageType(CmsDto.PageType.ABOUT_US).build();

        CmsDto.PageResponse response = cmsService.createPage(request);

        assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        assertThat(response.getAuthorId()).isEqualTo(USER_ID);
        assertThat(response.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("createPage：slug 已存在拋出 E_9005")
    void createPage_duplicateSlug_throwsE9005() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(contentPageRepository.findBySlug("about-us")).thenReturn(Optional.of(pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT)));

        CmsDto.CreatePageRequest request = CmsDto.CreatePageRequest.builder()
                .title("About Us").slug("about-us").pageType(CmsDto.PageType.ABOUT_US).build();

        assertThatThrownBy(() -> cmsService.createPage(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9005);
        verify(contentPageRepository, never()).save(any());
    }

    // ========== updatePage ==========

    @Test
    @DisplayName("updatePage：本租戶可更新自己的頁面")
    void updatePage_sameTenant_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));
        when(contentPageRepository.save(any(ContentPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.UpdatePageRequest request = CmsDto.UpdatePageRequest.builder().title("Updated Title").build();
        CmsDto.PageResponse response = cmsService.updatePage(PAGE_ID, request);

        assertThat(response.getTitle()).isEqualTo("Updated Title");
        verify(contentPageRepository).save(page);
    }

    @Test
    @DisplayName("updatePage：頁面不存在拋出 E_8004")
    void updatePage_notFound_throwsE8004() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cmsService.updatePage(PAGE_ID, CmsDto.UpdatePageRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8004);
    }

    @Test
    @DisplayName("DEF-032：updatePage 跨租戶竄改必須被拒絕（修復前為紅燈，修復後轉綠）")
    void updatePage_crossTenant_mustBeRejected() {
        TenantContext.setCurrentTenant(TENANT_B);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> cmsService.updatePage(PAGE_ID,
                CmsDto.UpdatePageRequest.builder().title("Hacked").build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(contentPageRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePage：admin 可跨租戶更新")
    void updatePage_admin_crossTenant_allowed() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_B);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));
        when(contentPageRepository.save(any(ContentPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.PageResponse response = cmsService.updatePage(PAGE_ID,
                CmsDto.UpdatePageRequest.builder().title("Admin Edit").build());

        assertThat(response.getTitle()).isEqualTo("Admin Edit");
    }

    // ========== publishPage ==========

    @Test
    @DisplayName("publishPage：本租戶可發布自己的頁面")
    void publishPage_sameTenant_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));
        when(contentPageRepository.save(any(ContentPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.PageResponse response = cmsService.publishPage(PAGE_ID);

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        assertThat(response.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("DEF-032：publishPage 跨租戶發布必須被拒絕（修復前為紅燈，修復後轉綠）")
    void publishPage_crossTenant_mustBeRejected() {
        TenantContext.setCurrentTenant(TENANT_B);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> cmsService.publishPage(PAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(contentPageRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishPage：admin 可跨租戶發布")
    void publishPage_admin_crossTenant_allowed() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_B);
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findById(PAGE_ID)).thenReturn(Optional.of(page));
        when(contentPageRepository.save(any(ContentPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.PageResponse response = cmsService.publishPage(PAGE_ID);

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
    }

    // ========== getPageBySlug（Sprint 82 DEF-034 修復：須帶 tenantId，比照 PostController 模式） ==========

    @Test
    @DisplayName("getPageBySlug：已發布頁面回傳成功")
    void getPageBySlug_published_success() {
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.PUBLISHED);
        when(contentPageRepository.findByTenantIdAndSlug(TENANT_A, "about-us")).thenReturn(Optional.of(page));

        CmsDto.PageResponse response = cmsService.getPageBySlug("about-us", TENANT_A);

        assertThat(response.getSlug()).isEqualTo("about-us");
    }

    @Test
    @DisplayName("getPageBySlug：頁面不存在拋出 E_8004")
    void getPageBySlug_notFound_throwsE8004() {
        when(contentPageRepository.findByTenantIdAndSlug(TENANT_A, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cmsService.getPageBySlug("missing", TENANT_A))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8004);
    }

    @Test
    @DisplayName("getPageBySlug：未發布頁面拋出 E_8004")
    void getPageBySlug_draft_throwsE8004() {
        ContentPage page = pageOf(TENANT_A, ContentPage.ContentStatus.DRAFT);
        when(contentPageRepository.findByTenantIdAndSlug(TENANT_A, "about-us")).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> cmsService.getPageBySlug("about-us", TENANT_A))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8004);
    }

    @Test
    @DisplayName("🔴 getPageBySlug：缺少 tenantId 拋出 E_1002")
    void getPageBySlug_missingTenantId_throwsE1002() {
        assertThatThrownBy(() -> cmsService.getPageBySlug("about-us", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1002);
        verify(contentPageRepository, never()).findByTenantIdAndSlug(any(), any());
    }

    @Test
    @DisplayName("🔴 getPageBySlug：他租戶同名 slug 不可見（不外洩跨租戶內容）")
    void getPageBySlug_crossTenantSlug_notFound() {
        when(contentPageRepository.findByTenantIdAndSlug(TENANT_B, "about-us")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cmsService.getPageBySlug("about-us", TENANT_B))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8004);
        verify(contentPageRepository, never()).findBySlug(any());
    }

    // ========== getPages（Admin 列表） ==========

    @Test
    @DisplayName("DEF-033：非 admin 呼叫 getPages 僅能看到本租戶頁面（修復前為紅燈，修復後轉綠）")
    void getPages_nonAdmin_onlyOwnTenant() {
        TenantContext.setCurrentTenant(TENANT_A);
        ContentPage ownPage = pageOf(TENANT_A, ContentPage.ContentStatus.PUBLISHED);
        when(contentPageRepository.findByTenantIdAndStatusOrderBySortOrderAsc(
                eq(TENANT_A), eq(ContentPage.ContentStatus.PUBLISHED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(ownPage)));

        CmsDto.PageListResponse response = cmsService.getPages(0, 20);

        assertThat(response.getPages()).hasSize(1);
        assertThat(response.getPages().get(0).getTenantId()).isEqualTo(TENANT_A);
        verify(contentPageRepository, never()).findByStatusOrderBySortOrderAsc(any(), any());
    }

    @Test
    @DisplayName("getPages：admin 可看到跨租戶頁面總覽")
    void getPages_admin_seesAllTenants() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_A);
        ContentPage otherTenantPage = pageOf(TENANT_B, ContentPage.ContentStatus.PUBLISHED);
        when(contentPageRepository.findByStatusOrderBySortOrderAsc(
                eq(ContentPage.ContentStatus.PUBLISHED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(otherTenantPage)));

        CmsDto.PageListResponse response = cmsService.getPages(0, 20);

        assertThat(response.getPages()).hasSize(1);
        assertThat(response.getPages().get(0).getTenantId()).isEqualTo(TENANT_B);
    }

    // ========== createBanner ==========

    @Test
    @DisplayName("createBanner：建立橫幅成功，tenantId 取自當前 context，狀態為 DRAFT")
    void createBanner_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.CreateBannerRequest request = CmsDto.CreateBannerRequest.builder()
                .title("Summer Sale").imageUrl("https://example.com/banner.png")
                .bannerType(CmsDto.BannerType.PROMOTION).position(CmsDto.BannerPosition.HOME_TOP)
                .build();

        CmsDto.BannerResponse response = cmsService.createBanner(request);

        assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getImpressionCount()).isZero();
        assertThat(response.getClickCount()).isZero();
    }

    // ========== updateBanner ==========

    @Test
    @DisplayName("updateBanner：本租戶可更新自己的橫幅")
    void updateBanner_sameTenant_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.BannerResponse response = cmsService.updateBanner(BANNER_ID,
                CmsDto.UpdateBannerRequest.builder().title("Winter Sale").build());

        assertThat(response.getTitle()).isEqualTo("Winter Sale");
    }

    @Test
    @DisplayName("updateBanner：橫幅不存在拋出 E_8005")
    void updateBanner_notFound_throwsE8005() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cmsService.updateBanner(BANNER_ID, CmsDto.UpdateBannerRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8005);
    }

    @Test
    @DisplayName("DEF-032：updateBanner 跨租戶竄改必須被拒絕（修復前為紅燈，修復後轉綠）")
    void updateBanner_crossTenant_mustBeRejected() {
        TenantContext.setCurrentTenant(TENANT_B);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));

        assertThatThrownBy(() -> cmsService.updateBanner(BANNER_ID,
                CmsDto.UpdateBannerRequest.builder().title("Hacked").build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(bannerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBanner：admin 可跨租戶更新")
    void updateBanner_admin_crossTenant_allowed() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_B);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.BannerResponse response = cmsService.updateBanner(BANNER_ID,
                CmsDto.UpdateBannerRequest.builder().title("Admin Edit").build());

        assertThat(response.getTitle()).isEqualTo("Admin Edit");
    }

    // ========== publishBanner ==========

    @Test
    @DisplayName("publishBanner：本租戶可發布自己的橫幅")
    void publishBanner_sameTenant_success() {
        TenantContext.setCurrentTenant(TENANT_A);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.BannerResponse response = cmsService.publishBanner(BANNER_ID);

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("DEF-032：publishBanner 跨租戶發布必須被拒絕（修復前為紅燈，修復後轉綠）")
    void publishBanner_crossTenant_mustBeRejected() {
        TenantContext.setCurrentTenant(TENANT_B);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));

        assertThatThrownBy(() -> cmsService.publishBanner(BANNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(bannerRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishBanner：admin 可跨租戶發布")
    void publishBanner_admin_crossTenant_allowed() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_B);
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.DRAFT);
        when(bannerRepository.findById(BANNER_ID)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsDto.BannerResponse response = cmsService.publishBanner(BANNER_ID);

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
    }

    // ========== getBanners（Admin 列表） ==========

    @Test
    @DisplayName("DEF-033：非 admin 呼叫 getBanners 僅能看到本租戶橫幅（修復前為紅燈，修復後轉綠）")
    void getBanners_nonAdmin_onlyOwnTenant() {
        TenantContext.setCurrentTenant(TENANT_A);
        Banner ownBanner = bannerOf(TENANT_A, Banner.BannerStatus.PUBLISHED);
        when(bannerRepository.findByTenantIdAndStatus(TENANT_A, Banner.BannerStatus.PUBLISHED))
                .thenReturn(List.of(ownBanner));

        List<CmsDto.BannerResponse> response = cmsService.getBanners();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getTenantId()).isEqualTo(TENANT_A);
        verify(bannerRepository, never()).findByStatusOrderBySortOrderAsc(any(), any());
    }

    @Test
    @DisplayName("getBanners：admin 可看到跨租戶橫幅總覽")
    void getBanners_admin_seesAllTenants() {
        asAdmin();
        TenantContext.setCurrentTenant(TENANT_A);
        Banner otherTenantBanner = bannerOf(TENANT_B, Banner.BannerStatus.PUBLISHED);
        when(bannerRepository.findByStatusOrderBySortOrderAsc(eq(Banner.BannerStatus.PUBLISHED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(otherTenantBanner)));

        List<CmsDto.BannerResponse> response = cmsService.getBanners();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getTenantId()).isEqualTo(TENANT_B);
    }

    // ========== getActiveBanners（Sprint 82 DEF-034 修復：須帶 tenantId） ==========

    @Test
    @DisplayName("getActiveBanners：指定 position 依 position + tenantId 查詢")
    void getActiveBanners_withPosition() {
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.PUBLISHED);
        when(bannerRepository.findActiveByPositionAndTenantId(
                eq(Banner.BannerPosition.HOME_TOP), eq(TENANT_A), any(LocalDate.class)))
                .thenReturn(List.of(banner));

        CmsDto.BannerListResponse response = cmsService.getActiveBanners(CmsDto.BannerPosition.HOME_TOP, TENANT_A);

        assertThat(response.getBanners()).hasSize(1);
        assertThat(response.getPosition()).isEqualTo("HOME_TOP");
        verify(bannerRepository, never()).findAllActiveByTenantId(any(), any());
    }

    @Test
    @DisplayName("getActiveBanners：未指定 position 查詢本租戶全部活躍橫幅")
    void getActiveBanners_withoutPosition_queriesAll() {
        Banner banner = bannerOf(TENANT_A, Banner.BannerStatus.PUBLISHED);
        when(bannerRepository.findAllActiveByTenantId(eq(TENANT_A), any(LocalDate.class))).thenReturn(List.of(banner));

        CmsDto.BannerListResponse response = cmsService.getActiveBanners(null, TENANT_A);

        assertThat(response.getBanners()).hasSize(1);
        assertThat(response.getPosition()).isNull();
    }

    @Test
    @DisplayName("🔴 getActiveBanners：缺少 tenantId 拋出 E_1002")
    void getActiveBanners_missingTenantId_throwsE1002() {
        assertThatThrownBy(() -> cmsService.getActiveBanners(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1002);
        verify(bannerRepository, never()).findAllActiveByTenantId(any(), any());
    }

    @Test
    @DisplayName("🔴 getActiveBanners：他租戶橫幅不會混入本租戶查詢結果")
    void getActiveBanners_crossTenant_notLeaked() {
        when(bannerRepository.findAllActiveByTenantId(eq(TENANT_B), any(LocalDate.class))).thenReturn(List.of());

        CmsDto.BannerListResponse response = cmsService.getActiveBanners(null, TENANT_B);

        assertThat(response.getBanners()).isEmpty();
        verify(bannerRepository, never()).findAllActive(any());
    }

    // ========== recordBannerClick（Sprint 82 DEF-034 修復：須帶 tenantId） ==========

    @Test
    @DisplayName("recordBannerClick：呼叫 repository 遞增本租戶點擊次數")
    void recordBannerClick_incrementsCount() {
        cmsService.recordBannerClick(BANNER_ID, TENANT_A);

        verify(bannerRepository).incrementClickCountForTenant(BANNER_ID, TENANT_A);
    }

    @Test
    @DisplayName("🔴 recordBannerClick：缺少 tenantId 拋出 E_1002")
    void recordBannerClick_missingTenantId_throwsE1002() {
        assertThatThrownBy(() -> cmsService.recordBannerClick(BANNER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1002);
        verify(bannerRepository, never()).incrementClickCountForTenant(any(), any());
    }
}
