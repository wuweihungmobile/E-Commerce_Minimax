# Sprint 156 Plan — URL 協定白名單驗證缺口延伸掃描（DEF-103/104/194 家族補漏）

**Sprint**: Sprint 156
**日期**: 2026-09-15

---

## 1. 起點

Sprint 155 §8「誠實揭露總結」明確記錄：`avatarUrl` 等其他 Tenant 相關 DTO 的 URL 欄位「未查證」，非確認無缺陷。Sprint 154/155 累積修復 `DEF-103`（`CreateListingRequest.coverImageUrl`）、`DEF-104`（`CmsDto` Banner `linkUrl`）、`DEF-194`（`TenantUpdateRequest.logoUrl`）三個「寫入路徑無協定白名單驗證」的同型缺口，但每次都只查證觸發本輪的那一個欄位，從未系統性掃過全代碼庫其他 URL 型別的 `Create*Request`/`Update*Request` 欄位。本輪主動延伸這個既定模式，全面掃描其餘候選欄位。

---

## 2. 查證過程

以 `grep -rn "String.*[Uu]rl\b"` 全庫掃描所有 DTO，篩出宣告於 `Create*Request`/`Update*Request`（寫入路徑）的 URL 欄位，逐一查證是否已有 `@Pattern` 驗證、是否有真實寫入/持久化路徑、是否有前端消費端（渲染 sink）：

| 欄位 | 寫入路徑 | 持久化 | 前端消費 | 既有驗證 | 分類 |
|------|----------|--------|----------|----------|------|
| `ProductDto.CreateRequest`/`UpdateRequest.coverImageUrl` | ✅ `ProductService` 寫入 `Listing.coverImageUrl`（`POST/PUT /v2/products`） | ✅ | ✅ `cart`/`checkout`/`orders` 頁 `<img src=...>` | ❌ 無 | **A：修復** |
| `RoomDto.CreateRequest`/`UpdateRequest.coverImageUrl` | ✅ `RoomService` 寫入同一個 `Listing.coverImageUrl`（`POST/PUT /v2/rooms`） | ✅ | ✅ 同上 | ❌ 無 | **A：修復** |
| `M15Dto.CreatePostRequest`/`UpdatePostRequest.featuredImageUrl` | ✅ `PostService`（`POST/PUT /v2/posts`） | ✅ | ✅ `blog/[slug]`、`blog/page.tsx` `<img src=...>`（公開部落格頁） | ❌ 無 | **A：修復** |
| `CmsDto.CreateBannerRequest`/`UpdateBannerRequest.imageUrl` | ✅ `CmsService`（`POST/PUT /cms/banners`） | ✅ | ❌ 零消費端（同手足欄位 `linkUrl`/`DEF-104`） | ❌ 無 | **B：修復（手足欄位一致性）** |
| `CmsDto.CreatePageRequest`/`UpdatePageRequest.featuredImageUrl` | ✅ `CmsService`（`POST/PUT /cms/pages`） | ✅ | ❌ 整個 Content Page 功能（About/FAQ/Terms 等自訂頁）**全代碼庫零前端頁面**，`grep PageType/CreatePageRequest` 前端零命中 | ❌ 無 | **C：只登記** |
| `knowledge/CreateKnowledgeArticleRequest`/`UpdateKnowledgeArticleRequest.coverImageUrl` | ✅ `KnowledgeService` | ✅ | ❌ 建立文章 UI 本身不存在（`DEF-170` 已記錄 `createKnowledgeArticle()` 零呼叫點） | ❌ 無 | **C：只登記** |
| `ReviewDto.AddImageRequest.imageUrl` + `CreateRequest.images`（`List<String>`） | ✅ `ReviewService.addImage`/`createReview` | ✅ | ❌ 全代碼庫零前端呼叫（`addImage` 零呼叫點；`createReview` 前端表單從未送出 `images`） | ❌ 無 | **C：只登記** |
| `TenantApplicationRequest.businessLicenseUrl` | ✅ | ✅ | ❌ | ❌ 無 | 既有 `DEF-195`（Sprint 155 已登記，本輪不重複處理） |
| `avatarUrl`（`UserInfoResponse`/`TenantMemberResponse`/`TenantDetailsResponse`） | 僅 `OAuthService.exchangeCodeForUserInfo` 寫入（來源：Google/GitHub 結構化 `picture`/`avatar_url` 欄位）；`UserPrivacyService` 可清空為 null | — | — | — | **確認為 CLEAN**：`grep setAvatarUrl`/`.avatarUrl(` 全庫確認**無任何自由文字表單**可寫入此欄位，僅 OAuth 流程（第三方身分提供者的結構化欄位，非使用者可直接輸入的任意字串）。與 `DEF-103/104/194` 家族「使用者可透過我方表單直接輸入任意字串」的威脅模型不同，不納入本次修復家族 |

---

## 3. 分類與決策依據

- **A 類（3 組欄位）**：與 `DEF-103` 完全同一因果——同一個 `Listing.coverImageUrl` 欄位其實有*三條*獨立寫入路徑（`CreateListingRequest`／`ProductDto`／`RoomDto`），`DEF-103` 當時只覆蓋了第一條。`M15Dto` 的部落格精選圖也是即時可達、有真實公開渲染 sink 的寫入路徑。三者皆符合 CLAUDE.md「有清楚前例可循」的直接修復授權範圍，不需另外詢問使用者。
- **B 類（1 組欄位）**：`CmsDto.CreateBannerRequest`/`UpdateBannerRequest.imageUrl` 本身雖零前端消費端，但其手足欄位 `linkUrl` 已在 `DEF-104`（Sprint 154）確立「零消費端仍比照防禦性修復」的先例，且屬於*同一個 DTO 類別*——不修 `imageUrl` 只修 `linkUrl` 會造成同一張表單兩個欄位驗證強度不一致，故延伸同一決策。
- **C 類（3 組欄位）**：涉及的功能本身（CMS 自訂頁、知識庫建立文章、評論圖片）在前端**完全沒有任何 UI 入口**（非「有 UI 但不渲染這個欄位」，而是整條路徑除了直接呼叫 API 之外無法觸發），與既有 `DEF-170`~`DEF-177`「零呼叫點死路徑，不排入排程」的判準完全一致，故不擴大本輪修復範圍，只登記備查，避免 Rule 2（簡潔優先）所警告的 speculative 修改。

---

## 4. 實作內容

沿用 `DEF-103`/`104`/`194` 已驗證的同一條負向前瞻（negative lookahead）regex 與訊息風格：

```java
@Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
        message = "<Field> URL must not use javascript/data/vbscript/file protocol")
```

### 4.1 DEF-196：`ProductDto`/`RoomDto` 的 `coverImageUrl`

[`ProductDto.java`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/ProductDto.java)：`CreateRequest`/`UpdateRequest.coverImageUrl` 新增 `@Pattern`；`Response`/`ListResponse` 為輸出專用，不加驗證（加在其上無意義，且非本次缺口範圍）。

[`RoomDto.java`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/RoomDto.java)：同上，`CreateRequest`/`UpdateRequest.coverImageUrl` 新增 `@Pattern`，`Response`/`ListResponse` 不動。

### 4.2 DEF-197：`M15Dto.CreatePostRequest`/`UpdatePostRequest` 的 `featuredImageUrl`

[`M15Dto.java`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/M15Dto.java) 新增 `jakarta.validation.constraints.Pattern` import（此檔案先前完全未使用 Bean Validation 註解）並在兩個 Request 類別的 `featuredImageUrl` 補上 `@Pattern`。`title`/`content` 的儲存型 XSS 已於 `DEF-105`（Sprint 154）以偵測即拒絕方式修復，本項結構不同（純 URL 欄位），沿用協定白名單而非偵測拒絕。

### 4.3 DEF-198：`CmsDto.CreateBannerRequest`/`UpdateBannerRequest` 的 `imageUrl`

[`CmsDto.java`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/CmsDto.java)：`imageUrl` 補上與手足欄位 `linkUrl` 相同風格的 `@Pattern`（訊息文字改為 "Image URL must not use..."）。

### 4.4 DEF-199/200/201：只登記，不修改程式碼

見 §5。

---

## 5. 範圍外（刻意不做，如實揭露）

- **`DEF-199`**：`CmsDto.CreatePageRequest`/`UpdatePageRequest.featuredImageUrl`——整個 CMS 自訂頁（Content Page：About/FAQ/Terms 等）功能前端零 UI 入口，登記備查，待未來真正開發此 UI 時一併處理。
- **`DEF-200`**：`knowledge/CreateKnowledgeArticleRequest`/`UpdateKnowledgeArticleRequest.coverImageUrl`——與既有 `DEF-170` 同一個「建立文章 UI 不存在」死路徑，登記備查。
- **`DEF-201`**：`ReviewDto.AddImageRequest.imageUrl` + `CreateRequest.images`（`List<String>`，容器元素驗證需要不同語法 `List<@Pattern(...) String>`，結構與其餘單一 `String` 欄位不同）——評論圖片全代碼庫零前端呼叫點（新增/顯示皆無），登記備查。
- **不重複處理 `DEF-195`**：Sprint 155 已登記 `TenantApplicationRequest.businessLicenseUrl`，本輪掃描重新確認同一結論，不重複建立新 DEF 編號。
- **`avatarUrl` 確認為 CLEAN**：見 §2 表格最後一列，正式結案 Sprint 155 §8 遺留的「未探查」旗標——非協定驗證缺口（沒有自由文字寫入路徑），不需要也不會補 `@Pattern`。
- **未逐一排查 `ReviewDto.userAvatarUrl`/`BookingReviewDto.userAvatarUrl`/`OAuthDto.pictureUrl`**：三者皆為 Response DTO 回顯既有 `User.avatarUrl`（已在 §2 確認 CLEAN 的同一來源），或 OAuth 結構化欄位，不重複查證。

---

## 6. 驗證結果

### 6.1 紅燈先行驗證

新增 `ProductDtoValidationTest`（10 案例）、`RoomDtoValidationTest`（10 案例）、`M15DtoValidationTest`（10 案例），並擴充既有 `CmsDtoValidationTest`（+10 案例，共 20 案例）。修復前執行：`Tests run: 46, Failures: 24`（4 個欄位 × 6 個危險協定案例，含 Create/Update 各一次斷言 = 24 個危險協定斷言全數如預期轉紅，其餘合法值/null 案例綠燈）。套用 `@Pattern` 修復後重跑：`Tests run: 46, Failures: 0`。

### 6.2 `mvn -o verify`（完整單元＋整合回歸）

**BUILD SUCCESS**：單元測試 **1329 個**（相對 Sprint 155 結束時基準 1293，+36 為本輪新增的 `ProductDtoValidationTest`(9)/`RoomDtoValidationTest`(9)/`M15DtoValidationTest`(9)/`CmsDtoValidationTest`(+9)），**0 failures / 0 errors**；整合測試 **478 個**（與 Sprint 155 基準持平，本輪未新增整合測試），**0 failures / 0 errors**；checkstyle-main/checkstyle-test **0 違規**。

（第一次執行因忘記先 `make test-db-up` 而有 3 個既有 `SellerDashboardServiceCacheTest` 因 `ApplicationContext` 載入失敗而 Error——與本輪修改無關的環境前置缺失，非回歸；補跑 `make test-db-up` 後重新執行即全數通過，如實記錄此步驟疏失。）

### 6.3 `make validate-e2e`（乾淨 DB + host 全棧 + Playwright）

**62 passed / 4 skipped / 0 failed**，與 Sprint 155 既有基準完全一致（本輪無新增/刪除 E2E 案例，純背景回歸確認），schema 對齊無漂移（`[e2e-gate] ✅ 本地 E2E 守門通過`）。

---

## 7. 下一步 / Action Items

| # | 項目 | 狀態 |
|---|------|------|
| 1 | DEF-196：`ProductDto`/`RoomDto.coverImageUrl` | ✅ 完成 |
| 2 | DEF-197：`M15Dto` 部落格 `featuredImageUrl` | ✅ 完成 |
| 3 | DEF-198：`CmsDto` Banner `imageUrl` | ✅ 完成 |
| 4 | DEF-199/200/201：登記備查 | ✅ 完成 |
| 5 | `mvn -o verify` 完整回歸 | ✅ 完成 |
| 6 | `make validate-e2e` | ✅ 完成 |

---

## 8. 誠實揭露總結

- 本輪確認 `Listing.coverImageUrl` 實際上有三條獨立寫入路徑，`DEF-103`（Sprint 154）只覆蓋了其中一條；這代表過去「找到一個缺口就修一個欄位」的模式容易漏掉同一份資料的其他寫入入口，值得日後掃描時留意「這個資料庫欄位/概念是否有多個 DTO 都在寫」。
- C 類三項（`DEF-199`/`200`/`201`）刻意不修，理由完全對齊既有 `DEF-170`~`177` 判準，避免對「前端本來就不可達的路徑」做 speculative 加固；如果未來這些功能真的補了前端 UI，修復方式已在本文件記錄，屆時可直接套用同一 `@Pattern`。
- `avatarUrl` 的查證正式結案 Sprint 155 §8 留下的「未探查」旗標。
- 未逐一排查全庫是否還有其他尚未發現的 URL 欄位家族（例如未來新增的 DTO）；本輪範圍限定在「本次全庫 grep 命中的既有欄位」，非窮盡式保證未來新欄位不會重蹈覆轍。
