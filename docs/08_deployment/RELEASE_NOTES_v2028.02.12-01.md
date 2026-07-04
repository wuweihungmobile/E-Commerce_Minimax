# Release Notes - v2028.02.12-01 (Sprint 60)

**發布日期**: 2028-02-12（規劃）／實作完成 2026-07-04
**發布類型**: Minor（全站錯誤訊息中文化；schema-free；後端聚焦）
**Sprint**: Sprint 60
**狀態**: ⏳ 待 push（本 Sprint commit；依新節奏收尾後即 push，嚴禁 `--no-verify`）

> Sprint 60 主題：**全站 BusinessException 英文訊息中文化**。PO 決策完整修復：翻譯 130 個 `ErrorCode` 常數為繁體中文，動態英文細節改為僅供伺服器端 log，不再回傳給使用者。

---

## 新功能 / 改進 🚀

- **ErrorCode 中文化（AI-2418）**：`ErrorCode.java` 全部 130 個常數的訊息翻譯為繁體中文（含 3 個帶格式化佔位符的訊息，語意不變）。
- **動態細節不外洩（AI-2418）**：`BusinessException` 新增 `getUserMessage()`，`GlobalExceptionHandler` 改用此方法組成 API 回應，使用者只看到通用的中文 ErrorCode 訊息，不再看到英文的動態細節（如具體日期/ID）；細節仍完整記錄於伺服器端 log。
- **`GlobalExceptionHandler` 硬編碼字串中文化（AI-2418）**：另外 7 處驗證/認證相關的硬編碼英文字串（Validation failed、Invalid credentials 等）一併翻譯為中文。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **422 tests，0 fail**。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe `*IntegrationTest.java`/`*E2ETest.java`）**全量 890 tests，0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（schema-free）。
- **前端變動**：無（前端已顯示 `response.data.message`，後端修改後自動生效）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **PO 決策完整修復**：翻譯 ErrorCode 基礎訊息 + 隱藏動態細節，而非保留英文細節的折衷方案。
- **未逐一修改 383 個呼叫點的動態英文細節本身**：只是不再回傳前端，程式碼中的英文字串仍存在（供 log 使用）。
- **未來如需在使用者訊息保留具體細節**：需另立結構化錯誤欄位方案，非本次範圍。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 60）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 60 Plan | d55e3d9 | 全站錯誤訊息中文化計劃（1 US / 8 SP）|
| US-001 AI-2418 | 62f27a0 | ErrorCode 中文化 + BusinessException.getUserMessage() + GlobalExceptionHandler + 測試更新 |
| Sprint 60 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
