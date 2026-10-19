# Sprint 286 Plan — DEF-330(a)/(c) 複核：通知模板權限修復＋租戶呼叫處守門測試

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 285 總結，回覆「請繼續完成項目！」，沒有任何 `==>` 逐項回覆。依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）：這**不是**對總結裡「等你決定」項目的授權，`DEF-392`（賣家評價管理 UI 優先順序）維持不替使用者決定、本輪不碰。改做總結自己列為「還沒做」且 AI 能獨立處理的項目——總結的「下一步」段落列了兩個方向：`DEF-330c`（租戶過濾守門測試）與「挑一個沒走過的真實流程再做一次真實跨角色實測」。

另外，`DEF-393` 的登記文字裡有一句未經逐輪查證的觀察：「Sprint 281~284 的全量驗證紀錄皆只提到 `mvn verify`，未見 E2E 結果」。開工前先查證這句話本身，確認是否為真。

### 1.2 查證：Sprint 281~284 是否真的沒跑 E2E

讀 `RELEASE_TRACKER.md` 對應列：Sprint 281~284 push 前使用的都是「本機輕量守門（act Backend Unit＋schema 漂移＋frontend）」，雲端 CI 的 3 個 job（Backend Unit／Backend Integration／Frontend）本身也不含 E2E。確認 `make validate-release`（含 E2E）是本地手動觸發的全量驗證，不是自動 pre-push hook 的一部分（符合既有 [[local-first-ci-strategy]] 的既定三層守門設計：pre-commit 快速檢查、pre-push 輕量 act、`make validate-release` 手動全量）。

**結論**：DEF-393 的觀察屬實，但不是遺漏或設計缺陷——E2E 本來就是「按需手動全量」而非「每次 push 自動跑」，Sprint 281~284 沒跑 E2E 符合既有設計，不是制度漏洞。且 Sprint 285 的 `make validate-release` 已經把這段期間累積的變更重新跑過一次完整 E2E 並全綠（僅抓到 DEF-393 自己，不是 281~284 的程式碼造成的新問題），不需要往回逐輪補跑。本輪不改動任何 CI/CD hook（CLAUDE.md 對此有嚴格規範）。

### 1.3 選定方向：DEF-330(c) 守門測試 + DEF-330(a) 複核

選 `DEF-330(c)`（而非開一輪新的真實跨角色實測）的理由：
- 範圍明確、已有既定模式可循（比照 `ApiRouteDocDriftTest`／`ErrorCodeDocDriftTest` 的「掃描＋登記清單，drift 就失敗」風格），不需要重新架設真實 Postgres/Redis/瀏覽器全棧。
- `DEF-330(c)` 從 Sprint 234 登記至今一直是「待評估」，是本輪少數「範圍明確、無需產品決策」的現成候選。
- 寫守門測試的過程必然要讀過全部呼叫處所在的 Controller／Service，順手把 `DEF-330(a)` 當年「未驗證前端是否有買家可達畫面」的懸而未決問題一併查清楚（三個端點就在呼叫處清單裡）。

### 1.4 假設與限制（可被使用者推翻）

- `DEF-330(c)` 的守門測試是**機械清點**（登記目前每個檔案的呼叫處數量，增減就失敗），不是語意稽核。150+ 處現有呼叫處逐一判斷「是否已有 `isStoreTenant`／ownership 等效防護」超出一次 Sprint 的合理範圍，本輪只對複核 `DEF-330(a)` 時實際讀到的檔案（`ShippingTemplateController`／`NotificationTemplateController`／`PricingController`／`PricingService`）做了判斷，其餘檔案的分類（見 `TenantContextCallSiteDriftTest.BASELINE` 的分組註解）是依「誰呼叫這個 Service／這個 Controller 要求什麼權限」的粗略判斷，不是逐行覆核。
- `DEF-330(a)` 的「低風險」結論成立的前提是「系統租戶底下目前沒有真實資料」——這是觀察現有程式碼寫入路徑得出的推論，不是窮舉證明；若未來有任何功能直接寫入系統租戶（例如全域範本管理），結論需要重新評估。

## 2. 執行紀錄

讀 `DEFERRED_ITEMS_TRACKER.md` DEF-330 完整登記內容，列出全部 34 個含 `getCurrentTenant()` 呼叫的生產檔案（`grep -rn` 排除 `.bak` 備份檔與 `TenantContext.java` 自身宣告行），逐檔計算排除註解行後的真實呼叫行數。

複核 `DEF-330(a)` 三個端點：
- `GET /v2/dashboard/pricing/rules`（`PricingController.getRules` → `PricingService.getRules`）：讀碼確認無篩選條件分支已受 `DEF-255`（Sprint 185）保護，查詢的是呼叫者自己的租戶（BUYER 為系統租戶，目前無資料）。
- `GET /v2/shipping-templates`（`ShippingTemplateController.getTemplates`）：確認與同控制器 3 個 mutation 端點（`product:create/update/delete`）相比，list 端點用的 `product:read` 是 BUYER 也持有的共用權限，且運費模板沒有專屬權限可借用。
- `GET /v2/notification-templates`／`GET /v2/notification-templates/{id}`：確認 `RolePermissionMapping` 明文將 `notification_template:*` 標註為 STORE_OWNER（全權）／STORE_STAFF（唯讀）／ADMIN 專屬，但這兩個 GET 端點用的是 `isAuthenticated()`，與同控制器的 `render`（已用 `notification_template:read`）及三個 mutation 端點不一致——確認為真實疏漏。

前端可達性查證：`grep` 確認三者皆有前端呼叫點（`/dashboard/pricing/rules`、`/dashboard/pricing/calendar`、`ShippingTemplateList.tsx`、`/dashboard/notifications/page.tsx`），且全站 `/dashboard/*` 沒有任何 `layout.tsx` 或角色守門 hook/middleware——確認三者皆為買家可技術性觸及，非僅理論風險。

讀 `NotificationTemplateService` 全部 5 個方法時，另外發現與 `DEF-330(a)` 方向相反的獨立問題：`RolePermissionMapping` 的 ADMIN 區塊註解明文此權限為「跨租戶的平台端權限」，但 Service 層完全沒有 `isSuperAdmin` 旁路（對照 `PricingController`／`PricingService` 的既有慣例），ADMIN 實際上無法跨租戶管理任何店鋪的通知模板——登記為獨立的 `DEF-394`。

## 3. 發現與結論

### 3.1 ✅ DEF-330(a) 部分修復：`notification-templates` list/detail 權限不一致

`NotificationTemplateController` 的 `getTemplates`（list）／`getTemplate`（detail）原用 `@PreAuthorize("isAuthenticated()")`，任何登入者（含 BUYER）皆可呼叫；同控制器的 `render`／`createTemplate`／`updateTemplate`／`deleteTemplate` 皆正確要求 `notification_template:*`。`RolePermissionMapping` 第 119-120 行註解明文「通知模板為租戶範圍資料...店主管理自己租戶的模板；前端入口即店家後台 `/dashboard/notifications`（Sprint 128，DEF-073）」——list/detail 的寬鬆權限違反此文件明載的設計意圖。

**已修復**，見 §4.1。

### 3.2 🟢 DEF-330(a) 複核：`shipping-templates`／`pricing/rules` 維持現狀（已登記風險評估）

`shipping-templates` 的 list 端點沿用 `product:read`，與同控制器 3 個 mutation 端點的 `product:create/update/delete` 不一致，但運費模板模組沒有專屬權限可借用；若改為要求 `product:create/update/delete` 之一，`STORE_STAFF`（僅有 `product:read`，比照本檔案 FAQ／知識庫／媒體模組的「員工在設定類模組唯讀」既定慣例）會被連帶擋下，若改為新增專屬 `shipping_template:*` 權限，需同步決定是否授予舊角色 `SELLER`（目前靠 `product:read` 間接持有）避免功能退化。兩種改法都超出「這是不是 bug」的範圍、進入「怎麼設計這個權限」的產品決策，故本輪**不修改**，已在 `DEFERRED_ITEMS_TRACKER.md` DEF-330(a) 補充複核結論。

`pricing/rules` 的無篩選條件分支已受 `DEF-255`（Sprint 185）保護，BUYER 可達但只查到系統租戶名下的空清單，維持現狀不變。

### 3.3 🟡 DEF-394（新登記，未修復）：`NotificationTemplateService` 的 ADMIN 跨租戶權限從未真正實作

複核 §3.1 時讀碼發現，與買家越權方向相反：ADMIN 被授予 `notification_template:*` 意圖是跨租戶平台管理，但 Service 層 5 個方法全部只認呼叫者自己的 `tenantId`，ADMIN 實際上無法對任何真實店鋪的模板進行任何操作。詳見 `DEFERRED_ITEMS_TRACKER.md` DEF-394。本輪未修復——需先確認這是否為真實產品需求，修復方式（新增 `isSuperAdmin` 旁路）牽涉「ADMIN 该不该能跨租戶改任何店鋪的通知模板」的授權範圍決策，不是單純的程式碼疏漏修正。

### 3.4 ✅ DEF-330(c) 完成：新增呼叫處清點守門測試

新增 `TenantContextCallSiteDriftTest`，詳見 §4.2。

### 3.5 ✅ DEF-395（新登記並已修復）：`registerAndLogin` 註冊步驟無限流重試，E2E 全量跑必然確定性失敗兩個測試

跑 `make validate-release` 的 E2E 關卡時意外發現，與本輪通知模板權限修復無關。詳見 §4.3 的完整追查過程與修復內容。

## 4. 本輪異動

### 4.1 DEF-330(a) 修復：`notification-templates` list/detail 改回店主專屬權限

- `NotificationTemplateController.java`：`getTemplates`／`getTemplate` 的 `@PreAuthorize` 由 `isAuthenticated()` 改為 `hasAuthority('notification_template:read')`，與 `render` 端點一致。
- `M09NotificationTemplateIntegrationTest.java`：新增 `createBuyerTokenAndGetToken`（註冊後維持預設 BUYER，不像既有 `createTestUserAndGetToken` 提升為 STORE_OWNER）與兩個新案例：
  - `testGetTemplates_buyerForbidden`：BUYER 呼叫 list 應 403。
  - `testGetTemplateDetail_buyerForbidden`：BUYER 呼叫 detail 應 403——刻意對一個 **`tenantId=null`（全域）** 的範本呼叫，而非既有 `testTemplate`（屬 `testTenantId`）。原因：對後者呼叫無論權限修復與否都會被 `NotificationTemplateService.getTemplate` 既有的 `template.getTenantId() != null && !equals(tenantId)` 跨租戶比對擋下，驗證不到這次的權限修復；全域範本的比對會短路放行，唯一擋買家的只剩本次新加的權限註解。
- **突變驗證**：還原兩處 `@PreAuthorize` 為 `isAuthenticated()`，重新編譯後重跑這兩個新案例，確認兩者皆正確轉紅（`Status expected:<403> but was:<200>`）；還原修復後重跑整檔 14 個案例全線轉綠。

### 4.2 DEF-330(c) 完成：`TenantContextCallSiteDriftTest`

- 新檔 `backend/src/test/java/com/nextkey/ecommerce/shared/tenant/TenantContextCallSiteDriftTest.java`：比照 `ApiRouteDocDriftTest` 的風格，掃描 `backend/src/main/java` 全部 `*.java`（排除 `TenantContext.java` 自身宣告行；trim 後以 `*`／`//` 開頭的註解行不計入），逐檔統計 `getCurrentTenant()` 真實呼叫行數，與硬編碼的 `BASELINE`（34 個檔案，依模組分組並附簡短複核註記）逐一比對；數量增減（新檔案出現、既有檔案呼叫處變多或變少）皆會讓測試失敗，並在斷言訊息列出確切是哪個檔案、登記值與實際值分別是多少。
- **突變驗證**：於 `SettlementGenerator.java` 暫時插入一行重複呼叫（`2`→`3` 處），確認守門測試正確轉紅並精準指出該檔案；還原後重新編譯確認回到綠燈。
- 這道守門是 drift 鎖定機制，不是語意稽核——它不證明「現有呼叫處都寫得對」，只保證「新增呼叫處會被看見」，逼著加的人（或未來的我）先讀過 `TenantContext` class 開頭的系統租戶風險說明再決定要不要加防護，而不是悄悄漏過去。

### 4.3 DEF-395 修復：`registerAndLogin` 補上註冊端點限流重試

第一次執行 `make validate-release` 時 E2E 關卡 2 個失敗：`at-account-data-rights.spec.ts` 的匯出資料／刪除帳戶兩個測試。**先假設是全量 174 個 spec 並行跑造成的資源競爭**（`playwright.config.ts` 本機執行 `retries: 0`、`workers: undefined`），單獨重跑 `make validate-e2e` 驗證——**同兩個測試再次確定性失敗**（不是隨機換成別的測試失敗），推翻資源競爭假設，改為深入讀碼追根因。

讀 `error-context.md`（Playwright 失敗時自動留存的頁面快照）發現關鍵線索：共用 `StorefrontHeader` 顯示「登入／註冊」連結（判定為未登入），但 `/account` 頁面主內容仍正常渲染；匯出測試的快照額外顯示一個「Authentication required」inline alert。對照 `frontend/src/app/(auth)/account/page.tsx`：「刪除帳戶」卡片靠 `canDeleteAccount = role === 'BUYER'` 控制渲染，`role` 來自 `useSyncExternalStore` 讀 `AuthService.getCurrentUser()?.role`——若 localStorage 根本沒有有效的登入資料，這整張卡片不會渲染（不是「慢出現」，是「完全不存在」，與 30 秒逾時找不到元素的症狀吻合）。追到 `frontend/e2e/helpers/auth.ts` 的 `registerAndLogin`：註冊表單送出後用 `page.waitForURL('**/login**', { timeout: 6000 }).catch(() => {})` 等待導回登入頁，若被 `LoginRateLimitFilter`（Sprint 183 DEF-250 擴大覆蓋 `/v2/auth/register`，與登入端點各自以路徑+IP 獨立計量）限流導致逾時，這個 `.catch` 直接吞掉失敗，函式判斷仍停在 `/register`（非 `/login`）而**整個跳過後續的登入步驟**，最終回傳一個 `userId` 為空字串但不拋出任何例外的「假成功」。既有的登入步驟（`submitLogin`）十年來就有限流偵測＋重試機制，**註冊步驟完全沒有對應保護**——先前只對登入端點做過併發壓力驗證（Sprint 234 實測記錄），從未同等驗證過註冊端點。

**修復**（`frontend/e2e/helpers/auth.ts`）：
- 新增 `submitRegister` 函式，比照既有 `submitLogin` 的限流偵測＋重試邏輯；註冊頁沒有 `data-testid` 標記錯誤訊息（讀碼確認 `register/page.tsx` 的錯誤 `<div>` 無 testid），改用 `page.getByText('速率限制')` 文字比對偵測。重試次數（沿用既有 `RATE_LIMIT_MAX_RETRIES`/`RATE_LIMIT_RETRY_WAIT_MS` 常數）耗盡仍失敗時明確 `throw`。
- `submitLogin` 原本「不是被限流的其他失敗」與「限流重試用盡」兩種分支都是靜默 `return`，拆分為：前者維持原行為返回（`registerAndLogin` 的「先試登入、失敗就改註冊」正依賴這個分支，不可改為拋例外，否則會破壞既有的「帳號不存在」正常回退路徑）；後者（限流且重試用盡，對任何呼叫端都不是合理分支）改為明確 `throw`。
- `registerAndLogin` 回傳前新增最終防呆：`userId` 為空字串就 `throw`，避免任何前面沒覆蓋到的靜默失敗路徑繼續蒙混過關。

**驗證**：`npx tsc --noEmit` 確認型別正確。單獨重跑 `make validate-e2e`——修復後 **170 passed／4 skipped／0 failed**，`at-account-data-rights.spec.ts` 兩個測試皆轉綠，且是在與先前兩次失敗相同的全量 174-spec 並行環境下驗證（非隔離單檔跑），直接證明修復解決了全量併發下的真實問題。**限制**：未反向驗證「限流重試用盡後真的會 throw」這條新增的錯誤路徑本身（沒有刻意製造出持續限流到重試上限的情境），只驗證了「加入重試後，原本必然重現的問題不再發生」這個外顯結果；且本次診斷過程中，第一次背景執行 `make validate-release` 曾因指令用 `tee` 管線，被 shell 回報的 exit code 誤判為成功（實際是 E2E 失敗的 `make: *** [validate-release] Error 2`）——後續改用 `> file 2>&1; echo "MAKE_EXIT_CODE=$?"` 的寫法才取得真實 exit code，過程本身是對 [[pipe-masks-exit-code-in-background-tasks]] 這則既有記憶的又一次真實撞上。

### 4.4 `make validate-release` 最終執行結果

四次嘗試：
1. 第一次全量：E2E 關卡 2 個失敗（`at-account-data-rights.spec.ts`，見 §3.5／§4.3）。
2. 單獨重跑 `make validate-e2e` 確認「不是隨機閃爍」：同兩個測試再次確定性失敗，推翻資源競爭假設，深入讀碼追出 DEF-395 根因。
3. 修復後單獨重跑 `make validate-e2e`：170 passed／4 skipped／0 failed。
4. **完整 `make validate-release` 全數通過**：Checkstyle 0 違規（unit 與 integration job 皆同）；整合測試（failsafe）**865/865**（較 Sprint 285 基準 863 增加 2，對應本輪新增的 2 個 `M09NotificationTemplateIntegrationTest` 買家 403 案例）；3 個 act job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 `Job succeeded`；schema 漂移驗證兩次皆通過（`validate-schema` 與 `validate-e2e` 各自啟動一次 backend 對齊乾淨 DB）；E2E **170 passed／4 skipped／0 failed**。`make validate-release` 回報「完整測試程序全數通過（act + schema + E2E）；已寫 FULL 記錄，30 分內 git push 直接放行」。

**過程中的工具陷阱**（記錄供日後參考）：第一、二次執行時用 `command | tee file` 的管線寫法，shell 回報的整體 exit code 反映 `tee` 而非 `make`，一度誤判為「背景指令成功」，實際 `make` 已以 `Error 2` 失敗；改用 `command > file 2>&1; echo "MAKE_EXIT_CODE=$?"`（無管線）才拿到 `make` 真實的 exit code。

## 5. 後續（未動工，不在本輪範圍內）

- `DEF-392`（賣家評價管理 UI）、`DEF-394`（ADMIN 跨租戶通知模板權限）、`shipping-templates` 專屬權限設計：三者皆需使用者裁決，留待下一輪。
- `TenantContextCallSiteDriftTest.BASELINE` 列出的 150+ 現有呼叫處，本輪只複核了 §2 提到的 4 個檔案；其餘檔案的分組註記是粗略判斷，未來若要逐一語意稽核，可按模組分批進行。
