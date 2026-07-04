# Release Notes - v2028.04.22-01 (Sprint 65)

**發布日期**: 2028-04-22（規劃）／實作完成 2026-07-05
**發布類型**: Minor（營收報表頁+granularity修正；schema-free；後端+前端）
**Sprint**: Sprint 65
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 65 主題：**營收報表頁補齊 + granularity 死碼修正**。

---

## 新功能 / 改進 🚀

- **`AnalyticsService.getRevenueStats` granularity 修正**：修正前不論傳 `DAY`/`WEEK`/`MONTH` 皆固定按日分組，現在真正依粒度分桶聚合（WEEK 以週一為起始、MONTH 以月初為起始）。
- **`/dashboard/revenue` 營收報表頁**：獨立報表頁，支援自訂日期範圍 + 粒度切換，顯示總營收/淨營收/AOV/總退款 + 營收趨勢長條圖。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **483 tests，0 fail**（含新增 2 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**全量 342 tests，0 fail**。
- **schema 漂移守門**：無漂移（無新 migration）。
- **前端**：lint/tsc/build 皆 0 error，新路由成功產出。

## 技術決策 / 已知限制 ⚠️

- **刻意不呈現 categoryRevenue**：後端此欄位為空 stub（非查無資料），呈現會誤導使用者。
- **未進行手動瀏覽器互動測試**：已通過 lint/型別檢查/build 與後端完整回歸，未實際於瀏覽器操作。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 65）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 65 Plan | c539839 | 營收報表頁+granularity修正計劃（2 US / 8 SP）|
| US-001 | bd2db95 | 修正 getRevenueStats 的 granularity 死碼 |
| US-002 | d87c6a6 | /dashboard/revenue 營收報表頁 |
| Sprint 65 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
