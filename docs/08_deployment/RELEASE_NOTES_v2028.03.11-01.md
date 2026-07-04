# Release Notes - v2028.03.11-01 (Sprint 62)

**發布日期**: 2028-03-11（規劃）／實作完成 2026-07-05
**發布類型**: Minor（M18 知識管理/FAQ 測試防護網補強；schema-free；純後端測試）
**Sprint**: Sprint 62
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 62 主題：**M18 知識管理/FAQ 測試防護網補強**。清償 `PRODUCT_BACKLOG.md` 候選 #9，並發現、修復比原描述更嚴重的測試缺口。

---

## 新功能 / 改進 🚀

- **KnowledgeBaseService 單元測試從 0 建立**：新增 `KnowledgeBaseServiceTest.java`（24 測試），涵蓋分類 CRUD、文章 CRUD/查詢、版本控制邏輯（快照/清單/還原/排程發布）。先前唯一相關的整合測試對其用 `@MockBean` 完全繞過，實質業務邏輯覆蓋為 0。
- **FaqService 測試缺口補齊**：`FaqServiceTest.java` 新增 12 個測試，覆蓋原本零測試的 11 個方法。
- **AnalyticsService 測試缺口補齊**：`AnalyticsServiceTest.java` 新增 `getRecentActivity` 的 2 個測試。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **459 tests，0 fail**（含新增 38 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**全量 342 tests，0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（純測試新增，無 migration）。

## 技術決策 / 已知限制 ⚠️

- **候選 #9 描述已過時**：`PRODUCT_BACKLOG.md` 原描述「M14 Analytics/M18 FAQ，0–1 測試」實際上早於 Sprint 28 已補過部分測試，規劃時重新盤點才發現真正缺口是完全未被提及的 `KnowledgeBaseService`。
- **開發過程環境教訓**：pre-commit 期間與前景手動 mvn 指令並發共用 `target/` 目錄，導致假性 `ClassNotFoundException`，已釐清非程式問題並改為序列化操作。
- **FAQ 前端頁面本次未排入**：`/v2/faqs` API 已備妥但無任何前端頁面，屬功能缺口而非測試缺口，列為 Sprint 63 候選。

## 資料庫遷移 🗄️

- 無（schema-free；純測試新增）。

## 內含 Commit（Sprint 62）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 62 Plan | 2b34f4c | M18 知識管理/FAQ 測試防護網補強計劃（2 US / 8 SP）|
| US-002 | de81b07 | FaqService + AnalyticsService 測試缺口補強 |
| US-001 | 0326043 | KnowledgeBaseService 單元測試從 0 建立 |
| Sprint 62 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
