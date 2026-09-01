# Sprint 102 Plan — 優惠券額度佔用改為原子操作（DEF-046）

**Sprint**: Sprint 102
**日期**: 2026-09-01
**AI 編號**: AI-2436
**主題**: 修復 DEF-046——`max_usage_count` 自 Sprint 100 起首次真正生效，但「檢查未達上限」與「遞增使用次數」分屬兩次資料庫往返，中間的讀後寫窗口讓併發結帳能超發限量券，且互相覆蓋的寫入使計數漏計。本輪把額度佔用與退還雙雙下沉為資料庫端的單一條件式 UPDATE。

---

## 1. 缺口盤點結果

### 起點

承接 Sprint 100 記錄、Sprint 101 列為第 1 順位 Action Item 的 **DEF-046**。這是它連續第三輪出現在候選清單上。

### 確認的缺口

| # | 缺口 | 證據 | 嚴重性 |
|---|------|------|--------|
| 1 | **總量額度超發** | `OrderService.resolveValidPromoForCheckout` 呼叫 `promo.isUsageLimitReached()` 做檢查，實際遞增則在稍後的 `commitPromoUsage → PromoService.incrementUsageCount`，後者是「讀出實體 → `+1` → `save()`」。兩者分屬不同 SQL 往返，並行請求可同時通過檢查 | 🔴 限量券可被超發 |
| 2 | **計數漏計（lost update）** | `incrementUsageCount` 以 JPA `save()` 寫回整個實體，兩筆並行更新互相覆蓋，`current_usage_count` 累加次數少於實際發出張數——連「事後對帳發現超發」都做不到 | 🔴 帳目失真 |
| 3 | **每人限用同樣是讀後寫** | `max_usage_per_user` 的檢查（`countByPromoCodeIdAndUserIdAndStatus`）與用券紀錄的寫入之間有同一個窗口。同一買家同時送出兩筆訂單，兩邊都會讀到 0 次而雙雙放行 | 🟠 每人限用可被繞過 |
| 4 | **退還額度也是讀後寫** | `refundPromoUsage` 的 `findById → setCurrentUsageCount(max(0, n-1)) → save`。兩筆用同一張券的訂單同時取消時互相覆蓋，額度只退還一次，該券永久少一次可用額度 | 🟠 額度永久蒸發 |

**紅燈實測（非推論）**：把修復後的實作暫時換回舊語意重跑併發測試——上限 3 的券被 **10 條執行緒全數領走**（超發 233%），不限量券的 10 次遞增在 DB 只累計為 **1**（9 次寫入被覆蓋）。缺口 1 與 2 均為實測確認，不是理論風險。

### PRD 依據與其不足

PRD 定義了 `max_usage_count` / `max_usage_per_user` 的業務語意（上限即上限），但**未定義併發語意**——沒有任何條文說明超發容忍度。DEF-046 的原始記錄因此把「確認業務上可接受的超發容忍度」列為前置需求。

---

## 2. 技術決策（本輪未召開 AskUserQuestion，理由如下）

| 決策點 | 選項 | 裁定與理由 |
|--------|------|------------|
| 修法 | (A) DB 條件式 UPDATE / (B) Redis Lua（複用 Sprint 93 `RateLimitFilter`）/ (C) 悲觀鎖 | **(A) 條件式 UPDATE**。(B) 會讓額度出現**兩個真相來源**（Redis 計數 vs `promo_codes.current_usage_count`），退還、對帳、Redis 重啟都得再補一套協調機制，成本與風險都高於它解決的問題；`RateLimitFilter` 用 Redis 是因為限流狀態本來就不該落 DB，與本案不同。(C) 悲觀鎖需多一次 `SELECT ... FOR UPDATE` 往返，效果與 (A) 相同而較慢 |
| 超發容忍度 | 需業務拍板？ | **不需要**。DEF-046 原始記錄把它列為前置需求，是預設修法會帶來「效能 vs 精確」的取捨；(A) 的超發容忍度是**零**，且不需要新基礎設施、不需要新的協調機制——沒有取捨要讓使用者權衡，故未佔用使用者的決策成本 |
| 佔用失敗時的行為 | 拒絕下單 / 靜默改收原價 | **拒絕下單（E-5009）**，沿用 Sprint 100 已確立且已寫入程式碼註解的原則：買家在購物車看到的是折扣後金額，靜默回退原價等同在買家不知情下多收款。非新決策 |
| 是否一併修每人限用與退還（缺口 3、4） | 只修 DEF-046 字面範圍（總量）/ 一併修 | **一併修**。三者是同一個計數器、同一種讀後寫模式，共用同一次修改成本（約 5 行）。只修總量會留下「限量券擋得住、每人限用擋不住」的半套防護，而那正是本專案反覆記取的「在修復缺口的同時親手做出下一個同類缺口」 |

---

## 3. 實作內容

### Repository 層（`PromoCodeRepository`）

新增兩支原生 `@Modifying` 查詢，把判斷條件寫進 SQL：

```sql
-- 佔用：檢查與遞增在同一條敘述內，由 DB 保證不可分割
UPDATE promo_codes
   SET current_usage_count = COALESCE(current_usage_count, 0) + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = :id
   AND (max_usage_count IS NULL OR COALESCE(current_usage_count, 0) < max_usage_count)

-- 退還：相對遞減，GREATEST 保下限
UPDATE promo_codes
   SET current_usage_count = GREATEST(COALESCE(current_usage_count, 0) - 1, 0),
       updated_at = CURRENT_TIMESTAMP
 WHERE id = :id
```

回傳受影響筆數：`1` = 佔用成功，`0` = 已達上限。

**關鍵副作用（本輪防護的一半靠它）**：該敘述同時取得 `promo_codes` 該列的行鎖並持有至交易結束，因此同一張券的併發結帳從這一行起被序列化。

### Service 層（`PromoService`）

- `incrementUsageCount(PromoCode)` → **移除**，改為 `tryConsumeUsageQuota(PromoCode): boolean`。
  刻意不保留舊簽章：舊方法回傳 `void`，呼叫端無從得知額度是否真的取得，留著只會讓下一位呼叫者在毫無徵兆下重新引入同一個競態（與 Sprint 101 移除 `computeDiscount` 舊多載同一個「大聲失敗」理由）。
- 新增 `releaseUsageQuota(UUID)`，與佔用對稱。

### 結帳流程（`OrderService`）

- `commitPromoUsage` 改為：**原子佔用 → 失敗即拋 E-5009 回滾整筆交易 → 鎖內重查每人限用 → 寫入用券紀錄**。
- 每人限用的判斷抽為 `perUserLimitReached(promo, userId)`，由前置檢查與鎖內重查共用。
  - 前置檢查（`resolveValidPromoForCheckout`）保留，但其角色降級為「提早失敗、給精確錯誤訊息」，**不再是額度的把關者**——程式碼註解已寫明，避免日後有人以為檢查過了就安全。
  - 鎖內重查必須排在原子佔用**之後**：行鎖已取得時，並行的另一筆請求要嘛還沒進來、要嘛已提交，此時 `COUNT` 才讀得到真實值。
- `refundPromoUsage` 改用 `releaseUsageQuota`，不再讀出實體改欄位再 `save`。

---

## 4. 測試

### `M11PromoConcurrencyIntegrationTest`（新增，5 個；真實 PostgreSQL + 多執行緒）

**為什麼一定要真實 DB**：DEF-046 的本質是兩次 DB 往返之間的窗口。任何 mock 掉 Repository 的測試都是在單執行緒中依序回放 stub，窗口根本不存在——這正是 Sprint 97 記取的「所有相關測試都用固件繞過同一段邏輯」教訓。本類別以 10 條執行緒、各自獨立交易，直接壓在同一列 `promo_codes` 上。

| # | 案例 | 驗證意圖 |
|---|------|---------|
| 1 | 10 執行緒搶上限 3 的券 → 恰好 3 次成功、DB 計數 = 3 | 不超發**且**不漏計 |
| 2 | 不限量券（`max_usage_count` 為 NULL）→ 10 次全放行、計數 = 10 | 不限量路徑同樣走原子遞增，未退化為讀後寫 |
| 3 | 已達上限的券 → 併發全數被擋，計數不被推過上限 | 邊界 |
| 4 | 計數為 0 時退還 → 不得成為負數 | 下限保護（由 `M11PromoConcurrencyIntegrationTest` 承接，見下） |
| 5 | 佔用 → 退還 → 可再次佔用 | 取消訂單的完整往返 |

**Context 快取**：本類別的 `@SpringBootTest`/`@AutoConfigureMockMvc`/`@ActiveProfiles`/`@MockBean` 組合刻意與 `M11PromoCheckoutIntegrationTest` 完全一致，共用同一個已快取的 Spring context。DEF-049 已量測出整合測試耗時由 context 啟動主導，新類別若順手改動這組註解等於替 CI 再加一次冷啟動。本類別不使用 MockMvc，`@AutoConfigureMockMvc` 僅為對齊快取鍵而保留（已寫入 javadoc）。

### `PromoServiceTest`（-1 / +5，共 27）

移除 `incrementUsageCount_incrementsAndSaves`（方法已不存在）。新增：條件式 UPDATE 影響 1 筆 → `true`；影響 0 筆 → `false`；**不得退回讀後寫**（斷言 `never()).save(...)`，這是 DEF-046 的核心意圖）；`releaseUsageQuota` 走原子遞減；找不到該券時不拋例外（取消流程不因此中斷）。

### `OrderPromoCodeTest`（-1 / +2，共 17）

新增 `UsageQuotaRaceTests`：

- **前置檢查通過但原子佔用失敗** → E-5009、不寫用券紀錄、不清購物車的券（模擬「檢查與遞增之間被搶完」）。
- **佔用成功後鎖內重查發現每人限用已滿** → E-5009（`COUNT` stub 依序回 `0L, 1L`，模擬並行請求剛提交）。

移除 `refundNeverGoesNegative`：下限保護已下沉為 SQL 的 `GREATEST(...)`，mock 掉 Repository 的單元測試已無從驗證。**未靜默刪除**——已遷移為上表第 4 案（真實 DB），並在原處留下說明註解。

### 紅燈驗證（實跑，2026-09-01）

把 `PromoService.tryConsumeUsageQuota` 暫時換回修復前的「`findById` 檢查 → `+1` → `save`」語意後重跑：

| 案例 | 期望 | 舊實作實得 | 結論 |
|------|------|-----------|------|
| `concurrentConsumeNeverExceedsLimit` | 3 | **10** | 上限 3 的券被 10 條執行緒全數領走 |
| `unlimitedPromoGrantsEveryAttemptWithoutLostUpdate` | 計數 10 | **1** | 10 次遞增有 9 次被互相覆蓋 |

另 3 個案例（邊界、下限、往返）在舊實作下仍通過——它們是守衛型斷言，不在紅燈範圍，如實記錄。換回條件式 UPDATE 後 5 個案例全綠。

---

## 5. 驗證結果

### 全量回歸

`cd backend && mvn -o verify`（2026-09-01）：

| 項目 | 結果 |
|------|------|
| 單元測試（surefire） | **1074 tests, 0 failures, 0 errors, 0 skipped** |
| 整合測試（failsafe） | **397 tests, 0 failures, 0 errors, 0 skipped** |
| 合計 | **1471 tests, 0 fail** |
| checkstyle-main / checkstyle-test | 皆通過 |
| BUILD | **SUCCESS** |

### 開發-編譯-測試循環

依 CLAUDE.md 強制規則逐步執行：Repository 改完即 `mvn -o compile`（通過）→ Service/OrderService 改完即編譯 → 單元測試改完即跑 `PromoServiceTest` + `OrderPromoCodeTest`（27 / 17 全綠）→ 併發整合測試單獨跑（5 全綠）→ 紅燈驗證 → 最後全量回歸。無累積開發。

### 測試計數（以 `@Test` 實數核對，非憑印象）

| 檔案 | HEAD | 本輪 | 淨變化 |
|------|------|------|--------|
| `PromoServiceTest` | 23 | 27 | +4（新增 5、移除 1） |
| `OrderPromoCodeTest` | 16 | 17 | +1（新增 2、移除 1） |
| `M11PromoConcurrencyIntegrationTest` | — | 5 | +5（新建） |
| **合計** | | | **新增 12、移除 2、淨 +10** |

**誠實揭露一項對不上的數字**：本輪單元測試總數 1074，而 Sprint 101 文件記載的基準是 1068，差 +6；但上表以 `@Test` 實數核對，本輪只增加 **5** 個單元測試（唯二被改動的測試檔已用 `git diff --name-only` 確認）。也就是說**同一套量測條件下的 S101 基準應為 1069，文件記載的 1068 少了 1**。已確認 `M11PromoConcurrencyIntegrationTest` 未被 surefire 重複計入（pom 的 surefire `excludes` 含 `**/*IntegrationTest.java`，本次 run 的 surefire 區段搜尋該類別為 0 次）。此差異不影響本輪正確性，未回頭考證 S101 當時的量測條件，如實記錄。

### 紅燈驗證

已實跑，結果見 §4 末。**這是本輪最重要的驗證**：DEF-046 原記錄的定性是「理論上可超發」，紅燈實測推翻了它——舊實作在 10 條執行緒下，上限 3 的券被全數領走。若沒有實跑，這個缺陷會繼續以「低優先級技術債」的身分留在追蹤表上。

---

## 6. 範圍外（延後）

- 🔴 **DEF-050（新記錄，庫存超賣）**：本輪依 Action Item 做同類競態橫向掃描時，在 `ProductInventoryService.reserveForOrder` 找到**完全同型**的讀後寫——`hasAvailableStock(qty)` 讀 `totalQty - reservedQty` 後，`inventory.reserve(qty)` + `save()` 寫回整個實體。併發下單搶同一 SKU 的最後一件會雙雙通過檢查而超賣，且兩次 `save()` 互相覆蓋使 `reservedQty` 少算；`releaseForOrder`/`deductForOrder` 同樣是讀後寫。**嚴重性高於 DEF-046**（超發的是實體商品而非可補償的折扣額度）。依 Rule 3 不順手擴大範圍——該模組需要自己的併發測試與紅燈驗證，併入本輪會使範圍失控——故記錄為 DEF-050 並列為高優先級，建議排入 Sprint 103。修法可直接複用本輪的條件式 UPDATE。
- **DEF-047（ROOM 訂單套券）**、**DEF-048（混合購物車折扣基數）**：維持延後，本輪未動。
- **DEF-049（整合測試執行時間結構性成長）**：本輪未動，但已依其結論在新測試類別對齊既有 context 快取鍵，避免再加一次冷啟動。本輪新增 1 個整合測試類別（+5 個測試），會使該 job 再逼近上限一些。
- **`PromoCodeUsage` 沒有 `(promo_code_id, user_id)` 的資料庫層唯一約束**：本輪以「行鎖內重查」關掉每人限用的競態，屬應用層防護。DB 層約束無法表達「至多 N 筆」（`max_usage_per_user` 可 > 1），且既有 `REVOKED` 紀錄仍需保留，故不適用唯一索引。記錄為已知的設計取捨，非缺口。
- **前端無需改動（已確認，非略過）**：新增的失敗路徑沿用既有的 `E_5009`，`BusinessException(errorCode, details)` 的訊息為 `errorCode.getMessage() + ": " + details`，即「優惠碼已達使用上限: Promo code sold out during checkout: XXX」；`checkout/product/page.tsx` 的 `extractErrorMessage` 直接顯示 `response.data.message`，買家看到的是可讀的繁體中文訊息。與 Sprint 100 既有的 E-5009 路徑完全一致，故本輪未動前端。
- **ROOM 訂單路徑不受本輪影響**：`createRoomOrder` 完全沒有促銷碼路徑（即 DEF-047），故無額度佔用可競。

---

## 7. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S99 | 6 |
| S100 | 8 |
| S101 | 5 |
| **S102** | **3**（1 個競態修復，涵蓋總量／每人限用／退還三條路徑 + 12 個新測試，含首個多執行緒併發整合測試） |

---

## 8. 下一步 / Action Items

### 8.1 上輪（Sprint 101）Action Items 追蹤

| Sprint 101 列的項目 | 本輪結果 |
|---------------------|---------|
| 1. DEF-046（併發額度競態） | ✅ **本輪完成**。連續 3 輪被列為候選（S100 記錄 → S101 第 1 順位 → S102 執行） |
| 2. DEF-047（ROOM 訂單套券） | ⏳ 未動，續列 |
| 3. DEF-048（混合購物車折扣基數） | ⏳ 未動，續列（需產品確認） |
| 4. DEF-049（整合測試執行時間） | ⏳ 未動，續列並**升為第 1 順位**——本輪又加了 5 個整合測試，該 job 的餘裕再縮 |
| 5. 第九輪 PRD 全文掃描 | ⏳ 未動，續列 |

### 8.2 本輪產出的 Action Items

| # | 項目 | 說明 |
|---|------|------|
| 1 | 🔴 **DEF-050（庫存超賣）** | **本輪橫向掃描已執行並命中**（見 §6）：`ProductInventoryService.reserveForOrder` 是與 DEF-046 完全同型的讀後寫，但超賣的是實體商品。修法可直接複用本輪的條件式 UPDATE，測試可複用 `M11PromoConcurrencyIntegrationTest` 的 race 結構。**建議列為 Sprint 103 首要項目** |
| 2 | DEF-049（整合測試執行時間） | 已連續兩輪列為候選，且本輪又加了 5 個整合測試。DEF-049 的第一步是**量測 context 重建次數**（確認各類別的 `@MockBean`/`@ActiveProfiles` 差異造成多少個獨立 context），量測本身成本低、結論直接決定要不要做平行化/分片 |
| 3 | DEF-047（ROOM 訂單套券，PRD US-010） | 規模較大：需新增 `POST /v2/orders` ROOM 分支的促銷碼契約 + 訂房結帳前端。本輪的原子佔用可直接複用 |
| 4 | DEF-048（混合購物車折扣基數） | 需先確認產品期望（券是否該折抵 ROOM 項目） |
| 5 | 橫向掃描的剩餘範圍 | 本輪只掃了庫存模組即命中。尚未掃：`SettlementAdjustment`、`MediaAsset.incrementUsageCount`、`ReviewService.markHelpful`（DEF-031 已記錄其重複投票問題，但未從併發角度看）等其他計數/額度型欄位 |
| 6 | 第九輪 PRD 全文掃描 | 若上述皆不排入，回到 Sprint 93-101 的方法論繼續找未追蹤缺口 |

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: DEF-046（Sprint 100 記錄，Sprint 101 列為第 1 順位）
