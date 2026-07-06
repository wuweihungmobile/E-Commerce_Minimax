# Sprint 76 計劃 / Sprint 76 Plan

> **Sprint 編號**: Sprint 76
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_74_PLAN.md`/`SPRINT_75_PLAN.md` §7「後續 Sprint 待處理清單」——`LogisticsService`
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 75 收尾狀態 | ✅ 已收尾並 push | `ChatService` 探查確認本身無缺口 + `StompAuthChannelInterceptor` SUBSCRIBE 授權缺失修復（`DEF-035`）完成 |
| `LogisticsService` 範圍探查 | ✅ 完成 | 1 個 Service（`core/logistics/LogisticsService.java`，7 個 public 方法、329 行）+ `LogisticsController`（123 行） |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已標記並確認：`createLogistics`（`DEF-019`，Sprint 36 已修復）以外的**其餘 6 個方法皆無租戶擁有權檢查**（`DEF-036`，新發現），已確認並修復 | 詳見「1. 既有覆蓋現況」、US-002（`DEF-036`） |
| 探查誠實揭露 | 使用者特別提醒「`checkOrderTenant` 已存在不代表全部方法都安全」，本 Sprint 逐一審視後證實此提醒完全命中——`createLogistics` 是唯一有擁有權檢查的方法，其餘 6 個方法（含 2 個寫入操作 `updateLogisticsStatus`/`cancelLogistics`）皆無檢查 | 依 Rule 12「大聲失敗」誠實揭露：`cancelLogistics` 修復前雖已有 `DEF-011` 的錯誤碼測試（`LogisticsServiceCancelTest`），但完全未涵蓋擁有權面向，本 Sprint 需同步更新該既有測試的 fixture |
| 附帶探查（`ShippingTemplateService`） | ✅ 完成，發現 `calculateFee` 缺租戶過濾（`DEF-037`），使用者已決策**擱置僅記錄，不修復** | 詳見「1.2」、`DEFERRED_ITEMS_TRACKER.md` |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### 1.1 `LogisticsService`（`core/logistics/LogisticsService.java`，329 行，7 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況（修復前） |
|---|---|---|
| `createLogistics` | 單元（`LogisticsServiceOwnershipTest`，`DEF-019`） | ✅ 正常，`checkOrderTenant`（本租戶 or admin 放行） |
| `getLogistics` | 無 | ❌ 缺口——`findById` 後直接回傳，Controller 僅要求 `order:read` |
| `getLogisticsByOrderId` | 無 | ❌ 缺口——`findByOrderId` 無租戶過濾 |
| `trackLogistics` | 無 | ❌ 缺口——同上 |
| `getTrackingDetail` | 無 | ❌ 缺口——同上 |
| `updateLogisticsStatus` | 無 | ❌ 缺口（寫入操作，可竄改他租戶物流/連動訂單狀態）——Controller 僅要求 `order:update` |
| `cancelLogistics` | 單元（`LogisticsServiceCancelTest`，`DEF-011` 錯誤碼），**未涵蓋擁有權** | ❌ 缺口（寫入操作）——同上 |

**結論**：`createLogistics` 是唯一有擁有權檢查的方法；其餘 6 個方法全數缺口，與 `DEF-019`/`DEF-024`/`DEF-028`/`DEF-032` 同一 tenant-based IDOR 模式，前例清楚，本 Sprint 直接修復（未等待確認）。

### 1.2 `ShippingTemplateService`（附帶檢查，`core/logistics/ShippingTemplateService.java`，139 行，6 個 public 方法）

| 方法 | 擁有權/租戶檢查現況 |
|---|---|
| `createTemplate` | ✅ 正常，`tenantId` 由 Controller 經 `TenantContext` 傳入 |
| `getTemplates` | ✅ 正常，同上 |
| `updateTemplate` | ✅ 正常，`.filter(t -> tenantId.equals(t.getTenantId()))` |
| `deleteTemplate` | ✅ 正常，同上 |
| `calculateFee` | ❌ 缺口——`templateId` 未驗證租戶歸屬（`DEF-037`，新發現） |
| `calculateFeeForTenant` | ✅ 正常，`tenantId` 由呼叫端（結帳流程）明確傳入 |

**結論**：CRUD 四個方法皆已正確隔離，僅 `calculateFee` 缺口。因回傳內容僅運費計算參數（`feeType`/`fixedAmount`/`freeThreshold`），無 PII、無寫入風險，且是否應收斂為僅限模板所屬租戶存取涉及「是否要支援買家跨租戶比價試算」的業務判斷，本 Sprint 依範圍僅記錄（`DEF-037`）、**使用者已確認擱置不修復**。

---

## 2. Sprint 76 目標

> **主題**: `LogisticsService` 範圍探查 + 除 `createLogistics` 外其餘 6 個方法的租戶擁有權檢查修復（`DEF-036`，新發現）

---

## 3. User Story

### US-001：`LogisticsService`（+ `ShippingTemplateService`）範圍探查

> **SP**: 1 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**：列出 `LogisticsService` 全部 7 個 public 方法，逐一確認既有測試涵蓋（單元/整合/E2E）與擁有權/租戶檢查現況。

**AC-001-2**：以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」角度逐一審視，比對 `DEF-019/024/028/032` 歷史模式，特別驗證「`checkOrderTenant` 已存在是否代表全部方法都安全」。

**AC-001-3**：時間充裕，一併檢查 `ShippingTemplateService` 6 個 public 方法。

---

### US-002：修復 `LogisticsService` 其餘 6 個方法的租戶擁有權檢查缺口（`DEF-036`，新發現，已確認）

> **SP**: 3 | **優先級**: P0（安全） | **狀態**: ✅ 完成

**背景**：`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics` 皆直接 `findById`/`findByOrderId` 後即讀取或寫入，完全沒有租戶擁有權檢查；`LogisticsController` 僅以 `order:read`/`order:update` 權限把關（權限分散於各租戶角色，非租戶範圍限制），任一租戶皆可跨租戶讀取物流單詳情/追蹤歷史或竄改其狀態，屬跨租戶 IDOR。

**AC-002-1**：先在 `LogisticsServiceTenantAccessTest.java`（新檔）新增**修復前會失敗（紅燈）**測試：他租戶讀取/追蹤/竄改他人物流單，驗證修復前皆未被攔截。

**AC-002-2**：新增 `checkLogisticsTenant(Logistics)` helper（依 `orderId` 反查 `Order` 後委派既有 `checkOrderTenant`），6 個方法皆於狀態/業務邏輯檢查**之前**呼叫（IDOR 正確順序）；`checkOrderTenant` 訊息由「create」改為通用措辭。

**AC-002-3**：AC-002-1 測試轉綠；同步更新既有 `LogisticsServiceCancelTest`（`cancelLogistics` 新增檢查後需要 `orderRepository`/`TenantContext` fixture）；新增 admin 跨租戶放行對照測試，避免修復矯枉過正。

---

### 附帶項目：`ShippingTemplateService.calculateFee` 跨租戶查詢（`DEF-037`，新發現，已記錄擱置）

> **SP**: 0（僅記錄，使用者已決策不修復）

`calculateFee(templateId, orderAmount)` 未驗證 `templateId` 租戶歸屬，任一登入使用者可查得他租戶運費模板設定。因無 PII/寫入風險，且是否應限制涉及「買家跨租戶比價試算」的既有使用情境判斷，記入 `DEFERRED_ITEMS_TRACKER.md`（`DEF-037`），**使用者已明確決策擱置，不列入本 Sprint 修復範圍**。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `LogisticsService`（+`ShippingTemplateService`）範圍探查 | 1 | P2 |
| US-002 | 修復 `LogisticsService` 租戶擁有權檢查缺口（`DEF-036`） | 3 | P0（安全） |
| 附帶 | `DEF-037` 記錄（不修復） | 0 | — |
| **合計** | | **4** | |

> **Velocity 參考**：與 Sprint 75（4 SP）量級相近——探查範圍集中、修復模式有清楚前例（`checkOrderTenant`），實際工作量為 6 個呼叫點套用同一 helper + 對應測試。

---

## 5. Definition of Done

- [x] US-001：完整探查 `LogisticsService` 7 個 public 方法 + `ShippingTemplateService` 6 個 public 方法，確認既有測試覆蓋與擁有權檢查現況
- [x] US-002：新增 `LogisticsServiceTenantAccessTest.java`（8 個測試）→ 紅燈確認失敗（暫時還原程式碼至修復前執行，5 個跨租戶案例未被攔截 + 3 個因 stub 未被呼叫觸發 `UnnecessaryStubbingException`）→ 修復 `LogisticsService` → 轉綠（8 tests 0 fail）
- [x] 同步更新既有 `LogisticsServiceCancelTest.java`（補上 `orderRepository`/`TenantContext` fixture），3 tests 0 fail
- [x] 開發-編譯-測試循環：每完成一批測試立即編譯 + 執行驗證，未累積
- [x] 因本 Sprint 修改生產程式碼（US-002），完成後執行全量回歸 `mvn verify -Pintegration-test`，**BUILD SUCCESS**：單元 749 + 整合（failsafe）342 = **1091 tests，0 fail**
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-036`（記入「已完成延後項目」）+ `DEF-037`（記入「中優先級」，使用者已決策擱置）
- [x] Sprint 76 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試（新檔） | `LogisticsServiceTenantAccessTest.java`（8 個測試） |
| 後端測試（更新） | `LogisticsServiceCancelTest.java`（補上擁有權 fixture） |
| 生產程式碼修復 | `LogisticsService.java`（`DEF-036`，新增 `checkLogisticsTenant` helper + 6 處呼叫） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-036`/`DEF-037`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 77+ 規劃參考：

1. `NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`（建議先做輕量探查再估點數）
2. 待決策事項：`DEF-034`（`CmsService` 公開端點租戶範圍設計）、`DEF-037`（`ShippingTemplateService.calculateFee` 跨租戶查詢，已決策擱置）、`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點（延續自 Sprint 73-75 Retro）
3. 新觀察（本 Sprint 發現）：同一 Service 內「已修復一個方法」不代表其餘方法安全——`DEF-019` 只修了 `createLogistics`，其餘 6 個方法的缺口存在了 40 個 Sprint（Sprint 36 → Sprint 76）才被發現，未來每個新 Service 探查都應**逐一列出全部 public 方法**檢查，不可因單一方法已有防護而跳過同檔案其餘方法

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
