# Sprint 195 Plan — 結算訂單「恰好結算一次」（DEF-273 實作）與退款定位（DEF-274/275/276）

**Sprint**: Sprint 195
**日期**: 2026-09-25

## 1. 起點

Sprint 194 §2.4 以真實資料庫實證：週間下單、結算當下仍在配送的訂單，之後任何一期都撈不到它，**永遠不會被結算**。使用者以互動選擇拍板：**新增「已結算」標記，每筆訂單恰好結算一次**（否決「以 `order_state_logs` 完成時間歸週」與「維持現狀」）。本輪實作，並處理實作中發現的三個同型缺口。

## 2. 設計

### 2.1 資料模型（Flyway `V83`）

`orders.settled_statement_id UUID REFERENCES settlement_statements(id)`（NULL＝尚未結算）＋兩個部分索引：`idx_orders_settled_statement`（由結算單反查訂單）、`idx_orders_unsettled (tenant_id, created_at) WHERE settled_statement_id IS NULL AND status IN ('DELIVERED','COMPLETED')`（只含待結算者，隨結算自然縮小）。

`Order` 實體對它是**唯讀映射**（`insertable = false, updatable = false`）：只有結算流程以原生 UPDATE 寫入，避免任何後續實體更新把記憶體中的舊值寫回、蓋掉併發的結算標記（同 `applyRefundDeduction` 刻意不呼叫 setter 的理由）。

**歷史訂單不回填**：週結算單過去在真實資料庫從未成功產生過（DEF-272），所有歷史已完成訂單本來就都尚未結算，首次結算會一併納入——**首張結算單會包含全部歷史已完成訂單**（見 §7）。

### 2.2 結算生成（`SettlementGenerator`）

- 查詢改為「該租戶、狀態∈可結算（`SettlementCalculator.SETTLEABLE_STATUSES`，抽成單一來源）、`settled_statement_id IS NULL`、建立時間早於期間結束次日 00:00（營運時區）」——**不再要求下單落在本期**。上界只排除「本期結束之後才建立」的訂單。
- 儲存結算單後，`markSettled(ids, statementId)` 以 `UPDATE ... WHERE id IN (...) AND settled_statement_id IS NULL` **原子認領**；回傳筆數少於讀到的筆數（有訂單被另一個結算搶先認領）→ 拋 `IllegalStateException` 回滾整張結算單（`generateWeeklyStatements` 逐租戶攔截、記錄、下次重試）。READ COMMITTED 下後到者會等待列鎖、待先到者提交後重新檢查條件、更新 0 列。
- 沒有可結算訂單時不呼叫認領（不對空清單下 `IN ()`）。

### 2.3 釋放規則（依結算單終態，逐狀態核對程式碼後決定）

| 結算單狀態 | 處置 | 理由 |
|---|---|---|
| `REJECTED`（`PENDING_REVIEW` 唯一去向，**無回頭路**，資金未發生） | **釋放**訂單與折入的調整單 | 否則訂單永遠掛在死掉的結算單上（又是「永遠不被結算」）；釋放後由下一期重新結算、重新審核，未經核准不會撥款 |
| `FAILED`（`TransferService.retryFailedTransfer` 可**改回 `APPROVED` 重試撥款**） | **不釋放** | 釋放會讓同一批訂單被下一期結算，重試撥款後**雙重撥款** |
| `REVERSED`（`CREDIT_NOTE` 會計沖銷，不處理 clawback） | 不釋放 | 是否重新結算屬會計人工處理；自動釋放會與貸項通知單疊加 |
| `PENDING`／`PENDING_REVIEW`／`APPROVED`／`PAID` | 保留 | 正常流程 |

### 2.4 退款調整定位（`SettlementAdjustmentService.handleOrderRefund`）

簽名去掉 `orderCreatedAt`，改由 `orders.settled_statement_id` 定位結算單。**過去以「下單日期」猜測結算單，對遲到結算的訂單是錯的**（真實資料庫紅燈實證，見 §4）：
- 遲到訂單的退款被扣在**下單日期落入、但根本沒含這筆訂單**的結算單（`expected 100.00 but was 0.00`）。
- 尚未結算的訂單退款，同樣誤扣那張結算單（`expected 0.00 but was 100.00`）。

未結算訂單的退款不動任何結算單：它在被結算時由 `Payment.refundedAmount`（`buildRefundedAmountMap`）帶入。已移除成為死碼的 `findByTenantIdAndPeriodCovering`。

## 3. 同型缺口（實作中發現並修復）

- **`DEF-274`（🟠）退款調整以下單日期定位，遲到結算的訂單扣錯結算單**：見 §2.4，隨 DEF-273 修復。
- **`DEF-275`（🟠）駁回結算單時，它折入的調整單隨之消失**：`generateStatementForTenant` 把待處理調整單標記為 `APPLIED` 並記下 `appliedStatementId`；結算單若被駁回（終態），這些「賣家該被扣的退款」永遠不會被下一張結算單折入——賣家少扣錢。修復：駁回時 `releaseAppliedTo` 還原為 `PENDING`。
- **`DEF-276`（🟠）`FAILED`（可重試撥款）結算單的退款被當「終態」忽略**：`SettlementAdjustmentService` 把 `REJECTED`/`FAILED` 一併視為「終態、不做事」，但 `FAILED` 可被 `retryFailedTransfer` 改回 `APPROVED` 並以**當下淨額**撥款——退款若被忽略，重試撥款時賣家拿到已退款訂單的全額（**少收錢**）。修復：`FAILED` 比照 `APPROVED`／`PAID` 產生調整單，於下一期折入（不直接改結算單金額：`Transfer.transferAmount` 在建立時已取值，改結算單淨額會與已建立的轉帳記錄不一致）。

## 4. 測試

**真實 PostgreSQL 整合測試**：
- `SettlementExactlyOnceIntegrationTest`（6）：遲到訂單（週三下單、配送中 5000 元）在送達後的**下一次**結算被結算一次、其後不再重複；週期結束之後才建立的訂單不被提前結算；`REJECTED` 釋放訂單與調整單、下一期重新結算並重新折入調整單；`FAILED` 保留掛鉤（下一期不可重複結算）；退款扣在**實際結算它的那張**結算單、未結算訂單的退款不動任何結算單。
- `SettlementConcurrentClaimIntegrationTest`（1）：**8 個結算同時搶同一批 40 筆訂單**（各用不同期間，不會被同期間冪等檢查擋下）。不假設誰贏（時序不可控），只斷言與時序無關的不變量：無未結算訂單、各結算單 `total_orders` 加總恰等於 40（不重複）、GMV 加總 4000。
- `M07SettlementRefundConcurrencyIntegrationTest`（既有，2）：改為每條執行緒退款一筆已被該結算單結算的真實訂單，10 線程併發退款仍不丟失更新。
- `SettlementPeriodBoundaryIntegrationTest`（既有，Sprint 194）：依序生成三期，5 筆邊界訂單恰好各歸一期。

**單元測試**：`SettlementGeneratorClaimTest`（3：認領筆數不足→回滾、認領以正確的訂單 id 與結算單 id 進行、無訂單不認領）；`SettlementAdjustmentServiceTest` 改寫（9：以掛鉤定位；含租戶隔離防禦、`FAILED`→調整單）；`SettlementGeneratorBusinessWeekTest` 沿用（更新查詢名）。

**紅燈先行**：
- `SettlementExactlyOnceIntegrationTest` 4 個對舊生成邏輯**行為性紅燈**（`expected: 1 but was: 0`——遲到訂單撈不到；及標記未寫入）。
- 退款定位 2 個對舊日期定位邏輯**行為性紅燈**（`100.00`↔`0.00`，見 §2.4）。
- `FAILED` 案例對舊 `switch` **行為性紅燈**（`Wanted but not invoked`）。

**突變驗證（確認測試抓得到缺陷，而非只是綠燈）**：把 Java 端「認領筆數不足則回滾」檢查改成永遠不觸發，(a) 單元測試 `claimShortfall_throwsToRollbackWholeStatement` 變紅；(b) 真實併發測試 `concurrentGenerations_settleEveryOrderExactlyOnce` **3 次重跑全紅**（重複結算）。驗證後已還原原檔。

## 5. 驗證結果

**預設時區** `mvn -o clean verify`（真實 postgres/redis）：**1716 個單元測試（+4）+ 500 個整合測試（+7），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 9:50。

**跨時區**：結算與統計的真實 DB 測試（`SettlementExactlyOnceIntegrationTest`、`SettlementPeriodBoundaryIntegrationTest`、`AnalyticsRealDbIntegrationTest`、`M07SettlementRefundConcurrencyIntegrationTest` 共 11 個）另以 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` 與 `Pacific/Honolulu` 各重跑一次，皆 11/11 通過。

**過程揭露**：第一次背景完整驗證啟動後，我決定再補一個真實併發測試（`SettlementConcurrentClaimIntegrationTest`）而中止它，避免在 Maven 執行中改動原始碼；上述數字來自補完併發測試後重跑的單一完整驗證。schema 守門（`make validate-schema-doc`）綠燈；push 前的 `schema-gate`（`ddl-auto=validate` + Flyway 真實啟動 backend）由 pre-push 執行。

## 6. Schema 文件

依 `schema-doc-drift-guard` 流程：`make sync-schema-doc` 重新產生 SRD §2 DDL（`orders` 1 張表）→ `make validate-schema-doc` 綠燈。PRD §8.2 無 `orders` 表章節，不需補欄位；PRD §6.2 新增「[Specification] 結算訂單歸屬與時區」段落，把使用者拍板的規則明文化（原文「結算上一週已完成訂單」未定義以下單或完成時間歸週，正是缺口的成因）。

## 7. 誠實揭露總結

- **首張結算單會包含全部歷史已完成訂單**：歷史不回填、且週結算單過去從未成功產生過（DEF-272），所以第一次結算會把該租戶所有已完成訂單一次納入。目前專案只有本地驗證環境、無正式歷史，故無影響；**若日後有「上線日之前的訂單不應結算」的需求，需另訂截止日**（業務決策，本輪未定）。
- **`REVERSED` 不釋放訂單**是保守選擇（避免與貸項通知單疊加造成重複給付），但也代表被沖銷的結算單的訂單不會自動重新結算；是否重新結算需會計人工處理。若使用者希望自動化，需另議。
- **`FAILED` 的退款走調整單**，不直接扣結算單：重試撥款會以結算單「當下淨額」撥款，賣家該次會拿到全額，扣除延到下一期折入——長期淨額正確，但有一個週期的資金時間差。
- **未端到端驗證**：真實 Stripe 撥款與 `retryFailedTransfer` 串接（僅以單元／真實 DB 測試驗證結算單與訂單的掛鉤語意）；`SettlementReversalService` 與貸項通知單的後續套用（本輪未動）。
- **併發測試的限制**：真實併發測試以「與時序無關的不變量」為斷言，並以突變驗證確認其敏感度；但無法保證每次執行都撞上競態窗口（`8 個結算同時讀取同一批訂單`在本機幾乎必然重疊，突變驗證 3/3 皆紅，但不是形式證明）。原子性的根本保證來自 PostgreSQL 的列鎖語意與 `UPDATE ... WHERE settled_statement_id IS NULL`。
- 本輪未派背景 agent；`Settlement*Test` 以 `-Dtest` 執行時會連 `*IntegrationTest` 一併跑（surefire 對明確指定的類別略過排除規則），屬既知行為。
