# API 規格文件 - M15 內容管理系統 / API Specification - M15 CMS

> **文件編號**: API-M15
> **文件版本**: v1.0（Sprint 277，首次建立；首次有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-11-11（Sprint 338：貼文分類名稱同店不可重複（忽略大小寫與前後空白）——`POST`／`PUT /v2/dashboard/post-categories` 同名回 409，新錯誤碼 `E-8021`；分類網址代稱撞到改為自動加隨機後綴、建立成功，`E-8014` 不再由任何端點回傳（DEF-492，使用者以互動選項拍板「擋下並提示」）。見 §4.22～4.25、§7）。2026-11-11（Sprint 337：公開貼文三個端點 `GET /v2/posts`／`/v2/posts/{slug}`／`/v2/posts/categories` 只供應 `ACTIVE` 店鋪，非 `ACTIVE`（審核中、被拒、停權、終止）或不存在一律 404 `E-2000`（DEF-485，使用者以互動選項拍板）；店鋪前台貼文列表新增公開版分類篩選 `?category=`，文章頁的分類徽章改連過去（DEF-486，前端；後端 API 沒有新增或變更）。見 §4.19～4.21、§7）。2026-11-10（Sprint 336：新增 `PublicMediaController` 與素材「公開開關」（`isPublic`，預設私有；`GET /v2/public/media/{assetId}` 匿名讀取）；`GET /v2/posts/{slug}` 新增 `countView` 參數、文章回應新增 `tenantSlug`，文章的公開網址改為 `/stores/{店鋪}/blog/{文章}`）。2026-11-09（Sprint 334：移除 `PostController` 的 5 個 `/v2/dashboard/media/*` 舊媒體端點，媒體只剩 M18 一套；M18 媒體權限對齊貼文寫入權並新增「被貼文封面圖使用就不能刪」；已發布貼文須先下架才能刪；貼文／分類 slug 不再為空字串、標題沒改不換 slug。Sprint 332：前端 /cms 系列併入 /dashboard）
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`CmsController`／`PostController`／`MediaController`／`MediaCategoryController`（`api/controller/`）、`CmsService`（`core/cms/`）、`PostService`／`PostCategoryService`（`core/cms/post/`）、`ListingCardService`（`core/cms/listing/`）、`MediaService`（`core/media/`，媒體庫，M18；Sprint 334 起是唯一的媒體 Service，原 `core/cms/media/MediaService` 已隨舊端點移除）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[CmsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/CmsServiceTest.java)、[PostServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostServiceTest.java)、[PostCategoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostCategoryServiceTest.java)、[ListingCardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/listing/ListingCardServiceTest.java)、[core/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/media/MediaServiceTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M15 章節，PRD §6.6（CMS 內容管理系統）為主要依據
> **⚠️ 本文件範圍最重要的前情提要**：本模組實際上是**四個互相獨立、部分重疊的子系統**，分散在四個 Controller，沒有共用的資料模型或 Service；§1.2 第 1、2 點是閱讀本文件前必讀的架構說明

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M15 |
| **模組名稱** | 內容管理系統 / CMS |
| **描述** | 平台靜態頁面與橫幅（`CmsController`）、店鋪部落格貼文與分類（`PostController`）、商品/房源嵌入卡片（`PostController` 一端點）、通用媒體庫與分類（`MediaController`／`MediaCategoryController`；Sprint 334 起貼文也用這一套）、公開素材的匿名讀取（`PublicMediaController`，Sprint 336） |
| **API 前綴** | `/api/v2/cms/...`（`CmsController`）、`/api/v2/dashboard/posts|post-categories/...`／`/api/v2/posts/...`／`/api/v2/admin/posts/...`／`/api/v2/listings/{id}/card`（`PostController`，前綴不統一，見 §1.2）、`/api/v2/media/...`（`MediaController`／`MediaCategoryController`）、`/api/v2/public/media/...`（`PublicMediaController`） |
| **多租戶隔離** | `CmsController`／`MediaController`／`MediaCategoryController`／`PostController` 四者皆直接使用 `TenantContext.getCurrentTenant()`（`PostController` 原自行重新實作一套反射式租戶解析，已於 Sprint 280／DEF-386 移除，見 §7） |

### 1.2 設計重點（本模組最重要的部分，請務必先讀）

1. **`PostController` 的租戶解析已統一（DEF-386，Sprint 280 修復）**：原先 `getTenantIdFromUser` 不直接呼叫 `TenantContext.getCurrentTenant()`，而是反查 `TenantMemberRepository`／`UserRepository`、失敗則反射挖 `UserDetails`、最終 fallback 寫死的 mock 租戶 UUID；現已全部移除，12 個端點方法改為直接呼叫 `TenantContext.getCurrentTenant()`／`getCurrentUser()`，與其餘三個 Controller 一致。
2. **媒體庫現在只有一套（M18：`MediaController`／`MediaCategoryController` → `core.media.MediaService`，`/v2/media/*`）**。
   - **【Sprint 334（DEF-474）】**原本 `PostController` 還內嵌另一套「部落格媒體」（`/v2/dashboard/media/*` 共 5 個端點，委派給 `core.cms.media.MediaService`），與 M18 讀寫**同一張 `media_assets` 表**但 DTO／驗證／錯誤碼／權限風格都不同。Sprint 332（DEF-466）合併前端後，這 5 個端點沒有任何前端呼叫者；使用者於 Sprint 334 決定**移除**它們（連同 `core.cms.media.MediaService`／`MediaUploadService`、`M15Dto` 的三個媒體 DTO 與專屬測試）。`core.cms.media.MediaValidationService` 保留——M18 的 `MediaService` 仍用它做檔案大小／類型驗證。
   - **權限對齊貼文寫入權（Sprint 334 使用者拍板）**：合併前走 M15 的角色判斷，`STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST` 都能上傳與刪圖；合併後只走 M18 的 `media:*` 權限，員工只剩讀取、`SELLER`／`HOST` 完全沒有——能寫貼文卻不能傳圖。現在 `RolePermissionMapping` 讓 `STORE_STAFF`／`SELLER`／`HOST` 也擁有 `media:read`／`create`／`update`／`delete`（與 `STORE_OWNER` 相同），並由 `RolePermissionMappingTest.everyRoleThatCanWritePosts_canManageMedia` 釘住「所有有 `cms:create` 的角色都要有四項 `media:*`」。這取代 Sprint 129（DEF-075）當時「員工對 media 唯讀」的決定。
   - **刪除前檢查封面圖引用（Sprint 334）**：M18 的刪除是軟刪除，原本完全不檢查引用，被貼文當封面圖的素材一刪，貼文的圖就悄悄壞掉（舊的 M15 刪除有這個保護）。現在 `deleteAsset` 先檢查本店鋪是否有貼文的 `featured_image_url` 含該素材的**編號**（M18 的檔案網址是 `/api/v2/media/files/{編號}`）或**儲存路徑**，有就回 409 `E-8020`、不刪除。只看封面圖，不掃貼文內文（見 §7）。
3. **（已移除）`PostController` 內嵌的「部落格媒體」上傳**：見上一點。§2.2 的 API-M15-026～030 與 §4.26～4.30 保留編號但標示為已移除。
4. **CMS 頁面／橫幅（`CmsController`）Javadoc 標「(Admin)」但實際權限比字面寬**：`@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:create')")` 這類寫法，而 `cms:create`／`cms:update`／`cms:read` 持有者包含 `SELLER`／`HOST`／`STORE_OWNER`／`STORE_STAFF`（見 `RolePermissionMapping`），`cms:publish` 則只有 `STORE_OWNER`／`ADMIN`。**實際語意是「任何店鋪角色管理自己租戶的頁面/橫幅，`SUPER_ADMIN` 亦可」**，不是字面註解暗示的「僅限平台管理員」；`CmsService` 內部一律用 `TenantContext.getCurrentTenant()` 隔離，不是全平台共用內容。
5. **錯誤碼精確性曾是本模組的系統性弱點，已於 Sprint 278（DEF-388）修復**：原至少 4 類錯誤碼被跨領域誤用，包括已存在專屬碼卻未採用的情況（`E-3006`／`E-4103` 原已定義但 `core.media.MediaService` 從未使用，現已改用），詳見 §6／§7。
6. **貼文發布有配額限制**：`AppConstants.QUOTA_MAX_POSTS`（PRD §4.4），`publishPost` 與 `createPost`（當 `autoPublish=true`）都會在發布前檢查已發布貼文數是否達上限，超過則拋例外（配額值與對應錯誤碼屬 `FeatureToggleService` 範圍，非本文件新增內容，見既有實作）。
7. **貼文內容的嵌入語法與安全檢查**：Markdown 語法 `{{embed:listing:<uuid>}}`，解析時驗證格式、不重複、listing 存在且 `ACTIVE`（格式錯/不存在/非 ACTIVE 皆靜默跳過不阻斷存檔，僅重複嵌入會報錯）；`title`／`content` 另外偵測 `<script>`/`<iframe>`/事件屬性/`javascript:` 等危險標記，偵測到即拒絕存檔（不做任何改寫消毒，純粹拒絕）。
8. **瀏覽次數遞增不可標 `readOnly`**：`getPublishedPostBySlug` 因為要原子遞增 `viewCount`，方法本身不能是 `@Transactional(readOnly = true)`（唯讀交易下 Hibernate `FlushMode.MANUAL` 會讓遞增永遠不 flush，Sprint 106 紅燈實測證實）。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| PRD §6.6.1 | 平台管理頁面與橫幅（實際任何店鋪角色皆可管理自己租戶內容） | §4.1～4.11（`CmsController`） | ✅ 已實作 |
| PRD §6.6.2～6.6.3 | 店家撰寫/發布部落格貼文，可嵌入商品/房源卡片 | §4.12～4.21（`PostController` 貼文部分） | ✅ 已實作 |
| （無對應 US） | 部落格貼文分類管理 | §4.22～4.25 | ✅ 已實作 |
| （無對應 US） | 部落格專用媒體上傳 | §4.26～4.30 | ➖ 已於 Sprint 334 移除（媒體只剩 §4.35～4.42 一套） |
| （無對應 US） | 平台管理員管理任意店鋪貼文 | §4.31～4.33 | ✅ 已實作 |
| （無對應 US） | 嵌入卡片資訊查詢（供部落格渲染） | §4.34 | ✅ 已實作 |
| （無對應 US） | 通用媒體庫（上傳/查詢/更新/刪除；貼文插圖也用這一套） | §4.35～4.42 | ✅ 已實作 |
| （無對應 US） | 獨立媒體分類管理（支援子分類） | §4.43～4.48 | ✅ 已實作 |

FRD 完全無 M15 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

### 2.1 CmsController（`/v2/cms`）—— 頁面與橫幅

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-001 | POST | `/v2/cms/pages` | 建立頁面 | `SUPER_ADMIN` 或 `cms:create` |
| API-M15-002 | PUT | `/v2/cms/pages/{pageId}` | 更新頁面 | `SUPER_ADMIN` 或 `cms:update` |
| API-M15-003 | POST | `/v2/cms/pages/{pageId}/publish` | 發布頁面 | `SUPER_ADMIN` 或 `cms:publish` |
| API-M15-004 | GET | `/v2/cms/pages` | 頁面列表 | `SUPER_ADMIN` 或 `cms:read` |
| API-M15-005 | GET | `/v2/cms/pages/{slug}` | 依 slug 查頁面（公開，需帶 `tenantId`） | 公開 |
| API-M15-006 | POST | `/v2/cms/banners` | 建立橫幅 | `SUPER_ADMIN` 或 `cms:create` |
| API-M15-007 | PUT | `/v2/cms/banners/{bannerId}` | 更新橫幅 | `SUPER_ADMIN` 或 `cms:update` |
| API-M15-008 | POST | `/v2/cms/banners/{bannerId}/publish` | 發布橫幅 | `SUPER_ADMIN` 或 `cms:publish` |
| API-M15-009 | GET | `/v2/cms/banners` | 橫幅列表 | `SUPER_ADMIN` 或 `cms:read` |
| API-M15-010 | GET | `/v2/cms/banners/active` | 活躍橫幅（公開，需帶 `tenantId`） | 公開 |
| API-M15-011 | POST | `/v2/cms/banners/{bannerId}/click` | 記錄橫幅點擊（公開，需帶 `tenantId`） | 公開 |

### 2.2 PostController（`/v2`）—— 貼文、分類、嵌入卡片

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-012 | POST | `/v2/dashboard/posts` | 建立貼文 | `STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST` |
| API-M15-013 | PUT | `/v2/dashboard/posts/{postId}` | 更新貼文 | 同上 |
| API-M15-014 | GET | `/v2/dashboard/posts` | 貼文列表（可依狀態篩選） | 同上 |
| API-M15-015 | GET | `/v2/dashboard/posts/{postId}` | 貼文詳情 | 同上 |
| API-M15-016 | DELETE | `/v2/dashboard/posts/{postId}` | 刪除貼文（**已發布須先下架**，否則 409 `E-4107`；Sprint 334） | 同上 |
| API-M15-017 | POST | `/v2/dashboard/posts/{postId}/publish` | 發布貼文 | 同上 |
| API-M15-018 | DELETE | `/v2/dashboard/posts/{postId}/publish` | 下架貼文 | 同上 |
| API-M15-019 | GET | `/v2/posts` | 已發布貼文列表（公開） | 公開 |
| API-M15-020 | GET | `/v2/posts/{slug}` | 依 slug 查已發布貼文（公開，需帶 `tenantId`；`countView=false` 時不計瀏覽，Sprint 336） | 公開 |
| API-M15-021 | GET | `/v2/posts/categories` | 公開分類列表（僅啟用中） | 公開 |
| API-M15-022 | GET | `/v2/dashboard/post-categories` | 分類列表（Dashboard，含停用） | `STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST` |
| API-M15-023 | POST | `/v2/dashboard/post-categories` | 建立分類 | 同上 |
| API-M15-024 | PUT | `/v2/dashboard/post-categories/{categoryId}` | 更新分類 | 同上 |
| API-M15-025 | DELETE | `/v2/dashboard/post-categories/{categoryId}` | 刪除分類（無貼文關聯時） | 同上 |
| API-M15-026～030 | — | （已移除） | Sprint 334（DEF-474）移除的 5 個 `/v2/dashboard/media/*` 部落格媒體端點；編號保留不重排 | — |
| API-M15-031 | GET | `/v2/admin/posts` | 平台查全部店鋪貼文（需帶 `tenantId`） | `SUPER_ADMIN` |
| API-M15-032 | DELETE | `/v2/admin/posts/{postId}/unpublish` | 平台下架任意店鋪貼文 | `SUPER_ADMIN` |
| API-M15-033 | DELETE | `/v2/admin/posts/{postId}` | 平台刪除任意店鋪貼文（同樣要先下架：與 API-M15-016 共用 `PostService.deletePost`；可先用 API-M15-032 下架） | `SUPER_ADMIN` |
| API-M15-034 | GET | `/v2/listings/{listingId}/card` | 嵌入卡片資訊（公開） | 公開 |

### 2.3 MediaController（`/v2/media`）—— 媒體庫

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-035 | GET | `/v2/media` | 媒體列表（分頁/篩選） | `media:read` |
| API-M15-036 | GET | `/v2/media/{assetId}` | 媒體詳情 | `media:read` |
| API-M15-037 | POST | `/v2/media/upload` | 上傳媒體（登記既有路徑） | `media:create` |
| API-M15-038 | POST | `/v2/media/upload-multipart` | 上傳媒體（實際檔案） | `media:create` |
| API-M15-039 | GET | `/v2/media/files/{assetId}` | 媒體檔案串流 | `media:read` |
| API-M15-040 | PUT | `/v2/media/{assetId}` | 更新媒體（含公開開關 `isPublic`，Sprint 336） | `media:update` |
| API-M15-041 | DELETE | `/v2/media/{assetId}` | 刪除媒體（軟刪除；**被本店鋪貼文當封面圖使用時回 409 `E-8020`**，Sprint 334） | `media:delete` |
| API-M15-042 | GET | `/v2/media/count` | 媒體總數 | `media:read` |

### 2.4 MediaCategoryController（`/v2/media/categories`）—— 媒體分類

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-043 | GET | `/v2/media/categories` | 分類列表 | `media:read` |
| API-M15-044 | GET | `/v2/media/categories/root` | 根分類列表 | `media:read` |
| API-M15-045 | GET | `/v2/media/categories/{categoryId}` | 分類詳情 | `media:read` |
| API-M15-046 | POST | `/v2/media/categories` | 建立分類（可指定父分類） | `media:create` |
| API-M15-047 | PUT | `/v2/media/categories/{categoryId}` | 更新分類 | `media:update` |
| API-M15-048 | DELETE | `/v2/media/categories/{categoryId}` | 刪除分類（無子分類/媒體時） | `media:delete` |

### 2.5 PublicMediaController（`/v2/public/media`）—— 公開素材（Sprint 336，DEF-482）

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-049 | GET | `/v2/public/media/{assetId}` | 匿名讀取「公開」素材的檔案內容（串流）；私有、已刪除、店鋪非 `ACTIVE` 一律 404 | 公開 |

共 44 個端點，橫跨 5 個 Controller，全數納入 `ApiRouteDocDriftTest.GUARDED`（Sprint 336 以工具重數：概覽表 44 列、「端點」宣告行 44 行。此句先前寫 48，Sprint 334 移除 5 個端點後沒有更新，Sprint 336 新增 1 個後應為 44）。

---

## 3. 資料模型 / Data Model

### 3.1 `ContentPage`／`Banner`（`CmsController`）

| 欄位 | 說明 |
|------|------|
| Page：`id`／`tenantId`／`title`／`slug`／`content`／`metadata`（Map）／`status`（`DRAFT`/`PUBLISHED`） | `slug` 全站（非租戶範圍）唯一，重複回 `E-8013`（Sprint 278 起，見 §7） |
| Banner：`id`／`tenantId`／`title`／`imageUrl`／`linkUrl`／`position`（`BannerPosition`）／`status`／`clickCount`／`startDate`／`endDate` | `imageUrl`／`linkUrl` 皆有 `javascript:`/`data:`/`vbscript:`/`file:` protocol 黑名單驗證 |
| `BannerPosition` 列舉 | `HOME_TOP`／`HOME_MIDDLE`／`HOME_BOTTOM`／`LISTING_PAGE`／`PRODUCT_PAGE`／`CHECKOUT_PAGE` |

### 3.2 `Post`／`PostCategory`／`PostEmbed`（`PostController`）

| 欄位 | 說明 |
|------|------|
| Post：`id`／`tenant`／`author`／`title`／`slug`／`content`／`excerpt`（自動產生，Markdown 語法剝除後前 500 字）／`featuredImageUrl`／`tags[]`／`status`／`viewCount`／`category`／`publishedAt` | `slug` 全站唯一（非租戶範圍），自動依標題產生，衝突時附加 8 位隨機字串 |
| `Post.PostStatus` | `DRAFT`／`PUBLISHED`／`ARCHIVED` |
| PostCategory：`id`／`tenant`／`name`／`slug`／`description`／`sortOrder`／`isActive` | `name` 無 `@NotBlank` 驗證（見 §7），`slug` 租戶範圍內唯一 |
| PostEmbed：`id`／`post`／`listingId`／`listingType`／`embedOrder` | 解析自 `content` 的 `{{embed:listing:uuid}}` 語法，見 §1.2 第 7 點 |

### 3.3 `MediaAsset`（`domain.model.cms.media.MediaAsset`，§1.2 第 2 點所稱「同一張表」）

| 欄位 | 說明 |
|------|------|
| `id`／`tenant`／`uploader`／`category`／`fileName`／`originalName`／`filePath`／`fileSize`／`mimeType`／`fileType`（`IMAGE`/`VIDEO`/`DOCUMENT`）／`tags[]`／`altText`／`title`／`usageCount`／`isDeleted`／`isPublic` | `core.cms.media.MediaService` 與 `core.media.MediaService` 皆讀寫此實體，但走不同的 Repository 查詢方法與不同的 DTO（`M15Dto.MediaResponse` vs `api.dto.media.MediaAssetDto`）。**`isPublic`（Sprint 336，V98）**：`NOT NULL`，預設 `false`（私有）；`true` 才能由匿名端點 `GET /v2/public/media/{assetId}`（§4.49）讀取，既有素材全部回填為私有 |

### 3.4 `MediaCategory`（`domain.model.media.MediaCategory`，僅 `MediaCategoryController` 使用）

| 欄位 | 說明 |
|------|------|
| `id`／`tenantId`／`name`／`description`／`parent`／`sortOrder`／`children[]`（回應時遞迴載入） | 支援父子階層；刪除前檢查無子分類、無關聯媒體 |

**注意**：`PostCategory`（§3.2，部落格分類）與 `MediaCategory`（本節，媒體分類）是完全不同的兩張表，互不相關，恰好都叫「分類」。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1～4.5 頁面管理（`CmsController`）

- **端點**: `POST /v2/cms/pages`
- **端點**: `PUT /v2/cms/pages/{pageId}`
- **端點**: `POST /v2/cms/pages/{pageId}/publish`
- **端點**: `GET /v2/cms/pages`
- **端點**: `GET /v2/cms/pages/{slug}`

**權限**：建立/更新/發布/列表依 §2.1；`getPageBySlug`（§4.5）公開，但**必須**帶 `tenantId` 查詢參數。

**請求（建立）**：`title`（必填，無驗證註解僅 Service 層檢查）、`slug`（必填，全站唯一）、`content`／`metadata`（選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `slug` 重複 | 409 | `E-8013`（Sprint 278／DEF-388 新增專屬碼，修復前誤用「缺少必填欄位」`E-9005`，見 §7） |
| 頁面不存在 | 404 | `E-8004` |
| 頁面未發布（§4.5 查詢未發布頁面） | 404 | `E-8016`（Sprint 278／DEF-388 新增專屬碼，修復前與「頁面不存在」共用 `E-8004`，見 §7） |
| §4.5 缺少 `tenantId` | 400 | `E-9005`（Sprint 278／DEF-388 修復前誤用「憑證已過期」`E-1002`，見 §7） |
| 非本租戶操作他租戶頁面 | 403 | `E-1007` |

### 4.6～4.11 橫幅管理（`CmsController`）

- **端點**: `POST /v2/cms/banners`
- **端點**: `PUT /v2/cms/banners/{bannerId}`
- **端點**: `POST /v2/cms/banners/{bannerId}/publish`
- **端點**: `GET /v2/cms/banners`
- **端點**: `GET /v2/cms/banners/active`
- **端點**: `POST /v2/cms/banners/{bannerId}/click`

**權限**：同頁面管理。`getActiveBanners`（§4.10）`position` 選填篩選；`recordBannerClick`（§4.11）無回應內容，原子遞增 `clickCount`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `imageUrl`（必填）缺失或使用黑名單 protocol | 400 | `E-9000` |
| 橫幅不存在 | 404 | `E-8005` |
| §4.10／4.11 缺少 `tenantId` | 400 | `E-9005`（同上） |

### 4.12～4.18 貼文 CRUD／發布（`PostController`）

- **端點**: `POST /v2/dashboard/posts`
- **端點**: `PUT /v2/dashboard/posts/{postId}`
- **端點**: `GET /v2/dashboard/posts`
- **端點**: `GET /v2/dashboard/posts/{postId}`
- **端點**: `DELETE /v2/dashboard/posts/{postId}`
- **端點**: `POST /v2/dashboard/posts/{postId}/publish`
- **端點**: `DELETE /v2/dashboard/posts/{postId}/publish`

**權限**：`hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')`；呼叫前一律先經 §1.2 第 1 點的租戶解析（`TenantContext.getCurrentTenant()`，DEF-386 已修復），再檢查 `CMS_ENABLED` 功能開關。

**slug（Sprint 334，DEF-480）**：由標題產生——轉小寫、只保留英數字與連字號。清完為空（例如全中文標題）時改用 `post-<8 碼>` 後備，**絕不是空字串**（`posts.slug` 是 `NOT NULL UNIQUE`，空字串是合法值，過去第一篇全中文貼文的公開連結是 `/blog`、點不進去；V97 遷移把既有的空字串補成同格式）。更新時**只有標題真的改了才重新產生 slug**，標題照舊的儲存不換網址（過去每次儲存都會在網址後面再接一段隨機碼）。貼文分類同理（`category-<8 碼>` 後備，標題／名稱沒改不換 slug）。

**請求（建立/更新）**：`title`（必填，Service 層檢查）、`content`（選填，含嵌入語法解析）、`categoryId`（選填，須屬本租戶）、`tags[]`、`featuredImageUrl`（黑名單 protocol 驗證）、`autoPublish`（僅建立時，布林）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `title` 缺失或空白 | 400 | `E-9005`（此處用法正確） |
| `title`／`content` 含危險標記 | 400 | `E-9009` |
| 貼文不存在 | 404 | `E-4100` |
| 操作非本租戶的貼文 | 403 | `E-4031` |
| `categoryId` 不存在或非本租戶 | 404 | `E-4102` |
| 發布已發布中的貼文／下架未發布的貼文 | 409 | `E-4106` |
| 刪除已發布的貼文（含平台管理員刪除，§4.33） | 409 | `E-4107`（Sprint 334，DEF-476：須先下架；驗收條件 AC-007。被擋下時貼文不會被刪） |
| 發布數已達配額上限 | — | 見 `FeatureToggleService.checkQuotaNotExceeded`（既有實作，非本模組新增） |
| Feature Toggle `CMS_ENABLED` 未啟用 | 403 | `E-2004` |

### 4.19～4.21 公開貼文／分類（`PostController`）

- **端點**: `GET /v2/posts`
- **端點**: `GET /v2/posts/{slug}`
- **端點**: `GET /v2/posts/categories`

**權限**：公開，無需登入。`getPublishedPosts`／`getPublishedPostBySlug` 皆**必須**帶 `tenantId`。`getPublishedPostBySlug` 每次呼叫原子遞增 `viewCount`（見 §1.2 第 8 點）。

**`countView` 參數（`GET /v2/posts/{slug}`，Sprint 336，DEF-483）**：選填布林，預設 `true`（維持原行為，瀏覽數 +1）。傳 `false` 時整個請求沒有任何寫入，回應的 `viewCount` 是目前的值、不加一。給 Next 伺服器端組頁面標題與分享資訊用——一次真正的瀏覽，瀏覽器載入頁面會呼叫一次取全文（計一次），伺服器端為了標題再呼叫一次；若這次也計，每篇文章的瀏覽數會多算一倍，搜尋引擎與連結預覽機器人抓標題也會灌水。

**回應的 `tenantSlug`（Sprint 336，DEF-483）**：貼文回應（所有回傳 `PostResponse` 的端點）新增店鋪的網址代稱 `tenantSlug`。文章的公開網址是 `/stores/{tenantSlug}/blog/{slug}`（前端 `lib/article-path.ts`），列表頁靠它組連結。舊的 `/blog/{slug}` 網址由前端導向新網址（登入且選定店鋪的人；匿名訪客維持原本的登入提示）。

**三個端點都只供應 `ACTIVE` 店鋪（Sprint 337，DEF-485，使用者 2026-11-10 以互動選項拍板「全擋並回 404」）**：先確認 `tenantId` 對應的店鋪現在是 `ACTIVE`，再讀貼文或分類；審核中（`PENDING_REVIEW`）、被拒（`REJECTED`）、停權（`SUSPENDED`）、終止（`TERMINATED`）與「根本不存在」一律回同一個 404 `E-2000`，外人分不出是哪一種，也就不能拿這幾個端點探測店鋪狀態——與公開店鋪（`GET /v2/public/stores/{slug}`）、商品詳情（`GET /v2/listings/{id}`）、公開素材（`GET /v2/public/media/{assetId}`，§4.49）同一做法。讀的是店鋪**現在**的狀態（每次請求查一次主鍵，不快取）：停權後立刻 404，恢復 `ACTIVE` 後立刻又讀得到。非 `ACTIVE` 店鋪不會有任何寫入（單篇的瀏覽數不會增加）。規則在 `PostService`（列表、單篇）與 `PostCategoryService.getActiveCategories`（分類）。
修復前實測（Sprint 337 紅燈，真實 PostgreSQL＋真實 HTTP）：四種非 `ACTIVE` 狀態 × 五種請求形狀（列表、依分類篩選的列表、單篇、單篇 `countView=false`、公開分類）共 20 格全部回 200（不是 404）；失敗訊息裡可直接看到 `SUSPENDED` 店鋪的列表、單篇、公開分類的回應本文帶著貼文與分類資料，其餘格子走同一條程式路徑，沒有逐格檢視本文；不存在的店鋪，列表與分類回 200＋空清單、單篇回 404 `E-4100`（修復後三個端點都是 404 `E-2000`）。

**行為變更**：「不存在的店鋪」的 `GET /v2/posts`、`GET /v2/posts/categories` 原本回 200＋空清單，現在回 404 `E-2000`；單篇原本回 404 `E-4100`，現在回 404 `E-2000`（先查店鋪、再查文章）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 缺少 `tenantId` | 400 | `E-9005`（Sprint 278／DEF-388 修復前誤用「憑證已過期」`E-1002`，見 §7） |
| 店鋪不是 `ACTIVE` 或不存在（三個端點都一樣；Sprint 337，DEF-485） | 404 | `E-2000` |
| 貼文不存在（店鋪是 `ACTIVE`） | 404 | `E-4100` |
| 貼文存在但非 `PUBLISHED` | 409 | `E-4101` |

單篇的檢查順序：缺少 `tenantId`（`E-9005`）→ 店鋪不是 `ACTIVE`（`E-2000`）→ 貼文不存在（`E-4100`）→ 貼文非 `PUBLISHED`（`E-4101`）。

**分類篩選（前端，Sprint 337，DEF-486）**：`GET /v2/posts?categoryId=` 與 `GET /v2/posts/categories` 本來就是公開端點，店鋪前台貼文列表 `/stores/{店鋪}/blog?category={分類編號}` 直接使用（不新增端點）：先取 `GET /v2/posts/categories`（只含啟用中分類，後端不保證順序，前端依 `sortOrder` 再依名稱排）畫篩選列，分類編號對得上才送出 `GET /v2/posts?categoryId=&page=&size=12`。網址的分類編號對不上公開清單（亂填、已停用）時前端顯示「此分類不存在或已停用」，**不送出貼文請求**（後端對格式錯誤的編號會回 400）。分類目前沒有任何 API 能停用（`PostCategoryService` 只在建立時設 `isActive = true`），「已停用」只可能來自資料庫直接修改。

### 4.22～4.25 貼文分類管理（`PostController`）

- **端點**: `GET /v2/dashboard/post-categories`
- **端點**: `POST /v2/dashboard/post-categories`
- **端點**: `PUT /v2/dashboard/post-categories/{categoryId}`
- **端點**: `DELETE /v2/dashboard/post-categories/{categoryId}`

**權限**：同貼文管理。`createCategory`／`updateCategory` 的 `name` 欄位**無任何必填驗證**（DTO 無 `@NotBlank`，Service 層也不檢查，見 §7）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 分類不存在 | 404 | `E-4102`（官方定義「找不到分類」，此處正確） |
| 分類名稱與同店既有分類相同（`POST` 建立、`PUT` 改名；Sprint 338，DEF-492） | 409 | `E-8021`（忽略大小寫與前後空白；只比對同一家店；見下方「名稱與代稱」） |
| 更新/刪除非本租戶分類 | 403 | `E-4031` |
| 刪除有貼文關聯的分類 | 409 | `E-8015`（Sprint 278／DEF-388 新增專屬碼，修復前誤用「找不到分類」`E-4102`，見 §7） |

**名稱與代稱（Sprint 338，DEF-492，使用者 2026-11-11 以互動選項拍板「擋下並提示」）**

- **同店不可有同名分類**：`createCategory`、`updateCategory`（名稱**真的改了**才檢查，並排除分類自己）比對同一家店所有分類的名稱，**忽略大小寫與前後空白（含全形空白 U+3000）**，相同回 409 `E-8021`（訊息「分類名稱已存在」）。只比對名稱整串相同：包含、相近的不算同名（`美食`／`美食家`、`Food`／`Foods` 可並存）。名稱沒改的更新不檢查，所以規則上線前已經存在的同名分類（全中文同名原本能建立）仍能改描述與排序。別家店的同名分類互不影響。
- **網址代稱（`slug`）撞到不再失敗**：代稱由名稱產生（只留英數字與連字號，中文被濾掉；全中文名稱用 `category-<8碼>`）。名稱不同、只差中文的分類會產生同一個代稱（`旅遊筆記2026` 與 `咖啡手帖2026` 都是 `2026`），修復前第二個起 `createCategory` 直接回 409 `E-8014`，店主看到的是兩個名稱明明不同卻建不起來。現在撞到同店既有代稱時**自動接 `-<8碼隨機>` 後綴並建立成功**（`updateCategory` 本來就這樣處理）。**`E-8014` 因此不再由任何端點回傳**（錯誤碼保留，見 §7）。
- 修復前的現況不一致：英文同名會因代稱相同被擋，全中文同名（代稱是隨機後備）卻能建立；現在統一為「同名一律擋」。
- 前端（儀表板「貼文分類管理」）：儲存失敗時，`E-8021` 顯示「分類名稱已存在，請換一個名稱」，其他失敗仍是「儲存分類失敗，請稍後再試」。

### 4.26～4.30 部落格媒體（**已於 Sprint 334 移除**）

原本的 `GET /v2/dashboard/media`、`POST /v2/dashboard/media/upload-by-path`、`POST /v2/dashboard/media/upload-multipart`、`GET /v2/dashboard/media/{mediaId}/file`、`DELETE /v2/dashboard/media/{mediaId}` 五個端點已隨 DEF-474 移除（前端自 Sprint 332 起就沒有呼叫者）。請改用 §4.35～4.42 的 `/v2/media/*`。

### 4.31～4.33 平台管理貼文（`PostController`）

- **端點**: `GET /v2/admin/posts`
- **端點**: `DELETE /v2/admin/posts/{postId}/unpublish`
- **端點**: `DELETE /v2/admin/posts/{postId}`

**權限**：`hasRole('SUPER_ADMIN')`。**三個端點皆以查詢參數 `tenantId` 指定目標租戶，且完全不驗證租戶擁有權**（程式碼註解自承「Admin 可以操作任何店鋪的貼文，不驗證 tenant ownership」）——這是刻意設計（平台管理員本就該能跨租戶操作），非缺陷。

### 4.34 嵌入卡片資訊（`PostController` → `ListingCardService`）

- **端點**: `GET /v2/listings/{listingId}/card`

**權限**：公開。依 `listingId` 查詢，自動判斷 `PRODUCT`／`ROOM` 型別回傳對應卡片資訊（庫存/維護狀態/定價/CTA 連結），`MAINTENANCE` 中的房源無定價且 CTA 導向客服，下架/刪除的 listing 導向店鋪頁而非失效連結（PRD §6.6.3 Cascade 處理）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| Listing 不存在 | 404 | `E-3000` |
| `listingId` 格式有誤（路徑參數不是合法 UUID，例如 `abc`） | 400 | `E-9000`「請求參數格式錯誤」（Sprint 333 對真實後端實測；由 `GlobalExceptionHandler` 的型別轉換失敗統一處理） |
| Listing 型別非 `PRODUCT`/`ROOM`（理論上不可達） | 422 | `E-3001`（此處用法正確） |

**前端消費（Sprint 333，DEF-473）**：文章內嵌商品卡只有收到 404 或 400（上表前兩列）才顯示「此商品已下架」（PRD §6.6.3：已刪除或格式有誤）；其餘失敗顯示「載入商品卡片失敗」與重試。這個判準依賴上表的狀態碼，由真實後端 E2E（`at-load-failure-screens.spec.ts` 的 `E2E-LOADFAIL-01`）釘住。

**已知限制**：商品卡的庫存欄位（`inStock`／`availableQty`）目前前端未渲染（`ListingCardService` 原始碼註解自承），屬已修復資料來源（Sprint 116 DEF-066 同款修復）但尚無 UI 消費的欄位。

### 4.35～4.42 媒體庫（`MediaController` → `core.media.MediaService`）

- **端點**: `GET /v2/media`
- **端點**: `GET /v2/media/{assetId}`
- **端點**: `POST /v2/media/upload`
- **端點**: `POST /v2/media/upload-multipart`
- **端點**: `GET /v2/media/files/{assetId}`
- **端點**: `PUT /v2/media/{assetId}`
- **端點**: `DELETE /v2/media/{assetId}`
- **端點**: `GET /v2/media/count`

**權限**：`media:*` 系列（`STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST`／`ADMIN` 全部四權限皆有——Sprint 334 起店員與舊賣家、民宿主人與店主一致，對齊他們的貼文寫入權；買家與訪客沒有）。`uploadAsset`（§4.37）登記既有路徑，會驗證 `filePath` 確實屬於本租戶且物件真實存在於儲存層（DEF-101 IDOR 防護）；`uploadAssetMultipart`（§4.38）才是真正檔案上傳，含 magic-bytes 內容驗證（`mediaValidationService.validateActualContent`）。`downloadAsset`（§4.39 對應的 Service 方法）額外驗證 `filePath` 真的屬於本租戶儲存路徑（雙重 IDOR 防護，不只信任資料列的 `tenantId`）。建立/更新/刪除成功回應 HTTP **201**（建立）／200（其餘）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 媒體資產不存在 | 404 | `E-4103`（Sprint 278／DEF-388 修復前誤用房源碼 `E-4000`，見 §7） |
| 上傳/更新時指定的分類不存在 | 404 | `E-3006`（Sprint 278／DEF-388 修復前誤用 `E-4000`，見 §7） |
| 租戶不存在 | 404 | `E-2000` |
| 檔案路徑非法／上傳失敗 | 400 | `E-9000` |
| 刪除正被本店鋪貼文當封面圖使用的媒體（§4.41，Sprint 334） | 409 | `E-8020`（封面圖網址含素材編號或儲存路徑；被擋下時不會軟刪除。只檢查封面圖，貼文內文裡的圖不在檢查範圍，見 §7） |

**公開開關（Sprint 336，DEF-482）**：`PUT /v2/media/{assetId}`（§4.40）的請求新增選填的 `isPublic`（布林）——`true` 公開、`false` 撤回、**沒帶＝不變**（編輯替代文字、標題等其他欄位不會誤動公開狀態）。素材的回應（`MediaAssetDto`）新增 `isPublic`（一律有值，預設 `false`）與 `publicUrl`（只有公開的素材才有，`/api/v2/public/media/{assetId}`，含 context-path 的路徑，不含主機）。需要登入的 `url`（`/api/v2/media/files/{assetId}`，§4.39）不受影響，仍限本店鋪。切換權限與其他更新相同（`media:update`）。撤回公開**不會**檢查是否有貼文正在使用這個網址（與刪除不同，刪除會被 `E-8020` 擋下）——撤回的目的就是快速收回；使用中的頁面會顯示不出圖片，前端在確認視窗裡說明這件事。

### 4.49 公開素材（`PublicMediaController` → `core.media.PublicMediaService`，Sprint 336，DEF-482）

- **端點**: `GET /v2/public/media/{assetId}`

**權限**：公開，無需登入（`/v2/public/**` 在 `SecurityConfig` 本來就是 `permitAll`）。這是整個媒體模組**唯一有意不看呼叫者店鋪**的讀取路徑——匿名者沒有租戶，素材自己的店鋪才是依據，所以獨立成 `PublicMediaService`，不放進以呼叫者租戶為邊界的 `MediaService`（DEF-101 的跨租戶防護）。

**條件**（三個缺一就當作找不到）：素材 `isPublic = true`、`isDeleted = false`、所屬店鋪狀態是 `ACTIVE`；另外儲存路徑要屬於素材自己的店鋪（縱深防護，同 §4.39 的雙重檢查）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 私有、已刪除（軟刪除）、店鋪非 `ACTIVE`（審核中／被拒／停權／終止）、儲存路徑不屬於素材自己的店鋪、素材不存在 | 404 | `E-4103`（**一律同一個回應**，不區分原因，避免被拿來探測素材是否存在） |

**回應**：檔案內容串流；`Content-Type` 為素材的 MIME 類型（全域 `force-response` 設定會補上 `;charset=UTF-8`，見 §7）、`Content-Disposition: inline`、`Cache-Control: public, max-age=300`、`X-Content-Type-Options: nosniff`（Spring Security 預設標頭）。

**快取的代價**：公開圖片會被反覆載入，所以回 5 分鐘的公開快取（`PublicMediaController.CACHE_SECONDS`）。店主撤回公開或刪除素材之後，伺服器端立刻不再提供，但已經被瀏覽器或 CDN 快取的副本最多還看得到 5 分鐘。

### 4.43～4.48 媒體分類（`MediaCategoryController` → `core.media.MediaService`）

- **端點**: `GET /v2/media/categories`
- **端點**: `GET /v2/media/categories/root`
- **端點**: `GET /v2/media/categories/{categoryId}`
- **端點**: `POST /v2/media/categories`
- **端點**: `PUT /v2/media/categories/{categoryId}`
- **端點**: `DELETE /v2/media/categories/{categoryId}`

**權限**：同媒體庫。支援父子階層（`parentId` 選填），回應遞迴載入 `children[]`。併發建立同名分類有部分唯一索引＋`saveAndFlush`捕捉違反約束兜底（DEF-160）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 分類/父分類不存在 | 404 | `E-3006`（Sprint 278／DEF-388 修復前誤用 `E-4000`，見 §7） |
| 同層級分類名稱重複 | 409 | `E-8017`（Sprint 278／DEF-388 新增專屬碼，修復前誤用「無效的刊登類型」`E-3001`，見 §7） |
| 刪除有子分類的分類 | 409 | `E-8018`（Sprint 278／DEF-388 新增，修復前與下列情境共用 `E-3001`） |
| 刪除有媒體關聯的分類 | 409 | `E-8019`（Sprint 278／DEF-388 新增，修復前與上列情境共用 `E-3001`，呼叫端原本無法區分三種情境） |

---

## 5. 權限矩陣 / Permission Matrix

| 端點群組 | SELLER／HOST（無店鋪） | STORE_OWNER | STORE_STAFF | SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| CMS 頁面/橫幅管理（§4.1～4.3／4.6～4.8，寫入） | ✅（落入 §1.2 第 1 點的 CmsController 租戶解析） | ✅ | ✅（無 `cms:publish`） | ✅ |
| 頁面/橫幅列表（§4.4／4.9） | ✅ | ✅ | ✅ | ✅ |
| 貼文/分類（§4.12～4.25） | ✅（統一經 `TenantContext.getCurrentTenant()`，DEF-386 已修復，不再有假租戶 fallback） | ✅ | ✅ | ❌（角色不符，`SUPER_ADMIN` 不在 `hasAnyRole` 清單內） |
| 平台管理貼文（§4.31～4.33） | ❌ | ❌ | ❌ | ✅（跨租戶） |
| 媒體庫/分類查詢（§4.35／4.36／4.39／4.42～4.45） | ✅（Sprint 334 起；無店鋪的 `SELLER`／`HOST` 簽發 token 時已降為買家，所以實際上是「有店鋪」才成立） | ✅ | ✅ | ❌（`media:*` 與角色無關，`ADMIN`／`SUPER_ADMIN` 本身無此權限，`SUPER_ADMIN` 持有 `Permission.values()` 全集例外，見 `RolePermissionMapping.SUPER_ADMIN`） |
| 媒體庫/分類寫入（§4.37／4.38／4.40／4.41／4.46～4.48） | ✅（同上） | ✅ | ✅（Sprint 334 起，原本僅 `media:read`） | ✅（透過全權限集合） |

注意：**CMS（頁面/橫幅）用 `cms:*`／部落格（貼文）用角色字面字串／媒體庫用 `media:*`**——同一模組內三套完全不同的授權風格並存，見 §7。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 官方定義 | 本模組實際用途 |
|--------|:----:|---------|----------------|
| `E-1007` | 403 | 權限不足 | CMS 頁面/橫幅跨租戶操作 |
| `E-2000` | 404 | 找不到租戶 | 建立頁面/橫幅/貼文/分類/媒體時租戶不存在 |
| `E-2004` | 403 | 此租戶未啟用該功能 | `CMS_ENABLED` 功能開關未啟用 |
| `E-3000` | 404 | 找不到刊登項目 | §4.34 Listing 不存在（正確） |
| `E-3001` | 422 | 無效的刊登類型 | §4.34 正確使用（Sprint 278／DEF-388 前 §4.46～4.48 曾誤用，已改用 `E-8017`～`E-8019`） |
| `E-3006` | 404 | 找不到分類 | §4.35～4.48 獨立媒體分類不存在（Sprint 278／DEF-388 新採用，修復前誤用 `E-4000`） |
| `E-4031` | 403 | 無權操作此店鋪 | 貼文/分類跨租戶操作 |
| `E-4100` | 404 | 找不到文章 | 正確 |
| `E-4101` | 404 | 文章尚未發布 | 正確 |
| `E-4102` | 404 | 找不到分類 | §4.22～4.25 正確使用（分類不存在；Sprint 278／DEF-388 前曾誤用於「刪除有貼文關聯的分類」，已改用 `E-8015`） |
| `E-4103` | 404 | 找不到媒體檔案 | §4.35～4.42 媒體資產（Sprint 278／DEF-388 新採用，修復前誤用 `E-4000`） |
| `E-4106` | 409 | 無效的文章狀態轉換 | 正確 |
| `E-4107` | 409 | 已發布的文章無法刪除，請先下架 | §4.12～4.18 刪除貼文、§4.33 平台刪除貼文（Sprint 334，DEF-476 新增） |
| `E-8004` | 404 | 找不到 CMS 頁面 | 正確使用（找不到；Sprint 278／DEF-388 前曾與「頁面未發布」共用，已改用 `E-8016`） |
| `E-8005` | 404 | 找不到 CMS 橫幅 | 正確 |
| `E-8013` | 409 | 頁面網址代稱重複 | §4.1（Sprint 278／DEF-388 新增，修復前誤用 `E-9005`） |
| `E-8014` | 409 | 分類網址代稱重複 | §4.23（Sprint 278／DEF-388 新增，修復前誤用 `E-4102`）。**Sprint 338（DEF-492）起不再由任何端點回傳**：代稱撞到改為自動加隨機後綴，錯誤碼保留 |
| `E-8015` | 409 | 分類仍有關聯貼文，無法刪除 | §4.25（Sprint 278／DEF-388 新增，修復前誤用 `E-4102`） |
| `E-8016` | 404 | 頁面尚未發布 | §4.5（Sprint 278／DEF-388 新增，修復前與「頁面不存在」共用 `E-8004`） |
| `E-8017` | 409 | 媒體分類名稱重複 | §4.45（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-8018` | 409 | 媒體分類仍有子分類，無法刪除 | §4.48（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-8019` | 409 | 媒體分類仍有關聯媒體，無法刪除 | §4.48（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-8021` | 409 | 分類名稱已存在 | §4.23、§4.24 貼文分類同店名稱重複（Sprint 338，DEF-492 新增；與 `E-8017` 媒體分類名稱重複同類，但這是貼文分類） |
| `E-8020` | 409 | 媒體正被貼文使用，無法刪除 | §4.41（Sprint 334，DEF-474 新增；舊的 M15 刪除用 `E-9000` 代表這件事，隨舊端點移除） |
| `E-9000` | 400 | 驗證錯誤 | 一般請求驗證失敗 |
| `E-9005` | 400 | 缺少必填欄位 | 貼文 `title` 缺失（正確）；§4.5／4.10／4.11／4.19／4.20 缺少 `tenantId` 查詢參數（Sprint 278／DEF-388 新採用，修復前誤用 `E-1002`） |
| `E-9009` | 400 | 內容包含不允許的標記 | 貼文 `title`／`content` 危險標記偵測 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| ~~`PostController` 反射式租戶解析含硬編碼假租戶 fallback~~（**DEF-386，Sprint 280 已修復**） | 見 §1.2 第 1 點。已移除整段反射邏輯（含同款問題的 `getUserIdFromUser`）與硬編碼 fallback，改為直接呼叫 `TenantContext.getCurrentTenant()`／`getCurrentUser()`，與同模組其餘三個 Controller 一致 |
| ~~兩套互相獨立的媒體庫並存~~（**DEF-387 → DEF-474，Sprint 334 已解決**） | 歷史：Sprint 280 使用者拍板暫不合併（兩套定位不同）；Sprint 332 合併前端介面後，兩套後端的權限與刪除語意差異變成實際的使用者影響（DEF-474）；Sprint 334 使用者決定**對齊權限並移除舊的 5 個端點**，媒體只剩 M18 一套。見 §1.2 第 2 點 |
| **媒體刪除的引用檢查只看貼文封面圖** | `E-8020` 只比對 `posts.featured_image_url`，不掃貼文 `content`（Markdown 內文裡的圖片）。內文引用的素材被刪後，貼文內文的圖會壞掉。已登記 DEF-481 |
| ~~公開貼文端點不看店鋪狀態~~（**DEF-485，Sprint 337 已解決**） | 歷史：Sprint 336 登記時是讀碼確認（`PostRepository` 的公開查詢沒有店鋪狀態條件）；Sprint 337 紅燈先行**實測屬實**（四種非 `ACTIVE` 狀態 × 五種請求形狀共 20 格全部回 200，不是 404），使用者以互動選項拍板「三個端點全擋並回 404」，現在非 `ACTIVE` 或不存在的店鋪一律 404 `E-2000`。見 §4.19～4.21 |
| ~~平台層 `/blog`、`/blog/category/[id]` 對匿名訪客仍是登入提示~~ → **文章頁的分類徽章已改連店鋪前台的公開篩選（DEF-486，Sprint 337 已解決）；兩個平台層頁面保留現狀、成為沒有連結指向的孤兒頁（DEF-491）** | 歷史：這兩個頁面的 tenantId 取自瀏覽器 localStorage（只有登入才有），文章頁的「分類」徽章連到 `/blog/category/{id}`，匿名訪客點了看到登入提示。Sprint 337 在店鋪前台貼文列表做了公開版分類篩選（`/stores/{店鋪}/blog?category=`，見 §4.19～4.21），徽章改連過去。`/blog` 與 `/blog/category/[id]` 沒有動（仍是登入者專用）；生產碼中已沒有任何連結指向分類頁，`/blog` 只剩這兩個保留頁（分類頁、舊文章網址導向頁）內部連到它。去留未決，已登記 DEF-491。**Sprint 338（DEF-491，使用者指示「請先製作暫時入口暫代」）加了暫時入口**：前台頁尾「部落格」連到 `/blog`，`/blog` 上方的分類篩選列連到分類頁（兩頁仍是登入者專用，匿名訪客點進去看到登入提示）；正式的入口（跨店鋪部落格，要新的後端端點）或移除兩頁連同暫時入口，待 DEF-495 決定 |
| **同名分類只在應用程式裡比對，沒有資料庫唯一索引（DEF-492，Sprint 338）** | 名稱檢查（§4.22～4.25）是「讀出同店分類再比對」，與寫入之間沒有鎖：兩個請求同時建立同名分類，兩個都可能通過檢查，結果是多一個同名分類（店主可自行改名或刪除）。**刻意不加資料庫唯一索引**：這項規則上線前，全中文同名的分類本來就能建立，已存在的同名資料會讓建索引的遷移失敗，要先修資料才能上；比較後認為得不償失。**規則上線前已存在的同名分類不會被清理**，也不會被鎖死（名稱沒改的更新不檢查） |
| **公開素材圖片經過後端串流，沒有 CDN／預簽網址（DEF-488，Sprint 336 登記）** | `GET /v2/public/media/{assetId}`（§4.49）從物件儲存讀出再由 Spring 回應。匿名請求以來源 IP 共用每分鐘 100 次的限流桶（`RateLimitFilter`，讀碼），一頁十幾張封面圖會吃掉十幾次額度；流量大時要改走預簽網址或 CDN。目前以 5 分鐘公開快取減輕（未量測實際影響） |
| **全域 `force-response` 會替圖片的 `Content-Type` 補上 `;charset=UTF-8`** | `server.servlet.encoding.force-response=true`（DEF-397，Sprint 296）是全域設定，連 `image/png` 也變成 `image/png;charset=UTF-8`（`PublicMediaEndpointIntegrationTest` 實測）。瀏覽器對圖片類型忽略 charset 參數，不影響顯示；需要登入的 `/v2/media/files/{id}` 本來就是這樣，不是公開端點引入的 |
| **既有的 `-<8碼>` slug（開頭有連字號）未被遷移** | V97 只補 `slug = ''` 的列；先前被存成 `-d46b778f` 的貼文網址是能開的，改掉會讓已分享的連結失效，故刻意不動 |
| **大量錯誤碼跨領域誤用（DEF-388，已於 Sprint 278 修復）** | 本模組是目前已完成的 11 個模組中錯誤碼精確性問題最密集的一個：原 `E-4000`（房源專用）、`E-3001`（刊登類型專用）、`E-1002`（憑證過期專用）、`E-9005`（缺少欄位專用）、`E-4102`（找不到分類）、`E-8004`（找不到頁面）皆被借用到語意不符的情境。已新增 7 個專屬碼 `E-8013`～`E-8019`，並改採既有但先前未使用的 `E-3006`／`E-4103`；過程中額外發現並連帶修復 2 處原描述未列出的同類誤用（頁面/分類 slug 重複）。`CmsServiceTest`／`PostServiceTest`／`PostCategoryServiceTest`／`MediaServiceTest` 已同步更新/新增測試斷言具體錯誤碼 |
| `PostCategory.name`／`MediaCategory` 建立請求無 `@NotBlank` 驗證 | 可建立空白名稱的分類，無資料品質防護（非安全問題，純資料完整性缺口） |
| CMS 頁面/橫幅、貼文、獨立媒體庫三套授權風格並存 | `cms:*`／角色字面字串／`media:*`，同一模組內三種不同慣例，見 §5 備註 |
| `ApiRouteDocDriftTest` 覆蓋全部 5 個 Controller | 本文件完成後新增 `CmsController`／`PostController`／`MediaController`／`MediaCategoryController` 至 `GUARDED` 清單；Sprint 336 再加入 `PublicMediaController` |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.6（內容管理系統 CMS）§6.6.1～6.6.3 |
| FRD | 無章節（見文件頂部狀態說明） |
| 實作 | `CmsController`／`PostController`／`MediaController`／`MediaCategoryController`／`PublicMediaController`（`api/controller/`）；`CmsService`（`core/cms/`）；`PostService`／`PostCategoryService`（`core/cms/post/`）；`ListingCardService`（`core/cms/listing/`）；`MediaService`／`PublicMediaService`（`core/media/`；`core/cms/media/` 只剩 `MediaValidationService`） |
| 契約測試 | [CmsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/CmsServiceTest.java)、[PostServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostServiceTest.java)、[PostCategoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostCategoryServiceTest.java)、[ListingCardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/listing/ListingCardServiceTest.java)、[core/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/media/MediaServiceTest.java)、[PublicMediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/media/PublicMediaServiceTest.java)、[PublicMediaEndpointIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/PublicMediaEndpointIntegrationTest.java)（真實 PostgreSQL＋真實 HTTP）、[CmsDtoValidationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/dto/CmsDtoValidationTest.java)、[M15DtoValidationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/dto/M15DtoValidationTest.java) |
| 前端 | `frontend/src/services/cms.ts`（`/dashboard/posts*` 頁面；Sprint 332 起原 `/cms*` 頁面已併入並移除，舊網址 308 轉址）、`frontend/src/services/media.ts`（`/dashboard/media` 頁面，唯一的媒體介面） |

---

**文件狀態**：v1.0（Sprint 277 新建，DEF-286 範圍）。
