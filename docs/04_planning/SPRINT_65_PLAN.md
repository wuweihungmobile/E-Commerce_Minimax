# Sprint 65 計劃 / Sprint 65 Plan

> **Sprint 編號**: Sprint 65
> **期間**: 2028-04-09 ~ 2028-04-22 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `docs/05_development/SPRINT_64_RETRO.md` 第 4 節候選項目「`/dashboard/revenue` 營收報表頁補齊」
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| API 契約盤點 | ✅ 完成：`GET /v2/dashboard/revenue`（`dashboard:read` 權限，內部後台功能）已支援 `startDate`/`endDate`/`granularity` 參數，回傳 `RevenueStats`（總營收/淨營收/AOV/退款/每日明細/分類營收）|
| **重大發現：granularity 參數是死碼** | ✅ 已確認：`AnalyticsService.getRevenueStats` 完全未使用 `request.getGranularity()`，不論傳 `DAY`/`WEEK`/`MONTH` 皆固定按日分組。若只做前端頁面、附上「粒度切換」UI 會是**功能性錯誤**（使用者切換週/月毫無效果）。本 Sprint 需一併修正後端才能誠實提供此功能 |
| **重大發現：categoryRevenue 為未實作 stub** | ✅ 已確認：`categoryRevenue.data` 恆為空陣列（程式碼明確 `Collections.emptyList()`），非資料庫查無資料。本 Sprint 不在頁面呈現此區塊，避免顯示誤導性的「永遠空白」UI |
| 既有慣例確認 | ✅ `frontend/src/services/analytics.ts` 已有 `getRevenueStats` 封裝（僅缺 `granularity` 參數）；`frontend/src/app/dashboard/page.tsx` 首頁已有簡易 30 天營收長條圖範本（純 CSS bar，無圖表套件依賴），本 Sprint 沿用同一視覺風格但做成獨立、可自訂日期範圍與粒度的完整報表頁 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 65 目標

> **主題**: 營收報表頁補齊 + granularity 死碼修正

修正後端 `getRevenueStats` 的 `granularity` 參數死碼問題，使 WEEK/MONTH 分組真正生效；新增 `/dashboard/revenue` 獨立報表頁，支援自訂日期範圍與粒度切換。

---

## 2. User Story

### US-001：修正 AnalyticsService.getRevenueStats 的 granularity 死碼

> **SP**: 3 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**: `getRevenueStats` 依 `request.getGranularity()`（`DAY`/`WEEK`/`MONTH`，預設 `DAY`）將訂單分桶聚合，`WEEK` 以週一為起始、`MONTH` 以月初為起始，`DailyRevenue.date` 欄位代表該桶的起始日。

**AC-001-2**: 不改變 `DAY`（預設）情況下的既有輸出結果，確保向後相容。

**AC-001-3**: 補充單元測試涵蓋 `WEEK`/`MONTH` 分桶的邊界情況（跨週/跨月邊界、範圍起訖非剛好對齊桶邊界）。

---

### US-002：`/dashboard/revenue` 營收報表頁

> **SP**: 5 | **優先級**: P2 | **狀態**: 🔲 待開始

**AC-002-1**: `frontend/src/services/analytics.ts` 的 `getRevenueStats` 補上 `granularity` 參數。

**AC-002-2**: 新增 `frontend/src/app/dashboard/revenue/page.tsx`，提供日期範圍選擇（起訖日期）與粒度切換（日/週/月），呼叫修正後的 API。

**AC-002-3**: 顯示摘要卡片（總營收、淨營收、平均客單價、總退款）+ 營收趨勢長條圖（沿用首頁的純 CSS bar 視覺風格，不引入新圖表套件依賴）。

**AC-002-4**: 不呈現 `categoryRevenue` 區塊（後端未實作，呈現空白區塊會誤導使用者）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 修正 getRevenueStats 的 granularity 死碼 | 3 | P2 |
| US-002 | `/dashboard/revenue` 營收報表頁 | 5 | P2 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-64（皆 8 SP）之單 Sprint 產能。US-001 先修正後端使粒度切換真正有效，US-002 才實作依賴此功能的前端頁面，順序符合依賴關係。

---

## 4. Definition of Done

- [ ] US-001：`getRevenueStats` granularity 修正 + 單元測試（含 WEEK/MONTH 邊界）
- [ ] US-002：`services/analytics.ts` 補 granularity 參數 + `dashboard/revenue/page.tsx`
- [ ] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）0 fail
- [ ] `make validate-schema` 無漂移（本 Sprint 無新 migration）
- [ ] 前端 lint/tsc/build 0 error
- [ ] Sprint 65 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 | `core/analytics/AnalyticsService.java`（修正 granularity 分桶邏輯）|
| 後端測試 | `AnalyticsServiceTest.java`（新增 WEEK/MONTH 分桶測試）|
| 前端 | `services/analytics.ts`（補 granularity 參數）、`app/dashboard/revenue/page.tsx`（新檔）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration**（純邏輯修正 + 新頁面）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
