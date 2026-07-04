# Sprint 54 Review / Sprint 54 評審會議

> **Sprint 編號**: Sprint 54
> **期間**: 2027-11-07 ~ 2027-11-20
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `findActiveRulesForDateRange` containment→overlap 查詢語意修正（AI-2407）| 3 | ✅ 完成 |
| US-002 | 同優先級 tie-break 落實——後建立者優先 + 修正空斷言測試（AI-2407）| 3 | ✅ 完成 |
| US-003 | 更新 `DEFERRED_ITEMS_TRACKER.md` AI-2407 狀態（不計點）| — | ✅ 完成 |

**正式承諾 6 SP（US-001~002）全數完成**。修正 `PricingService` 規則選取的兩個語意缺口：range 查詢由 containment 改 overlap（修正部分晚數規則被整批排除的漏套問題）、同優先級規則落實明確的「後建立者優先」tie-break（此前依賴資料庫未定義的回傳順序）。

---

## 2. 交付內容

- **US-001（`PricingRuleRepository`）**：`findActiveRulesForDateRange` JPQL 由 `validFrom<=startDate AND validTo>=endDate`（containment）改為 `validFrom<=endDate AND validTo>=startDate`（overlap）；`isRuleApplicable`（逐日精準判斷）不變，overlap 僅放寬候選規則的前置篩選範圍。
- **US-001（測試）**：新增真 DB 整合測試 `API-M06-017`（`BookingControllerE2ETest`）——SEASONAL 規則僅覆蓋 3 晚住宿中的中間 1 晚，驗證修正後總價含該晚漲價（6000）、修正前會因整批排除而漏套（4500）。
- **US-002（`PricingService`）**：`calculatePrice`（ROOM）與 `getEffectivePrice`（PRODUCT）排序 Comparator 皆補上 `.thenComparing(PricingRule::getCreatedAt, Comparator.reverseOrder())`，同 priority 時較晚建立者排序在前（覆蓋較早建立者）；沿用既有 `PricingRule.createdAt` 欄位，免 migration。
- **US-002（測試）**：修正既有空斷言測試 `PricingServiceTest.UT-M12-009`（標題聲稱驗證「後建立覆蓋」但原斷言僅 `assertThat(response).isNotNull()`）為驗證實際生效規則的真斷言；新增 `UT-M12-019`（`getEffectivePrice` 同優先級 tie-break 單元測試）。
- **US-003（文件）**：`DEFERRED_ITEMS_TRACKER.md` 將 AI-2407 自「活躍延後項目」移至「已完成延後項目」，並新增 Sprint 54 歷史紀錄段落。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn test -Dtest="com.nextkey.ecommerce.core.**"`）| ✅ **508 tests，0 fail**（含 UT-M12-009 修正 + UT-M12-019 新增）|
| 後端整合（真實 DB，`mvn test -Dspring.profiles.active=integration-test`）| ✅ **415 tests，0 fail**（含 `BookingControllerE2ETest` 21 tests 0 fail，含新增 API-M06-017）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（schema-free，無 migration）|
| 前端變動 | 無（本 Sprint 純後端定價選取邏輯修正）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **只修正查詢語意與排序次鍵，未重寫計價核心**：`isRuleApplicable`（逐日精準判斷）與 `calculateAdjustment`（各規則類型調整計算）皆未改動；本 Sprint 修正範圍嚴格限定在「候選規則前置篩選」與「同優先級排序次鍵」兩處。
2. **`createdAt` 精度邊界情況未特別處理**：若同一奈秒內建立多條規則（正常人工操作不會發生），tie-break 仍可能不穩定；如未來出現可另評估以 `id` 作三級排序鍵，本 Sprint 不處理。
3. **既有 repository 級真 DB 測試基礎設施有限**：本專案原無 `@DataJpaTest` 或其他直接對 repository JPQL 做真 DB 驗證的測試層，本次選擇在既有 `BookingControllerE2ETest`（`@SpringBootTest` + 真 DB + `@Autowired PricingRuleRepository`）中新增 API-M06-017 端到端驗證查詢語意修正，而非新建 repository 專屬測試類別（避免新增測試基礎設施超出本次修正範圍）。
4. **範圍決策記錄**：AI-2416（真實金流 Phase D-2 分潤）、AI-2414/AI-1903（人工執行項目）均非本 Sprint 範圍，維持 Sprint 53 Retro 原規劃；push 待本 Sprint 收尾後徵詢處理（累積 S41~S54）。

---

## 5. Demo 重點

- **Overlap 修正生效**：`API-M06-017` 示範 SEASONAL 規則僅覆蓋 3 晚中間 1 晚時，`checkAvailability` 回傳總價 6000（含該晚 2x 漲價），修正前會因規則被整批排除而回傳 4500（無漲價，漏套 bug）。
- **Tie-break 生效**：`UT-M12-009`（ROOM）與 `UT-M12-019`（PRODUCT）皆驗證同 priority=5/10 時，較晚建立的規則覆蓋較早建立的規則，行為不再依賴資料庫未定義的回傳順序。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
