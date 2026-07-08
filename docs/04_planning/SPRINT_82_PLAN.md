# Sprint 82 Plan：DEF-034 CMS 公開端點租戶範圍修復

**Sprint**: Sprint 82
**期間**: 2026-07-08
**主軸**: 承接 `DEFERRED_ITEMS_TRACKER.md` 記錄的 DEF-034，使用者已拍板業務決策

---

## 背景

`CmsService.getPageBySlug`/`getActiveBanners`/`recordBannerClick`（`/v2/cms/pages/{slug}`、`/v2/cms/banners/active`、`/v2/cms/banners/{id}/click`）完全不做租戶過濾。動手前重新探查程式碼確認：

- 現況與追蹤器原始記錄有出入：這三個端點目前**沒有** `SecurityConfig` 的 `permitAll()`，實際上落入 `.anyRequest().authenticated()`——需要登入才能呼叫，但登入後任何租戶的使用者都會看到混在一起的所有租戶內容。
- 前端目前**完全沒有任何地方**呼叫這三個端點，是尚未真正投入使用的功能缺口，修改零風險（不會破壞現有呼叫端）。

## 業務決策（使用者已確認）

這些 CMS 頁面/橫幅的產品定位是**訪客可瀏覽的公開行銷內容**（如首頁橫幅、關於我們頁），比照既有的 `PostController`/`PostService.getPublishedPostBySlug` 前例（`/v2/posts`，同屬 CMS 領域已採用「呼叫端明確傳入 `tenantId` query 參數」模式）：

1. `SecurityConfig` 新增 `permitAll()`（比照 `/v2/posts` 模式，免登入）。
2. Controller/Service 簽名新增必填 `tenantId` 參數，比照 `PostService.getPublishedPostBySlug(slug, tenantId)` 的 `if (tenantId == null) throw E_1002` 模式。

## 變更點

- `ContentPageRepository` 新增 `findByTenantIdAndSlug(tenantId, slug)`。
- `BannerRepository` 新增 `findActiveByPositionAndTenantId`/`findAllActiveByTenantId`（沿用既有 `findActiveByPosition`/`findAllActive` 的 JPQL 條件 + tenantId）、`incrementClickCountForTenant`（`UPDATE ... WHERE id=:id AND tenant_id=:tenantId`，跨租戶點擊靜默無效果，維持原本「不存在也不報錯」的寬鬆語意，只是加上租戶邊界）。
- `CmsService.getPageBySlug(slug, tenantId)`/`getActiveBanners(position, tenantId)`/`recordBannerClick(bannerId, tenantId)`：新增 `tenantId` 參數 + null 檢查（`E_1002`），改用租戶過濾查詢。
- `CmsController` 三個端點新增 `@RequestParam UUID tenantId`（無 `defaultValue`，比照 `PostController` 必填模式）。
- `SecurityConfig` 新增 3 條 `permitAll()`：`/v2/cms/pages/*`、`/v2/cms/banners/active`、`/v2/cms/banners/*/click`。

## 測試

- `CmsServiceTest` 新增：`getPageBySlug`/`getActiveBanners`/`recordBannerClick` 缺少 `tenantId` 拋 `E_1002`；跨租戶查詢查無結果（不外洩他租戶內容）；本租戶查詢成功。
- 全量回歸 `mvn verify -Pintegration-test`（因涉及 `SecurityConfig` 全站授權規則變更）+ `make validate-schema`（無 migration，純查詢邏輯與簽名變更）。

## 風險與範圍界線

- 零前端呼叫端，不影響任何現有功能。
- 不涉及金流或使用者資料寫入，push 前不需要額外徵詢（使用者已於 Sprint 81 授權金流/安全相關 commit 完成後直接 push，本次性質更輕）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
