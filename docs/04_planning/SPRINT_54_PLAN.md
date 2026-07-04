# Sprint 54 計劃 / Sprint 54 Plan

> **Sprint 編號**: Sprint 54
> **期間**: 2027-11-07 ~ 2027-11-20 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: Sprint 53 Retro 下一 Sprint 候選（AI-2407）+ `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 延後項目 + 本 Sprint 前置程式碼調查
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2407 定價規則選取語意評估**」為主軸；PO 已就同優先級 tie-break 業務語意做出決策（見下方前置條件確認）

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Tie-break 業務語意 PO 決策 | ✅ **後建立者優先**（priority 相同時，較晚建立的規則覆蓋較早建立的規則）| 2026-07-04 PO 決策（Sprint 54 規劃前徵詢） |
| Range 查詢語意修正方向 | ✅ `findActiveRulesForDateRange` 由 containment 改為 overlap（規則只要與住宿區間有重疊即納入候選，逐日精準套用仍由既有 `isRuleApplicable` 逐日檢查把關）| 前置調查確認 `isRuleApplicable`（`PricingService.java:363-372`）已對每日做精準 `validFrom`/`validTo` 檢查，range 查詢僅是候選規則的**前置篩選**，不影響逐日精準性 |
| 現況調查 | ✅ 完成（本 Sprint 前置）| `PricingRuleRepository.findActiveRulesForDateRange`（`:29-36`）JPQL 無 `ORDER BY`；`PricingService.calculatePrice`（`:197-201`）與 `getEffectivePrice`（`:539-543`）皆僅 `Comparator.comparingInt(PricingRule::getPriority).reversed()`，同 priority 時無次要排序鍵，實際順序依資料庫回傳順序（未定義），**目前並無真正落實任何 tie-break 規則** |
| 可複用欄位 | ✅ `PricingRule.createdAt`（`Instant`，`PricingRule.java:83-84`，`@PrePersist` 手動賦值 `:89-93`）已存在，可直接作為 tie-break 次要排序鍵，**免 migration**（schema-free）| |
| 既有空斷言測試 | 🔴 **已確認**：`PricingServiceTest.java:346-400` 的 `calculatePrice_samePriority_laterRuleWins()`（UT-M12-009）標題聲稱驗證「後建立覆蓋」，但斷言僅 `assertThat(response).isNotNull()`（`:398-399`），並未驗證實際生效規則，需在本 Sprint 修正為真斷言 | 誠實揭露：這是一個標題與斷言不符的既有測試，非本 Sprint 新增的缺陷 |
| push 狀態 | ⏸️ 本地領先 origin/main 3 個 commit（累積 S41~S53），2026-07-04 PO 決策**先規劃 Sprint 54、稍後再一併 push**（維持批次 push 慣例）| 不影響本 Sprint 開發，收尾時一併徵詢 push |

---

## 1. Sprint 54 目標

> **主題**: 定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實

`PricingService` 的規則選取邏輯存在兩個語意缺口：(1) `findActiveRulesForDateRange` 以 containment 語意要求規則覆蓋整段住宿區間，導致部分晚數才生效的 SEASONAL 等規則被整批排除在候選之外；(2) 同 priority 規則彼此之間沒有明確的次要排序依據，實際生效的規則取決於資料庫未定義的回傳順序。本 Sprint 修正查詢語意為 overlap（候選層），並落實「後建立者優先」的 tie-break（排序層），使 ROOM（`calculatePrice`）與 PRODUCT（`getEffectivePrice`）兩處規則選取行為皆明確、可預期、可測試。

---

## 2. User Stories

### US-001：`findActiveRulesForDateRange` containment → overlap 查詢語意修正（P2）（AI-2407 之一）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: `PricingRuleRepository.findActiveRulesForDateRange`（`:29-36`）JPQL 由 `p.validFrom <= :startDate AND p.validTo >= :endDate`（containment）改為 `p.validFrom <= :endDate AND p.validTo >= :startDate`（overlap）；`findActiveRulesForDate`（單日查詢）與 `getEffectivePrice` 所用之 `findByListingIdAndIsActiveTrue` + stream 過濾（單日語意，`:539-543`）不受影響、不修改。

**AC-001-2**: 確認 `calculatePrice` 逐日迴圈中 `isRuleApplicable`（`:363-372`）的逐日精準檢查邏輯維持不變——overlap 查詢只放寬候選規則的前置篩選範圍，實際每日是否套用規則仍由 `isRuleApplicable` 逐日精準判斷把關，不改動此方法。

**AC-001-3**: 測試——新增/修正整合測試涵蓋「SEASONAL 規則僅覆蓋多晚住宿其中部分晚數」情境：修正前該規則因 containment 查詢被整批排除、對應晚數無法套用；修正後該規則進入候選、僅在其 `validFrom`~`validTo` 涵蓋的晚數生效，其餘晚數維持 basePrice 或其他適用規則。既有全區間覆蓋規則（`validFrom`~`validTo` 涵蓋整段住宿）行為不變（overlap 是 containment 的超集，不會排除原本就會命中的規則）。

**AC-001-4**: 既有 `PricingServiceTest`、`BookingServiceDynamicPricingTest` 全數不退步；`make validate-schema` 無漂移（無 migration，schema-free）。

### US-002：同優先級定價規則 tie-break 落實——後建立者優先（P2）（AI-2407 之二）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `PricingService.calculatePrice` 排序邏輯（`:201`）由 `rules.sort(Comparator.comparingInt(PricingRule::getPriority).reversed())` 擴充為 `.thenComparing(PricingRule::getCreatedAt, Comparator.reverseOrder())`（priority 降冪為主鍵，`createdAt` 降冪為次鍵，即同 priority 時較晚建立者排序在前，覆蓋較早建立者）；`getEffectivePrice`（`:542`）比照同一 Comparator 邏輯修正，兩處排序邏輯保持一致。

**AC-002-2**: 修正 `PricingServiceTest.java:346-400`（`calculatePrice_samePriority_laterRuleWins`，UT-M12-009）既有空斷言測試——移除 `// 此測試驗證邏輯存在` 誤導性註解與 `assertThat(response).isNotNull()` 弱斷言，改為驗證 `response` 中實際生效的規則名稱/調整值對應**較晚建立**的規則（測試既有的兩個 priority=5、不同 `createdAt`、不同 `weekendMultiplier` 的 mock 規則已具備驗證條件，僅需補上正確斷言，不需改動測試資料設置）。

**AC-002-3**: 補充 `getEffectivePrice`（PRODUCT）對應的同優先級 tie-break 單元測試（目前該方法無同類測試覆蓋）：兩條 priority 相同、`createdAt` 不同的規則，驗證 `bestRule` 為較晚建立者。

**AC-002-4**: 既有依賴 `calculatePrice`/`getEffectivePrice` 排序結果的測試（ROOM 折扣/漲價、PRODUCT 折扣/漲價、cart 折扣）全數不退步——本次為新增次要排序鍵，不改變單一規則命中或無同優先級衝突時的既有行為。

### US-003：更新 `DEFERRED_ITEMS_TRACKER.md` AI-2407 狀態（不計點）

> **SP**: 不計點 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-003-1**: US-001/US-002 完成後，將 `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 中 AI-2407 自「活躍延後項目」移至「已完成延後項目」，記錄修正內容（overlap 查詢 + createdAt tie-break）與驗證結果。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | `findActiveRulesForDateRange` containment→overlap 修正 | 3 | P2 |
| US-002 | 同優先級 tie-break 落實（後建立者優先）+ 修正空斷言測試 | 3 | P2 |
| **承諾合計** | | **6 SP** | |
| US-003 | 更新 DEFERRED_ITEMS_TRACKER.md | 不計點 | P3 |

> **Velocity 參考** S48=8, S49=5, S50=8, S51=5, S52=5, S53=8，歷史區間 5-8 SP。本 Sprint 承諾 **6 SP**，落於歷史區間內；`DEFERRED_ITEMS_TRACKER.md` 原估 5-8 SP，本次前置調查確認 `isRuleApplicable` 逐日邏輯本身已正確（不需重寫計價核心），實際修正範圍為「查詢語意」+「排序次鍵」兩處局部修正，收斂至區間下緣。

---

## 4. 執行順序

```
US-001（PricingRuleRepository JPQL overlap 修正
   → 部分晚數 SEASONAL 規則整合測試（修正前失敗→修正後通過）
   → 既有 PricingServiceTest/BookingServiceDynamicPricingTest 全數不退步）
   ↓ Range 查詢綠
US-002（calculatePrice + getEffectivePrice 排序 Comparator 補 thenComparing(createdAt, reverseOrder)
   → 修正 UT-M12-009 空斷言為真斷言
   → 新增 getEffectivePrice tie-break 單元測試
   → 既有折扣/漲價相關測試全數不退步）
   ↓ 後端綠
make validate-schema（確認無 migration、schema-free）+ 全量回歸
   ↓
US-003（DEFERRED_ITEMS_TRACKER.md AI-2407 移至已完成）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：先修查詢語意（US-001）確認候選規則集合正確，再修排序次鍵（US-002），避免兩處變更同時進行時難以定位測試失敗根因。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Overlap 查詢放寬候選範圍後，可能有未預期的規則被納入候選並意外生效 | `isRuleApplicable`（`:363-372`）逐日精準檢查不變，overlap 僅放寬候選集合、不放寬實際套用條件；以「部分晚數 SEASONAL」整合測試驗證候選放寬後仍逐日精準套用 |
| 同優先級 tie-break 改變既有依賴「資料庫回傳順序」的隱性行為 | 目前該順序本就未定義（無 `ORDER BY`），任何測試若依賴此隱性順序本身就是脆弱測試；本次變更明確化行為，屬修正而非破壞既有承諾行為 |
| `createdAt` 精度不足（同毫秒內建立多條規則）導致 tie-break 仍不穩定 | `Instant` 為奈秒精度，正常業務操作（人工建立規則）不會在同一奈秒內建立兩條規則；此邊界情況本次不特別處理，如未來出現可另評估以 `id` 作三級排序鍵 |
| 修正範圍波及既有大量定價相關測試（PricingServiceTest、BookingServiceDynamicPricingTest、cart/order 折扣測試）| 執行順序刻意拆兩步（先查詢後排序），每步完成立即跑全量定價相關測試，及早定位問題 |

---

## 6. Definition of Done

- [ ] US-001：`findActiveRulesForDateRange` JPQL 改 overlap；部分晚數 SEASONAL 規則整合測試通過；既有測試不退步；`make validate-schema` 無漂移（schema-free）
- [ ] US-002：`calculatePrice`/`getEffectivePrice` 排序 Comparator 補 `createdAt` 次鍵；UT-M12-009 改為真斷言；新增 `getEffectivePrice` tie-break 測試；既有折扣/漲價測試不退步
- [ ] US-003：`DEFERRED_ITEMS_TRACKER.md` AI-2407 狀態更新為已完成
- [ ] 後端單元 + 真 DB 整合全量回歸 0 fail；`catch(Exception)=0`、`@Deprecated=0`
- [ ] Sprint 54 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 完成後，於徵詢時一併處理累積待 push 之 commit（S41~S54），執行 `make validate-release` 後 push（嚴禁 `--no-verify`）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 Repository | `backend/src/main/java/com/nextkey/ecommerce/domain/repository/PricingRuleRepository.java`（`findActiveRulesForDateRange` JPQL 修正）|
| 後端 Service | `backend/src/main/java/com/nextkey/ecommerce/core/pricing/PricingService.java`（`calculatePrice`/`getEffectivePrice` 排序 Comparator 修正）|
| 後端測試 | `PricingServiceTest.java`（UT-M12-009 修正 + 新增 tie-break/overlap 測試案例）|
| 文件 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md`（AI-2407 移至已完成）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**（純後端定價選取邏輯修正，schema-free）。

---

## 8. 範圍決策紀錄（2026-07-04）

> 以下決策已於 Sprint 54 規劃前徵詢並取得 PO 明確決策：

1. **同優先級 tie-break 業務語意**：採「**後建立者優先**」——PO 於規劃前徵詢時選定，符合既有測試標題原本想表達的意圖（`calculatePrice_samePriority_laterRuleWins`），以 `PricingRule.createdAt` 作次要排序鍵實作，免 migration。
2. **Range 查詢修正方向**：改為 **overlap 語意**（而非維持 containment 另立特殊規則），因調查確認逐日精準套用邏輯（`isRuleApplicable`）本就存在且正確，overlap 只是修正候選規則的前置篩選，風險與改動範圍皆最小。
3. **範圍排除**：AI-2416（真實金流 Phase D-2 分潤）、AI-2414/AI-1903（人工執行項目）均非本 Sprint 範圍，維持 Sprint 53 Retro 原規劃；push 待本 Sprint 收尾時一併徵詢處理。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
