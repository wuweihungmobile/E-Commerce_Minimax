# Sprint 145 Plan — 流程債補正 + DEF-166/167 查證與修復 + DEF-168 契約漂移

**Sprint**: Sprint 145
**日期**: 2026-09-08

---

## 1. 缺口盤點結果

本輪起點不是程式碼掃描，而是使用者要求「回歸 AISDLC 流程，確認下一步該進行什麼」。盤點 Sprint 144 收尾狀態時，查出的**不是程式缺口，而是流程債**。

### 1.1 盤點方法

| 步驟 | 做法 | 結果 |
|------|------|------|
| 確認交付狀態 | `git status` / `git rev-list --left-right --count origin/main...HEAD` | 工作區乾淨、與 `origin/main` 同步（`0 0`） |
| 確認 CI 真實結果 | `gh run list` 逐筆核對 commit SHA | **S136~144 九個 commit 全部 `success`**（非事後推測） |
| 核對必要產出 | 依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) §3.1/§3.2 逐項比對實際檔案 | 🔴 查出兩項斷更 |
| 核對待辦池 | `DEFERRED_ITEMS_TRACKER` 活躍區逐列讀 | 高優先級**空**；中優先級 1 筆已裁定不做；低優先級 8 筆全標「不排入排程」 |

### 1.2 查出的流程債

**(A) `RELEASE_TRACKER.md` 斷更 8 個 Sprint**

最新一列停在 Sprint 136，S137~144 完全沒有 row；Sprint 136 的狀態欄仍是「⏳ 待 push」，但該 commit 早已在 `origin/main` 且 CI 全綠——依該文件自己的「狀態欄維護規則」，早該在 S137 開工時回填。統計區三個數字（`待 push 1`、`最近一次已 push = S135`、`Tag 107`）全部過時。

**(B) Velocity / Action Items 兩節：不是漏寫，是從未被穩定執行**

初判為「S137~144 漏寫」，但逐檔掃描 `SPRINT_1*_PLAN.md` 後**修正了這個判斷**：

| 區間 | Velocity 節 | 說明 |
|------|------------|------|
| S101～S104 | ✅ 4/4 | 「Sprint 101 重新起算」確實執行了四輪 |
| **S105～S125** | ❌ **0/21** | 第一次中斷 |
| S126～S127 | ✅ 2/2 | 短暫恢復 |
| **S128～S144** | ❌ **0/17** | 第二次中斷 |
| **合計** | **6 / 44（14%）** | |

最刺眼的一點：慣例文件 v1.0 於 2026-09-01 寫下「一旦停寫就會斷鏈」這句警告時，**斷鏈已經發生 21 個 Sprint**——撰寫時沒有回頭核對實際檔案，把一個當下已失效的要求寫成了現行規則。

### 1.3 偽陽性排除

- **不把「`v2030.xx` Release Tag 與實際 git tag 不符」當本輪缺陷**：查證 `git tag` 實際只有 14 個（最新 `v2026.08.01-01`），而 tracker 記帳 107 次。這是既有的**文件記帳慣例**，非本輪造成，僅在統計區揭露，不擅自改動計數方式。

---

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | Sprint 145 主題 | **先補流程債再定主題** |
| 2 | Velocity / Action Items 怎麼處理 | **修慣例文件，承認實務**（Velocity 降選配、Action Items 維持必要、誠實記錄兩度漂移） |
| 3 | 流程債要先單獨 commit 嗎 | **等 S145 實質工作完成同批 commit** |
| 4 | S145 實質主題 | **清 DEF-166/167 兩筆新登記技術債** |
| 5 | DEF-167 修法方向 | **清死碼 + 修顯示層** |
| 6 | DEF-166 查證為偽陽性後如何處理 | **更正為「查證為非缺陷」並結案** |
| 7 | 功能開關頁面前後端契約漂移（7 欄只有 1 欄對得上）本輪是否一併修 | **本輪一併修** |

問題 5、6 是在**實作開始後、查證結果推翻原描述時**才提出的——因為正確修法取決於產品意圖，不在可自行判斷的範圍內。

---

## 3. 實作內容：流程債補正

### 3.1 `RELEASE_TRACKER.md`（v2.29）

- 新增 Sprint 137~144 共 8 列，每列含範圍、修法、誠實揭露、驗證數據、commit SHA 與雲端 CI run 編號
- Sprint 136 狀態欄「⏳ 待 push」→「✅ 已 push」，並標註**回填遲延 8 個 Sprint**
- 統計區：Tag 107→115、已 push 106→115、待 push 1→**0**、最近一次 Tag/已 push Release 改為 Sprint 144
- 新增「文件記帳的 Release Tag 與實際 git tag 不符」的既有落差揭露

### 3.2 `SPRINT_ARTIFACT_CONVENTION.md`（v1.0 → v2.0）

依使用者裁定：

- **Velocity 降為選配**。論證不是「反正沒人寫」，而是「它假設了一件本專案不成立的事：有人會用這份趨勢資料做決策」——範圍大小由「這一輪查證出多少筆真缺陷」決定，SP 估算從未驅動過任何一次排程決策。44 次機會被忽略 38 次且期間無人受阻，合理結論是要求本身不成立。
- **Action Items 維持必要**。它有真實且持續運作的消費端，且**風險此刻正在發生**（見 §3.3）。
- §4 拆為 4.1/4.2，新增第二次漂移的完整區間統計
- 頁尾新增「讀者須知」：本文件的規範性條款**不可預設為正在被執行**，引用前先實際掃描核對

### 3.3 `DEFERRED_ITEMS_TRACKER.md` 補登 DEF-166/167（v2.35）

這是讓「Action Items 維持必要」的裁定**真正生效**，而非只寫在文件裡：`SPRINT_144_PLAN.md` §11 記錄的兩筆候選原本只存在於單一 Plan 內文，既未進追蹤器，也沒有任何跨 Sprint 機制會讓下一輪盤點看到它們。

**過程中的自我修正**：補登時把版本歷史誤編為 `v2.29`，與既有的 v2.29（Sprint 131）**編號重複**。修正時順帶查出同型漂移——本文件「文件版本」停在 **v2.34（Sprint 137）**，S138~144 共七輪未更新版本區，與 `RELEASE_TRACKER` 斷更屬同一問題。已升為 v2.35 並揭露。

---

## 4. 誠實揭露：DEF-166 查證為**非缺陷**，不修復

**查證結論**：`assignTicket` 是 **blind write**——讀出實體、`setAssignedTo`、存回，**不讀取舊值來計算新值，也沒有任何前置條件檢查**。它既不是 check-then-act，也不是 read-modify-write，不屬 Sprint 136 併發競態全掃所針對的缺陷型態。

三項依據：

1. **跨方法欄位覆寫風險已消除**：Sprint 144 為 DEF-163 加的 `@DynamicUpdate` 讓 UPDATE 只帶 `assigned_to`，不會與 `updateStatus` 互相整列覆寫。
2. **剩下的 last-write-wins 是正確語意**：[M18_Knowledge_Management_SPEC.md](../01_requirements/M18_Knowledge_Management_SPEC.md) §5.4 對「指派工單」只定義端點（`PUT /admin/support/tickets/:id/assign`）與角色（Admin），**未定義任何「不可重複指派」或「已指派者不可再指派」的前置規則**。兩個管理員同時指派給不同人、後者覆蓋前者，與單執行緒依序呼叫兩次的結果完全相同。
3. **加 CAS 反而引入缺陷**：條件式 UPDATE 會讓正常的重新指派被拒絕，且需要呼叫端傳入預期舊值——這是 PRD 沒有要求的 API 契約變更。

🔴 **自我揭露**：本項是**本 Sprint 開工時由主控 session 自己補登的**，而補登時的措辭把 Sprint 144 §11 的條件句（「`assignTicket` 自身**若要**有 CAS 防護屬於新的技術債候選」）升格成了「最後一個 commit 者覆蓋前者的指派結果」的缺陷斷言。描述的事實正確，但把一個正常語意框成了缺陷。這與 Sprint 137~144 反覆遇到的「不可直接信任標籤」是同一類問題，差別在於**這次要被推翻的標籤是自己上一輪寫的**。已在 `DEFERRED_ITEMS_TRACKER` 完整記錄判斷依據，以免未來再被翻出重做一次。

---

## 5. 修復摘要：DEF-167 功能開關數值配額（查證推翻原描述）

### 5.1 真實情況與原描述的差異

原描述為「`initializeFeatureToggles` 與 `FEATURE_DEFINITIONS` 兩份清單需人工同步」。實際查證結果——**問題不是同步，是其中一份根本是死碼**，而且實際上有三份：

| 來源 | 內容 | 狀態 |
|------|------|------|
| `TenantService.FEATURE_DEFINITIONS` | 10 個 key（6 布林 + 4 數值） | 讀取端在用 |
| `TenantService.initializeFeatureToggles` | 建立完整 10 個 | 🔴 **零呼叫點，死碼** |
| `AdminService.initializeFeatureToggles` | **只建 6 個布林** | 唯一活路徑 |

`AdminService.initializeFeatureToggles` 被 `approveTenantApplication` 呼叫——依既有教訓 [[m17-tenant-application-dual-flow]]，那是**唯一真正可達的開店路徑**。因此**每一個生產環境建立的租戶，都缺 `MAX_PRODUCTS`/`MAX_ROOMS`/`MAX_POSTS`/`COMMISSION_RATE` 四筆 toggle**。

**可見後果**：`getFeatureToggles` 對缺失 toggle fallback 到 `def.booleanDefault`（四者皆 `false`），使前端 `/dashboard/tenants/[id]/features` 把數值配額也當成開關項目送進渲染；按下該開關會經 `updateFeatureToggle` 建立布林 toggle，把數值語意的 key 汙染成布林。

⚠️ **本節初稿寫的是「渲染成四個永遠關閉的假開關」，該描述不準確，於同一輪內更正**：後續查證該頁面時發現前後端契約 7 欄只有 1 欄對得上（見 §6），**全部 10 項都無法正確顯示**，不只是這四個數值配額。本項的後端修復依然成立（不該把數值配額當開關回傳），但當時對可見後果的描述低估了實際損壞範圍。

**明確排除的更嚴重推論**：這四個 key 在生產程式碼中**沒有任何強制執行點**（`grep` 確認除 `TenantService` 與 Flyway migration 外零讀取端），實際抽佣走 `Tenant.commissionRate` 欄位而非此 toggle。所以**沒有金流損失、也沒有配額失效**——配額從來就沒有被執行過。這一點特別記錄，是因為既有教訓 `e-commerce-prd-gap-sprints-93-94` 提醒過「橫向掃描的樣式比對會找對位置但推錯後果」。

### 5.2 修法

使用者拍板「清死碼 + 修顯示層」：

1. **刪死碼**：移除零呼叫點的 `TenantService.initializeFeatureToggles` 及其專用私有方法 `createToggle`/`createNumericToggle`，共 **56 行**。
2. **修顯示層**：`getFeatureToggles` 依 `FeatureDefinition.isBoolean` 過濾掉數值配額。關鍵發現——**`isBoolean` 欄位程式碼裡本來就存在，卻從未被任何地方讀取**（數值建構子的 `defaultValue` 參數甚至完全沒被使用，只設 `booleanDefault=false`，這正是假開關顯示為「關閉」的成因）。因此修法不需新增 API 契約欄位、不需改動前端。
3. **防汙染**：`updateFeatureToggle` 拒絕對數值配額做布林切換。內嵌檢查會使該方法 NPath 複雜度由 200 以下升至 **384**（checkstyle 失敗），故抽出 `requireToggleableFeature` 私有方法——**比照 Sprint 143 對同一個方法降 NPath 的既有做法**（該輪是 388 > 200）。

### 5.3 🔴 紅燈驗證抓到一個假綠燈

紅燈驗證**實際執行**（`git checkout` 還原修復版、清 `target/maven-status` 強制重編後跑測試），結果：

- `getFeatureToggles_excludesNumericQuotas` → **真紅燈**，失敗訊息直接證明缺陷：`expected: <[6 個布林]> but was: <[6 個布林, MAX_PRODUCTS, MAX_ROOMS, MAX_POSTS, COMMISSION_RATE]>`
- `updateFeatureToggle_numericQuotaKey_rejected` → **意外通過**

第二個測試「通過」的原因是錯的：修復前 `updateFeatureToggle("MAX_PRODUCTS", true)` 仍會拋 `BusinessException`，但那是走到 `buildNewFeatureToggle` 時 `tenantRepository.findById` 未 stub、`orElseThrow` 拋出的「找不到租戶」(E_2000)——**與數值配額毫無關係**。只斷言例外型別構成假綠燈，與既有教訓 `frontend-backend-enum-contract-drift` 記錄的 `anyOf(400,500)` 式寬鬆斷言同型。

**修正**：改為斷言例外訊息含 `"numeric quota"`。再跑紅燈，失敗訊息為「應因數值配額而拒絕，實際訊息：**找不到租戶**」——直接證實了假綠燈的診斷。還原修復版後兩個測試皆綠。

---

## 6. 修復摘要：DEF-168 功能開關頁面前後端契約漂移

查證 DEF-167 的「可見後果」時發現的獨立缺陷，經使用者拍板本輪一併修。

### 6.1 漂移範圍

| 前端 `FeatureToggle` 期待 | 後端 `FeatureInfo` 回傳 | 對得上？ |
|---|---|---|
| `feature` | `featureKey` | ❌ |
| `displayName` | `featureName` | ❌ |
| `enabled` | `isEnabled` | ❌ |
| `category` | （無此欄位） | ❌ |
| `status` | （無此欄位） | ❌ |
| `requiresAdminReview` | （無此欄位） | ❌ |
| `description` | `description` | ✅ |

已確認專案**未配置任何 Jackson 命名策略**，不會自動轉換；`Boolean isEnabled` 經 Lombok 產生 `getIsEnabled()`，序列化為 `isEnabled`，前端讀 `enabled` 必為 `undefined`。

**後果**：整個頁面實質壞掉——功能名稱空白、Switch 恆為 unchecked、所有項目擠進 `'other'` 分組、「啟用後需管理員審核」提示永不出現，且點擊開關會把 `undefined` 當 featureKey 送給後端。

### 6.2 為什麼長期存活

唯一的 E2E 測試 `TenantControllerE2ETest.getFeatureToggles_AsStoreOwner_ReturnsFeatures` 只斷言：

```java
.body("data.features", notNullValue());
```

**完全不驗欄位名稱或內容**。與既有教訓 [[frontend-backend-contract-drift-sweep]] 記錄的模式一致：契約漂移不會被寬鬆斷言攔截，測試照樣全綠。

### 6.3 修法

**後端補齊**（資料本來就在手上，只是沒序列化出去）：

- `FeatureInfo` 新增 `category`/`status`/`requiresAdminReview` 三欄
- `category` 的值**刻意對應前端 `getFeatureCategoryLabel` 既有的標籤鍵**（listing/booking/cms/erp/promo/pricing）——該對照表一直存在於前端，後端卻從未供給，可見前端原始設計就預期有這個欄位
- `requiresAdminReview` 直接取自 `FeatureDefinition.requiresApproval`，**該欄位本就存在，只是從未被回傳**
- `status` 由新增的 `resolveFeatureStatus` 依 toggle 與 requiresApproval 推導（ACTIVE/PENDING/INACTIVE），抽成獨立方法以免重蹈 §5.2 的 NPath 覆轍

**前端對齊後端命名**（後端命名是既有 API 契約，不動它以免影響未知消費端）：

- 介面改為 `featureKey`/`featureName`/`isEnabled`
- 🔴 狀態更新邏輯內還有一處 `f.feature === feature` 是 grep 沒抓到的漏改，由 `tsc --noEmit` 攔截——**型別檢查在此比人工搜尋可靠**
- 順帶更正 `updateFeatureToggle` 的回應型別宣告（原 `{feature, enabled, status}` 與後端 `FeatureToggleUpdateResponse` 三欄有兩欄不符；因回傳值未被使用故**無執行後果**，但錯誤宣告會誤導未來取用者）

**E2E 斷言一併收緊**為逐欄斷言（`featureKey`/`featureName`/`category`/`status`/`requiresAdminReview`/`isEnabled` 與 `size()`），否則修完仍然沒有東西守得住這個契約。

### 6.4 誠實揭露：本項的紅燈驗證形式較弱

DEF-167 的兩個測試做了完整的紅燈→綠燈驗證（§5.3）。DEF-168 的單元測試**無法以同樣形式驗證**：新欄位在修復前不存在於 DTO，測試會**編譯失敗**而非斷言失敗。編譯失敗技術上也是紅燈，但它證明的是「欄位不存在」，不是「行為錯誤」。

真正能證明此缺陷的紅燈是「前端讀到 undefined」，那需要瀏覽器層級的測試，本輪未建立。**收緊後的 E2E 斷言是最接近的替代**：它在最終全量回歸中**實際執行並通過**（`TenantControllerE2ETest` 14 tests 全綠，log 可見 `✅ API-M17-010 PASSED: StoreOwner 成功獲取功能開關`），證明後端真的回傳了 `category`/`status`/`requiresAdminReview` 三欄。

但仍需據實區分：**這是綠燈的正向證據，不是紅燈驗證**——本輪未單獨還原程式碼再跑一次 E2E 來實測「修復前該斷言會失敗」（E2E 全套約 9 分鐘）。不宣稱做過未做的驗證。

---

## 7. 驗證

- **編譯**：🔴 過程中再次遇到 `mvn-phantom-build-success-after-failure` 記錄的陷阱——改完程式後 `mvn -o compile` 回報 `BUILD SUCCESS` 但**沒有 `Compiling N source files` 那行**，即實際沒編譯。清 `target/maven-status` 後強制重編才看到 `Compiling 349 source files`。本輪每次編譯皆以該行為準，不採信單獨的 BUILD SUCCESS。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`（Sprint 140 教訓：兩個 execution 都要驗）**0 violations**。首次因 §5.2 所述 NPath 384 失敗，抽方法後通過。
- **單元測試**：新增 2 個（`TenantServiceTest.GetFeatureTogglesTests.getFeatureToggles_excludesNumericQuotas`、`UpdateFeatureToggleTests.updateFeatureToggle_numericQuotaKey_rejected`），紅燈先行且實際執行（見 §5.3）。`TenantServiceTest` 全類 **31 passed**。
- **全量回歸**：`make test-db-up` 後 `mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1194**（相對 Sprint 144 的 1191，+3：DEF-167 兩個 + DEF-168 一個）、整合 **477**（與 Sprint 144 持平，本輪只收緊既有 E2E 的斷言、未新增整合測試方法），0 failures/errors；耗時 **8:50 min**。
  - 中途另有一次「DEF-167 修完、DEF-168 未動」的全量回歸亦 BUILD SUCCESS（單元 1193、整合 477、0 失敗、10:35 min），用於確認 DEF-167 單獨修改無破壞。
- **前端**：`npx tsc --noEmit` 通過（過程中攔截到一處 grep 沒抓到的漏改）、`npx eslint` 0 問題、`npm run build` ✓ Compiled successfully（54 個靜態頁面全數產生）。
- **Schema 守門**：`make validate-schema` **通過**（entity 與 Flyway schema 對齊，無漂移）。本輪未新增任何 Flyway 遷移，亦未變更 entity 欄位——`FeatureToggleResponse` 是 DTO 而非 entity，不影響 schema。

---

## 8. 範圍外（延後）

- **不實作配額強制**（上架商品/房源/貼文時真的檢查 `MAX_*` 上限並拒絕超額）。這是**新功能**而非技術債清理，且需先確認 PRD 是否真的要求配額限制——目前「上限」二字只出現在 `FEATURE_DEFINITIONS` 的文字描述裡，無任何規格條文。使用者在拍板時明確選擇了「清死碼 + 修顯示層」而非此項。
- **不補建四筆數值 toggle**（讓 `AdminService` 對齊完整 10 項）。既然配額沒有任何強制執行點，補建只會讓資料存在而不改變任何行為。
- **不清理 `getFeatureMap`**：該私有 helper 有同樣的「把數值 key 當布林塞入 map」寫法，但它不直接餵給開關 UI，且消費端未查證。依 Rule 3（精準改動）不順手改，記為候選（見 §8）。
- **不處理 DEF-103/104/105**（三筆 defense-in-depth 輸入驗證）：使用者本輪選擇的是 DEF-166/167，未含此三筆。

---

## 9. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) **v2.0 §3.2**，本節為必要章節。本輪是 v2.0 生效後的第一個 Sprint，以身作則寫出。（v2.0 已將 Velocity 降為選配，本輪不寫該節。）

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | `RELEASE_TRACKER` 回填 S136~144 | 本輪 §1.2(A) | ✅ 完成 | — |
| 1b | **`RELEASE_TRACKER` 回填 S145 自身的 push 狀態與雲端 CI 結果** | 本輪交付後 | ✅ **同日完成**（使用者拍板不留到下一輪） | commit `099e183` 已 push；雲端 CI run 34226984729 三個 job 全綠（3m12s／6m25s／1m30s）。**選擇同日回填而非依 `RELEASE_TRACKER` 原規則留待下輪，理由**：本 Sprint 修的正是「狀態欄斷更 8 個 Sprint」，若又留待下輪即自我否定 |
| 2 | 慣例文件承認實務（v2.0） | 本輪 §1.2(B) | ✅ 完成 | — |
| 3 | DEF-166 查證 | S144 §11 | ✅ 完成（判定非缺陷，結案） | — |
| 4 | DEF-167 修復 | S143 發現 | ✅ 完成 | — |
| 5 | **`TenantService.getFeatureMap` 的同型寫法** | 本輪 §8 | ⬜ 未處理 | **需登記為 DEF 或於下輪盤點時處理** |
| 5b | DEF-168 契約漂移修復 | 本輪 §6 | ✅ 完成 | — |
| 5c | **其他頁面是否有同型契約漂移** | 本輪 §6.2 | ⬜ 未掃描 | **本輪只修了功能開關頁；`notNullValue()` 式寬鬆斷言可能還掩蓋著別的漂移，值得作為獨立掃描角度** |
| 6 | **配額強制是否要實作** | 本輪 §5.1 | ⬜ 待產品決策 | **需 PRD 確認是否真的要求配額限制** |
| 7 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已在追蹤器 |
| 8 | `Sprint 138~144` 未更新 `DEFERRED_ITEMS_TRACKER` 版本區 | 本輪 §3.3 | ✅ 已揭露，不回填 | 比照 S93~100 裁定，不事後補文件 |

**第 5、6 項是本節存在的理由**：它們目前只寫在本 Plan 的 §7 裡。若沒有這張表，下一輪盤點同樣看不到它們——這正是 §1.2(B) 中「Action Items 維持必要」的論證所指的風險，也正是 DEF-166/167 上一輪的遭遇。

---

## 10. 刻意不做的事（避免範圍蔓延）

- 不回填 S105~144 的 Velocity 數字——慣例文件 v2.0 已將其降為選配，回填等於推翻 §4.1 對 S93~100 的既有裁定。
- 不修正 `RELEASE_TRACKER` 的「文件記帳 Tag vs 實際 git tag」落差——既有慣例，非本輪造成，僅揭露。
- 不重新翻案 `DEF-145`/`146`/`147`（Sprint 137 已查證為生產不可達的死流程）。
