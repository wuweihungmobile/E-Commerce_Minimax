# Sprint 63 計劃 / Sprint 63 Plan

> **Sprint 編號**: Sprint 63
> **期間**: 2028-03-12 ~ 2028-03-25 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `docs/05_development/SPRINT_62_RETRO.md` 第 4 節候選項目「FAQ 前端頁面」——`/v2/faqs` API 已備妥、`frontend/src/lib/api.ts` 已有 client stub，但完全沒有任何頁面引用
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| API 契約盤點 | ✅ 完成：`FaqArticleController`（`/v2/faqs`，9 端點）+ `FaqCategoryController`（`/v2/faqs/categories`，6 端點）已完整實作，涵蓋 CRUD、置頂、關鍵字高亮搜尋、分類統計 |
| **重大澄清：FAQ 非公開頁面** | ✅ 已確認：所有 `/v2/faqs/*` 端點（含單純 `GET` 列表）皆要求 `@PreAuthorize("hasAuthority('faq:read')")`，與既有 `knowledge:read` 權限設計完全一致——FAQ 屬**內部/後台管理功能**（比照既有 `frontend/src/app/dashboard/knowledge/page.tsx`），並非公開的買家自助客服頁面。原始候選描述「FAQ 前端頁面」若理解為公開頁面會誤導範圍，本 Sprint 依實際 RBAC 設計走後台管理頁面 |
| 既有慣例確認 | ✅ `frontend/src/app/dashboard/knowledge/page.tsx` + `frontend/src/services/knowledge.ts` 為對應的既有實作範本（純 Tailwind + service 層封裝 apiClient，非 shadcn 元件）；Knowledge 本身也僅有列表/篩選/刪除，無分類管理 UI 與文章新增/編輯 UI——後台內容多半透過 API/seed 管理，非本次需補齊的缺口 |
| 範圍界定 | ✅ 本 Sprint 聚焦 FAQ 特有、Knowledge 沒有的功能：文章列表（含置頂區塊、關鍵字高亮搜尋）+ 分類管理（CRUD，FAQ 文章建立前提是分類需存在，目前完全無 UI 可管理）|
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 63 目標

> **主題**: FAQ 後台管理頁面（比照 Knowledge 既有模式）

補齊 `frontend/src/services/faq.ts` service 層與兩個後台頁面：FAQ 文章列表（含置頂、關鍵字高亮搜尋、分類篩選、刪除）與 FAQ 分類管理（CRUD）。串接既有完整的 `/v2/faqs` API，不需新增後端程式碼。

---

## 2. User Story

### US-001：FAQ 文章列表頁

> **SP**: 5 | **優先級**: P2 | **狀態**: 🔲 待開始

**AC-001-1**: 新增 `frontend/src/services/faq.ts`，比照 `services/knowledge.ts` 封裝所有 `/v2/faqs` 端點（含 `getArticles`、`getArticle`、`deleteArticle`、`getPinnedArticles`、`searchArticlesWithHighlight`、`incrementViewCount`、`getCategories`）。

**AC-001-2**: 新增 `frontend/src/app/dashboard/faq/page.tsx`，比照 `dashboard/knowledge/page.tsx` 的列表/分類篩選/關鍵字搜尋/分頁/刪除模式。

**AC-001-3**: 額外顯示置頂文章區塊（呼叫 `getPinnedArticles`，Knowledge 沒有此概念，為 FAQ 特有功能）。

**AC-001-4**: 關鍵字搜尋改用 `searchArticlesWithHighlight` 端點（而非單純 `getArticles` 的 keyword 參數），呈現後端已支援的高亮結果（`<mark>` 標籤，需以 `dangerouslySetInnerHTML` 或安全的高亮渲染方式處理）。

---

### US-002：FAQ 分類管理頁面

> **SP**: 3 | **優先級**: P2 | **狀態**: 🔲 待開始

**AC-002-1**: `frontend/src/services/faq.ts` 補齊分類 CRUD 函式（`getCategories`、`createCategory`、`updateCategory`、`deleteCategory`、`getCategoryStats`）。

**AC-002-2**: 新增 `frontend/src/app/dashboard/faq/categories/page.tsx`，列表顯示所有分類與統計（文章總數/已發布數，串接 `getCategoryStats`），提供新增/編輯/刪除表單（簡易 modal 或 inline 表單，比照專案既有慣例）。

**AC-002-3**: 刪除分類時若後端回傳「分類底下仍有文章」錯誤（E_3001），前端需清楚顯示錯誤訊息而非泛用失敗提示。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | FAQ 文章列表頁 | 5 | P2 |
| US-002 | FAQ 分類管理頁面 | 3 | P2 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-62（皆 8 SP）之單 Sprint 產能。本 Sprint 純前端、不涉及後端變動，`mvn verify` 全量回歸應與 Sprint 62 結果一致（無新增後端測試）。

---

## 4. Definition of Done

- [ ] US-001：`services/faq.ts`（文章相關函式）+ `dashboard/faq/page.tsx`
- [ ] US-002：`services/faq.ts`（分類相關函式）+ `dashboard/faq/categories/page.tsx`
- [ ] 前端 `npx eslint` + `npx tsc --noEmit` + `npm run build` 皆 0 error，新路由成功產出於 build
- [ ] 後端無變動，`mvn verify -Pintegration-test` 全量回歸維持 0 fail（確認未間接影響）
- [ ] `make validate-schema` 無漂移（無 migration）
- [ ] Sprint 63 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 前端 service | `frontend/src/services/faq.ts`（新檔）|
| 前端頁面 | `frontend/src/app/dashboard/faq/page.tsx`（新檔）、`frontend/src/app/dashboard/faq/categories/page.tsx`（新檔）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無後端變動、無 migration**（純前端串接既有完整 API）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
