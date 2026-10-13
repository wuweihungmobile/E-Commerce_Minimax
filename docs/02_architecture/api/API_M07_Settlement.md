# API 規格文件 - M07 結算與撥款 / API Specification - M07 Settlement & Transfer

> **文件編號**: API-M07
> **文件版本**: v1.0（Sprint 262，首次建立；Sprint 16 規劃時僅留一張 6 端點的草表於 `API_Index.md`，從未真正建立獨立文件，DEF-364）
> **建立日期**: 2026-10-13
> **最後更新**: 2026-10-13
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`SettlementController`、`TransferController`（`api/controller/settlement/`）、`SettlementGenerator`、`SettlementReviewer`、`SettlementReversalService`、`TransferService`、`SettlementCalculator`（`core/settlement/`）；回應形狀、狀態碼、錯誤碼與權限宣稱皆逐行讀碼核對，部分另有真實測試佐證：[TransferControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/TransferControllerE2ETest.java)（真實 HTTP）、[M07SettlementIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M07SettlementIntegrationTest.java)、[SettlementReversalServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReversalServiceTest.java)、[SettlementReviewerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReviewerTest.java)、[SettlementGeneratorManualTriggerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementGeneratorManualTriggerTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **取代關係**: 本文件取代 `API_Index.md` 內嵌的 Sprint 15/16 舊表格（API-M07-S-001～006 編號維持不變，供 [TC_M07_Settlement.md](../../03_testing/TC_M07_Settlement.md) 既有引用相容）

> **⚠️ 本文件撰寫範圍說明**：結算與撥款涉及的「訂單／訂房付款、Mock 付款、Stripe Checkout、管理員退款」等端點屬 M05／M06，已各自記載於 [API_M05_Order.md](API_M05_Order.md) §7、[API_M06_Booking.md](../API_M06_Booking.md) §4.8～4.10／4.15，**本文件只收錄結算單（Settlement Statement）與撥款對帳/重試（Transfer）本身的端點**，對應 FRD §6B.6 標註「API_M07 尚未建立」的兩類：結算、撥款。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M07 |
| **模組名稱** | 結算與撥款 / Settlement & Transfer |
| **描述** | 每週一 00:00（營運時區）自動彙總每個店鋪當週已完成的訂單與訂房為一張結算單；商家提交審核、管理員核准或駁回；核准後系統自動呼叫 Stripe Connect 撥款（`Transfer`）；撥款失敗可由管理員重試；已撥款（`PAID`）的結算單如發現錯誤，可由 `SUPER_ADMIN` 與 `CFO` 雙重授權發起並確認「逆轉」，產生會計沖銷記錄（`CreditNote`）。 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/settlements/...`、`/api/v2/transfers` 等 |
| **多租戶隔離** | 結算單與 transfer 記錄的 `tenantId` 是**商家店鋪**。商家只能讀寫自己店鋪的結算單；`ADMIN` 限自己租戶，**`SUPER_ADMIN` 與 `CFO` 可跨租戶**（逆轉候選列表、沖正本身一律跨租戶，見 §4.8～4.10）；系統租戶（沒有店鋪的呼叫者）一律回空清單或 404，不當作任何一間店鋪 |
| **範圍外** | 訂單／訂房付款本身、Mock 付款、Stripe Checkout、管理員退款（見上方範圍說明）、GMV／抽成的計算公式細節（`SettlementCalculator`，見 §3.3） |

### 1.2 設計重點

1. **結算單狀態機**：`PENDING → PENDING_REVIEW → APPROVED → PAID`（正常路徑）；`PENDING_REVIEW → REJECTED`（駁回，釋放已認領的訂單/訂房，讓下一期重新結算）；`APPROVED → FAILED`（撥款失敗，可重試回到 `APPROVED` 再次嘗試）；`PAID → REVERSAL_PENDING → REVERSED`（雙重授權逆轉，需 `SUPER_ADMIN` 與 `CFO` 各一人、角色不同）。**狀態轉換一律用 `BusinessException(E_5014)` 擋非法轉換**，沒有獨立的狀態圖算式，以程式碼的 `if (status != X)` 判斷為準。
2. **每筆訂單/訂房恰好結算一次**：`SettlementGenerator` 用 `UPDATE ... WHERE settled_statement_id IS NULL` 原子認領當週已完成的訂單與訂房，認領不足即回滾整張結算單（避免漏單或重複結算）；駁回時反向釋放（`releaseOrdersOfStatement`／`releaseBookingsOfStatement`／`releaseAppliedTo`），讓下一期能重新認領。
3. **撥款與審核解耦**：`SettlementReviewer.approveStatement` 核准後立即嘗試呼叫 `TransferService.createTransferForStatement`，但**任何撥款失敗都只記錄，絕不回滾「已核准」這個 Admin 決策**（try-catch 隔離）。`Transfer` 記錄以 `settlement_statement_id` 唯一索引**原子佔位**防止併發重複撥款；賣家 Stripe Connect 帳戶未完成 onboarding 時狀態為 `SKIPPED_ONBOARDING_INCOMPLETE`（非失敗，可之後補建）。
4. **手動補產（DEF-287，Sprint 208）**：僅 `SUPER_ADMIN`（`settlement:generate`）可用，供排程漏產補救或上線前 Stripe 測試走查；與排程共用同一套冪等與認領防護，不會重複結算同一筆訂單。
5. **逆轉不處理真實資金收回**：`SettlementReversalService` 的「逆轉」只產生 `CreditNote` 會計沖銷記錄，**不呼叫任何 Stripe API 把錢拿回來**（Sprint 80/81 既定範圍界線）。真正的資金收回只能靠 Stripe 主動發出的 `transfer.reversed` webhook（`TransferService.handleTransferReversedWebhook`，見 [API_M05_Order.md](API_M05_Order.md) §8 Webhook 章節），兩條路徑彼此不覆寫已進入正式流程的狀態（見 §4.10 備註）。
6. **抽成採租戶自訂費率**：`commissionAmount = GMV × Tenant.commissionRate`（四捨五入至 2 位小數），非固定費率（Sprint 80 起取代先前硬編碼 10%）。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M07-001 | 商家查詢結算單列表與詳情 | `GET /v2/settlements`、`GET /v2/settlements/{statementId}` | ✅ 已實作 |
| US-M07-002 | 商家提交結算單審核 | `PUT /v2/settlements/{statementId}/submit` | ✅ 已實作 |
| US-M07-003 | 管理員審核（核准／駁回）結算單 | `GET /v2/admin/settlements/pending`、`PUT …/approve`、`PUT …/reject` | ✅ 已實作 |
| US-M07-004 | 管理員手動補產結算單 | `POST /v2/admin/settlements/generate` | ✅ 已實作（DEF-287） |
| US-M07-005 | 查詢撥款對帳記錄 | `GET /v2/transfers` | ✅ 已實作 |
| US-M07-006 | 管理員重試失敗撥款 | `POST /v2/admin/transfers/{statementId}/retry` | ✅ 已實作 |
| US-M07-007 | SUPER_ADMIN／CFO 雙重授權沖正已撥款結算單 | `GET /v2/admin/settlements/reversal-candidates`、`POST …/reverse/initiate`、`POST …/reverse/confirm` | ✅ 已實作（PRD §6.2.1） |

---

## 2. API 端點總覽 / Endpoints Summary

| API ID | 方法 | 端點 | 說明 | 角色 |
|--------|:----:|------|------|------|
| API-M07-S-001 | GET | `/v2/settlements` | 商家結算單列表 | StoreOwner+ |
| API-M07-S-002 | GET | `/v2/settlements/{statementId}` | 商家結算單詳情 | StoreOwner+ |
| API-M07-S-003 | PUT | `/v2/settlements/{statementId}/submit` | 商家提交審核 | StoreOwner+ |
| API-M07-S-004 | GET | `/v2/admin/settlements/pending` | Admin 待審核列表 | Admin |
| API-M07-S-005 | PUT | `/v2/admin/settlements/{statementId}/approve` | Admin 核准 | Admin |
| API-M07-S-006 | PUT | `/v2/admin/settlements/{statementId}/reject` | Admin 駁回 | Admin |
| **🆕 API-M07-S-007** | POST | `/v2/admin/settlements/generate` | 手動補產結算單（DEF-287） | SuperAdmin |
| **🆕 API-M07-S-008** | GET | `/v2/admin/settlements/reversal-candidates` | 逆轉候選列表 | SuperAdmin／CFO |
| **🆕 API-M07-S-009** | POST | `/v2/admin/settlements/{statementId}/reverse/initiate` | 發起逆轉 | SuperAdmin／CFO |
| **🆕 API-M07-S-010** | POST | `/v2/admin/settlements/{statementId}/reverse/confirm` | 確認逆轉（需換人） | SuperAdmin／CFO |
| **🆕 API-M07-T-001** | GET | `/v2/transfers` | 撥款對帳記錄 | 同結算單 |
| **🆕 API-M07-T-002** | POST | `/v2/admin/transfers/{statementId}/retry` | 重試失敗撥款 | Admin |

標 🆕 的 6 個端點是本文件（Sprint 262）新增記載；其餘 6 個沿用 `API_Index.md` 原有的 Sprint 15/16 編號與內容，經本輪核對路由仍一致。

---

## 3. 資料模型 / Data Models

### 3.1 `SettlementStatement`

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | UUID | 主鍵 |
| `statementNumber` | String | `STL-{tenantId前8碼}-{periodStart yyyyMMdd}` |
| `tenantId` | UUID | 商家店鋪 |
| `periodStart`／`periodEnd` | LocalDate | 結算週期（含頭含尾） |
| `totalOrders`／`totalBookings` | Integer | 本期彙總的訂單／訂房筆數（`totalBookings` 為 Sprint 247 新增，DEF-353） |
| `totalGmv`／`totalRefunds`／`commissionAmount`／`netSettlementAmount`／`adjustmentAmount` | BigDecimal | 金額欄位，`netSettlementAmount` 為實際撥款金額 |
| `currency` | String | 預設 `TWD` |
| `status` | enum | `PENDING`、`PENDING_REVIEW`、`APPROVED`、`REJECTED`、`PAID`、`FAILED`、`REVERSAL_PENDING`、`REVERSED` |
| `generatedAt`／`reviewedAt`／`approvedAt`／`paidAt` | Instant | 各階段時間戳 |
| `rejectionReason` | String | 駁回原因 |
| `reversalInitiatedBy`／`reversalInitiatedByRole`／`reversalRequestedAt`／`reversalReason` | — | 逆轉發起資訊（§4.9） |

完整 DDL 見 [SRD_Database_Schema.md](../SRD_Database_Schema.md)。

### 3.2 `Transfer`（領域模型，非獨立資料表以外還有 `transfers` 表）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | UUID | 主鍵 |
| `settlementStatementId` | UUID | **唯一索引**（`idx_transfers_settlement_statement_id`），一張結算單僅一筆 transfer（冪等） |
| `tenantId` | UUID | 商家店鋪 |
| `stripeTransferId` | String | Stripe `Transfer` 物件 ID，撥款成功才有值 |
| `transferAmount` | BigDecimal | 觸發當下 `SettlementStatement.netSettlementAmount` 的快照 |
| `currency` | String | 預設 `TWD` |
| `status` | enum | `PENDING`、`COMPLETED`、`FAILED`、`SKIPPED_ONBOARDING_INCOMPLETE`、`REVERSED` |
| `failureReason` | String | 失敗或略過原因 |

### 3.3 `CreditNote`（逆轉產生，僅會計沖銷記錄，不處理資金收回）

| 欄位 | 說明 |
|------|------|
| `creditNoteNumber` | `CN-{tenantId前8碼}-{今日yyyyMMdd}` |
| `amount` | 原結算 `netSettlementAmount` 的**負數**（沖銷） |
| `status` | `PENDING`（本文件範圍內一律為此值，無後續狀態轉換端點） |

### 3.4 GMV／抽成計算（`SettlementCalculator`，純函數，供背景參考，非本文件端點）

`commissionAmount = GMV × Tenant.commissionRate`（四捨五入至 2 位小數），取代 Sprint 80 以前硬編碼的 10% 固定費率，見 FRD BR-M07-09～12。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `GET /v2/settlements` — 商家結算單列表

**權限**：`order:read`（StoreOwner、Host、Admin、Super Admin 持有；BUYER 技術上也持有此權限，但下方租戶守門會回空清單，見備註）。查詢參數：`page`（預設 0）、`size`（預設 20，上限 100）。成功 `200`，`data` 是 `SettlementStatementListResponse`。

> **沒有店鋪的呼叫者回空清單，不是錯誤**：`getStatementsByTenant` 對 `TenantContext.isStoreTenant(tenantId)` 為 `false` 時直接回傳空分頁，理由是「每週結算會替每個 `ACTIVE` 租戶（含系統租戶）產生結算單，原本任何買家都讀得到平台的 GMV、抽成與退款」（Sprint 234 既有防護）。

### 4.2 `GET /v2/settlements/{statementId}` — 商家結算單詳情

**權限**：`order:read`，且限**自己租戶**（`findByIdAndTenantId`，非自己租戶或無店鋪一律視為找不到）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在，或存在但不屬於呼叫者租戶，或呼叫者無店鋪 | 404 | `E-5013` |

### 4.3 `PUT /v2/settlements/{statementId}/submit` — 商家提交審核

**權限**：`order:read`，限自己租戶。`PENDING → PENDING_REVIEW`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在／不屬於呼叫者租戶／呼叫者無店鋪 | 404 | `E-5013` |
| 狀態不是 `PENDING` | 409 | `E-5014` |

### 4.4 `GET /v2/admin/settlements/pending` — Admin 待審核列表

**權限**：`admin:read`。非 `SUPER_ADMIN` 僅能查自己租戶；`SUPER_ADMIN` 可用 `tenantId` 查詢參數指定租戶，未指定則跨租戶總覽（Sprint 81，DEF-040 修復）。查詢參數：`tenantId`（選填）、`page`、`size`。

### 4.5 `PUT /v2/admin/settlements/{statementId}/approve` — Admin 核准

**權限**：`admin:write`。非 `SUPER_ADMIN` 僅能核准自己租戶（Sprint 81，DEF-040 修復）。`PENDING_REVIEW → APPROVED`，並寫入稽核紀錄（`SETTLEMENT_APPROVED`）。**核准成功後立即嘗試觸發撥款**（`TransferService.createTransferForStatement`，見 §4.11）；撥款失敗只記錄 log，**回應仍是 `200` 且結算單狀態仍是 `APPROVED`**（撥款結果需另呼叫 §4.1/4.2 或 §4.7 查詢）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 狀態不是 `PENDING_REVIEW` | 409 | `E-5014` |

### 4.6 `PUT /v2/admin/settlements/{statementId}/reject` — Admin 駁回

**權限**：`admin:write`。非 `SUPER_ADMIN` 僅能駁回自己租戶。`PENDING_REVIEW → REJECTED`，查詢參數 `reason`（選填），寫入稽核紀錄（`SETTLEMENT_REJECTED`）。**駁回會釋放本期已認領的訂單、訂房與折入的調整單**（`releaseOrdersOfStatement`／`releaseBookingsOfStatement`／`releaseAppliedTo`），讓下一期重新結算——否則這些訂單/訂房會永遠掛在一張死掉的結算單上，調整單（賣家該被扣的退款）也隨之消失。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 狀態不是 `PENDING_REVIEW` | 409 | `E-5014` |

### 4.7 `POST /v2/admin/settlements/generate` — 手動補產結算單（DEF-287，Sprint 208）🆕

**權限**：`settlement:generate`（**僅 `SUPER_ADMIN` 持有**，`RolePermissionMapping` 未對任何其他角色個別授予）。用途：(1) 排程當週漏產/失敗時補救；(2) 上線前以 Stripe 測試模式走查撥款，不必等下週一排程。查詢參數：`tenantId`（必填）、`periodStart`（必填，`yyyy-MM-dd`）、`periodEnd`（選填，未指定則 `periodStart + 6` 天，比照週結算天數）。

**冪等**：與排程共用同一套「同租戶＋期間已有結算單就直接回傳既有的」檢查與「原子認領訂單/訂房」併發防護——手動觸發不會讓同一筆訂單被兩張結算單重複結算。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `periodEnd` 早於 `periodStart` | 400 | `E-9008` |
| 租戶不存在 | 404 | `E-2000` |

### 4.8 `GET /v2/admin/settlements/reversal-candidates` — 逆轉候選列表 🆕

**權限**：`settlement:reverse`（`SUPER_ADMIN` 或 `CFO`）。回傳狀態為 `PAID`（可發起逆轉）或 `REVERSAL_PENDING`（可確認逆轉）的結算單，**一律跨租戶**（不區分 `SUPER_ADMIN`／`CFO`，也不提供「限自己租戶」回退——與 §4.9/4.10 本就一律跨租戶操作對等）。查詢參數：`tenantId`（選填，篩選特定租戶）、`page`、`size`。

### 4.9 `POST /v2/admin/settlements/{statementId}/reverse/initiate` — 發起逆轉 🆕

**權限**：`settlement:reverse`（`SUPER_ADMIN` 或 `CFO`）。`PAID → REVERSAL_PENDING`。查詢參數：`reason`（選填）。寫入 `reversalInitiatedBy`／`reversalInitiatedByRole`／`reversalRequestedAt`／`reversalReason`，供 §4.10 的「換人」檢查比對。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 發起人角色不是 `SUPER_ADMIN`／`CFO` | 403 | `E-1007` |
| 狀態不是 `PAID`（含併發搶占失敗，條件式更新 `updateStatusIfCurrent` 回 0 筆，Sprint 137 DEF-138 同款防護） | 409 | `E-5014` |
| 發起人使用者不存在（理論上不會發生，JWT 已驗證） | 404 | `E-1006` |

### 4.10 `POST /v2/admin/settlements/{statementId}/reverse/confirm` — 確認逆轉（雙重授權核心）🆕

**權限**：`settlement:reverse`（`SUPER_ADMIN` 或 `CFO`）。`REVERSAL_PENDING → REVERSED`，成功後產生 `CreditNote`（§3.3）。**確認人角色必須與發起人角色不同**（例如發起人是 `SUPER_ADMIN`，確認人須是 `CFO`，反之亦然）——這是雙重授權機制的核心檢查，單一角色無法自己發起又自己確認。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 確認人角色不是 `SUPER_ADMIN`／`CFO` | 403 | `E-1007` |
| 狀態不是 `REVERSAL_PENDING` | 409 | `E-5014` |
| **確認人角色與發起人角色相同**（雙重授權失敗） | 403 | `E-1007`（訊息：「Reversal confirmation must come from a different role than the initiator」） |
| 確認人使用者不存在 | 404 | `E-1006` |

> **逆轉與 Stripe webhook 的優先順序**：若 Stripe 主動送達 `transfer.reversed` webhook（`TransferService.handleTransferReversedWebhook`），只有結算單仍是 `PAID` 時才會被 webhook 改回 `FAILED`；若本端點已將其推進到 `REVERSAL_PENDING`／`REVERSED`，webhook **不會覆寫**已在正式雙重授權流程中處理或完成的決策，只記錄 info log。

### 4.11 `GET /v2/transfers` — 撥款對帳記錄 🆕

**權限**：`order:read`。一般角色（含 `ADMIN`）僅能查自己租戶；`SUPER_ADMIN` 可加 `tenantId` 查詢參數查任意租戶。查詢參數：`tenantId`（選填，僅 `SUPER_ADMIN` 有效）、`page`、`size`（上限 100）。成功 `200`，`data` 是 `TransferListResponse`。

### 4.12 `POST /v2/admin/transfers/{statementId}/retry` — 重試失敗撥款 🆕

**權限**：`admin:write`。限自己租戶的 `ADMIN`，或 `SUPER_ADMIN` 跨租戶。僅 `FAILED` 或 `SKIPPED_ONBOARDING_INCOMPLETE` 狀態的 transfer 可重試。重試方式是**整個重新走一次 `createTransferForStatement` 流程**：先刪除舊 transfer 記錄、把結算單狀態退回 `APPROVED`，再重新判斷前置條件（Stripe Connect 帳戶是否已就緒）並嘗試撥款——不是單純的「再送一次相同請求」。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 沒有既有 transfer 記錄，或既有記錄狀態不是 `FAILED`／`SKIPPED_ONBOARDING_INCOMPLETE` | 409 | `E-5014` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | StoreOwner（自己租戶） | Admin（自己租戶） | Super Admin | CFO |
|------|:----:|:----:|:----:|:----:|
| `GET`／`PUT submit`（§4.1～4.3） | ✅ | ✅（視為管理員操作需求外） | ✅ | ❌ |
| Admin 審核／駁回（§4.4～4.6） | ❌ | ✅（限自己租戶） | ✅（跨租戶） | ❌ |
| 手動補產（§4.7） | ❌ | ❌ | ✅（唯一角色） | ❌ |
| 逆轉候選／發起／確認（§4.8～4.10） | ❌ | ❌ | ✅ | ✅ |
| `GET /v2/transfers`（§4.11） | ✅ | ✅（限自己租戶） | ✅（跨租戶） | ❌ |
| 撥款重試（§4.12） | ❌ | ✅（限自己租戶） | ✅（跨租戶） | ❌ |

`CFO` 角色僅持有 `TENANT_READ`／`ORDER_READ`／`ADMIN_READ`／`SETTLEMENT_REVERSE` 四個權限（`RolePermissionMapping`），專門為 §4.8～4.10 的雙重授權設計，不能操作結算單審核、手動補產或撥款重試。

---

## 6. 錯誤碼 / Error Codes

M07 常見錯誤碼（完整對照表與預設訊息見 [API_Error_Codes.md](../API_Error_Codes.md)）：

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1006` | 404 | 找不到使用者（§4.9／4.10，理論上不會發生） |
| `E-1007` | 403 | 權限不足／非自己租戶／雙重授權角色相同 |
| `E-2000` | 404 | 找不到租戶（§4.7） |
| `E-5013` | 404 | 找不到結算單 |
| `E-5014` | 409 | 無效的結算單／transfer 狀態轉換 |
| `E-9008` | 400 | 數值超出範圍（§4.7：`periodEnd` 早於 `periodStart`） |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| 真實 Stripe Connect 撥款未對真實服務驗證 | `createTransferForStatement` 呼叫 `PaymentGatewayFactory.createTransfer`，與訂單/訂房付款同樣的「未對真實 Stripe 驗證」限制（見 [API_M06_Booking.md](../API_M06_Booking.md) §7）適用於此 |
| 逆轉不處理真實資金收回 | `CreditNote` 僅會計沖銷記錄，不呼叫任何 Stripe API 把已撥款的錢拿回來（Sprint 80/81 既定範圍外） |
| 本文件撰寫前自動化守門未覆蓋此模組 | `ApiRouteDocDriftTest` 的 `GUARDED` 清單目前只含 M03／M04／M05／M06，本文件完成後是否納入 M07 由下一輪決定（DEF-372） |
| `Transfer` 併發防護靠唯一索引而非樂觀鎖 | `createTransferForStatement` 的併發防護是 `saveAndFlush` 搶 `idx_transfers_settlement_statement_id` 唯一索引，非 `@Version` 樂觀鎖；重試時先刪記錄再重建，若與另一個真正併發的核准/重試交錯，理論上仍可能有極短暫的競態窗口，未專門寫併發測試驗證（一般業務頻率下核准與重試不會真的同時對同一張結算單發生） |
| 撥款失敗原因可能模糊 | `TransferService.createTransferForStatement` 的 catch block 只記錄 `BusinessException` 的 `message`，若底層是其他未分類例外則不會被捕捉為 `FAILED`（會往外拋，由 `GlobalExceptionHandler` 處理，結算單仍停在 `APPROVED` 等待人工介入） |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.2.1 結算與沖正（CFO 雙重授權）、Stripe Connect 分帳 |
| FRD | §6B 金流與結算（BR-M07-09～12 結算與撥款規則） |
| 相關規格 | [API_M05_Order.md](API_M05_Order.md) §7（訂單付款與退款）、[API_M06_Booking.md](../API_M06_Booking.md) §4.8～4.10／4.15（訂房付款與人工退款）、[API_Error_Codes.md](../API_Error_Codes.md) |
| 實作 | `SettlementController`、`TransferController`（`api/controller/settlement/`）；`SettlementGenerator`、`SettlementReviewer`、`SettlementReversalService`、`TransferService`、`SettlementCalculator`（`core/settlement/`） |
| 契約測試 | [TransferControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/TransferControllerE2ETest.java)、[M07SettlementIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M07SettlementIntegrationTest.java)、[SettlementReversalServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReversalServiceTest.java)、[SettlementReviewerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReviewerTest.java)、[SettlementGeneratorManualTriggerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementGeneratorManualTriggerTest.java)、[SettlementConcurrentClaimIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementConcurrentClaimIntegrationTest.java)（原子認領併發防護） |
| 相容性 | 既有 [TC_M07_Settlement.md](../../03_testing/TC_M07_Settlement.md) 引用的 `API-M07-S-001～006` 編號在本文件保持不變 |

---

**文件狀態**：v1.0（Sprint 262 新建，DEF-364）。
