# Release Notes - v2027.12.04-01 (Sprint 55)

**發布日期**: 2027-12-04（規劃）／實作完成 2026-07-04
**發布類型**: Docs-only（定價計算器統一評估，Spike，無程式碼變動）
**Sprint**: Sprint 55
**狀態**: ⏳ 待 push（本 Sprint commit；於檢查點徵詢後連同 S41~S55 一併 push，嚴禁 `--no-verify`）

> Sprint 55 主題：**定價計算器統一評估——PRODUCT/ROOM 兩套計算器分歧探勘**。純 Spike 型 Sprint，產出決策文件，不改動任何 production code。

---

## 文件產出 📄

- **`docs/06_quality/PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`（新）**：探勘 `PricingService` 的 ROOM `calculateAdjustment` 與 PRODUCT `applyProductRule`/`applyProductMarkup` 兩套規則套用邏輯的分歧。確認 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別自 Sprint 48（AI-2406c）起已語意等價（僅程式碼重複）；`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 對 PRODUCT 本無自然語意。發現 PRODUCT 端對這三型別會靜默落入通用折扣 fallback、略過原本的 gating 條件，但因前端無 PRODUCT 定價規則管理 UI，實際曝險低。比較 3 個合併選項並給出建議（抽共用 helper，非急迫）。

## 測試 / 驗證 ✅

- **本 Sprint 無程式碼變動**：既有測試狀態沿用 Sprint 54（後端單元 508 tests + 真 DB 整合 415 tests，0 fail），無需重跑回歸。
- **schema 漂移守門**：無需執行（無 schema 相關變動）。
- **catch(Exception) / @Deprecated 計數**：維持 0（無程式碼變動）。

## 技術決策 / 已知限制 ⚠️

- **純評估，未落地任何合併方案**：選項 B（抽共用 helper 統一三個已對齊型別）評估為低風險、可行，但非急迫，未強制排入本 Sprint 或後續 Sprint，留待未來依容量決定。
- **資料完整性缺口記錄但未修復**：`validateRuleRequest` 未驗證 `ruleType` 與 listing 類型搭配合理性；本文件已記錄，是否修復屬獨立低優先級決定。
- **無需 PO 決策**：本次評估結論不涉及商業語意判斷，純工程風險評估。

## 資料庫遷移 🗄️

- 無。

## 內含 Commit（Sprint 55）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 55 Plan | c339f61 | 定價計算器統一評估計劃（1 US / 3 SP，Spike）|
| US-001 AI-2409 | e1b0ff2 | PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md + DEFERRED_ITEMS_TRACKER.md 更新 |
| Sprint 55 收尾 | （本次）| Review / Retro / Release Notes + RELEASE_TRACKER.md |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
