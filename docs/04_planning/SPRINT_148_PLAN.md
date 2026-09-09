# Sprint 148 Plan — DEF-183/DEF-184 技術債清理

**Sprint**: Sprint 148
**日期**: 2026-09-09

---

## 1. 起點

Sprint 147 完成配額強制政策實作並 push 後，`DEFERRED_ITEMS_TRACKER.md` 已無高優先級待辦項目，`SPRINT_147_PLAN.md` §8 留下 4 個「待排程」項目：

| # | 項目 | 性質 |
|---|------|------|
| 4 | DEF-103/104/105 三筆輸入驗證 | 低優先級，defense-in-depth |
| 5 | DEF-183（`getUserMessage()` 佔位符缺陷） | 低優先級，範圍局限 Review 模組 |
| 6 | DEF-184（Controller 層布林開關檢查） | 低優先級，純架構規範落差 |
| 7 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | 新功能，非技術債，需先確認是否要做 |

使用者拍板本輪方向：**DEF-183 + DEF-184 技術債清理**。

---

## 2. §1 DEF-184：Controller 層布林開關檢查搬移至 Service 層

### 2.1 問題

`ProductController.createProduct`/`RoomController.createRoom` 的 `checkFeatureEnabled` 呼叫都正確放在 Service 層，但 `DashboardListingController.createListing`（`POST /v2/dashboard/listings`，前端 `lib/api.ts` 的 `API_ENDPOINTS.create` 實際指向的正式建立路徑）卻是在 Controller 方法內直接呼叫 `featureToggleService.checkFeatureEnabled(...)`，與 PRD §4.4「Feature Toggle 驗證...不得在 Controller 層執行」的明文規定相牴觸。

### 2.2 修法

把 `featureToggleService.checkFeatureEnabled("RETAIL_ENABLED"/"BOOKING_ENABLED")` 從 `DashboardListingController.createListing` 搬進 `ProductService.createProductFromDashboard`/`RoomService.createRoomFromDashboard`（緊接在既有的 `checkQuotaNotExceeded` 之前），與 `createProduct`/`createRoom`（非 Dashboard 版本）既有的做法一致。`DashboardListingController` 移除不再使用的 `FeatureToggleService` 欄位與 import。純搬移，行為不變（切了 toggle 一樣會擋下），但修復後即使未來有其他呼叫端繞過 Controller 直接呼叫 Service 方法，開關檢查依然生效——這正是 PRD 該規範存在的理由。

### 2.3 測試

新增 4 個單元測試，全數**紅燈先行且實際執行**：

- `ProductServiceTest`（+2）：`createProductFromDashboard_retailDisabled_throwsAndDoesNotCreateListing`、`createProductFromDashboard_retailEnabled_createsSuccessfullyAndChecksToggle`
- `RoomServiceTest`（+2）：對稱的兩個案例（`BOOKING_ENABLED`）

**紅燈驗證方式**：`git stash push` 還原 `DashboardListingController.java`/`ProductService.java`/`RoomService.java` 至修復前版本，重跑上述 4 個測試，全數失敗（2 個因 `checkFeatureEnabled` 未被呼叫而 `NeverWantedButInvoked`／`WantedButNotInvoked`）；`git stash pop` 還原修復後重跑，`ProductServiceTest` 15/15、`RoomServiceTest` 17/17 全數通過。

---

## 3. §2 DEF-183：`getUserMessage()` 佔位符從未代入值

### 3.1 問題

`ErrorCode.E_1088`/`E_1089`/`E_1090`（皆 Review 模組）的訊息模板帶 `%d`/`%s` 佔位符。`ReviewService` 其中 4 處呼叫端（`addImage`/`reorderImages` ×2/`validateImageCount`，皆 `E_1088`；`removeImage`，`E_1090`）已用 `String.format(ErrorCode.X.getMessage(), value)` 組出格式化字串，但傳進的是 `BusinessException(errorCode, details)` 的 `details` 參數——`GlobalExceptionHandler.handleBusinessException` 回給前端的是 `ex.getUserMessage()`，其實作固定 `return errorCode.getMessage();`，完全不讀 `details`。使用者實際收到的文字是字面上的「評價圖片數量超過上限（最多 9 張，目前 **%d** 張）」，`%d`/`%s` 從未被替換。

`ErrorCode.getFormattedMessage()` 全庫零呼叫點，本身也帶一個潛藏 bug：`String... args` 簽章下 `String.format(message, (Object) args)` 把整個 `String[]` 強制轉型為單一 `Object`，使 varargs 展開規則將它包成單一元素陣列而非展開——對 `%d` 這類非字串格式碼會直接拋 `IllegalFormatConversionException`。因零呼叫點，此 bug 從未被觸發。

`E_1089`（3 處呼叫端：`createReview`/`updateReview`/`addImage` 的無效圖片檢查）則是另一種情況——**沒有任何一處**嘗試用 `String.format` 組格式化字串，一律傳英文除錯字串（如 `"Invalid image: " + invalidImageId`）作為 `details`。

### 3.2 修法設計

**不採用**「讓 `getUserMessage()` 一律優先回傳 `details`」——`BusinessException` 全庫其餘所有呼叫端（`E_1007`/`E_1087`/`E_3000` 等數十處）的 `details` 都是英文除錯字串（如 `"You can only update your own review"`），依 AI-2418 的既有設計刻意不外洩給使用者；若讓 `getUserMessage()` 一律回傳 `details`，會讓全站其餘所有 ErrorCode 的使用者訊息瞬間變成英文除錯字串，是影響面遠超本次修復範圍的破壞性變更。

**採用**：新增 `BusinessException.withFormattedMessage(ErrorCode, Object...)` 靜態工廠方法，讓 `getUserMessage()` 在**這個新入口下**優先回傳代入實際值後的文字；既有的 `BusinessException(ErrorCode)`/`BusinessException(ErrorCode, String details)` 兩個建構子完全不受影響，`getUserMessage()` 仍固定回傳原始模板。同時修正 `ErrorCode.getFormattedMessage` 簽章由 `String... args` 改為 `Object... args`，讓 `String.format(message, args)` 正確展開多值。

`ReviewService` 7 處呼叫端改用新工廠方法：

| 方法 | ErrorCode | 代入值 |
|------|-----------|--------|
| `createReview` | E_1089 | 無效圖片 ID |
| `updateReview` | E_1089 | 無效圖片 ID |
| `addImage`（無效圖片） | E_1089 | 無效圖片 ID |
| `addImage`（數量超限） | E_1088 | 目前張數 |
| `removeImage` | E_1090 | 實際索引 |
| `reorderImages`（數量不符） | E_1088 | 目前張數 |
| `validateImageCount` | E_1088 | 目前張數 |

**刻意不修復的 1 處**：`reorderImages` 的圖片集合不一致分支（`E_1089`，"Reorder must contain the same set of images"）維持原樣。此分支的 `%s` 佔位符沒有自然對應的單一值可代入——不是像「一個無效圖片 ID」那樣的單一值，而是「新舊集合不一致」這個整體判斷，強行塞一個值進去會是牽強附會、失去訊息原意。此路徑的使用者體驗維持修復前現狀（看到字面 `%s`），非本輪範圍，如實登記於下方 §5。

### 3.3 測試

新增 10 個單元測試，全數**紅燈先行且實際執行**：

- 新建 `BusinessExceptionTest`（7 個）：直接測試機制本身——`withFormattedMessage` 對單一 int/String 參數正確代入、`details` 與 `getUserMessage()` 一致、既有 2-arg 建構子與無參數建構子行為不變（回歸守衛，證明本次修復未影響全站其餘 ErrorCode）、`ErrorCode.getFormattedMessage` 多值/零值正確處理。
- `ReviewServiceTest`（+3）：`addImage_atQuota_userMessageShowsActualCount`、`removeImage_invalidIndex_userMessageShowsActualIndex`、`addImage_invalidMedia_userMessageShowsActualImageId`——針對 `addImage`/`removeImage` 端到端驗證訊息內容（`M08ReviewImageIntegrationTest` 把 `ReviewService` 整個 mock 掉，無法驗證訊息內容，故此 3 個屬全新覆蓋而非重複造測試）。

**紅燈驗證方式**：`git stash push` 還原 `BusinessException.java`/`ErrorCode.java`/`ReviewService.java` 至修復前版本，重跑 10 個新測試：`ReviewServiceTest` 3 個斷言失敗（實際訊息內容為 `"評價圖片數量超過上限（最多 9 張，目前 %d 張）"`/`"找不到索引 %d 的評價圖片"`/`"無效的評價圖片：%s"`，證明缺陷真實存在）；`BusinessExceptionTest` 7 個因 `withFormattedMessage` 方法不存在而編譯錯誤。`git stash pop` 還原修復後重跑，`BusinessExceptionTest` 7/7、`ReviewServiceTest` 21/21 全數通過。

---

## 4. 驗證結果

`mvn -o compile`/`mvn -o test-compile` 每次生產程式碼變更後立即編譯，`checkstyle:check` 0 violations。全量回歸：

`make test-db-up`（首次執行時測試 DB 未啟動，`SellerDashboardServiceCacheTest` 等 `@ActiveProfiles("integration-test")` 測試因 `Failed to load ApplicationContext` 失敗，屬既有環境前置需求（見 [[backend-integration-test-profile-needs-real-db]]），非本輪程式碼回歸；啟動 DB 後重跑即通過）後 `mvn -o verify`（含 checkstyle main+test、failsafe 整合測試）**BUILD SUCCESS**：

- **單元測試**：**1224**（相對 Sprint 147 的 1210，+14，與本輪新增測試數一致），0 failures/errors
- **整合測試**：**477**（與 Sprint 147 持平，本輪未新增整合測試方法），0 failures/errors/skipped
- **checkstyle**（main+test 兩個 execution）：**0 violations**
- **前端**：本輪零前端檔案變動，未執行 `tsc`/`eslint`/`build`（無變更範圍可驗）
- **schema**：本輪未動任何 entity 欄位或 migration，未執行 `make validate-schema`（無變更範圍可驗）

---

## 5. 範圍外（延後）

- **`reorderImages` 的 `E_1089` 集合不一致分支**：`%s` 佔位符沒有自然對應值可代入，維持修復前現狀（見 §3.2）。純粹是同一個 ErrorCode 下不同呼叫端的情境差異，非新登記的 DEF，僅在此如實記錄。
- **DEF-103/104/105 三筆輸入驗證**：Sprint 147 §8 item 4，本輪未排入，維持待排程。
- **`/dashboard/tenants/[id]/features` 顯示頁補上配額用量**：Sprint 147 §8 item 7，本輪未排入，需先確認是否要做（新功能，非技術債）。

---

## 6. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | DEF-184 修復（Controller 層布林開關檢查搬移） | Sprint 147 §8 item 6 | ✅ 完成 | 移入已完成延後項目 |
| 2 | DEF-183 修復（`getUserMessage()` 佔位符機制） | Sprint 147 §8 item 5 | ✅ 完成 | 移入已完成延後項目 |
| 3 | `RELEASE_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⬜ 待回填 | 依慣例留待下一 Sprint 開工回填，或成本為零時同日回填 |
| 4 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已在追蹤器 |
| 5 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | S147 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做（新功能，非技術債） |

---

## 7. 誠實揭露總結

- DEF-183 的修法刻意只新增一個選擇性入口（`withFormattedMessage`），不改動既有兩個建構子的行為——`BusinessExceptionTest` 的 2 個回歸測試（`twoArgConstructor_userMessageIgnoresDetails_unchangedFromBeforeFix`/`singleArgConstructor_userMessageReturnsRawTemplate`）明確驗證這一點，避免「順手把 details 一律外洩」這個看似省事、實則破壞 AI-2418 既有設計的簡化陷阱。
- `reorderImages` 的集合不一致分支刻意不強行套用 `withFormattedMessage`——同一個 ErrorCode（`E_1089`）底下不同呼叫端的情境不同，機械式全部套用會製造出語意不通的訊息，如實記錄為範圍外而非默默留著不提。
- `mvn -o verify` 第一次執行因測試 DB 未啟動而失敗（`SellerDashboardServiceCacheTest` 等 3 個 `ApplicationContext` 載入錯誤），依 CLAUDE.md「先看 log 找 actual error，不盲目猜測」原則確認為既有環境前置需求後啟動 DB 重跑，非本輪程式碼問題，如實記錄而非略過不提。
