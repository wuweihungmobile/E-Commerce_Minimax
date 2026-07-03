# Sprint 49 計劃 / Sprint 49 Plan

> **Sprint 編號**: Sprint 49
> **期間**: 2027-08-29 ~ 2027-09-11 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: PRODUCT_BACKLOG #10（M07 真實金流串接，13 SP + 外部依賴，建議單獨 Sprint 評估）+ S49 金流現況探勘
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**真實金流評估 spike（backlog #10）**」——決策先行，產出評估/決策文件，**不寫 production code**（比照 S45 spike 模式）

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「真實金流評估 spike」 | 產出評估/決策文件，實作另立 |
| S48 狀態 | ✅ 已完成（4 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S48（8 Sprint）；**M12 進階定價全面收官** |
| 技術現況已調查 | ✅ Explore Agent 全面盤點金流版圖 | 兩套並行付款程式碼；real/stub/missing 已釐清 |
| **🔴 核心發現：兩套並行付款程式碼** | 🔴 (1) 上線中純 Mock（`PaymentService` + `PaymentStateService`，MOCK-xxxx）(2) 孤兒 Gateway 抽象層（`PaymentGatewayFactory` + `StripePaymentGateway`，**無人注入**，S14/S21 遺留死碼）| 「真實上線」≠「填金鑰」，需接回孤兒層 + 補 stub + 補 DB + 補 webhook 事件 + 補前端 |
| **已真實接線** | ✅ Stripe SDK 24.3.0（pom）、`createPaymentIntent`（真呼叫 Stripe）、webhook 簽章驗證（真 HMAC-SHA256）、金鑰 env 設定、WireMock 整合測試 | 這些是可複用的基礎 |
| **stub（假實作）** | 🔴 `confirmPayment` / `processRefund`（假 re_ id）/ `getPaymentStatus`（讀本地）/ webhook 事件處理（驗簽後只回 OK）/ LinePay | 需補齊 |
| **完全缺** | 🔴 gateway 接入主流程、Stripe 專屬 DB 欄位（pi_id/client_secret/charge_id）、付款非同步/MQ 對帳、前端 Stripe.js/Elements、分帳/提現真實轉帳（Stripe Connect payout）、mock↔real toggle | 這是評估要規劃的路線 |
| **既有 settlement 模組** | ⚠️ `core/settlement/`（對帳單 + 貸項通知，V35/V36）但**無真實轉帳/payout** | 分帳評估需銜接此模組 |
| schema 影響 | ✅ **本 spike 無 schema 變更**（只產文件）；實作階段將需 migration（payments 加 Stripe 欄位）| Flyway 維持 V58 |
| 產出型態 | ✅ 決策文件（ADR 候選），**不改 production code、不加測試** | 比照 S45（CALENDAR_OPEN_WINDOW_ASSESSMENT.md）|
| push 前置 | ✅ 本 Sprint 純文件 → push 相對低風險（仍承 S41~S48 債）| 承累積後徵詢 |

---

## 1. Sprint 49 目標

> **主題**: 真實金流（Stripe）上線評估——盤點現況、界定缺口、提出分階段實作路線與關鍵決策，供 PO 拍板後另立實作

平台付款目前為純 Mock（明文「不會實際扣款」），但已有 S14/S21 遺留的 Stripe scaffolding（部分真接線、部分 stub、整體未接入主流程）。backlog #10（真實金流，13 SP + 外部依賴）為變現關鍵大項。本 Sprint 以 **spike** 產出金流整合評估/決策文件——盤點「兩套並行程式碼」現況、界定真實上線缺口、提出分階段路線（card payment → webhook 驅動狀態 → refund → 分帳/提現）、關鍵架構決策（Stripe Connect vs 手動分帳、Stripe.js 選型、mock↔real toggle）與風險（PCI、webhook 冪等、對帳一致性），供 PO 決策後另立實作 US。**本 Sprint 不寫 production code、無 schema 變更。**

---

## 2. User Stories

### US-001：後端真實金流接線評估 + 分階段實作路線（P3）（backlog #10 spike）

> **SP**: 3 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-001-1**: 產出決策文件 `docs/07_design/PAYMENT_INTEGRATION_ASSESSMENT.md`（ADR 候選），**§後端現況盤點**：記錄「兩套並行付款程式碼」（上線 Mock：PaymentService/PaymentStateService；孤兒 Gateway 抽象層：PaymentGatewayFactory/StripePaymentGateway 無人注入）；附 real/stub/missing 速查表（含檔案:行號佐證）。

**AC-001-2**: **§真實上線缺口**——明列缺口：接回孤兒 gateway 抽象層、補齊 stub（confirmPayment/refund/getPaymentStatus/webhook 事件處理）、payments 表加 Stripe 專屬欄位（pi_id/client_secret/charge_id/refund_id）+ 狀態（requires_action/processing/partially_refunded）、付款非同步/webhook 驅動狀態更新、`STRIPE` PaymentMethod。

**AC-001-3**: **§分階段實作路線**——提出建議分期（如 Phase A：接 gateway + 卡片付款【createPaymentIntent 已真接線】；Phase B：webhook 事件驅動狀態更新【簽章驗證已真、缺事件解析】；Phase C：refund 真接線；Phase D：分帳/提現），每期範圍 + 估 SP + 相依 + 風險。

**AC-001-4**: **§mock↔real 切換策略**——評估 feature toggle（如 `PAYMENT_PROVIDER=mock|stripe`）讓 mock 與 real 並存、可漸進切換 + 灰度；與現有無 payment toggle 的落差。

**AC-001-5**: **§風險與相容**——webhook 冪等（重送）、對帳一致性（同步 vs 非同步）、PaymentIntent 狀態機對映既有 Order/Booking 狀態、既有 Mock 路徑的向後相容/下線策略、金鑰/密鑰管理（env）。

### US-002：分帳/提現 + 前端收單 + 上線策略評估（P3）（backlog #10 spike）

> **SP**: 2 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-002-1**: 於 `PAYMENT_INTEGRATION_ASSESSMENT.md` 增 **§分帳/提現（payout）**——評估 **Stripe Connect（marketplace 自動分帳/提現）vs 手動分帳（沿用既有 `core/settlement/` 對帳單 + 手動轉帳）**；含各自架構影響、對既有 settlement 模組（SettlementStatement/CreditNote，V35/V36）的銜接、多租戶（賣家收款帳戶）語意、合規（KYC/onboarding）。

**AC-002-2**: **§前端收單評估**——Stripe.js 收單選型比較：**Elements（自建卡片 UI，PCI SAQ-A EP）vs Checkout（Stripe hosted，PCI SAQ-A）vs Payment Links**；對既有 Mock 付款 UI（orders/[id] 「確認付款（模擬）」）的取代路線；前端無 @stripe 依賴的導入成本。

**AC-002-3**: **§待 PO 決策事項**——彙整需 PO 拍板的關鍵決策（Connect vs 手動、分期範圍與優先、Stripe.js 選型、mock 下線時機、上線 gating），每項附建議 + 取捨。

**AC-002-4**: **§後續實作 US 建議**——依分期提出可另立的實作 US（backlog #10 的 13 SP 拆解），標記相依與外部依賴（Stripe 帳號/Connect onboarding/合規）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端真實金流接線評估 + 分階段路線（spike）| 3 | P3 |
| US-002 | 分帳/提現 + 前端收單 + 上線策略評估（spike）| 2 | P3 |
| **承諾合計** | | **5 SP** | |

> **Velocity 參考**：S44=8, S45=5, S46=8, S47=7, S48=8。**本 Sprint 5 SP**，決策/spike 型（偏輕，比照 S45 決策 sprint）。**產出為決策文件而非可運行功能**——backlog #10 實作（13 SP + 外部依賴）待決策後另立。M12 收官後轉入平台變現關鍵評估。

---

## 4. 執行順序

```
US-001 後端評估（探勘結論已備 → 撰寫 §現況盤點 + §缺口 + §分階段路線 + §toggle + §風險）
   ↓ 後端評估章節完成
US-002 分帳/前端/策略評估（§Connect vs 手動 + §Stripe.js 選型 + §待 PO 決策 + §後續實作 US）
   ↓ 文件完整
收尾（Review / Retro / Release Notes + trackers）；於檢查點向 PO 呈現決策事項
```

**強制**：本 spike **不改 production code**；文件須基於探勘的檔案:行號佐證（避免臆測）。完成後向 PO 呈現 §待決策事項，實作另立。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 低估「真實上線」複雜度（誤以為填金鑰即可）| 探勘已揭露兩套並行程式碼 + stub/missing；文件明列缺口與分階段，避免一次到位的高風險 |
| 孤兒 gateway 抽象層品質未知（是否值得接回 vs 重寫）| 文件評估「接回 vs 重寫」取捨；createPaymentIntent/webhook 驗簽已真接線可複用 |
| 分帳/提現（Connect）為獨立大主題，可能吃掉 spike 焦點 | US-002 界定為「評估選項」非「設計細節」；Connect vs 手動的決策交 PO |
| spike 產出為文件、SP 偏輕（決策型）| 已於本計劃誠實揭露；比照 S45 決策 sprint；實作 13 SP 另立 |
| PCI 合規範疇易被忽略 | US-002 明列 Stripe.js 選型的 PCI SAQ 等級差異（Elements vs Checkout）|
| 純文件 → push 相對低風險，但仍承 8 Sprint 債 | 檢查點徵詢後決定 push 時機 |

---

## 6. Definition of Done

- [ ] US-001：`PAYMENT_INTEGRATION_ASSESSMENT.md` 含 §現況盤點（real/stub/missing 表 + 檔案佐證）+ §缺口 + §分階段路線（Phase A~D + SP/相依/風險）+ §mock↔real toggle + §風險相容
- [ ] US-002：文件增 §分帳/提現（Connect vs 手動）+ §前端 Stripe.js 選型（Elements/Checkout/Links + PCI）+ §待 PO 決策事項 + §後續實作 US 建議
- [ ] **不改 production code、無 schema 變更**；既有測試不受影響（本 spike 不跑 validate-e2e，因無 code 變動；如收尾需要可跑一次確認綠）
- [ ] Sprint 49 Review / Retro / Release Notes + trackers 更新
- [ ]（檢查點）向 PO 呈現 §待決策事項；承 S41~S48 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 金流整合評估決策文件 | `docs/07_design/PAYMENT_INTEGRATION_ASSESSMENT.md`（本 Sprint 主產出）|
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

> **本 Sprint 不修改**：`PaymentService` / `PaymentStateService` / `StripePaymentGateway` / payments 表 / 前端付款 UI（皆為評估對象，實作另立）。

---

## 8. 🔴 待使用者（PO）確認點

1. **範圍**：真實金流評估 spike = US-001 後端接線評估 + 分階段路線（3 SP）+ US-002 分帳/前端/策略評估（2 SP）= **5 SP**，**產出決策文件、不寫 production code**。是否核准?
2. **spike 型 sprint**：本 Sprint 產出為評估/決策文件（非可運行功能），SP 偏輕（決策密集）；backlog #10 實作（13 SP + 外部依賴）待決策後另立。是否確認此定位?
3. **評估深度界線**：本 spike 產「選項比較 + 建議 + 分階段路線」，**不做**詳細 API 設計/schema 設計/PoC 程式碼（那屬實作階段）。是否同意此深度?
4. **關鍵決策預告**：文件將把「Stripe Connect vs 手動分帳」「Stripe.js 選型」「分期範圍與優先」「mock 下線時機」列為 §待 PO 決策——本 Sprint 只界定選項，決策於檢查點/後續。是否同意此協作方式?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
