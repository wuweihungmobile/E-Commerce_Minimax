# Release 流程追蹤表 / Release Tracker

> **文件類型**: 追蹤表 (Tracker)
> **版本**: v1.0
> **建立日期**: 2026-06-11
> **目的**: 追蹤每個 Sprint 的 Release 狀態，避免連續多個 Sprint 跳過 Release

---

## 📋 Release 總覽

| Sprint | Release Tag | PR 號碼 | 合併日期 | 主要功能 | 狀態 |
|--------|-------------|---------|----------|----------|------|
| Sprint 104 | v2030.02.20-01 | - | 2026-09-01 | DEF-049 整合測試耗時的兩支真槓桿（1 US，5 SP）：**US-001** 承接連續三輪（S101/S102/S103）被列為候選卻未動的 DEF-049。本輪動它，但**先查證前提，結果推翻了該記錄的兩個關鍵敘述、並找到它從未提及的最大一支槓桿**。**(1) `reuseForks=false` 的理由從未被查證過**——它由 commit `d444b2d`（2026-06-15）引入，該 commit 標題是「fix(DB): V30 migration 移除不存在的 tenant_id 索引」，與測試 fork 毫無關係，是夾帶的順手改動，時間點正落在 CLAUDE.md 記載「20+ 次盲目修復」的期間，commit message 完全沒提到它。**(2) 那句「avoid SecurityContext pollution」指錯了對象**——稽核 138 個測試類別後，`SecurityContext` 是唯一**沒有**缺口的（設定 context 的測試全部都有對應 `clearContext()`，零例外）；真正會洩漏的是 `WithErpSecurity` 的 factory 在建立 SecurityContext 時**順帶**寫入 `TenantContext` 兩個 ThreadLocal，而 Spring Security 的 `WithSecurityContextTestExecutionListener` 只還原 SecurityContext，對 `TenantContext` 一無所知。另查證整合階段零 `mockStatic`、零 `System.setProperty`、無跨類別 static 狀態、外部資源皆有 `@AfterAll` 回收。**(3) 實測回答了懸置三輪的問題：顧慮不成立**——`reuseForks=true` 且**完全不裝任何清理機制**跑完整套件，404 個整合測試**零失敗**。故 `ThreadLocalIsolationExtension` 誠實定位為**護欄而非 bug 修復**：讓「共用 JVM 不得互相汙染」這個從此永久必須成立的不變量成為結構保證，而非仰賴 138 個類別各自記得清理。**(4) DEF-049 稱 `reuseForks` 為「單一設定變更，影響最大」也不準確**——單獨翻它會撞上物理限制：51 個類別有 **27 種相異 context 設定** × HikariCP 預設池 10 = **270 條連線** vs 測試 postgres `max_connections=100`，加上本機 JVM 預設堆僅 2.0 GB，故必須配套 `runOrder=alphabetical` 與 `spring.test.context.cache.maxSize=3`。**刻意不採「壓低連線池」**：`M11PromoConcurrency`／`M12InventoryConcurrency` 各以 10 執行緒壓同一列，池子小於執行緒數會讓它們在取連線處排隊而**不再真的併發**——測試仍全綠但綠得沒有意義（Sprint 97「固件繞過同一段邏輯」同型陷阱）。`runOrder=alphabetical` 的主要價值也不是那多出來的 4 次命中（模擬：filesystem 14 vs alphabetical 18），而是**決定性**：filesystem 順序在 macOS 與 Linux runner 上不同，本機與雲端的快取行為、耗時、順序相依 flaky 本來就不一致。**(5) DEF-049 從未提及的最大槓桿**：CI 整合 job 跑 `mvn verify`，而 `verify` 含 `test` 階段——以 S103 雲端 run 33511000120 逐 plugin 取時間戳（非推估），22m36s 裡 **surefire 佔 4m25s（19.5%），在重跑 Backend Unit Tests job 已跑完的同一批單元測試**；已查證零額外覆蓋（87 個單元類別中唯一帶 Spring context 的 `SellerDashboardServiceCacheTest` 自帶 `@ActiveProfiles`，其餘純 Mockito）。修法為新增 Maven profile `skip-unit-tests`（由 `-DskipUnitTests=true` 啟用，**預設組態完全不碰，本機 `mvn verify` 行為不變**），只在 workflow 的整合 job 傳該旗標；刻意不用 `-DskipTests`（那會連 failsafe 一起跳過）。**🔴 此處第一次的寫法引入了一個靜默回歸並被 `make validate-release` 攔下**：原本把 `<skipTests>${skipUnitTests}</skipTests>` 直接寫進 surefire 的 `<configuration>`，而 POM 裡的顯式值會**蓋過命令列的 `-DskipTests`**，使 `mvn package -DskipTests` 不再跳過測試——`validate-schema` 建 JAR 時真的跑起單元測試，而該階段 test DB 已被 `test-db-down` 關掉（讓出 5432/6379 給 act），`SellerDashboardServiceCacheTest` 三案例以 `Failed to load ApplicationContext` 失敗（`Errors: 3`，`make[1]: *** [validate-schema] Error 2`）。影響範圍比表面大：workflow 的 `Package Backend` 步驟也是 `mvn package -DskipTests`，該寫法會讓它變成**第三次**跑單元測試，把本輪要省的時間加回去（act 的整合 job 仍 succeeded 只因當時服務容器的 DB 還在）。修正後逐一實測三種情境皆正確（`-DskipTests` 跳過／`-DskipUnitTests=true` 只跳 surefire 而 failsafe 照跑／預設照跑）。**教訓**：本機通過了我自己設想的全部三種情境，卻打斷了第四種——一個既有、而我沒有列舉的用法。動共用組態（pom／workflow／全域設定）時，風險不在「我改的那條路徑」而在「別人已經依賴、我沒列舉的路徑」；這正是完整驗證程序不能因「本機都綠了」而跳過的理由。**依 Rule 7 surefire 與 failsafe 一起改**，不留「已知不成立的理由」與「依它設定的組態」並存的矛盾。**驗證**：先廉價紅燈（關閉自動偵測、單獨跑守衛測試 → 確實失敗），再 Run B（`reuseForks=true` 但故意不註冊擴充 → 整合 404 全綠，回答 (3)），再 Run C 完整 `mvn -o verify` → **單元 1017 / 整合 404 / 0 失敗、checkstyle main+test 皆 0 違規、7 分 06 秒**。**本機實測 32:54 → 7:06（−78%）**，測試數完全不變。**主要效益來源與預測不同**（先寫預測再實測才發現）：模擬預測「省 18 次 context 啟動 ≈ 561 秒」，實際省 1298 秒——共用 JVM 後第一個 context 仍要 33 秒、後續每個只要 5～8 秒（類別載入／Hibernate 中繼資料／JIT 暖機只付一次），**真正的成本結構是「冷 JVM 裡的啟動特別貴」而非「啟動次數」**；context 啟動原本佔整合牆鐘 **79%**（48 次 × 31.2 秒）。**意外發現並更正歷史記錄**：Run B surefire 報 1017、基準報 1077，差 60 一度像「測試被靜默跳過」，故在採信任何加速數字前先追查——88 個基準單元類別**全數執行、逐類測試數完全一致**，差額全在 `@Nested`：16 個不一致者有 15 個**基準恰為 Run B 的兩倍**（實際觀察到 `TenantServiceTest$MemberInviteTests` 同時印出 `Tests run: 11` 與 `Tests run: 19` 兩行）。以原始碼裁決：單元測試共 **1003 個 `@Test` + 4 個 `@ParameterizedTest`**（案例數 4/3/3/4 = 14 次展開），**1003 + 14 = 1017** 與 Run B 完全相符；1077 需要那 4 個參數化展開出 74 個案例才成立，不可能。故 S100~S103 記錄的單元測試數（1055/1068/1074）**全部偏高約 60**，正確量級 **1014**；S102 記下的「差 1」不是差 1，而是**計數方法本身結構性不可靠**。整合 404 不受影響（無 `@Nested`）。**不回頭改寫歷史 Plan**，但日後引用基準應以 1014 為準並以原始碼交叉核對。新增 `ThreadLocalIsolationExtensionTest`（3 個，含一個記錄「本擴充為何存在」的測試）與 `ThreadLocalIsolationAutoRegistrationTest`（2 個，**會因護欄失效而失敗**——前者只證明「呼叫它會清乾淨」，不能證明它有被註冊，一個打錯字的註冊檔會讓護欄無聲失效而所有測試依舊全綠；刻意用同類別內 `@TestMethodOrder` 而非跨類別順序，避免日後有人調 `runOrder` 就悄悄失去守衛作用）。**雲端效益本輪未實測，刻意不寫成既定事實**（S103 教訓），待 Sprint 105 開工回填。新增 🟢 DEF-052（`-Pintegration-test` 是不存在的 Maven profile，`pom.xml` 無任何 `<profiles>` 區段；無功能影響但會誤導——DEF-049 自己的記錄就引用了它，依 Rule 3 未順手修改）。詳見 SPRINT_104_PLAN.md | ✅ 已 push（2026-09-01，commit `488d686` + 防呆 commit `9ef2300`；`make validate-release` 第三次全綠——act 三 job + schema 對齊 + E2E 54 passed/6 skipped，耗時 **23.9 分**）。**雲端 CI 已於當日回填（原訂 S105 開工，提前完成，見 SPRINT_104_PLAN §4.11）**：run **33527771681** 三個 job 全綠：unit **3m20s**／integration **5m35s**／frontend **1m18s**，整個 run **9m58s**（S103 對照：8m01s／24m29s／1m27s，整個 run 32m36s）。**(a) 重複單元測試消除已由 log 直接證實**——整合 job 中 surefire 印出 `Tests are skipped.`，而 failsafe 仍產出 `Tests run: 404, Failures: 0, Errors: 0`，**兩件事成對成立才算數**（否則只是靠少跑測試換加速）；**(b) 雲端 fork 重用約 4.37 倍**，與兩個槓桿合計的 1357s→253s（−81.4%）需區分。⚠️ **僅單一雲端樣本**，而 S102/S103 已證實同碼 run 之間變異可達 21%——「效益為真」可斷言，「倍率精確為 4.37」不可。⚠️ **耗時樣本序列在此中斷**：前五筆（25m19s／29m48s／23.1m／28m05s／24m29s）的意義建立在測試內容相同之上，本輪同時改了 fork 設定與 CI 指令，日後應自 S104 之後重新起算。另註：`488d686` 自身的 run `33527330716` 被 `9ef2300` 的 push 依 `cancel-in-progress: true` 取消（Integration／Frontend 各僅跑 1m26s／1m58s，離 45m／10m timeout 極遠，**非逾時亦非測試失敗**），因兩 commit 的 backend/frontend 程式碼樹相同，`9ef2300` 的全綠 run 完整涵蓋之，不需補跑）|
| Sprint 103 | v2030.02.06-01 | - | 2026-09-01 | 商品庫存三段式操作改為原子敘述（DEF-050，1 US，3 SP）：**US-001** 承接 Sprint 102 橫向掃描命中、列為 Sprint 103 首要的 DEF-050。`ProductInventoryService` 的 `reserveForOrder`／`releaseForOrder`／`deductForOrder` 皆為「載入實體 → 改欄位 → `save()`」的讀後寫。**本輪紅燈實測推翻了 DEF-050 的原始定性**：該記錄稱其與 DEF-046「完全同型」、併發下會「雙雙通過檢查而超賣實體商品，且兩次 `save()` 互相覆蓋使 `reservedQty` 少算」，並據此評為「嚴重性高於 DEF-046」。實際上 `ProductInventory` 帶 `@Version` 樂觀鎖而 `PromoCode` 沒有（全庫僅 `Inventory`／`ProductInventory` 兩個 entity 有），以真實 PostgreSQL + 10 條執行緒實測，三個方法的結果完全一致：`granted=2, insufficientStock=0, unexpectedFailures={ObjectOptimisticLockingFailureException=8}`，Hibernate SQL log 直接證實 `update ... where sku_id=? and version=?`。**零超賣、零 lost update**，真正的失效模式是：(1) 預扣端 10 張訂單搶 3 件庫存只有 2 張成功，**庫存賣不完**且 8 位買家看到 500 而非「庫存不足」；(2) 釋放端 10 筆取消只有 2 筆還得回去，`reserved_qty` 殘留使那批貨**永久賣不出去**；(3) 扣帳端失敗被 `PaymentStateService.deductStockSafely` 的 try/catch **完全靜默吞掉**——付款成立卻沒扣 `total_qty`，該 SKU 帳實不符且持續可賣，**這才是真正通往超賣的路徑（間接、延遲、無任何徵兆）**。原記錄「嚴重性高於 DEF-046」不成立，但「維持高優先級」的結論正確。修法複用 S102 的條件式 UPDATE，新增 `reserveIfAvailable`／`releaseReservation`／`deductReserved` 三支原生 `@Modifying` 查詢；**三條敘述都刻意帶 `version = version + 1`**——M16 ERP 仍走 JPA `save()`，若不推進版號，ERP 端會拿過期快照通過樂觀鎖檢查而整列覆蓋訂單流程的寫入，那才會做出真正的 lost update（本輪最容易被忽略的副作用）。`@Modifying` **刻意不加 `clearAutomatically`**：`reserveForOrder` 在 `createOrderFromCart` 中於 `orderRepository.save(order)` 之前被呼叫，清空 persistence context 會把稍早載入的 `Listing`／`ProductSku`／`User` 一併 detach。`reserveForOrder` 的 0 筆回傳有兩種意義（可售量不足 vs 該 SKU 無庫存列＝未啟用追蹤），以 `existsById` 區分且只在失敗路徑上多付一次查詢。`ProductInventory.hasAvailableStock()`／`reserve()`／`release()` **刻意移除**（改用原子 UPDATE 後即成零呼叫死碼，留著等於留一個無徵兆退回讀後寫的入口——與 S101 移除 `computeDiscount` 舊多載、S102 移除 `incrementUsageCount` 同一個「大聲失敗」理由）；`addStock()`／`deductStock()` 保留供 M16 ERP 使用。測試淨 +7：新建 `M12InventoryConcurrencyIntegrationTest`（7 個，真實 DB 多執行緒），`ProductInventoryServiceTest` 6 個**全部改寫而非新增**（數量變化已下沉到 SQL，mock 掉 Repository 無從觀察，改為驗證派送行為，並保留強化後的 `never()).save(...)`／`never()).findById(...)` 作為「不得退回讀後寫」守衛）。**既有 6 個單元測試全部 mock 掉 Repository，是這個缺陷從 Sprint 88 起對測試隱形的原因**（Sprint 97「固件繞過同一段邏輯」教訓的又一實例）。紅燈 7 個中 3 個失敗、綠燈 7/7；另 4 個為守衛型斷言在舊實作下本就通過，如實記錄不計入紅燈範圍。**方法論教訓**：DEF-050 是靠「結構同型」的樣式比對找到的，樣式比對**找對了位置但推錯了後果**——它只比對程式碼形狀，沒有查證 entity 的併發防護。新增 🟡 DEF-051（ERP 側 `createManualMovement`／`createInboundMovement` 同型讀後寫，但因本輪已推進 version 故無資料正確性風險，僅可用性問題）。全量回歸 `mvn -o verify`：見 SPRINT_103_PLAN.md | ✅ 已 push（2026-09-01，commit `3b19790`；雲端 CI run **33511000120** 三個 job 全綠：unit 8m01s／integration 24m29s／frontend 1m27s）。**Sprint 104 開工回填的第五筆 CI 耗時樣本**：S103 多加了一個整合測試類別（+7 測試），該 job 反而從前一筆的 28m05s 降到 **24m29s**，再次印證變異來自 runner 而非測試內容。五個樣本為 25m19s／29m48s／23.1m／28m05s／24m29s。另注意 `Backend Unit Tests` 已到 **8m01s**（預算 12 分，用掉 67%），成長來源與整合 job 相同 |
| Sprint 102 | v2030.01.23-01 | - | 2026-09-01 | 優惠券額度佔用改為原子操作（DEF-046，1 US，3 SP）：**US-001** 承接 Sprint 100 記錄、Sprint 101 列為第 1 順位的 DEF-046。`max_usage_count` 自 Sprint 100 起首次真正生效，但 `resolveValidPromoForCheckout` 的 `isUsageLimitReached()` 檢查與 `commitPromoUsage → incrementUsageCount` 的「讀出實體 → +1 → save」分屬兩次 DB 往返，中間的讀後寫窗口讓併發結帳雙雙通過。**原記錄列為「🟢 低優先級 / 理論上可超發」，本輪實測推翻該定性**：以真實 PostgreSQL + 10 條執行緒（各自獨立交易）壓同一列 `promo_codes`，舊實作下上限 3 的券被 **10 條執行緒全數領走**（超發 233%），不限量券的 10 次遞增在 DB 只累計為 **1**（9 次寫入互相覆蓋的 lost update）。修法採 **DB 條件式 UPDATE**（`UPDATE ... SET current_usage_count = COALESCE(...)+1 WHERE id = ? AND (max_usage_count IS NULL OR ... < max_usage_count)`）而非 DEF-046 原記錄建議的 Redis Lua——後者會讓額度出現**兩個真相來源**（Redis 計數 vs `promo_codes.current_usage_count`），退還／對帳／Redis 重啟都得再補協調機制；`RateLimitFilter`（S93）用 Redis 是因為限流狀態本就不該落 DB，與本案不同。原記錄的前置需求「確認業務上可接受的超發容忍度」**未召開 AskUserQuestion**：本修法容忍度為零且無效能取捨，沒有可讓使用者權衡的選項，故未佔用其決策成本（判斷理由已寫入 Plan）。**該敘述取得的行鎖同時把每人限用的重查序列化**，因此一併修掉兩個對稱缺口：(1) `max_usage_per_user` 的檢查與用券紀錄寫入之間同樣是讀後寫（同一買家並發雙開可繞過），改為在鎖內重查；(2) `refundPromoUsage` 退還額度也是「讀出 → 減 1 → save」，兩筆訂單同時取消會互相覆蓋使額度永久蒸發，改為原子相對遞減。`PromoService.incrementUsageCount` **刻意移除而非保留**（回傳 `void`，呼叫端無從得知額度是否真的取得，留著等於留一個讓人無徵兆重新引入競態的陷阱——與 S101 移除 `computeDiscount` 舊多載同一個「大聲失敗」理由），改為 `tryConsumeUsageQuota(): boolean` + `releaseUsageQuota()`。新增 12 個測試：新建 `M11PromoConcurrencyIntegrationTest`（+5，**本專案首個多執行緒併發整合測試**）、`PromoServiceTest` +5（含「不得退回讀後寫」的 `never()).save(...)` 斷言）、`OrderPromoCodeTest` +2（原子佔用失敗 → E-5009 不留訂單；鎖內重查擋每人限用）。移除 2 個已失去意義的舊測試，其中 `refundNeverGoesNegative` **未靜默刪除**而是遷移為真實 DB 案例（下限保護已下沉為 SQL `GREATEST`，mock 版無從驗證）。**實際執行紅燈驗證**：暫時把 `tryConsumeUsageQuota` 換回舊語意重跑，5 個案例中 2 個立即失敗（期望 3 實得 10、期望計數 10 實得 1），另 3 個為守衛型斷言不在紅燈範圍，如實記錄。全量回歸 `mvn -o verify` **1471 tests 0 fail**（單元 1074 + 整合 397）、checkstyle main/test 皆過。**測試計數以 `@Test` 實數核對**（沿用 S101 教訓）：新增 12、移除 2、淨 +10；同時誠實揭露 S101 文件記載的單元基準 1068 應為 1069（差 1，已排除新併發類別被 surefire 重複計入的可能，未回頭考證 S101 當時量測條件）。**自我更正，並連帶推翻 DEF-049 的評估方向 (1)**：本輪原先聲稱新測試類別「對齊註解以共用已快取 Spring context、避免再加一次冷啟動」，查證 `pom.xml` 後確認**該說法錯誤**——failsafe（與 surefire 同）設定為 `forkCount=1` + `reuseForks=false`（原註解寫明為避免 SecurityContext 汙染），每個測試類別跑在各自 JVM，Spring context 快取無法跨類別共用，註解怎麼對齊都沒有效益。連帶推翻 DEF-049 的評估方向 (1)（「收斂 context 設定以提高快取命中率」）：在現行 fork 設定下收斂註解不會有任何效果；DEF-049 量到的「30 類平均 28.7 秒、分布極平坦」正是 `reuseForks=false` 的必然結果（每類都付一次完整 context 啟動），**真正的第一支槓桿是 `reuseForks`**（需先確認當初 SecurityContext 汙染的顧慮是否仍成立）。已回寫 DEF-049 記錄。**新增高優先級延後項 DEF-050**：修復後依 Action Item 做同類競態橫向掃描，在 `ProductInventoryService.reserveForOrder` 命中**完全同型**的讀後寫（`hasAvailableStock` 讀 `totalQty - reservedQty` → `inventory.reserve()` + `save()` 寫回整個實體），併發下單搶同一 SKU 最後一件會雙雙通過檢查而**超賣實體商品**，嚴重性高於 DEF-046（超發的是實體貨而非可補償的折扣額度）；依 Rule 3 不順手擴大範圍（該模組需自己的併發測試與紅燈驗證），記錄為 DEF-050 並建議排入 Sprint 103，修法可直接複用本輪的條件式 UPDATE。 | ✅ 已 push（2026-09-01，commit `a18dd0f` + 回填 commit `cbede57`；`make validate-release` 全綠——act 三 job + schema 對齊 + E2E 60/60；兩筆 commit 的雲端 run 皆三個 job 全綠）。**Sprint 103 開工回填第四筆 CI 耗時樣本**：`cbede57` 只改文件與一段 javadoc、測試套件與 `a18dd0f` 完全相同，run **33495383858** 的整合測試 job 卻從 23.1m 變成 **28m05s**（+21%；unit 7m36s／frontend 1m25s）。四個樣本累計為 25m19s／29m48s／23.1m／28m05s。**這組同碼對照把 v1.7 的更正從「推測變異大」推進為「確定變異來源在 runner 而非測試內容」**——同一套測試在兩次 run 之間差 21%，而 S102 新增 5 個整合測試時該 job 反而更快。DEF-049 的結構性成長問題仍成立，但任何以單次 run 時長推估「還剩多少餘裕」的做法皆不可靠 |
| Sprint 101 | v2030.01.09-02 | - | 2026-09-01 | FREE_SHIPPING 免運券落地 + 購物車運費預覽（DEF-045，1 US，5 SP）：**US-001** 承接 Sprint 100 掃描順帶發現的 DEF-045：`PromoService.computeDiscount` 對 `FREE_SHIPPING` 直接回 `BigDecimal.ZERO` 並註明「免運費由物流模組處理」，但物流模組（`ShippingTemplateService`）全庫零相關邏輯——main 程式碼僅 3 處提及此值（enum 定義、回 ZERO 的 case、一行 DTO 註解），店家發此型別的券、買家套用後折扣為 0 且運費照收。PRD §769 僅在 M04 模組表寫「免運計算」，**計算規則全文未定義**，故經 AskUserQuestion 由使用者裁定：**全額折抵運費**（`maxDiscountAmount` 若有設仍為上限）、**購物車一併預覽運費與折抵**。修復：`computeDiscount` 改為單一 3 參數簽章 `(promo, itemsTotal, shippingFee)`，**刻意不保留 2 參數多載**——保留多載等於保留「呼叫端無徵兆拿到 0 折扣」的同一個陷阱，移除後編譯器一次列出全部呼叫點（刻意的大聲失敗）；折扣型別分派抽為私有 `rawDiscountByType`（內嵌會使 NPath 衝到 288，超過上限 200，與 Sprint 100 同一陷阱）。**連帶修復兩個相鄰缺口**：(1) `getCartWithPromo` 在 main 程式碼為**零呼叫死碼**，`GET /v2/cart` 走的是不含 promo 的 `getCart`，買家套券後重新整理購物車折扣即消失（第四次由「Service 方法零呼叫者」訊號直接命中缺口）；(2) 購物車完全不顯示運費，改為一律回填 `shippingFee`、`finalAmount` 語意改為含運費，`previewShippingFee` 基數只取 PRODUCT 小計（與結帳一致，純 ROOM 購物車回 0，避免對訂房顯示實體出貨運費）。與滿額免運的疊加不需另做排除（運費本為 0 時券自然算出 0 折扣）。前端購物車摘要新增「運費」列（0 顯示「免運」），總金額改為一律取 `finalAmount`。新增 12 個測試：`PromoServiceTest` +6、`OrderPromoCodeTest` +2（含 `verify` 結帳確實把運費傳進折扣計算——DEF-045 的失效點本身）、`RedisCartServiceTest` +3、`M11PromoCheckoutIntegrationTest` +1（真實 DB/Redis/JWT 完整迴路：購物車預覽 60 運費 → 套券折抵 60 → 下單實收回到商品小計 200，修復前為 260）。全量回歸 `mvn -o clean verify -Pintegration-test` **1460 tests 0 fail**（單元 1068 + 整合 392）。**實際執行紅燈驗證**：暫時還原舊行為重跑，3 個行為型斷言確實失敗（expected 120.00/60.00/60.00，實得 0），另 3 個為守衛型斷言不在紅燈範圍（如實記錄）。另記錄新延後項 DEF-048（混合購物車 PRODUCT+ROOM 的折扣基數，顯示端含 ROOM、結帳端只含 PRODUCT，早於 Sprint 100 即存在，依 Rule 3 未順手修改）。**雲端 CI 後續**：本 Sprint 的 push 讓沉寂已久的雲端 CI 第一次真的跑起來（S99/S100 皆被 GitHub Actions 帳單問題擋在 2~5 秒內），隨即暴露 `Backend Integration Tests & Package` 的 `timeout-minutes: 25` 預算不足（run 33466882327 於 25m19s 被 cancel，步驟 1-8 全綠、無任何測試失敗）。已以 commit `09020e0` 調為 45 分並記錄 DEF-049（結構性成長：30 個整合測試類別平均 28.7 秒且分布平坦＝Spring context 啟動主導，單純調高數字不是解法）。run 33471691304 重跑**三個 job 全綠**（unit 6m58s／integration 29m48s／frontend 1m26s）。**流程**：依使用者裁定建立 `SPRINT_ARTIFACT_CONVENTION.md`，正式承認 Sprint 93 起「Retro 併入 Sprint Plan」的慣例，並要求 Plan 自 Sprint 101 起必含 Velocity 與 Action Items 兩節（原 RETRO 中唯一未被 Plan 覆蓋的內容）；誠實揭露 Sprint 93~100 缺此兩節且不回填 | ✅ 已 push（2026-09-01，commit 64b0afb + CI 修正 09020e0；雲端 CI run 33471691304 全綠）|
| Sprint 100 | v2030.01.09-01 | - | 2026-09-01 | 優惠券機制完整斷鏈修復（PRD §9.5.1/§2630，1 US，8 SP）：**US-001** 第八輪 PRD 掃描以「孤兒錯誤碼 → 零呼叫死碼 → 欄位零讀取」三訊號連續命中，查出**目前唯一直接涉及金錢收取正確性**的缺口。**(1)** `OrderService.createOrderFromCart` 取購物車用不含 promo 的 `getCart()`、金額為 `Σsubtotal + shippingFee`，折扣從未扣除——買家在購物車看到「已套用優惠券，折扣 $XXX」，下單卻被收全額（紅燈實測 `expected: 160 but was: 260`）。**(2)** `PromoService.incrementUsageCount` 在 main 程式碼零呼叫者，`current_usage_count` 永遠是 0，`max_usage_count` 總量上限形同虛設，限量券可無限使用。**(3)** `max_usage_per_user` 自 V20 建表即存在但全庫零讀取，同一買家可無限次重複用同一張券。**(4)** 促銷碼只在加入購物車時驗一次，Redis TTL 期間過期/停用/售罄皆不被攔。**(5)** Order 無折扣欄位，導致 PRD §2630「取消時退還優惠券」在資料上根本無法實作。**(6)** 下單後購物車券碼未清除，同張券被下一單沿用。修復：V70 migration（orders 加 `promo_code`/`discount_amount`；新建 `promo_code_usages` 表，採軟撤銷 REVOKED 比照 Sprint 98 `tenant_members.status` 決策）；新增 `RedisCartService.getAppliedPromoCode`（只回券碼、刻意不回折扣——`getCartWithPromo` 的折扣是 fallback-tolerant 顯示值，券失效會靜默回退原價，不可作為收款依據）；`OrderService` 新增 `applyPromoDiscount`/`resolveValidPromoForCheckout`/`commitPromoUsage`/`refundPromoUsage`，依 PRD 明訂順序重驗且**失敗一律拒絕下單不靜默改收原價**，取消時不限 CREATED 狀態一律退還額度。啟用兩個原孤兒碼 `E_5008`/`E_5009`（修缺口同時消滅孤兒碼），連帶修正 `GlobalExceptionHandler` 未涵蓋此二碼會落入 `default → 500` 的問題。前端訂單詳情頁新增折扣列。新增測試：`OrderPromoCodeTest`（14，皆先紅燈證實）、`M11PromoCheckoutIntegrationTest`（真實 DB 完整迴路，刻意不 mock repository——既有 `M11CartPromoIntegrationTest` 正是以 `@MockBean` 繞過才讓缺口存活 99 個 Sprint）。全量回歸 `mvn verify -Pintegration-test` **1446 tests 0 fail**（單元 1055 + 整合 391），checkstyle 0 violations，`make validate-schema` V70 無漂移，前端 tsc/eslint 0 errors。誠實：`E_8009` 客服工單孤兒碼經逐方法檢視確認為**偽陽性**（擁有權檢查完整，只是回「找不到」）已排除；順帶發現 `FREE_SHIPPING` 折扣型別物流模組零處理（選此型別買家拿不到任何優惠）記錄待評估；ROOM 訂單套券（PRD US-010）、併發額度競態均列延後 | ✅ 已 push（2026-09-01，commit 144b219）|
| Sprint 99 | v2029.12.26-01 | - | 2026-07-19 | StoreStaff 角色同步 + RefreshToken 租戶解析 + ERP Feature Toggle 三項修復（PRD §7.3/§7.4.1/§7.5，1 US，5 SP）：**US-001** 第七輪 PRD 掃描（延續「角色授予雙邊同步」判讀技巧）找到三個明確缺口。**(1)** `TenantService.acceptInvite()`（Sprint 98 新增）只更新 `tenant_members.status`，從未同步 `user.setRole(STORE_STAFF)`——是 Sprint 97/98 對 StoreOwner 修復的孿生遺漏，同一個 Sprint 新增的功能沒有連帶檢查；同時收斂 `inviteMember` 僅允許邀請 `STORE_STAFF`（拒絕 `STORE_MANAGER`，因 `User.UserRole` 無對應值、`RolePermissionMapping` 亦未定義其權限，邀請後同步也拿不到任何權限）。**(2)** `AuthService.refreshToken()` 在 `user.tenantId` 為 null 時直接退化為 `SYSTEM_TENANT_ID`，未比照 `login()` 查詢 `tenant_members` 取得正確租戶——抽出共用 `resolveTenantForUser()` 供兩處使用。**(3)** `PurchaseOrderService.createPurchaseOrder()` 從未檢查 `ERP_ENABLED` Feature Toggle（PRD §7.5 明訂），對照 `RETAIL_ENABLED`/`BOOKING_ENABLED`/`DYNAMIC_PRICING_ENABLED`/`CMS_ENABLED` 皆已正確實作，唯獨 ERP 模組漏掉，已補上並修正 `M16ErpIntegrationTest` 種子資料（原本繞過 `initializeFeatureToggles` 直接種 `tenants` 表，缺少對應 toggle 列）。新增/更新測試：`TenantServiceTest`（`acceptInvite` 補斷言 + 新增 `STORE_MANAGER` 拒絕測試）、`AuthServiceRefreshTokenTest`（新增 tenant_members fallback 測試）、`PurchaseOrderServiceTest`（新增 ERP_ENABLED 拒絕測試）。全量回歸 `mvn verify -Pintegration-test` **1417 tests 0 fail**（單元 1027 + 整合 390），checkstyle/PMD 0 violations，`make validate-schema` 無漂移（無 migration）。誠實：`STORE_MANAGER` 角色若未來需要真正支援，須先在 `User.UserRole`/`RolePermissionMapping` 補齊對應定義，屬較大角色體系擴充非本輪範圍；其餘 Feature Toggle 是否有類似遺漏未做全面掃描，僅修復本輪發現的 ERP_ENABLED | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 98 | v2029.12.12-01 | - | 2026-07-19 | 店鋪成員邀請確認制 + Sprint 97 遺漏修復（PRD §7.4/§8.2.3/§9.11，1 US，5 SP）：**US-001** 第六輪 PRD 全文掃描找到需產品判斷的模糊地帶——PRD schema 定義 `tenant_members.status`（INVITED/ACTIVE/REMOVED）且 API 表格寫「邀請成員」，但實際上 StoreOwner 新增員工單方直接生效，被邀請人無接受/拒絕機會。經 AskUserQuestion 向使用者確認後，改為兩階段邀請確認制：新增 migration `V69`（`status`/`invited_at` 欄位，既有紀錄回填 ACTIVE 不影響現況）；`TenantService.inviteMember`（原 `addMember`）建立 INVITED 紀錄，需被邀請人呼叫 `acceptInvite`/`declineInvite` 確認；`removeMember` 改為軟刪除（狀態轉 REMOVED，避免 REMOVED 成為永遠無法觸發的孤兒 enum 值）；曾被移除者重新邀請時更新既有紀錄而非新增（UNIQUE 約束）；新增 `GET /tenants/invites/my` 待確認邀請列表。**⚠️ 實作過程中意外發現並修復 Sprint 97 的遺漏**：追查 StoreOwner 測試帳號權限時發現 `AdminService.approveTenantApplication()` 雖建立了 `tenant_members` STORE_OWNER 紀錄，卻從未同步 `User.role`，而 `AuthService.login()` 的 JWT role claim 完全來自 `User.role`（非動態查 tenant_members）——導致 Sprint 97 核准的使用者重新登入後仍拿不到 StoreOwner 權限，實質上仍無法管理自己剛核准的店鋪，違反 PRD §7.4.1「新 Token 的 JWT Payload 內 roles 陣列將包含 StoreOwner」的明文要求。已補上 `user.setRole(STORE_OWNER)` 並強化 Sprint 97 的測試斷言。新增測試：`TenantServiceTest`+10、`TenantMemberInviteE2ETest`（新檔 4 tests，真實 DB+JWT，含完整迴路）、`AdminServiceTest`/`TenantApplicationReviewE2ETest` 補強斷言。全量回歸 `mvn verify -Pintegration-test` **1413 tests 0 fail**（單元 1023 + 整合 390），checkstyle/PMD 0 violations，`make validate-schema` 驗證新 migration 與 entity 對齊無漂移。誠實：`tenant_members.role` 欄位的 PRD 字面值（SELLER/HOST）與現行 `StoreRole` enum（STORE_MANAGER）不一致，非本輪範圍；邀請通知僅能主動查詢，無 Email/站內信推播（M09 Phase 2 上線前的既有替代方案模式） | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 97 | v2029.11.28-01 | - | 2026-07-19 | 開店申請 → Admin 審核 → StoreOwner 授權端到端斷點修復（PRD §7.4.1/§9.10.2/§12.1，1 US，8 SP）：**US-001** 重新全面比對 PRD 全文（含孤兒錯誤碼/零呼叫死碼/欄位從未賦值三項分析）發現目前為止最嚴重的缺口——`TenantController.createApplication()` 只寫入 `tenant_applications` 表，`AdminService.approveTenant/rejectTenant` 卻只操作既有 `tenants` 表記錄，兩者從未串接：96 個 Sprint 以來，「網友開店」這條 PRD 明訂的 P0 自助流程在提交申請後永遠卡住，Admin 端看不到、審不了，也不會產生真正的店鋪與 StoreOwner 授權，僅能靠工程師手動塞資料庫繞過（既有測試也全部如此繞過，因此存活 96 個 Sprint 未被發現）。保留既有 `reviewTenant`/`approveTenant`/`rejectTenant` 完全不動（邏輯正確，只是永遠等不到資料），新增專屬的 `TenantApplication` 審核端點：`GET /v2/admin/tenant-applications`、`POST .../{id}/approve`、`POST .../{id}/reject`。核准時依 PRD §7.4.1 逐字規格：建立 `Tenant`（ACTIVE，slug 比照既有 `PostService` 慣例產生並確保唯一）→ 初始化 6 個 Feature Toggle → 於 `tenant_members` 直接建立 STORE_OWNER 記錄（不透過需要已有 StoreOwner 才能呼叫的既有 `addMember()`）→ 回填 `TenantApplication.tenantId`/`status=APPROVED`。新增 `ErrorCode.E_2006/2007/2008`（找不到申請/狀態非待審核/訪客申請無法核准），不重用語意不準確的既有 `E_2000`/`E_2005`。新增測試：`AdminServiceTest`+8（`TenantApplicationReviewTests`）、`TenantApplicationReviewE2ETest`（新檔 5 tests，真實 DB+JWT，含**完整迴路驗證**：Buyer 申請→Admin 列表可見→核准→真正的 Tenant 已建立且 Buyer 已成為 StoreOwner）。全量回歸 `mvn verify -Pintegration-test` **1389 tests 0 fail**（單元 1003 + 整合 386），checkstyle/PMD 0 violations，`make validate-schema` 無漂移（無 migration）。誠實：`E_4091`（店鋪名稱已被使用）孤兒碼/`tenants.name` 唯一性檢查性質不同（防呆而非功能缺失），留待後續；Guest 申請目前仍無法核准（`E_2008`），PRD 未定義後續轉換路徑，維持現狀由 Admin 駁回 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 96 | v2029.11.14-01 | - | 2026-07-19 | M17 MAINTENANCE 狀態工作流程與 Admin MaintenanceWarnings（PRD §5.5.3/§18.9.4，Phase 1 規格，1 US，5 SP）：**US-001** 重新全面比對 PRD 全文（含孤兒錯誤碼分析）發現的第四個缺口——`RoomCalendar.RoomCalendarStatus.MAINTENANCE`、`Booking.statusFlags`（JSONB）欄位皆已存在但從未被賦值，`RoomCalendarService` 只有 `blockDateRange`/`unblockDateRange`（且兩者本身也是死碼、全庫零呼叫者），完全沒有 MAINTENANCE 標記/解除機制，Admin Dashboard 也無 `maintenance-warnings` 端點。新增 `RoomCalendarService.markMaintenance`/`unmarkMaintenance`：AVAILABLE/BLOCKED/BOOKED 皆可轉為 MAINTENANCE，原為 BOOKED 者保留 `bookingId` 並標記 `Booking.statusFlags.under_maintenance=true`；解除時保留 bookingId 者恢復 BOOKED（清除標記），否則恢復 AVAILABLE——依 PRD 較晚加入、明確標示 `[Specification]` 的狀態轉換矩陣判讀，非依早期敘述段字面（兩者表面矛盾，以矩陣+正式 Test Case 為準）。PRD 要求 MAINTENANCE 時 `price=NULL` 已因既有架構決策（`room_calendar.price` 整欄位停用，Sprint 45 AI-2406）天然滿足，無需額外程式碼。新增房東對外端點 `RoomCalendarController`（`POST`/`DELETE /v2/dashboard/rooms/{id}/maintenance`）——因發現既有姊妹方法從未對外暴露，一併補上最基本對外 API；新增 `AdminService.getMaintenanceWarnings()` + `GET /v2/admin/maintenance-warnings`（`SUPER_ADMIN`），透過 `RoomCalendarRepository.findByStatusAndBookingIdIsNotNull` 取得受影響 Booking（一次查詢天然去重多晚同一 Booking），入住日期今日或明日視為緊急（PRD 以「小時」描述但 Booking 僅有日期粒度，近似 24 小時窗口）。新增測試：`RoomCalendarServiceTest`+6、`AdminServiceTest`+4、`M17MaintenanceWorkflowIntegrationTest`（新檔 5 tests）。全量回歸 `mvn verify -Pintegration-test` **1370 tests 0 fail**（單元 989 + 整合 381），checkstyle/PMD 0 violations，`make validate-schema` 無漂移（無 migration，欄位早已存在）。誠實：`blockDateRange`/`unblockDateRange` 對外端點仍未補齊（PRD 未對 BLOCKED 獨立要求工作流程規格，留待未來）；M09 通知系統上線後 MaintenanceWarnings 應改主動推播，屬該模組後續工作 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 95 | v2029.10.31-01 | - | 2026-07-19 | M12 動態定價 pricing_rules 數量與衝突防護機制（PRD §5.5.1/§9.13/§18.9.1，Phase 1 Must Have，1 US，5 SP）：**US-001** 重新全面比對 PRD 全文發現的第三個缺口——`ErrorCode.E_4008`（"定價規則衝突"）已預留但全庫從未被拋出，是規格已定義、實作被遺漏的訊號；`PricingService.validateRuleRequest` 原僅檢查日期先後，完全沒有數量/唯一性/重疊檢查。新增 `PricingService.enforceRuleLimitAndConflict`：每 `room_listing_id` 最多 50 條 active 規則（超過拒絕 `E-4001 RULE_LIMIT_EXCEEDED`）；同 `rule_type` 且時間範圍重疊的既有 active 規則，未確認覆蓋（新增 `CreateRuleRequest.confirmOverride` 欄位）即拒絕（`E-4001`），確認後僅軟刪除重疊規則、新規則寫入為 active，不重疊的同類型規則允許共存不需確認（PRD 字面「同類型唯一」與 TC-PR-004 gap 回退案例對照後的工程判斷）。錯誤碼採 PRD 字面規格 `E-4001`（既有通用 400 驗證碼慣例），不用預留孤兒碼 `E_4008`。`PricingController.getCalendarPreview`（Sprint 83 既有「未來 90 天定價日曆預覽」端點）新增過去日期拒絕，落在既有端點而非另建 PRD 字面的 `/pricing-preview` 路徑（避免重複建置同功能端點）。新增測試：`PricingServiceTest` +4（`RuleCreationLimitAndConflictTests`）、`M12PricingIntegrationTest` +4（IT-M12-009~012）。全量回歸 `mvn verify -Pintegration-test` **1351 tests 0 fail**（單元 975 + 整合 376），checkstyle/PMD 0 violations，`make validate-schema` 無漂移（無 migration）。誠實：`room_calendar.price` 每日凌晨重算排程（PRD §5.5.2）與 `MANUAL_OVERRIDE` 類型是否納入同一組檢查，本輪判斷非高信心缺口/PRD 未明確要求，留待未來評估 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 94 | v2029.10.17-01 | - | 2026-07-18 | 會員資料 Export + 帳戶刪除（AI-2428，PRD §1.5.1，Phase 2 會員權利，1 US，8 SP）：**US-001** Sprint 93 全面比對 PRD 發現的第二個缺口，先盤點 User 相關 entity 影響範圍，經使用者拍板三項業務決策——僅 BUYER 可自助刪除（StoreOwner/Admin 需另案處理，避免商店孤兒化與管理者真空）、有未結案訂單/訂房時封鎖刪除、交易快照個資（`Order.shippingRecipientName/Phone`、`Booking.guestName/Phone/Email`）保留不動記錄為已知限制。新增 `UserPrivacyService`（`exportMyData`/`deleteMyAccount`）+ `UserDataExportResponse`；`GET /v2/auth/me/data-export`、`DELETE /v2/auth/me`。匿名化沿用既有 `User.status` 慣例（改 `"DELETED"` 即被既有登入/refresh 檢查擋下，不需改認證邏輯）；email 改寫為 `deleted-{uuid}@anonymized.local` 釋放 unique 約束；`Address`/`OAuthAccount` 直接刪除，其餘關聯（訂單/評價/聊天等）不刪除，因作者顯示為即時查詢 `User.fullName`，User 匿名化後自動顯示「已刪除的使用者」。新增 `ErrorCode.E_1009`/`E_1010`。新增測試：`UserPrivacyServiceTest`（6 tests）+ `AuthControllerE2ETest`（+3 tests，累計 13）。全量回歸 `mvn verify -Pintegration-test` 詳見下方統計，checkstyle/PMD 0 violations，`make validate-schema` 無漂移（無 migration）。誠實：聊天/客服工單完整訊息串未納入匯出範圍（僅摘要，因無 `findBySenderId` 查詢方法，需求不明確前不預先擴充）；StoreOwner/Admin 角色的刪除需求留待未來獨立評估 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 93 | v2029.10.03-01 | - | 2026-07-18 | API 限流機制（AI-2427，PRD §3.3/§13.4/§16.4.2 Phase 1 已凍結規格，1 US，8 SP）：**US-001** 全面重新比對 PRD v1.0 Final 全文（非僅既有追蹤文件）發現「每租戶 100 req/min Token Bucket 限流」自 92 個 Sprint 以來從未落地，也從未被任何追蹤文件記錄；新增 `RateLimitFilter`（`OncePerRequestFilter`，本專案第一支 Redis Lua script，原子讀取-補充-扣除 token bucket，容量 100/每 60 秒補滿）掛載於 `SecurityConfig.addFilterAfter(rateLimitFilter, TenantContextFilter.class)`；複用既有已預留但從未使用的 `ErrorCode.E_9904`（PRD 原文寫的 `E-6001` 與既有付款錯誤碼撞碼，不採用）；CORS `exposedHeaders` 新增 4 個限流 header。限流維度僅「每租戶」（PRD 唯一明確要求）；`/v2/auth/**`/`/actuator/**` 排除（無租戶身分/非業務流量）；Redis 故障 fail-open；刻意不用 FeatureToggle 包裝（避免預設對所有既有租戶失效，違反 Phase 1「全租戶適用」要求）。新增 `RateLimitFilterTest.java`（5 tests，Mockito 模擬）+ `RateLimitFilterIntegrationTest.java`（2 tests，真 Redis，刻意不用 `IntegrationTestConfiguration` 因其將 Redis 標為 `@Primary` mock 會測不到真實 Lua script 行為）。驗證：因異動核心 Security filter chain（比照 DEF-038 前例），執行完整 `mvn verify -Pintegration-test` 全量回歸、checkstyle/PMD 0 violations、`make validate-schema` 無漂移（無 migration）。誠實：PRD §16.4.2 定義的巢狀錯誤 body 格式（`error:{...,requestId,...}`）與現行扁平 `ApiResponse` 有落差，依既有慣例優先未改動格式，落差記錄待 PRD Errata；另一同批發現的缺口 A2（會員資料 Export+帳戶刪除）需先補規格，留待下一輪 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 79 | v2028.11.04-01 | - | 2026-07-06 | 多 Sprint 測試強化計劃最後一輪（`IdempotencyService`/`FeatureToggleService`，3 US，5 SP）：**US-001（1 SP）** 探查兩個 Service（`IdempotencyService` 6 個 public 方法、`FeatureToggleService` 2 個 public 方法），確認擁有權/租戶檢查現況；**US-002（3 SP）** 修復 `DEF-039`（`IdempotencyService` Redis key 未做租戶/使用者範圍化，跨租戶重放相同 Idempotency-Key 可讀到他租戶已儲存的訂房回應，紅燈測試以真實 `HashMap` 模擬 Redis 語意證實後，比照既有 `RedisCartService.getCartKey` 前例新增 `buildKey()` helper 修復）+ `IdempotencyServiceTest.java`/`IdempotencyServiceTenantIsolationTest.java` 共 14 個測試；**US-003（1 SP）** `FeatureToggleServiceTest.java` 新建 7 個測試（探查確認乾淨，無缺口）。附加修正：清除 Sprint 77 遺留的 `NotificationServiceTest.java` checkstyle-test 未使用 import 違規（首次執行 `mvn verify -Pintegration-test` 才浮現，因 Sprint 77/78 依政策僅需 `mvn test`）。驗證：因修改生產程式碼，全量回歸（`mvn verify -Pintegration-test`）**1191 tests 0 fail**（單元 849 + 整合 342）、`make validate-schema` 無漂移；本 Sprint 無前端變動。**多 Sprint 測試強化計劃（源自 Sprint 66）至此全部完成**，累計 14 個 Sprint、補齊約 20 個 Service/模組測試覆蓋、發現並修復 12 個安全漏洞（`DEF-023/024/026/027/028/029/030/032/033/035/036/039`），另有 `DEF-025/031/034/037/038` 待業務/架構決策擱置（`DEF-038` 為 🔴 高優先級，建議下一輪優先處理）。誠實：`IdempotencyService` 的紅燈測試以真實 Redis 語意模擬（非固定 stub）才能重現跨租戶碰撞，比單純 mock 更具說服力 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 77 | v2028.10.07-01 | - | 2026-07-06 | 多 Sprint 測試強化計劃（`NotificationService` 系列，3 US，7 SP）：**US-001（1 SP）** 探查 `core/notification/` 全部 4 個 Service（`NotificationService`/`NotificationHistoryService`/`NotificationPreferenceService`/`NotificationTemplateService`，19 個 public 方法），確認擁有權/租戶檢查現況；**US-002（3 SP）** `NotificationServiceTest.java` 新建 18 個測試；**US-003（3 SP）** `NotificationTemplateServiceTest.java` 新建 20 個測試。與過去 7 個連續 Sprint（68/70/72/73/74/75/76）皆發現真實漏洞不同，**本 Sprint 確認無新的擁有權/租戶檢查缺口**，探查過程中發現架構層級疑慮 `DEF-038`（`TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任，可能影響過去 9 個既有 DEF 修復前提），已記錄交由使用者決策，不阻擋本 Sprint。驗證：**未修改任何生產程式碼**，依政策僅需 `mvn test`（全量 0 fail，含新增 38 個）、`make validate-schema` 無漂移；本 Sprint 無前端變動。誠實：曾誤判部分端點權限字串為死碼，經實測（`M09NotificationTemplateIntegrationTest`）推翻此推論 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 73 | v2028.08.12-01 | - | 2026-07-06 | `ReviewService` 測試強化 + 3 項安全問題處理（5 US，14 SP）：**US-001（8 SP）** `ReviewService` 7 個先前完全零覆蓋方法（`updateReview`/`deleteReview`/`markHelpful`/`markAsHandled`/`markAsUnhandled`/`getReviewsByHandlingStatus`/`getUserReviews`）新建 `ReviewServiceTest.java` 共 **18 個測試**；**US-002（2 SP）** 修復 `DEF-028`（`markAsHandled`/`markAsUnhandled` 跨租戶寫入 IDOR，紅燈測試證實後修復，新增 `checkReviewManagementAuthorization`/`isCurrentUserAdmin`）；**US-003（2 SP）** 修復 `DEF-029`（`getReviewsByHandlingStatus` 跨租戶讀取洩漏，新增 `ReviewRepository.findByIsHandledAndTenantId`）；**US-004（2 SP）** 修復 `DEF-030`（`getUserReviews` 匿名保護繞過，比照 `DEF-018` 改為 owner-or-admin）；**US-005（0 SP）** 記錄 `DEF-031`（`markHelpful` 重複投票、`createReview` 未驗證訂單歸屬，使用者決策擱置）。驗證：全量回歸（`mvn verify -Pintegration-test`）**1058 tests 0 fail**（單元 716 + 整合 342）、`make validate-schema` 無漂移；本 Sprint 無前端變動。誠實：動手前先探查範圍並主動以「誰可以呼叫、有無檢查資源歸屬」角度逐一審視 14 個方法，一次確認 3 項缺口並經使用者確認後才修復；紅燈測試撰寫過程中發現並修正 2 類 fixture 缺陷（`reviewType` 遺漏、stub 誤用導致巧合性失敗），確認乾淨紅燈才進行修復 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 72 | v2028.07.29-01 | - | 2026-07-06 | ERP 模組測試強化 + 2 項安全問題處理（3 US，13 SP）：**US-001（8 SP）** `SupplierService`/`StockMovementService`/`PurchaseOrderService`/`InventoryService`（先前完全零單元測試）新建 4 個測試檔案共 **50 個測試**；**US-002（2 SP）** 修復 `DEF-026`（`InventoryService.getInventoryBySku` 跨租戶讀取洩漏，紅燈測試證實後修復，新增 `InventoryRepository.findBySkuIdAndTenantId`）；**US-003（3 SP）** 驗證並修復 `DEF-027`（`PurchaseOrderService.createPurchaseOrder` 品項未驗證 listing 租戶歸屬，紅燈測試證實漏洞成立後比照既有 `DEF-017` 模式修復）。驗證：全量回歸（`mvn verify -Pintegration-test`）**1032 tests 0 fail**（單元 690 + 整合 342）、`make validate-schema` 無漂移；本 Sprint 無前端變動。誠實：動手前先探查範圍並經使用者確認可單一 Sprint 涵蓋，未拆分；US-002/US-003 皆先寫測試取得紅燈證據才修復 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 71 | v2028.07.15-01 | - | 2026-07-06 | 多 Sprint 測試強化計劃（恢復例行排程，1 US，8 SP）：**US-001（8 SP）** `RoomCalendarService`（先前零單元測試）新建 `RoomCalendarServiceTest.java` 17 個測試（含 `bookDateRange` idempotency 核心：同 bookingId 重複呼叫跳過、不同 bookingId 衝突擋 E_4001）；`BookingService.createBooking`（先前僅 E2E 間接涵蓋）新建 `BookingServiceCreateBookingTest.java` 12 個測試；`updateBooking` 日期變更流程新建 `BookingServiceUpdateDateChangeTest.java` 5 個測試。三檔合計新增 **34 個測試**，與 Sprint 68 `BookingServiceOwnershipTest`（擁有權檢查）互補不重複。驗證：**未修改任何生產程式碼**，依政策僅需 `mvn test`（`640 tests 0 fail`，含新增 34 個）、`make validate-schema` 無漂移；本 Sprint 無前端變動。誠實：撰寫測試時發現 `createBooking` 的 `idempotencyKey` 參數為死碼（真正 idempotency 在 Controller 層 `IdempotencyService`），登記 `DEF-025`（🟢 低優先級），使用者審閱後決定不清理 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 67 | v2028.05.20-01 | - | 2026-07-05 | 多 Sprint 測試強化計劃第二階段（1 US，8 SP）：**US-001（8 SP）** `PaymentStateService`（金流核心）新增 `PaymentStateServiceTest.java` 48 個 Mockito 單元測試，補齊 10 個方法（含 Stripe 退款/對帳）的正常/邊界/錯誤路徑覆蓋。驗證：後端單元 542 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 884 tests 0 fail**、`make validate-schema` 無漂移；本 Sprint 無前端變動、**未修改任何生產程式碼**。誠實：Sprint 66 規劃「10 方法零測試」措辭有落差（實際已有 11 個既有測試覆蓋 4 方法），本 Sprint 補的是真正缺口；撰寫測試時發現 booking 付款擁有權檢查缺口（IDOR 疑慮），登記 `DEF-023`（🔴 高優先級）待 PO 決策，未自行修改 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 66 | v2028.05.06-01 | - | 2026-07-05 | 多 Sprint 測試強化計劃第一階段（2 US，8 SP）：**US-001（3 SP）** 修正 `ProductService.getProducts` 關鍵字搜尋死碼（`keyword` 分支先前與無篩選分支完全相同，等同搜尋永遠失效）+ 新增 `ProductRepository.searchByTenantIdAndKeyword`；**US-002（5 SP）** `AuthServiceTest.java` 從 0 建立 16 個 Mockito 單元測試（register/login/refreshToken/logout/getCurrentUser）。驗證：後端單元 494 + 真 DB 整合（`mvn verify -Pintegration-test`）**836 tests 0 fail**、`make validate-schema` 無漂移；本 Sprint 無前端變動。誠實：`AuthServiceTest` 初版未使用 import 未被 pre-commit 攔截，全量 `mvn verify` 的 checkstyle-test execution 才抓到，已獨立 commit 修正 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 65 | v2028.04.22-01 | - | 2026-07-05 | 營收報表頁+granularity修正（2 US，8 SP）：**US-001（3 SP）** 修正 `getRevenueStats` granularity 死碼（WEEK/MONTH 真正分桶）；**US-002（5 SP）** `/dashboard/revenue` 報表頁（日期範圍+粒度切換）。驗證：後端單元 483 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 342 tests 0 fail**、`make validate-schema` 無漂移、前端 lint/tsc/build 0 error。誠實：刻意不呈現 categoryRevenue（後端空 stub） | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 64 | v2028.04.08-01 | - | 2026-07-05 | Admin 租戶/使用者列表真分頁化+篩選（2 US，8 SP）：**US-001（5 SP）** `getTenants` 改 Specification 動態篩選（status/keyword）+ 真資料庫分頁，前端 `admin/tenants/page.tsx` 改伺服器端篩選；**US-002（3 SP）** `getUsers` 同模式（+tenantId/role）。驗證：後端單元 481 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 342 tests 0 fail**、`make validate-schema` 無漂移、前端 lint/tsc/build 0 error。誠實：真正問題是分頁機制本身失效（findAll() 取全部資料），比原候選描述更嚴重 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 63 | v2028.03.25-01 | - | 2026-07-05 | FAQ 後台管理頁面（2 US，8 SP，純前端）：**US-001（5 SP）** `dashboard/faq/page.tsx` 文章列表（分類篩選/關鍵字高亮搜尋/置頂/分頁/刪除）；**US-002（3 SP）** `dashboard/faq/categories/page.tsx` 分類管理（CRUD+統計）；新增 `services/faq.ts` 封裝完整 API。驗證：前端 lint/tsc/build 0 error，新路由成功產出；後端無變動。誠實：規劃前發現 FAQ 所有端點皆要求 faq:read 權限，屬內部後台功能非公開頁面，依此調整實作方向；未重跑後端全量回歸（無後端異動） | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 62 | v2028.03.11-01 | - | 2026-07-05 | M18 知識管理/FAQ 測試防護網補強（2 US，8 SP）：**US-001（5 SP）** `KnowledgeBaseServiceTest` 從 0 建立 24 測試（含版本控制邏輯）；**US-002（3 SP）** `FaqServiceTest`+12、`AnalyticsServiceTest`+2。驗證：後端單元 459 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 342 tests 0 fail**、`make validate-schema` 無漂移。誠實：PRODUCT_BACKLOG 候選 #9 描述已過時，重新盤點發現真正缺口是 KnowledgeBaseService 完全零覆蓋；FAQ 前端頁面缺失留待 Sprint 63 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 61 | v2028.02.26-01 | - | 2026-07-04 | M13/M14 後台管理深化（2 US，8 SP）：**US-001（5 SP）** `GET /v2/admin/audit-logs`（DEF-016 後續，Specification 動態篩選）+ 前端 `admin/audit-logs/page.tsx`；**US-002（3 SP）** `SellerDashboardServiceTest` 功能測試補強。驗證：後端單元 439 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 342 tests 0 fail**、`make validate-schema` 無漂移、前端 lint/tsc/build 0 error。誠實：原規劃 JPQL 動態篩選寫法因 PostgreSQL 型別推斷限制改用 Specification；M01 ES 因未核准 Docker image + RICE 最低排除本 Sprint | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 60 | v2028.02.12-01 | - | 2026-07-04 | 全站 BusinessException 英文訊息中文化（AI-2418，8 SP）：**US-001** `ErrorCode.java` 130 常數中文化；`BusinessException` 新增 `getUserMessage()`（隱藏動態英文細節）；`GlobalExceptionHandler` 改用 + 7 處硬編碼字串中文化；4 處測試斷言更新。驗證：後端單元 422 + 真 DB 整合（`mvn verify -Pintegration-test`）**全量 890 tests 0 fail**、`make validate-schema` 無漂移。誠實：不改 383 個呼叫點的動態英文細節本身，僅不外洩前端 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 59 | v2028.01.29-01 | - | 2026-07-04 | 退款路徑整合評估（AI-2417，Spike，3 SP）：**US-001** 產出 `REFUND_PATH_CONSOLIDATION_ASSESSMENT.md`——確認 `PaymentService`/`PaymentStateService` 平行重疊源自 Sprint 49 金流計畫只涵蓋 Order、從未觸及 Booking 的既定範圍；兩條退款路徑目前皆無前端呼叫端。**不改 production code**。誠實：純評估，建議維持現狀 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 58 | v2028.01.15-01 | - | 2026-07-04 | Availability reason 錯誤碼化（AI-2408，2 SP）：**US-001** `BookingDto.AvailabilityReasonCode` enum + `checkAvailability` 三處改用 code + 前端 `ListingDetail.tsx` 中文對照表；不導入 i18n 框架（全站純中文，工程範圍判斷）。驗證：後端單元 515 tests 0 fail + 真 DB 整合 422 tests 0 fail、`make validate-schema` 無漂移、前端 tsc/eslint/build 0 error。誠實：只碼化 availability 欄位，全站 BusinessException 英文訊息不在範圍 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 57 | v2028.01.01-01 | - | 2026-07-04 | 開放窗清除機制（AI-2202f，2 SP）：**US-001** `RoomService.clearOpenWindow` + `DELETE /v2/rooms/{listingId}/open-window` 端點（比照 `CartController.clearCart` 模式）+ 前端 `RoomForm.tsx` 清除按鈕。驗證：後端單元 515 tests 0 fail（新增 `RoomServiceTest`）+ 真 DB 整合 422 tests 0 fail、`make validate-schema` 無漂移（schema-free）、前端 build 0 error。誠實：機制選擇為工程設計決策非業務語意，未徵詢 PO | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 56 | v2027.12.18-01 | - | 2026-07-04 | 部分退款——任意金額，運費不退（AI-2415，後端聚焦，5 SP）：**US-001** V63 migration `payments` 加 `refunded_amount`；`PaymentStatus` 加 `PARTIALLY_REFUNDED`；`PaymentStateService.refundOrderPayment` 擴充 `amount` 參數（PO 決策：任意金額粒度、運費不退），累計退款金額達全額才轉終態。驗證：後端單元 512 tests 0 fail（新增 4 + 更新 2）+ 真 DB 整合 419 tests 0 fail、`make validate-schema` 無漂移（V63）。誠實：只修真 Stripe 退款路徑，未動平行純 Mock 路徑；webhook 仍假設全額 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 55 | v2027.12.04-01 | - | 2026-07-04 | 定價計算器統一評估（AI-2409，Spike，3 SP）：**US-001** 產出 `PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`——探勘 ROOM/PRODUCT 兩套計算器分歧，確認已對齊三型別（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND）僅程式碼重複、stay-based 三型別（EARLY_BIRD/LONG_STAY/LAST_MINUTE）對 PRODUCT 無自然語意；發現靜默 gating 略過落差但因無 PRODUCT 定價規則管理 UI 實際曝險低。**不改 production code**，無需回歸測試。誠實：純評估，建議選項非急迫 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 54 | v2027.11.20-01 | - | 2026-07-04 | 定價規則選取語意修正（AI-2407，後端聚焦，6 SP）：**US-001** `PricingRuleRepository.findActiveRulesForDateRange` JPQL containment→overlap 修正部分晚數規則漏套；**US-002** `calculatePrice`/`getEffectivePrice` 排序 Comparator 補 `createdAt` tie-break（PO 決策後建立者優先，免 migration）、修正既有空斷言測試 UT-M12-009。驗證：後端單元 508 tests 0 fail（含 UT-M12-019 新增）+ 真 DB 整合 415 tests 0 fail（含 API-M06-017 新增）、`make validate-schema` 無漂移（schema-free）。誠實：只修查詢語意與排序次鍵，計價核心邏輯不動 | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 53 | v2027.11.06-01 | - | 2026-07-04 | 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding（AI-2413，後端聚焦，8 SP）：**US-001+US-002** V62 migration tenants 加 4 個 Connect 欄位；StripePaymentGateway 新增 createConnectAccount/createAccountLink/getConnectAccountStatus；TenantStripeConnectService（onboarding 發起/複用/狀態查詢，toggle 保護）；SellerDashboardController onboarding/status 端點；PaymentWebhookService 擴充 account.updated dispatch。驗證：後端單元 20（TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ **全量回歸 536 tests 0 fail**、validate-schema V62 無漂移。同時交付 AI-2414（STRIPE_PRODUCTION_CHECKLIST.md）+ AI-1903 部分（更新既有走查 checklist）。誠實：只做帳戶 onboarding，分潤另立 AI-2416；不含前端；只做 Express | ✅ 已 push（2026-09-01 確認 origin/main 已對齊 Sprint 99 收尾 commit 24decc8）|
| Sprint 52 | v2027.10.23-01 | - | 2026-07-03 | 真實金流 Phase C——退款真串接（AI-2412，後端聚焦，退款無前端 UI）：**US-001+US-002** StripePaymentGateway.processRefund 由 stub 改真 Refund.create（以 payment_intent 全額退款）；PaymentStateService.mockRefund 重構 refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService 加 charge.refunded 權威 REFUNDED（冪等+V60 去重）；createCheckoutSession 補 payment_intent_data.metadata.order_id（補 S51 best-effort 缺口）；V61 payments 加 stripe_refund_id。驗證：後端單元 19（TC-S006 Refund + service 退款 005~007 + UT-WH-006 charge.refunded）+ 真 DB 整合 21（mock/Phase A/B 不退步）、validate-schema V61 無漂移、validate-e2e **54 passed/0 fail**（持平）。誠實：只做全額退款（partial 另立 AI-2415）；測試不打真 Stripe；⚠️ V61 schema。**真實金流付款閉環完整**（付款+權威狀態+退款皆真實）| ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 51 | v2027.10.09-01 | - | 2026-07-03 | 真實金流 Phase B——webhook 事件驅動權威狀態（AI-2411，後端聚焦）：**US-001+US-002** 補 Phase A「買家未回跳」缺口——PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID【權威，未回跳也 PAID】、payment_intent.payment_failed→FAILED、未知→記錄不 dispatch）；StripeWebhookController 驗簽後委派、一律回 2xx（避免無限重送）；PaymentStateService 抽 markStripePaymentSucceeded/Failed 共用核心（回跳 Phase A 與 webhook 雙路徑一致）；V60 processed_stripe_events 事件去重表 + event id 去重；雙層冪等（去重+狀態轉移）。驗證：後端單元 9（PaymentWebhookServiceTest 5 UT-WH-001~005 + service 4）+ 真 DB 整合 21（mock/Phase A 不退步）、validate-schema V60 無漂移、validate-e2e **54 passed/0 fail**（持平，後端聚焦）。誠實：只做付款成功/失敗（退款留 Phase C）；失敗路徑 best-effort（pi 惰性）；測試模式跳驗簽，生產須配 secret + 端點；⚠️ V60 schema | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 50 | v2027.09.25-01 | - | 2026-07-03 | 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）：**後端 Checkout Session + gateway 接線 + toggle + V59**(AI-2410，US-001，承 S49 評估 + PO 拍板；V59 payments 加 stripe_session_id/payment_intent_id/charge_id + STRIPE method + PROCESSING；接回孤兒 gateway 抽象層——StripePaymentGateway 補 createCheckoutSession【Session.create 平台代收】+ retrieveCheckoutSession；STRIPE_PAYMENT_ENABLED toggle【預設關=mock 不變】；PaymentStateService initiateStripeCheckout【建 PROCESSING+Session 回重導 url】+ confirmStripeCheckout【回跳 retrieve，paid→SUCCESS+Order PAID，冪等】；端點 /pay/checkout + /pay/checkout/return) + **前端 Checkout 重導**(AI-2410，US-002，orders/[id] 依 paymentProvider 分支「前往付款」重導 + success/cancel 頁；hosted Checkout 無需 @stripe；E2E-M11-013)。⚠️ **行為變更**：toggle 開啟走真 Stripe 收款。⚠️ **V59 打破 schema-free**。驗證：後端單元 9（WireMock TC-S004/005 + service 4）+ 真 DB 整合 25（mock 不退步）、validate-schema 無漂移、validate-e2e **54 passed/0 fail**（+1）。誠實：Phase A 僅回跳 retrieve、webhook 權威狀態留 Phase B(AI-2411)；平台代收分帳留 Phase D；測試以 WireMock 不打真 Stripe | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 49 | v2027.09.11-01 | - | 2026-07-03 | 真實金流評估——決策先行 spike（backlog #10）：**US-001+US-002 產出 PAYMENT_INTEGRATION_ASSESSMENT.md**（M12 收官後轉入平台變現關鍵評估，不寫 production code、無 schema）。揭穿「Stripe 已整合」假象——**兩套並行付款程式碼**：上線純 Mock（PaymentService/PaymentStateService）+ 孤兒 Gateway 抽象層（PaymentGatewayFactory/StripePaymentGateway 無人注入，S14/S21 遺留死碼）。real/stub/missing 速查表（真實：Stripe SDK 24.3.0/createPaymentIntent/webhook 驗簽/金鑰設定/WireMock；stub：confirm/refund/getStatus/webhook 事件處理；缺：gateway 接主流程/Stripe DB 欄位/非同步對帳/前端 Stripe.js/分帳提現）。分階段路線 Phase A 卡片 MVP→B webhook→C 退款→D 分帳；§mock↔real toggle + §Connect vs 手動分帳 + §Stripe.js 選型（Checkout PCI SAQ-A）+ §待 PO 決策 6 項 + §後續實作 US（AI-2410~2413）。實作（13 SP+外部依賴）待決策另立 | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 48 | v2027.08.28-01 | - | 2026-07-03 | PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官：**後端 PRODUCT 計價支援漲價 + 閘門放寬**(AI-2406c，US-001，承 S46 界線 PRODUCT 另立；破**兩道閘門**——閘門 2【結構性】applyProductRule 由 discount-only 擴充支援漲價型 MANUAL_OVERRIDE price/SEASONAL multiplier/WEEKDAY_WEEKEND weekendMultiplier【對齊 ROOM config key，保留 discountPercent 向後相容】、閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse 加 priceAdjustmentType + 有號 discountAmount；下單自動繼承【OrderService 未改】) + **前端購物車定價雙向顯示**(AI-2406c，US-002，cart/page 首次顯示 item 層級定價：折扣刪除線+綠標「省」/漲價不刪除線+橙標「加價」，兼補 S44 未顯示折扣；checkout 為 ROOM 訂房頁不 itemize 未改；E2E-M11-012)。⚠️ **行為變更**：toggle 開啟時 PRODUCT 漲價計入(PO 拍板)。驗證：後端單元 22 + 真 DB 整合 54（含 IT-EP-004 漲價）、validate-e2e **53 passed/0 fail**（+1）、schema 無漂移。**M12 進階定價全面收官**(ROOM+PRODUCT 折扣+漲價皆顯示=收費)。schema-free(V58)。誠實：SP 初估 3→探勘修正 8（兩道閘門）、PRODUCT/ROOM 兩套計算器對齊 key 未合併(另立 AI-2409) | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 47 | v2027.08.14-01 | - | 2026-07-03 | 開放窗語意實作——區分「未開放 vs 可訂」：**後端開放窗三層 + migration V58**(AI-2202e，US-001，承 S45 決策 PO 拍板選項 A + 追加滾動視窗 + host UI；rooms 加 open_until_date DATE + booking_window_days INT【皆 nullable、既有列 NULL=無限制、backfill 免異動、ADD COLUMN IF NOT EXISTS 冪等】；抽 Room.resolveOpenUntil【取最早生效 min】三層一律呼叫；getCalendar 超窗無記錄日補 NOT_OPEN【計算產物非持久化，抽 appendNotOpenDays 控 NPath】、checkAvailability 超窗 available=false+原因、createBooking+reschedule 超窗擋訂 E-3002【422】；RoomCalendarService 未改【擋在 caller 層更精準】；兩欄 NULL 維持現狀) + **前端開放窗顯示 + 賣家設定**(AI-2202e，US-002，MonthCalendar NOT_OPEN 灰底禁選不刪除線+data-not-open+圖例；ListingDetail 沿用既有不可訂路徑；booking.ts type；room.ts+RoomForm 雙欄位；E2E-ROOM-10/11)。⚠️ **V58 結束 S42~S46 連續零-migration**(PO 已知悉)。驗證：後端單元 9 + 真 DB 整合 38（含 API-M06-016 三層一致）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**（+2 NOT_OPEN E2E）。誠實：只做 ROOM、NOT_OPEN 計算非持久化、部分更新無法清窗(另立 AI-2202f)、reason 英文字串(另立 AI-2408) | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 46 | v2027.07.31-01 | - | 2026-07-03 | 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）：**後端 ROOM 計價全面走 adjustedTotal 含漲價**(AI-2406b，US-001，承 S45 決策 PO 拍板選項 B；BookingService 放寬三處折扣閘門【tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal】使 availability/月曆/建單 totalAmount 三者一律含漲價乘數；保留 toggle 關短路+失敗降級【向後相容】；計算核心不動；PricingService 抽 resolveListingForPricing 優雅降級【無 Room fallback basePrice、null 回 4xx 非 NPE→500】；DTO 中性調整語意【discountAmount 改有號差額 正=折扣/負=加價，新增 priceAdjustmentType DISCOUNT/MARKUP/NONE】) + **前端漲價雙向顯示**(AI-2406b 前端，US-002，ListingDetail/MonthCalendar 折扣維持刪除線+綠 badge「省 X」、漲價改不刪除線+橙 badge「加價 X」；booking.ts 加 priceAdjustmentType；E2E-ROOM-08/09 漲價變體)。⚠️ **行為變更**：toggle 開啟時漲價規則開始計入訂房金額（PO 拍板）。驗證：後端單元 18 + 真 DB 整合 34 全過、validate-e2e **50 passed/0 fail**（+2 漲價 E2E）、schema 對齊。**無 schema 變動**(連續 S42~S46 零 migration)。誠實：只做 ROOM(PRODUCT 另立 AI-2406c)、bestRule priority/range 查詢落差記錄不修(另立 AI-2407)、E2E 編號順延 06/07→08/09 | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 45 | v2027.07.17-01 | - | 2026-07-02 | 定價區技術債收斂（清死碼 + 語意決策）：**定價機制統一**(AI-2406，US-001，揭穿「雙定價機制」實為死碼假象——`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk 零呼叫者、欄位恆 NULL；移除死碼 + BookingService 三處讀取移除死欄位 fallback 改直取 basePrice【行為等價，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估】；RoomCalendar.price 註解標記停用；確立 MANUAL_OVERRIDE 為唯一手動日價路徑；決策文件 PRICING_MECHANISM_UNIFICATION.md 就漲價計入 booking 提選項→PO 裁決另立 AI-2406b) + **開放窗語意評估**(AI-2202d，US-002，spike，CALENDAR_OPEN_WINDOW_ASSESSMENT.md 記錄三層硬語意 + 三選項比較【推薦 A open_until_date，需 migration】+ NULL 安全過渡→PO 拍板另立 AI-2202e)。驗證：後端單元 6 + 真 DB 整合 57 全過、validate-e2e **48 passed/0 fail**、schema 對齊。**無 schema 變動**(連續 S42~S45 零 migration)。誠實：決策密集項另立 AI-2406b/AI-2202e；@Deprecated=0 慣例（用註解非 annotation）| ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 44 | v2027.07.03-01 | - | 2026-07-02 | 完成 M12 進階定價全覆蓋：**PRODUCT 購物車/訂單折扣**(AI-2403，getCart 讀取重算 getEffectivePrice、訂單繼承、toggle+向後相容、CartItemResponse transient 折扣欄位) + **買家整月日曆每日折扣**(AI-2405b，getCalendar merge calculatePrice breakdown、MonthCalendar 原價刪除線、E2E-ROOM-07) + **Inter 字體自 host 離線化**(AI-2303，next/font/local + committed woff2，消 build 期 Google Fonts 依賴)。驗證：後端單元 23 + 真 DB 整合 71、validate-e2e **48 passed/0 fail**、schema 對齊。**無 schema 變動**(連續 S42~S44 零 migration)。M12 進階定價自此 ROOM+PRODUCT+買家顯示全覆蓋。誠實：schema-free(訂單不留原價欄位)、定價機制統一另立 AI-2406 | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 43 | v2027.06.19-01 | - | 2026-07-02 | M12 進階定價落地（早鳥/長住/末班車折扣真正生效於 ROOM）：**語意修正**(AI-2401，早鳥/末班車改「下單日 vs 入住日」+ config Number 防護) + **定價引擎接入 ROOM 計價鏈**(AI-2402，BookingService 注入 PricingService，toggle+向後相容+availability 回折扣明細，訂房金額與顯示一致) + **config 型別化編輯 UI**(AI-2404，動態子表單取代黑箱 {} + dashboard 入口) + **買家折扣顯示 + 賣家預覽**(AI-2405，折扣後價+原價刪除線+標籤 + 沿用 PricingCalendarPreview + E2E-ROOM-06)。驗證：後端單元 15 + 真 DB 整合 36 全過、validate-e2e **47 passed/0 fail**、schema 對齊。**無 schema 變動**(沿用 jsonb config)。誠實：只接 ROOM(PRODUCT/Cart 另立 AI-2403)、買家日曆每日折扣另立 AI-2405b | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 42 | v2027.06.05-01 | - | 2026-07-02 | 收尾技術債：**backend pre-commit 提速**(AI-2302，2 慢測 @Tag(slow) + `-DexcludedGroups=slow`，pre-push act 仍完整跑=零覆蓋損失，順帶移除 pre-commit 對 test DB 的依賴) + **整月日曆每日價格顯示**(AI-2202c Part A，純前端，basePrice fallback) + **E2E 硬等待清除**(DEF-022，5 檔 waitForTimeout→顯式等待+補斷言，保留 STOMP 例外)；連帶根治既有 flaky（auth helper 與 S37 共用 Header「註冊」連結碰撞→改 goto；原生 alert teardown→dialog 處理器）。本地驗證：validate-e2e **46 passed/0 fail**、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。**無 production code/schema 變動**（後端僅測試 @Tag）| ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 41 | v2027.05.22-01 | - | 2026-07-02 | S41 技術債徹底清償 + 整月日曆：**test DB↔act port 制度化**(AI-2301，validate-release 自動 test-db-down) + **E2E 登入 helper 完全統一**(AI-2101b，auth.ts + 重構 m10/m17-001~004) + **api.ts 端點契約清理**(AI-2202a，pricing base path + 移除死碼) + **整月日曆**(AI-2202b，read-only 後端 GET /v2/bookings/calendar + MonthCalendar 前端 + E2E-ROOM-05) + **買家閉環走查**(AI-1903，自動 validate-e2e 證據 + 手動 checklist，真人 live 走查殘留) + **CJK 字體評估**(DEF-021，決策維持系統堆疊)。本地各層驗證通過：validate-e2e **47 passed/0 fail**、schema 對齊、後端 Booking 18 tests 0 fail。read-only 無 schema 變動 | ✅ 已 push（累積 S41~S52，確認 origin/main 已同步 2026-07-04）|
| Sprint 40 | v2027.05.08-01 | - | 2026-07-02 | ROOM 可用性 UX 完成（含小幅後端）：**availability 端點修復**(AI-2201，@RequestBody→@RequestParam，read-only 無 DB；原 GET+body 瀏覽器不可呼叫) + **詳情頁 ROOM 即時可用性檢查**(選日期→可訂+總價 / 不可訂+原因，不可訂禁用加購) + availability E2E。**完整 make validate-release 通過**（後端 act 330 tests 0 fail + E2E 45 passed/0 failed）。無 DB/schema 變動 | ✅ 已 push（累積 S32~S40，已過完整守門；檢查點徵詢後 push）|
| Sprint 39 | v2027.04.24-01 | - | 2026-07-02 | ROOM 訂房閉環補完（補強既有閉環，非從零）：**booking service 抽取 + 訂房衝突優雅處理**(US-001+002，createBooking 抽取、日期衝突 409/E-4001 等給可讀提示、詳情頁 ROOM 日期驗證) + **ROOM 訂房閉環 E2E**(US-003 AI-2104，mock：詳情計價加購→checkout 建 booking→409 衝突) + **E2E 共用登入 helper 抽取**(US-004 AI-2101，waitForURL 收 DEF-022，收斂 4 檔 + 通知 flaky 修)。全棧 44 passed/0 failed。誠實：availability 端點 GET+body 不可用→免後端 reframe；無後端/DB 變動 | ✅ 已 push（累積 S32~S39，完整守門+徵詢後 push）|
| Sprint 38 | v2027.04.10-01 | - | 2026-07-02 | 買家體驗補完——商品詳情頁：**買家商品詳情頁**(AI-2103，/listings/[id]，(storefront) 公開 + 401 引導；PRODUCT 數量加購 + ROOM 日期計價加購 + 三態；listing service 補 getListingById/getListingPrice；cartEvents 使 Header 購物車數即時更新；首頁連結由評價頁改導向詳情頁) + **有資料 E2E**(AI-1905，page.route mock 免 seed：首頁網格+分頁+詳情導覽+加購+401/404)。全棧 41 passed/0 failed。無後端/DB 變動 | ✅ 已 push（累積 S32~S38，完整守門+徵詢後 push） |
| Sprint 37 | v2027.03.27-01 | - | 2026-07-02 | 買家頁全頁套版 + 清償 push 債：**買家頁全頁套用共用賣場版型**(AI-1901，新增 (auth)/layout.tsx 承載共用 Header/Footer、10 頁移除自包 nav 改用 StorefrontShell、Header 加 auth-aware 帳號選單 useSyncExternalStore) + **m15 flaky 修復**(AI-2001，dialog 處理器+明確等待，解鎖 release 守門) + 買家頁版型一致性 E2E(BUYER-04/05)；順帶修 E2E 登入 helper SearchBar submit 碰撞 + secret 掃描器誤報收緊。全棧 37 passed/0 failed。無後端/DB 變動 | ✅ 已 push（累積 S32~S37，完整守門+徵詢後 push；m15 阻礙已清） |
| Sprint 36 | v2027.03.13-01 | - | 2026-07-02 | 安全收尾 + 版型架構債償還：**DEF-019 物流/賣家側 IDOR 收尾**(createLogistics tenant-based + processOrderPayment user-based，活躍安全 DEF 歸零；順帶修好 S33 遺留 M07 5 失敗) + **DEF-020 版型 Shell route-group 架構重構**(layout + client 邊界下推 + URL 搜尋，at-homepage E2E 全綠) + AI-1907 home-error/重試 E2E(at-homepage 6 tests)；買家頁套版(AI-1901)/live 走查延 S37 | ✅ 已 push（累積 S32~S36，完整守門+徵詢後 push；含 m15 flaky 前置 AI-2001） |
| Sprint 35 | v2027.02.27-01 | - | 2026-07-01 | 前端賣場版型 + 首頁改版：**意象若水 RUOSHUI 設計稿套為全站共用版型**(TOP/Tools/Bottom 共用 + Content 分頁)、5 套色票主題、6 個 DS 元件(shadcn 重建)、共用 StorefrontShell、首頁接真實 /v2/listings + at-homepage E2E(4 tests)；DEF-019 物流賣家側 + 買家 live 走查延 S36 | ✅ 已 push（累積 S32~S35，完整守門+徵詢後 push） |
| Sprint 34 | v2027.02.13-01 | - | 2026-07-01 | 安全修復落地：**DEF-017 ERP 手動庫存租戶隔離清償**(AI-1701，歷時 S28→34 三度回退後落地：raw SQL 種 FIXED_TENANT_ID 租戶+null 安全檢查+IT-M16-307，乾淨 DB 43 tests 0 fail)；DEF-019 物流/賣家側 + 買家 live 走查延 S35 | ✅ 已 push（累積 S32~S34 共 8 commit，完整守門+徵詢後 push） |
| Sprint 33 | v2027.01.30-01 | - | 2026-07-01 | 安全修復收尾：DEF-019 訂單付款 IDOR 修復(AI-1702，getOrderPaymentState+pay/fail/refund，403)；DEF-017 三層根因完整診斷(NPE→403→FK，@GeneratedValue+FK)延 S34；DEF-019 物流/賣家側續 S34 | ✅ 已 push（累積 S32~S34，完整守門+徵詢後 push） |
| Sprint 32 | v2027.01.16-01 | - | 2026-07-01 | 安全修復 DEF-018 getOrder IDOR(AI-1601，403/E_1007，最小爆炸半徑) + 買家頁面 E2E 驗證(AI-1602，at-buyer-pages 30 passed)；揪出 DEF-019 付款物流 IDOR；DEF-017 二度驗證(修法正確缺 seeding)延 S33 | ✅ 已 push（累積 S32+S33，完整守門，徵詢後 push） |
| Sprint 31 | v2027.01.02-01 | - | 2026-07-01 | 買家閉環後端驗證(BuyerJourney E2E) + roomTitle 填充(AI-1502) + DEF-016 audit 持久化(V57)；揪出 getOrder IDOR(DEF-018) | ✅ 已 push（S29+30+31 累積批次 fb221f3） |
| Sprint 30 | v2026.12.19-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環完成(純前端)：M06 預訂管理 + M08 評價(提交/列表) + M11 物流追蹤(訂單詳情) | ✅ 已 push（S29+30+31 累積批次） |
| Sprint 29 | v2026.12.05-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環起手(純前端)：M05 訂單前端(列表/詳情/取消/狀態日誌) + M09 通知收件匣 + M07 Mock 付款(訂單詳情整合) | ✅ 已 push（S29+30+31 累積批次） |
| Sprint 28 | v2026.11.21-01 | - | 2026-07-01 | 品質硬化(M14/M18 測試 0→13) + 商家營運總覽儀表板(營收/訂單/趨勢) + US-004 調查(ERP 租戶隔離 no-op→DEF-017、audit→DEF-016) | ✅ 已 push（本地優先驗證） |
| Sprint 27 | v2026.11.07-01 | - | 2026-07-01 | DEF-013 通知端到端斷鏈修復 + DEF-015 前端離線 build + pre-push v5 完整守門實證 + 產品方向決策(PRODUCT_BACKLOG) | ✅ 已 push（本地優先驗證；活躍 DEF 歸零） |
| Sprint 26 | v2026.10.24-01 | - | 2026-07-01 | 本地優先 CI（停用雲端自動 CI）+ WS/即時 DoD 制度化 + M11 取消技術債清償(DEF-010/011) + 廣播 conversationId(DEF-012) + Logistics jsonb 統一(DEF-009) + e2e strict 守門 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 25 | v2026.10.10-01 | - | 2026-06-30 | schema 漂移守門關卡 + Conversation tenant_id(V56) + M10 WebSocket 前端整合(live E2E) + SSH keepalive + M11 取消規則確認 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 24 | v2026.09.26-01 | - | 2026-06-29 | M10 WebSocket STOMP 即時訊息 + M13 Redis TTL + 整合測試標準化 + E2E schema 修復(V48~V55) | ✅ |
| Sprint 23 | v2026.09.12-01 | - | 2026-06-27 | M10 IM Migration(V45/V46) + M11 物流履約整合 + 運費接入 + M13 @Cacheable | ✅ |
| Sprint 22 | v2026.08.29-01 | - | 2026-06-27 | 詳見 RELEASE_NOTES_v2026.08.29-01.md | ✅ |
| Sprint 21 | v2026.08.15-01 | - | 2026-06-27 | MQ 一致性 + Stripe Phase 3 + M11 Provider + M14 統計 + 運費模板 | ✅ |
| Sprint 20 | v2026.08.01-01 | - | 2026-06-26 | MQ 技術債清零 + @Deprecated 清零 + M09 通知歷史 + M08 評分統計 | ✅ |
| Sprint 19 | v2026.07.18-01 | - | 2026-06-25 | 詳見 RELEASE_NOTES_v2026.07.18-01.md | ✅ |
| Sprint 18 | v2026.07.03-01 | - | 2026-06-24 | 詳見 RELEASE_NOTES_v2026.07.03-01.md | ✅ |
| Sprint 17 | v2026.06.19-01 | #17 | 2026-06-10 | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 | ✅ |
| Sprint 16 | v2026.06.06-01 | #15 | 2026-06-06 | M08 評價多圖 + M07 結算強化 + Pre-commit | ✅ |
| Sprint 15 | v2026.06.04-01 | #13, #14 | 2026-06-04 | M08 商家回覆 + 評價標記 | ✅ |
| Sprint 14 | v2026.05.16-02 | #12 | 2026-05-16 | M09 MQ 通知 + M07 Stripe 整合 | ✅ |
| Sprint 13 | v2026.05.16-01 | #11 | 2026-05-16 | M18 知識庫版本控制 + 排程發布 | ✅ |
| Sprint 12 | v2026.05.15-01 | #10 | 2026-05-15 | M18 知識管理 + M07 金流準備 + M09 通知模板 | ✅ |
| Sprint 11 | v2026.05.12-01 | #7 | 2026-05-12 | CI/CD Pipeline 修復 | ✅ |
| Sprint 10 | v2026.05.09-01 | #5 | 2026-05-09 | M16 ERP Backend | ✅ |
| Sprint 9 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 8 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 1-7 | - | - | - | 初期開發階段 | ⚠️ 無記錄 |

---

## 📊 Release 統計

| 項目 | 數值 |
|------|------|
| 建立 Release Tag 次數 | 75 (Sprint 10~104 中已建 row 者；Sprint 8-9 未正式 Release) |
| 已 push（已 Release） | 74 (2026-09-01 Sprint 104 開工核對 `git rev-list --left-right --count origin/main...HEAD` = `0 0`，確認 Sprint 103 的 `3b19790` 已 push) |
| 待 push（Tag 已建、尚未 push） | 1 (Sprint 104) |
| 跳過 Release 次數 | 2 (Sprint 8-9) |
| 最近一次 Release Tag | v2030.02.20-01 (Sprint 104，⏳ 待 push) |
| 最近一次已 push Release | v2030.02.06-01 (Sprint 103，2026-09-01，雲端 CI run 33511000120 全綠) |
| 最近一次跳過 | Sprint 8-9 |
| 連續 Release Tag 開始 | Sprint 10（⚠️ **連續性已中斷**：Sprint 68/69/70/74/75/76/78/80~92 共 20 個 Sprint 未建 row，該期間 tracker 未同步維護，非未交付） |

---

## ⏳ Sprint 35 Release（最新）

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.02.27-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 前端賣場版型 + 首頁改版：意象若水 RUOSHUI 設計稿套為全站共用版型（TOP/Tools/Bottom 共用 + Content 分頁）、5 套色票主題、6 個 DS 元件、共用 StorefrontShell、首頁接真實 `/v2/listings` + at-homepage E2E（4 tests） |
| **測試狀態** | 前端 build/type-check/lint 0 error；at-homepage E2E 4 tests 全綠；全棧 33 passed（唯一失敗為既有 flaky m15，非本 Sprint）；活躍 DEF=1（DEF-019 物流賣家側） |
| **Flyway** | V57（無新 migration；純前端變更） |
| **Release Notes** | [RELEASE_NOTES_v2027.02.27-01.md](../08_deployment/RELEASE_NOTES_v2027.02.27-01.md) |
| **狀態** | ✅ 已 push（累積 S32+S33+S34+S35，完整守門 + 檢查點徵詢後 push） |

---

## ⏳ Sprint 34 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.02.13-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復落地：DEF-017 ERP 手動庫存租戶隔離清償（AI-1701，歷時 S28→34 三度回退後落地：raw SQL 種 FIXED_TENANT_ID 租戶 + null 安全檢查 + IT-M16-307，乾淨 DB 43 tests 0 fail）；DEF-019 物流/賣家側 + 買家 live 走查延 S35 |
| **測試狀態** | 乾淨 DB M16 43 tests 0 fail；catch(Exception)=0、@Deprecated=0；活躍 DEF=1（DEF-019 物流賣家側）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.02.13-01.md](../08_deployment/RELEASE_NOTES_v2027.02.13-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ⏳ Sprint 33 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.30-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復收尾：DEF-019 訂單付款 IDOR 修復（AI-1702，getOrderPaymentState + pay/fail/refund，403）；DEF-017 三層根因完整診斷（NPE→403→FK）延 S34 |
| **測試狀態** | `@Test` 690→691（付款越權 E2E）；catch(Exception)=0、@Deprecated=0；活躍 DEF=2（DEF-017/019 物流賣家側）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.30-01.md](../08_deployment/RELEASE_NOTES_v2027.01.30-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ⏳ Sprint 32 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.16-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復 DEF-018 getOrder IDOR（AI-1601，403/E_1007，最小爆炸半徑）+ 買家頁面 E2E 驗證（AI-1602，at-buyer-pages 30 passed）；揪出 DEF-019 付款物流 IDOR |
| **測試狀態** | US-001 +1（otherBuyerCannotGetOrder）、前端 e2e +3（buyer pages）；活躍 DEF=2（DEF-017/019）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.16-01.md](../08_deployment/RELEASE_NOTES_v2027.01.16-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ✅ Sprint 31 Release（最近一次已 push）

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.02-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 買家閉環後端驗證（BuyerOrderJourneyE2ETest）+ BookingListResponse roomTitle 填充（AI-1502）+ DEF-016 Admin audit log 持久化（AuditLog + V57）；US-002 揪出 getOrder IDOR（DEF-018） |
| **測試狀態** | 後端 `@Test` 689（+6）, catch(Exception)=0, @Deprecated=0, make validate-schema 無漂移, 活躍 DEF=2（DEF-017/018） |
| **Flyway** | **V57**（audit_log） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.02-01.md](../08_deployment/RELEASE_NOTES_v2027.01.02-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ⏳ Sprint 30 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.19-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環**完成**（純前端）：M06 預訂管理（我的預訂/詳情/取消）+ M08 評價（提交/列表/評分統計）+ M11 物流追蹤（訂單詳情軌跡時間軸） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.19-01.md](../08_deployment/RELEASE_NOTES_v2026.12.19-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ⏳ Sprint 29 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.05-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環起手（純前端）：M05 訂單前端（列表/詳情/取消/狀態日誌）+ M09 通知收件匣（未讀/篩選/已讀/刪除）+ M07 Mock 付款（訂單詳情整合，CREATED→PAID） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.05-01.md](../08_deployment/RELEASE_NOTES_v2026.12.05-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ✅ Sprint 28 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.11.21-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 品質硬化（M14 Analytics/M18 FAQ 測試 0→13）+ 商家營運總覽儀表板（營收/訂單/趨勢）+ US-004 調查（ERP 租戶隔離 no-op→DEF-017、audit→DEF-016） |
| **測試狀態** | `@Test` 靜態計數 683（+13）, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2026.11.21-01.md](../08_deployment/RELEASE_NOTES_v2026.11.21-01.md) |
| **狀態** | ✅ 完成 |

---

## 🔴 Sprint 22 Release 規劃

**目標**: Sprint 22 結束（2026-08-29）執行 Release，Tag = `v2026.08.29-01`

### Release 前檢查清單

| 檢查項目 | 標準 | 狀態 |
|---------|------|------|
| 所有 US 完成 | AC 100% 達成 | ⏳ |
| mvn verify 100% 通過 | 0 Failures, 0 Errors | ⏳ |
| Checkstyle | 0 violations | ⏳ |
| Sprint 22 Review 文件 | SPRINT_22_REVIEW.md 建立 | ⏳ |
| Sprint 22 Retro 文件 | SPRINT_22_RETRO.md 建立 | ⏳ |
| RELEASE_TRACKER.md 更新 | 新增 Sprint 22 記錄 | ⏳ |

---

## 📝 Release 歷史詳細資料

### Sprint 16 (v2026.06.06-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #15 |
| **合併日期** | 2026-06-06 |
| **主要功能** | M08 評價多圖 (9張上限) + M07 結算強化 + Pre-commit Hook |
| **架構異動** | JPA 衝突修復 (media.MediaAsset vs cms.MediaAsset) |
| **技術債** | 83 個既有測試 bug (需 Sprint 17 修復) |
| **測試覆蓋** | 新增 34 個測試，100% 通過 |

### Sprint 17 (v2026.06.19-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #17 |
| **合併日期** | 2026-06-10 |
| **主要功能** | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 + 83個測試 bug 修復 |
| **架構異動** | V38/V39 Migration 建立、sellerReply 欄位移除 |
| **技術債清理** | 83個測試 bug 歸零、已棄用方法移除 |
| **流程改進** | CI/CD Pipeline 優化、Artifact 配額管理改善 |
| **測試覆蓋** | 532 tests, 0 Failures, 0 Errors (100%) |
| **Release** | ✅ 已建立 (v2026.06.19-01) |

### Sprint 15 (v2026.06.04-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #13, #14 (CI hotfix) |
| **合併日期** | 2026-06-04 |
| **主要功能** | M08 商家回覆評價 + 評價標記功能 |
| **重要變更** | ReviewReply Entity 建立 (1:1 with Review) |
| **取消功能** | sellerReply 欄位廢除 (改用 ReviewReply) |

### Sprint 14 (v2026.05.16-02)

| 欄位 | 內容 |
|------|------|
| **PR** | #12 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M09 MQ 通知 + M07 Stripe 整合 + M18 FAQ 進階 |

### Sprint 13 (v2026.05.16-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #11 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M18 知識庫版本控制 + 排程發布 + M08 預訂評價系統 |

### Sprint 12 (v2026.05.15-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #10 |
| **合併日期** | 2026-05-15 |
| **主要功能** | M18 知識管理 + M07 金流準備 + M09 通知模板 |

### Sprint 11 (v2026.05.12-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #7 |
| **合併日期** | 2026-05-12 |
| **主要功能** | CI/CD Pipeline 修復 |

### Sprint 10 (v2026.05.09-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #5 |
| **合併日期** | 2026-05-09 |
| **主要功能** | M16 ERP Backend 完成 |

---

## ⚠️ 未 Release 的 Sprint

### Sprint 8-9 問題說明

> **歷史問題**: Sprint 8 和 Sprint 9 沒有執行正式的 Release 流程，導致：
> - M15 CMS Backend 功能未能及時進入 Production
> - 程式碼累積在 develop 分支
> - 技術債逐漸累積

### 補救措施

1. ✅ Sprint 10 時已將 M15 CMS 程式碼带入 main
2. ✅ 後續 Sprint 都有執行 Release 流程
3. ⚠️ 建議建立文件記錄 Sprint 8-9 的功能事實上已進入 Production

---

## 📈 Release 頻率趨勢

```
Sprint 10  → ✅ Release (v2026.05.09-01)
Sprint 11  → ✅ Release (v2026.05.12-01)
Sprint 12  → ✅ Release (v2026.05.15-01)
Sprint 13  → ✅ Release (v2026.05.16-01)
Sprint 14  → ✅ Release (v2026.05.16-02)
Sprint 15  → ✅ Release (v2026.06.04-01)
Sprint 16  → ✅ Release (v2026.06.06-01)
Sprint 17  → ✅ Release (v2026.06.19-01)
Sprint 18  → ✅ Release (v2026.07.03-01)
Sprint 19  → ✅ Release (v2026.07.18-01)
Sprint 20  → ✅ Release (v2026.08.01-01)
Sprint 21  → ✅ Release (v2026.08.15-01)
Sprint 22  → ✅ Release (v2026.08.29-01)
Sprint 23  → ✅ Release (v2026.09.12-01)
Sprint 24  → ✅ Release (v2026.09.26-01)
Sprint 25  → ✅ Release (v2026.10.10-01)
Sprint 26  → ✅ Release (v2026.10.24-01)
Sprint 27  → ✅ Release (v2026.11.07-01)
Sprint 28  → ✅ Release (v2026.11.21-01)
Sprint 29  → ✅ Release (v2026.12.05-01)  [已 push，S29+30+31 累積批次]
Sprint 30  → ✅ Release (v2026.12.19-01)  [已 push，S29+30+31 累積批次]
Sprint 31  → ✅ Release (v2027.01.02-01)  [已 push，S29+30+31 累積批次]
Sprint 32  → ⏳ Tag 已建 (v2027.01.16-01)  [待 push，S32~S35 累積批次]
Sprint 33  → ⏳ Tag 已建 (v2027.01.30-01)  [待 push，S32~S35 累積批次]
Sprint 34  → ⏳ Tag 已建 (v2027.02.13-01)  [待 push，S32~S35 累積批次]
Sprint 35  → ⏳ Tag 已建 (v2027.02.27-01)  [待 push，S32~S35 累積批次]
```

**連續建立 Release Tag**: 26 次 (Sprint 10-35，未中斷)
**已 push（已 Release）**: Sprint 10-31（22 次）
**待 push（Tag 已建、尚未 push）**: Sprint 32-35（4 次，累積批次待完整守門 + 檢查點徵詢後 push）

---

## 🔧 使用方式

### 在 Sprint Planning 時

1. 開啟此文件
2. 確認上一個 Sprint 的 Release 狀態
3. 將 Release 追蹤加入 Sprint Planning Template 檢查清單

### 在 Final Approval 時

1. 確認 Release Tag 已建立
2. 確認 PR 已合併至 main
3. 更新此文件的 Release 狀態
4. 建立 GitHub Release (如尚未建立)

### 在 Sprint Retrospective 時

1. 檢視 Release 頻率
2. 識別任何跳過的 Release
3. 討論改善措施

---

## 📚 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 17 Plan | `docs/04_planning/SPRINT_17_PLAN.md` |
| Sprint 17 Tasks | `docs/05_development/SPRINT_17_TASKS.md` |
| Final Approval Process | `docs/04_planning/SPRINT_FINAL_APPROVAL_PROCESS.md` |
| Execution Checklist | `docs/04_planning/EXECUTION_CHECKLIST.md` |

---

## 🔧 狀態欄維護規則（v1.3 新增）

> **真相來源是 git，不是本文件。** 核對指令：
> ```bash
> git rev-list --left-right --count origin/main...HEAD   # 0 0 表示全數已 push
> ```
>
> **為什麼狀態欄總是過時**：Sprint 收尾時先 commit（此時尚未 push，只能寫「待 push」），
> 而 push 發生在 commit 之後。若當下就要把狀態改對，得為這一行字再跑一次完整
> `make validate-release`（約 30-45 分鐘）才能 push，而那個 commit 又會標成「待 push」——
> 無限循環。這正是 v1.1 累積 12 筆、v1.2 累積 27 筆過時記錄的結構性原因。
>
> **規則**：**下一個 Sprint 開工時，先回填上一個 Sprint 的實際 push 狀態**，
> 隨該 Sprint 的收尾 commit 一併推送。如此過時記錄最多只有 1 筆（最新的 Sprint），
> 不會再累積成數十筆。

---

## 📝 歷史版本

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.9 | 2026-09-01 | 新增 Sprint 104 row（DEF-049 整合測試耗時的兩支真槓桿，AI-2438）並同步統計區。**依「狀態欄維護規則」開工回填 Sprint 103**：`git rev-list --left-right --count origin/main...HEAD` = `0 0`，確認 `3b19790` 已 push，狀態欄補上雲端 CI run **33511000120** 三個 job 全綠（unit 8m01s／integration 24m29s／frontend 1m27s）——這是 DEF-049 的**第五筆耗時樣本**，S103 多加了一個整合測試類別該 job 反而從 28m05s 降到 24m29s，再次印證變異來自 runner 而非測試內容。**本版另含一項影響歷史記錄的更正**：Sprint 104 證實 `reuseForks=false` 會重複計入 `@Nested` 測試，S100~S103 各 row 記載的單元測試數（1055／1068／1074）**全部偏高約 60**，正確量級為 1014。**刻意不回頭改寫那些 row**——它們是當時的真實觀測，改寫會抹掉「量測方法曾經不可靠」這個事實本身；更正記錄於此與 Sprint 104 row，日後引用基準數應以 1014 為準並以原始碼 `@Test` 實數交叉核對 |
| v1.8 | 2026-09-01 | 新增 Sprint 103 row（商品庫存三段式操作改為原子敘述，AI-2437）。**依「狀態欄維護規則」開工回填 Sprint 102**：`git rev-list --left-right --count origin/main...HEAD` = `0 0`，確認 `cbede57` 已 push，狀態欄由 v1.7 標示的內容補齊為兩筆 commit 皆已 push。**同時回填 v1.7 當時尚未取得的第四筆 CI 耗時樣本**（run 33495383858，整合測試 28m05s）——該 run 的 commit 只改文件與 javadoc、測試套件與前一筆完全相同卻慢 21%，構成**同碼對照**，把 v1.7「runner 變異大於測試成長」的更正從推測推進為確認。此筆刻意未在當時另開一次 commit 回填，正是「狀態欄維護規則」要避免的「為一行數字再跑一次守門」循環，依慣例留到本 Sprint 開工一併處理。Sprint 103 自身標為「⏳ 待 push」待 Sprint 104 開工回填 |
| v1.7 | 2026-09-01 | **Sprint 102 狀態於同日確定，依 v1.5 補充的判準直接回填**（雲端 CI 結果本來就要再做一次 commit，順帶回填狀態的成本為零，不觸發「為一行字再跑一次完整驗證」的循環）。同時併入本輪的**兩項自我更正**：(1) 新測試類別「對齊註解共用 context 快取」的說法錯誤（`reuseForks=false` 使 context 無法跨類別共用），連帶推翻 DEF-049 評估方向 (1)；(2) S101 對 DEF-049 記下的「距離撞穿只剩 15 分鐘」是單一取樣誤判——S102 多加 5 個整合測試，該 job 反而快 6.7 分（23.1m），runner 變異大於測試成長。統計區同步（待 push 歸零、最近一次已 push 改為 Sprint 102）|
| v1.6 | 2026-09-01 | 新增 Sprint 102 row（優惠券額度佔用改為原子操作，AI-2436）並同步統計區。**依「狀態欄維護規則」運作**：開工時先核對 Sprint 101 狀態（`git rev-list --left-right --count origin/main...HEAD` = `0 0`，確認 `3caebbb` 已 push，v1.5 已回填為「✅ 已 push」故無需再動），Sprint 102 自身標為「⏳ 待 push」待 Sprint 103 開工時回填。**順帶更正統計區的自相矛盾**：「最近一次已 push Release」原記載為 Sprint 100，但同一份文件的總覽表已把 Sprint 101 標為「✅ 已 push」，v1.5 更新 row 時漏改統計區，已更正為 Sprint 101 |
| v1.5 | 2026-09-01 | **Sprint 101 狀態於同日確定，故直接回填而非留待 Sprint 102**：v1.4 依「狀態欄維護規則」把 Sprint 101 標為「⏳ 待 push」，但該規則的用意是避免「為了改一行字再跑一次 30-45 分鐘 `make validate-release`」的無限循環；本次因雲端 CI 逾時問題本來就要再做一次commit + push（`09020e0`），順帶確認狀態的成本為零，不觸發該循環，故直接更新為「✅ 已 push」。**規則補充**：狀態欄應在「當輪已確定且不需為此額外付出驗證成本」時立即回填，只有在「回填本身會逼出一次額外的完整驗證」時才延到下一個 Sprint。同時補記本 Sprint 的雲端 CI 事件（timeout 25→45、DEF-049、run 33471691304 全綠）|
| v1.4 | 2026-09-01 | 新增 Sprint 101 row（FREE_SHIPPING 免運券落地 + 購物車運費預覽，AI-2435）並同步統計區。**首次依 v1.3 新增的「狀態欄維護規則」運作**：開工時先核對 Sprint 100 狀態（`git rev-list --left-right --count origin/main...HEAD` 確認 `144b219` 已 push，狀態欄正確無需回填），Sprint 101 自身標為「⏳ 待 push」待 Sprint 102 開工時回填 |
| v1.3 | 2026-09-01 | 新增 Sprint 100 row（優惠券機制完整斷鏈修復，AI-2434）並同步統計區。**新增下方「狀態欄維護規則」**：狀態欄無法在 commit 當下寫正確（push 必然發生在 commit 之後），這是它在 v1.1 累積 12 筆、v1.2 累積 27 筆過時記錄的結構性原因。規則改為「下一個 Sprint 開工時先回填上一個 Sprint 的實際 push 狀態」，使過時記錄最多只有 1 筆而非無限累積 |
| v1.2 | 2026-09-01 | **修正 Sprint 53~99 共 27 個 row 過時「⏳ 待 push」狀態為「✅ 已 push」**（Sprint 99 收尾 commit `24decc8` 完成 `make validate-release` 全綠後已 push，`git rev-list --left-right --count origin/main...HEAD` 為 `0 0`，確認全數同步）；**重建 Release 統計區**（原數值停留在 Sprint 53，已過時 46 個 Sprint）；**誠實揭露**：Sprint 68/69/70/74/75/76/78/80~92 共 20 個 Sprint 從未建立 row，該期間（多 Sprint 測試強化計劃 + Sprint 80~92）tracker 未同步維護，原「連續」敘述已不成立，補齊需歷史考證，另案處理 |
| v1.1 | 2026-07-04 | 新增 Sprint 53 row；**修正 Sprint 41~52 過時「⏳ 待 push」狀態為「✅ 已 push」**（確認 origin/main HEAD 已對齊 Sprint 52 收尾 commit，push 債已於本 Sprint 前清償，此前 tracker 未同步更新） |
| v1.0 | 2026-06-11 | 初始建立，包含 Sprint 10-16 Release 歷史資料 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**作者**: Claude Code (AI Assistant)
**維護責任**: PM/PO (每個 Sprint 結束後更新)