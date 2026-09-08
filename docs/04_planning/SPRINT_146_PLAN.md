# Sprint 146 Plan — 前後端契約漂移掃描 + getFeatureMap 同型死碼清理

**Sprint**: Sprint 146
**日期**: 2026-09-08

---

## 1. 缺口盤點結果

### 1.1 起點

Sprint 145 §9 Action Items 留下三個明確標記為「未處理」的候選：`TenantService.getFeatureMap` 同型寫法（項目 5）、其他頁面是否有同型契約漂移（項目 5c）、配額強制是否要實作（項目 6，需產品決策）。使用者拍板本輪主題為「契約漂移掃描 + getFeatureMap 清理」，配額強制留待下一輪決策。

### 1.2 掃描方法

依 [[frontend-backend-contract-drift-sweep]] 既有教訓，把 77 個前端頁面依模組分成 5 組，各自平行派出一個唯讀 Explore agent（依 CLAUDE.md 規則，prompt 明文禁止寫入/git），比對前端 TypeScript 介面與後端 DTO/Controller 的欄位名、路徑、必填性：

| 組別 | 範圍 | 結果 |
|------|------|------|
| A | 訂單/付款/退貨/評價 | 1 項零呼叫者死配置（低），其餘一致 |
| B | 客服工單/知識庫/FAQ | 3 項知識庫建立文章相關（皆零呼叫者死路徑），其餘一致 |
| C | ERP 進銷存（庫存/異動/供應商，採購單已於 S127-131 掃過） | **無契約漂移**；側面發現一個純前端顯示邏輯 bug（非契約問題） |
| D | CMS/Blog/媒體/通知 | 3 項媒體相關型別問題（皆零呼叫者/無可見症狀），其餘一致 |
| E | 租戶/訂房/購物車/房源/儀表板/管理後台 | **5 項真實漂移，其中 3 項是目前正在發生的頁面級故障** |

### 1.3 偽陽性排除與範圍擴大

Group E 的初次報告已相當精確，但主控 session 對其中兩項做獨立查證時，發現實際損壞範圍比報告描述的更廣、更嚴重（見 §4）：

- 報告只點出 `TenantApplyForm.tsx` 一處 `businessType` 值域錯誤；獨立查證後發現 `TenantEditForm.tsx`、`TenantList.tsx`、`TenantDetail.tsx` 共四處使用同一組錯誤值域，且 `TenantUpdateRequest.businessType` 欄位存在卻從未被讀取。
- 報告描述 `TenantEditForm` 的後果是「使用者被迫重新輸入電話」；獨立查證發現真正後果是 **`formData.contactPhone.trim()` 對 `undefined` 呼叫方法直接拋出 `TypeError`，整個編輯表單崩潰**。

這與 Sprint 136～144 反覆出現的教訓一致：子 agent 的唯讀分析可信但不完整，主控 session 的獨立複核仍是必要步驟。

---

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | Sprint 146 主題 | **契約漂移掃描 + getFeatureMap 清理**（推薦選項）；配額強制留待下一輪、其餘兩項候選不強制併入本輪 |

---

## 3. 實作內容（一）：`getFeatureMap` 同型死碼清理（DEF-178）

`TenantService.getTenantsListByUser()`（`GET /v2/tenants/my` 的資料來源）呼叫的私有方法 `getFeatureMap()`，與 DEF-167 修復前的 `getFeatureToggles()` 是同一種寫法：對缺失的 toggle 一律 `def.isBoolean ? def.booleanDefault : false`，導致 `MAX_PRODUCTS`/`MAX_ROOMS`/`MAX_POSTS`/`COMMISSION_RATE` 四個數值配額以假布林值 `false` 混入 `Map<String, Boolean>`。

**修法**：完全比照 DEF-167 的既有修復模式——迴圈中先查 `FEATURE_DEFINITIONS`，`!def.isBoolean` 就 `continue`，兩處（讀取既有 toggle、補預設值）皆套用。

**追蹤呼叫鏈時的意外發現（DEF-169）**：`TenantService` 存在一個更早、已損壞的姊妹方法 `getTenantsByUser()`——邏輯上組出了完整的 `tenantList`，但最終回傳硬寫成 `TenantListResponse.builder().tenantId(null).build()`，完全丟棄剛組好的資料。`grep` 全庫確認此方法本身零呼叫點，純粹是被 `getTenantsListByUser()` 取代後留下的死碼。登記為 DEF-169，本輪不處理（避免範圍蔓延），留待未來決定是否要恢復或刪除。

**紅燈驗證（實際執行）**：新增 `TenantServiceTest.GetTenantsListByUserTests.getTenantsListByUser_featuresExcludeNumericQuotas`。用 `git stash` 還原修復前的 `TenantService.java`，重跑該測試類別，確認失敗（`expected: <[6 個布林]> but was: <[6 個布林, MAX_PRODUCTS, MAX_ROOMS, MAX_POSTS, COMMISSION_RATE]>`）；`git stash pop` 還原後全部 33 個測試通過。

同步強化既有 E2E 測試 `TenantControllerE2ETest.getMyTenants_AsStoreOwner_ReturnsTenantList`——原本只斷言 `storeName`/`status` 非空，本輪新增 `features` 逐欄斷言（含 `not(hasKey(...))` 反向斷言四個數值配額不得出現）。同樣紅燈先行且實際執行：還原修復前程式碼跑此 E2E，實際回應 body 印出 `"features":{"BOOKING_ENABLED":false,...,"MAX_ROOMS":false,"MAX_POSTS":false,"MAX_PRODUCTS":false,"COMMISSION_RATE":false}`，證實缺陷存在；還原修復後重跑通過。

---

## 4. 實作內容（二）：租戶管理契約漂移叢集修復（DEF-179～182）

Group E 掃描與後續獨立查證共同揭露出「開店/店鋪管理」這一整條使用者旅程，從申請、列表到編輯，四個環節全部都有契約層級的缺陷——這些缺陷彼此獨立但集中在同一批檔案，故合併在同一節說明修法。

### 4.1 DEF-179：開店申請表單 `businessType` 值域完全不重疊，申請必定 400

`TenantApplyForm.tsx`、`TenantEditForm.tsx` 的 `businessTypes` 下拉選項，以及 `TenantList.tsx`、`TenantDetail.tsx` 的 `getBusinessTypeLabel()` 對照表，**四處全部**使用 `RETAIL/WHOLESALE/F&B/SERVICE/MANUFACTURING/OTHER` 這組值；後端 `TenantApplicationRequest.businessType` 的 `@Pattern` 只接受 `RETAIL_ONLY/BOOKING_ONLY/HYBRID`（PRD §9.10.1 明定）。`grep` 全庫確認前端從未送出或顯示過一個真正合法的值——這是存在已久的初始實作缺陷，不是近期回歸。

**額外發現**：`TenantUpdateRequest.businessType` 欄位存在，但 `TenantService.applyTenantUpdates()` 完全沒有讀取它；即使值域修正，店主編輯店鋪時修改「營業類型」仍會被靜默丟棄。

**修法**：
- 四處前端對照表/選項統一改為 `RETAIL_ONLY`＝零售商城／`BOOKING_ONLY`＝民宿訂房／`HYBRID`＝複合式（零售＋訂房），標籤命名比照 `TenantService.FEATURE_DEFINITIONS` 既有的 `RETAIL_ENABLED`＝零售功能／`BOOKING_ENABLED`＝民宿預訂功能。
- `TenantUpdateRequest.businessType` 補上與 `TenantApplicationRequest` 一致的 `@Pattern` 驗證。
- `applyTenantUpdates()` 補上寫入邏輯——`businessType` 存於 `Tenant.metadata` JSONB，新建 `HashMap` 而非原地修改，確保 Hibernate 對 JSON 型別的變更偵測正確觸發 UPDATE。
- 確認 `AdminService.approveTenantApplication`／`initializeFeatureToggles` 不依 `businessType` 值分支（純粹欄位傳遞），本次修法不影響功能開關的預設建立行為。

**紅燈驗證（實際執行）**：新增 `TenantServiceTest.updateTenant_writesBusinessTypeToMetadata`。用臨時 `if (false && ...)` 短路寫入邏輯重跑，確認失敗（`expected: <BOOKING_ONLY> but was: <RETAIL_ONLY>`）；還原後通過。

**另修復一個因此長期存活的弱斷言 E2E**：`frontend/e2e/at-m17-001.spec.ts` 的開店申請流程測試，先前用 `waitForURL(...).catch(() => {})` 吞掉逾時，再對「導向成功」與「導向失敗」兩種結果都呼叫 `test.skip()`——這正是這個 400 缺陷能存活的原因：測試從未真正斷言過申請會成功。已改為真斷言（`waitForURL` 不吞例外 + `expect(page.url()).toContain(...)`）。

### 4.2 DEF-180：`GET /v2/tenants` 路徑不存在，店鋪管理列表頁必定 404

`lib/api.ts` 的 `API_ENDPOINTS.tenants.list` 註解寫著「取得當前用戶的店鋪列表」，卻設成不存在的裸路徑 `/v2/tenants`；`TenantController` 只有 `/tenants/apply`、`/tenants/my`、`/tenants/{id}` 等帶字尾路徑。

**修法**：改指向既有、已有完整 E2E 覆蓋、語意完全相符的 `GET /v2/tenants/my`（依 §3 的 DEF-178 事實查證，此端點修復前也是零前端呼叫者——本次修復使其首次真正被使用）。回應形狀是 `{ tenants: [...] }` 而非裸陣列，欄位是 `TenantListResponse` 的 `tenantId/storeName/businessType/status/role/memberCount/createdAt`，沒有 `contactEmail`/`contactPhone`/`updatedAt`/`id`。`TenantList.tsx` 同步改寫：欄位改用 `tenantId`；「聯絡信箱」「聯絡電話」兩個此回應本就不存在的欄位，改顯示真實存在的「我的角色」「成員數」，而非留白或砍掉整塊資訊。不新增後端端點，避免重複實作已有且已測試的功能。

### 4.3 DEF-181：`TenantDetail`/`TenantEditForm` 讀取不存在的欄位，`status` 值域不符，含崩潰風險

`TenantDetailsResponse` 真實欄位是 `tenantId`（非 `id`），完全沒有 `contactPhone`/`updatedAt`——但 `Tenant` 實體本身有這兩個欄位，只是從未被此 DTO/mapper 帶出。`status` 前端宣告 `PENDING|APPROVED|REJECTED|SUSPENDED`，後端實際是 `PENDING_REVIEW|ACTIVE|REJECTED|SUSPENDED|TERMINATED`。

**可見後果**：店鋪詳情頁「店鋪 ID」恆空白、「最後更新」恆 `Invalid Date`、狀態徽章對 `ACTIVE` 等真實值全數落到 fallback、顯示原始英文字串。

**獨立查證發現的崩潰風險**：`TenantEditForm.tsx` 的「聯絡電話」為必填欄位，`fetchTenantDetail()` 把 `undefined` 的 `contactPhone` 直接指派進 `formData`；使用者若不主動重新輸入電話就送出表單，`validateForm()` 對 `formData.contactPhone.trim()` 會直接拋出 `TypeError: Cannot read properties of undefined`，**整個編輯表單崩潰**，而非只是「被迫重新輸入」。

**修法**：
- 後端補齊 `TenantDetailsResponse.contactPhone`/`updatedAt`（資料本來就在 `Tenant` 實體上，只是沒序列化出去，比照 [[frontend-backend-contract-drift-sweep]] 既有教訓的「前端有顯示區塊，後端卻從不提供」處理原則——後端補齊而非砍前端功能）。僅補在「已核准」分支，未核准分支維持既有的「限縮資訊」設計不變。
- 四個 Tenant 前端介面（`TenantDetail`/`TenantEditForm`/`TenantList`）統一改用 `tenantId`；`status` 值域改為真實的 5 種狀態，補上 `TERMINATED` 的標籤與樣式。
- `TenantEditForm` 初始化時 `contactPhone` 加上 `|| ''` 防呆——因申請時電話為選填，既有店鋪可能合法地是 `null`（不只是本次修復前的 `undefined`），兩種空值都要防。
- `TenantDetail` 唯讀顯示的電話欄位改為 `tenant.contactPhone || '未提供'`，避免對 `<Input value={null}>` 產生 React 警告。

**紅燈驗證（實際執行）**：強化 `TenantServiceTest.getTenantDetails_activeStatus_returnsFullInfo`，新增 `contactPhone`/`updatedAt` 斷言。用 `git stash` 還原三個相關檔案（`TenantService.java`/`TenantDetailsResponse.java`/`TenantUpdateRequest.java`）重跑，因 `getContactPhone()`/`getUpdatedAt()` 方法不存在直接**編譯失敗**（`cannot find symbol`）——這本身就是一種紅燈，證明這兩個欄位修復前確實不存在於 DTO。還原修復後 34 個測試全數通過。

### 4.4 DEF-182：購物車 `itemCount` vs 後端 `totalItems`，「共 __ 項商品」永遠空白

`(auth)/cart/page.tsx` 的 `CartResponse` 介面宣告 `itemCount`，後端 `CartDto.CartResponse` 實際以 `@JsonProperty("totalItems")` 序列化（`CartControllerE2ETest` 多處斷言 `data.totalItems` 證實）。`fetchCart()` 取回的 `cart.itemCount` 恆為 `undefined`；`removeItem()` 的本地樂觀更新雖然自己重算了一個同名欄位，僅在移除商品後短暫「巧合正確」，重新整理頁面又打回原形。

**修法**：改前端欄位名對齊後端既有的 `totalItems`（後端命名是既有 API 契約，不動它），介面宣告、本地樂觀更新、畫面顯示三處一併修正。

---

## 5. 測試

- **後端單元測試**：新增 2 個（`getTenantsListByUser_featuresExcludeNumericQuotas`、`updateTenant_writesBusinessTypeToMetadata`），強化 1 個既有測試的斷言（`getTenantDetails_activeStatus_returnsFullInfo`）。兩個新測試皆**紅燈先行且實際執行**（見 §3、§4.3），非僅口頭宣稱。
- **後端 E2E 測試**：強化 `TenantControllerE2ETest.getMyTenants_AsStoreOwner_ReturnsTenantList` 的 `features` 斷言，紅燈先行且實際執行（見 §3）。
- **前端 E2E 測試**：修正 `at-m17-001.spec.ts` 的弱斷言（見 §4.1）。撰寫時先做了程式邏輯的靜態複核（確認 `TenantController.createApplication` 無 `@PreAuthorize` 限制、`registerAndLogin` helper 建立的一般帳號可呼叫），實際紅燈→綠燈的執行結果見 §6（`make validate-e2e` 全量跑過，非只跑單一 spec）。
- **後端全量回歸**：`make test-db-up` 後 `mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1196**（相對 Sprint 145 的 1194，+2）、整合 **477**（與 Sprint 145 持平，本輪只強化既有 E2E 斷言、未新增整合測試方法），0 failures/errors。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`（兩個 execution 皆驗）**0 violations**。
- **編譯陷阱複查**：本輪每次 `mvn compile`/`mvn test` 前皆 `rm -rf target/maven-status` 強制重編並確認出現 `Compiling N source files` 該行，不採信單獨的 `BUILD SUCCESS`（[[mvn-phantom-build-success-after-failure]] 既有教訓）。
- **前端**：`npx tsc --noEmit` 通過（0 error）、`npx eslint src/` 0 error（92 個既有 warning，皆與本輪改動無關的既有項目）、`npm run build` 成功（77 個路由全數產生，含本輪改動的 4 個頁面）。
- **Schema 守門**：`make validate-schema` **通過**（entity 與 Flyway schema 對齊，無漂移）。本輪未新增任何 Flyway 遷移，`Tenant.metadata` 為既有 JSONB 欄位，`TenantDetailsResponse`/`TenantUpdateRequest` 皆為 DTO 而非 entity，不影響 schema。
- **本地 E2E 守門**：`make validate-e2e`（複製雲端 e2e job：乾淨 DB + host 全棧 + Playwright）結果見 §6（本節於執行完成後填寫）。

---

## 6. 驗證結果彙總

`make validate-e2e`（複製雲端 e2e job：`test-db-down` → 乾淨 PostgreSQL/Redis → 建置 backend JAR（`ddl-auto=validate`）→ Flyway migrate → 建置並啟動 frontend production build → `npx playwright test`）：

- **Schema 對齊**：✅（backend 以 `ddl-auto=validate` 啟動成功，entity 與 Flyway migration 無漂移）
- **Playwright 全量**：**57 passed / 4 skipped / 0 failed**（3.3 分鐘，61 個測試案例，非只跑單一 spec）
- 關鍵確認：
  - `at-m17-001.spec.ts`「開店申請流程 - 完整填寫並提交」**通過**——直接證實 DEF-179 的 `businessType` 值域修復在真實瀏覽器 + 真實後端 + 乾淨 DB 環境下確實生效，且本輪一併修正的弱斷言（移除 `waitForURL().catch(() => {})` + 兩種結果皆 `test.skip()`）本身也如期運作為真斷言。
  - `at-m17-004.spec.ts`「店鋪 Profile 完整更新／部分更新」皆**通過**——間接證實 `TenantUpdateRequest.businessType` 寫入邏輯（DEF-179 的連帶修復）與 `applyTenantUpdates` 對其餘欄位的既有 partial-update 語意未被本輪改動破壞。
  - `at-m11-cart-checkout.spec.ts`「E2E-M11-002: 查看購物車內容」等購物車相關案例皆**通過**，`cart/page.tsx` 的 `totalItems` 欄位重新命名（DEF-182）未破壞既有购物車瀏覽/結帳流程。
  - 4 個 skipped 與若干 `⚠️`（如「找不到增加按鈕」「未看到預訂編號」）皆為既有測試對「購物車為空/資料狀態依賴」情境的既有優雅降級分支，非本輪改動引入，且不計入 failed。
- `at-m17-002.spec.ts`（Admin 審核開店申請）、`at-m17-003.spec.ts`（Feature Toggle 更新，對應 DEF-167/178 的既有修復範圍）亦全數通過，確認本輪對 `TenantService` 的改動未影響既有已修復功能。

**結論**：本輪 4 項租戶管理契約漂移修復（DEF-179～182）與 1 項 `getFeatureMap` 死碼清理（DEF-178），皆通過後端單元/整合測試、前端 tsc/eslint/build，以及最終的真實瀏覽器端 E2E 驗證，無一項僅停留在靜態推理層級。

---

## 7. 範圍外（延後）

本輪掃描與獨立查證過程中額外發現、但判定不併入本輪修復範圍的項目，皆已登記至 `DEFERRED_ITEMS_TRACKER.md`：

| DEF | 摘要 | 不修復的理由 |
|-----|------|-------------|
| DEF-169 | `TenantService.getTenantsByUser()` 死碼（回傳空殼） | 零呼叫點，非執行中缺陷，刪除前應先確認是否要恢復多租戶清單頁 |
| DEF-170 | 知識庫建立文章缺 `slug`/多送 `autoPublish` | 零呼叫點（前端無「新增文章」UI） |
| DEF-171 | `KnowledgeCategoryDto` 缺 3 欄位 | 尚無可見症狀，前端未讀取這些欄位 |
| DEF-172 | 媒體庫 `PageResponse.page` 實際是 `number` | 前端用自己的 state 管理分頁，未讀取此欄位 |
| DEF-173 | 媒體上傳兩處型別/欄位問題 | 皆零呼叫點或回傳值從未被使用 |
| DEF-174 | `payments.mock`/`.callback` 設定殘留 | 零呼叫點死配置 |
| DEF-175 | `PaymentStatus` 缺 `PARTIALLY_REFUNDED` | 觸發此狀態的退款 UI 尚未串接，目前不可達 |
| DEF-176 | Admin 審核頁「駁回原因」從未回傳 | 功能性缺失非崩潰，且需先查證資料是否真的有持久化 |
| DEF-177 | ERP 庫存明細頁異動正負號顯示錯誤 | 非契約漂移範疇，純 UI 顯示品質問題 |

**配額強制是否要實作**（Sprint 145 §9 item 6）：本輪未討論，維持待產品決策，見 `SPRINT_145_PLAN.md` §5.1／§8。

---

## 8. 刻意不做的事（避免範圍蔓延）

- 不修復 `getTenantsByUser()` 死碼本身（DEF-169）——與本輪已修復的 `getFeatureMap` 問題根源相同，但方法本身零呼叫點，刪除與否是獨立的產品/架構決策。
- 不擴大修復 Group A/B/D 掃描出的 8 個零呼叫者/無可見症狀項目（DEF-170~177）——依 Rule 3（精準改動），這些是死路徑或潛伏問題，非本輪目標。
- 不在 `AdminService.initializeFeatureToggles` 中依 `businessType` 值分支決定預設功能開關——本輪已確認現況是「純欄位傳遞、不分支」，維持現狀，改變此行為屬於新功能設計而非契約修復。
- 不清理 `services/media.ts`/`cms.ts` 中發現的其餘零呼叫者死碼（`getMediaAsset`/`getMediaAssetCount`/`updateMediaAsset`/`uploadMedia`）——未在本輪掃描報告中被登記為獨立 DEF，範圍已经足夠大，留待未來死碼清理專項處理。

---

## 9. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | `getFeatureMap` 同型死碼清理（DEF-178） | Sprint 145 §9 item 5 | ✅ 完成 | — |
| 2 | 契約漂移全掃（5 組平行 Explore agent） | Sprint 145 §9 item 5c | ✅ 完成 | — |
| 3 | DEF-179～182 租戶管理契約漂移叢集修復 | 本輪掃描 + 獨立查證擴大範圍 | ✅ 完成 | — |
| 4 | DEF-169～177 共 9 筆零呼叫者/低優先級發現 | 本輪掃描副產品 | ✅ 已登記 | 不排入排程，留待未來相關功能開發時一併處理 |
| 5 | `RELEASE_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ✅ 完成（Sprint 147 開工時同日回填） | commit `326e5b6` 已 push，雲端 CI run 34246025893 三個 job 全綠，詳見 `RELEASE_TRACKER.md` v2.32 |
| 6 | 配額強制是否要實作 | Sprint 145 §9 item 6 | ⬜ 待產品決策 | 需 PRD 確認是否真的要求配額限制 |
| 7 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已在追蹤器 |

---

## 10. 誠實揭露總結

- Group C（ERP）掃描結論是「無契約漂移」——這是本輪 5 組掃描中唯一的空手而回，如實記錄而非為了產出硬找問題。
- Group A/B/D 找到的 7 項全部是零呼叫者死路徑或無可見症狀的潛伏落差，主控 session 依既有教訓「零呼叫者的漂移是死路徑，嚴重性要下修」的判準，全數降為低優先級登記、不修復。
- Group E 的 5 項發現中，有 2 项（DEF-179、DEF-181）在主控 session 獨立查證後，發現實際損壞範圍與嚴重度都比原始掃描報告更大——分別是「四處同型錯誤」與「崩潰風險」，已在 §1.3、§4.1、§4.3 如實記錄查證過程與差異。
- `at-m17-001.spec.ts` 的 E2E 斷言修正撰寫時先做了靜態邏輯複核，隨後**確實以 `make validate-e2e` 實際執行**（57 passed / 4 skipped / 0 failed，見 §6），非僅止於靜態推理。
