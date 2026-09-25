# Sprint 196 Plan — 全庫 Repository 查詢「真的在真實資料庫執行過」掃描（DEF-277）

**Sprint**: Sprint 196
**日期**: 2026-09-25

## 1. 起點

Sprint 195 收尾時指出：DEF-272（週結算單從未成功產生）之所以沒被發現，是因為全庫測試絕大多數 mock 了 Repository；「全庫其他用 mock Repository 驗證的金額邏輯，是否還有同型盲點」尚未系統性檢查，並建議下一輪做此掃描。使用者回覆「請繼續完成上輪任務未完成任務」，本輪即執行該建議。

**過程揭露**：新 session 沒有上一輪的對話脈絡，使用者也表示找不到上輪內容。我從上一個 session（`d6dc85bc`）的紀錄讀出它最後的總結與建議，並在回覆中完整轉述給使用者，再據以開工。「未完成」的解讀（＝該次建議的下一輪掃描）是我的推論，使用者當時未逐字確認。

## 2. 方法：機械式掃描，取代逐一審視

原計畫是逐一審視「用 `@MockBean *Repository` 的整合測試」。實測範圍：57 個 Repository、157 個 `@Query`（29 個 native、35 個 `@Modifying`）、72 個 `@SpringBootTest` 檔案。逐一審視既慢又依賴人判斷，而且單元測試裡 mock Repository 的更多。

改以「程式能回答就用程式回答」：`RepositoryQueryExecutionIntegrationTest` 以反射列舉每個 Repository **自己宣告**的查詢方法（378 個），依簽名上的參數型別合成不會命中任何列的參數，在交易內對真實 PostgreSQL 各執行一次並一律回滾。因為參數型別取自方法簽名，所以「簽名與實體欄位型別錯配」（DEF-272 的形態）、欄位或表格不存在、SQL 語法錯誤、不支援的型別，都會立即現形。

**兩輪**：null 輪（所有可為 null 的參數傳 null）在前、非 null 輪在後——原因見 §5。共 751 次執行（非 null 輪 378 次、null 輪 373 次，沒有可為 null 參數的方法略過），略過 0 個。

## 3. 發現：DEF-277（🟠 評價搜尋端點在真實資料庫不可用，三層缺陷疊加）

`GET /v2/reviews/listing/{listingId}/search`（`ReviewController.searchReviews`，任何登入者皆可呼叫）背後的 `ReviewRepository.searchReviews`，在真實 PostgreSQL 有三層缺陷，像洋蔥一樣第一層蓋住第二層：

1. **`SIZE(r.images)`**：`Review.images` 是 `@JdbcTypeCode(SqlTypes.JSON)` 的 `List<String>`，Hibernate 視為基本屬性而非集合，SQL 翻譯階段拋 `ClassCastException`（`BasicAttributeMapping cannot be cast to PluralAttributeMapping`）。啟動時的查詢驗證抓不到，SQL 翻譯與參數值無關，所以**不論帶什麼條件每次都失敗**——整個端點不可用。
2. **`keyword` 為 null**：`CONCAT('%', :keyword, '%')` 在函式引數位置沒有型別脈絡，Hibernate 把 null 綁成 `bytea`，PostgreSQL 報 `function lower(bytea) does not exist`。
3. **`startDate`／`endDate`／`hasImages`／`hasReply` 為 null**：`:x IS NULL` 位置的 null 沒有型別，PostgreSQL 報 `could not determine data type of parameter $9`。

**為什麼沒被發現**：`ReviewServiceSearchTest`（14 個案例）全程 mock `ReviewRepository`，只驗證 Service 傳了什麼參數。

**修復**（`ReviewRepository.searchReviews`）：
- 「是否有圖片」改為 `COALESCE(FUNCTION('jsonb_array_length', r.images), 0) > 0`／`= 0`；SQL NULL 與 JSON 空陣列 `[]` 兩種「沒有圖片」的資料形態（欄位預設值就是 `'[]'`）都視為沒有圖片。
- `keyword` 三個出現處、`startDate`／`endDate`／`hasImages`／`hasReply` 的 `IS NULL` 位置加 `CAST(:x AS 型別)`（`ReviewRepository` 本身已有 `CAST(:userId AS text)` 先例）。
- `minRating`／`maxRating` **沒有**加 CAST：測試證實不需要（語意測試含 null 的 minRating/maxRating，全數通過；推測是 Hibernate 從 `r.rating >= :minRating` 的比較式推得出 Integer，此為推測、未讀原始碼證實）。是否加 CAST 是以證據決定，不是一律加。

## 4. 測試

**`ReviewSearchRealDbIntegrationTest`（9，真實 PostgreSQL，驗證語意而非只驗證「不拋例外」）**：種 4 則可見評價（含 `["a.jpg"]`、多張、`[]`、SQL NULL 四種圖片形態，1 則有商家回覆）＋1 則不可見（有圖片）＋1 則他人商品的評價（有圖片）。斷言：無條件→只回該商品 4 則可見；`hasImages=true`→2 則；`hasImages=false`→`[]` 與 NULL 兩則都算沒圖片；`hasReply` 兩向；關鍵字比對標題或內容且不分大小寫；評分區間含邊界；日期區間；組合條件；另有一個走端點實際的 Service 路徑的案例。

**`RepositoryQueryExecutionIntegrationTest`（1，全庫掃描）**：斷言失敗清單為空、略過清單為空（新增無法合成參數的方法者必須有意識處理）、執行次數下限 500（防止反射什麼都沒找到而空轉綠燈）。`IncorrectResultSizeDataAccessException` 視為「查詢可執行」（例如 `findByStripeConnectAccountId(null)` 命中 46 個沒有 Stripe 帳號的租戶——查詢已成功執行，只是資料形狀不符 `Optional`）。

**`SpecificationQueriesRealDbIntegrationTest`（4）**：見 §6，補上掃描碰不到的 `Specification` 動態查詢。

**紅燈先行**：
- 全庫掃描第一次執行即抓到 `searchReviews`（378 個方法中唯一失敗者）。
- `ReviewSearchRealDbIntegrationTest` 對未修改的程式碼 **9/9 全以同一個 `ClassCastException` 失敗**，包含「完全沒有篩選條件」的案例，證實端點從未能成功執行。
- 修復過程中依序浮現第 2、3 層的實際資料庫錯誤（以 PostgreSQL 的錯誤訊息為依據，未憑猜測改動），逐一修到 9/9 綠燈。

## 5. 突變驗證與一次「測試守門其實沒守到」的發現

為確認新掃描的 null 輪真的敏感，暫時拿掉 `CONCAT` 內的 `CAST`：

- **第一次突變：掃描仍然全綠**——守門失效。原因是我把 null 輪排在非 null 輪之後：同一個查詢先用非 null 值執行過，之後傳 null 就不再出問題。
- 實驗確認機制：**非 null 在前 → 全綠；同順序但中間清掉 Hibernate 查詢計畫快取 → 紅**。因此最終設計是 (1) null 輪在非 null 輪之前，(2) 開跑前清掉查詢計畫快取（`mvn verify` 所有整合測試共用同一個 JVM，別的測試可能已先執行過同一個查詢）。
- 重跑突變：確認以精準的 `function lower(bytea) does not exist` 變紅，並還原（`cmp` 與備份一致）。

**「Hibernate 會從先前非 null 的執行學到型別」是我對上述實驗結果的解釋**，我沒有讀 Hibernate 原始碼證明機制；有實驗證據的是「順序＋清快取會改變結果」。

## 6. 補強：Specification 動態查詢（掃描的盲點）

`JpaSpecificationExecutor.findAll(Specification, …)` 是**繼承**方法，而 Specification 由 Service 動態組出，全庫掃描碰不到。grep 找出全部 4 處：`RoomService.getRooms`、`AdminService.getTenants`／`getUsers`／`getAuditLogs`。這些查詢刻意只在條件有值時才加入 predicate（`getAuditLogs` 的註解明說是為了避開 PostgreSQL 對純 null 參數的型別推斷限制），所以 null 問題不適用；風險是屬性路徑拼錯只在執行時才爆。

`SpecificationQueriesRealDbIntegrationTest` 對每一處把**所有篩選條件同時帶上**；租戶與使用者兩處另有命中剛建立資料的正向斷言。**突變驗證**：把 `RoomService` 的 `root.get("location")` 改成 `"locationX"`，測試以 `Could not resolve attribute 'locationX'` 變紅，已還原。

## 7. 驗證結果

**預設時區** `mvn -o clean verify`（真實 postgres/redis）：**1716 個單元測試（持平）+ 514 個整合測試（+14），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 9:08。

**跨時區**：`ReviewSearchRealDbIntegrationTest` 與 `RepositoryQueryExecutionIntegrationTest` 另以 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` 與 `Pacific/Honolulu` 各重跑，皆 9/9 與 1/1 通過。

**過程揭露**：第一次背景完整驗證剛進入單元測試階段（尚未有任何測試跑完）時，我決定補上 §6 的 Specification 探測測試而中止它，避免在 Maven 執行中改動測試原始碼；上述數字來自補完後重跑的單一完整驗證。

## 8. 誠實揭露總結

- **掃描只證明「查詢能被資料庫接受並執行」，不證明結果語意正確**：合成參數不命中任何資料。語意要靠各功能自己的真實 DB 測試（本輪只為評價搜尋補了語意測試；其餘 377 個方法只證明「可執行」）。
- **只涵蓋 Repository 介面自己宣告的方法與 4 處 Specification**：全庫 `src/main` 沒有 `EntityManager`／`createQuery`／`createNativeQuery`／`JdbcTemplate`／`@NamedQuery` 的任何使用，也沒有自訂 Repository 實作類（grep 全目錄確認），但這是 2026-09-25 的現況；日後新增者不會被掃到（Repository 新增的宣告方法會被掃描自動涵蓋，Specification 與直接查詢不會）。
- **null 只測「全 null」與「全非 null」兩種組合，不是每個參數單獨為 null**。PostgreSQL 的型別推斷是逐參數的，而一個錯誤會遮住後面的（本輪連續浮現三層就是如此）；掃描通過後，每個查詢在「全 null」下沒有問題，但「只有某一個為 null」的組合沒有逐一驗證。
- **`minRating`／`maxRating` 沒加 CAST 是實測證明可行**，不是漏改；若日後 Hibernate 升版改變推斷行為，null 輪會抓到。
- **清快取使用 Hibernate 內部 API**（`getQueryEngine().getInterpretationCache().close()`），Hibernate 升版可能需調整；該處若編譯失敗，是升版的正常後果。
- **觀察但未修（不登記 DEF）**：`TenantRepository.findByStripeConnectAccountId(null)` 會命中所有沒有 Stripe 帳號的租戶（46 筆）而拋 `NonUniqueResultException`。唯一呼叫端 `TenantStripeConnectService.syncAccountStatusFromWebhook` 的 `accountId` 來自已通過 Stripe 簽章驗證的 `account.updated` 事件，合法事件必有 `id`，null 只會出現在偽造或畸形 payload，實務上不可達。
- **未做**：Sprint 195 建議的次選方向（`SecurityConfig` 安全標頭）；實際 HTTP 層對評價搜尋端點的驗證（本輪驗證到 Service 層與 Repository 層，Controller 與 Spring Security 過濾鏈未另測）。
- 本輪未派背景 agent；`-Dtest` 指定的整合測試類別會略過 surefire 排除規則，屬既知行為。
