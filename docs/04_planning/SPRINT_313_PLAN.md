# Sprint 313 Plan — DEF-436（管理員建立的定價規則蓋房源所屬店鋪）

## 1. 起點與範圍

### 1.1 起點

[Sprint 312](SPRINT_312_PLAN.md) 收尾，我用 `AskUserQuestion` 問了兩題，使用者**兩題都選了建議項**（這是工具回傳的真實互動答案，不是我對「請繼續」的推論）：

| 題 | 使用者的選擇 | 授權範圍 |
|---|---|---|
| 要現在 push Sprint 312 嗎 | 現在 push | Sprint 312 的 commit `a047721e` → 已於 `53a9073b..a047721e` push，pre-push 輕量守門通過；雲端 CI run [40729658346](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40729658346) 三個 job 皆 success（約 13 分鐘），整合測試 966／0 失敗 |
| `DEF-436`：管理員用 `POST /rules` 在店鋪房源上建的規則要蓋誰的租戶 | 蓋房源所屬店鋪（建議） | 選項 (a)：`PricingService.createRule` 的蓋章租戶改為房源所屬店鋪；不綁房源的規則仍蓋請求租戶 |

範圍：`DEF-436`。不碰 `DEF-430`～`DEF-432`、`DEF-434`、`DEF-419`。

### 1.2 成功標準（開工時定義）

1. 管理員（`SUPER_ADMIN`）帶房源或商品 id 用 `POST /rules` 建立的規則蓋**目標房源／商品所屬店鋪**（`Tenant` 關聯、影子欄位、稽核紀錄的租戶一致）；該店店主列得到、`PUT` 改得動、`DELETE` 停用得了。
2. 不變的部分：店主建立的規則仍蓋自己的店鋪、不多查房源；兩個 id 都沒帶的規則仍蓋請求租戶；`DEF-435` 的開關檢查不變。
3. 紅燈先行（真實 PostgreSQL＋完整 HTTP／JWT 鏈）；每個新增的判斷都經突變驗證；全量後端 `mvn -o clean verify` 與完整 E2E 套件通過；`API_M12` 同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 選項說明：「改動範圍是 `PricingService.createRule` 一個方法，用到請求租戶的三處（`Tenant` 關聯的查詢與蓋章、稽核紀錄的租戶、傳給重疊檢核的 `tenantId`）」 | **屬實**。`createRule` 裡變數 `tenantId` 的使用點：`enforceRuleLimitAndConflict(request, tenantId)`、`tenantRepository.findById(tenantId)`、`.tenantId(tenantId)`（與同一個查出來的 `.tenant(tenant)`）、稽核紀錄；重疊檢核的 `findActiveRulesInScope` 只有第三個分支（不綁房源也不綁商品）才用到這個租戶，綁了房源或商品的規則是以房源或商品為範圍 |
| 選項說明：「既有 4 個斷言蓋章租戶的測試都不受影響（不綁房源，或房源本來就屬系統租戶），實際以跑測試為準」 | **斷言蓋章租戶的 4 個測試確實不受影響**，但我沒有預料到另外 3 個單元案例需要調整固件：`createRule_superAdminBypassesOwnership` 與我自己在 `SuperAdminDelegationToggleTests` 的兩個案例（`createRule_storeToggleOn_passesEvenWhenCallerToggleIsOff`、`createRule_bothSuppliedIdsStoresOn_passes`）——它們的房源屬於別間店鋪，規則現在蓋那間店鋪，所以要備好那間店鋪的 `Tenant` 實體（第一次單元執行恰好是這 3 個以『找不到租戶』失敗）。只改固件，驗證意圖不變 |
| Sprint 312 的結果表案例：店鋪開啟時覆蓋『管理員自己建立的那條規則』來分辨『看規則蓋的租戶』與『看房源所屬店鋪』 | **DEF-436 之後這個設計失效**：管理員建的規則現在蓋店鋪，不再能分辨。改成直接種一條**蓋著關閉租戶、綁在開啟店鋪房源上的舊式規則**（有效期在 30 年後，不影響其他案例的價格）當覆蓋目標——它同時代表『上線前留下的舊資料』，所以這個判斷在修復後仍然必要 |
| 紅燈案例的預期差異 | 對未修改的正式碼實測 6 項：回應 `tenantId` 與資料庫 `tenant_id`（沒帶標頭）是系統租戶；標頭指向關閉租戶時蓋的是**請求租戶**（`f8b8d15e…`）；店主 `PUT`、`DELETE` 都 `403 E-1007`；停用後 `is_active` 仍是 `true`。『店主列得到』與『不綁房源仍蓋系統租戶』兩項沒有差異（與 Sprint 312 探針一致）。改過的結果表案例在未修正式碼上維持綠燈 |

## 2. 做法

`PricingService.createRule`：新增私有方法 `resolveRuleTenantId(isSuperAdmin, requestTenantId, roomListingId, listingId)`——

| 呼叫者 | 蓋章租戶 |
|---|---|
| 店主 | 呼叫者租戶（不變；其後的擁有權檢查已保證目標屬於他，所以不多查房源） |
| `SUPER_ADMIN`，帶房源或商品 id | **第一個目標所屬的店鋪**（有 `roomListingId` 就以它為準；兩個 id 屬於不同店鋪時以房源的店鋪為準） |
| `SUPER_ADMIN`，兩個 id 都沒帶 | 請求租戶（沒有店鋪可蓋；規則永遠不會被引擎選到；不變） |

這個租戶同時用在：`Tenant` 關聯的查詢與蓋章（INSERT 依據的是 `.tenant(...)` 關聯，DEF-414）、影子欄位 `tenantId`、稽核紀錄的租戶、傳給 `enforceRuleLimitAndConflict` 的租戶。查不到目標房源（只可能是競態——`DEF-435` 的檢查已確認存在）直接丟 `E_4000`，不靜默退回請求租戶。

## 3. 測試

**真實 PostgreSQL（`StoreToggleGatesApplicationE2ETest`，+1，紅燈先行）**：沒帶標頭與標頭指向關閉租戶兩種請求，管理員建立的規則都蓋店鋪（回應與資料庫 `tenant_id`）；該店店主列得到、`PUT` 改得動（資料庫 `priority`）、`DELETE` 停用得了（資料庫 `is_active`）；不綁房源的規則仍蓋系統租戶。一次比對所有差異（紅燈時看得到全部）。既有的結果表案例改以舊式規則當覆蓋目標（§1.3 第三列）。

**單元（`PricingServiceTest.SuperAdminDelegationToggleTests`，+4）**：管理員在房源上建立→Tenant 關聯、影子欄位、稽核紀錄都是店鋪，且請求租戶（`TENANT_ID`）完全沒被拿來蓋章；只帶商品 id；房源與商品屬於不同店鋪→房源的店鋪；店主蓋呼叫者租戶且只查房源一次。調整 3 個既有／相鄰案例的固件（§1.3 第二列）。

## 4. 突變驗證

驅動 `mutate_s313.py`（沿用前兩輪）。基準線 Z0：148 個案例 0 失敗。**7 個突變：6 個擊殺、1 個存活，存活的是預先判定的等價突變。** 每次都確認檔案還原。

| 突變 | 內容 | 抓到它的案例 |
|---|---|---|
| D1 | 回到蓋請求租戶（修復前） | 5：單元 4（含 `createRule_superAdminBypassesOwnership`：它備的店鋪 `Tenant` stub 沒被用到，嚴格 stub 抓出）＋真實棧 1 |
| D2 | 兩個 id 屬於不同店鋪時以商品的店鋪為準 | 1：單元 |
| D3 | 稽核紀錄的租戶仍用請求租戶 | 1：單元（**只有單元案例抓得到**） |
| D4 | `Tenant` 關聯仍用請求租戶（影子欄位是店鋪） | 3：單元 2＋真實棧 1（資料庫 `tenant_id`——INSERT 依據的是關聯物件） |
| D5 | 店主也經房源解析蓋章租戶（多一次查詢；結果相同） | 2：單元（**只有單元案例抓得到**，靠『只查房源一次』的斷言） |
| D6 | 不綁房源的管理員規則不再退回請求租戶 | 2：單元 1＋真實棧 1 |
| D7 | `enforceRuleLimitAndConflict` 改傳請求租戶 | **0——等價突變**：該參數只在不綁房源的規則才有作用，而那種規則的蓋章租戶本來就是請求租戶；綁了房源或商品的規則是以房源或商品為範圍，不看租戶。存活不代表測試漏洞 |

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| Sprint 312 雲端 CI（`a047721e`） | run [40729658346](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40729658346) 三個 job 皆 success（Backend Unit Tests 3 分 40 秒／Frontend Lint & Build 45 秒／Backend Integration Tests & Package 9 分 07 秒，整個 run 12 分 52 秒）；`gh run view --json status,conclusion,headSha` 交叉確認 headSha 為 `a047721e`；下載整合 job 的完整雲端日誌（約 56 MB）確認整合測試 **966／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS` |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 35 秒**（Sprint 312 那次是 30 分 17 秒；上次較長的原因沒有追查）；單元 **2415／0 失敗**（2411＋本輪 4：`SuperAdminDelegationToggleTests`）；整合 **967／0 失敗**（966＋本輪 1：`StoreToggleGatesApplicationE2ETest`）；Checkstyle 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個；測試報告 XML 合計（surefire 2415、failsafe 967）與日誌一致。受影響的既有類別：`StoreToggleGatesApplicationE2ETest` 11／11、`PlatformSelfRunWriteIsolationE2ETest` 18／18、`PricingSystemTenantIsolationE2ETest` 6／6、`PricingRulePersistenceE2ETest` 3／3、`PricingRuleScopeIsolationE2ETest` 13／13、`M12PricingIntegrationTest` 12／12、`M12PricingProductIntegrationTest` 3／3、`PricingRulesCorruptRowFlywayIntegrationTest` 4／4、`FeatureRequestLifecycleIntegrationTest` 4／4、`BookingControllerE2ETest` 25／25 |
| 完整 E2E 套件（Flyway 資料庫＋真實 Redis；`validate-e2e.sh` 副本、list 報表） | **286 通過／0 跳過／0 失敗**，6.6 分鐘（JAR 是 verify 剛打包的；前端沿用 Sprint 311 的建置，本輪沒有改動前端）；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功；認證端點被限流 21 次（E2E 輔助函式依 `Retry-After` 重試，全數自癒、最後全綠）；清單中跳過、flaky 的標記皆 0 個；定價相關案例（`E2E-PRICING-*`、`E2E-PCART-*`）都在清單內並通過 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS`（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1） |
| IDE Problems | 453 個檔案條目、診斷 **0**；本輪改過的 `PricingService`、`PricingServiceTest`、`StoreToggleGatesApplicationE2ETest` 都在條目內且 0 診斷 |
| 沒有執行的項目 | 本輪沒有改 schema、遷移或前端，所以 `make validate-schema-doc`、ESLint、`tsc` 不在範圍（沒有執行） |

## 6. 過程中的失誤與修正

1. **選項說明低估了固件調整**（§1.3 第二列）：我寫『既有 4 個斷言蓋章租戶的測試不受影響』是對的，但沒有一併盤點『會因為規則現在蓋別間店鋪而需要備 `Tenant` 實體』的其他單元案例——與 Sprint 308／311 的『選項說明的範圍不完整』同類，這次的代價只是 3 個固件調整，沒有改變決定。
2. **自己上一輪的設計被這一輪的修復掏空**（§1.3 第三列）：Sprint 312 結果表裡『覆蓋管理員剛建立的規則』是為了分辨兩種檢查依據，DEF-436 讓那條規則改蓋店鋪，原本能擊殺 `M4` 的真實棧案例就不再有牙齒了。我在動手前的唯讀盤點就預見這一點，所以先改好舊式規則再跑紅燈，沒有等到突變時才發現。
3. 本機測試資料庫在 pre-push 守門結束後再次消失（`nk-test-pg`／`nk-test-redis` 都不在），跑整合測試前用 `make test-db-up` 重建；全量 verify 前又重建一次。
4. 前景 `sleep` 被工具擋下（改用背景任務與完成通知等待），沒有造成任何問題。

## 7. 限制（誠實揭露）

1. **既有資料不回填**：修復前由管理員建立、蓋成系統租戶（或標頭租戶）的規則仍蓋著原租戶，店主仍列得到卻管不到。需要的話要另做資料遷移（把 `pricing_rules.tenant_id` 改成所綁房源／商品所屬的店鋪），這是對正式資料的變更，**沒有被授權，所以沒做**。
2. **管理員不帶 id 的 `GET /rules` 列的是請求租戶名下的規則**，他在店鋪房源上建立的規則蓋的是那間店鋪，不再出現在那個列表（要帶 `roomListingId`／`listingId` 查）。這是選項 (a) 的直接結果，已寫進 `API_M12` §4.3。
3. 兩個 id 屬於不同店鋪（只有管理員做得到）時以房源的店鋪為準——規則同時作用在兩處，只能蓋一個租戶；這是我替使用者做的決定（§8 第 1 點）。
4. `D3`、`D5` 只有單元案例抓得到（真實棧看不到稽核紀錄的租戶與查詢次數）。
5. **本輪尚未 push，雲端 CI 沒有驗證這一輪**：使用者的互動答案只授權了 Sprint 312 的 push；本輪改變生產行為（管理員建立的規則蓋章租戶），所以收尾再問一次。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | 兩個 id 屬於不同店鋪時，以 `roomListingId` 的店鋪為準 | 選項說明沒有講；與專案裡處理兩個 id 的慣例一致（`roomListingId` 優先，如上限與重疊檢核的範圍選擇）；只有管理員做得到這種請求，實務上極罕見 | 告訴我改成拒絕這種請求（`E-1007` 或 `E-4003`），或蓋商品的店鋪 |
| 2 | 既有資料不回填、不寫 Flyway 遷移 | 對正式資料的變更需要另外授權；店主還是可以請平台管理員停用舊規則（管理員不受擁有權限制） | 告訴我寫遷移（我會先量出受影響的列數與樣態） |
| 3 | 稽核紀錄的租戶跟著蓋章租戶 | 與 `setCalendarPrice` 一致；店主在自己店鋪的稽核紀錄看得到平台代建的規則 | 告訴我稽核紀錄維持請求租戶 |
| 4 | `enforceRuleLimitAndConflict` 傳蓋章租戶（等價於請求租戶） | 語意一致：重疊檢核的範圍是『即將擁有這條規則的租戶』；D7 證明結果相同 | 告訴我維持傳請求租戶 |
| 5 | 本輪不自動 push，收尾再問 | 使用者的互動答案只授權了 Sprint 312 的 push（『approval in one context doesn't extend to the next』） | 告訴我 push |

## 9. 新登記的缺陷

本輪沒有新登記。`DEF-436` 已修復；`DEF-430`～`DEF-432`、`DEF-434` 未動。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 313 列。本輪的 commit 留在本機，**尚未 push**（見 §7 第 5 點）；push 後要回填雲端 CI 結果。

本輪的 commit 範圍：生產程式 `PricingService`；測試 `PricingServiceTest`、`StoreToggleGatesApplicationE2ETest`；文件 `API_M12_Dynamic_Pricing.md`（v2.10）、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`（含 Sprint 312 的 push 與雲端 CI 回填）、`SPRINT_312_PLAN.md`（回填）與本計畫書。沒有 Flyway 遷移、沒有前端變更。
