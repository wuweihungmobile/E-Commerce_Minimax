# Release Notes - v2027.09.11-01 (Sprint 49)

**發布日期**: 2027-09-11（規劃）／實作完成 2026-07-03
**發布類型**: Docs（決策 spike：真實金流評估；無 production code、無 schema 變更）
**Sprint**: Sprint 49
**狀態**: ⏳ 待 push（本 Sprint 2 commit；push 債累積 S41~S49，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 49 主題：**真實金流（Stripe）上線評估——決策先行 spike（backlog #10）**。M12 進階定價收官後轉入平台變現關鍵評估。本 Sprint 產出金流整合評估/決策文件，**不寫 production code、無 schema 變更**（比照 S45 決策 sprint）。揭穿「Stripe 已整合」假象——實際上線為純 Mock，Stripe 程式碼為 S14/S21 遺留的**未接線孤兒抽象層**。backlog #10 實作（13 SP + 外部依賴）待 PO 決策後分階段另立。

---

## 文件 / 決策 📋

- **真實金流整合評估（backlog #10，US-001+US-002，spike）**：[PAYMENT_INTEGRATION_ASSESSMENT.md](../07_design/PAYMENT_INTEGRATION_ASSESSMENT.md)——ADR 候選，記錄：
  - **§現況盤點**：兩套並行付款程式碼（上線純 Mock：PaymentService/PaymentStateService；孤兒 Gateway 抽象層：PaymentGatewayFactory/StripePaymentGateway **無人注入**）+ real/stub/missing 速查表（檔案:行號佐證）。
  - **§缺口**：接回 gateway + 補 stub（confirm/refund/status/webhook 事件）+ Stripe DB 欄位（pi_id/client_secret/charge_id/refund_id）+ 狀態機 + 非同步對帳。
  - **§分階段路線**：Phase A 卡片付款 MVP（5 SP）→ B webhook 驅動狀態（3 SP）→ C 退款（2 SP）→ D 分帳/提現（5+ SP）。
  - **§mock↔real toggle**（PAYMENT_PROVIDER 按租戶灰度）、**§分帳/提現**（Stripe Connect vs 手動 settlement）、**§前端 Stripe.js 選型**（Checkout PCI SAQ-A vs Elements）、**§風險**（webhook 冪等/對帳一致性/PCI/3DS）。
  - **§待 PO 決策 6 項** + **§後續實作 US 建議**（AI-2410~2413 依 Phase A~D，含外部依賴）。

## 測試 / 驗證 ✅

- **無 production code / schema 變更** → 不跑 validate-e2e（無 code 可驗）；既有測試狀態沿用 S48（後端單元 22 + 整合 54 + validate-e2e 53 passed/0 fail，皆綠）。
- catch(Exception) / @Deprecated 計數：維持 0（未動 code）。

## 技術決策 / 已知限制 ⚠️

- **spike 定位（誠實揭露 Rule 12）**：產出為決策文件、非可運行功能；真實金流實作（backlog #10，13 SP + 外部依賴）分 AI-2410~2413 另立。
- **揭穿孤兒死碼**：Stripe gateway 抽象層自 S14/S21 起即未接線（死碼）；createPaymentIntent + webhook 驗簽已真接線可複用。
- **分帳/提現為獨立大主題**：Connect vs 手動為重大商業/架構決策，本 spike 只界定選項。
- **外部依賴為排程關鍵路徑**：Stripe 帳號 / Connect onboarding / KYC / 公開 webhook 端點。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V58）。實作階段（Phase A）將需 migration（payments 加 Stripe 欄位）。

## 內含 Commit（Sprint 49）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 49 Plan | bea0a2e | 真實金流評估 spike（2 US / 5 SP）|
| US-001+US-002 backlog #10 | 11f1a53 | PAYMENT_INTEGRATION_ASSESSMENT.md（現況/缺口/路線/分帳/前端/決策）|
| Sprint 49 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
