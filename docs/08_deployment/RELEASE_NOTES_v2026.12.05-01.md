# Release Notes - v2026.12.05-01 (Sprint 29)

**發布日期**: 2026-12-05（規劃）／實作完成 2026-07-01
**發布類型**: Minor（買家端閉環起手，純前端）
**Sprint**: Sprint 29
**狀態**: ⏳ 待 push（累積 US-001/002/003，檢查點徵詢後跑完整守門）

> Sprint 29 主題：EPIC-BUYER 買家端閉環起手 —— 讓買家在 UI 完成「下單 → 付款 → 收通知 → 查訂單」

---

## 新功能 ✨

- **我的訂單（US-001，M05）**：新增買家訂單前端 `/orders`（列表：分頁、狀態徽章、商品/訂房標記）與 `/orders/[id]`（詳情：項目明細、金額/運費、收件或訂房資訊、備註、**訂單狀態日誌時間軸**），可取消訂單（可填原因）。
- **通知收件匣（US-002，M09）**：新增買家 `/notifications` 收件匣 —— 未讀數、全部/僅未讀篩選、標記已讀（單筆+全部）、刪除；訂單類通知可一鍵跳訂單詳情。
- **Mock 付款（US-003，M07）**：訂單詳情整合付款面板 —— 待付款訂單可「確認付款（模擬）」（`CREATED → PAID`）或「模擬付款失敗」；付款成功顯示交易編號/時間；付款後即時刷新訂單與狀態日誌。

## 改進 🚀

- 新增前端 service：`services/order.ts`、`services/notificationInbox.ts`、`services/payment.ts`（皆對齊既有 `product.ts` class + `ApiResponse<T>` 解包模式）。
- `lib/api.ts`：補 `orders.logs`、訂單付款端點（payment/pay/payFail/refund）、買家 `notifications` 端點群。
- 買家收件匣 `notificationInbox.ts` 與既有通知模板管理 `notification.ts` **關注點分離**。
- 付款/取消判斷以後端 `OrderPaymentStateDto`（canPay/canCancel）狀態機為權威。
- 前端遵守 Next 16/React 19 嚴格 effect（async fetch、cleanup cancel flag、useCallback）。

## 資料庫遷移 🗄️

- 無新 Flyway migration（最新仍為 V56）；**後端零程式變更**。

## 重大變更 ⚠️

- 無破壞性 API 變更。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| 買家閉環真實 E2E（登入→下單→付款→通知）尚待手動驗證 | Sprint 30（AI-1401） |
| EPIC-BUYER 續章：M06 預訂管理 / M08 評價 / M11 物流前端 | Sprint 30（AI-1402） |
| 通知即時推播（WebSocket）未做，本版為收件匣查詢 | 後續評估 |
| DEF-016 audit log 持久化 / DEF-017 ERP 租戶隔離 | Sprint 30+（AI-1403） |

## 驗證狀態 ✅

- 後端 `@Test` 靜態計數：**683**（純前端，無變化）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- 前端 lint 0 errors / type-check / build 通過（新增路由 /orders、/orders/[id]、/notifications）
- 活躍 DEF：2（DEF-016 / DEF-017，順延）
- pre-push v5 完整守門（act + schema + e2e）—— 隨本批 push 執行

## 內含 Commit（Sprint 29）

| US / 項目 | Commit |
|----------|--------|
| Sprint 29 Plan（EPIC-BUYER 起手）+ Sprint 28 收尾準確性 | `ed2e7e6`（已 push） |
| US-001 M05 訂單前端（列表/詳情/取消/狀態日誌） | `ae3073b` |
| US-002 M09 通知收件匣前端（未讀/已讀/刪除/篩選） | `31d2b5c` |
| US-003 M07 付款前端（Mock，訂單詳情整合） | `c2bdc17` |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
