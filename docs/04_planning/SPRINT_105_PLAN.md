# Sprint 105 Plan — 讀後寫競態的系統性盤點與兩處修復

**Sprint**: Sprint 105
**日期**: 2026-09-02
**AI 編號**: AI-2439
**主題**: 承 S102（優惠券額度）、S103（商品庫存）連續兩輪在同一模式上發現缺陷，本輪不再逐案處理，改為**全專案系統性盤點**「服務層對數值欄位的讀後寫」，逐一驗證後果後決定修哪些。結果：找到一處**金額**缺陷（結算單退款，靜默漏計 90%）、一處**去重＋競態雙缺陷**（評價有幫助投票），另有兩個符合樣式但**經查證不是競態**的偽陽性。

---

## 1. 為什麼改用系統性盤點

S102、S103 各自修掉一個讀後寫缺陷，兩輪的 Action Items 都留下同一句「橫向掃描剩餘的計數／額度型欄位」，並點名三個候選：`SettlementAdjustment`、`MediaAsset.incrementUsageCount`、`ReviewService.markHelpful`。

本輪先問一個更基本的問題：**這個模式在專案裡到底有多普遍？**

### 盤點結果：`@Version` 幾乎不存在

| 項目 | 數量 |
|---|---|
| `domain/model/` 下的 `@Entity` | **59** |
| 其中帶 `@Version` 的 | **2**（`Inventory`、`ProductInventory`） |

這個數字改變了判準。原本設想的「找出沒有 `@Version` 的實體」根本無法縮小範圍——**沒有樂觀鎖才是本專案的常態**。真正的判準只能是：**服務層是否對數值欄位做讀後寫**。

### 這也解釋了 S103 的失效模式為何特殊

S103 的 `ProductInventory` 是那 2 個有 `@Version` 的實體之一，所以它的併發失效表現為「大量 `ObjectOptimisticLockingFailureException`」——**會拋、看得見**，這正是當時紅燈實測推翻 DEF-050「會超賣」定性的原因。

其餘 57 個實體沒有這層保護，同樣的讀後寫在它們身上是**靜默丟失更新**：後寫入者以自己讀到的舊值覆蓋先寫入者的結果，沒有例外、沒有日誌。**S103 的結論不能直接套用到其他實體上**，兩者的嚴重性方向相反。

---

## 2. 掃描方法與完整結果

掃描三種樣式，再逐一驗證後果（S103 教訓：**樣式比對會找對位置但推錯後果**）：

- 樣式 A：`x.setFoo(x.getFoo().add(...))`（BigDecimal 累加）
- 樣式 B：`x.setFoo(x.getFoo() + n)`（整數累加）
- 樣式 C：實體自帶的 `increment*/decrement*` 方法 + 呼叫端 `save()`

| # | 位置 | 樣式 | 驗證後的後果 | 判定 |
|---|---|---|---|---|
| 1 | `SettlementAdjustmentService.applyDirectDeduction` | A | **金額**靜默漏計。同一結算期間內不同訂單的退款落在同一列結算單上 | 🔴 **本輪修復（DEF-053）** |
| 2 | `ReviewService.markHelpful` | — | 整份 JSON map 讀後寫互相覆蓋；**另有無去重缺陷** | 🔴 **本輪修復（DEF-054）** |
| 3 | `PostService` / `FaqService` / `KnowledgeBaseService` 的 `incrementViewCount` | C | 瀏覽數系統性少計。**流量最高的路徑**，且 `KnowledgeArticleRepository:41` 以 `viewCount DESC` 排序 → 熱門排名失真 | 🟡 記錄為 DEF-055，未修 |
| 4 | `ChatService` 未讀計數 | B | 未讀數字偏低；使用者開啟對話即歸零，**自癒** | 🟢 記錄為 DEF-056，低優先 |
| 5 | `MediaService.incrementUsageCount` | C | 競態為真，但**全生產程式碼零呼叫者**，且 `usageCount` 不參與任何判斷（只出現在 DTO） | 🟢 記錄，後果趨近於零 |
| 6 | `NotificationService` retryCount | B | **不是競態**——只在同一交易內剛建立的通知上遞增，無其他執行緒持有該列 | ⚪ 排除 |
| 7 | `RedisCartService` finalAmount | A | **不是競態**——由 `totalAmount` 推導而非累加，且走 Redis 非 JPA | ⚪ 排除 |

**第 6、7 項是刻意記錄的偽陽性**。它們完全符合 grep 樣式，若只做樣式比對就會被當成缺陷「修」掉，而實際上第 6 項不存在併發窗口、第 7 項連累加語意都不是。承 S103「橫向掃描的樣式比對會找對位置但推錯後果」——**排除的理由和修復的理由一樣需要被寫下來**，否則下一輪掃描會再把它們撿回來一次。

---

## 3. DEF-053：結算單退款的金額靜默漏計

### 缺陷

`SettlementAdjustmentService.applyDirectDeduction`（修復前）：

```java
statement.setTotalRefunds(statement.getTotalRefunds().add(refundAmount));
statement.setNetSettlementAmount(statement.getNetSettlementAmount().subtract(refundAmount));
settlementStatementRepository.save(statement);
```

由 `PaymentStateService:223` 在退款成功後呼叫。同一結算期間內**不同訂單**的退款會找到**同一列**結算單（`findByTenantIdAndPeriodCovering` 依期間查詢），形成讀後寫窗口。`SettlementStatement` 無 `@Version`。

### 🔴 紅燈實測：比預期嚴重得多

先寫測試證實失效模式，不從樣式推論（S103 教訓）。`M07SettlementRefundConcurrencyIntegrationTest` 以 10 條執行緒、各自獨立交易，對同一張 PENDING 結算單送出 10 筆各 10.00 的退款：

```
[累計退款必須等於 10 筆 × 10.00…]
expected: 100.00
 but was: 10.00
```

**10 筆退款只有 1 筆存活。** 不是「掉了幾筆」——10 條執行緒全部讀到 `total_refunds = 0`，各自算出 `0 + 10 = 10`，然後全部寫回 `10.00`。

更關鍵的是**過程中零例外**（測試的 `unexpected` 分類為空）。賣家因此拿到本應扣除的 90% 退款金額，而系統沒有任何訊號。**這正是這個測試存在的理由**：沒有一個會失敗的測試，這種錯誤在生產環境只會表現為「帳差了一點」。

同一測試的第二個案例（APPROVED 結算單 → 產生 10 張調整單）**修復前就通過**，證實純 INSERT 路徑本來就安全，也證明這組測試有鑑別力、不是全部亂紅。

### 修復

沿用 S102／S103 的原子 UPDATE（`SettlementStatementRepository.applyRefundDeduction`），兩個金額欄位在同一敘述內相對增減。

**本表無 `version` 欄位，故不需比照 S103 推進版號**——S103 記下的「原生 UPDATE 必須推進 `@Version` 版號」是條件性規則，不是無條件的。

服務層刻意**不再呼叫任何 setter**：`statement` 仍在本交易的持久化上下文中，只要碰了 setter，Hibernate 的髒檢查就會在提交時把整列寫回、覆蓋原生 UPDATE 的結果——競態原封不動，等於白修。

### 既有測試為何沒抓到

`SettlementAdjustmentServiceTest` 的 6 個案例全部 mock 掉 `SettlementStatementRepository`，在單執行緒中依序回放 stub，讀後寫的窗口根本不存在。其中兩個案例斷言的是「**記憶體物件上的數字對不對**」——那正是**在缺陷存在時照樣全綠**的斷言。

本輪把它們改為斷言「以正確的 delta 呼叫了原子敘述、且不再走 `save()`」，並在註解寫明併發正確性由整合測試負責。承 Sprint 97「所有相關測試都用固件繞過同一段邏輯」。

---

## 4. DEF-054：評價「有幫助」的兩個獨立缺陷

### 缺陷一：無去重（不是併發問題）

```java
int currentVotes = votes.getOrDefault(userIdStr, 0);
votes.put(userIdStr, currentVotes + 1);
int totalVotes = votes.values().stream().mapToInt(Integer::intValue).sum();
```

欄位註解自己就寫著 `// userId -> vote count`，全專案沒有任何防重複檢查。端點是 `@PreAuthorize("isAuthenticated()")`，**任何登入者可無限次遞增任意評價的 `helpfulCount`**。

這不只是「規格未定義」——它與前端契約直接衝突：

- `ReviewList.tsx:101` 顯示 `{review.helpfulCount} 人覺得有幫助`（**人數**語意）
- `helpfulCount` 是 `ReviewSearchCriteria.HELPFUL_COUNT` 的**排序欄位**

也就是說，UI 宣稱的「N 人」可以被單一使用者灌到任意大，且會改變評價排名。

規格 `IT-M08-203` 只寫「POST 一次 → helpfulCount+1」，未涵蓋重複投票。**每人一票（冪等）的語意由使用者拍板**（承 S100 起「PRD 未定義算法時交由使用者裁定」的慣例）。

補充事實：前端目前**沒有任何投票按鈕**，只顯示數字。所以這不是一般使用路徑的 bug，而是 API 層的操縱向量。

### 缺陷二：讀後寫競態

整份 JSON map 被讀出、記憶體改完再整份寫回，`Review` 無 `@Version`。

### 🔴 紅燈實測（兩個案例各自對應一個缺陷）

```
同一使用者連投 5 次   → expected: 1  but was: 5    （去重缺陷）
10 位相異使用者併發   → expected: 10 but was: 2    （競態，8 票被靜默覆蓋）
```

### 修復

`ReviewRepository.registerHelpfulVote` 以單一 JSONB 敘述同時解決兩者：

```sql
helpful_votes = COALESCE(helpful_votes, '{}'::jsonb) || jsonb_build_object(CAST(:userId AS text), 1)
helpful_count = (SELECT COUNT(*) FROM jsonb_object_keys(<同上合併結果>))
```

- **冪等**：`||` 對同一 key 覆寫成 1，重複呼叫不改變任何值
- **原子**：單一敘述，由資料庫序列化
- `helpful_count` 取合併後的 key 數 ＝ **相異投票人數**，與前端「N 人」一致

動手前先對測試 DB 直接驗證過 JSONB 語意（`u1` 投一次→1 key、投兩次→仍 1 key、`u1+u2`→2 keys），而不是寫完再看測試結果。

---

## 5. 過程中的兩個自我修正

1. **第一次的紅燈不是紅燈。** `M08ReviewHelpfulVotingIntegrationTest` 首次執行時兩個案例都失敗，但失敗原因是 `Review.getIsAnonymous()` 為 null 而 NPE——我的 seeding 少給了 `is_anonymous`／`is_handled`。**本機測試 DB 由 `ddl-auto=update` 建表，沒有 Flyway migration 上的 `DEFAULT false`**，省略欄位就會留 null。修正 seeding 後才拿到真正的斷言失敗。**「測試失敗了」不等於「缺陷被證實了」**，紅燈必須紅在正確的斷言上。
2. **`seedUser` 的回傳型別**：一開始回傳 `UUID`，但 `Listing.builder().owner(...)` 需要 `User` 實體，編譯期即擋下。
3. **🔴 `::jsonb` 在帶具名參數的原生查詢裡不能用。** 第一版 SQL 用 PostgreSQL 慣用的 `'{}'::jsonb`，執行時報 `syntax error at or near ":"`——**Hibernate 把 `::` 的第一個冒號當成具名參數前綴吃掉**，送到資料庫的是 `'{}':jsonb`。改用 `CAST('{}' AS jsonb)` 後正常。已寫入該方法的 javadoc，避免日後有人「順手」改回慣用寫法。

   值得注意的是 §4 動手前對測試 DB 直接驗證 JSONB 語意那一步**沒有攔到這個問題**——因為當時是用 psql 直接跑，沒有經過 Hibernate 的參數解析。**驗證要驗在真正的執行路徑上**，隔一層就可能漏掉隔那一層自己的問題。

---

## 6. 範圍外（延後）

| # | 項目 | 理由 |
|---|------|------|
| 1 | 🟡 DEF-055（`incrementViewCount` ×3） | 同型且修法機械化，但屬**排名／分析**而非金額或安全；本輪已修兩處生產邏輯，依 Rule 3 不順手擴大。證據已完整記錄，可獨立成一輪 |
| 2 | 🟢 DEF-056（`ChatService` 未讀計數） | 自癒型缺陷，低優先 |
| 3 | 🟡 DEF-051（ERP 側庫存讀後寫） | 承 S103，維持不排程（有 `@Version`，無資料正確性風險） |
| 4 | DEF-047 / DEF-048 / DEF-052 | 續列 |
| 5 | 第九輪 PRD 全文掃描 | 續列 |

---

## 7. 方法論教訓

1. **當一個模式連續三輪出現，先量它的普遍性，再決定逐案修還是系統性處理。** 本輪一開始就問「59 個實體裡有幾個有 `@Version`」，答案（2 個）直接改變了判準，也讓 S103 結論的適用範圍浮現——**有樂觀鎖時失效是「大聲拋例外」，沒有時是「靜默丟資料」，方向相反**。若沿用 S103 的心智模型去看結算單，會嚴重低估。
2. **偽陽性的排除理由必須寫下來。** 掃描出的 7 個候選有 2 個經查證不是競態。如果只記錄「修了哪些」，下一輪掃描會再把它們撿回來重查一次；更糟的是可能有人直接把它們「修」成原子 UPDATE，增加複雜度卻沒有換到任何東西。
3. **「零呼叫者」是一個獨立於嚴重性的維度。** `MediaService.incrementUsageCount` 的競態完全成立，但它沒有生產呼叫者、欄位也不參與任何判斷。承 S103 的教訓再進一步：樣式比對找對位置之後，**除了「後果是什麼」，還要問「這條路徑今天有沒有人走」**。
4. **紅燈紅在哪個斷言上，跟紅不紅一樣重要。** 見 §5.1。
5. **前端的顯示文字是一份可查證的規格。** 本輪判定 markHelpful 為缺陷（而非單純「規格未定義」）的關鍵證據，是 `ReviewList.tsx` 寫的「N **人**覺得有幫助」——PRD 沒說的事，UI 已經對使用者說了。

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: S102／S103 Action Items 的「橫向掃描剩餘的計數／額度型欄位」
