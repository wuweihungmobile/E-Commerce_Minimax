# Sprint 307 Plan — DEF-417／DEF-423／DEF-422（使用者貼回 Sprint 306 總結並說「請繼續完成任務」，沒有 `==>`）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 306](SPRINT_306_PLAN.md) 的完成總結，結尾只有一句「請繼續完成任務！」，**沒有任何 `==>` 回覆**。

我的解讀（推論，寫下來讓使用者能糾正）：

1. **這是「無 `==>`」變體**：不是對任何「等你決定」項目的授權。Sprint 306 總結裡沒有等使用者決定的項目；唯一的使用者端動作是 `make down-clean`（只有使用者能做），本輪沒有碰。
2. **做的是總結自己列為「還沒做」、AI 能獨立處理的事**：`DEF-422`（總結寫「只掃描未分析」）；以及追蹤表標明「不需產品決策」的 `DEF-417`（Sprint 306 計畫書 §12 把它與 `DEF-422` 並列為「皆為讀碼推論或掃描結果，需先實測」）。
3. **不碰 `DEF-419`**：追蹤表標明「屬權限模型決策，要使用者決定」。
4. 開工前先自己核對上一輪的「雲端 CI 全綠」，不直接採信總結（§1.3 第一列）。

### 1.2 成功標準（開工時定義）

1. `DEF-417`：先用真實 PostgreSQL＋完整 HTTP／JWT 鏈，在**未修**的程式上實測讀碼推論是否屬實，再改；修法要有「對自己仍照常運作」的對照案例；每個守門與每個查詢條件都要有突變驗證。
2. `DEF-422`：先實測影響（不憑「`findAll()` 看起來不好」就改），再決定哪些要修、哪些維持現狀並寫明理由。
3. 過程中讀到的相鄰缺陷：屬缺陷且不需產品決策就修並登記；需要決策的只登記。
4. 全量後端 `mvn -o clean verify`（全新測試 DB）通過、標準 `make validate-e2e` 通過、文件與追蹤表同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「雲端 CI 全綠」（Sprint 306 總結） | `gh run list`：run 40062709075 對 `01edcea1` 為 `completed／success`；另有 2026-10-26 的排程「依賴安全審計」run 40276486701 對同一個 commit 也是 `success`。本機 `HEAD` 領先 `origin/main` 1 個 commit（`f3f5b7d2`，純文件的回填），工作樹乾淨 |
| 「`DEF-417`：商品規則的上限與重疊檢核查的是全平台所有租戶的無房源規則」（Sprint 303 登記時是**讀碼推論，沒有實測**） | **實測屬實**（§4）：`PricingRuleScopeIsolationE2ETest` 第一版 10 個案例對未修的程式 **7 個紅燈、3 個對照維持綠燈**。包含跨租戶寫入——別家店帶 `confirmOverride=true`，我這家店的規則被軟刪除 |
| 「`createRule` 的擁有權檢查（`DEF-242`）已完整」 | **不完整**：檢查的參數是 `roomListingId != null ? roomListingId : listingId`，只驗證其中一個。兩個 id 同時帶入時，`listingId` 完全沒被驗證。實測（修復前）：帶自己的房源 id、夾帶別家店的商品 id，回 **201**，別家商品的 `effective-price` 從 100.00 變成 **1.0**。登記為 `DEF-423` |
| 「前端會不會送兩個 id」 | 不會：`PricingRuleList.tsx` 建立規則只送 `roomListingId`（`CreatePricingRuleRequest` 型別沒有 `listingId`）。商品規則只能走 API（或 Playwright 規格） |
| 「全庫其他請求 DTO 有沒有同型的雙 id 欄位」 | 掃描 `api/dto`：同時有 `roomListingId` 與 `listingId` 的只有 `PricingDto` 與 `OrderDto`；`OrderDto` 的在回應類別（`BookingResponse`）裡，不是請求。主程式碼沒有其他 `roomListingId != null ? … : listingId` 的合併寫法 |
| 「Flyway 資料庫的 `users.role`、`tenants.status` 有沒有 CHECK 約束」（Sprint 306 寫「我沒有查」） | `tenants.status`：V1 是純 `VARCHAR(20)`；**實測**在 94 支遷移跑完的資料庫直接 `INSERT` 一列 `LEGACY_STATUS` 成功，所以沒有 CHECK。`users.role`：只讀了 V1（`VARCHAR(50)`，沒有 CHECK）並 `grep` 後續遷移沒有改它，**沒有實測**寫入 |
| 「`AdminService.getPlatformStats` 遇到壞資料列會整個 500」（`DEF-422` 登記時的推論） | **實測屬實**（§4）：Flyway 資料庫裡一列 `tenants.status = 'LEGACY_STATUS'`，`GET /v2/admin/stats` 回 **500**，後端日誌根因 `No enum constant …TenantStatus.LEGACY_STATUS`。前端 `/admin` 首頁呼叫這個端點 |
| 「兩處廣播的 `findAll()`」 | `NotificationProducerService.broadcastToQueue`：主程式碼**零呼叫點**，測試也沒有引用（死碼）。`NotificationService.broadcastNotification`：只有 `POST /v2/notifications/broadcast` 一個入口，`@PreAuthorize("hasRole('SUPER_ADMIN')")`，DTO 註解寫明「`tenantId` 為 null＝全站廣播」 |
| 「`E-4001` 的 HTTP 狀態碼」（`API_M12` 寫 422，§7 還有一列說 `GlobalExceptionHandler` 把它歸為 422 群組） | **文件記錯**：`GlobalExceptionHandler.mapErrorCodeToStatus` 第 223～226 行把 `E-4001`～`E-4003` 歸在 `BAD_REQUEST`（400），只有 `E-8001` 是 422；我的 E2E 以 `statusCode(400)` 斷言上限／重疊衝突並通過 |
| 「既有測試是否把舊查詢當成預期」 | 是：`PricingServiceTest.StorelessCallerWriteTests` 兩處 stub 了 `findByRoomListingIdAndIsActiveTrue(null)`（也就是被當成設計的隱含 `IS NULL` 查詢）；`createRule_superAdmin_noListing_…` 在嚴格 stub 下因此變紅，已改成新的租戶範圍查詢 |

## 2. 決策與設計

### 2.1 `DEF-417` → 比較範圍改為規則掛載的目標

`PricingService.findActiveRulesInScope`（`createRule` 在擁有權檢查與設定驗證之後呼叫）：

| 請求 | 比較範圍 | 查詢 |
|---|---|---|
| 帶 `roomListingId` | 那間房源的啟用中規則 | `findByRoomListingIdAndIsActiveTrue`（與原本相同） |
| 沒帶 `roomListingId`、帶 `listingId`（商品規則） | 那個商品的啟用中規則 | `findByListingIdAndIsActiveTrue` |
| 兩者都沒帶 | 呼叫者自己租戶、同樣不綁的啟用中規則 | 新增 `findActiveUnboundRulesByTenantId`（JPQL，`roomListingId IS NULL AND listingId IS NULL AND isActive = true`，**`IS NULL` 明寫在查詢裡**） |

- **為什麼用 JPQL 而不是衍生查詢**：原缺陷正是衍生查詢對 null 參數悄悄變成 `IS NULL`；這次讓條件明寫出來，讀的人與測試都看得見。
- **兩個 id 同時帶入**：房源優先（維持原本「帶 `roomListingId` 就是那間房源」的行為）。
- **「不綁」的規則不拒絕建立**：DTO 明文不強制必填（程式註解與 `API_M12` §7 都記載），Playwright 的 `at-pricing-rules-real` 以 `SUPER_ADMIN` 建立這種平台自營規則並預期 201（Rule 3：不新增必填限制）。
- 50 條上限與「同類型重疊」沿用同一個方法，商品規則以「每個商品」為單位（PRD §5.5.1 只定義了房源，見 §9 第 1 點）。

### 2.2 `DEF-423` → 每個帶入的 id 都驗證擁有權

`createRule`：原本 `checkListingTenantOwnership(roomListingId ?: listingId, …)`，改成對 `roomListingId` 與 `listingId` 各呼叫一次。`checkListingTenantOwnership(null, …)` 只做「呼叫者有真正的店鋪」檢查就回傳，所以單一 id 與兩者皆 null 的行為完全不變；只有「兩個都帶」的情形多驗證一個。

沒有選「禁止兩個 id 並存」：那會改變今天可以成功的請求（兩個都是自己的），而缺陷的本質是「沒驗證」，不是「並存」。對照案例 `ruleMayReferenceBothOfMyOwnListings` 把這個選擇釘住（「改成並存就拒絕」的突變 M9 會被它殺掉）。

### 2.3 `DEF-422` (a) → `getPlatformStats` 改資料庫端計數

新增 `TenantRepository.countByStatus(TenantStatus)`，`activeTenants` 改用它。**先實測再改**：新增 `PlatformStatsCorruptRowFlywayIntegrationTest`（比照 Sprint 306 的 `PricingRulesCorruptRowFlywayIntegrationTest`：每次建獨立資料庫、Flyway 實跑 94 支遷移、`ddl-auto=validate`、自己以 SQL 種一列 `status = 'LEGACY_STATUS'`），對未修的程式跑，統計端點回 500；修復後 200，且 `totalTenants`、`activeTenants` 都等於直接從資料庫量到的筆數（期望值用 SQL 量、不寫死；並要求「活躍」嚴格少於「全部」，固件裡有被停權與狀態壞掉的店鋪）。

既有的 `AdminServiceTest.GetPlatformStats` 兩個案例**原本完全沒有斷言 `activeTenants`**（只 stub 了 `findAll()` 讓程式跑得過）；補上 `activeTenants == 7`（刻意與總數 10 不同）與 `verify(never()).findAll()`。

### 2.4 `DEF-422` (b) → 兩處廣播維持現狀，寫明理由

- `broadcastToQueue`：零呼叫點，沒有執行路徑，不動（Rule 3；是否清死碼是另一個決定）。
- `broadcastNotification`：`findAll()` 不是瓶頸。載入之後對**每位**使用者同步執行 `sendToQueue`（`findById`、組訊息、推進 Redis），整個廣播是請求執行緒內的 O(N) 同步迴圈。改成分頁只會移動一個小成本；要根治得改成非同步工作，那是產品／架構決策，不是缺陷修復。壞資料列風險：`users.role` 的壞值會讓廣播在送出任何訊息之前就失敗——是「大聲」的失敗，且由 `SUPER_ADMIN` 手動觸發。**我沒有量測使用者規模，也無法從程式碼得知有沒有人在用全站廣播。**

## 3. 刻意沒做的事

| 項目 | 為什麼沒做 |
|---|---|
| `DEF-419`（運費模板同型缺口） | 追蹤表標明屬權限模型決策、要使用者決定；本輪是無 `==>` 變體，不替使用者決定 |
| `adminAccessToken` 快取的直接測試（Sprint 306 限制第 4 點） | 看過程式：`API_BASE` 是模組載入時讀環境變數的常數、快取是模組層狀態，用本機假伺服器測它得靠動態 `import` 與清模組快取，結果取決於同一個 worker 先載入了哪個規格——脆弱度高於它保護的價值。真正的訊號在 `make validate-e2e` 結尾印出的登入請求數與限流次數（Sprint 306 已加）。這是有理由的不做，不是漏掉 |
| `TC-M15-018` 的 HTTP 層案例 | 仍受 `IntegrationTestConfiguration` mock 了 `MediaService` 限制（Sprint 306 §7 第 3 點），沒有新資訊 |
| 廣播改成非同步／分頁 | 見 §2.4 |
| 修改 PRD §5.5.1 | PRD 只定義房源；商品規則的範圍延伸寫在 `API_M12`（實作規格）。PRD 是需求文件，延伸需求的措辭交使用者決定；也避免觸發 PRD 變更的 schema 文件守門（本輪沒有遷移） |
| 修改 50 條上限的錯誤訊息 | 訊息是「每間房的定價規則上限為 50 條…」，用在商品時措辭略不貼切；訊息是使用者可見文字，不屬於缺陷修復，沒有動 |

## 4. 測試

### 4.1 新增與修改

| 檔案 | 內容 |
|---|---|
| `api/controller/PricingRuleScopeIsolationE2ETest`（新增，真實 PostgreSQL，13 個案例） | 兩間真實店鋪、每個案例用全新房源與商品、結束清理。**範圍**（`DEF-417`）：`differentStoresCanHoldOverlappingRulesOfTheSameType`、`otherStoreOverrideNeverDeactivatesMyRule`、`productsOfTheSameStoreHaveIndependentRules`、`overrideOnOneProductKeepsTheRulesOfAnotherProductOfTheSameStore`、`ruleLimitIsCountedPerProduct`、`unboundRulesAreComparedWithinMyStoreOnly`、`unboundRulesIgnoreRoomProductAndInactiveRules`；**對照**：`sameProductConflictIsStillDetectedAndOverrideStillWorks`（同商品重疊仍擋、覆蓋仍軟刪除重疊的舊規則並保留不重疊的、已停用的不再算重疊）、`roomRulesAreScopedToTheRoom`；**擁有權**（`DEF-423`）：`ruleCannotTargetAnotherStoresProductAlongsideMyOwnRoom`（用 `SoftAssertions` 一次量三件事：狀態碼、規則是否存在、有效售價）、`ruleMayReferenceBothOfMyOwnListings`、`ruleCannotTargetAnotherStoresProduct`、`ruleCannotTargetAnotherStoresRoom`（後兩者是 `DEF-242` 原路徑，先前沒有真實 DB 層測試） |
| `core/pricing/PricingServiceTest.RuleCreationScopeTests`（新增 9 個案例） | 以 `verify(never())` 擋住「商品規則又走無房源查詢」、斷言不綁規則的查詢參數是**呼叫者租戶**、兩個 id 各自的擁有權、以及「兩個都是自己的仍可建立」。因為 `PricingServiceTest` 是嚴格 stub，另開巢狀類別各案例自帶 stub |
| `core/pricing/PricingServiceTest.StorelessCallerWriteTests`（修改 2 處 stub） | `findByRoomListingIdAndIsActiveTrue(null)` → `findActiveUnboundRulesByTenantId(systemTenantId)`（見 §1.3 最後一列） |
| `integration/PlatformStatsCorruptRowFlywayIntegrationTest`（新增，Flyway 資料庫，2 個案例） | 第一個是「固件會咬人」的前置斷言（`tenantRepository.findAll()` 在這個資料庫必須丟含 `LEGACY_STATUS` 的例外）；第二個走真實 HTTP／JWT／`SUPER_ADMIN` 權限，期望值用 SQL 量 |
| `core/admin/AdminServiceTest.GetPlatformStats`（修改 2 個案例） | 補上 `activeTenants` 斷言與 `verify(never()).findAll()`；第二個案例保留「租戶側有一筆 PENDING_REVIEW 的誘餌」（改為 `lenient()`，因為 `findAll()` 不再被呼叫） |

### 4.2 紅燈證據（修復前、對未修的程式）

- `PricingRuleScopeIsolationE2ETest` 第一版（10 個案例）：**7 紅、3 綠（對照）**。失敗原因逐一讀過：六個案例是 `Expected status code <201> but was <400>`（或覆蓋後 `isActive` 為 false），`ruleCannotTargetAnotherStoresProductAlongsideMyOwnRoom` 三項斷言全紅（狀態碼 201、規則存在、有效售價 1.0 而非 100.00）。之後補的三個案例（`unboundRulesIgnoreRoomProductAndInactiveRules`、兩個 `DEF-242` 對照）沒有對未修程式單獨跑過，它們的鑑別力由 §5 的突變證明（M1 把原缺陷裝回去時 `unboundRulesIgnoreRoomProductAndInactiveRules` 轉紅）。
- `PlatformStatsCorruptRowFlywayIntegrationTest`：固件案例綠、統計案例 `Status expected:<200> but was:<500>`；後端日誌根因 `IllegalArgumentException: No enum constant …TenantStatus.LEGACY_STATUS`。

## 5. 突變驗證

驅動見記憶 `mutation-validation-driver-pattern`（備份 → 套用 → 跑單元＋整合 → 由 XML 取失敗集合 → 還原 → 雜湊比對）。每個突變事先登記「預期紅燈的新案例集合」，與實際失敗集合比對；`MISSING`＝預期會紅卻綠（漏殺）。

**結果：21 個突變全數擊殺，`MISSING` 0 個。19 個的失敗集合與預測完全相同；2 個（M7、M8）是預測的超集。**

| 組 | 突變 | 擊殺的新案例數 |
|---|---|---|
| M1 | 把原缺陷裝回去（一律 `findByRoomListingIdAndIsActiveTrue(roomListingId)`） | 11（E2E 7、單元 4） |
| M2 | 拿掉商品分支（商品規則掉到「不綁」） | 5 |
| M3 | 商品範圍變成整個租戶（「只依租戶修」的半套修法） | 7 |
| M4 | 「不綁」範圍變成全平台 `IS NULL` | 3 |
| M5 | 拿掉房源分支 | 3 |
| M6 | 商品分支排在房源分支前面 | 1（只有單元層的嚴格 stub 能分辨） |
| M7 | 拿掉 `listingId` 的擁有權檢查 | 8（預測 4，超出的 4 個見下） |
| M8 | 拿掉 `roomListingId` 的擁有權檢查 | 5（預測 2，超出的 3 個見下） |
| M9 | 過度擋：兩個 id 並存就拒絕 | 2 |
| M10 | 「不綁」查詢帶 null 租戶 | 2 |
| M11 | 上限差一（`>=` → `>`） | 2 |
| M12 | 商品範圍含已停用的規則 | 4 |
| R1～R4 | JPQL 各拿掉一個條件（租戶、`listingId IS NULL`、`roomListingId IS NULL`、`isActive`） | 各 1 |
| A1 | `activeTenants` 退回 `findAll()` 過濾 | 3（含 Flyway 型的 500） |
| A2 | 數錯狀態（`PENDING_REVIEW`） | 3 |
| A3 | `activeTenants = totalTenants` | 3 |
| F1 | 固件：壞資料列換成合法值 | 1（只有前置斷言紅） |
| F2 | 固件失效**且**退回 `findAll()` | 3（前置斷言與單元紅，**統計的 Flyway 案例仍綠**——正是 `DEF-421` 的失效模式，被前置斷言抓到） |

- **M7／M8 的超集**：我的預測漏算了 Mockito 嚴格模式——拿掉所有權檢查後，原本會被呼叫的 `listingRepository.findById` stub 變成「未使用」，其他單元案例連帶變紅。用該次 Maven 日誌核對：多出來的案例都是 `ERROR`（`UnnecessaryStubbingException`；M7 日誌 12 次、M8 24 次），預測的案例是 `FAILURE`（斷言失敗）。連帶紅燈的機制是我從類型與日誌次數確認的，沒有逐一讀每個案例的訊息。
- **既有案例連帶變紅**：M1、M4、M5、M8、M10、M11 也讓既有案例（`StorelessCallerWriteTests`、`RuleCreationLimitAndConflictTests`、`RuleTenantOwnershipTests`）變紅——方向符合預期（這些既有案例本來就在守同一段邏輯），不影響「新案例各自被擊殺」的判讀。
- 還原：兩批驅動結束時，八個變更過的原始碼檔（四個生產、四個測試）SHA-256 與突變前記錄逐一相同，`grep` 突變標記（`OR 1 = 1`、`"mutated"`、`findAll().stream().filter(t -> …)`）無殘留。

## 6. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（開跑前重建全新測試 DB） | `BUILD SUCCESS`，28 分 16 秒（Sprint 306 是 19 分 28 秒；我沒有查慢的原因） |
| 單元測試（surefire） | **2370** 通過／0 失敗／0 錯誤／0 跳過（Sprint 306 的 2361 ＋ 本輪新增 9） |
| 整合／E2E（failsafe） | **948** 通過／0／0／0（Sprint 306 的 933 ＋ 13 ＋ 2）；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個 |
| Checkstyle | main 與 test 各 `You have 0 Checkstyle violations.` |
| 本輪相關類別在全量中的結果 | `PricingRuleScopeIsolationE2ETest` 13／13、`PlatformStatsCorruptRowFlywayIntegrationTest` 2／2、`PricingServiceTest$RuleCreationScopeTests` 9／9、`AdminServiceTest$GetPlatformStats` 2／2、`AdminControllerE2ETest` 24／24、`PlatformSelfRunWriteIsolationE2ETest` 18／18、`PricingRulesCorruptRowFlywayIntegrationTest` 4／4 |
| 標準 `make validate-e2e`（完整建置、預設並行度＝2 個 worker） | 282 案例：**278 通過／4 跳過／0 失敗**，7.6 分鐘；日誌「認證端點被限流 0 次」；schema 對齊（backend 啟動成功、entity 與 Flyway 對齊、無漂移）；「遷移後沒有測試資料，`admin@nextkey.local` 無法以已知密碼登入」✅。跑到本輪相關的真實全棧規格：`at-pricing-rules-real`（`beforeAll` 以 SUPER_ADMIN 建立不綁房源的平台自營規則並預期 201，E2E-PRICING-01～03）、`at-admin-overview-real` |
| 4 個跳過 | 與 Sprint 306 同為 4 個；規格裡有條件式 `test.skip()`（`at-m17-003`、`at-m17-004`）。我**沒有**逐一核對這輪跳過的就是它們 |
| 突變驗證 | 21 個全數擊殺（§5） |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 全量 verify 中全過；但它們跑在單元階段、早於我寫計畫書與追蹤表，所以文件定稿後（含本計畫書、追蹤表、`API_M12`／`API_M14` 的最終內容）另行補跑一次：4 個類別共 14 個案例全過 |

## 7. 過程中的失誤與修正

1. **API 文件我自己先沿用了錯的 422**：在 `API_M12` §4.1 新增兩列錯誤表時，照抄了文件原有的 `E-4001 | 422`；之後自己的 E2E 以 `statusCode(400)` 通過、再讀 `GlobalExceptionHandler` 的對應表才知道是 400。已更正全文各端點錯誤表、§6 與 §7（`E-4002`／`E-4003` 只讀了對應表，沒有逐端點實測 HTTP）。
2. **第一次編譯被 IDE 的 Java 語言服務搶寫 `target/classes`**：大量無關類別出現 `cannot access …`（記憶 `ide-ecj-stale-class-phantom-failures` 的症狀）。重跑通過；並用 `javap` 確認新方法在 class 內、class 比原始碼新、`grep -a` 沒有 `Unresolved compilation`。
3. **zsh 兩個老坑**：`grep --include=*.java` 未加引號報 `no matches found`；`rm -f target/…/TEST-*.xml` 在沒有符合檔案時中止整串 `&&`，Maven 沒有被執行（輸出檔不存在、我看到才發現）。改用引號與 `find -delete`。
4. **macOS 的 `strings` 把 Java class 的魔數 `CAFEBABE` 當成 Mach-O fat binary**，印出 `fat file … truncated or malformed`；改用 `grep -a`。
5. **突變預測漏算嚴格 stub 的連帶紅燈**（M7、M8，見 §5）：沒有漏殺，但預測不完整；驅動標成 `MISMATCH`，我用日誌核對後才下結論。
6. **既有測試把缺陷當設計**（`StorelessCallerWriteTests` 兩處 stub 了隱含 `IS NULL` 的查詢）：第一次單元測試跑出 1 個 `UnnecessaryStubbingException` 才發現，不是事前掃到的。

## 8. 限制（誠實揭露）與同型缺口

1. **`DEF-423` 的影響只實測到 `effective-price` 端點**：購物車套用該價格、結帳用購物車小計，是讀碼推論；購物車的動態價格開關看的是**呼叫者所在租戶**的 `DYNAMIC_PRICING_ENABLED`（`FeatureToggleService.isFeatureEnabled`），沒有店鋪的買家用的是系統租戶的開關——這個開關語意是否符合預期我沒有追，也沒有跑到結帳為止。
2. `users.role` 沒有實測寫入列舉外的值（只讀了 V1 與 `grep` 後續遷移）。
3. `DEF-422` (b)：沒有量測使用者規模，也無從得知全站廣播有沒有人在用；分析的結論建立在程式碼結構上。
4. `PricingRuleScopeIsolationE2ETest` 與其他整合測試類別共用一個測試資料庫；規則期間從「今天＋4 年」起，避免與其他類別留下的規則重疊，但沒有強制隔離。
5. 新的 Flyway 資料庫 `s307_platform_stats_corrupt_row` 執行完留在測試用 PostgreSQL（Spring context 快取仍持有連線）；容器停止時一併消失。
6. 雲端 e2e job（只能手動觸發）仍沒有實跑。
7. `DEF-419` 未動；它列的「沒有審查的同型比對」（`CmsService`、`ReviewReplyService`、`ReviewService`、`BookingReviewService`、`NotificationTemplateService`）本輪也沒有審查。
8. 商品規則的 50 條上限錯誤訊息仍寫「每間房」。
9. 50 條上限的 `MAX_ACTIVE_RULES_PER_ROOM` 常數名稱仍帶 `ROOM`（只更新了文件註解），沒有重新命名。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | 商品規則沿用「50 條上限＋同類型重疊檢查」，以**每個商品**為單位 | PRD §5.5.1 只定義房源；既有程式本來就對商品規則套用同一段邏輯（只是範圍錯了）。最小的改動是把範圍修對，而不是改變規則 | 告訴我商品規則不設上限／不檢查重疊（等於移除商品分支的檢核）或改成別的單位 |
| 2 | 兩者都沒帶的規則仍可建立，只在呼叫者自己租戶內比較 | DTO 明文不強制必填；Playwright 的平台自營規則依賴它（Rule 3） | 告訴我改成必填其一（會讓該 E2E 步驟需要改） |
| 3 | `DEF-423` 修成「每個帶入的 id 都驗證」，不禁止並存 | 缺陷是「沒驗證」；禁止並存會改變今天可以成功的請求 | 告訴我改成並存就 400／403 |
| 4 | 兩個 id 並存時，比較範圍以房源為準 | 維持原本「帶 `roomListingId` 就是那間房源」 | 告訴我改成商品優先或兩個範圍都比 |
| 5 | 順手更正 `API_M12` 各處把 `E-4001`～`E-4003` 記成 422 的錯誤 | 我自己編輯的列帶著已知的錯；程式的對應表與我的實測都是 400 | 從 git 歷史還原該文件的那幾列 |
| 6 | `DEF-422` (b) 不修 | §2.4 | 告訴我要做分頁或非同步 |
| 7 | 不改 PRD、不改 50 條上限的錯誤訊息 | §3 | 告訴我要改的措辭 |
| 8 | 不做 `adminAccessToken` 快取的直接測試 | §3 | 告訴我要做，我用動態 `import` 加清快取的做法 |
| 9 | `DEF-423` 標 🔴（追蹤表嚴重度） | 跨租戶改寫售價，與 `DEF-242` 同級 | 改標記 |
| 10 | 為了跑整合測試，我啟動了本機的 Docker Desktop（開工時 daemon 沒開）；結束時清理 Docker 殘留的卷 | 專案每輪都要 Docker；清理是使用者 2026-10-20 的長期規則 | 告訴我之後不要自己開 |

## 10. 雲端 CI 與狀態

功能 commit `79302fcf` 已 push（`01edcea1..79302fcf  main -> main`；同一次 push 也帶出 Sprint 306 的純文件回填 commit `f3f5b7d2`）；`git fetch` 後本機 `HEAD` 與 `origin/main` 同為 `79302fcf008caaa5ca1d5de74badd04b521345b2`，ahead／behind 為 0／0。開工時 Docker daemon 沒有開，我啟動了 Docker Desktop 才能跑整合測試（§9 第 10 點）。

- **pre-commit**：一次通過——Checkstyle、Maven compile、核心層快速測試（commit 前以 `docker ps | grep -c nk-test` 確認測試 DB 容器在；沒有繞過 hook）。本輪沒有動遷移或 SRD／PRD，所以 schema 文件漂移檢查沒有被觸發。
- **pre-push 輕量守門**通過：本機 act 的 Backend Unit Tests `Job succeeded`（單元測試步驟 3 分 4 秒）、Frontend Lint & Build `Job succeeded`；schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」；push 之後 `docker ps -a` 為空（pre-push 清掉了 `nk-test-*`）。
- **雲端 CI** run [40517176358](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40517176358)：`completed／success`，三個 job 皆 success，約 12 分 34 秒（14:01:01Z～14:13:35Z）；`gh run view --json` 的 `headSha` 為 `79302fcf008caaa5ca1d5de74badd04b521345b2`。
  - 下載了「Backend Integration Tests & Package」job 的**完整**雲端日誌（54,445,100 位元組、208,740 行；下載結束 exit 0、stderr 為空、檔尾是 job 收尾）：去除 ANSI 色碼後 failsafe 彙總 **`Tests run: 948, Failures: 0, Errors: 0, Skipped: 0`**（與本機整合測試 948 一致），`BUILD SUCCESS`，`<<< FAILURE`／`<<< ERROR` 標記 0 個；`PricingRuleScopeIsolationE2ETest` 13／13、`PlatformStatsCorruptRowFlywayIntegrationTest` 2／2（GitHub service 的全新 Postgres 上由 Flyway 實跑）、`AdminControllerE2ETest` 24／24、`PricingRulesCorruptRowFlywayIntegrationTest` 4／4。
  - 這次也下載了「Backend Unit Tests」job 的日誌（3,160,183 位元組；Sprint 306 當時沒下載）：它依序執行 `mvn checkstyle:check`（`You have 0 Checkstyle violations.`）→ 重設資料庫 → `mvn clean compile -DskipTests -q` → `mvn test -Dspring.profiles.active=test -q`。**`-q` 安靜模式不印測試數**，所以雲端只能確認每個步驟成功（job 結論 success），單元測試 2370 以本機全量為準；日誌裡有測試產生的例外堆疊，job 結論不是失敗。
  - 雲端三個 job 不含 Playwright E2E，E2E 以本機 `make validate-e2e` 為準（§6）；雲端 e2e job（只能手動觸發）沒有實跑。
- **Docker 清理**（所有 commit、push、本機守門都結束之後）：容器 0；8 個匿名卷逐一刪除（0 失敗），卷 1.277GB → 928MB；2 個懸空映像（回收 45.72MB）；5 個具名卷（`act-toolcache`、`e-commerce_minimax_maven_cache`、`_minio_data`、`_postgres_data`、`_redis_data`）全部保留；映像只留 3 個核准的基底映像（`postgres:18-alpine`、`redis:7-alpine`、`catthehacker/ubuntu:act-latest`）。**建置快取有 25 筆、1.889GB**（Sprint 306 結束時是 17 筆、1.68GB；我沒有查增加的原因），不在「卷與映像」的清理範圍內，沒有刪；要清可執行 `docker builder prune -af`。Docker Desktop 本身是我啟動的，結束時沒有關閉。

## 11. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/java/.../core/pricing/PricingService.java` | `createRule` 兩個 id 各驗證擁有權；`enforceRuleLimitAndConflict` 改用新增的 `findActiveRulesInScope`；常數註解 |
| `backend/src/main/java/.../domain/repository/PricingRuleRepository.java` | 新增 `findActiveUnboundRulesByTenantId`（JPQL） |
| `backend/src/main/java/.../core/admin/AdminService.java` | `getPlatformStats` 的 `activeTenants` 改用 `countByStatus` |
| `backend/src/main/java/.../domain/repository/TenantRepository.java` | 新增 `countByStatus` |
| `backend/src/test/java/.../api/controller/PricingRuleScopeIsolationE2ETest.java` | 新增（13 個案例） |
| `backend/src/test/java/.../integration/PlatformStatsCorruptRowFlywayIntegrationTest.java` | 新增（2 個案例） |
| `backend/src/test/java/.../core/pricing/PricingServiceTest.java` | 新增 `RuleCreationScopeTests`（9 個案例）；`StorelessCallerWriteTests` 改 2 處 stub |
| `backend/src/test/java/.../core/admin/AdminServiceTest.java` | `GetPlatformStats` 兩個案例補斷言與 stub |
| `docs/02_architecture/api/API_M12_Dynamic_Pricing.md` | v2.5：§1.2、§4.1、§6、§7（兩列新增、狀態碼更正）、契約測試連結 |
| `docs/02_architecture/api/API_M14_Admin_Console.md` | v1.1：§3.2、§7（新增一列）、契約測試連結 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-417` 結案、`DEF-422` 部分處理、新增 `DEF-423`；頁尾 v3.80 |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 307 row、統計區、版本歷史 |
| `docs/04_planning/SPRINT_307_PLAN.md` | 新增（本文件） |

## 12. 後續

- **使用者要做的一件事（Sprint 306 留下，本輪沒碰）**：拉到 Sprint 306 之後執行一次 `make down-clean`，再 `make up`、`make dev-admin-password`。
- `DEF-419`（運費模板同型缺口）：等使用者決定權限模型（比照 `DEF-415` 全部擋下，或另有想法）。
- `DEF-422` (b)：只在確認有人使用全站廣播、且使用者規模大到讓同步迴圈成為問題時才值得做，起點是那個迴圈。
- 商品規則的範圍延伸是否要寫進 PRD §5.5.1：交使用者決定措辭。
- 建置快取（Sprint 306 留下的 1.68GB）：不在「卷與映像」的清理範圍，由使用者決定要不要 `docker builder prune -af`。
