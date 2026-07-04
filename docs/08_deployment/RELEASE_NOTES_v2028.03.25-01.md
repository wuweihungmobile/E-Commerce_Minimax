# Release Notes - v2028.03.25-01 (Sprint 63)

**發布日期**: 2028-03-25（規劃）／實作完成 2026-07-05
**發布類型**: Minor（FAQ 後台管理頁面；schema-free；純前端）
**Sprint**: Sprint 63
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 63 主題：**FAQ 後台管理頁面**。補齊 `/v2/faqs` API 的前端串接，比照既有 Knowledge 後台模式。

---

## 新功能 / 改進 🚀

- **FAQ 文章列表頁**（`/dashboard/faq`）：分類篩選、關鍵字高亮搜尋、置頂區塊、分頁、刪除。
- **FAQ 分類管理頁**（`/dashboard/faq/categories`）：分類 CRUD，含文章統計（總數/已發布數）。
- **`frontend/src/services/faq.ts`**：封裝完整 `/v2/faqs` API（15 個端點）。

## 測試 / 驗證 ✅

- **前端**：ESLint / TypeScript 型別檢查 / production build 皆 0 error，新路由成功產出。
- **後端**：本 Sprint 無任何後端變動；pre-push hook 內建 backend-unit + schema 漂移檢查作為推送前把關。

## 技術決策 / 已知限制 ⚠️

- **重大澄清**：`/v2/faqs/*` 所有端點皆要求 `faq:read` 權限，FAQ 屬內部後台功能而非公開買家頁面，實作方向依此調整。
- **分類管理表單為簡易內嵌表單**：非彈出式 modal，功能完整但視覺較陽春。
- **未進行手動瀏覽器互動測試**：已通過 lint/型別檢查/build，未實際於瀏覽器登入操作。

## 資料庫遷移 🗄️

- 無（schema-free；純前端）。

## 內含 Commit（Sprint 63）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 63 Plan | 86fc545 | FAQ 後台管理頁面計劃（2 US / 8 SP）|
| US-001+US-002 | e5a71cc | faq.ts + FAQ 文章列表頁 + FAQ 分類管理頁 |
| Sprint 63 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
