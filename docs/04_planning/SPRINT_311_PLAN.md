# Sprint 311 Plan — DEF-426（日曆手動改價回 E-2004）、DEF-429（新增申請時間欄位）與 DEF-428（點需審核功能後的畫面）

## 1. 起點與範圍

### 1.1 起點

[Sprint 310](SPRINT_310_PLAN.md) 收尾，我用 `AskUserQuestion` 問了三題，使用者**三題都選了建議項**（這是工具回傳的真實互動答案，不是我對「請繼續」的推論）：

| 題 | 使用者的選擇 | 授權範圍 |
|---|---|---|
| 要現在 push 嗎 | 現在 push | Sprint 310 的兩個 commit（`0532edc0`、`164ca615`）→ 已於 `035a14e6..164ca615` push，pre-push 輕量守門通過，雲端 CI run [40673442467](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40673442467) 三個 job 皆 success，整合測試 957／0 失敗 |
| `DEF-426` 日曆手動改價在開關關閉時 | 回 `E-2004` | 實作選項 (a) |
| `DEF-429` 「已申請」怎麼表示 | 新增申請時間欄位 | 實作方案 A（含 Flyway 遷移） |

範圍：`DEF-426`、`DEF-429`，以及與 429 互相影響的 `DEF-428`（追蹤表寫「不需產品決策，建議跟 429 一起修」；使用者沒有逐題點名它，**這是我替使用者做的決定**，見 §9）。不碰 `DEF-430`～`DEF-432`、`DEF-419`。

### 1.2 成功標準（開工時定義）

1. `DEF-426`：兩個日曆手動改價端點在呼叫者租戶的動態定價開關關閉時回 `403 E-2004`、不建立規則；開關開啟時照常。
2. `DEF-429`：待審核只認店主真的申請過；核准開店建的預設列是「已停用」；店主端與管理員端一致；申請、重複申請、撤回、核准、駁回、關閉的狀態轉換都有測試。
3. `DEF-428`：點需審核功能後，畫面與後端一致（開關仍關、徽章待審核、提示不謊稱已啟用）。
4. 紅燈先行；每個新增的判斷都經突變驗證；全量後端 `mvn -o clean verify` 與完整 E2E 套件通過；Flyway 遷移與實體對齊（`ddl-auto=validate`）；SRD／PRD／API 文件同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| `DEF-426`「各加一行開關檢查」（我寫在選項說明裡） | **範圍屬實**：`PricingService` 會建出規則的方法只有 `createRule`（已有檢查）、`setCalendarPrice`、`overridePrice`（沒有）；`updateRule`／`deleteRule` 不建規則，本題不涵蓋。端點兩個（`PricingController` 的 `POST /calendar/price`、`DashboardPricingController` 的 `POST /rules/{ruleId}/override`），權限都是 `room:update`。受影響的既有測試類別 4 個（`PricingServiceTest`、`M12PricingIntegrationTest`、`PricingRulePersistenceE2ETest`、`PlatformSelfRunWriteIsolationE2ETest`）修復後全過 |
| `DEF-429` 方案 A「改 TenantService 三處與 AdminService 核准路徑；SRD 的 DDL 文件要同步」 | **文件範圍我寫少了**。生產程式：`TenantService` 三處（`resolveFeatureStatus`、`buildFeatureToggleResponse` 的 `requestedAt`、`updateFeatureToggle`）、`AdminService` 兩個方法（`setFeatureToggle`、`updateTenantFeatureToggle`）；寫入 `isEnabled` 的生產程式只有這兩個類別；沒有原生 SQL 碰這張表；`initializeFeatureToggles` 不用改。文件：除了 SRD 的 DDL，`make validate-schema-doc` 還要求 **PRD §8.2.2 的欄位表**補上 `requested_at`（`--write` 不會自動處理），選項說明沒寫到這一項 |
| 「既有資料無法分辨預設列與真申請」（選項說明裡的說法） | 讀碼推論，沒有用生產資料驗證（沒有生產資料庫）：申請需審核功能時資料列的 `is_enabled` 不變，`@DynamicUpdate` 不產生 UPDATE，`updated_at` 也不變；稽核日誌的前後值同樣看不出差別。所以遷移不回填，既有資料列全為 `NULL` |
| 管理員頁的「N 項功能待審核」是否要改前端 | **不用**：它是 `requiresAdminReview && status === 'PENDING'` 的筆數（Sprint 310 讀碼），後端的 `status` 修好後自動只算真的申請過的；E2E 管理員規格的斷言從 `/項功能待審核/` 加強成 `/1 項功能待審核：民宿預訂功能/` |
| 前端 `Switch` 與 PUT 回應 | PUT 回應的 `newState`／`status` 已足夠（`PENDING_APPROVAL`＝已送出、尚未生效），不需要再多打一次 GET |

## 2. DEF-426：日曆手動改價回 E-2004

`PricingService.setCalendarPrice` 與 `overridePrice` **第一步**（比查房源／規則還早）呼叫 `featureToggleService.checkFeatureEnabled("DYNAMIC_PRICING_ENABLED")`，與 `createRule` 完全一致；未啟用丟 `E-2004`（HTTP 403）。放在最前面的理由是不查任何東西、不寫規則、不記稽核——單元測試以 `verify(never())` 鎖住這個順序。

**真實棧測試**（`StoreToggleGatesApplicationE2ETest`，沿用 Sprint 309 的兩家店鋪）：同一位店主、同一個房源、同一組請求，開關關閉時兩個端點都回 `403 E-2004` 且 `pricing_rules` 筆數不變；只翻轉開關（其餘完全相同）→ 兩個端點都 `200`、各建立一條規則。這樣擋的是「開關」而不是別的原因（擁有權、房間不存在……）。

**紅燈**：對未修版本，單元 2／2 紅（`expected: E_2004`）；真實棧第一版紅在 `404`——**這是固件問題不是被測行為**：`setCalendarPrice` 先查 `rooms` 列，而這個類別的房源只有 `listings` 列。補上 `rooms` 列（只在這個案例內建立、`finally` 清掉，不改變其他案例的固件）後，紅燈變成預期的 `Expected status code <403> but was <200>`。

## 3. DEF-429＋DEF-428：申請時間

### 3.1 資料與狀態轉換

`V95__Add_Requested_At_To_Tenant_Feature_Toggles.sql`：`tenant_feature_toggles.requested_at timestamp with time zone`（可為 NULL，不回填）；實體 `TenantFeatureToggle.requestedAt`。

| 事件 | `requested_at` | 對外的 `status` |
|---|---|---|
| 核准開店建的預設列（三個需審核的功能，`is_enabled=false`） | `NULL` | `INACTIVE`（修復前是 `PENDING`） |
| 店主對需審核功能送 `enabled=true` | 無值 → 現在；已有值 → **保留最早的時間** | `PENDING`（`isEnabled` 仍為 false） |
| 店主送 `enabled=false`（撤回，或關閉已核准的功能） | 清除 | `INACTIVE` |
| 免審核功能啟用，或已啟用功能的重複確認 | 清除 | `ACTIVE` |
| 管理員核准（`enabled=true`）／駁回（`enabled=false`）／`setFeatureToggle` 直接設定 | 清除 | `ACTIVE`／`INACTIVE` |

`resolveFeatureStatus`：`ACTIVE`＝已啟用；`PENDING`＝需審核、未啟用、`requestedAt != null`；其餘 `INACTIVE`。回應的 `requestedAt` 只在未啟用時帶出真正的申請時間（原本是資料列建立時間）。寫入集中在 `recordRequestTime(toggle, pendingApproval)`——獨立成方法是為了不推高 `updateFeatureToggle` 的 NPath（Checkstyle 門檻 200）。

### 3.2 前端（DEF-428）

`features/page.tsx` 的 `updateFeatureToggle` 改用 PUT 回應的 `newState`／`status`：`pendingApproval = enabled && status === 'PENDING_APPROVAL'`；本地 `isEnabled = newState`、`status` 依 `pendingApproval`／`newState` 推；提示在待審核時是「已送出啟用申請，待平台審核後生效」，不再說「已啟用」。管理員頁原本的寫法（核准→`ACTIVE`、停用→`INACTIVE`）就是對的，不動。

## 4. 測試

| 檔案 | 內容 |
|---|---|
| `core/pricing/PricingServiceTest.CalendarManualOverrideToggleTests`（新增 2） | `doThrow` 模擬開關關閉：兩個方法都丟 `E_2004`，且 `verify(never())` 房源／規則查詢、規則儲存、稽核 |
| `api/controller/StoreToggleGatesApplicationE2ETest`（新增 1，真實 PostgreSQL） | §2 的「只翻轉開關就從 403 變 200」 |
| `core/tenant/TenantServiceTest`（新增 9） | 功能清單 3：預設列→`INACTIVE`、申請過→`PENDING` 且帶申請時間、已啟用→`ACTIVE` 且不帶申請時間；更新 6：預設列申請→記時間、新建列申請→記時間、重複申請保留最早時間、撤回→清除、關閉已核准功能→清除、免審核啟用→不產生申請時間 |
| `core/admin/AdminServiceTest`（新增 3） | `updateTenantFeatureToggle` 核准、駁回，與 `setFeatureToggle`：都清掉申請時間 |
| `integration/FeatureRequestLifecycleIntegrationTest`（新增，4，真實 PostgreSQL＋Redis） | 還原核准開店的真實狀態（六個功能都有列、三個需審核的是 false）後走完整 HTTP＋JWT＋權限鏈：預設列不是待審核（店主端、管理員端一致，管理員的待審核筆數是 0）；申請→只有被申請的那項 `PENDING` 且帶申請時間，重複申請保留最早時間；管理員核准→`ACTIVE`、資料庫欄位為 NULL，店主之後關閉是 `INACTIVE`；管理員駁回→`INACTIVE`，可重新申請 |
| `frontend/e2e/at-m17-003.spec.ts` E2E-M17-003-02（改寫） | 前提：新店鋪的需審核功能是「已停用」、不是待審核，API 讀回 `INACTIVE` 且 `requestedAt: null`；點擊後回應契約、開關仍關、徽章「待審核」、提示不是「已啟用」、API 讀回 `PENDING` 且有申請時間；重新整理後一致 |
| `frontend/e2e/at-admin-tenant-features-real.spec.ts` E2E-ADMIN-FEAT-01（加強） | 摘要必須是「1 項功能待審核：民宿預訂功能」（修復前是 3 項） |

**紅燈先行**（只加了實體欄位與遷移、服務邏輯尚未改）：`AdminServiceTest` 3 紅、`TenantServiceTest.GetFeatureTogglesTests` 2 紅、`UpdateFeatureToggleTests` 4 紅、`FeatureRequestLifecycleIntegrationTest` 4／4 紅，原因都是預期的（預設列是 `PENDING`、申請時間沒記、沒清）；另兩個**守門型**案例（重複申請保留最早時間、免審核啟用不產生申請時間）在舊邏輯下本來就綠，它們留給突變驗證檢驗。

## 5. 突變驗證

### 5.1 後端（驅動 `mutate_s311.py`；每個突變只跑相關的單元＋真實棧類別，解析 surefire／failsafe XML 取失敗集合；還原後 SHA-256 與 `shasum -c` 核對）

先跑無突變基準 Z0：253 個案例 0 失敗（驅動走通）。

| 突變 | 內容 | 失敗集合 |
|---|---|---|
| P1 | 拿掉 `setCalendarPrice` 的開關檢查 | 單元 `setCalendarPrice`＋真實棧案例（2） |
| P2 | 拿掉 `overridePrice` 的開關檢查 | 單元 `overridePrice`＋真實棧案例（2） |
| P3 | `setCalendarPrice` 查錯開關鍵（`PROMO_ENABLED`） | 單元＋真實棧（翻開關後仍被擋）（2） |
| P4 | 檢查移到房源查詢之後 | **只有單元案例**（1）——驗證「第一步就檢查」；真實棧維持綠，與預測一致 |
| T1 | 待審核判定退回「有資料列就算」（修復前行為） | 6：整合 4＋單元 2 |
| T2 | 回應的 `requestedAt` 退回資料列建立時間 | 2 |
| T3 | 重複申請覆寫申請時間 | 2：整合＋單元 |
| T4 | 從不清除申請時間 | 2，**都是單元案例**；整合測試沒抓到（見 §8） |
| T5 | 完全不記申請時間 | 7 |
| T6 | 免審核功能啟用時也記申請時間 | 1（守門型案例） |
| A1 | 管理員 `updateTenantFeatureToggle` 不清申請時間 | 4：單元 2＋整合 2 |
| A2 | 管理員 `setFeatureToggle` 不清申請時間 | 1 |

### 5.2 前端與 E2E（驅動 `mutate_e2e.py`：套用→重建該端→單規格真實全棧→還原→再重建乾淨）

| 突變 | 殺手 | 紅燈原因 |
|---|---|---|
| FE428A 前端改回用請求的 `enabled` 更新畫面 | 003-02 | `aria-checked` 預期 `false`、實際 `true`——正是原缺陷的症狀；003-01 維持綠 |
| FE428B 前端永遠不顯示待審核 | 003-02 | 「待審核」徽章找不到 |
| BE429T1 待審核判定退回「有資料列就算」 | 003-02、ADMIN-FEAT-01 | 003-02 的前提「已停用」找不到；管理員頁的「1 項功能待審核」找不到（修復前是 3 項） |
| BE429T5 後端不記申請時間 | 003-02、ADMIN-FEAT-01 | 003-02：點擊後 API 讀回 `status` 預期 `PENDING`、實際 `INACTIVE`；管理員頁沒有待審核項目 |

## 6. 驗證總表

| 項目 | 結果 |
|---|---|
| Sprint 310 雲端 CI（`164ca615`） | run [40673442467](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40673442467) 三個 job 皆 success（約 13 分鐘；`gh run view --json` 交叉確認 headSha）；下載整合 job 日誌確認整合測試 **957／0 失敗**、`BookingControllerE2ETest` 25／0／0 |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**19 分 13 秒**；單元 **2394／0 失敗**（Sprint 310 的 2380＋本輪 14：`CalendarManualOverrideToggleTests` 2、`TenantServiceTest` 9、`AdminServiceTest` 3）；整合 **962／0 失敗**（957＋本輪 5：`StoreToggleGatesApplicationE2ETest` 1、`FeatureRequestLifecycleIntegrationTest` 4）；Checkstyle main／test 皆 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。`DEF-426` 受影響的既有類別：`PlatformSelfRunWriteIsolationE2ETest` 18／18、`PricingRulePersistenceE2ETest` 3／3、`M12PricingIntegrationTest` 12／12 |
| Flyway 遷移與實體對齊 | E2E 守門以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動後端，「entity 與 Flyway schema 對齊，無漂移」（V95 已套用）；`make validate-schema-doc`：95 個遷移、SRD 的 DDL 與 PRD §8.2 欄位表皆一致，退出碼 0 |
| 完整 E2E 套件（Flyway 資料庫＋真實 Redis；`validate-e2e.sh` 副本、list 報表） | **286 通過／0 跳過／0 失敗**，6.8 分鐘（JAR 是 verify 剛打包、含 V95；前端是突變驗證後從還原的原始碼乾淨重建，沒有重跑 `npm ci`）；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功；認證端點被限流 13 次（重試自癒）；清單中跳過、失敗、flaky 的標記皆 0 個；003-01／003-02、004-01／004-02、管理員頁 FEAT-01／02、DASH-FEAT-01，以及 `E2E-PRICING-02`（日曆改價與區間覆蓋，開關開啟的店鋪照常 200）都在清單內並通過 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | verify 跑到它們的時候我還在寫文件，所以在**所有文件定稿後**另行補跑：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS` |
| ESLint（`--max-warnings 0`）／`tsc --noEmit`／IDE Problems | `features/page.tsx` 與三個規格的 ESLint 退出碼 0；`tsc` 退出碼 0；IDE Problems：398 個檔案條目、診斷 **0**，本輪改過的後端檔案與 `features/page.tsx` 都在條目內。兩個 Playwright 規格不在條目內（TypeScript 語言服務只分析開啟的檔案），以 ESLint＋`tsc` 為準 |

## 7. 過程中的失誤與修正

1. **真實棧固件缺 `rooms` 列**：DEF-426 的真實棧案例第一版紅在 `404`，不是預期的 `200` 與 `403` 之差（§2）。這種「紅了但原因不對」是紅燈先行最需要逐項檢查的地方，所以我讀失敗訊息而不是只看紅綠。
2. **突變驅動的乾淨檢查擋住了第一次執行**：驅動原本要求被突變的檔案相對 HEAD 乾淨，而這輪的基準是尚未 commit 的修復版。改成「有未提交變更時，必須與事先記錄的基準 SHA-256 相符」，仍然擋得住被上一次殘留突變汙染的狀態。
3. **我引用了一個還沒建立的替換檔**：改 003-02 時我寫了一支 Python 腳本去讀還不存在的文字檔，腳本在寫入之前就失敗，規格檔沒有被改動；之後改用 Edit 工具精準替換。
4. **追蹤表裡自己數錯了新增案例數**（寫成「10 個：3＋7」）：寫完對照測試輸出（`GetFeatureTogglesTests` 25、`UpdateFeatureToggleTests` 11）發現實際是 9（3＋6），當場更正。
5. **我寫的選項說明漏了 PRD §8.2.2**（§1.3 第二列）：這與 Sprint 309「選項說明的範圍不完整」是同類問題——動手前沒有先跑 `make validate-schema-doc` 看守門要求哪些文件。
6. **使用者在收尾中途貼來一則 CI 失敗通知**（標題是 Sprint 309 的功能 commit）：依 CLAUDE.md 先取日誌，不憑印象。查證是 Sprint 309 的紅燈 run 40556219811（`BookingControllerE2ETest` 清理階段被 `bookings` 外鍵擋下，約束名與 Sprint 309 重現時完全相同），已由 `035a14e6` 修復、Sprint 310 又讓 IT-M06-103 不再有背景執行緒；它只是 Actions 清單裡不可變的歷史紅叉，最新的 run 是綠的。
7. 工作目錄與 zsh 萬用字元的老問題在這輪沒有再造成誤判（一律絕對路徑、引號）。

## 8. 限制（誠實揭露）

1. **既有資料的遷移是保守的**：V95 不回填，既有資料列全是 `NULL`。遷移前已送出、尚未被核准的申請，遷移後會顯示「已停用」，店主需要重新送出。我沒有用生產資料量過這件事（沒有生產資料庫）；使用者選方案 A 時，選項說明已寫明這個代價。
2. **`T4`（從不清除申請時間）只有單元案例抓得到**：整合測試的流程（管理員核准／駁回都會清除）蓋住了店主端清除路徑的缺口。店主撤回、關閉已核准功能的清除邏輯由 `TenantServiceTest` 的兩個案例守住。
3. **`SUPER_ADMIN` 代操作的檢查看的是系統租戶的開關**（`createRule` 原本就如此，`DEF-426` 的修復「比照」它）：管理員替開關關閉的店鋪操作，仍可能得到 `200` 卻不生效——登記為 `DEF-435`，讀碼推論，沒有實測（[Sprint 312](SPRINT_312_PLAN.md) 已實測屬實並修復）。
4. `updateRule`／`deleteRule` 仍不查開關（本題不涵蓋）。
5. `determineToggleStatus` 對「本來就未啟用的功能再送 `enabled=false`」仍回 `PENDING_APPROVAL`（舊行為，畫面無法觸發，只有直接打 API）——登記為 `DEF-434`，沒有順手改。
6. 畫面沒有「撤回申請」的入口：開關為關時點擊送的是 `enabled=true`（再申請一次）。撤回只能走 API。
7. **本輪的 push 與雲端 CI（push 後回填）**：使用者的互動答案只授權了 Sprint 310 的 push，本輪新增 Flyway 遷移 `V95` 並改變生產行為，所以收尾再問一次；使用者選「現在 push」，已於 `164ca615..53a9073b` push。pre-push 輕量守門通過（後端單元與前端 Lint／建置兩個 act job 皆 `Job succeeded`，schema 漂移守門「entity 與 Flyway schema 對齊，無漂移」）。雲端 CI run [40698764431](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40698764431) 三個 job 皆 success（Backend Unit Tests 2 分 53 秒、Frontend Lint & Build 54 秒、Backend Integration Tests & Package 10 分 06 秒；整個 run 13 分 05 秒，13:41:32Z～13:54:37Z），以 `gh run view --json status,conclusion,headSha` 交叉確認 headSha 為 `53a9073b`；下載整合 job 的完整雲端日誌（約 56 MB）確認整合測試 **962／0 失敗／0 錯誤／0 跳過**、`FeatureRequestLifecycleIntegrationTest` 4／0、`BUILD SUCCESS`。
8. 管理員頁的「N 項功能待審核」靠前端對 `status` 的計數，沒有為它另寫前端單元測試（專案沒有前端單元測試，只有 Playwright）；由管理員頁規格 FEAT-01 的「1 項功能待審核：民宿預訂功能」斷言與 `FeatureRequestLifecycleIntegrationTest` 的 `pendingCount` 共同守住。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | **把 `DEF-428` 與 429 一起修**（使用者沒有逐題點名它） | 追蹤表已寫「不需產品決策、建議跟 429 一起修」；429 修好後，若前端仍用請求值更新畫面，點需審核功能會顯示「開關已開、已啟用」而後端是待審核，429 的使用者可見效果就是壞的 | 告訴我回退 `features/page.tsx` 的修改（003-02 的點擊後斷言改回只驗後端） |
| 2 | PRD §8.2.2 欄位表補一列 `requested_at` | `make validate-schema-doc` 守門要求（`--write` 不處理 PRD）；欄位本身是使用者選的方案 A，只是機械同步資料字典，沒有改任何需求敘述 | 告訴我還原這一列（守門會報不一致） |
| 3 | 重複申請**保留最早**的申請時間，不重置 | 管理員看到的是「等了多久」；重置會讓一直被審核拖延的申請看起來很新 | 告訴我改成保留最新 |
| 4 | 店主送 `enabled=false`（含對待審核功能）＝撤回申請，清除申請時間 | 申請時間代表「仍在等待的申請」；留著會讓關閉後的功能又顯示待審核 | 告訴我撤回不清除（會需要另設撤回語意） |
| 5 | `DEF-426` 照選項說明「比照 `createRule`」檢查**呼叫者租戶**的開關 | 使用者選的是我描述的做法；改成檢查房源所屬店鋪是更大的行為變更，且會讓三個端點不一致——登記 `DEF-435` 請你裁決 | 告訴我選 `DEF-435` 的 (a) |
| 6 | 登記 `DEF-434`、`DEF-435`，不順手改 | Rule 3；前者是 API 回應語意的小落差，後者要你裁決範圍 | 告訴我先做哪個 |
| 7 | **本輪不自動 push，收尾再問**（結果：使用者選「現在 push」，已 push，雲端全綠，見 §8 第 7 點） | 使用者的互動答案只授權了 Sprint 310 的 push（「approval in one context doesn't extend to the next」）；本輪含 DB 遷移與生產行為變更 | （已執行） |

## 10. 新登記的缺陷

| ID | 摘要 | 狀態 |
|---|---|---|
| `DEF-434` | `PUT …/features/{feature}` 對本來就未啟用的功能送 `enabled=false`，回應仍說 `PENDING_APPROVAL`（讀碼） | 未處理，不需產品決策 |
| `DEF-435` | `SUPER_ADMIN` 代操作時開關檢查看系統租戶而非房源所屬店鋪（讀碼推論） | 未處理，**待你裁決範圍**（建議 (a)）。**後續**：你選了 (a)，[Sprint 312](SPRINT_312_PLAN.md) 已實測並修復 |

`DEF-426`、`DEF-428`、`DEF-429` 已於本輪修復。`DEF-430`～`DEF-432` 未動。

## 11. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 311 列。本輪的 commit `53a9073b` 已 push，雲端 CI 全綠（見 §8 第 7 點）。`DEF-435` 已由 [Sprint 312](SPRINT_312_PLAN.md) 依使用者裁決實測並修復。

本輪的 commit 範圍：生產程式 `PricingService`、`TenantService`、`AdminService`、`TenantFeatureToggle`、前端 `dashboard/tenants/[id]/features/page.tsx`；Flyway `V95`；測試 `PricingServiceTest`、`StoreToggleGatesApplicationE2ETest`、`TenantServiceTest`、`AdminServiceTest`、`FeatureRequestLifecycleIntegrationTest`（新增）、Playwright `at-m17-003.spec.ts`、`at-admin-tenant-features-real.spec.ts`；文件 `API_M12_Dynamic_Pricing.md`（v2.8）、`API_M17_Tenant.md`（v2.5）、`SRD_Database_Schema.md`（DDL 同步）、PRD §8.2.2（補一列）、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`、`SPRINT_310_PLAN.md`（補後續指引）與本計畫書。
