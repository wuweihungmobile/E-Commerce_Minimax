# Sprint 256 計畫：補齊 DEF-358(c)（`/blog/category/[id]` 貼文分類篩選頁）

## 1. 起點與範圍

Sprint 255 收尾後使用者對 DEF-358（PRD §10 路由表核對發現的 4 個真缺口）決策：4 項全做，依「由小到大、依耦合度排列」排入 Sprint 256 起依序處理：

1. **Sprint 256（本輪）**：(c) `/blog/category/[id]` 貼文分類篩選頁
2. Sprint 257：(b) `/auth/profile` 個人資料編輯表單
3. Sprint 258：(d) `/admin` 管理台總覽首頁
4. Sprint 259：(a) `/products`、`/rooms` 跨店鋪商品/房源列表

本輪（Sprint 256）處理第 1 項。PRD 規格：§10.1.1 `/blog/category/[id]`（M15）——「貼文分類篩選頁尚未實作」。`/blog`（列表）、`/blog/[slug]`（詳情）皆已實作，僅分類篩選頁缺席。

## 2. 讀碼確認範圍

**資料模型**：`Post.category` 為多對一關聯（一篇貼文最多一個分類，`category_id` 可為 NULL）。`PostCategory` 有 `name`/`slug`/`description`/`sortOrder`/`isActive` 等欄位，無 SEO 專用欄位。

**既有程式碼**：
- `/blog`、`/blog/[slug]` 都是 `'use client'` 元件，`tenantId` 從 `localStorage.getItem('tenantId')` 取得（非 URL），這是整個 `/blog/*` 路由家族既有的既定慣例（非本輪引入）。登入時 `services/auth.ts` 會自動把 `tenantId` 寫入 localStorage。
- `GET /v2/posts`（公開）原本只接受 `tenantId`/`page`/`size`，無分類篩選。`PostRepository.findByTenantIdAndCategoryId` 已存在但只用於 `deleteCategory` 的關聯檢查（不含 `status=PUBLISHED` 篩選，不能直接套用）。
- `GET /v2/posts/categories`（PRD §9.15 明文定義、Guest+ 可用）**完全不存在**——`PostController` 只有 `/dashboard/post-categories`（需登入）。
- `PostCategoryRepository.findByTenantIdAndIsActiveTrue` 已存在但是死碼（零呼叫點）；`PostCategoryService.getCategory(id, tenantId)` 已存在、已有完整單元測試，但從未被任何 Controller 端點接線（可能是為單筆分類詳情預留，但本輪判斷不需要，見下）。
- 全站目前沒有任何 `isActive=false` 的寫入路徑——`createCategory` 永遠寫 `true`，`UpdateCategoryRequest` 沒有 `isActive` 欄位。這是既有的、與本次任務無關的侷限（分類一旦建立就無法停用），不在本輪修復範圍內，但代表「公開端點只回傳啟用中分類」這個防線目前在正式環境尚無法被觸發到非空的停用分支——仍照正確語意實作，為未來若補上停用功能時先把防線建好。
- `SecurityConfig` 的 `/v2/posts/**` 已是 `permitAll`，新端點只要掛在 `/posts/categories` 下即自動放行，不需改 SecurityConfig。
- `/blog/[slug]` 既有的分類徽章只是純文字 `<span>`，沒有任何連結——這正是 DEF-358(c) 要補的「分類頁做出來了但沒有入口」缺口的另一半。

**設計決策**：
1. 不新增單筆分類查詢端點（如 `/posts/categories/{id}`）。PRD 只明文定義列表端點 `/posts/categories`；分類頁要顯示的「單一分類名稱/描述」改為向公開列表端點要資料、前端自行以 `id` 找出對應項——分類數量天生不多（店主手動維護），效能上沒有負擔，且不需要在 PRD 規格之外多開一條路由。既有但從未接線的 `PostCategoryService.getCategory` 維持不變、不接線（非本輪需要，也不是缺陷，不擅自處理)。
2. 公開分類列表只回傳 `isActive=true`（復用既有死碼 `findByTenantIdAndIsActiveTrue`），Dashboard 既有的 `getCategories`（管理視角，含停用分類以便重新啟用）維持不變、不受影響——兩者是不同視角，刻意不合併。
3. `/blog/category/[id]` 延續 `/blog`、`/blog/[slug]` 既有的 client component + `localStorage.tenantId` 慣例（Rule 11：同一路由家族的既有模式），不套用 `/stores/[slug]/*`（Sprint 252）的 Server/Client 分離模式——後者是為了從 URL slug 解析租戶，與本頁從 localStorage 取得租戶的前提不同，套用會是把不相關的模式混在一起（Rule 7）。
4. 分頁元件改用既有共用的 `<Pagination />`（`components/ui/pagination.tsx`，有頁碼按鈕，比 `/blog/page.tsx` 手刻的「上一頁/下一頁」更完整），因為這是全新檔案、不是在改 `/blog/page.tsx` 本身，依 Rule 7 選一個不混用。
5. 不做 `generateMetadata`（PRD §6.6.2 SEO 優化列為 P2，且全站目前零範例可循、`PostCategory` 也没有 SEO 專用欄位），維持與 `/blog`、`/blog/[slug]` 一致的無動態 metadata 現況，避免無謂的新增複雜度。
6. 不在 `/blog` 主頁新增「依分類瀏覽」側欄——缺口描述的是分類篩選頁本身缺席，不是首頁導覽重新設計；入口改由貼文詳情頁既有的分類徽章補上可點擊連結即可讓頁面「可達」，範圍精準對應缺口本身。

## 3. 實作

**後端**：
- `PostRepository`：新增 `findPublishedByTenantIdAndCategoryId(tenantId, categoryId, pageable)`（比照既有 `findPublishedByTenantId` 的 JPQL，多加 `p.category.id = :categoryId` 條件）。
- `PostService.getPublishedPosts`：簽章改為 `(tenantId, categoryId, page, size)`（唯一呼叫點在 Controller，直接改簽章而非另開 overload），`categoryId` 為 `null` 時走原查詢，否則走新查詢。
- `PostCategoryService`：新增 `getActiveCategories(tenantId)`，呼叫既有 `findByTenantIdAndIsActiveTrue`，回應 DTO 與既有 `getCategories` 共用 `CategoryListResponse`/`CategoryResponse`（無需新增 DTO 欄位，既有欄位已足夠)。
- `PostController`：`GET /posts` 新增 `categoryId`（`required=false`）參數；新增 `GET /posts/categories`（公開，呼叫 `getActiveCategories`）。

**前端**：
- `services/cms.ts`：`getPublishedPosts` 的 `params` 型別加 `categoryId?: string`；新增 `getPublicCategories(tenantId)` 呼叫 `GET /v2/posts/categories`。
- 新增 `frontend/src/app/blog/category/[id]/page.tsx`：client component，`useParams` 取 `id`，流程與 `/blog/page.tsx` 一致（載入中/查無分類/空清單/列表四態），改用 `<Pagination />`。
- `frontend/src/app/blog/[slug]/page.tsx`：分類徽章從純文字 `<span>` 改為 `<Link href={/blog/category/{categoryId}}>`，是頁面唯一真正的入口。

## 4. 測試

**後端單元測試**：
- `PostServiceTest$GetPublishedPosts`：既有案例補上第 2 個參數 `null`；新增 `getPublishedPosts_noCategoryId_usesUnfilteredQuery`、`getPublishedPosts_withCategoryId_usesCategoryFilteredQuery`（以 `verify(never())` 鎖住「只能走一條查詢路徑」，不是只斷言回應不是 null）。
- `PostCategoryServiceTest`：新增 `GetActiveCategories` 巢狀類別 3 案例（含「公開端點不得呼叫會連停用分類一起回傳的 `findByTenantIdOrderBySortOrderAsc`」的 `verify(never())`）。
- **突變驗證**：暫時把 `PostService`/`PostCategoryService` 改回「忽略 categoryId」／「查全部分類」的錯誤版本，重跑新測試：4 個新案例全數轉紅（2 FAILURE + 2 ERROR，含 Mockito `UnnecessaryStubbing`/`NullPointerException`），其餘既有案例不受影響；還原後重跑恢復 59/59 全綠。

**後端整合測試**（`PostControllerE2ETest`，真實 PostgreSQL）：
- `IT-M15-021`：公開分類列表只回傳啟用中分類——直接以 repository 寫入一筆 `isActive=false` 的分類（全站無此寫入路徑可從 API 觸發），斷言公開端點回應含啟用分類、不含停用分類。
- `IT-M15-022`：`GET /v2/posts?categoryId=` 只回傳該分類的已發布貼文，不含另一分類的貼文。

**前端**：
- `npm run type-check`、`npx eslint`（新檔案與修改檔案）、`npm run build` 皆通過；`/blog/category/[id]` 確實出現在建置輸出的路由清單中。
- 新增 `frontend/e2e/at-blog-category-real.spec.ts`（真實後端，不 mock）：E2E-BLOGCAT-01（分類篩選正確性，含「不包含其他分類」的負向斷言）、02（貼文詳情頁分類徽章可點擊導向分類頁）、03（不存在的分類 ID 顯示「此分類不存在」而非崩潰）、04（存在但無貼文的分類顯示空狀態、與「不存在」區分）。

## 5. 全量回歸結果

`mvn -o test`（`PostServiceTest`／`PostCategoryServiceTest`）：**59/59 全綠**。

突變驗證：暫時把 `PostService.getPublishedPosts` 改回忽略 `categoryId`、`PostCategoryService.getActiveCategories` 改回呼叫 `findByTenantIdOrderBySortOrderAsc`（連停用分類一起回傳），重跑新增測試：4 個新案例全數轉紅（2 FAILURE + 2 ERROR）；還原後重跑恢復 59/59 全綠。

`mvn -o verify`（真實 PostgreSQL／Redis，含全量整合測試）：**BUILD SUCCESS**，整合測試 **834**（+2）／**0 失敗**；`PostControllerE2ETest` 本身 24/24（+2）全綠。

前端：`npm run type-check`、`npx eslint`（新增/修改檔案）、`npm run build` 皆通過，`/blog/category/[id]` 確實出現在建置輸出路由清單。

`make validate-release`（act 全量 CI＋schema 漂移檢查＋E2E，commit 前完整驗證）：**全數通過**。
- act（backend checkstyle/compile/單元/整合測試＋打包）：BUILD SUCCESS
- schema 對齊：無漂移
- E2E（host JAR＋npm build＋Playwright）：**158 passed（+4，與新增的 E2E-BLOGCAT-01~04 一致）／4 skipped／0 failed**（5.3 分）
- 已寫入 FULL 驗證記錄，30 分內可直接 push
