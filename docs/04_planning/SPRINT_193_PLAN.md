# Sprint 193 Plan — 定價規則設定值範圍驗證（DEF-268 結案）＋時區與日期邊界掃描（DEF-269）

**Sprint**: Sprint 193
**日期**: 2026-09-24

## 1. 起點

Sprint 192 收尾時留下一項**待業務決策**的 `DEF-268`（定價規則 `config` 數值無範圍驗證），並列出兩個未查證的鄰近角度（`SecurityConfig` 安全標頭、時區處理）。使用者本輪指示：「以最佳理想化幫我做決定，不要虧錢，若真要決策，請以互動選擇讓我決定」。

- **`DEF-268`**：業務決策以互動選擇請使用者拍板（見 §2.1），實作結案。
- **新掃描角度「時區與日期邊界」**：自行選定。理由：所有「判斷資格／有效期／價格適用日」的邏輯都用「今天」，若環境時區與營運時區不同，同一時刻會算出不同價格，**直接影響收入**，與「不要虧錢」的指示同向；前 60 個 Sprint 計畫書全文檢索 `時區`／`ZoneId` 皆為 0 筆。

## 2. 調查方法與結果

### 2.1 `DEF-268`：使用者拍板與實作

三個決策皆採推薦選項：

| 問題 | 使用者決定 |
|---|---|
| 設定值超出範圍時的處置 | **建立/更新時拒絕（E-8001）＋計算時略過既存的壞規則** |
| 是否允許免費（0 元） | **不允許**：`discountPercent` 須 > 0 且 < 100；覆蓋價 `price` 須 > 0；倍率須 > 0 |
| 加價倍率上限 | **10 倍**（擋打錯字，如想輸入 1.3 卻打成 130） |

另依 FRD v1.0（第 2715/2789 行）明訂，`minNights`、`minDaysAhead`（FRD 稱 `daysInAdvance`）須 ≥ 1。

**設計要點**：

- 錯誤碼用 `ErrorCode.E_8001`（「無效的定價規則設定」，HTTP 422）——該碼在 Sprint 172（DEF-224）被通知範本誤用後移走，之後無人使用，語意正好吻合。
- 單一違規判定函式 `findConfigViolation`，同時供「建立/更新拒絕」與「計算時略過」使用，兩處規則不會分歧。
- **以 config 鍵而非規則類型判定**：PRODUCT 側的 `discountPercent` 可掛在任何 `ruleType`（S44 向後相容路徑），逐類型驗證會漏。
- 存在但不是有限數字（`NaN`／`Infinity`／`"10%"`／`"abc"`）一律違規。同時修正 `getDoubleConfig`：`Double.valueOf("NaN")` 過去會成功，之後 `BigDecimal.valueOf(NaN)` 才拋 `NumberFormatException`（`BookingService.tryDynamicPricing` 只攔 `BusinessException` → 500）。現在 `NaN`/`Infinity` 視為無法解析。
- 計算時略過壞規則時**記 `log.warn`**（不靜默），且在「挑選最高優先級規則」**之前**過濾——最高優先級規則是壞的，改套用次一優先級的合法規則，而非整個房源不打折。
- 兩個價格端點（`setCalendarPrice`／`overridePrice`）的 DTO 早已有 `@Positive`，經核對無需更動；唯一缺口是 `CreateRuleRequest`/`UpdateRuleRequest` 的自由格式 `config`。
- 未知鍵（如 `weekdayMultiplier`、`startDate`、`originalRuleId`）不驗證；Flyway V5 種子規則使用不被辨識的舊鍵（`surcharge_percent`），不受影響；既有整合測試的設定值（10%、20%、1.2、1.3、2.0、正價）皆在新範圍內。

### 2.2 `DEF-269`：全專案沒有時區設定，「今天」取決於部署環境

**證據**：

1. `Dockerfile`／`docker-compose*.yml`／`application.yml`／`pom.xml` 全部沒有 `TZ`、`user.timezone`、`hibernate.jdbc.time_zone`、`spring.jackson.time-zone`。
2. 正式映像為 `eclipse-temurin:21-jre-alpine`（Alpine，無 `/etc/localtime`、無 `TZ`）→ JVM 預設時區推論為 **UTC**。雲端 CI（GitHub Actions ubuntu runner）同為 UTC。**開發機（本機）為 `Asia/Taipei`**——這正是問題從未被察覺的原因。
3. PRD v0.9／v1.0／FRD v1.0 三份文件明訂營運時區為 **UTC+8**（「每日凌晨 03:00（UTC+8）」）。
4. 全庫無 `java.time.Clock`；業務邏輯以 `LocalDate.now()`／`LocalDateTime.now()` 取得 JVM 預設時區的日期，共 24 處（`Instant.now()` 與 3 處 `new Date()` 為絕對時間，不受影響）。

**後果**：台灣時間每天 00:00～08:00，伺服器算出的「今天」是前一天。逐一判定 24 處中影響金額／資格者：

| 呼叫點 | 後果（台灣 00:00～08:00） | 處置 |
|---|---|---|
| `PricingService.calculatePrice` 預設 `bookingDate` | **早鳥**：提前天數多算 1 天，發出**未達門檻的折扣**（少收錢）；**末班車**：漏發應得折扣 | ✅ 改用營運時區 |
| `PromoCode.isExpired()`／`isNotYetActive()` | 賣家輸入的起訖時間是營運時區牆上時間，卻與 UTC 現在比對：優惠券**過期後多有效 8 小時**（少收錢）、開始日**晚 8 小時**才生效 | ✅ 改用營運時區 |
| `RedisCartService.tryProductAdjustment` 商品促銷價有效日 | 促銷價結束後多適用 8 小時、開始日晚 8 小時 | ✅ 改用營運時區 |
| `BookingService.openWindowReferenceDate` 滾動開放窗 | 開放窗少算一天（少賣，非少收） | ✅ 改用營運時區 |
| `SettlementGenerator` 每週一結算（cron 與 `LocalDate.now()`） | cron 以 JVM 時區為準：週一 00:00 UTC = 台灣 08:00；週期邊界與營運週相差 8 小時。結算期間內部一致（每筆訂單恰被結算一次），**非金額錯誤**，但「哪個時區定義結算週」是業務決策 | ⚠️ 未更動，列入 §7 |
| `AnalyticsService`（`LocalDate.now()`、`atZone(ZoneId.systemDefault())`） | 報表日期分桶與營運日相差 8 小時（報表，非收款） | 未更動 |
| `CmsService`（橫幅/頁面排程可見窗）、`ListingCardService`（維護日窗）、`AdminService`（維護緊急窗）、`PricingController.getCalendarPreview`（預覽不可查過去日期） | 可見性／預覽邊界差一天，不影響金額 | 未更動 |
| 單號日期字串（`PurchaseOrderService`、`SettlementReversalService`、`HCT/TCATLogisticsProvider`）、`LogisticsService`/`PromoCode` 的 `createdAt`/`updatedAt` 時間戳 | 標籤／稽核戳記，不參與判斷 | 未更動 |

**修法選擇**：建立 `BusinessTime`（`Asia/Taipei`），凡「判斷資格／有效期／價格適用日」一律使用，使結果**不再取決於部署環境的時區設定**——在開發機、CI、正式容器三處行為一致。刻意**沒有**改 Docker／compose 加 `TZ`：`CLAUDE.md` Docker 管理限制禁止 AI 自行修改基礎設施；且應用層明確指定時區比依賴環境設定更穩固（未來換映像、換雲端都不會再踩）。

**為何沒有用 `TimeZone.setDefault`**：它是全域副作用，會改變 Hibernate 對既有 `timestamp`（無時區）欄位的 `LocalDateTime` 解讀，使新舊資料語意不一致；明確傳入時區只影響被改的呼叫點。

## 3. 修復範圍與實作

### 3.1 `DEF-268`（`PricingService`）

- 新增常數 `MAX_DISCOUNT_PERCENT_EXCLUSIVE=100`、`MAX_PRICE_MULTIPLIER=10`、`RANGE_CHECKED_CONFIG_KEYS`。
- 新增 `findConfigViolation`／`rangeProblem`（範圍規則）、`validateRuleConfig`（拒絕，E-8001）、`isRuleConfigUsable`（計算時略過並記 warn）、`parseFiniteDouble`（`getDoubleConfig` 共用，拒絕 NaN/Infinity）。
- `createRule`（經 `validateRuleRequest`）與 `updateRule`（`setConfig` 之前）呼叫 `validateRuleConfig`；`calculatePrice` 與 `getEffectivePrice` 在挑選規則前過濾壞規則。

### 3.2 `DEF-269`

- 新增 `shared/time/BusinessTime`：`ZONE = Asia/Taipei`、`today()`、`now()`；`useClockForTesting(Clock)`／`resetClock()` 供測試固定「現在」。
- 4 個呼叫點改用之：`PricingService`（預設 `bookingDate`）、`BookingService`（開放窗基準日）、`RedisCartService`（商品促銷價查詢日）、`PromoCode`（`isExpired`/`isNotYetActive`）。
- 測試自動重置：既有全域 `ThreadLocalIsolationExtension`（同一 JVM 循序跑全部測試、為防 ThreadLocal 洩漏而設）新增 `BusinessTime.resetClock()`，使時鐘洩漏在結構上不可能發生。

## 4. 測試

**`DEF-268`**（`PricingServiceTest`，新增 39 個測試案例含參數化）：
- `RuleConfigValidationTests`（30）：13 組越界值 → E-8001 且不寫入、5 種非有限數字 → E-8001、9 組邊界值（`discountPercent` 0.01／99.99、`multiplier` 10、`price` 0.01、`minNights`／`minDaysAhead` 1 等）**允許**（不可誤擋）、未知鍵不驗證、更新越界拒絕且**不改動既有 config**、更新合法通過。
- `BadStoredRuleSkippedTests`（9）：既存壞規則（`discountPercent` 150／100、`multiplier` -2、`weekendMultiplier` 130、`price` -100／0）計算時略過、照原價；`"NaN"` 不拋例外；最高優先級規則為壞 → 改套用次一優先級的合法規則；PRODUCT 側同樣略過。

**`DEF-269`**（新增 12 個案例）：
- `BusinessTimeTest`（4）：時區為 `Asia/Taipei`、固定時鐘換算（UTC 1/31 16:30 → 台灣 2/1 00:30）、預設時鐘不受 JVM 預設時區影響（UTC／檀香山／台北皆然）、`resetClock`。
- `PromoCodeBusinessTimeTest`（4）：過期後不可再用、未到期不可提早失效、開始日到了即生效、未到不可提早。
- `PricingServiceTest.BusinessDateTests`（2）：末班車 `maxDaysAhead=0` 當日訂當日住須適用；早鳥門檻 8 天、實際提前 7 天須**不**適用。
- `BookingServiceOpenWindowTest`（+1）、`RedisCartServiceDynamicPricingTest`（+1）。

**紅燈先行**：`DEF-268` 39 個中 28 個為**行為性紅燈**（失敗訊息為 `-500.00`、`0.00`（免費）、`130000.00`（打錯字放大 130 倍）、`-2000.00`、`-100.00`、`Expecting code to raise a throwable`），11 個守門案例修復前後皆綠。`DEF-269` 先建立 `BusinessTime` 工具類（機制本身），再寫測試對「尚未接線的正式呼叫點」執行：6 個行為性紅燈（早鳥誤發 `900.00`、末班車漏發 `1000`、促銷價未套用 `500`、優惠券過期／生效判斷相反、開放窗少一天）。

## 5. 驗證結果

**預設時區（本機 Asia/Taipei）** `mvn -o clean verify`（真實 postgres/redis）：**1708 個單元測試（+51：DEF-268 39 + DEF-269 12）+ 490 個整合測試（持平），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 9:05。

**跨時區驗證**（本輪核心方法）：開發機時區與營運時區相同，會掩蓋「測試用系統時區、程式用營運時區」的混用，所以另以 `JAVA_TOOL_OPTIONS=-Duser.timezone=Pacific/Honolulu`（UTC−10，此刻檀香山日期比台灣晚整整一天，與雲端 CI 的 UTC 在每天 16:00–24:00 UTC 出現的偏移同方向且更大）跑全部測試：**單元 1708 個 0 failure**（另 11 個 error 全是 `Connection to localhost:5432 refused`——測試 DB 先前被 push 守門關掉，與時區無關，啟動 DB 後整合測試階段全數通過）、**整合／E2E 490 個 0 failed**（含 `BookingControllerE2ETest` 42 處 `LocalDate.now()`、`M12*`、`M11*` 等）。**既有測試沒有需要為時區修改的**（其日期邊際皆以「天」為單位，未跨 8 小時偏移失敗）。

**過程揭露**：`ThreadLocalIsolationExtension` 於 `afterEach`/`afterAll` 還原 `BusinessTime` 時鐘；測試對時鐘的覆寫不會洩漏到下一個測試（同一 JVM 循序執行）。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- `DEF-268`：⚠️ 待業務決策 → ✅ 已修復（Sprint 193，使用者拍板）。
- 新增 `DEF-269`：✅ 已修復（Sprint 193）。

## 7. 誠實揭露總結

- **正式容器實際時區未實測**：`eclipse-temurin:21-jre-alpine` 不在本機，依 Docker 政策不為探測額外 pull。「正式 JVM 為 UTC」由 Dockerfile 基底映像、compose 無 `TZ` 及 Alpine 預設值推論；本輪以 `-Duser.timezone=Pacific/Honolulu`／`UTC` 模擬。修復本身不依賴此推論（應用層明確指定時區，環境是什麼都不影響）。
- **`SettlementGenerator` 的結算週定義未更動**：cron 與週期邊界以 JVM 時區為準，目前內部一致、無金額錯誤，但「結算週的邊界是否應為營運時區的週一 00:00」是業務決策，且改動會改變歷史週期的切分。**建議**使用者決定是否統一為 UTC+8；若要，需同時處理 cron 的 `zone` 屬性與 `SettlementAdjustmentService`（`atZone(ZoneId.systemDefault())`）。
- **可另行加固（需你明確指示，AI 不自行改 Docker）**：在 compose 為 backend 加 `TZ=Asia/Taipei`，可讓上表「未更動」的報表／結算類呼叫點也對齊營運日；本輪的金額相關修復不依賴它。
- 本輪**未查證**：`SecurityConfig` 安全標頭（`headers()` 無顯式設定，沿用 Spring Security 預設）——仍是未來候選角度。
- 本輪未派背景 agent（Rule 5：確定性工作交給程式碼）。
