# Sprint 60 計劃 / Sprint 60 Plan

> **Sprint 編號**: Sprint 60
> **期間**: 2028-01-30 ~ 2028-02-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2418，Sprint 58 探勘時發現、本次使用者授權立案並決定完整修復）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 前置技術調查 | ✅ 完成：全站 383 處 `new BusinessException(...)` 呼叫、130 個 `ErrorCode` 常數皆為英文；前端 `axios.ts` response interceptor 無任何中文化放行層，44 處前端錯誤處理直接顯示 `response.data.message`；僅 `booking.ts` 有 7 個 code 的中文映射（訂房流程），其餘 90%+ 流程使用者會直接看到後端英文字串 |
| **PO 決策：修復範圍** | ✅ **完整修復**（規劃前徵詢）：翻譯 130 個 `ErrorCode` 基礎訊息為繁體中文；`BusinessException` 中動態英文細節（如具體日期/ID/狀態名）改為僅供伺服器端 log 使用，不再回傳給前端 | 2026-07-04 PO 決策；取捨：使用者看到的訊息會變得較通用（失去部分具體細節），換取全站訊息語言一致 |
| 影響範圍確認 | ✅ 已全面搜尋確認：後端 19 處 `.getMessage()).contains(...)` 測試斷言（皆檢查 details 部分，`BusinessException.getMessage()` 語意不變，不受影響）；4 處 API 回應層級的斷言（`AdminControllerE2ETest`/`BookingControllerE2ETest`/`OrderControllerE2ETest`/`PostControllerE2ETest`）需更新為中文；前端 `booking.ts` 以 code（非 message 文字）映射，不受影響；前端 e2e 無斷言真實後端英文字串 | 已於實作階段逐一確認並修正 |
| Push 狀態 | ✅ 依新節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 60 目標

> **主題**: 全站 BusinessException 英文訊息碼化——ErrorCode 中文化 + 動態細節不外洩

翻譯 `ErrorCode` 全部 130 個常數的訊息為繁體中文；新增 `BusinessException.getUserMessage()` 只回傳 ErrorCode 基礎訊息（不含動態細節），`GlobalExceptionHandler` 改用此方法組成 API 回應；`GlobalExceptionHandler` 中其餘 7 處硬編碼英文字串一併中文化。使全站錯誤訊息不再出現使用者可見的英文字面值。

---

## 2. User Story

### US-001：全站錯誤訊息中文化（AI-2418）

> **SP**: 8 | **優先級**: P4 | **狀態**: ✅ 完成

**AC-001-1**: `ErrorCode.java` 全部 130 個常數的 `message` 翻譯為繁體中文（含 3 個帶 `%d`/`%s` 格式化佔位符的訊息，格式化語意不變）。

**AC-001-2**: `BusinessException` 新增 `getUserMessage()`，回傳 `errorCode.getMessage()`（不含 details/自訂 message 動態細節）；既有 `getMessage()`（含細節，供伺服器端 log 使用）行為不變。

**AC-001-3**: `GlobalExceptionHandler.handleBusinessException` 改用 `ex.getUserMessage()` 組成 API 回應（`ex.getMessage()` 仍用於 log 行，保留除錯用的完整細節）；另外 7 處硬編碼英文字串（`Validation failed`/`Binding failed`/`Required request parameter '...' is not present`/`Authentication required`/`Invalid credentials`/`Insufficient permissions`/`An unexpected error occurred`）一併翻譯為中文。

**AC-001-4**: 測試——更新 4 處依賴舊英文訊息內容的 API 層級斷言（`AdminControllerE2ETest`/`BookingControllerE2ETest`/`OrderControllerE2ETest`/`PostControllerE2ETest`）為對應中文文字；既有 19 處 `.getMessage()).contains(...)` 斷言（檢查 details，不受影響）與其餘測試不受影響。

**誠實揭露**：本次不修改 383 個呼叫點中 320 處的動態「details」英文字串本身（例如 `"Room is not open for booking on " + date`）——這些字串仍存在於程式碼中，只是不再回傳給前端（改為僅 log）。若未來需要在使用者訊息中保留具體細節（如「哪一天」），需要另外設計方案（例如結構化錯誤欄位），不在本次範圍。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 全站錯誤訊息中文化（AI-2418）| 8 | P4 |

> **Velocity 參考**：探勘時標注「未知（需先 spike 估點）」；確認修復範圍集中在 `ErrorCode.java`（130 行翻譯）+ `BusinessException`/`GlobalExceptionHandler`（架構性修改，不需逐一改 383 個呼叫點）+ 4 處測試斷言更新，規模收斂至可控的 8 SP（貼近歷史高點但單一 Sprint 可完成）。

---

## 4. Definition of Done

- [x] US-001：`ErrorCode.java` 130 個常數中文化 + `BusinessException.getUserMessage()` + `GlobalExceptionHandler` 改用 + 7 處硬編碼字串中文化
- [x] 4 處測試斷言更新
- [ ] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）0 fail
- [ ] `make validate-schema` 無漂移（schema-free，無 migration）
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2418 狀態更新
- [ ] Sprint 60 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 | `shared/exception/ErrorCode.java`（130 常數中文化）、`shared/exception/BusinessException.java`（新增 `getUserMessage()`）、`api/dto/GlobalExceptionHandler.java`（改用 `getUserMessage()` + 7 處字串中文化）|
| 後端測試 | `AdminControllerE2ETest.java`、`BookingControllerE2ETest.java`、`OrderControllerE2ETest.java`、`PostControllerE2ETest.java`（4 處斷言更新）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**（純後端錯誤訊息語意調整；前端已顯示 `response.data.message`，不需改前端程式碼即可看到中文效果）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
