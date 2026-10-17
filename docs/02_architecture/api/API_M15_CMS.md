# API 規格文件 - M15 內容管理系統 / API Specification - M15 CMS

> **文件編號**: API-M15
> **文件版本**: v1.0（Sprint 277，首次建立；首次有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-16
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`CmsController`／`PostController`／`MediaController`／`MediaCategoryController`（`api/controller/`）、`CmsService`（`core/cms/`）、`PostService`／`PostCategoryService`（`core/cms/post/`）、`ListingCardService`（`core/cms/listing/`）、`MediaService`（`core/cms/media/`，PostController 專用）、`MediaService`（`core/media/`，獨立媒體庫專用，與前者**同名不同類別**）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[CmsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/CmsServiceTest.java)、[PostServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostServiceTest.java)、[PostCategoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostCategoryServiceTest.java)、[ListingCardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/listing/ListingCardServiceTest.java)、[core/cms/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/media/MediaServiceTest.java)、[core/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/media/MediaServiceTest.java)
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
| **描述** | 平台靜態頁面與橫幅（`CmsController`）、店鋪部落格貼文與分類（`PostController` 前半）、部落格專用媒體上傳（`PostController` 後半）、商品/房源嵌入卡片（`PostController` 一端點）、獨立通用媒體庫與分類（`MediaController`／`MediaCategoryController`） |
| **API 前綴** | `/api/v2/cms/...`（`CmsController`）、`/api/v2/dashboard/posts|post-categories|media/...`／`/api/v2/posts/...`／`/api/v2/admin/posts/...`／`/api/v2/listings/{id}/card`（`PostController`，前綴不統一，見 §1.2）、`/api/v2/media/...`（`MediaController`／`MediaCategoryController`） |
| **多租戶隔離** | `CmsController`／`MediaController`／`MediaCategoryController`／`PostController` 四者皆直接使用 `TenantContext.getCurrentTenant()`（`PostController` 原自行重新實作一套反射式租戶解析，已於 Sprint 280／DEF-386 移除，見 §7） |

### 1.2 設計重點（本模組最重要的部分，請務必先讀）

1. **`PostController` 的租戶解析已統一（DEF-386，Sprint 280 修復）**：原先 `getTenantIdFromUser` 不直接呼叫 `TenantContext.getCurrentTenant()`，而是反查 `TenantMemberRepository`／`UserRepository`、失敗則反射挖 `UserDetails`、最終 fallback 寫死的 mock 租戶 UUID；現已全部移除，12 個端點方法改為直接呼叫 `TenantContext.getCurrentTenant()`／`getCurrentUser()`，與其餘三個 Controller 一致。
2. **本模組有兩套完全獨立、互不相通的媒體庫**：
   - **`core.cms.media.MediaService`**（`domain.model.cms.media.MediaAsset` 實體）：供 `PostController` 的 `/v2/dashboard/media/*` 使用，租戶來自 §1.2 第 1 點的風險解析；前端對應 `frontend/src/services/cms.ts`，頁面在 `/cms`、`/cms/media`。
   - **`core.media.MediaService`**（同樣讀寫 `domain.model.cms.media.MediaAsset` 實體——**底層資料表其實是同一張**，但 Service／Controller／DTO 完全分開實作）：供 `MediaController`／`MediaCategoryController` 的 `/v2/media/*` 使用，租戶正確經 `TenantContext.getCurrentTenant()`；前端對應 `frontend/src/services/media.ts`，頁面在 `/dashboard/media`。
   - 兩套 API 的 DTO 形狀、驗證規則、錯誤碼完全不同（見 §6），但操作的是**同一張 `media_assets` 表**——經由 `/cms/media` 上傳的檔案與經由 `/dashboard/media` 上傳的檔案理論上會互相出現在對方的查詢結果中（只要 `tenantId` 相同），但兩個前端頁面呈現的 UI／篩選條件完全獨立，使用者不會意識到這是同一個資料來源。
   - **定位差異（Sprint 280，DEF-387 拍板維持現狀）**：`/cms/media` 是**部落格貼文專用**的媒體上傳，定位上從屬於 `PostController` 的貼文編輯流程（插入貼文內文的圖片）；`/dashboard/media` 是**獨立的通用媒體庫**，供商品/房源等其他內容情境重複使用素材，不綁定任何特定業務流程。兩者雖共用底層資料表，但服務的使用情境不同，**暫不合併**——除非未來觀察到使用者對兩個「媒體庫」頁面內容不一致感到困惑，否則合併是不小的重構成本（需統一 DTO／驗證規則／錯誤碼並改動兩個前端頁面），優先序低於其他已確認的缺陷。詳見 §7 DEF-387。
3. **`PostController` 另外還內嵌一套獨立的「部落格媒體」上傳**（`/v2/dashboard/media/*`，委派給上面第 2 點的 `core.cms.media.MediaService`），與 `MediaController` 的 `/v2/media/*` 是**第三條**上傳路徑的錯覺——實際上第 2 點已說明兩者寫入同一張表，這裡只是重複強調：目前已有 3 個 Controller（`PostController`、`MediaController`，以及下方會提到的 `ErpController`／`AdminController` 都不涉及媒體，純屬 M15 範圍內 2 個 Controller）可以上傳到同一張 `media_assets` 表。
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
| （無對應 US） | 部落格專用媒體上傳 | §4.26～4.30 | ✅ 已實作（見 §1.2 第 2、3 點重複） |
| （無對應 US） | 平台管理員管理任意店鋪貼文 | §4.31～4.33 | ✅ 已實作 |
| （無對應 US） | 嵌入卡片資訊查詢（供部落格渲染） | §4.34 | ✅ 已實作 |
| （無對應 US） | 獨立通用媒體庫（上傳/查詢/更新/刪除） | §4.35～4.42 | ✅ 已實作（見 §1.2 第 2 點，與上方部落格媒體寫入同一張表） |
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

### 2.2 PostController（`/v2`）—— 貼文、分類、部落格媒體、嵌入卡片

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-012 | POST | `/v2/dashboard/posts` | 建立貼文 | `STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST` |
| API-M15-013 | PUT | `/v2/dashboard/posts/{postId}` | 更新貼文 | 同上 |
| API-M15-014 | GET | `/v2/dashboard/posts` | 貼文列表（可依狀態篩選） | 同上 |
| API-M15-015 | GET | `/v2/dashboard/posts/{postId}` | 貼文詳情 | 同上 |
| API-M15-016 | DELETE | `/v2/dashboard/posts/{postId}` | 刪除貼文 | 同上 |
| API-M15-017 | POST | `/v2/dashboard/posts/{postId}/publish` | 發布貼文 | 同上 |
| API-M15-018 | DELETE | `/v2/dashboard/posts/{postId}/publish` | 下架貼文 | 同上 |
| API-M15-019 | GET | `/v2/posts` | 已發布貼文列表（公開） | 公開 |
| API-M15-020 | GET | `/v2/posts/{slug}` | 依 slug 查已發布貼文（公開） | 公開 |
| API-M15-021 | GET | `/v2/posts/categories` | 公開分類列表（僅啟用中） | 公開 |
| API-M15-022 | GET | `/v2/dashboard/post-categories` | 分類列表（Dashboard，含停用） | `STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST` |
| API-M15-023 | POST | `/v2/dashboard/post-categories` | 建立分類 | 同上 |
| API-M15-024 | PUT | `/v2/dashboard/post-categories/{categoryId}` | 更新分類 | 同上 |
| API-M15-025 | DELETE | `/v2/dashboard/post-categories/{categoryId}` | 刪除分類（無貼文關聯時） | 同上 |
| API-M15-026 | GET | `/v2/dashboard/media` | 部落格媒體列表 | 同上 |
| API-M15-027 | POST | `/v2/dashboard/media/upload-by-path` | 部落格媒體註冊（既有路徑） | 同上 |
| API-M15-028 | POST | `/v2/dashboard/media/upload-multipart` | 部落格媒體上傳（實際檔案） | 同上 |
| API-M15-029 | GET | `/v2/dashboard/media/{mediaId}/file` | 部落格媒體檔案串流 | 同上 |
| API-M15-030 | DELETE | `/v2/dashboard/media/{mediaId}` | 刪除部落格媒體 | 同上 |
| API-M15-031 | GET | `/v2/admin/posts` | 平台查全部店鋪貼文（需帶 `tenantId`） | `SUPER_ADMIN` |
| API-M15-032 | DELETE | `/v2/admin/posts/{postId}/unpublish` | 平台下架任意店鋪貼文 | `SUPER_ADMIN` |
| API-M15-033 | DELETE | `/v2/admin/posts/{postId}` | 平台刪除任意店鋪貼文 | `SUPER_ADMIN` |
| API-M15-034 | GET | `/v2/listings/{listingId}/card` | 嵌入卡片資訊（公開） | 公開 |

### 2.3 MediaController（`/v2/media`）—— 獨立媒體庫

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-035 | GET | `/v2/media` | 媒體列表（分頁/篩選） | `media:read` |
| API-M15-036 | GET | `/v2/media/{assetId}` | 媒體詳情 | `media:read` |
| API-M15-037 | POST | `/v2/media/upload` | 上傳媒體（登記既有路徑） | `media:create` |
| API-M15-038 | POST | `/v2/media/upload-multipart` | 上傳媒體（實際檔案） | `media:create` |
| API-M15-039 | GET | `/v2/media/files/{assetId}` | 媒體檔案串流 | `media:read` |
| API-M15-040 | PUT | `/v2/media/{assetId}` | 更新媒體 | `media:update` |
| API-M15-041 | DELETE | `/v2/media/{assetId}` | 刪除媒體（軟刪除） | `media:delete` |
| API-M15-042 | GET | `/v2/media/count` | 媒體總數 | `media:read` |

### 2.4 MediaCategoryController（`/v2/media/categories`）—— 獨立媒體分類

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M15-043 | GET | `/v2/media/categories` | 分類列表 | `media:read` |
| API-M15-044 | GET | `/v2/media/categories/root` | 根分類列表 | `media:read` |
| API-M15-045 | GET | `/v2/media/categories/{categoryId}` | 分類詳情 | `media:read` |
| API-M15-046 | POST | `/v2/media/categories` | 建立分類（可指定父分類） | `media:create` |
| API-M15-047 | PUT | `/v2/media/categories/{categoryId}` | 更新分類 | `media:update` |
| API-M15-048 | DELETE | `/v2/media/categories/{categoryId}` | 刪除分類（無子分類/媒體時） | `media:delete` |

共 48 個端點，橫跨 4 個 Controller，全數納入 `ApiRouteDocDriftTest.GUARDED`。

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
| `id`／`tenant`／`uploader`／`category`／`fileName`／`originalName`／`filePath`／`fileSize`／`mimeType`／`fileType`（`IMAGE`/`VIDEO`/`DOCUMENT`）／`tags[]`／`altText`／`title`／`usageCount`／`isDeleted` | `core.cms.media.MediaService` 與 `core.media.MediaService` 皆讀寫此實體，但走不同的 Repository 查詢方法與不同的 DTO（`M15Dto.MediaResponse` vs `api.dto.media.MediaAssetDto`） |

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

**請求（建立/更新）**：`title`（必填，Service 層檢查）、`content`（選填，含嵌入語法解析）、`categoryId`（選填，須屬本租戶）、`tags[]`、`featuredImageUrl`（黑名單 protocol 驗證）、`autoPublish`（僅建立時，布林）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `title` 缺失或空白 | 400 | `E-9005`（此處用法正確） |
| `title`／`content` 含危險標記 | 400 | `E-9009` |
| 貼文不存在 | 404 | `E-4100` |
| 操作非本租戶的貼文 | 403 | `E-4031` |
| `categoryId` 不存在或非本租戶 | 404 | `E-4102` |
| 發布已發布中的貼文／下架未發布的貼文 | 409 | `E-4106` |
| 發布數已達配額上限 | — | 見 `FeatureToggleService.checkQuotaNotExceeded`（既有實作，非本模組新增） |
| Feature Toggle `CMS_ENABLED` 未啟用 | 403 | `E-2004` |

### 4.19～4.21 公開貼文／分類（`PostController`）

- **端點**: `GET /v2/posts`
- **端點**: `GET /v2/posts/{slug}`
- **端點**: `GET /v2/posts/categories`

**權限**：公開，無需登入。`getPublishedPosts`／`getPublishedPostBySlug` 皆**必須**帶 `tenantId`。`getPublishedPostBySlug` 每次呼叫原子遞增 `viewCount`（見 §1.2 第 8 點）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 缺少 `tenantId` | 400 | `E-9005`（Sprint 278／DEF-388 修復前誤用「憑證已過期」`E-1002`，見 §7） |
| 貼文不存在 | 404 | `E-4100` |
| 貼文存在但非 `PUBLISHED` | 409 | `E-4101` |

### 4.22～4.25 貼文分類管理（`PostController`）

- **端點**: `GET /v2/dashboard/post-categories`
- **端點**: `POST /v2/dashboard/post-categories`
- **端點**: `PUT /v2/dashboard/post-categories/{categoryId}`
- **端點**: `DELETE /v2/dashboard/post-categories/{categoryId}`

**權限**：同貼文管理。`createCategory`／`updateCategory` 的 `name` 欄位**無任何必填驗證**（DTO 無 `@NotBlank`，Service 層也不檢查，見 §7）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 分類不存在 | 404 | `E-4102`（官方定義「找不到分類」，此處正確） |
| 分類 slug 重複 | 409 | `E-8014`（Sprint 278／DEF-388 新增專屬碼並修正本文件先前的錯誤描述——`generateSlug` 不會自動加隨機尾碼避免衝突，重複時會直接拋錯；修復前誤用「找不到分類」`E-4102`，見 §7） |
| 更新/刪除非本租戶分類 | 403 | `E-4031` |
| 刪除有貼文關聯的分類 | 409 | `E-8015`（Sprint 278／DEF-388 新增專屬碼，修復前誤用「找不到分類」`E-4102`，見 §7） |

### 4.26～4.30 部落格媒體（`PostController` → `core.cms.media.MediaService`）

- **端點**: `GET /v2/dashboard/media`
- **端點**: `POST /v2/dashboard/media/upload-by-path`
- **端點**: `POST /v2/dashboard/media/upload-multipart`
- **端點**: `GET /v2/dashboard/media/{mediaId}/file`
- **端點**: `DELETE /v2/dashboard/media/{mediaId}`

**權限**：同貼文管理。`uploadMediaByPath`（§4.27）僅登記既有儲存路徑的 metadata；`uploadMediaMultipart`（§4.28）才是真正的檔案上傳（到 MinIO/S3）。`getMediaFile`（§4.29）回應為串流（`InputStreamResource`），非 JSON 封包。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 媒體不存在 | 404 | `E-4103`（官方定義「找不到媒體檔案」，此處正確） |
| 操作非本租戶媒體 | 403 | `E-4031` |
| 刪除仍被貼文使用的媒體 | 400 | `E-9000`（語意上較接近 409，但至少不是離題的碼） |

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
| Listing 型別非 `PRODUCT`/`ROOM`（理論上不可達） | 422 | `E-3001`（此處用法正確） |

**已知限制**：商品卡的庫存欄位（`inStock`／`availableQty`）目前前端未渲染（`ListingCardService` 原始碼註解自承），屬已修復資料來源（Sprint 116 DEF-066 同款修復）但尚無 UI 消費的欄位。

### 4.35～4.42 獨立媒體庫（`MediaController` → `core.media.MediaService`）

- **端點**: `GET /v2/media`
- **端點**: `GET /v2/media/{assetId}`
- **端點**: `POST /v2/media/upload`
- **端點**: `POST /v2/media/upload-multipart`
- **端點**: `GET /v2/media/files/{assetId}`
- **端點**: `PUT /v2/media/{assetId}`
- **端點**: `DELETE /v2/media/{assetId}`
- **端點**: `GET /v2/media/count`

**權限**：`media:*` 系列（`STORE_OWNER`／`ADMIN` 全部四權限皆有；`STORE_STAFF` 僅 `media:read`）。`uploadAsset`（§4.37）登記既有路徑，會驗證 `filePath` 確實屬於本租戶且物件真實存在於儲存層（DEF-101 IDOR 防護）；`uploadAssetMultipart`（§4.38）才是真正檔案上傳，含 magic-bytes 內容驗證（`mediaValidationService.validateActualContent`）。`downloadAsset`（§4.39 對應的 Service 方法）額外驗證 `filePath` 真的屬於本租戶儲存路徑（雙重 IDOR 防護，不只信任資料列的 `tenantId`）。建立/更新/刪除成功回應 HTTP **201**（建立）／200（其餘）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 媒體資產不存在 | 404 | `E-4103`（Sprint 278／DEF-388 修復前誤用房源碼 `E-4000`，見 §7） |
| 上傳/更新時指定的分類不存在 | 404 | `E-3006`（Sprint 278／DEF-388 修復前誤用 `E-4000`，見 §7） |
| 租戶不存在 | 404 | `E-2000` |
| 檔案路徑非法／上傳失敗 | 400 | `E-9000` |

### 4.43～4.48 獨立媒體分類（`MediaCategoryController` → `core.media.MediaService`）

- **端點**: `GET /v2/media/categories`
- **端點**: `GET /v2/media/categories/root`
- **端點**: `GET /v2/media/categories/{categoryId}`
- **端點**: `POST /v2/media/categories`
- **端點**: `PUT /v2/media/categories/{categoryId}`
- **端點**: `DELETE /v2/media/categories/{categoryId}`

**權限**：同獨立媒體庫。支援父子階層（`parentId` 選填），回應遞迴載入 `children[]`。併發建立同名分類有部分唯一索引＋`saveAndFlush`捕捉違反約束兜底（DEF-160）。

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
| 貼文/分類/部落格媒體（§4.12～4.30） | ✅（統一經 `TenantContext.getCurrentTenant()`，DEF-386 已修復，不再有假租戶 fallback） | ✅ | ✅ | ❌（角色不符，`SUPER_ADMIN` 不在 `hasAnyRole` 清單內） |
| 平台管理貼文（§4.31～4.33） | ❌ | ❌ | ❌ | ✅（跨租戶） |
| 獨立媒體庫/分類查詢（§4.35／4.36／4.39／4.42～4.45） | ❌（無 `media:read`） | ✅ | ✅ | ❌（`media:*` 與角色無關，`ADMIN`／`SUPER_ADMIN` 本身無此權限，`SUPER_ADMIN` 持有 `Permission.values()` 全集例外，見 `RolePermissionMapping.SUPER_ADMIN`） |
| 獨立媒體庫/分類寫入（§4.37／4.38／4.40／4.41／4.46～4.48） | ❌ | ✅ | ❌（僅 `media:read`） | ✅（透過全權限集合） |

注意：**CMS（頁面/橫幅）用 `cms:*`／部落格（貼文）用角色字面字串／獨立媒體庫用 `media:*`**——同一模組內三套完全不同的授權風格並存，見 §7。

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
| `E-4031` | 403 | 無權操作此店鋪 | 貼文/分類/部落格媒體跨租戶操作 |
| `E-4100` | 404 | 找不到文章 | 正確 |
| `E-4101` | 404 | 文章尚未發布 | 正確 |
| `E-4102` | 404 | 找不到分類 | §4.22～4.25 正確使用（分類不存在；Sprint 278／DEF-388 前曾誤用於「刪除有貼文關聯的分類」，已改用 `E-8015`） |
| `E-4103` | 404 | 找不到媒體檔案 | §4.26～4.30 正確使用；§4.35～4.42 獨立媒體資產（Sprint 278／DEF-388 新採用，修復前誤用 `E-4000`） |
| `E-4106` | 409 | 無效的文章狀態轉換 | 正確 |
| `E-8004` | 404 | 找不到 CMS 頁面 | 正確使用（找不到；Sprint 278／DEF-388 前曾與「頁面未發布」共用，已改用 `E-8016`） |
| `E-8005` | 404 | 找不到 CMS 橫幅 | 正確 |
| `E-8013` | 409 | 頁面網址代稱重複 | §4.1（Sprint 278／DEF-388 新增，修復前誤用 `E-9005`） |
| `E-8014` | 409 | 分類網址代稱重複 | §4.23（Sprint 278／DEF-388 新增，修復前誤用 `E-4102`） |
| `E-8015` | 409 | 分類仍有關聯貼文，無法刪除 | §4.25（Sprint 278／DEF-388 新增，修復前誤用 `E-4102`） |
| `E-8016` | 404 | 頁面尚未發布 | §4.5（Sprint 278／DEF-388 新增，修復前與「頁面不存在」共用 `E-8004`） |
| `E-8017` | 409 | 媒體分類名稱重複 | §4.45（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-8018` | 409 | 媒體分類仍有子分類，無法刪除 | §4.48（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-8019` | 409 | 媒體分類仍有關聯媒體，無法刪除 | §4.48（Sprint 278／DEF-388 新增，修復前誤用 `E-3001`） |
| `E-9000` | 400 | 驗證錯誤 | 一般請求驗證失敗 |
| `E-9005` | 400 | 缺少必填欄位 | 貼文 `title` 缺失（正確）；§4.5／4.10／4.11／4.19／4.20 缺少 `tenantId` 查詢參數（Sprint 278／DEF-388 新採用，修復前誤用 `E-1002`） |
| `E-9009` | 400 | 內容包含不允許的標記 | 貼文 `title`／`content` 危險標記偵測 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| ~~`PostController` 反射式租戶解析含硬編碼假租戶 fallback~~（**DEF-386，Sprint 280 已修復**） | 見 §1.2 第 1 點。已移除整段反射邏輯（含同款問題的 `getUserIdFromUser`）與硬編碼 fallback，改為直接呼叫 `TenantContext.getCurrentTenant()`／`getCurrentUser()`，與同模組其餘三個 Controller 一致 |
| **兩套互相獨立的媒體庫並存（DEF-387，Sprint 280 拍板維持現狀）** | 見 §1.2 第 2、3 點。`/cms/media`（部落格貼文專用）與 `/dashboard/media`（通用媒體庫，供其他內容情境重複使用素材）實際寫入同一張 `media_assets` 表卻各自有獨立的 DTO／驗證規則／錯誤碼。**決策**：兩者定位不同（前者從屬貼文編輯流程、後者是獨立通用素材庫），暫不合併；若未來觀察到使用者對兩個「媒體庫」頁面內容不一致感到困惑，再評估合併成本 |
| **大量錯誤碼跨領域誤用（DEF-388，已於 Sprint 278 修復）** | 本模組是目前已完成的 11 個模組中錯誤碼精確性問題最密集的一個：原 `E-4000`（房源專用）、`E-3001`（刊登類型專用）、`E-1002`（憑證過期專用）、`E-9005`（缺少欄位專用）、`E-4102`（找不到分類）、`E-8004`（找不到頁面）皆被借用到語意不符的情境。已新增 7 個專屬碼 `E-8013`～`E-8019`，並改採既有但先前未使用的 `E-3006`／`E-4103`；過程中額外發現並連帶修復 2 處原描述未列出的同類誤用（頁面/分類 slug 重複）。`CmsServiceTest`／`PostServiceTest`／`PostCategoryServiceTest`／`MediaServiceTest` 已同步更新/新增測試斷言具體錯誤碼 |
| `PostCategory.name`／`MediaCategory` 建立請求無 `@NotBlank` 驗證 | 可建立空白名稱的分類，無資料品質防護（非安全問題，純資料完整性缺口） |
| CMS 頁面/橫幅、貼文、獨立媒體庫三套授權風格並存 | `cms:*`／角色字面字串／`media:*`，同一模組內三種不同慣例，見 §5 備註 |
| `ApiRouteDocDriftTest` 本次起覆蓋全部 4 個 Controller | 本文件完成後新增 `CmsController`／`PostController`／`MediaController`／`MediaCategoryController` 至 `GUARDED` 清單 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.6（內容管理系統 CMS）§6.6.1～6.6.3 |
| FRD | 無章節（見文件頂部狀態說明） |
| 實作 | `CmsController`／`PostController`／`MediaController`／`MediaCategoryController`（`api/controller/`）；`CmsService`（`core/cms/`）；`PostService`／`PostCategoryService`（`core/cms/post/`）；`ListingCardService`（`core/cms/listing/`）；`MediaService`（`core/cms/media/` 與 `core/media/`，兩個同名不同類別） |
| 契約測試 | [CmsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/CmsServiceTest.java)、[PostServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostServiceTest.java)、[PostCategoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/post/PostCategoryServiceTest.java)、[ListingCardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/listing/ListingCardServiceTest.java)、[core/cms/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/cms/media/MediaServiceTest.java)、[core/media/MediaServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/media/MediaServiceTest.java)、[CmsDtoValidationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/dto/CmsDtoValidationTest.java)、[M15DtoValidationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/dto/M15DtoValidationTest.java) |
| 前端 | `frontend/src/services/cms.ts`（`/cms`、`/cms/media` 頁面）、`frontend/src/services/media.ts`（`/dashboard/media` 頁面） |

---

**文件狀態**：v1.0（Sprint 277 新建，DEF-286 範圍）。
