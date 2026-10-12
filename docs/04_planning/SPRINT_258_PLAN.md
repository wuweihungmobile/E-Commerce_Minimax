# Sprint 258 計畫：補齊 DEF-358(d)（`/admin` 管理台總覽首頁）

## 1. 起點與範圍

承接 Sprint 256/257：DEF-358 使用者已核准 4 項全做，依「由小到大、依耦合度排列」排入 Sprint 256 起依序處理：

1. ✅ Sprint 256：(c) `/blog/category/[id]` 貼文分類篩選頁
2. ✅ Sprint 257：(b) `/auth/profile` 個人資料編輯表單
3. **Sprint 258（本輪）**：(d) `/admin` 管理台總覽首頁
4. Sprint 259：(a) `/products`、`/rooms` 跨店鋪商品/房源列表

PRD 規格：§10.1.3 `/admin`（M14）——「目前無獨立 `/admin` 根頁面，平台管理台各功能各自獨立路由（`/admin/tenants`、`/admin/audit-logs` 等），無統一入口頁」。

## 2. 讀碼確認範圍

**現況**：`/admin` 是 `frontend/src/app/admin/` 下獨立頂層目錄（不在任何 route group），已有 8 個子頁面（租戶管理、租戶詳情、Feature Toggle、稽核紀錄、採購單審批、結算沖正、客服工單列表/詳情），**沒有共用 layout、沒有統一入口頁**，與 PRD §10.1.3 缺口描述完全一致。

**角色守門現況（重要，決定本輪範圍邊界）**：8 個既有頁面中 7 個**完全沒有任何前端角色檢查**（僅 1 個檢查「是否登入」，不檢查是否為 `SUPER_ADMIN`），純粹依賴後端 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 擋 403、頁面以既有的通用 `catch` 顯示「載入失敗」。全前端專案目前**沒有任何「非 SUPER_ADMIN 就導向別處」的先例**可循。**範圍決策**：新總覽頁沿用這個既有慣例（不加前端角色守門），不在本輪「補入口頁」範圍內順手導入一套全新的、7 個既有頁面都還沒有的角色守門機制——那是另一個更大、影響既有 7 頁的獨立課題，不屬於 DEF-358(d) 字面描述的缺口（「無總覽首頁」≠「既有頁面缺乏角色守門」）。

**最佳可重用 API**：`GET /v2/admin/stats`（`AdminController.getPlatformStats`，`@PreAuthorize("hasRole('SUPER_ADMIN')")`）已存在、**先前零前端呼叫點**，回傳 `totalTenants`/`activeTenants`/`totalUsers`/`totalListings`/`totalOrders`/`totalPlatformGMV`/`pendingTenantReviews`。`pendingTenantReviews` 已有既有單元測試鎖住「只能算 `tenant_applications` 不能算 `tenants.status`」（Sprint 108 DEF-059 回歸守門）。**`totalPlatformGMV` 刻意不採用**——原始碼註解明載是 `訂單數 × MOCK_AVERAGE_ORDER_VALUE` 的假資料，放上總覽頁會誤導使用者以為是真實金額。

另一個現成、精確的待辦數字：`GET /v2/admin/purchase-orders/pending` 的 `totalElements`（既有端點，`/admin/purchase-orders` 頁本身已在用）。**結算沖正候選**（`totalElements` 混合「可發起」與「待確認」兩種狀態，無法拆分出單純的「待審核」數字）與**客服工單待處理數**（既有 `listAllTickets` 無 `status` 篩選參數，`totalElements` 是全部工單而非僅待處理）兩項若要精確數字需要小幅擴充既有端點參數，**本輪不做**——為求零後端風險、零新增攻擊面，總覽頁只重用「不需任何後端改動即可精確呈現」的既有數字，這兩項僅以純導覽卡片（無數字徽章）呈現，不顯示可能誤導的粗略/錨總數字。

**最直接的參考範本**：店主總覽頁 `/dashboard`（`frontend/src/app/dashboard/page.tsx`）——同樣無共用 layout、自帶 `<nav>`、`QUICK_LINKS` 陣列＋`StatCard` 元件＋「數字可點擊導向篩選頁」的既有模式（Sprint 151 DEF-188 已示範）。新 `/admin` 總覽頁是這個模式的平台層級版本，架構原樣套用，只是資料來源從 tenant-scoped 的 `AnalyticsService`（內部用 `TenantContext.getCurrentTenant()` 過濾）換成跨租戶的 `AdminService.getPlatformStats()`。

**API 端點呼叫慣例**：既有 5 個 admin 頁面都是直接在頁面內 `apiClient.get(...)` + 本地定義 `ApiResponse<T>`/回應型別 interface，沒有共用的 `AdminService` 前端服務類別（與 `AuthService`/`AnalyticsService` 等其他模組的慣例不同）。新頁面沿用 admin 頁面自己的既有慣例（Rule 7：不同區域各自既有不同模式時，各自match，不互相混用）。

## 3. 實作

**前端**：
- `lib/api.ts` 新增 `API_ENDPOINTS.admin.stats`（既有 `purchaseOrders.pending` 已存在可直接重用）。
- 新增 `frontend/src/app/admin/page.tsx`：結構比照 `/dashboard/page.tsx`（`<nav>`+使用者資訊+登出、`QUICK_LINKS` 快速入口列、`StatCard` 統計卡片）。平行呼叫 `GET /v2/admin/stats`、`GET /v2/admin/purchase-orders/pending` 兩支既有端點。5 張平台統計卡（租戶總數/營運中租戶/使用者總數/上架中商品房源/訂單總數，不含 Mock GMV）＋ 2 張可點擊的待辦卡（待審核開店申請→`/admin/tenants`、待審批採購單→`/admin/purchase-orders`）＋ 5 個快速入口連結（租戶管理/稽核紀錄/採購單審批/結算沖正/客服工單）。

**後端**：**零改動**——`GET /v2/admin/stats`、`GET /v2/admin/purchase-orders/pending` 皆為既有端點，直接重用。唯一的後端變動是**補測試**（見下）。

## 4. 測試

**後端**：`GET /v2/admin/stats` 先前只有 Service 層單元測試（`AdminServiceTest`），本輪起被前端真正呼叫，在 `AdminControllerE2ETest` 補上 2 個 HTTP 層案例（SUPER_ADMIN 成功取得；BUYER 角色 403）——這是本輪唯一的後端測試新增，驗證的是「既有端點首次被賦予真實前端用途後，其 HTTP 層權限與回應結構確實如預期」，非新寫業務邏輯。

**前端**：`npm run type-check`／`npx eslint`／`npm run build` 皆通過，`/admin` 確實出現在建置輸出路由清單（65 條，+1）。新增 `frontend/e2e/at-admin-overview-real.spec.ts`（真實後端，不 mock，使用既有固定 SUPER_ADMIN 測試帳號 `admin@nextkey.local`）4 案例：統計卡片正確顯示（非载入失敗）、快速入口導航、兩個待辦數字卡片的點擊導航。因統計數字依賴共用測試資料庫既有資料量，不斷言精確數值，只斷言「有顯示」與「導覽正確」。

## 5. 全量回歸結果

`mvn -o test-compile`＋`checkstyle:check`／`checkstyle:check@checkstyle-test`：0 違規（有 Sprint 257 的教訓，本輪提前跑過，沒有在 act 才被擋下）。

後端整合測試（`AdminControllerE2ETest`，真實 PostgreSQL）：**24/24 全綠**（含新增的 2 個 `GET /v2/admin/stats` 案例：SUPER_ADMIN 成功、BUYER 403）。

前端：`npm run type-check`／`npx eslint`／`npm run build` 皆通過，`/admin` 確實出現在建置輸出路由清單（65 條，+1）。

`make validate-release`：第一次跑在 Playwright E2E 層遇到與 Sprint 257 完全相同的既有 flaky 模式（`at-account-data-rights.spec.ts` 兩案例逾時，同一檔案、同一症狀，與本輪變更的檔案範圍無任何交集，確認是整套 170 個規格平行執行時的登入/註冊 IP 限流偶發碰撞，非回歸）。重跑第二次：**166 passed（+4，與新增的 AT-ADMIN-OVERVIEW-REAL 四案例一致）／4 skipped／0 failed**（5.3 分），act（backend checkstyle/compile/單元/整合測試＋打包）BUILD SUCCESS，schema 對齊無漂移。已寫入 FULL 驗證記錄。

## 6. Push 狀態與雲端 CI

commit `24bf65bb` 已推送至 `origin/main`。雲端 CI run [38290670347](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/38290670347)（以輪詢迴圈等到真正 `completed` 再查核，不單憑 `gh run watch` 訊號）：**`conclusion: success`**，3 個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）全數 success。
