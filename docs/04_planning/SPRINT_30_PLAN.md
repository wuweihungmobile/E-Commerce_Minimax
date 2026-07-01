# Sprint 30 計劃 / Sprint 30 Plan

> **Sprint 編號**: Sprint 30
> **期間**: 2026-12-06 ~ 2026-12-19 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [PRODUCT_BACKLOG.md](./PRODUCT_BACKLOG.md)（EPIC-BUYER #4–#6）+ [SPRINT_29_RETRO.md](../05_development/SPRINT_29_RETRO.md)（AI-1401/1402）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（sprint-planning skill）

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 產品方向 | ✅ EPIC-BUYER 買家端閉環（續章） | Sprint 29 已交付 #1–#3（訂單/通知/付款前端） |
| Sprint 29 成果 | ✅ 買家可「下單→付款(Mock)→收通知→查訂單」 | `ae3073b`/`31d2b5c`/`c2bdc17` |
| 後端就緒度（本 Sprint） | ✅ M06 預訂(6 測試)、M08 評價(8)、M11 物流(7) 後端皆成熟 | PRODUCT_BACKLOG 盤點 |
| 前端缺口 | M06 僅 checkout（缺我的預訂/取消）、M08 僅店鋪審核（缺買家提交/列表）、M11 **無 UI** | 買家閉環最後一段 |
| 活躍 DEF | 2（DEF-016 audit / DEF-017 ERP，非本 Sprint 範圍） | 順延 AI-1403 |
| 前端規範 | Next.js 16.2.2 / React 19.2.4 嚴格 effect | 沿用 Sprint 29 已驗證模式 |

> 🔴 **取向說明**：延續 Sprint 29「純前端變現既有後端」策略，補齊買家閉環最後一段（預訂管理 / 評價 / 物流追蹤）。低風險（後端零改動），沿用 Sprint 29 已驗證的 service/page 模式。

---

## 1. Sprint 30 目標

> **主題**: EPIC-BUYER 買家端閉環**完成** —— 補齊預訂管理、評價、物流追蹤前端

Sprint 29 打通「下單→付款→收通知→查訂單」核心閉環。Sprint 30 補齊剩餘買家觸點：讓買家能管理自己的**預訂**（旅宿）、對完成的交易**提交評價**、追蹤訂單的**物流狀態**，完成整條買家旅程。

---

## 2. Sprint 目標對齊

| 目標 | 對應 backlog # | RICE | 類型 |
|------|--------------|------|------|
| M06 預訂管理前端（我的預訂/取消） | #4 | 5.4 | 最後一哩（純前端） |
| M08 評價前端（提交+列表） | #5 | 4.8 | 最後一哩（純前端） |
| M11 物流追蹤前端 | #6 | 4.0 | 最後一哩（純前端） |

---

## 3. User Stories

### US-001：M06 預訂管理前端（backlog #4，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: 後端 `BookingController` `/v2/bookings`（列表/詳情/取消/日曆）完整，checkout 已可建立預訂，但買家**無「我的預訂」頁**（checkout 成功導向的 `/bookings` 尚不存在）。補預訂列表 + 詳情 + 取消。

**作為** 買家
**我想要** 查看我的所有旅宿預訂、進入單筆看明細、必要時取消
**以便** 管理我的訂房（不必聯繫客服）

**AC-001-1**: 新增 `services/booking.ts`（串接 `api.bookings` list/detail/cancel），型別對齊後端 BookingResponse
**AC-001-2**: `(auth)/bookings` 我的預訂列表（房型、入退房日期、人數、狀態、金額；空狀態）
**AC-001-3**: `(auth)/bookings/[id]` 預訂詳情 + 可取消狀態顯示「取消預訂」（呼叫 `/cancel`，成功後刷新）
**AC-001-4**: 遵守 Next 16/React 19 嚴格 effect；前端 lint/type-check/build 通過

---

### US-002：M08 評價前端（backlog #5，P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: 後端評價 Service（8 測試）完整，前端**僅店鋪審核**，買家無法提交/瀏覽評價。補「完成的訂單/預訂可提交評價」+「商品/房型評價列表」。

**作為** 買家
**我想要** 對已完成的交易給星等與文字評價、並在商品/房型頁看到其他人的評價
**以便** 分享體驗並參考他人回饋

**AC-002-1**: 新增 `services/review.ts`（提交評價、依商品/房型查評價列表、我的評價），型別對齊後端 ReviewDto
**AC-002-2**: 評價提交 UI（星等 + 文字，於已完成訂單/預訂或商品頁觸發）；提交後即時反映
**AC-002-3**: 評價列表元件（星等、內容、時間、商家回覆若有），可嵌入商品/房型詳情
**AC-002-4**: 遵守 Next 16/React 19 嚴格 effect；前端 lint/type-check/build 通過

---

### US-003：M11 物流追蹤前端（backlog #6，P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: 後端物流 Service（7 測試，HCT/TCAT provider）完整，前端**無物流 UI**。補買家在訂單詳情追蹤物流狀態/單號。

**作為** 買家
**我想要** 在商品訂單看到物流狀態、物流單號與軌跡
**以便** 掌握包裹何時送達

**AC-003-1**: 新增 `services/logistics.ts`（依訂單查物流/軌跡），型別對齊後端 LogisticsDto
**AC-003-2**: 訂單詳情（M05，US-001 of S29）對 PRODUCT 訂單顯示物流區塊（物流商、單號、狀態、軌跡時間軸）
**AC-003-3**: 無物流資料時友善提示（如「尚未出貨」）
**AC-003-4**: 遵守 Next 16/React 19 嚴格 effect；前端 lint/type-check/build 通過

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | M06 預訂管理前端 | 2 | P1 |
| US-002 | M08 評價前端 | 3 | P1 |
| US-003 | M11 物流追蹤前端 | 3 | P1 |
| **合計** | | **8 SP** | |

> Velocity 對齊：S26=9 / S27=5 / S28=8 / S29=8。Sprint 30 承諾 8 SP，符合區間。

---

## 5. 執行順序建議

```
US-001（M06 預訂）  → 最直接，串既有 api.bookings（含 checkout 已建立路徑）
US-002（M08 評價）  → 依附「已完成」交易，可嵌商品/房型頁
US-003（M11 物流）  → 整合進 S29 訂單詳情頁（PRODUCT 訂單）
```

> 每完成一個前端單元即跑 lint/type-check/build（遵守開發-編譯-測試循環）；逐 US commit、批次 push。

---

## 6. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| 後端契約不明（評價/物流 DTO） | 中 | 中 | 沿用 Sprint 29 做法：每個 US 先用 Explore agent 抽後端 DTO/enum 契約再寫前端 |
| 評價「可評價」條件（需已完成？一交易一評價？） | 中 | 中 | 以後端規則為權威；前端依後端回應顯示可否評價，不自行推導 |
| 物流資料結構（jsonb 軌跡）解析 | 中 | 中 | 對照 LogisticsDto；無資料時友善降級 |
| 修改 S29 訂單詳情頁引入回歸 | 低 | 中 | 物流區塊為附加，不動既有付款/取消邏輯；改後重跑 build |
| 範圍蔓延（評價圖片上傳、物流地圖） | 中 | 中 | 嚴守 AC：評價=星等+文字；物流=狀態+單號+軌跡文字，不做地圖 |

---

## 7. 測試規劃（QA Quincy）

| US | 測試重點 | 類型 |
|----|---------|------|
| US-001 | 預訂列表/詳情資料、取消流程、空狀態 | 前端 build/type-check + 手動 |
| US-002 | 評價提交（星等/文字）、列表呈現、可評價條件 | 前端 + 手動 |
| US-003 | 物流狀態/軌跡呈現、無資料降級 | 前端 + 手動 |
| AI-1401 | 買家閉環完整手動 E2E（下單→付款→通知→物流→評價） | 手動（需 live 環境） |

> 本 Sprint 純前端，後端無新程式故無新後端測試；`@Test` 維持 683。過程發現後端缺口/bug 即記錄（Rule 12）。

---

## 8. Definition of Done（Sprint 30）

- [ ] US-001~003 所有 AC 達成（買家閉環最後一段補齊）
- [ ] 前端 lint（0 errors）/ type-check / build 通過
- [ ] 新增 service 型別對齊後端 DTO
- [ ] 遵守 Next 16/React 19 嚴格 effect
- [ ] 既有測試無退步（`@Test` 683）；後端無破壞性變更
- [ ] pre-push v5 完整守門（make validate-release）綠燈後 push（累積 Sprint 29+30 批次）
- [ ] Sprint 30 Review / Retrospective / Release Notes 建立

---

## 9. Backlog 對應追蹤

| backlog # | 內容 | Sprint 30 對應 US |
|-----------|------|-----------------|
| #4 | M06 預訂管理前端 | US-001 |
| #5 | M08 評價前端 | US-002 |
| #6 | M11 物流前端 | US-003 |

> EPIC-BUYER 於 Sprint 30 完成整條買家閉環（#1–#6）。後續：真實金流（#10）、進階定價（#7）等另行評估。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
