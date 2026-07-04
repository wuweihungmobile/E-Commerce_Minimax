# Sprint 62 計劃 / Sprint 62 Plan

> **Sprint 編號**: Sprint 62
> **期間**: 2028-02-27 ~ 2028-03-11 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/PRODUCT_BACKLOG.md` 候選 #9（測試補強，RICE 1.4，P2）——規劃前重新盤點確認現況，發現候選描述已過時（Sprint 28 已補過部分測試），實際缺口與原描述不同
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Backlog 候選 #9 現況重新盤點 | ✅ 完成：`AnalyticsService`（5 方法）已有 7 測試，僅 `getRecentActivity` 缺測試；`FaqService`（16 方法）僅 6 測試，11 方法零覆蓋；`KnowledgeBaseService`（17 方法，含版本控制）**完全沒有單元測試**，唯一相關的 `M18KnowledgePhase2IntegrationTest` 對其用 `@MockBean` 繞過，實質覆蓋為 0——是三者中風險最大的缺口 | 2026-07-04 規劃時重新確認 |
| PRD 對應規格確認 | ✅ M18 知識管理系統（PRD §6.10 + `M18_Knowledge_Management_SPEC.md`）功能規格已完整實作（知識庫分類/文章/版本/排程發布 + FAQ 分類/文章/搜尋高亮/統計），本次缺口為測試而非功能 | |
| 範圍排除確認 | ✅ 規劃時另發現 FAQ 前端頁面完全缺失（`/v2/faqs` API 已備妥、`frontend/src/lib/api.ts` 已有 client stub，但無任何頁面引用），此為**功能缺口**而非測試缺口，性質不同，本 Sprint 不排入，留待 Sprint 63 評估 | |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 62 目標

> **主題**: M18 知識管理/FAQ 測試防護網補強

為零測試覆蓋的 `KnowledgeBaseService`（含版本控制邏輯）建立完整單元測試；補齊 `FaqService` 現有 11 個零覆蓋方法的 happy-path 測試；補上 `AnalyticsService.getRecentActivity` 的測試。三者皆為既有邏輯補測試，不改變任何既有行為。

---

## 2. User Story

### US-001：KnowledgeBaseService 單元測試從 0 建立

> **SP**: 5 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**: 新增 `KnowledgeBaseServiceTest.java`，涵蓋分類 CRUD（`getCategories`/`getCategory`/`getCategoryBySlug`/`createCategory`/`updateCategory`/`deleteCategory`）的 happy path 與至少一個例外情境（如 `getCategory` 找不到資料）。

**AC-001-2**: 涵蓋文章 CRUD 與查詢（`getArticles`/`getArticle`/`getArticleBySlug`/`createArticle`/`updateArticle`/`deleteArticle`/`incrementViewCount`）的 happy path。

**AC-001-3**: 涵蓋版本控制邏輯（`createVersionSnapshot`/`getArticleVersions`/`getArticleVersion`/`restoreVersion`/`schedulePublish`）——這是全新程式碼路徑（先前完全零覆蓋），需特別確認版本快照建立時機、還原邏輯的正確性。

**AC-001-4**: 測試風格比照 `FaqServiceTest`/`AdminServiceTest`，使用 Mockito mock repository，不需真實 DB。

---

### US-002：FaqService 與 AnalyticsService 測試缺口補強

> **SP**: 3 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-002-1**: `FaqServiceTest.java` 補齊以下 11 個現有零覆蓋方法的 happy-path 測試：`getCategories`、`getCategory`、`getCategoryBySlug`、`updateCategory`、`getArticles`、`getPinnedArticles`、`getArticle`、`getArticleBySlug`、`updateArticle`、`deleteArticle`、`incrementViewCount`。

**AC-002-2**: `AnalyticsServiceTest.java` 補上 `getRecentActivity` 的測試（至少 1 個 happy path + 1 個空結果情境）。

**AC-002-3**: 不修改任何既有測試或生產程式碼行為，純新增測試案例。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | KnowledgeBaseService 單元測試從 0 建立 | 5 | P2 |
| US-002 | FaqService 與 AnalyticsService 測試缺口補強 | 3 | P2 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60/61（皆 8 SP）之單 Sprint 產能。US-001 涉及全新測試檔案（含版本控制邏輯，規模較大），US-002 為既有測試檔案的增量補強，兩者互相獨立可並行驗證。

---

## 4. Definition of Done

- [x] US-001：`KnowledgeBaseServiceTest.java` 新建，24 個測試涵蓋 17 個 public 方法
- [x] US-002：`FaqServiceTest.java` 補齊 11 個方法（12 測試）、`AnalyticsServiceTest.java` 補齊 `getRecentActivity`（2 測試）
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）**459 + 342 = 801 tests，0 fail**
- [x] `make validate-schema` 無漂移（本 Sprint 無新 migration，純測試新增）
- [x] `docs/04_planning/PRODUCT_BACKLOG.md` 候選 #9 狀態更新為完成（v1.4），並記錄與原描述的落差
- [x] Sprint 62 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試（新檔） | `core/knowledge/KnowledgeBaseServiceTest.java` |
| 後端測試（擴充） | `core/faq/FaqServiceTest.java`、`core/analytics/AnalyticsServiceTest.java` |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**（純測試補強；FAQ 前端頁面缺口另案處理，見前置條件確認）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
