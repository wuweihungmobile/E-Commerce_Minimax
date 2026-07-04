# Release Notes - v2028.01.15-01 (Sprint 58)

**發布日期**: 2028-01-15（規劃）／實作完成 2026-07-04
**發布類型**: Patch（availability reason 錯誤碼化；schema-free；前後端小幅調整）
**Sprint**: Sprint 58
**狀態**: ⏳ 待 push（本 Sprint commit；於檢查點徵詢後連同 S41~S58 一併 push，嚴禁 `--no-verify`）

> Sprint 58 主題：**Availability unavailableReason 錯誤碼化**。`checkAvailability` 回傳結構化 reason code 取代英文字串字面值，前端以中文對照表顯示，消除中英混雜的顯示體驗。

---

## 新功能 / 改進 🚀

- **Reason code 化（AI-2408）**：新增 `BookingDto.AvailabilityReasonCode` enum（`INVALID_DATE_RANGE`/`NOT_OPEN_FOR_BOOKING`/`BOOKED`/`BLOCKED`/`MAINTENANCE`），`checkAvailability` 三處回傳改用此 code。
- **前端中文對照（AI-2408）**：`ListingDetail.tsx` 新增 code→中文訊息對照表，查無對應 code 時原樣顯示（向後相容）。

## 測試 / 驗證 ✅

- **後端單元**：全量 **515 tests，0 fail**（含修正 `BookingServiceOpenWindowTest` 一處遺漏的舊字串斷言）。
- **後端整合（真實 DB）**：全量 **422 tests，0 fail**（`BookingControllerE2ETest` 更新既有斷言為新 code）。
- **前端**：`tsc --noEmit` 0 error、`eslint` 0 error、`npm run build` 0 error；`at-room-booking.spec.ts` 更新 mock/斷言為新 code 格式。
- **schema 漂移守門（`make validate-schema`）**：無漂移（schema-free）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **不導入 i18n 框架**：確認全站無 i18n 依賴、純中文介面，採最小方案（code + 對照表）而非完整多語系框架；工程範圍判斷，未徵詢 PO。
- **範圍限定於 availability 欄位**：建單/改期超窗擋訂拋出的 `BusinessException(E_3002)` 例外訊息仍為英文，不在本次範圍（全站 BusinessException 訊息一律英文的更大範圍問題）。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 58）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 58 Plan | 5f88899 | availability reason 碼化計劃（1 US / 2 SP）|
| US-001 AI-2408 | c539283 | reason code enum + checkAvailability 改用 code + 前端對照表 + 測試更新 |
| Sprint 58 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
