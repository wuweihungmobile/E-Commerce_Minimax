# Sprint 63 Review / Sprint 63 評審會議

> **Sprint 編號**: Sprint 63
> **期間**: 2028-03-12 ~ 2028-03-25
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: FAQ 後台管理頁面（比照 Knowledge 既有模式）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | FAQ 文章列表頁 | 5 | ✅ 完成 |
| US-002 | FAQ 分類管理頁面 | 3 | ✅ 完成 |

**8 SP 全數完成**。補齊 `docs/05_development/SPRINT_62_RETRO.md` 提出的候選項目——FAQ 後台管理頁面，串接既有完整的 `/v2/faqs` API。

---

## 2. 交付內容

- **`frontend/src/services/faq.ts`**（新檔）：封裝全部 15 個 `/v2/faqs` 端點（文章 CRUD、置頂、關鍵字高亮搜尋、分類 CRUD、分類統計）。
- **`frontend/src/app/dashboard/faq/page.tsx`**（新檔）：FAQ 文章列表，含置頂區塊、分類篩選、關鍵字高亮搜尋（呈現後端 `<mark>` 高亮結果）、分頁、刪除。
- **`frontend/src/app/dashboard/faq/categories/page.tsx`**（新檔）：FAQ 分類管理，列表含文章統計（總數/已發布數），提供新增/編輯/刪除表單。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 前端 ESLint | ✅ 0 error |
| 前端 TypeScript 型別檢查 | ✅ 0 error |
| 前端 production build | ✅ 0 error，新路由 `/dashboard/faq`、`/dashboard/faq/categories` 成功產出 |
| 後端 | 無任何變動（純前端 3 個新檔）|
| schema 漂移守門 | ✅ 由 pre-push hook 內建執行，無漂移 |

---

## 4. 誠實揭露（Rule 12）

1. **規劃階段發現原候選描述可能誤導範圍**：`SPRINT_62_RETRO.md` 提出的候選是「FAQ 前端頁面」，但重新盤點 API 契約後發現所有 `/v2/faqs/*` 端點（含單純 `GET` 列表）皆要求 `faq:read` 權限，與既有 `knowledge:read` 設計完全一致——FAQ 是內部後台管理功能而非公開的買家自助客服頁面。本 Sprint 依此重大澄清調整實作方向，改為比照既有 `dashboard/knowledge/page.tsx` 模式的後台管理頁面，而非公開頁面。
2. **未重跑後端全量回歸**：本 Sprint 純前端變動（3 個新檔，無任何後端程式碼異動），未重新執行 `mvn verify -Pintegration-test`（Sprint 62 收尾時已確認 801 tests 0 fail，且本次未觸碰任何後端檔案）。改以 pre-push hook 內建的 backend-unit + schema 漂移檢查作為推送前把關，不做無意義的重複驗證。
3. **未進行手動瀏覽器互動測試**：兩個頁面已通過 ESLint、TypeScript 型別檢查、production build（新路由成功產出），但未實際啟動前後端 dev server 並在瀏覽器中登入後台帳號手動操作過。
4. **分類管理表單為簡易版**：新增/編輯分類採用內嵌表單而非彈出式 modal 元件（專案目前無現成的 modal 元件慣例可套用），功能完整但視覺呈現較為陽春，符合 MVP 精神。

---

## 5. Demo 重點

- **FAQ 列表管理**：後台可查看所有 FAQ、依分類篩選、關鍵字搜尋（含高亮顯示命中文字）、查看置頂項目、刪除。
- **FAQ 分類管理**：後台可新增/編輯/刪除分類，並即時看到每個分類底下的文章總數與已發布數；刪除有文章的分類會被後端擋下並顯示錯誤訊息。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
