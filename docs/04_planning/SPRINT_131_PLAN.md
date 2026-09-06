# Sprint 131 Plan — DEF-080~091 契約漂移類 12 項修復

**Sprint**: Sprint 131
**日期**: 2026-09-06

---

## 1. 本輪範圍

Sprint 130 收尾時留下的候選清單為 DEF-080~091（12 項契約漂移類缺陷，詳見 `DEFERRED_ITEMS_TRACKER.md`），本輪使用 Workflow 唯讀平行調查全部 12 項現況與建議修法後，經 `AskUserQuestion` 拍板全部納入本輪範圍（含原本標記「需 UI/UX 設計」的 DEF-086，因調查發現有現成 pattern 可套用，工作量遠小於原估）。

**AskUserQuestion 拍板紀錄**：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | DEF-087 調查中意外發現 `RoomService` 的 `location`/`maxGuests` 篩選完全無 tenantId 過濾（疑似跨租戶資料外洩），如何處理？ | 一併修復（推薦） |
| 2 | DEF-087 keyword 搜尋修復範圍：是否讓 keyword/location/maxGuests 從互斥改為可同時 AND 套用？ | 改為可同時套用（推薦） |
| 3 | DEF-086 本輪要不要一併做（原估 5 SP，調查後估 2-3 SP）？ | 本輪一併做（推薦） |
| 4 | DEF-080 知識庫建立文章（死路徑）要不要現在動？ | 順便修後端 `authorId` 設計缺陷 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即執行編譯（`mvn compile`/`npx tsc --noEmit`）+ 相關測試（Mockito 單元測試或真實 PostgreSQL 整合測試），絕不累積多項後才驗證。12 項依風險/複雜度排序，由簡入繁逐項完成。

---

## 3. 各項修復摘要

### §1 DEF-087：房源搜尋 keyword 失效 + 跨租戶範圍缺口

見 `DEFERRED_ITEMS_TRACKER.md` 完成項說明。新增 `M02RoomSearchIntegrationTest`（真實 PostgreSQL + 真實 Repository，非 `@MockBean`，6 案例）直接驗證 Specification 查詢的實際過濾行為與租戶隔離，而非僅驗證委派呼叫（Mockito 無法驗證 Specification 內容）。

### §2 DEF-086：M09 通知模板建立/編輯 UI

只改 `frontend/src/app/dashboard/notifications/page.tsx`：新增 `TemplateFormState`、`startCreate`/`startEdit`/`cancelForm`/`handleSubmit`，比照 `faq/categories/page.tsx` 既有 modal pattern。MVP 不做手動 variables 欄位、不做富文本編輯器。

### §3 DEF-082：ERP 採購單供應商名稱補查

`PurchaseOrderService.toDto()` 拆為單參數（查一次）/雙參數（外部傳入）兩版本；`listPurchaseOrders()` 改用 `supplierRepository.findAllById()` 批次查詢避免 N+1。

### §4 DEF-081：ERP 採購單 poNumber 欄位對齊

前端 `orderNumber` → `poNumber`，2 檔案。

### §5 DEF-083：知識庫 tags 全鏈路接線

`KnowledgeBaseService` create/update/toDto 三處補上賦值，沿用既有逗號分隔 TEXT 欄位設計。

### §6 DEF-080：知識庫 authorId 伺服器端推導

`authorId` 改由 `TenantContext.getCurrentUser()` 推導，移除 `CreateKnowledgeArticleRequest.authorId` 欄位。

### §7 DEF-084：合併結帳電話空白修正

`checkout/mixed/page.tsx` 送出前補 `.replace(/\s/g,'')`。

### §8 DEF-085：媒體更新 categoryId 放寬為選填

移除 `UpdateMediaRequest.categoryId` 的 `@NotNull`。

### §9 DEF-088：冪等衝突錯誤碼改引用 enum

`BookingController`/`CheckoutController` 改用 `ErrorCode.E_6005.getCode()`。

### §10 DEF-089：刪除損壞的重複上傳函式

刪除 `cms.ts` 的 `uploadMedia()`（零呼叫點、與 `uploadMediaMultipart()` 功能重複且回應型別錯誤）。

### §11 DEF-090：採購單更新型別縮小

`purchaseOrder.ts` 的 `PurchaseOrderUpdateRequest` 縮小為僅 `{notes?: string}`。

### §12 DEF-091：PriceBreakdown 型別對齊

`listing.ts` 的 `PriceBreakdown` 補上 `basePrice`/`adjustedPrice` 等欄位。

---

## 4. 調查過程中發現、另開追蹤的項目

- **DEF-094**：`checkout/page.tsx`（純訂房結帳頁）與 DEF-084 相同結構的電話驗證/送出不一致 bug
- **DEF-095**：ERP 採購單編輯頁「更新訂單」按鈕空實作，點擊無反應（範圍大於 DEF-090 本身）
- **DEF-096**：M18 媒體資產庫（`/v2/media/upload` JSON 端點 + `media.ts` 的 `uploadMediaAsset()`）完整實作但從未串接使用的半成品功能

三項皆依 Rule 3（精準改動）不併入本輪修復，登記入 `DEFERRED_ITEMS_TRACKER.md` 待排程。

---

## 5. 驗證

- 12 項逐一編譯+測試，每項完成立即驗證，未累積開發（依 CLAUDE.md 強制規則）
- 新增/修改測試：`PurchaseOrderServiceTest`（+2）、`KnowledgeBaseServiceTest`（+2）、`M18MediaIntegrationTest`（+1，同步調整 5 個 `@Order`）、`BookingControllerE2ETest`（同步修正 1 個既有斷言，原本鎖定 bug 的底線版本改為連字號）、`RoomServiceTest`（無異動，既有 11 案例確認不受影響）、新增 `M02RoomSearchIntegrationTest`（+6，真實 PostgreSQL + 真實 Repository，非 `@MockBean`，直接驗證 Specification 查詢的實際過濾與租戶隔離行為）
- 後端全量回歸：`cd backend && mvn -o verify` **BUILD SUCCESS**（17:55 min）
  - 單元測試 **1100**（相對 Sprint 130 的 1096，+4，恰等於 `PurchaseOrderServiceTest`+2、`KnowledgeBaseServiceTest`+2）
  - 整合測試 **472**（相對 465，+7，恰等於 `M02RoomSearchIntegrationTest`+6、`M18MediaIntegrationTest`+1）
  - 0 Failures / 0 Errors；checkstyle（main+test）**0 violations**；PMD 通過
- 前端：`npx tsc --noEmit` 通過、`npx eslint`（逐檔案）無新增錯誤（僅既有警告）、`npm run build` 全站生產建置通過（確認刪除 `cms.ts` 的 `uploadMedia()` 死碼未破壞任何呼叫端）
- DEF-086 已於本機真實環境端到端驗證（`make test-db-up` + `mvn spring-boot:run` 本機啟動後端 + `npm run dev` 前端 + Playwright 腳本驅動瀏覽器）：以 STORE_OWNER 測試帳號登入，建立模板成功寫入並顯示於列表、編輯模板成功預填既有值並更新，全程 0 個 console 錯誤
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用
