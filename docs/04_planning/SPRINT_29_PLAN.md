# Sprint 29 計劃 / Sprint 29 Plan

> **Sprint 編號**: Sprint 29
> **期間**: 2026-11-22 ~ 2026-12-05 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [PRODUCT_BACKLOG.md](./PRODUCT_BACKLOG.md)（EPIC-BUYER，RICE Top3）+ [SPRINT_28_RETRO.md](../05_development/SPRINT_28_RETRO.md)（AI-1301 啟動）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（sprint-planning skill）

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 產品方向 | ✅ **(b) 強化既有模組 → EPIC-BUYER 買家端閉環** | Sprint 27 US-001 / AI-1101 |
| Sprint 28 基礎 | ✅ 品質硬化（+13 測試）+ 商家後台深化完成 | 有測試防護網後啟動買家端 |
| 後端就緒度（買家端） | ✅ M05/M09/M07 後端**全完整** | 3 路探查（見下表） |
| 前端缺口 | ❌ M05/M09/M07 買家前端**全缺** | 平台目前最大缺口（買家碰不到後端價值） |
| 活躍 DEF | 2（DEF-016 audit / DEF-017 ERP，皆非本 Sprint 範圍） | 順延處理 |
| 前端規範 | Next.js **16.2.2** / React **19.2.4**，嚴格 effect/hooks lint | 寫前端前先讀 [frontend/AGENTS.md](../../frontend/AGENTS.md) |

### 後端就緒度探查結果（2026-07-01）

| 功能 | 後端 Controller | 前端 Service | 前端 Page | api.ts 端點 |
|------|----------------|-------------|----------|-----------|
| **M05 訂單** | ✅ `OrderController` `/v2/orders`（建立/列表/詳情/取消/logs） | ❌ 缺 `order.ts` | ❌ 無頁 | ✅ 已有 `orders:{list,detail,create,cancel}` |
| **M09 通知** | ✅ `NotificationController` `/v2/notifications`（列表/未讀數/標記已讀/刪除/history） | ⚠️ `notification.ts` 僅模板管理 | ❌ 無買家收件匣（`/dashboard/notifications` 為 Admin 模板頁） | ❌ 缺 `/v2/notifications`（僅 `notificationTemplates`） |
| **M07 付款** | ✅ `PaymentController` + `OrderPaymentController`（Mock：`/v2/orders/{id}/pay`、`/pay/fail`、`/payment` 狀態、`/refund`） | ❌ 缺 `payment.ts` | ❌ 無頁 | ✅ 已有 `payments:{create,mock,callback}` |

> 🔴 **取向說明**：本 Sprint **後端零開發、純前端實作** —— 把「已建好、有測試防護網」的後端用前端「變現」，解鎖買家旅程「**下單 → 付款 → 收通知 → 查訂單**」的 first-class 可用性。低風險（不動 service），但仍走 **pre-push v5 完整守門**。

---

## 1. Sprint 29 目標

> **主題**: EPIC-BUYER 買家端閉環**起手** —— 讓買家在 UI 上完成「下單 → 付款 → 收通知 → 查訂單」

Sprint 28 打好品質與後台基礎後，Sprint 29 正式啟動 EPIC-BUYER。依 PRODUCT_BACKLOG RICE Top3（皆 P0、後端皆就緒）：M05 訂單前端 + M09 通知收件匣 + M07 付款前端。三者組成買家最小可用閉環（下單後能付款、能收通知、能查訂單狀態）。

---

## 2. Sprint 目標對齊

| 目標 | 對應 backlog # | RICE | 類型 |
|------|--------------|------|------|
| M05 訂單前端（列表/詳情/取消） | #1 | 8.1 | 最後一哩（純前端） |
| M09 通知收件匣前端（未讀/已讀/歷史） | #2 | 8.1 | 最後一哩（純前端） |
| M07 付款前端（Mock 付款/狀態/紀錄） | #3 | 6.3 | 最後一哩（純前端） |

---

## 3. User Stories

### US-001：M05 訂單前端（backlog #1，P0）

> **SP**: 3 | **優先級**: P0 | **狀態**: 📋 Ready

**目標**: 後端 `OrderController` `/v2/orders`（建立/列表/詳情/取消/狀態日誌）完整且有 34 測試，但買家在前端**無任何訂單頁面**。補「我的訂單」列表 + 詳情 + 取消，讓買家下單後能查詢與管理訂單。

**作為** 買家
**我想要** 在網站上查看我的訂單列表、進入單一訂單看明細與狀態、必要時取消訂單
**以便** 掌握每筆交易的進度（不必聯繫客服）

**AC-001-1**: 新增 `services/order.ts`（串接 `api.orders` 既有端點：list/detail/cancel），型別對齊後端 OrderResponse
**AC-001-2**: 買家訂單列表頁（沿用 `(auth)` 路由群組慣例，與 cart/checkout 一致）—— 分頁、狀態標籤、金額、下單時間；空狀態友善提示
**AC-001-3**: 訂單詳情頁 —— 品項明細、金額、收件/物流資訊、**訂單狀態日誌（`/v2/orders/{id}/logs`）**、可取消狀態顯示「取消訂單」（呼叫 `/cancel`，成功後刷新）
**AC-001-4**: 遵守 Next 16/React 19 嚴格 effect（async fetch、setState 皆在 await 後、cleanup cancel flag）；前端 lint/type-check/build 通過

---

### US-002：M09 通知收件匣前端（backlog #2，P0）

> **SP**: 2 | **優先級**: P0 | **狀態**: 📋 Ready

**目標**: 後端 `NotificationController` `/v2/notifications`（列表 + unreadOnly、未讀數、標記已讀、刪除、history）完整，但前端**僅有 Admin 模板管理頁**，買家**無收件匣**。補買家通知收件匣，串接全程狀態通知（下單/付款/出貨等）。

**作為** 買家
**我想要** 有一個通知收件匣，看到未讀數、瀏覽通知、標記已讀、刪除
**以便** 不錯過訂單/付款/物流的狀態變化

**AC-002-1**: `lib/api.ts` 新增買家 `notifications` 端點群（`/v2/notifications`：list、unread-count、read、delete；`/v2/notifications/history`），與既有 `notificationTemplates` 區隔
**AC-002-2**: 擴展 `services/notification.ts`（或新增買家專用區塊）串接上述端點，型別對齊後端
**AC-002-3**: 買家通知收件匣頁（`(auth)` 群組）—— 未讀數徽章、列表（支援僅未讀篩選）、標記已讀（單筆/批次）、刪除；空狀態提示
**AC-002-4**: 遵守 Next 16/React 19 嚴格 effect；前端 lint/type-check/build 通過

---

### US-003：M07 付款前端（backlog #3，P0，對 Mock）

> **SP**: 3 | **優先級**: P0 | **狀態**: 📋 Ready

**目標**: 後端 `OrderPaymentController` Mock 付款狀態機完整（`/v2/orders/{id}/pay`、`/pay/fail`、`/payment` 狀態、`/refund`），但買家**無付款 UI**。補「訂單詳情觸發付款」的 Mock 付款流程，打通「下單 → 付款」。

**作為** 買家
**我想要** 對 `PENDING_PAYMENT` 的訂單發起付款、看到付款結果與狀態
**以便** 完成交易（本 Sprint 對 Mock，可 demo）

**AC-003-1**: 新增 `services/payment.ts`（串接訂單付款端點：查詢付款狀態 `/v2/orders/{id}/payment`、Mock 付款 `/v2/orders/{id}/pay`、失敗模擬 `/pay/fail`），型別對齊後端
**AC-003-2**: 訂單詳情頁（US-001）對 `PENDING_PAYMENT` 訂單顯示「前往付款」；付款頁/區塊呈現金額、Mock 付款操作、付款結果（成功/失敗）與訂單狀態即時更新
**AC-003-3**: 付款成功後訂單狀態轉換正確呈現（依後端狀態機）；失敗路徑有清楚錯誤提示
**AC-003-4**: 遵守 Next 16/React 19 嚴格 effect；前端 lint/type-check/build 通過
**AC-003-5**: **範圍界線** —— 僅對 **Mock**；真實金流串接（Stripe/綠界，backlog #10，13 SP + 外部依賴）**不在本 Sprint**，另行單獨評估

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | M05 訂單前端（列表/詳情/取消） | 3 | **P0** |
| US-002 | M09 通知收件匣前端 | 2 | **P0** |
| US-003 | M07 付款前端（對 Mock） | 3 | **P0** |
| **合計** | | **8 SP** | |

> Velocity 對齊：S24=7 / S25=11 / S26=9 / S27=5 / S28=8。Sprint 29 承諾 8 SP，符合區間。三者皆 P0（買家閉環半套無價值），故本 Sprint 不設 Buffer，全力交付閉環。

---

## 5. 執行順序建議

```
US-001（M05 訂單前端）  → 🔴 最先：訂單是閉環骨幹，付款依附訂單詳情
US-003（M07 付款前端）  → 接續：在訂單詳情觸發 Mock 付款，打通「下單→付款」
US-002（M09 通知收件匣）→ 獨立可並行：串既有 /v2/notifications
```

> 每完成一個前端單元即跑 lint/type-check/build（遵守開發-編譯-測試循環）。

---

## 6. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| Next 16/React 19 嚴格 lint 反覆踩雷（列表狀態/非同步） | 中 | 中 | 寫前端前先讀 frontend/AGENTS.md；沿用 Sprint 28 dashboard 既有 async-effect 模式 |
| 訂單狀態機/付款狀態轉換前端理解偏差 | 中 | 中 | 對照後端 OrderStateMachine 與 OrderPaymentController；以 `/logs` 與 `/payment` 狀態為單一真相來源，不在前端自行推導 |
| 路由/權限慣例不一致（買家頁該放哪） | 低 | 低 | 沿用既有 `(auth)` 路由群組（cart/checkout 慣例），先讀既有 layout（Rule 8/11） |
| 通知端點型別與後端不符 | 低 | 中 | 對照 NotificationController DTO；先補 api.ts 端點再寫 service |
| 範圍蔓延（付款想串真實金流、通知想接 WebSocket 即時推播） | 中 | 中 | 嚴守 AC：付款僅對 Mock；通知本 Sprint 為收件匣輪詢/查詢，即時推播留 backlog |

---

## 7. 測試規劃（QA Quincy）

| US | 測試重點 | 類型 |
|----|---------|------|
| US-001 | 訂單列表/詳情資料正確、取消流程、狀態日誌呈現、空狀態 | 前端（build/type-check）+ 手動驗證 |
| US-002 | 收件匣未讀數/篩選/標記已讀/刪除、空狀態 | 前端 + 手動驗證 |
| US-003 | Mock 付款成功/失敗路徑、付款後訂單狀態更新 | 前端 + 手動驗證 |

> 本 Sprint 純前端，後端無新程式故無新後端測試；`@Test` 計數維持 683。若過程中發現後端缺口或 bug，即記錄（Rule 12）並評估補測試。

---

## 8. Definition of Done（Sprint 29）

- [ ] US-001~003 所有 AC 達成（買家可「下單 → 付款 → 收通知 → 查訂單」）
- [ ] 前端 lint（0 errors）/ type-check / build 通過
- [ ] 新增 service 型別對齊後端 DTO；api.ts 端點補齊（M09）
- [ ] 遵守 Next 16/React 19 嚴格 effect 規範
- [ ] 既有測試無退步（`@Test` 683 維持）；後端無破壞性變更
- [ ] pre-push v5 完整守門（make validate-release）綠燈後 push
- [ ] Sprint 29 Review / Retrospective / Release Notes 建立

---

## 9. Backlog 對應追蹤

| backlog # | 內容 | Sprint 29 對應 US |
|-----------|------|-----------------|
| #1 | M05 訂單前端 | US-001 |
| #2 | M09 通知收件匣前端 | US-002 |
| #3 | M07 付款前端（Mock） | US-003 |

> EPIC-BUYER 續章（**Sprint 30+**）：#4 M06 預訂管理、#5 M08 評價、#6 M11 物流前端，完成整條買家閉環。真實金流（#10）另行單獨 Sprint 評估。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
