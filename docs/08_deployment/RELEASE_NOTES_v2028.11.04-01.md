# Release Notes - v2028.11.04-01 (Sprint 79)

**發布日期**: 2028-11-04（規劃）／實作完成 2026-07-06
**發布類型**: 🔒 安全修復 + 🧪 測試補強（schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 79（多 Sprint 測試強化計劃 — 最後一輪）
**狀態**: ✅ 已 push

> Sprint 79 主題：探查 `IdempotencyService`（6 個 public 方法）與 `FeatureToggleService`（2 個 public 方法）。發現並修復 **DEF-039**：`IdempotencyService` 的 Redis key 未做租戶/使用者範圍化，跨租戶重放相同 Idempotency-Key 會導致資料洩漏；`FeatureToggleService` 探查確認乾淨，未發現缺口。本 Sprint 為「多 Sprint 測試強化計劃」（源自 Sprint 66）最後一輪，計劃排程模組全部完成。

---

## 🔍 範圍探查（US-001）

- **`IdempotencyService`（95 行）6 個 public 方法**：`checkAndMark`/`markCompleted`/`getStoredResponse`/`isStillProcessing`/`remove` 修復前 Redis key 僅為 `"idempotency:" + idempotencyKey`；`isValidUuidV4` 為純格式檢查。
- **`FeatureToggleService`（61 行）2 個 public 方法**：`checkFeatureEnabled`/`isFeatureEnabled` 皆以 `TenantContext.getCurrentTenant()` 查詢，無呼叫端可覆寫的租戶參數。

## 🔒 安全修復（US-002，DEF-039）

- **漏洞**：`Idempotency-Key` 由客戶端 HTTP header 提供（唯一呼叫端 `BookingController`），僅驗證 UUID v4 格式、不具機密性；Redis key 未範圍化導致不同租戶/使用者若送出相同值會互相碰撞，後送出方會被誤判為重複請求，且可透過 `getStoredResponse` 讀到先送出方已儲存的完整訂房回應內容（跨租戶資料洩漏）。
- **修復**：`IdempotencyService` 新增 `buildKey()` private helper，比照既有 `RedisCartService.getCartKey(userId, tenantId)` 前例，將 `TenantContext.getCurrentTenant()`/`getCurrentUser()` 納入 Redis key。
- **紅燈→綠燈**：`IdempotencyServiceTenantIsolationTest`（新檔，2 tests）以真實 `HashMap` 模擬 Redis SETNX/GET/SET 語意，修復前執行證實漏洞成立，修復後轉綠。

## 🧪 測試補強（US-002 + US-003）

- **`IdempotencyServiceTest.java`（新檔，12 個測試）**：涵蓋全部 6 個 public 方法一般行為。
- **`FeatureToggleServiceTest.java`（新檔，7 個測試）**：涵蓋 2 個方法情境 + 跨租戶查詢隔離確認。

## 測試 / 驗證 ✅

- **`IdempotencyServiceTenantIsolationTest`**：2 tests，紅燈→綠燈，0 fail。
- **`IdempotencyServiceTest`**：12 tests，0 fail。
- **`FeatureToggleServiceTest`**：7 tests，0 fail。
- **後端全量單元回歸**（`mvn test`，`make test-db-up` 後）：**0 fail**（含本 Sprint 新增 21 個）。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`）：**0 fail**（因本 Sprint 修改生產程式碼 `IdempotencyService.java`，依政策執行）。
- **驗證方式選擇**：依全量回歸頻率政策，本 Sprint 修改了生產程式碼，執行 `mvn verify -Pintegration-test`。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。

## 技術決策 / 已知限制 ⚠️

- **修復範圍嚴格限縮於 Redis key 組裝**：僅新增 `buildKey()` helper 並改寫 5 個方法的 key 組裝呼叫，未變更 `isValidUuidV4`（與租戶隔離無關）或其他不相關程式碼。
- **`DEF-025`（`BookingService.createBooking` 的 `idempotencyKey` 參數死碼）維持原決策不重新開啟**：該項目使用者已於 Sprint 71 決策擱置，屬程式碼清潔度技術債，與本次修復的安全缺口層級不同。
- **無前端變動**：本 Sprint 純後端修復 + 測試補強。

## 資料庫遷移 🗄️

- 無（schema-free；本 Sprint 未新增/修改任何 Entity 或 Repository 方法）。

## 內含 Commit（Sprint 79）

| US / 項目 | 說明 |
|----------|------|
| Sprint 79 Plan | `IdempotencyService`/`FeatureToggleService` 範圍探查 + DEF-039 修復計劃（3 US / 5 SP）|
| US-001 | `IdempotencyService`/`FeatureToggleService` 範圍探查（發現 DEF-039）|
| US-002 | 修復 `DEF-039`（`IdempotencyService.buildKey()`）+ `IdempotencyServiceTenantIsolationTest.java`（2 tests）+ `IdempotencyServiceTest.java`（12 tests）|
| US-003 | `FeatureToggleServiceTest.java` 新增 7 個測試 |
| Sprint 79 收尾 | Review / Retro / Release Notes + trackers（多 Sprint 測試強化計劃全部完成）|

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
