# Sprint 62 Review / Sprint 62 評審會議

> **Sprint 編號**: Sprint 62
> **期間**: 2028-02-27 ~ 2028-03-11
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: M18 知識管理/FAQ 測試防護網補強

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | KnowledgeBaseService 單元測試從 0 建立 | 5 | ✅ 完成 |
| US-002 | FaqService 與 AnalyticsService 測試缺口補強 | 3 | ✅ 完成 |

**8 SP 全數完成**。清償 `PRODUCT_BACKLOG.md` 候選 #9 的真正缺口——`KnowledgeBaseService` 全部 17 個方法（含版本控制）從零覆蓋建立完整單元測試，`FaqService`/`AnalyticsService` 補齊剩餘零覆蓋方法。

---

## 2. 交付內容

- **`KnowledgeBaseServiceTest.java`**（新檔，24 測試）：分類 CRUD（含 slug 重複、找不到情境）、文章 CRUD/查詢、以及先前完全零覆蓋的版本控制邏輯——`createVersionSnapshot` 版本號遞增、`getArticleVersions`/`getArticleVersion`、`restoreVersion`（還原前自動快照目前版本）、`schedulePublish`。
- **`FaqServiceTest.java`**（+12 測試）：補齊 `getCategories`、`getCategory`（含找不到）、`getCategoryBySlug`、`updateCategory`、`getArticles`、`getPinnedArticles`、`getArticle`、`getArticleBySlug`、`updateArticle`、`deleteArticle`、`incrementViewCount`。
- **`AnalyticsServiceTest.java`**（+2 測試）：補齊 `getRecentActivity`（happy path + 無訂單邊界）。
- **`PRODUCT_BACKLOG.md`**：候選 #9 標記完成，記錄與原「0–1 測試」描述的落差。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn test`）| ✅ **459 tests，0 fail**（含新增 38 個）|
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **全量 342 tests，0 fail** |
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（純測試新增，無 migration）|

---

## 4. 誠實揭露（Rule 12）

1. **規劃階段重新盤點發現候選描述已過時**：`PRODUCT_BACKLOG.md` 候選 #9 原描述「M14 Analytics/M18 FAQ，0–1 測試」實際上早於 Sprint 28 就已補過部分測試（v1.3 維護紀錄已註明），本次規劃前重新盤點才發現真正最大缺口是完全未被候選 #9 提及的 `KnowledgeBaseService`（唯一相關整合測試對其用 `@MockBean` 繞過，實質零覆蓋）。
2. **開發過程曾發生自己造成的環境問題**：US-002 commit 執行 pre-commit 核心測試期間，同時在前景手動執行 `mvn test-compile`/`mvn test` 開發 US-001，兩個 Maven 程序共用同一 `backend/target` 目錄，導致 class 檔案互相破壞、出現大量假性 `ClassNotFoundException`（非真實程式問題）。以 `mvn clean test-compile` 重新編譯確認後改為序列化操作（不在背景 commit 執行期間對同目錄下手動跑 mvn），問題未再發生。此教訓已記錄供後續 Sprint 遵循。
3. **未排入本 Sprint 的功能缺口**：規劃時另發現 FAQ 前端頁面完全缺失（`/v2/faqs` API 已備妥、`frontend/src/lib/api.ts` 已有 client stub，但無任何頁面引用）。此為功能缺口而非測試缺口，性質不同，未排入 `PRODUCT_BACKLOG.md` 候選清單，留待 Sprint 63 評估。

---

## 5. Demo 重點

- **KnowledgeBaseService 測試防護網**：版本控制邏輯（快照/還原/排程發布）現有明確測試覆蓋，未來修改若破壞既有行為會立即被攔截，這是先前完全沒有保護的高風險程式碼路徑。
- **FAQ/Analytics 測試覆蓋完整**：兩個服務的所有 public 方法現皆有至少一個測試案例。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
