# Release Notes - v2027.11.20-01 (Sprint 54)

**發布日期**: 2027-11-20（規劃）／實作完成 2026-07-04
**發布類型**: Patch（定價規則選取語意修正；schema-free；後端聚焦）
**Sprint**: Sprint 54
**狀態**: ⏳ 待 push（本 Sprint 3 commit；於檢查點徵詢後連同 S41~S54 一併 push，嚴禁 `--no-verify`）

> Sprint 54 主題：**定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實**。修正 `PricingService` 規則選取的兩個語意缺口：`findActiveRulesForDateRange` 由 containment 改 overlap（修正部分晚數規則被整批排除的漏套問題）；同優先級規則落實明確的「後建立者優先」tie-break（PO 決策，此前依賴資料庫未定義的回傳順序）。

---

## 修復 / 改進 🔧

- **Range 查詢語意修正（AI-2407）**：`PricingRuleRepository.findActiveRulesForDateRange` JPQL 由 containment（`validFrom<=startDate AND validTo>=endDate`）改為 overlap（`validFrom<=endDate AND validTo>=startDate`），修正僅覆蓋部分晚數的規則（如 SEASONAL）被整批排除、無法生效的漏套問題；逐日精準判斷（`isRuleApplicable`）不變。
- **同優先級 tie-break 落實（AI-2407）**：`calculatePrice`（ROOM）與 `getEffectivePrice`（PRODUCT）排序邏輯補上以 `createdAt` 為次要鍵的降冪排序，同 priority 時「後建立者優先」（PO 決策）；沿用既有欄位，免 migration。
- **既有空斷言測試修正**：`PricingServiceTest.UT-M12-009`（標題聲稱驗證「後建立覆蓋」但原斷言僅確認回應非 null）改為驗證實際生效規則的真斷言。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test -Dtest="com.nextkey.ecommerce.core.**"` **508 tests，0 fail**（含 UT-M12-009 修正 + UT-M12-019 新增：`getEffectivePrice` 同優先級 tie-break 驗證）。
- **後端整合（真實 DB）**：`mvn test -Dspring.profiles.active=integration-test` **415 tests，0 fail**（含 `BookingControllerE2ETest` 21 tests 0 fail，新增 API-M06-017：SEASONAL 規則覆蓋部分晚數仍正確套用）。
- **schema 漂移守門（`make validate-schema`）**：無漂移（schema-free，無 migration）。
- **前端變動**：無。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **只修正查詢語意與排序次鍵**：`isRuleApplicable`/`calculateAdjustment` 逐日精準計算核心未改動，本次修正範圍嚴格限定。
- **`createdAt` 精度邊界情況未處理**：同一奈秒內建立多條規則的極端情況（正常人工操作不會發生）本次不特別處理。
- **無 repository 專屬真 DB 測試層**：查詢語意修正的驗證透過既有 `BookingControllerE2ETest`（`@SpringBootTest` + 真 DB）端到端驗證，而非新建 `@DataJpaTest` 等 repository 專屬測試基礎設施。

## 資料庫遷移 🗄️

- 無（schema-free，沿用既有 `PricingRule.createdAt` 欄位）。

## 內含 Commit（Sprint 54）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 54 Plan | 10e988e | 定價規則選取語意修正計劃（2 US / 6 SP）|
| US-001+US-002 AI-2407 | 1ed68d6 | overlap 查詢修正 + tie-break 排序 + 測試修正/新增 |
| Sprint 54 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
