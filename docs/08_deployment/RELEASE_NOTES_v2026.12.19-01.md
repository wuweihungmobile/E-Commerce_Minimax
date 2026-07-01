# Release Notes - v2026.12.19-01 (Sprint 30)

**發布日期**: 2026-12-19（規劃）／實作完成 2026-07-01
**發布類型**: Minor（買家端閉環完成，純前端）
**Sprint**: Sprint 30
**狀態**: ⏳ 待 push（累積 Sprint 29+30，檢查點徵詢後跑完整守門）

> Sprint 30 主題：EPIC-BUYER 買家端閉環**完成** —— 補齊預訂管理、評價、物流追蹤前端

---

## 新功能 ✨

- **我的預訂（US-001，M06）**：新增 `/bookings`（列表：房型/日期/晚數/人數/狀態/金額）與 `/bookings/[id]`（詳情：住宿資訊 + 訂房人資料），可取消預訂（可填原因）。
- **評價（US-002，M08）**：買家可對**已送達/完成的商品訂單**（逐項）與**已完成/已退房的預訂**提交評價（星等 + 標題 + 內容 + 匿名）；新增 `/reviews/product/[listingId]` 商品評價檢視頁（評分統計 + 評價列表）。
- **物流追蹤（US-003，M11）**：商品訂單詳情新增物流追蹤區塊 —— 物流商（黑貓/新竹）、物流單號、目前狀態與**軌跡時間軸**；無出貨時顯示「尚未出貨」。

## 改進 🚀

- 新增前端 service：`services/booking.ts`、`services/review.ts`、`services/logistics.ts`（皆對齊既有 class + `ApiResponse<T>` 解包模式）。
- 可重用評價元件：`components/reviews/{ReviewStars, ReviewForm, ReviewList}`。
- `lib/api.ts`：新增 reviews、bookingReviews、logistics 端點群。
- 「一交易一評價」、預訂/物流查詢皆以後端為權威，前端做錯誤呈現與友善降級。
- 前端遵守 Next 16/React 19 嚴格 effect。

## 資料庫遷移 🗄️

- 無新 Flyway migration（最新仍為 V56）；**後端零程式變更**。

## 重大變更 ⚠️

- 無破壞性 API 變更。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| 買家閉環完整真實 E2E（下單→付款→通知→物流→評價）尚待手動驗證 | Sprint 31（AI-1501，P1 必辦） |
| 後端 `BookingListResponse.roomTitle` 未填充（前端以「訂房 #id」降級） | Sprint 31（AI-1502） |
| 無買家端公開商品/房型詳情頁（ReviewList 已備好待嵌入） | 評估中（AI-1504） |
| DEF-016 audit log 持久化 / DEF-017 ERP 租戶隔離 | Sprint 31+（AI-1503） |
| 評價圖片上傳、真實金流（#10） | 後續評估 |

## 驗證狀態 ✅

- 後端 `@Test` 靜態計數：**683**（純前端，無變化）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- 前端 lint 0 errors / type-check / build 通過（新增路由 /bookings、/bookings/[id]、/reviews/product/[listingId]）
- 活躍 DEF：2（DEF-016 / DEF-017，順延）
- pre-push v5 完整守門（act + schema + e2e）—— 隨 Sprint 29+30 批次 push 執行

## 內含 Commit（Sprint 30）

| US / 項目 | Commit |
|----------|--------|
| Sprint 30 Plan（EPIC-BUYER 續章） | `bb77952` |
| US-001 M06 預訂管理前端 | `6501c69` |
| US-002 M08 評價前端（提交+列表） | `d5eb35d` |
| US-003 M11 物流追蹤前端 | `3006c09` |

> 本 Release 與 Sprint 29（v2026.12.05-01）一同 push（累積批次，完整守門一次驗證）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
