# Sprint 312 Plan — DEF-435（管理員代操作的動態定價開關改看房源所屬店鋪）

## 1. 起點與範圍

### 1.1 起點

[Sprint 311](SPRINT_311_PLAN.md) 收尾，我用 `AskUserQuestion` 問了兩題，使用者**兩題都選了建議項**（這是工具回傳的真實互動答案，不是我對「請繼續」的推論）：

| 題 | 使用者的選擇 | 授權範圍 |
|---|---|---|
| 要現在 push Sprint 311 嗎 | 現在 push | Sprint 311 的 commit `53a9073b` → 已於 `164ca615..53a9073b` push，pre-push 輕量守門通過；雲端 CI run [40698764431](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40698764431) 三個 job 皆 success（約 13 分鐘），整合測試 962／0 失敗 |
| `DEF-435`：管理員代操作要不要改成檢查房源所屬店鋪 | 改檢查店鋪 | 追蹤表選項 (a)：建立規則、日曆改價、覆蓋價格三個端點，管理員代操作時的開關檢查改看房源所屬店鋪 |

範圍：`DEF-435`。不碰 `DEF-430`～`DEF-432`、`DEF-434`、`DEF-419`。

### 1.2 成功標準（開工時定義）

1. 管理員（`SUPER_ADMIN`）代操作的三個端點（建立規則、日曆改價、覆蓋價格）：結果**只取決於目標房源／商品所屬店鋪**的開關，與管理員請求的租戶的開關無關；店鋪關閉 → `403 E-2004` 且不建立規則；店鋪開啟 → 放行。
2. 店主的行為完全不變：仍是第一步檢查自己店鋪、不查房源／規則（`DEF-426` 的順序）。
3. **先實測再改**（追蹤表的 `DEF-435` 是讀碼推論）：紅燈先行，對象是真實 PostgreSQL＋完整 HTTP／JWT 鏈。
4. 每個新增的判斷都經突變驗證；全量後端 `mvn -o clean verify` 與完整 E2E 套件通過；`API_M12` 同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 追蹤表（Sprint 311 登記）：管理員代操作的檢查看「呼叫者租戶（系統租戶）」；「這是讀碼推論，沒有實測」 | **方向屬實，細節更精確**。讀 `TenantContextFilter.resolveEffectiveTenantId`：`SUPER_ADMIN` 的請求租戶**不是 token 裡的租戶**——有 `X-Tenant-ID` 標頭就用標頭指定的租戶，沒有才是系統租戶。前端 `axios` 對所有使用者都帶 localStorage 的登入租戶（平台管理員登入的租戶是系統租戶），所以實務上就是系統租戶。**實測**（修復前的程式碼、真實 PostgreSQL＋完整 HTTP／JWT 鏈）：沒帶標頭、系統租戶開關暫時打開（還原 Flyway 種子）、店鋪開關關閉 → 建立規則 `201`、日曆改價 `200`、覆蓋價格 `200`，三個都成功，建出的規則卻永遠不生效 |
| 「正式環境的管理員幾乎一定通過這道檢查；測試裡的管理員反而都被擋」 | 兩半都屬實：Flyway 種子（V1、V6）的系統租戶開關開啟；Hibernate 測試庫（`TestDatabaseInitializer`）是關閉。我的第一版測試固件就栽在後者（§6 第 1 點） |
| 選項說明「三個端點同一個落差，改法也一致」（我在 Sprint 311 寫的） | **範圍屬實，但三個端點取得『店鋪』的方式不同**：`createRule` 有兩個可選的 id（房源與商品，每個都要查）、`setCalendarPrice` 是房源、`overridePrice` 要看既有規則所作用的房源。讀碼還發現三個端點的『蓋章租戶』並不一致（`createRule` 蓋請求租戶、`setCalendarPrice` 蓋房源的租戶、`overridePrice` 蓋既有規則的租戶），所以 `overridePrice` **不能**看規則上蓋的租戶（開工前我的構想是看 `existingRule.getTenantId()`，讀碼後推翻）；這個不一致用探針量證，登記為 `DEF-436` |
| 受影響的既有測試 | 以**括號配對**掃描整個 test 目錄對這三個方法的呼叫（§6 第 2 點：我第一次用單行 grep 找『以 `true` 呼叫』，漏掉跨行的 `.build(), true);`）：直接呼叫的只有 `PricingServiceTest`；以 `true`（SUPER_ADMIN）呼叫且受影響的既有案例 2 個（`createRule_superAdminBypassesOwnership`、`createRule_savesRuleWithTenantRelation`），第三個（`createRule_superAdmin_noListing…`）不帶目標、走維持原行為的分支，不受影響。HTTP 層的 SUPER_ADMIN 案例（`PlatformSelfRunWriteIsolationE2ETest`、`PricingSystemTenantIsolationE2ETest` 等）靠全量 verify 驗證 |

## 2. 做法

**`FeatureToggleService`**：新增 `checkFeatureEnabledForTenant(tenantId, featureKey)`——不讀 `TenantContext`，判斷就是定價引擎用的 `isFeatureEnabledForTenant`（所以『能建立』與『會生效』不分岔），未啟用丟 `E-2004`；`checkFeatureEnabled` 改為委派給它（行為不變）。呼叫端必須傳入從資料庫資源推得的租戶（如 `listing.getTenantId()`），不可傳入請求帶來的值。

**`PricingService`**（兩個私有方法，三個端點各兩行）：

| 端點 | 店主（不變） | `SUPER_ADMIN`（新） |
|---|---|---|
| `createRule` | 第一步檢查自己店鋪的開關，不查房源／規則 | 擁有權檢查（對管理員是略過）之後，`roomListingId`、`listingId` **每個帶入的 id** 所屬店鋪的開關都要開啟；找不到的 id 回 `E-4000`；兩個都沒帶時沒有店鋪可看，維持看請求租戶 |
| `setCalendarPrice` | 同上 | 房源存在與擁有權檢查之後，該房源所屬店鋪的開關 |
| `overridePrice` | 同上 | 規則存在與擁有權檢查之後，**規則所作用房源**所屬店鋪的開關（不是規則上蓋的租戶） |

為什麼管理員的檢查排在查詢之後：要知道『哪間店鋪』必須先查房源／規則，沒有辦法像店主一樣第一步就擋。店主路徑沒有動，`DEF-426` 的『不查房源／規則、不寫入』照舊。

## 3. 測試

**紅燈先行（真實棧）**：`StoreToggleGatesApplicationE2ETest` 新增 4 個案例（原 6 個維持綠燈）：

1. 兩位管理員（自己這一側的開關一開一關，以 `X-Tenant-ID` 標頭指向我控制開關的租戶）× 兩間店鋪（開關一開一關）× 三個端點 = 12 格的結果表，**一次比對整張表**（紅燈時能看到所有格子，不是只看到第一個失敗）；店鋪開啟時覆蓋的是管理員自己建立的那條規則（它蓋的是請求租戶，才分得出關卡看的是『規則上蓋的租戶』還是『房源所屬店鋪』）。
2. 同時帶房源與商品 id：管理員自己這一側的開關刻意取與預期相反的一邊。
3. 正式環境的樣子：沒帶標頭、系統租戶的動態定價開關暫時打開（`finally` 還原；原本沒有開關列則刪掉我補的列），店鋪關閉 → 三個端點被擋、店鋪開啟 → 放行。
4. 對不存在的房源建立規則 → `404 E-4000`，資料庫沒有新規則。

**單元**：`PricingServiceTest.SuperAdminDelegationToggleTests`（13 個）、`FeatureToggleServiceTest`（+4）。誘餌設計沿用 Sprint 309：呼叫者租戶的檢查在店鋪關閉的案例放行、在店鋪開啟的案例會拋，並以 `verify(never())` 證明正確的實作從不去讀它。更新兩個原本假設『SUPER_ADMIN 不查房源』的既有案例（只調整固件，驗證意圖不變）。

**紅燈實錄（修復前，對未修改的正式碼）**：第一版固件 3／3 新案例紅，但原因不對（§6 第 1 點）；修正固件後 4／4 紅，原因都是預期的：

- 結果表：管理員自己這一側開關開啟＋店鋪關閉 → 建立規則 `201`、日曆改價 `200`、覆蓋價格 `200`（預期 `403 E-2004`）；自己這一側關閉＋店鋪開啟 → 三個端點都 `403 E-2004`（預期 `201`／`200`／`200`）。
- 兩個 id：房源開＋商品關 → `201`；房源關＋商品開 → `201`；兩個都開（管理員自己這一側關閉）→ `403`。
- 正式環境的樣子：店鋪關閉的三個端點 `201`／`200`／`200`。
- 不存在的房源：`201`（測試庫沒有外鍵，懸空規則被寫入）。

## 4. 突變驗證

驅動 `mutate_s312.py`（沿用 Sprint 311 的做法：備份→套用→等 class 比原始碼新→只跑相關的單元＋真實棧類別→還原→SHA 比對）。基準線 Z0：157 個案例 0 失敗。**14 個突變全數擊殺，每次都確認檔案還原。**

| 突變 | 內容 | 抓到它的案例 |
|---|---|---|
| M1a | `SUPER_ADMIN` 完全不檢查目標店鋪 | 17：單元 13（含兩個更新過的既有案例，因嚴格 stub 的『未使用的 stub』）＋真實棧 4 |
| M1b | `SUPER_ADMIN` 也經過呼叫者租戶檢查（修復前的呼叫者那一側） | 8：單元 6＋真實棧 2（結果表、兩個 id） |
| M2 | 只查第一個 id | 3：單元 2＋真實棧 1 |
| M3 | 只查最後一個 id | 4：單元 3＋真實棧 1（其中 `secondSuppliedIdStoreOff` 是附帶紅燈：第一個 id 的 stub 沒被用到，不是斷言本身抓到） |
| M14 | `createRule` 只傳房源 id | 3：單元 2＋真實棧 1 |
| M15 | `createRule` 只傳商品 id | 12：單元 8＋真實棧 4 |
| M12 | `setCalendarPrice` 的 `SUPER_ADMIN` 旗標丟掉 | 4：單元 2＋真實棧 2 |
| M4 | `overridePrice` 看規則上蓋的租戶（不是房源所屬店鋪） | 3：單元 2＋真實棧 1（因為結果表覆蓋的是管理員自己建立的規則） |
| M5 | 沒帶目標時不再看請求租戶 | 1：單元（**只有單元案例抓得到**） |
| M6 | 店主也走店鋪開關檢查 | 1：單元（**只有單元案例抓得到**） |
| M11 | `createRule` 拿掉店主的第一步檢查 | 2：單元（**只有單元案例抓得到**） |
| M8 | `checkFeatureEnabledForTenant` 改讀 `TenantContext` | 7：單元 4＋真實棧 3 |
| M9 | `checkFeatureEnabledForTenant` 一律放行 | 11：單元 7（含 `checkFeatureEnabled` 原有的三個案例，證明委派沒改壞原行為）＋真實棧 4（含 `DEF-426` 的案例） |
| M10 | 錯誤碼改成 `E-1007` | 6：單元 2＋真實棧 4 |

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| Sprint 311 雲端 CI（`53a9073b`） | run [40698764431](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40698764431) 三個 job 皆 success（Backend Unit Tests 2 分 53 秒／Frontend Lint & Build 54 秒／Backend Integration Tests & Package 10 分 06 秒，整個 run 13 分 05 秒）；`gh run view --json status,conclusion,headSha` 交叉確認 headSha 為 `53a9073b`；下載整合 job 的完整雲端日誌（約 56 MB）確認整合測試 **962／0 失敗／0 錯誤／0 跳過**、`FeatureRequestLifecycleIntegrationTest` 4／0、`BUILD SUCCESS` |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**30 分 17 秒**（Sprint 311 是 19 分 13 秒，這次較長；日誌沒有失敗或逾時跡象，我沒有追查原因）；單元 **2411／0 失敗**（Sprint 311 的 2394＋本輪 17：`SuperAdminDelegationToggleTests` 13、`FeatureToggleServiceTest` 4）；整合 **966／0 失敗**（962＋本輪 4：`StoreToggleGatesApplicationE2ETest`）；Checkstyle 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個；測試報告 XML 合計（surefire 2411、failsafe 966）與日誌一致。受影響的既有類別：`PlatformSelfRunWriteIsolationE2ETest` 18／18、`PricingSystemTenantIsolationE2ETest` 6／6、`PricingRulePersistenceE2ETest` 3／3、`PricingRuleScopeIsolationE2ETest` 13／13、`M12PricingIntegrationTest` 12／12、`M12PricingProductIntegrationTest` 3／3、`PricingRulesCorruptRowFlywayIntegrationTest` 4／4、`FeatureRequestLifecycleIntegrationTest` 4／4、`BookingControllerE2ETest` 25／25 |
| 完整 E2E 套件（Flyway 資料庫＋真實 Redis；`validate-e2e.sh` 副本、list 報表） | **286 通過／0 跳過／0 失敗**，7.6 分鐘（JAR 是 verify 剛打包的；前端沿用 Sprint 311 的建置，本輪沒有改動前端）；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功（「entity 與 Flyway schema 對齊，無漂移」）；認證端點被限流 0 次；清單中跳過、flaky 的標記皆 0 個；定價相關案例（`E2E-PRICING-01`～`03`、`E2E-PRICING-CAL-01`／`02`、`E2E-PCART-*`）都在清單內並通過——店主路徑在真實後端上不變 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS`（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1） |
| IDE Problems | 438 個檔案條目、診斷 **0**；本輪改過的 5 個 Java 檔（`FeatureToggleService`、`PricingService`、`StoreToggleGatesApplicationE2ETest`、`FeatureToggleServiceTest`、`PricingServiceTest`）都在條目內且 0 診斷 |
| 沒有執行的項目 | 本輪沒有改 schema、遷移或前端，所以 `make validate-schema-doc`、ESLint、`tsc` 不在範圍（沒有執行） |

## 6. 過程中的失誤與修正

1. **測試固件的假設被量測推翻**：我最初給兩位 `SUPER_ADMIN` 的 token 帶不同的「自己租戶」（開關一開一關），以為可以模擬管理員自己這一側的開關。第一次紅燈卻顯示：店鋪開啟的格子**全被擋**，連不存在的房源也先被擋 `E-2004`——兩人實際上都是系統租戶（測試庫開關關閉），被擋的原因全是錯的。讀 `TenantContextFilter` 才知道管理員的請求租戶不看 token。改成用 `X-Tenant-ID` 標頭指向我控制開關的租戶，才得到有意義的紅燈。這正是『紅了但原因不對』要逐項讀失敗訊息的原因。
2. **單行 grep 漏掉跨行呼叫**：我用單行 `grep` 找『以 `true` 呼叫這三個方法的測試』只找到 2 處，結果跑單元測試時 `createRule_savesRuleWithTenantRelation`（`.build(), true);` 在下一行）才紅。改用括號配對的腳本掃描全部 45 處呼叫，確認直接呼叫者只有 `PricingServiceTest`、以 `true` 呼叫且受影響的既有案例只有 2 個。
3. **Mockito 嚴格 stub 的參數不符**：我的第一版 `createRule_secondSuppliedIdStoreOff` 只備了『關閉的店鋪會拋』，第一個 id（開啟的店鋪）的呼叫被 Mockito 當成 `PotentialStubbingProblem`。加上預設『每間店鋪都開啟』的 `lenient` stub，再用特定店鋪的 `doThrow` 覆蓋。
4. **探針的第 4 項沒有資訊量**：『蓋章租戶』探針原本想順便驗證引擎有沒有套用那條規則，但探針規則的有效期在 17 年後，店主試算出的 `2400` 來自種子規則——我沒有採用這一項，引擎依房源 id 選規則是讀 `PricingRuleRepository` 得到的，不是探針量到的。
5. **本機測試資料庫不見了**：Sprint 311 的 push 守門（`act`）結束後 `nk-test-pg`／`nk-test-redis` 都不在，跑整合測試前用 `make test-db-up` 重建；全量 verify 前又重建一次，確保是全新資料庫。
6. 開工前我的實作構想（`overridePrice` 看 `existingRule.getTenantId()`）在讀碼後推翻（§1.3 第三列），沒有進入程式碼。

## 7. 限制（誠實揭露）

1. **Flyway 資料庫對不存在房源 id 的行為沒有實測**：測試庫（Hibernate 產生的 schema）沒有外鍵，修復前回 `201`；`pricing_rules.room_listing_id`／`listing_id` 在 Flyway 資料庫有外鍵，預期被資料庫拒絕。修復後的行為（`404 E-4000`，在寫入之前）與資料庫無關。
2. **兩個都沒帶房源／商品 id 的規則**（引擎永遠選不到它）沒有店鋪可看，管理員仍看請求租戶（維持原行為）；測試只有單元案例（M5）。
3. `updateRule`／`deleteRule` 仍不查開關（本題不涵蓋）。
4. 管理員的真實設定（沒帶標頭、系統租戶開關開啟）在 Hibernate 測試庫要靠『暫時打開系統租戶開關、`finally` 還原』才能還原；這會短暫改動共用的列（`PlatformSelfRunWriteIsolationE2ETest` 已有同樣做法），測試序列執行、結束時還原。
5. **`DEF-436`（蓋章租戶不一致）只登記、沒有改**，見 §9。
6. M5、M6、M11 只有單元案例抓得到（整合層沒有對應案例，與預期一致）。
7. **本輪尚未 push，雲端 CI 沒有驗證這一輪**：使用者的互動答案只授權了 Sprint 311 的 push；本輪改變生產行為（管理員代操作的開關檢查），所以收尾再問一次。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | 兩個都沒帶房源／商品 id 的規則，管理員仍看請求租戶 | 使用者的裁決是『改檢查店鋪』，這種規則沒有店鋪；引擎永遠選不到它；維持原行為是最小改動 | 告訴我改成不檢查，或直接拒絕這種規則 |
| 2 | 同時帶房源與商品 id 時，兩家店鋪的開關都必須開啟 | 比照 `DEF-423`『每個帶入的 id 都驗證』；一條規則同時作用在兩處，任何一處不會生效就是『騙人』 | 告訴我改成任一開啟即放行 |
| 3 | 管理員對不存在的房源建立規則回 `E-4000` | 要看店鋪必須先查房源；與店主路徑的行為一致（先前管理員完全不查房源） | 告訴我改回不驗證 |
| 4 | 新增 `FeatureToggleService.checkFeatureEnabledForTenant`，`checkFeatureEnabled` 改為委派給它 | 使用者選的選項文字就是這個機制；委派避免兩份錯誤訊息，也讓『能建立』與『會生效』用同一個判斷。順帶把 `checkFeatureEnabled` Javadoc 裡寫錯的錯誤碼 `E_2020` 更正為實際丟的 `E_2004`（改的是我正在改的那個方法的說明） | 告訴我拆成兩份獨立實作 |
| 5 | 登記 `DEF-436`，不順手改蓋章租戶 | Rule 3；這可能是刻意的設計（平台代營運的規則由平台管理），沒有需求文件，涉及店主能不能管理平台代建的規則 | 告訴我選哪個方向 |
| 6 | 本輪不自動 push，收尾再問 | 使用者的互動答案只授權了 Sprint 311 的 push（『approval in one context doesn't extend to the next』） | 告訴我 push |

## 9. 新登記的缺陷

| ID | 摘要 | 狀態 |
|---|---|---|
| `DEF-436` | `SUPER_ADMIN` 經 `POST /rules` 在店鋪房源上建立的規則蓋成請求租戶（系統租戶），店主列得到卻 `PUT`／`DELETE` 都回 `403 E-1007`；同一位管理員經 `POST /calendar/price` 建的規則蓋店鋪、店主可停用。探針實測，見 `API_M12` §7 | 未處理，**待你裁決方向** |

`DEF-435` 已於本輪修復。`DEF-430`～`DEF-432`、`DEF-434` 未動。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 312 列。本輪的 commit 留在本機，**尚未 push**（見 §7 第 7 點）；push 後要回填雲端 CI 結果。

本輪的 commit 範圍：生產程式 `FeatureToggleService`、`PricingService`；測試 `FeatureToggleServiceTest`、`PricingServiceTest`、`StoreToggleGatesApplicationE2ETest`；文件 `API_M12_Dynamic_Pricing.md`（v2.9）、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`（含 Sprint 311 的 push 與雲端 CI 回填）、`SPRINT_311_PLAN.md`（回填）與本計畫書。沒有 Flyway 遷移、沒有前端變更。
