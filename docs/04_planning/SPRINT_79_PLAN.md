# Sprint 79 計劃 / Sprint 79 Plan

> **Sprint 編號**: Sprint 79
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_78_PLAN.md` §7「後續 Sprint 待處理清單」——`IdempotencyService`、`FeatureToggleService`（多 Sprint 測試強化計劃剩餘最後 2 個模組，本 Sprint 完成後計劃全部結束）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 78 收尾狀態 | ✅ 已收尾並 push | `OAuthService`/`PromoService` 探查 + 測試補齊完成，未發現新缺口 |
| `IdempotencyService` 範圍探查 | ✅ 完成 | `core/idempotency/IdempotencyService.java`（95 行，6 個 public 方法） |
| `FeatureToggleService` 範圍探查 | ✅ 完成 | `core/feature/FeatureToggleService.java`（61 行，2 個 public 方法） |
| 探查中發現的擁有權/租戶隔離問題 | ⚠️ **發現 1 項並修復（DEF-039）** | `IdempotencyService` 的 Redis key 未做租戶/使用者範圍化，詳見「1.1 探查結論」；`FeatureToggleService` 未發現缺口 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### 1.1 `IdempotencyService`（`core/idempotency/IdempotencyService.java`，95 行，6 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `checkAndMark(String idempotencyKey)` | 無 | ❌ Redis key 僅為 `"idempotency:" + idempotencyKey`，完全沒有租戶/使用者範圍化 |
| `markCompleted(String idempotencyKey, Object response)` | 無 | ❌ 同上 |
| `getStoredResponse(String idempotencyKey)` | 無 | ❌ 同上 |
| `isStillProcessing(String idempotencyKey)` | 無 | ❌ 同上 |
| `remove(String idempotencyKey)` | 無 | ❌ 同上 |
| `isValidUuidV4(String key)` | 無 | 純格式檢查，不涉及擁有權 |

**探查結論（DEF-039，已修復）**：`idempotencyKey` 由客戶端經 `Idempotency-Key` HTTP header 提供（唯一呼叫端 `BookingController.createBooking`），僅驗證 UUID v4 格式，不具備任何機密性。與既有 `RedisCartService.getCartKey(userId, tenantId)` 同一類「Redis key 必須含租戶/使用者範圍」問題，但本服務未依此前例設計。紅燈測試證實：租戶 B 若送出與租戶 A 相同的 Idempotency-Key，會被誤判為重複請求並讀到租戶 A 已儲存的訂房回應內容，屬跨租戶資料洩漏。詳見「2. Sprint 79 目標」與 `DEFERRED_ITEMS_TRACKER.md`。

### 1.2 `FeatureToggleService`（`core/feature/FeatureToggleService.java`，61 行，2 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `checkFeatureEnabled(String featureKey)` | 無 | ✅ 正常，一律以 `TenantContext.getCurrentTenant()` 查詢，無可由呼叫端覆寫的參數 |
| `isFeatureEnabled(String featureKey)` | 無 | ✅ 正常，同上 |

**探查結論**：`FeatureToggleService` 設計正確，未發現需要修復的擁有權/租戶檢查缺口。

---

## 2. Sprint 79 目標

> **主題**: `IdempotencyService`/`FeatureToggleService` 單元測試補齊（先前皆為零單元測試覆蓋）+ 修復 `DEF-039`（`IdempotencyService` 跨租戶資料洩漏，安全，已確認）；本 Sprint 完成後「多 Sprint 測試強化計劃」（源自 Sprint 66）全部結束

---

## 3. User Story

### US-001：`IdempotencyService`/`FeatureToggleService` 範圍探查

> **SP**: 1 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**：找到兩個 Service，列出檔案路徑、行數、全部 public 方法、既有測試狀況、依賴的 Repository/Redis，確認每個方法是否有明顯的擁有權/租戶檢查邏輯。

**AC-001-2**：探查中發現 `IdempotencyService` 的 Redis key 未做租戶/使用者範圍化（`DEF-039`），比對既有前例 `RedisCartService.getCartKey` 確認修復方案清楚可循，屬有明確前例的技術修復，不涉及業務判斷。

**AC-001-3**：確認 `FeatureToggleService` 租戶隔離設計正確，無需修復。

---

### US-002：修復 `DEF-039`（`IdempotencyService` 跨租戶資料洩漏）+ 單元測試補齊

> **SP**: 3 | **優先級**: P0（安全） | **狀態**: ✅ 完成

**AC-002-1**：紅燈測試 `IdempotencyServiceTenantIsolationTest` 證實：不同租戶使用相同 Idempotency-Key 時，後送出的租戶會被誤判為重複請求，並可透過 `getStoredResponse` 讀到先送出租戶的已儲存回應。

**AC-002-2**：修復 `IdempotencyService`，新增 `buildKey()` helper 將 `TenantContext.getCurrentTenant()`/`getCurrentUser()` 納入 Redis key（比照 `RedisCartService.getCartKey` 前例），5 個涉及 Redis key 組裝的方法統一改用此 helper；紅燈測試轉綠，且同租戶同使用者重複請求的既有冪等行為不回歸。

**AC-002-3**：新增 `IdempotencyServiceTest.java` 涵蓋全部 6 個 public 方法的一般行為（含 `isValidUuidV4` 格式驗證邊界案例）。

**AC-002-4**：因修改生產程式碼，`mvn verify -Pintegration-test` 全量回歸 0 fail。

---

### US-003：`FeatureToggleService` 單元測試補齊

> **SP**: 1 | **優先級**: P1（測試防護網） | **狀態**: ✅ 完成

**AC-003-1**：新增 `FeatureToggleServiceTest.java`，涵蓋 `checkFeatureEnabled`（啟用/停用/查無紀錄）、`isFeatureEnabled`（啟用/停用/查無紀錄，fail-closed 不拋例外）、以及跨租戶情境下查詢一律使用當前 `TenantContext` 的 tenantId（`ArgumentCaptor` 驗證）。

**AC-003-2**：`mvn test` 全數通過。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `IdempotencyService`/`FeatureToggleService` 範圍探查 | 1 | P2 |
| US-002 | 修復 `DEF-039` + `IdempotencyService` 單元測試補齊 | 3 | P0 |
| US-003 | `FeatureToggleService` 單元測試補齊 | 1 | P1 |
| **合計** | | **5** | |

> **Velocity 參考**：規模與 Sprint 78 相當（皆為多 Sprint 測試強化計劃中規模較小的模組），因發現一項有明確前例可循的安全缺口，依指示直接修復不停下確認。

---

## 5. Definition of Done

- [x] US-001：完整探查 `IdempotencyService`（6 個 public 方法）與 `FeatureToggleService`（2 個 public 方法），確認既有測試覆蓋與擁有權檢查現況
- [x] US-002：紅燈測試證實 `DEF-039` 成立 → 修復 `IdempotencyService.buildKey()` → 轉綠；新增 `IdempotencyServiceTenantIsolationTest.java`（2 tests）+ `IdempotencyServiceTest.java`（12 tests）
- [x] US-003：新增 `FeatureToggleServiceTest.java`（7 tests）
- [x] 開發-編譯-測試循環：每完成一個測試檔案立即編譯 + 執行驗證，未累積
- [x] 本 Sprint**修改了生產程式碼**（`IdempotencyService.java`），依全量回歸頻率政策需執行 `mvn verify -Pintegration-test`（先 `make test-db-up`）：**1191 tests 0 fail**（單元 849 + 整合 342）
- [x] 附加修正：清除 Sprint 77 遺留的 `NotificationServiceTest.java` checkstyle-test 未使用 import 違規（`anyInt`/`Page`/`PageRequest`），重跑全量回歸確認乾淨
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-039` 並直接記錄於「已完成延後項目」（本 Sprint 內修復結案）
- [x] Sprint 79 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 生產程式碼修復 | `IdempotencyService.java`（新增 `buildKey()` 租戶/使用者範圍化） |
| 後端測試（新檔） | `IdempotencyServiceTenantIsolationTest.java`（2 個測試，紅燈→綠燈） |
| 後端測試（新檔） | `IdempotencyServiceTest.java`（12 個測試） |
| 後端測試（新檔） | `FeatureToggleServiceTest.java`（7 個測試） |
| DEF 追蹤 | `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-039`（已修復結案） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 多 Sprint 測試強化計劃收尾

本 Sprint 為「多 Sprint 測試強化計劃」（源自 Sprint 66 全面盤點）最後一輪，計劃完整回顧詳見 `SPRINT_79_RETRO.md`。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
