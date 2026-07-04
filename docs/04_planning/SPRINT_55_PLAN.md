# Sprint 55 計劃 / Sprint 55 Plan

> **Sprint 編號**: Sprint 55
> **期間**: 2027-11-21 ~ 2027-12-04 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2409）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 主軸選擇依據 | ✅ 活躍延後項目中，AI-2416（Phase D-2 分潤）需 Phase D-1 帳戶已於正式環境上線才能評估（外部前置條件，本專案尚未 push/部署，不具備），暫不可執行；AI-2409（定價計算器統一評估，P4）為下一個可自主執行的工程評估項目 | 依優先順序 + 可執行性篩選，非任意選擇 |
| Sprint 類型 | ✅ Spike（評估型，比照 Sprint 45 AI-2406 / Sprint 49 backlog #10 模式）：產出決策文件，**不改 production code**，無需 PO 業務語意決策才能開始 | 若評估後發現需行為變更，比照既有模式另立新 AI 項目待 PO 決策 |
| Push 狀態 | ⏸️ 本地領先 origin/main（累積 S41~S54），依先前決策維持批次 push，本 Sprint 收尾後持續累積 | 不影響本 Sprint 開發 |

---

## 1. Sprint 55 目標

> **主題**: 定價計算器統一評估——PRODUCT/ROOM 兩套計算器分歧探勘

`PricingService` 存在兩套獨立的規則套用邏輯：ROOM 用 `calculateAdjustment`（含 stay-based 語意：入住天數、下單日提前/臨近天數判斷），PRODUCT 用 `applyProductRule`/`applyProductMarkup`（單日語意）。Sprint 48（AI-2406c）已對齊兩者的 config key（`price`/`multiplier`/`weekendMultiplier`），但底層邏輯仍是兩套獨立實作，未合併。本 Sprint 探勘分歧程度、評估合併風險與效益，並檢查是否存在因兩套邏輯不一致而產生的規則選取語意落差，產出決策文件供未來評估是否落地。

---

## 2. User Story

### US-001：定價計算器分歧探勘 + 決策文件（AI-2409，Spike）

> **SP**: 3 | **優先級**: P4 | **狀態**: 📋 Ready

**AC-001-1**: 逐行比對 `PricingService.calculateAdjustment`（ROOM，`:432-509`）與 `applyProductRule`/`applyProductMarkup`（PRODUCT，`:577-614`），列出結構性差異表（支援的 `PricingRuleType`、config key、回傳型別、是否需要 `nights`/`bookingDate` context）。

**AC-001-2**: 確認 Sprint 48（AI-2406c）對齊 config key 後，`MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別是否已達成 ROOM/PRODUCT 語意等價（相同 config key、相同計算公式）。

**AC-001-3**: 探勘 `EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 三型別（stay-based，僅 ROOM 有專屬 gating 邏輯：`minDaysAhead`/`minNights`/`maxDaysAhead`）在 PRODUCT 端的實際行為——確認 `applyProductRule` 對這三型別**無專屬處理**，會落入通用 `discountPercent` fallback（若規則含此 config key），**完全略過原本的 gating 條件**；並確認前端 `PricingRuleList.tsx` 建立規則表單僅支援 `roomListingId`（無 `listingId` 欄位），故此落差**目前僅能透過直接 API 呼叫觸發，無法透過現有 UI 觸發**（降低實際曝險）。

**AC-001-4**: 產出決策文件 `docs/06_quality/PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`（比照 `PRICING_MECHANISM_UNIFICATION.md` 格式），列出：分歧結構表、AC-001-3 發現的落差、合併選項比較（維持現狀 / 抽共用 helper 統一三個已對齊型別 / 全面合併含 stay-based 型別）、建議與待決事項。

**AC-001-5**: 本 Sprint **不修改 production code**（純評估/文件產出），與 Sprint 45 AI-2406 探勘階段、Sprint 49 backlog #10 評估模式一致。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 定價計算器分歧探勘 + 決策文件（AI-2409，Spike）| 3 | P4 |

> **Velocity 參考**：Spike 型 Sprint 歷史 SP 偏輕屬合理（S45 定價區決策+清理 5 SP、S49 金流評估 spike 5 SP）；本 Sprint 純評估無實作，3 SP 符合 `DEFERRED_ITEMS_TRACKER.md` 原估。

---

## 4. Definition of Done

- [ ] US-001：`PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md` 完成，涵蓋結構性差異表 + AC-001-3 落差發現 + 合併選項比較 + 建議
- [ ] 不改動 production code；既有測試不受影響（不需重跑全量回歸，僅需確認無檔案異動觸發編譯風險）
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2409 狀態更新（依評估結論分類：已決策待實作 / 續留評估）
- [ ] Sprint 55 Review / Retro / Release Notes + trackers

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 決策文件 | `docs/06_quality/PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`（新）|
| 文件更新 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md`（AI-2409 狀態）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無程式碼變動、無前端變動**（純評估型 Spike）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
