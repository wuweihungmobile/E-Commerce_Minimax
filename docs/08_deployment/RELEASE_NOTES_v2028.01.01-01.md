# Release Notes - v2028.01.01-01 (Sprint 57)

**發布日期**: 2028-01-01（規劃）／實作完成 2026-07-04
**發布類型**: Patch（開放窗清除機制；schema-free；前後端小幅擴充）
**Sprint**: Sprint 57
**狀態**: ⏳ 待 push（本 Sprint commit；於檢查點徵詢後連同 S41~S57 一併 push，嚴禁 `--no-verify`）

> Sprint 57 主題：**開放窗清除機制**。承 Sprint 47（AI-2202e）建立開放窗語意，本 Sprint 讓賣家可將 `open_until_date`/`booking_window_days` 清回無限制。

---

## 新功能 / 改進 🚀

- **開放窗清除端點（AI-2202f）**：新增 `DELETE /v2/rooms/{listingId}/open-window`，一次性將兩個開放窗欄位清回 `null`（比照既有 `CartController.clearCart` 模式）。
- **前端清除操作（AI-2202f）**：`RoomForm.tsx` 新增「清除開放窗設定（恢復無限制）」按鈕，修正原本清空輸入框送出並不會清除既有值的誤導性行為。

## 測試 / 驗證 ✅

- **後端單元**：新增 `RoomServiceTest`（3 tests：清除成功/不影響其他欄位/找不到房源），**全量 515 tests，0 fail**。
- **後端整合（真實 DB）**：既有開放窗相關測試（API-M06-016）不退步，**全量 422 tests，0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（schema-free，無 migration）。
- **前端**：`tsc --noEmit` 0 error、`eslint` 0 error（既有警告非本次引入）、`npm run build` 0 error。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **機制選擇為工程設計決策**：新增專屬端點而非改 DTO 為 `Optional`/`JsonNullable` 包裝型別，依既有全域「非 null 才更新」慣例判斷風險最低；不涉及業務語意，未徵詢 PO。
- **未新增 RoomController 層級 E2E 測試**：專案內 Room CRUD 端點原本就無此類測試，已用 Service 層單元測試涵蓋新邏輯。
- **安全提醒**：開發過程中發現 `frontend/AGENTS.md` 內含疑似提示注入的異常指令文字，已忽略並提醒使用者確認來源。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 57）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 57 Plan | 06036f2 | 開放窗清除機制計劃（1 US / 2 SP）|
| US-001 AI-2202f | a85cbbb | `clearOpenWindow` 端點 + 前端按鈕 + 測試 |
| Sprint 57 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
