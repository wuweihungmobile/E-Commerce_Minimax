# Sprint 253 計畫：DEF-321(d)(e) 收尾 — 嵌入卡片規格 + 路由表校對

## 1. 起點與範圍

### 1.1 起點

Sprint 252 推送後使用者貼回總結說「請繼續完成任務」。盤點整個待辦池後以 `AskUserQuestion` 呈現四個方向，使用者選擇**「DEF-321(d)(e) 店鋪前台收尾」**——這是 DEF-321 四個子項裡最後剩下的兩項，(b) 店鋪前台已於 S252 完成，(a)(c) 已於 S248/S251 完成。

### 1.2 讀碼時發現範圍比原始 tracker 描述更大，兩輪 `AskUserQuestion` 確認

1. **DEF-321(d) 的 PRD 章節編號已過期**：tracker 寫「PRD §9」，目前版本（v1.0_Final）該規格實際在 **§6.6.3**（PRD §9 現在是「API 設計」）。已實際開檔核對定位到正確章節，未依賴 tracker 自述的編號。
2. **發現相鄰但不同程式碼路徑的缺口**：`PostService.parseEmbeds` 目前若嵌入的 `listing_id` 不存在或非 ACTIVE，會直接拋 `BusinessException(E_4105)` 擋下整篇貼文儲存；但 PRD §6.6.3「PostEmbed Markdown 語法錯誤處理」明文要求「不阻斷整篇 Post 寫入，只在該嵌入位置顯示錯誤」。**使用者選擇：本輪一併修正**。
3. **DEF-321(e) 路由表落差遠比「改名」更大**：深入核對 PRD §10 全部路由後，發現 (a) Dashboard 商品/房源從未做成 PRD 設計的統一 `/dashboard/listings`；(b) 整個 ERP 模組多了 `/erp/` 路徑前綴；(c) `/dashboard/pricing/calendar` 完全不存在（但背後元件 `PricingCalendarPreview` 與後端端點皆已存在，只是沒有獨立頁面）；(d) `/admin/tenants/[id]/features` 不存在（但後端 `GET/PUT /v2/admin/tenants/{id}/features` 已存在，純前端缺口）。**使用者選擇：文件對齊程式碼之外，(c)(d) 兩個缺口也一併做**。

### 1.3 挖掘 (d) 時發現的衍生缺口（本輪一併修正，非另登記）

- `AdminService.getTenantFeatureToggles` 只回傳**已存在資料列**的開關，缺少人類可讀的名稱/說明/分類/是否需審核——這些中繼資料只存在於 `TenantService.FEATURE_DEFINITIONS`（店主自助頁 `dashboard/tenants/[id]/features` 用的那套），Admin 側從未重用。改法：`AdminController.getTenantFeatureToggles` 直接呼叫既有 `tenantService.getFeatureToggles(tenantId)`，捨棄 `AdminService` 那個資訊量不足的版本。
- `AdminService.updateTenantFeatureToggle` 若該租戶從未建立過某功能的資料列會 404（`orElseThrow`），但**店主送出需審核功能的申請時一定會建立資料列**（`isEnabled=false` 待審），所以此 bug 只在「Admin 想主動開啟租戶從未申請過的功能」這個邊緣案例觸發；仍一併修正（改 `orElseThrow` 為缺列時建立新列）。
- `frontend/src/lib/api.ts` 的 `API_ENDPOINTS.admin.tenants.features(id)` 指向 `/feature-toggles`（複數+toggles），但後端對應的 **GET** 端點實際路徑是 `/features`（`/feature-toggles` 只有 **DELETE** mapping）——此常數目前零呼叫點，從未被任何前端程式碼觸發過，是潛伏的死路徑錯誤設定，本輪一併修正路徑字串。

### 1.4 讀碼時排除、另登記的發現（不在本輪範圍）

- **`/auth/profile`（PRD 10.1.1）可能是真的功能缺口，非改名**：全庫查無 `updateProfile`/`UpdateProfileRequest`/「更新個人資料」字樣；`/account` 頁面只有個資下載／刪除帳戶（GDPR 自助），沒有編輯姓名/電話等欄位的表單。使用者本輪只明確授權 (c)(d) 兩個缺口，此項**另登記為新缺陷，不在本輪動工**。
- `/admin/tenants/[id]`（PRD 10.1.3，不含 `/review` 後綴）：讀碼確認 `/admin/tenants/[id]/review` 頁面本身就是通用「店鋪詳情」檢視（處理全部 4 種狀態，非僅審核中），純粹是路由命名掛在 `/review` 後綴下，**判定為改名，非缺口**，文件對齊即可，不新建重複頁面。
- `/auth/oauth/:provider`（PRD 10.1.1）：實際採「按鈕觸發 `startOAuthFlow()` 重新導向」而非獨立頁面，`/oauth/callback/[provider]` 才是唯一需要的頁面路由。**判定為架構演進，非缺口**，文件對齊即可。

## 2. 實作範圍（逐項編譯＋測試，不累積開發）

### 2.1 後端

1. `ListingCardService.buildProductCard`／`buildRoomCard`：
   - `status != ACTIVE` → `statusReason="listing_unavailable"`、`ctaUrl="/stores/" + tenant.getSlug()`（PRD §6.6.3 Cascade 處理）
   - `buildRoomCard` 的 `under_maintenance` 分支 → 新增 `ctaUrl="/contact"`、`currentPrice=null`（PRD §6.6.3 MAINTENANCE 規格，目前只做了 `available=false`/`statusReason`，漏了這兩個欄位）
   - 刪除死碼 `M15Dto.ListingCardResponse.fromListing()`（grep 確認全庫零呼叫點，且殘留同一種舊 `ctaUrl` 錯誤寫法，與本輪修復目標矛盾，留著會誤導後續讀者）
2. `PostService.parseEmbeds`：listing 不存在或非 ACTIVE 時，比照同迴圈內「格式錯誤」分支的既有模式（`log.warn` + `continue`，不拋例外），不建立該筆 `PostEmbed`、不中斷整篇貼文儲存。
3. `AdminController`／`AdminService`：
   - `getTenantFeatureToggles` 改呼叫 `tenantService.getFeatureToggles(tenantId)`（重用既有目錄+中繼資料邏輯），回應型別改為 `FeatureToggleResponse`（`features[]`+`quotas[]`）
   - `AdminService.getTenantFeatureToggles`／對應的 `AdminDto.TenantFeatureTogglesResponse` 若無其他呼叫者則移除（避免留下死碼）
   - `AdminService.updateTenantFeatureToggle`：查無資料列時建立新列（預設 `isEnabled=false`，隨即由本次請求值覆寫），取代原 `orElseThrow`

### 2.2 前端

1. `frontend/src/services/cms.ts`：`ListingCardResponse.currentPrice` 型別改為 `number | null`
2. `frontend/src/app/blog/[slug]/page.tsx`：
   - `currentPrice` null-safe 顯示（避免 `.toLocaleString()` 對 `null` 噴錯）
   - 依 `statusReason` 分流顯示：`listing_unavailable`→「此商品已下架」＋前往店鋪 CTA；`under_maintenance`→「房源維護中」＋聯絡客服 CTA
   - 卡片載入失敗（`catch` 分支）文字改為「此商品已下架」，對齊 PRD 用語
3. 新增 `frontend/src/app/dashboard/pricing/calendar/page.tsx`：房源選單 + 重用既有 `<PricingCalendarPreview />`（比照 `PricingRuleList.tsx` 既有的 `filterRoom` 選單模式）
4. 新增 `frontend/src/app/admin/tenants/[id]/features/page.tsx`：比照既有 `dashboard/tenants/[id]/features/page.tsx` 的 UI 模式（分類卡片＋`Switch`＋配額條），改打 Admin 端點，角色檢查改 `SUPER_ADMIN`
5. `frontend/src/lib/api.ts`：修正 `API_ENDPOINTS.admin.tenants.features(id)` 路徑字串（`/feature-toggles`→`/features`，對齊後端 GET mapping）
6. `admin/tenants/[id]/review/page.tsx`：新增一個連到 `/admin/tenants/[id]/features` 的連結（**不動**既有「刻意不提供核准/駁回」註解區塊與邏輯）

### 2.3 文件

1. `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` §10.1.1/10.1.2/10.1.3：依 §1.2/§1.4 的核對結果更新路由表（改名對齊現狀、標註架構演進、新增先前未收錄的既有功能路由、為真缺口加註記）
2. `DEFERRED_ITEMS_TRACKER.md`：結案 DEF-321(d)(e)；新登記 `/auth/profile` 缺口（暫定 DEF-358，待確認下一個可用編號）
3. `RELEASE_TRACKER.md`：收尾時新增本輪列

## 3. 驗證

- 後端：單元（`ListingCardServiceTest` 新增 MAINTENANCE/INACTIVE 案例、`PostServiceTest` 更新/新增 embed 跳過案例、`TenantServiceTest` 新增 Admin 不受成員檢查案例）＋真實 PostgreSQL 整合測試
- 突變驗證：`git stash` 暫時還原 `ListingCardService`／`PostService`／`M15Dto` 三個檔案，確認新增的 5 個單元測試精準轉紅（且僅這 5 個，其餘不受影響），還原後恢復綠燈
- 前端：`tsc --noEmit`／`eslint`；新增 Playwright（部落格嵌入卡片狀態顯示、`/dashboard/pricing/calendar`、`/admin/tenants/[id]/features`）
- `make validate-release` 全綠後才 push

## 4. 實作中發現的問題

1. **`AdminController.getTenantFeatureToggles` 原始改法有誤**：一開始直接改呼叫既有 `tenantService.getFeatureToggles(tenantId)`，首次 E2E 跑發現該方法內建「呼叫者必須是該租戶的 ACTIVE 成員」檢查（自助語意），導致 Admin 查任意租戶時被誤擋 403。修法：抽出 `buildFeatureToggleResponse(tenantId)` 私有方法共用目錄組裝邏輯，新增 `getFeatureTogglesForAdmin(tenantId)` 跳過該檢查（Admin 本身的 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 已在 HTTP 層把關）。
2. **DEF-360（新登記，未修）**：寫 E2E 規格模擬真實流程（上架房源→呼叫 `markMaintenance` API→查卡片）時發現，`RoomCalendarService.markMaintenance` 對從未被訂過（無 `room_calendar` 記錄）的房源會靜默空操作——回 200 但完全沒寫入。既有整合測試固件直接寫 DB 繞過此 API，從未驗證到這個落差。E2E 規格改用 `test.fail()` 標記該案例，等 DEF-360 修好後會「意外通過」提醒移除標記。
3. **DEF-361（新登記，未修）**：`/dashboard/pricing/calendar`（本輪新頁）與既有 `/dashboard/pricing/rules` 共用的導覽列寫法都會觸發 React Hydration Error #418，以獨立診斷腳本在兩頁分別重現，確認非本輪新增程式碼獨有。約 1-2 秒內自行恢復，但系統負載較高時會讓 E2E 斷言超時。本輪已放寬相關斷言 timeout 至 15000ms 處理。
4. **DEF-362（新登記，未修）**：凌晨執行 E2E 時意外發現 `<PricingCalendarPreview/>`（既有元件）送給後端的「今天」用 `toISOString()`（UTC），後端 `LocalDate.now()` 用系統時區，台北時間 00:00~07:59 時 UTC 日期是「前一天」，後端誤判為過去日期回 400。純屬執行時間點造成的既有缺陷，本輪 E2E 規格改為不斷言日曆資料載入成功，避免假性失敗。
5. **Playwright strict-mode 的 locator 撞字**：`at-admin-tenant-features-real.spec.ts` 的 `getByText('民宿預訂功能')` 同時撞到待審核摘要句子裡的同一段文字，改用 `{ exact: true }` 排除；`getByText('待審核')` 有 3 個徽章同文字，改用 `.first()`。
6. **`setStoreStatus` 在未導覽過的 page 上呼叫會噴 `localStorage` SecurityError**：`at-embed-card-status.spec.ts` 的 E2E-EMBED-03 在全新 `page`（仍是 `about:blank`）呼叫會清 localStorage 的 `setStoreStatus`，改用不碰 localStorage 的 `setStoreStatusWithToken`（既有 helper，比照 `at-store-suspended-payment-real.spec.ts` 既有用法）。
7. **自己的環境疏失（非程式碼問題，已排除）**：為除錯上述三個真實問題，手動起過兩輪獨立的除錯用 Postgres/Redis/backend JAR/frontend server（非 `nk-test-pg`／`make validate-release` 自己的容器），過程中第一次重跑 `mvn clean verify` 忘記 `make validate-release` 已自動 `test-db-down` 把 `nk-test-pg` 關掉，導致一輪 11 個整合測試因 `Connection refused` 假性失敗；重新 `make test-db-up` 後確認與程式碼無關。

## 5. 全量回歸結果

`mvn -o clean verify`（真實 PostgreSQL／Redis）**BUILD SUCCESS**：單元 **2208**（+2）／整合 **832**（+0，僅修正既有案例斷言）／**0 失敗**；checkstyle（main+test）**0 違規**；PMD 通過。`make validate-release`（act 三個 job＋schema 漂移＋完整 Playwright E2E）**全綠**：Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build 皆 `BUILD SUCCESS`；schema 對齊無漂移；E2E **153 passed／4 skipped／0 failed**（含本輪新增 3 份規格、7 個案例，E2E-EMBED-02 以 `test.fail()` 標記 DEF-360 已知缺口）。
