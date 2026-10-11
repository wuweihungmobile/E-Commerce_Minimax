# Sprint 252 計畫：店鋪前台 `/stores/[slug]`（DEF-321b）

## 1. 起點與範圍

### 1.1 起點

Sprint 251 推送後使用者貼回總結說「請繼續完成任務」（無 `==>` 回覆）。盤點 `DEFERRED_ITEMS_TRACKER.md` 整個待辦池後，以 `AskUserQuestion` 呈現四個方向（店鋪前台 `/stores`／延續 DEF-331(d)(e)(f) 稽核／修復 DEF-347／決策清單整理），使用者選擇**「店鋪前台 `/stores`（DEF-321b）」**——這是使用者在 Sprint 232 選定要做的四項（成員管理 UI、CMS 卡片連結、DEF-318 通知、店鋪前台）裡最後一項還沒開始的。

### 1.2 讀碼確認後的範圍修正

開始設計前先讀碼確認 PRD 規格與實作現況，發現兩個重要修正：

1. **我最初誤判 `tenants.slug` 從未建立**（錯誤方法：只確認「migration 檔名含 slug 字樣」，沒有實際打開檔案核對）。重新核對後確認 **`slug` 欄位從 V1（第一個 migration）就存在**（`UNIQUE NOT NULL`），且**開店審核通過時已自動從店名產生 slug**（`AdminService.generateTenantSlug`，碰撞時加隨機後綴），`TenantRepository.findBySlug`/`existsBySlug` 也已存在。真正的缺口只是：`TenantDetailsResponse` 沒吐出 `slug`、`TenantUpdateRequest` 不能改 `slug`、沒有「以 slug 查店鋪」的公開端點。**不需要新 migration，也沒有既有店鋪回填問題**。已向使用者更正此誤判。
2. **新發現且比店鋪前台本身更底層的缺口**：PRD §12.5／§12.7 明寫 Guest（未登入）可瀏覽所有商品/房源，但 `SecurityConfig` 的 `GET /v2/listings`、`/v2/listings/{id}` 都沒有被放入 `permitAll()`，落入 `anyRequest().authenticated()`——真正匿名的請求會被 401 擋下。前端 `ListingDetail.tsx` 的「auth」狀態分支（顯示「請先登入以查看商品詳情」）證實這是現狀而非我的誤判。

### 1.3 四個決策點（`AskUserQuestion`，使用者選擇）

| 決策 | 使用者選擇 |
|------|-----------|
| 匿名訪客瀏覽的底層缺口怎麼處理 | **新建專用公開端點**（不動現有 `/v2/listings` 授權，風險最小） |
| 「精選商品/房源」策展機制 | **本輪就做完整策展機制**（`Listing` 新增 `isFeatured` + dashboard 勾選 UI） |
| slug 產生方式（含既有店鋪回填） | **自動產生，允許後續修改**（既有機制已符合「自動產生」；本輪補上「允許後續修改」） |
| 本輪交付範圍 | **全部 4 條路線一次做完**（首頁＋商品列表＋房源列表＋貼文列表） |

## 2. 設計

### 2.1 後端

**不需要的部分**（讀碼確認已存在，之前估計錯誤）：
- ~~新增 `tenants.slug` migration~~ → 已存在
- ~~既有店鋪 slug 回填~~ → V1 起所有租戶皆有 slug
- ~~`/stores/[slug]/blog` 新端點~~ → 直接重用既有公開端點 `GET /v2/posts?tenantId=X`（`PostController`，已 `permitAll`，已支援任意 `tenantId`）

**需要新增的部分**：

1. **Migration V92**：`listings` 新增 `is_featured BOOLEAN NOT NULL DEFAULT false`（策展機制，無既有欄位可重用）。
2. **`Listing.java`**：新增 `isFeatured` 欄位。
3. **`ListingRepository`**：新增 `findByTenantIdAndListingTypeAndStatus(...)`（分頁，供 `/products`、`/rooms` 子頁）、`findByTenantIdAndStatusAndIsFeaturedTrue(...)`（首頁精選區塊，限筆數）。
4. **`ProductDto`/`RoomDto`**：`CreateRequest`/`UpdateRequest`/`Response` 新增 `isFeatured`。
5. **`ProductService`/`RoomService`**：`updateListingFromRequest` 各自寫入 `isFeatured`（兩邊重複是既有慣例，同 DEF-207 的既有模式，不在本輪重構）。
6. **`TenantDetailsResponse`**：新增 `slug` 欄位；`TenantService.getTenantDetails` 填入。
7. **`TenantUpdateRequest`**：新增選填 `slug`；`TenantService.updateTenant` 變更時驗證格式（`^[a-z0-9]+(-[a-z0-9]+)*$`）與唯一性（`existsBySlug`，排除自己），重複回 `E_1007`-類錯誤（沿用既有驗證錯誤慣例，非新錯誤碼）。
8. **新增 `StorefrontController`**（公開，`GET /v2/public/stores/...`，重用既有但目前無任何 Controller 使用的 `permitAll()` 規則 `/v2/public/**`，不需改動 `SecurityConfig`）：
   - `GET /v2/public/stores/{slug}` → 店鋪公開資料（`slug`/`name`/`description`/`logoUrl`），**僅 `status=ACTIVE`** 才回傳，否則 `E_2000`（沿用 `TenantService` 既有「租戶找不到」錯誤碼）。
   - `GET /v2/public/stores/{slug}/listings?type=PRODUCT|ROOM&featured=true|false&page=&size=` → 分頁，僅 `status=ACTIVE`。
   - 貼文列表不另建端點：前端直接呼叫既有 `GET /v2/posts?tenantId=`。
9. **Hero Banner 範圍縮減（Rule 2 簡潔優先，向使用者明示的假設）**：PRD `<StoreHeroBanner/>` 需要的 `bannerImageUrl`/`ctaText` 目前無任何欄位或策展 UI，四個決策點並未涵蓋「要不要新增專屬橫幅圖片」。本輪**不新增橫幅圖片欄位**，Hero 區塊直接用既有 `logoUrl`/`name`/`description`，`ctaText` 固定為「查看商品」或「查看房源」（依店鋪商品/房源哪個有資料決定）、`ctaUrl` 指向 `/stores/{slug}/products` 或 `/rooms`。未來若需要獨立橫幅圖片，可用 `tenant.metadata` 擴充欄位追加，不需 migration。

### 2.2 前端（皆置於既有 `(storefront)` route group，沿用共用 Header/Footer）

- `frontend/src/services/storefront.ts`（新）：包裝上述公開端點 + 重用 `services/cms.ts` 的 `getPublishedPosts`。
- `frontend/src/components/storefront/StoreHeroBanner.tsx`、`StoreFeaturedListings.tsx`、`StoreNav.tsx`（商品/房源/貼文分頁導覽）。
- `frontend/src/app/(storefront)/stores/[slug]/page.tsx`、`/products/page.tsx`、`/rooms/page.tsx`、`/blog/page.tsx`。
- Dashboard：`TenantEditForm.tsx` 新增 `slug` 欄位（格式提示＋送出前正規化）；`ProductForm.tsx`/`RoomForm.tsx` 新增「精選」`Switch`。
- 商品/房源卡片 `ctaUrl` 沿用 S251 已修正的 `/listings/{id}` 慣例。

### 2.3 驗證

- 後端：單元（Service 層 isFeatured、slug 驗證）＋真實 PostgreSQL 整合測試（`StorefrontController`：非 ACTIVE 店鋪 404、分頁、featured 篩選、slug 唯一性衝突）。沿用 S251 的突變驗證做法，對新增的授權/篩選分支逐一驗證。
- 前端：`tsc --noEmit`、`eslint`；新增 Playwright E2E（匿名訪客走 `/stores/[slug]` 四條路線）。
- `make validate-release` 全綠後才 push。

## 3. 實作順序（逐一編譯＋測試，不累積開發）

依 2.1 的 9 個後端子項、2.2 的前端子項逐一進行，每項完成立即編譯/型別檢查並跑對應測試。

## 4. 實作中發現的問題

1. **Next.js 16 RSC 限制**：`page.tsx`（Server Component）不能把函式當 prop 傳給 Client Component
   （`StoreLayout` 的 `children` 需要是函式才能取得非同步載入的 `profile`）。只能用 Playwright 真實瀏覽
   器才會在執行期暴露（`npm run build` 不會，因為 4 條都是動態路由、build 時不會真的渲染）。修法：每條
   路由新增一個薄 client wrapper（`StoreHomeView`/`StoreProductsView`/`StoreRoomsView`/`StoreBlogView`），
   page.tsx 只傳純字串 `slug`/`page` 給它，函式形式的 children 改在 client-to-client 之間建立。
2. **checkstyle NPathComplexity**：`TenantService.applyTenantUpdates`、`RoomService.updateListingFromRequest`
   本已是高分支複雜度的方法，各加一個新 if 分支就超過上限 200（768、384）。修法：把新邏輯抽成獨立私有方法
   （`applySlugUpdate`、`applyIsFeaturedUpdate`），不在原方法內疊加分支。
3. **`SettlementBookingIntegrationTest` 迴歸**：該測試用原生 SQL `INSERT INTO listings`（明確欄位清單）
   建固件，未列出新欄位 `is_featured`。`integration-test` profile 的 schema 由 Hibernate `ddl-auto=update`
   依 entity annotation 生成（`flyway.enabled=false`），不會套用 migration 裡的 `DEFAULT false`，所以該
   欄位在測試庫是「NOT NULL、無預設值」，原生 SQL 插入直接違反約束。修法：SQL 補上 `is_featured` 欄位
   （比照既有 `Tenant.connectChargesEnabled` 同樣是 `nullable=false` 又無 `columnDefinition` 的既有慣例，
   全庫唯一一處對 `listings` 做原生 SQL insert 的測試）。
4. **（非程式碼問題，本輪自己踩的坑，已記憶）**：手動起後端 JAR 搭配真實 Flyway 對 `make test-db-up` 容器
   做快速 Playwright 迴圈後，未重建容器就直接跑 `mvn verify`，導致該容器殘留嚴格 NOT NULL 約束，衍生一輪
   51 個跨模組假性失敗；又在背景 `mvn clean verify` 執行期間於同一目錄跑了另一個 Maven 指令
   （`checkstyle:check`/`rm -rf target/classes`），弄壞該次背景執行（`NoClassDefFoundError`）。兩次都靠
   「重建乾淨容器＋不並行跑第二個 Maven 生命週期」排除，與程式碼本身無關。

## 5. 全量回歸結果

`mvn -o clean verify`（真實 PostgreSQL／Redis）**BUILD SUCCESS**（15 分 43 秒）：單元 **2205**（+7）／
整合 **832**（+7）／**0 失敗**；checkstyle（main+test）**0 違規**；PMD 通過。前端 `tsc --noEmit`／
`eslint` 0 問題；新增 Playwright `at-storefront-real.spec.ts`（5 案例，真實後端＋真實匿名瀏覽器工作階段）
全過。`StorefrontControllerE2ETest` 以突變驗證證實 ACTIVE 篩選確有效（拿掉後
`getStoreProfile_suspendedTenant_returns404` 精準轉紅，還原後恢復綠燈）。
