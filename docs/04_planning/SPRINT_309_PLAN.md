# Sprint 309 Plan — DEF-424：規則只在房源／商品所屬店鋪的動態定價開關開啟時套用（使用者以互動選項裁決「關閉後立即失效」）

## 1. 起點與範圍

### 1.1 起點

[Sprint 308](SPRINT_308_PLAN.md) 收尾，我對 `DEF-424`（店鋪層的 `DYNAMIC_PRICING_ENABLED` 只管建立規則、不管套用）用 `AskUserQuestion` 問了一題，列三個選項：(A) 維持現狀並明文化、(B) 套用端改看商品所屬店鋪的開關（建議）、(C) 撤銷時順便停用既有規則。工具回傳：**使用者選「關閉後立即失效（建議）」＝ (B)**。這是使用者真實的互動回答，不是我的推論；本輪依 (B) 實作。

範圍：只做 (B) 與它的連帶必要修改。不碰 `DEF-419`（權限模型）、`DEF-425`（四個永遠跳過的 E2E）；`DEF-426`（日曆手動改價）是本輪發現的連帶決策，只登記。

### 1.2 成功標準（開工時定義）

1. 規則只在**房源／商品所屬店鋪**的開關開啟時套用；購物車、訂房（報價、建單、整月日曆）、價格試算端點**一致**。
2. 結論與**呼叫者租戶**的開關無關；管理員關閉後立即失效、重新開啟立即恢復；各店鋪互不影響。
3. 測試覆蓋三層：單元（引擎關卡）、真實 PostgreSQL（HTTP＋JWT）、Flyway 資料庫＋真實 Redis（購物車與訂單金額）；每個關卡條件都經突變驗證。
4. 全量後端 `mvn -o clean verify`（全新測試 DB）與完整 E2E 套件通過；文件與追蹤表同步。
5. 這個改動會改變消費者實際被收的價格，連帶範圍要在計畫書如實揭露。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「(B) 要改購物車 1 處＋訂房 2 處」（我寫在選項說明裡） | **不完整**。盤點定價引擎兩個讀取方法的呼叫點：`BookingService` 2 處（`dailyDiscountMap`、`tryDynamicPricing`）、`RedisCartService` 1 處、`ListingController` 2 處（`GET /listings/{id}/price`、`GET /listings/{id}/effective-price`）、`PricingController` 1 處（`POST /calculate`）。前端真的在用 `/listings/{id}/price`（`services/listing.ts` 第 109～117 行，房間詳情頁）與儀表板預覽（`PricingCalendarPreview.tsx` 第 97 行）；`effective-price` 前端不用（`lib/api.ts` 第 32 行註解）。只改前面 3 處，被撤銷店鋪的詳情頁會顯示一個不會被收的折扣價——我自己製造出新的「顯示價 ≠ 收款價」 |
| 「呼叫端的 `FeatureToggleService` 還有別的用途嗎」 | `grep`：`RedisCartService` 只有 `tryProductAdjustment` 一處、`BookingService` 只有兩處，全是這個檢查。搬進引擎後兩個欄位都是孤兒，一併移除（`RedisCartServiceTest` 手動 `new` 建構子要跟著改；`BookingService` 沒有任何測試手動 `new`，都是 `@InjectMocks`） |
| 「日曆手動改價（`calendar/price`、`override`）會被 (B) 影響嗎」 | 會：它們建出的是 `MANUAL_OVERRIDE` 規則，也是規則。**前端沒有任何入口**（`PricingService.setCalendarPrice` 只有服務層定義，沒有元件呼叫，`grep` 確認），影響只在直接打 API 的人。登記 `DEF-426` |
| 「Java E2E 能不能假設系統租戶的開關是開啟」 | **不能**：Flyway 種子（V1 第 521 行、V6 第 58 行）是 TRUE，但 **Hibernate 測試庫是 FALSE**（`TestDatabaseInitializer` 第 47 行；`docker exec … psql` 直接查 `tenant_feature_toggles` 確認）。我的第一版 E2E 開頭有一條「系統租戶開關應為開啟」的前提斷言，被這個事實擋下，才發現假設錯了（§6 第 2 點） |
| 「哪些測試受影響」 | 動手前 `grep`：受影響的單元測試 7 個訂房類別＋2 個購物車類別＋`PricingServiceTest`；**`M12EffectivePriceIntegrationTest` 動手前沒找到**，是全量 verify 才抓到（§6 第 5 點） |

## 2. 設計與做法

### 2.1 單一關卡：定價引擎

`PricingService.isDynamicPricingEnabledFor(listing)` → `featureToggleService.isFeatureEnabledForTenant(listing.getTenantId(), "DYNAMIC_PRICING_ENABLED")`：

- `calculatePrice`：關閉時規則清單直接是空的（連規則都不查），逐日價格就是基準價；回應形狀與「沒有規則生效」完全相同，呼叫端不必改處理邏輯。
- `getEffectivePrice`：關閉時 `activeRules` 是空清單，走原本的「沒有規則」分支。
- 購物車 `RedisCartService.tryProductAdjustment` 與訂房 `BookingService.dailyDiscountMap`、`tryDynamicPricing`：**移除**自己查呼叫者租戶開關的檢查，一律向引擎查詢。
- `createRule` 的開關檢查（呼叫者租戶；店主＝自己的店鋪）**不動**：建立規則仍看呼叫者。

為什麼放引擎而不是三個呼叫端：引擎的兩個讀取方法是所有價格路徑的共同入口，一處把關不會有人漏改，也不會有兩層重複的判斷互相矛盾。

### 2.2 連帶影響（已登記或已揭露）

1. **日曆手動改價也隨店鋪開關**：關閉時 `calendar/price` 仍回 `200` 卻不生效（`DEF-426`，待裁決；前端沒有入口）。
2. **平台管理員替店鋪商品建的規則**（規則的租戶是系統租戶）同樣受該店鋪的開關約束。
3. **系統租戶的開關不再是全平台的「套用」總開關**，只決定平台自營的房源／商品。
4. 店主自己的價格預覽（`POST /calculate`）與消費者看到的一致——開關關閉時預覽就是基準價。

## 3. 測試

| 檔案 | 內容 |
|---|---|
| `core/pricing/PricingServiceTest.StoreToggleGatesApplicationTests`（新增 8 案例） | 房間與商品各 4：開關關閉不套用且不查規則、開啟照常套用、呼叫者開啟＋店鋪關閉、呼叫者關閉＋店鋪開啟。**店鋪 id 刻意與呼叫者租戶不同**；呼叫者租戶的 stub 是 `lenient` 誘餌，並以 `verify(never())` 證明引擎從未讀它；交叉案例另有誘餌規則（見 §4 的 Q3 發現）。外層 `@BeforeEach` 加了「預設每個租戶都開啟」的寬鬆 stub，既有 110 個規則數學案例不必改 |
| `api/controller/StoreToggleGatesApplicationE2ETest`（新增，真實 PostgreSQL，5 案例） | 兩間店鋪（開／關）各有房源、商品與一條生效中的規則。三條消費者價格路徑（商品 `effective-price`、房間 `listings/{id}/price`、訂房 `bookings/availability`）各對**三種呼叫者**比價：呼叫者租戶開關開啟、關閉（兩個我控制開關的租戶），以及系統租戶（真實消費者；其開關值依資料庫而異，本類別不依賴）；店主預覽；切換立即生效且可逆（沒有殘留快取） |
| `frontend/e2e/at-pricing-cart-checkout-real.spec.ts` E2E-PCART-03／04 | Sprint 308 的 03 原本是「現況記錄」（關閉後規則仍套用），**翻轉**成：關閉後新規則被擋 `E-2004`、購物車與**訂單**回原價、開關仍開的店鋪 A 的規則照常套用（900）；新增 04：重新開啟後同一條規則立刻恢復（購物車與訂單 800）。跑在 Flyway 資料庫＋真實 Redis，系統租戶開關為開啟——最接近正式環境 |
| `integration/M12EffectivePriceIntegrationTest`（修改） | 固件對開關「失明」：`FeatureToggleService` 是 `@MockBean`（預設回 `false`），舊的 `effective-price` 端點不查開關所以一直是綠的。`@BeforeEach` 明說前提「這家店鋪的動態定價是開啟的」，並新增 `IT-EP-005`（開關關閉、有誘餌規則，仍回基準價，且從未查規則） |
| 訂房單元測試（7 個類別）＋購物車單元測試（2 個類別）（修改） | 原本用「開關＝關閉」讓計價路徑繞過 `pricingService` mock；現在一律向引擎查詢，沒 stub 的 mock 回 `null`。新增共用固件 `BookingPricingFixtures.noAdjustment()`（引擎的「沒有規則生效」回應，與開關關閉時的實際結果相同）。`BookingServiceDynamicPricingTest` 的 DP-002、`RedisCartServiceDynamicPricingTest` 的 DP-002 原本斷言「開關關閉時不呼叫 PricingService」，改寫成「引擎回基準價 → 維持原價，且一律向引擎查詢（這間房源、這段日期）」；移除孤兒 `FeatureToggleService` mock，`RedisCartServiceTest` 建構子參數跟著改 |

### 3.1 紅燈證據

`StoreToggleGatesApplicationTests` 對**未修的引擎**（關卡還沒加）跑：8 個案例 **2 失敗＋6 錯誤，全數為紅**（2 個失敗是 `room_storeToggleOff_rulesDoNotApply`、`product_storeToggleOff_rulesDoNotApply` 這兩個「關閉」案例；其餘六個錯誤是嚴格 stub 的 `UnnecessaryStubbingException`——引擎沒有讀店鋪的開關，stub 沒被用到）。

## 4. 突變驗證

驅動 `mutate309.py`：備份 → 套用 → `mvn -o test`（巢狀單元類別）→ `failsafe:integration-test`（真實 PostgreSQL E2E）→ 還原 → SHA-256 比對。每個替換字串事先檢查剛好出現一次。

| 突變 | 單元（8） | 真實 PostgreSQL E2E（5） |
|---|---|---|
| Q1 關卡拿掉（一律視為開啟） | 8 紅（2 失敗＋6 錯誤） | 5 紅 |
| Q2 改看呼叫者租戶（DEF-424 原行為） | 8 紅（6＋2） | 4 紅——`ownerPreview` 維持綠：店主的租戶就是那家店鋪，與預測一致 |
| Q3 條件反向 | 8 紅（第二次跑全為斷言失敗） | 5 紅 |
| Q4 商品有效售價不把關 | 4 紅（只有 `product_*`） | 2 紅（商品 `effective-price`、切換案例） |
| Q5 房間計價不把關 | 4 紅（只有 `room_*`） | 4 紅（房間試算、報價、店主預覽、切換案例） |

**Q3 第一次跑揭露一個測試弱點**：單元 8 個只有 6 個紅——`room_`／`product_callerToggleOnButStoreToggleOff` 兩個交叉案例沒有紅。原因：它們沒有 stub 規則的 repository，關卡錯誤放行時 repository 回空清單，結果仍是基準價，「規則沒被套用」的斷言是空洞的（整體仍被別的案例擊殺，但這兩個案例自己沒有鑑別力）。加上誘餌規則後重跑 Q3：8 個全紅。

**JAR 層突變 M6**（把引擎改回看呼叫者租戶，單規格真實全棧，Flyway 資料庫＋真實 Redis）：E2E-PCART-01、02 通過，**03 在預期的斷言失敗**（預期 1000、實際 800），04 因 serial 模式未執行。

所有突變還原後驅動比對 SHA-256 皆一致，並以 `git status` 獨立核對。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（第一次，全新測試 DB） | 單元 **2378／0 失敗**（Sprint 307 的 2370＋本輪 8）；Checkstyle main／test 皆 0 違規；整合 **953 個、2 失敗**（`M12EffectivePriceIntegrationTest` 兩個案例：預期 450／650、實際 500）。18 分 31 秒 |
| 全量後端 `mvn -o clean verify`（修正固件後的最終一次，全新測試 DB） | `BUILD SUCCESS`，**17 分 54 秒**；單元 **2378／0 失敗**；整合 **954／0 失敗**（Sprint 307 的 948＋`StoreToggleGatesApplicationE2ETest` 5＋`IT-EP-005` 1）；Checkstyle main／test 皆 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。本輪相關類別：`StoreToggleGatesApplicationTests` 8／8、`StoreToggleGatesApplicationE2ETest` 5／5、`M12EffectivePriceIntegrationTest` 5／5、`PricingRuleScopeIsolationE2ETest` 13／13、`PlatformStatsCorruptRowFlywayIntegrationTest` 2／2。順帶：Sprint 307 的 28 分 16 秒與本輪兩次（18 分 31 秒、17 分 54 秒，新增的測試還比 Sprint 307 多）相比是離群值，支持『當時環境較慢』而不是『新測試造成』，但我沒有查明 Sprint 307 當時為何慢 |
| 完整 E2E 套件（Flyway 資料庫＋真實 Redis，完整建置：`mvn package`＋`npm ci`＋`next build`、2 個 worker；`validate-e2e.sh` 的副本、list 報表，見 Sprint 308 計畫書 §6） | **286 案例：282 通過／4 跳過／0 失敗**，6.9 分鐘；schema 對齊（backend 以 `ddl-auto=validate` 啟動成功）；認證端點被限流 0 次；新規格四個案例在並行下 2.0／1.9／2.4／1.3 秒通過。**4 個跳過仍是 `DEF-425` 的 `at-m17-003`（2）與 `at-m17-004`（2），所以這不是乾淨的全綠** |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 它們屬單元階段，早於我寫文件；所以在**所有文件定稿後**（含本計畫書、追蹤表、`RELEASE_TRACKER`、四份 API 文件）另行補跑：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS` |
| ESLint／`tsc --noEmit`（`at-pricing-cart-checkout-real.spec.ts`） | 退出碼皆 0 |

## 6. 過程中的失誤與修正

1. **我寫在互動選項裡的 (B) 範圍不完整**：選項說明只寫「購物車與訂房共 3 處程式」，動手前盤點才發現價格預覽端點也要一併處理，否則會製造「顯示價 ≠ 收款價」（§1.3 第一列）。使用者是依我的說明做的選擇，所以這裡明講：實際範圍比說明寬，也因此選擇把關卡放在引擎一處。
2. **測試前提的假設錯了**：Java E2E 第一版開頭斷言「系統租戶的開關為開啟」，實際上 Hibernate 測試庫是關閉（Flyway 種子才是開啟）。被前提斷言擋下才發現；改成不依賴它，用三種呼叫者比價。這又是記憶 `flyway-db-vs-hibernate-test-db` 的老問題。
3. **`failsafe:integration-test` 單獨執行時，測試失敗不影響 Maven 退出碼**（`verify` 目標才會）：第一次跑新 E2E 得到「mvn exit=0」但其實有失敗。之後一律看彙總行，不看退出碼。
4. **`-Dtest='Booking*Test'` 的萬用字元把需要資料庫的整合測試也抓進單元階段**，出現 76 個連線錯誤（環境雜訊）。改用完整套件路徑 `com.nextkey.ecommerce.core.booking.*Test`。
5. **我的受影響測試子集漏了一支**：先前用 `*Pricing*`、`*Booking*`、`*Cart*` 比對整合測試（585 個全綠），漏掉檔名是 `Price` 而不是 `Pricing` 的 `M12EffectivePriceIntegrationTest`——全量 verify 抓到它的 2 個失敗。原因是固件對開關失明（`@MockBean` 預設 `false`，舊端點不查開關，所以開關狀態一直無關緊要），這正是「測試以固件繞過同一段邏輯」。**用檔名比對挑子集本來就不可靠，全量 verify 是不能省的最後一道。**
6. **Q3 揭露兩個交叉案例沒有誘餌**（§4）。
7. **API_M04 修訂紀錄把 v2.4 插在 v2.3 前面**（表是由舊到新），寫完才看到，已對調。

## 7. 限制（誠實揭露）

1. **訂房建單的 `totalAmount` 沒有走真實 HTTP 測試**：Java E2E 驗證的是可用性報價（`bookings/availability`）；建單與報價共用 `calculateTotalAmount → tryDynamicPricing → 引擎`，建單由單元層（mock 引擎）覆蓋。整月日曆 `GET /v2/bookings/calendar` 的每日價同理（`dailyDiscountMap → 引擎`），只有單元層。引擎關卡本身是真實 PostgreSQL 驗證過的。
2. 購物車與**訂單**金額在 Flyway 資料庫＋真實 Redis 驗證（Playwright）；**付款金額**取自訂單總額仍是讀碼推論，沒有跑到付款。
3. Java E2E 刻意不依賴系統租戶的開關；「系統租戶開關為開啟的真實消費者」只被 Playwright（Flyway）覆蓋。
4. PRD 沒有改：PRD 權限矩陣寫「Service 層在建規則前查詢」，與新行為不衝突，只是沒涵蓋「套用」；要不要補寫由你決定。
5. `DEF-426`（日曆手動改價）、`DEF-425`、`DEF-419` 未動。
6. 雲端 e2e job 仍未實跑。
7. **雲端這次綠燈沒有觸發競態**：修復後的雲端 run 裡 IT-M06-103 走 `200` 分支，所以雲端綠燈只證明沒有回歸；修復的證據是本機注入延遲的前後對照（§9.2）。之後若同一個偶發失敗再出現，先看那一次 IT-M06-103 走哪個分支。
8. **IT-M06-103 自己的斷言偏弱**（`409` 與 `200` 都算過、其他狀態碼只印警告不失敗）：本輪只修清理競態、沒有改測試意圖，登記為 `DEF-427`。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | 關卡放在定價引擎，同時涵蓋價格預覽端點（比選項說明的「3 處」寬） | 只改 3 處會讓被撤銷店鋪的詳情頁顯示不會被收的折扣價（§1.3、§6 第 1 點） | 告訴我只改購物車與訂房（不建議） |
| 2 | 日曆手動改價隨店鋪開關、建立端點不擋（`DEF-426`） | 擋建立是新的產品決策（預設關閉的新店鋪從此不能用 API 改單日價），所以只登記 | 告訴我選 (a) 擋 `E-2004` |
| 3 | 不改 PRD | 與 PRD 文字不衝突；PRD 是需求文件，措辭交你決定 | 告訴我要寫的措辭 |
| 4 | 移除 `RedisCartService`、`BookingService` 的 `FeatureToggleService` 孤兒欄位 | 搬進引擎後沒有用途（Rule 3：清理自己造成的孤兒） | — |
| 5 | 單元測試用 `BookingPricingFixtures.noAdjustment()` 當引擎預設回應 | 正式環境的引擎永遠不回 `null`，固件讓 mock 貼近真實 | — |
| 6 | 依「每個 Sprint 收尾 push 一次」push（連同 Sprint 307 的回填 commit 與 Sprint 308 的 commit 一起帶出） | 這是定價行為的功能變更；Sprint 308 因為只有測試與文件所以留在本機，這次有功能變更 | 告訴我之後這類 commit 也先等你說 |
| 7 | 雲端紅燈的修復 commit（`035a14e6`，只改測試檔）直接 push，沒有另外問你 | 這是同一個 Sprint 收尾「push→等雲端 CI」流程的修復步驟：main 上雲端 CI 是紅的；只動測試檔；pre-push 本機守門通過 | 告訴我之後 CI 修復也先等你說 |
| 8 | 不改 IT-M06-103 的斷言，只修清理競態；弱點登記 `DEF-427` | Rule 3：只碰必須修改的；改測試意圖是另一件事 | 告訴我現在就改 |
| 9 | 在 `SPRINT_151_PLAN.md` 加一行更正註記（原文不動） | 那裡把這個失敗記成『狀態殘留型 flaky、僅需重跑』，會誘導之後再次略過 | 告訴我拿掉 |

## 9. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 309 列。

### 9.1 push 與雲端 CI（先紅後綠）

功能 commit `d26e8a00` 已 push（`79302fcf..d26e8a00  main -> main`；同一次 push 也帶出 Sprint 307 的純文件回填 commit `835d71e0` 與 Sprint 308 的 commit `8feddb60`），pre-push 的本機守門通過。

- **第一次雲端 CI 是紅的**：run [40556219811](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40556219811)，`headSha` 為 `d26e8a00`。Backend Unit Tests 與 Frontend Lint & Build 成功；Backend Integration Tests & Package 失敗——整合階段 `Tests run: 955, Failures: 0, Errors: 2`，兩個錯誤都在 `BookingControllerE2ETest`（該類別 `Tests run: 26, Errors: 2`）：`tearDown`（原檔第 269 行，刪除使用者）與 `@AfterAll cleanup`（第 294 行，刪除房源），都被 `bookings` 的外鍵擋下。依 CLAUDE.md 規則，我先下載雲端日誌確認真正的錯誤再分析，沒有盲修。
- **修復 commit `035a14e6`**（只改 `BookingControllerE2ETest.java`，+10／−2；原因與做法見 9.2）push 後，run [40564732879](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40564732879) `completed／success`，三個 job 皆 success（Backend Unit Tests、Backend Integration Tests & Package、Frontend Lint & Build），約 17 分 13 秒（18:57:27Z～19:14:40Z）；以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認 `headSha` 為 `035a14e66d09fb624612173b8e194176c3f5459b`，並下載整合 job 的完整雲端日誌確認 `Tests run: 954, Failures: 0, Errors: 0, Skipped: 0`、`BookingControllerE2ETest` 25／0／0。`git fetch` 後本機 `HEAD` 與 `origin/main` 同為該 commit，ahead／behind 為 0／0（之後新增的純文件回填 commit 留在本機）。
- **這次綠燈的證據強度**：綠的那次 run 裡 IT-M06-103 走的是 `200` 分支（第一個請求先完成），競態**沒有被觸發**；紅的那次走 `409` 分支。所以雲端這次綠燈只證明沒有回歸、套件全綠，**不證明競態已消失**——證據是 9.2 的本機注入前後對照。

### 9.2 雲端紅燈：既有的測試競態（第三次出現，第一次找到根因）

**歷史**：Sprint 151 第四輪本機完整 `mvn verify` 出現同樣的 2 個 teardown 外鍵錯誤，[SPRINT_151_PLAN.md](SPRINT_151_PLAN.md) 記為『測試間狀態殘留型 flaky、僅需重跑』；Sprint 275/276 的回填 commit `bdd06882` 的雲端 CI 也曾因同一個測試失敗並重跑轉綠（[RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 277 列；當時只記了測試名稱、沒有留下錯誤內容，所以是否為同樣的外鍵錯誤沒有證據）。兩次都被當成與變更無關而略過；這次是第三次，所以追根因。**Sprint 151 的『狀態殘留』歸因應更正**（症狀與本次根因吻合，但當時沒有重現，所以『同一個根因』是推論），已在該計畫書加更正註記。

**根因**（讀雲端日誌＋本機重現）：IT-M06-103 用 `CompletableFuture.runAsync` 送出第一個請求後不等它結束（整個類別裡唯一沒有 `join` 的背景請求）。第二個請求到達時第一個還在處理，回 `409`（測試預期的分支之一）；但第一個請求隨後才提交預訂。若這筆預訂落在 `tearDown`「列出該使用者預訂」之後、「刪除使用者」之前，刪使用者就撞外鍵；遺留的預訂又讓 `@AfterAll` 刪房源撞外鍵。機器夠快、第一個請求先完成時走 `200`（回快取）分支，就不會出事——所以是時序相關的偶發失敗。與本輪定價變更無關（根因在測試程式碼，而且在本輪之前就出現過）。

**重現**（暫時改程式、跑完用 SHA-256 比對還原）：

| 條件 | 結果 |
|---|---|
| 只把 `BookingService.createBooking` 放慢 500 毫秒 | 25／0／0，IT-M06-103 走 `409`——**沒有重現** |
| 服務端放慢 200 毫秒，並把 `tearDown`「列出預訂」與「刪除使用者」之間放大 300 毫秒的窄窗 | `Tests run: 26, Errors: 2`，IT-M06-103 走 `409`；錯誤點與外鍵約束名（`users`：`fkeyog2oic85xg7hsu2je2lx3s6`；`listings`：`fk6sm0gu2yl1gdbd0b1a0fkwqcl`）與雲端日誌完全相同（行號差 1 是注入多了一行） |
| 同上注入，套用修復 | `Tests run: 25, Failures: 0, Errors: 0`，IT-M06-103 仍走 `409`（競態確實被觸發、而且被擋下） |
| 不注入，套用修復 | 25／0／0，IT-M06-103 走 `200` |

第一列沒有重現，所以光放慢服務端不夠、要把窄窗撐開；這也代表注入的 300 毫秒是**放大器**，不是雲端實際的時序——雲端的窄窗是自然撐開的。每種條件只各跑一次。

**修法**：把背景請求登記在欄位 `inFlightRequest`，`tearDown` 在清理之前先等它結束；吞掉背景請求自己的例外（維持原本『不關心結果』的語意），並設 30 秒上限，避免背景請求卡住時整個套件無聲掛住。沒有改生產程式碼，沒有改任何斷言。測試檔 Checkstyle 0 違規、PMD 通過、pre-commit 通過。

**沒做**：IT-M06-103 自己的斷言偏弱（`409` 與 `200` 都算過、其他狀態碼只印警告不失敗），本輪只修清理競態、不改測試意圖，登記為 `DEF-427`。

### 9.3 commit 範圍

本輪的 commit 範圍：生產程式 `PricingService`、`RedisCartService`、`BookingService`（引擎關卡、移除孤兒欄位）；測試 `PricingServiceTest`、`StoreToggleGatesApplicationE2ETest`（新增）、`M12EffectivePriceIntegrationTest`、訂房與購物車單元測試、`BookingPricingFixtures`（新增）、Playwright `at-pricing-cart-checkout-real.spec.ts`；文件 `API_M01`、`API_M04`、`API_M06`、`API_M12`、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`、`SPRINT_308_PLAN.md`（補一行後續指引）與本計畫書。沒有改遷移、PRD、前端應用程式碼。 之後的兩個 commit：修復 commit `035a14e6`（只改 `BookingControllerE2ETest.java`）；以及純文件的回填 commit（`RELEASE_TRACKER.md` v4.40、`DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-427`、`SPRINT_151_PLAN.md` 更正註記、`SPRINT_308_PLAN.md` §10 後續、本計畫書；依慣例留在本機，隨下次有功能變更的 push 出去）。

> **後續（Sprint 310）**：`DEF-427`（IT-M06-103 斷言偏弱）已修復，且修復過程發現一個更大的問題——真實 Redis 下，訂房、訂單、合併結帳三個端點的同鍵請求「仍在處理中」其實回 500 而不是 409（`DEF-433`，已修）；§9.2 的 `inFlightRequest`＋`tearDown` 等待因為IT-M06-103 已不再有背景請求而移除。`DEF-425` 也已修復。見 [SPRINT_310_PLAN.md](SPRINT_310_PLAN.md)。
