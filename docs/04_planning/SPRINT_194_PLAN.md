# Sprint 194 Plan — 週結算與儀表板統計在真實資料庫從未成功過（DEF-270/271/272）＋結算歸屬缺口（DEF-273 拍板）

**Sprint**: Sprint 194
**日期**: 2026-09-24

## 1. 起點

Sprint 193 §7 記錄了 `SettlementGenerator` 週結算的時區問題並向使用者提問，使用者以互動選擇拍板：**結算週改為台灣時間（UTC+8）**。實作前完整閱讀結算相關程式與測試，發現遠比時區嚴重的問題（§2.1）。

## 2. 調查方法與結果

### 2.1 `DEF-272`（🔴 CRITICAL）：`OrderRepository` 的兩個期間查詢在真實資料庫每次都拋例外

`OrderRepository.findByTenantIdAndCreatedAtBetween`／`countByTenantIdAndCreatedAtBetween` 以 `LocalDateTime` 綁定查詢參數，但 `Order.createdAt` 是 `Instant`。Hibernate 6.4 對此直接拋：

```
QueryArgumentException: Argument [2026-12-28T00:00] of type [java.time.LocalDateTime]
did not match parameter type [java.time.Instant (n/a)]
```

**實證方式**：不憑推論——寫真實 PostgreSQL 整合測試，對**未修改的正式程式**執行（紅燈），並以一次性探針（用畢即刪，`git status` 確認無殘留）直接呼叫 Repository 與 `AnalyticsService` 全部公開方法：

| 呼叫 | 真實資料庫結果 |
|---|---|
| `findByTenantIdAndCreatedAtBetween` / `countByTenantIdAndCreatedAtBetween` | **兩者皆拋 `InvalidDataAccessApiUsageException`** |
| `SettlementGenerator.generateStatementForTenant` | 拋例外（`generateWeeklyStatements` 逐租戶 `catch (RuntimeException)` 只記 `log.error`、繼續下一個租戶，最終「Generated 0 statements」）→ **週結算單從未在真實資料庫成功產生過** |
| `AnalyticsService.getDashboardStats` / `getRevenueStats` | **失敗**（賣家儀表板統計、營收統計，前端 `dashboard/page.tsx`、`dashboard/revenue/page.tsx` 使用） |
| `AnalyticsService.getOrderStats` / `getListingStats` | 正常（不用這兩個查詢） |

**為何從未被發現**：所有結算與統計測試都 `mock` 了 `OrderRepository`（`SettlementScheduledJobIntegrationTest` 名為整合測試，實為 Mockito 單元測試；`AnalyticsServiceTest` 亦然），查詢語法與參數型別從沒被真實執行過。掃描其餘 Repository 的日期參數型別與對應實體欄位型別（`Instant`↔`Instant`、`LocalDate`↔`LocalDate`）：**皆一致，僅這兩個方法錯配**。

### 2.2 `DEF-270`（🟠）：期間上界 `atTime(23, 59, 59)` 漏掉最後一秒

`SettlementGenerator` 以 `periodEnd.atTime(23, 59, 59)` 當查詢上界（`<=`）。`Order.createdAt` 是微秒精度：落在 `23:59:59.000001 ~ 23:59:59.999999` 的訂單既不屬於本期（上界 `23:59:59.000000`）、也不屬於下一期（下界次日 `00:00:00`），**永遠不被結算**。改為半開區間 `[週一 00:00, 次週一 00:00)`，相鄰兩期共用同一邊界時刻。

### 2.3 `DEF-271`（🟡）：結算週切分取決於 JVM 時區（使用者拍板改為台灣時間）

以真實 PostgreSQL 實證 `LocalDateTime` 參數的解讀時區：**PostgreSQL 連線的 session 時區跟著 JVM 預設時區**（JVM=UTC → session=UTC；台北 → 台北；檀香山 → 檀香山；同一個 `LocalDateTime 2027-01-04T00:00` 對 `timestamptz` 分別對應 UTC `2027-01-04 00:00`／`2027-01-03 16:00`／`2027-01-04 10:00`）。因此（假設查詢能執行）正式容器（UTC）的一週是「台灣週一 08:00～次週一 07:59」，開發機是「台灣週一 00:00～週日 23:59」；`@Scheduled` 的 cron 同樣以 JVM 時區解讀。

**修法**（使用者選項：改為台灣時間 UTC+8）：`@Scheduled(cron = "0 0 0 ? * MON", zone = Asia/Taipei)`、`BusinessTime.today()` 決定「剛結束的那一週」、期間查詢用 `BusinessTime.startOfDay(...)` 換算的絕對時刻、`SettlementAdjustmentService` 的訂單日期換算同用營運時區。**因為 §2.1，週結算單從未成功產生，正式環境沒有歷史結算單，改切分沒有「週期重疊導致重複結算」的切換風險**——這也是使用者選項說明的前提。

### 2.4 `DEF-273`（🟠，使用者已拍板，Sprint 195 實作）：週間下單、下週才送達的訂單永遠不被結算

結算歸屬是「**下單時間**落在該週、且**結算產生當下**狀態為 COMPLETED/DELIVERED」。`Order` 沒有完成時間、也沒有已結算標記。真實資料庫實測（探針已刪）：週三下單、5000 元、結算當下仍 `SHIPPING`，之後改 `DELIVERED`，第 W／W+1／W+2 週結算單皆為 **0 筆、0.00**——永遠不會被結算。PRD 只寫「每週一結算上一週（週一 00:00 至週日 23:59）已完成（COMPLETED）訂單的收益」，未定義以下單或完成時間歸週。

**使用者以互動選擇拍板**：新增「已結算」標記，每筆訂單恰好結算一次（推薦方案；否決「以 `order_state_logs` 完成時間歸週」與「維持現狀」）。設計要點（Sprint 195）：訂單加 `settled_statement_id`（新 Flyway migration）、每週結算所有「已完成且未結算」訂單（不論下單週）、原子性標記（`UPDATE ... WHERE settled_statement_id IS NULL`）、退款調整改以訂單所屬結算單直接定位。

## 3. 修復範圍與實作

- `OrderRepository`：移除兩個永遠拋例外的 `LocalDateTime` 方法，新增 `findByTenantIdAndCreatedAtInRange`／`countByTenantIdAndCreatedAtInRange`（`Instant` 半開區間 `[startInclusive, endExclusive)`，Javadoc 記載成因）。
- `BusinessTime`：新增 `ZONE_ID` 編譯期常數（供 `@Scheduled(zone=...)`）、`startOfDay(LocalDate)`（營運日 00:00 對應的絕對時刻）。
- `SettlementGenerator`：cron `zone`、`today`、期間查詢。
- `SettlementAdjustmentService`：訂單日期以 `BusinessTime.ZONE` 換算（取代 `ZoneId.systemDefault()`）。
- `AnalyticsService`：9 處呼叫（3 count、3 find、`LocalDate.now()`×3、日分桶時區）改用營運日半開區間與 `BusinessTime`；移除不再使用的 `LocalTime`/`ZoneId` import。既有測試的 mock 方法名機械式更名（`SettlementScheduledJobIntegrationTest` 7 處、`AnalyticsServiceTest` 9 處，以 `git diff` 計數）。

## 4. 測試

**真實 PostgreSQL 整合測試（新增 3，這類功能過去完全沒有）**：
- `SettlementPeriodBoundaryIntegrationTest`（1）：5 筆訂單（上週日 23:59:59.999999、週一 00:00 整、週間、週日 23:59:59.5、次週一 00:00 整）→ 三期各 1／3／1 筆，GMV 800／1900／400，**每筆訂單恰好歸屬一期，不重疊、不留縫**。
- `AnalyticsRealDbIntegrationTest`（2）：`getDashboardStats`（今日/昨日/本月訂單與營收）、`getRevenueStats`（按日分桶）；含日界起點整、23:59:59.5、23:59:59.999999、次日 00:00 整、已取消訂單。

**單元測試（新增 4）**：`SettlementGeneratorBusinessWeekTest`（3：cron `zone` 為 `Asia/Taipei`；台灣週一 00:00 觸發時結算的是剛結束的那週（UTC 日期此刻仍是週日，若誤用 UTC 會結算再前一週）；期間查詢為台灣時區的絕對時刻半開區間且與 JVM 時區無關（UTC／檀香山／台北皆驗））；`SettlementAdjustmentServiceTest`（1：UTC 1/3 17:00＝台灣 1/4 01:00 歸 1/4 起的結算週）。

**紅燈先行**：3 個真實 DB 測試對未修改程式碼執行，皆以**正式環境同一個例外**失敗（`QueryArgumentException`）；4 個單元測試為行為性紅燈（`zone` 為空字串、結算了錯的一週、未呼叫新查詢、訂單日期為 1/3）。

## 5. 驗證結果

**預設時區** `mvn -o clean verify`（真實 postgres/redis）：**1712 個單元測試（+4）+ 493 個整合測試（+3），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 8:18。

**跨時區**：兩個新的真實 DB 邊界測試另以 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` 與 `Pacific/Honolulu` 各重跑一次，皆 3/3 通過（結果與 JVM 時區無關）。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-270`、`DEF-271`、`DEF-272`：✅ 已修復（Sprint 194）。
- 新增 `DEF-273`：🔧 已由使用者拍板，Sprint 195 實作。

## 7. 誠實揭露總結

- **`DEF-272` 是這輪最重要的發現，也暴露了測試策略缺口**：結算與統計的核心查詢從未在真實資料庫執行過。本輪新增 3 個真實 DB 測試，但其他以 mock Repository 驗證的金額邏輯是否還有同型盲點，尚未系統性檢查——列為未來候選角度（「mock 掩蓋了什麼」：全庫 `@MockBean *Repository` 的整合測試清單）。
- **`AnalyticsService` 修法的語意影響**：日界從 JVM 日期改為營運日（UTC+8）。因為這些方法過去在真實資料庫根本無法執行，不存在「既有報表數字變動」的問題；`getOrderStats`／`getListingStats`（不受影響）與報表相關的其他 `LocalDate.now()`（`CmsService`／`ListingCardService`／`AdminService`／`PricingController`）**未更動**，理由同 Sprint 193（不影響金額）。
- **未端到端驗證**：真實 `@Scheduled` 在台灣週一 00:00 觸發（僅驗證註解 `zone`／`cron` 值與觸發時刻的週計算，未等待真實觸發）；Stripe 撥款串接。
- **已知未處理（使用者已拍板，Sprint 195）**：`DEF-273` 結算歸屬缺口。在它完成前，週結算即使能執行，週間下單、下週送達的訂單仍不會被結算。
- 本輪未派背景 agent；探針（`LocalDateTime` 綁定、PG session 時區、`AnalyticsService` 公開方法、遲到訂單）皆為一次性、已刪除。
