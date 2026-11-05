# API 規格文件 - M07 結算與撥款 / API Specification - M07 Settlement & Transfer

> **文件編號**: API-M07
> **文件版本**: v1.7（Sprint 326：DEF-450／DEF-451——手動補產同一期間的並行請求改為冪等：輸家的交易整個回滾、在新交易重做，回 `200` 與贏家的那張結算單（修復前輸家回 `500 E-9900`）；週結算作業改為**逐租戶一個交易**，單一租戶失敗只回滾它自己（修復前外層單一交易使任一租戶的資料庫失敗讓全部租戶的結算單一起丟掉、作業以 `UnexpectedRollbackException` 結束，且『認領筆數不足』會留下半套結算單）；`generateWeeklyStatements`、`generateStatementManually` 不再標 `@Transactional`）；v1.6（Sprint 324：DEF-448——信用票據編號改成『開立日＋當日流水號』（`CN-{租戶 id 前 8 碼}-{yyyyMMdd}-{01、02…}`，使用者以互動選項選定），同一租戶同一天可以確認多張逆轉（修復前第二張回 `500`）；兩張不同結算單同時確認算出同一個流水號時，唯一約束仲裁、整個交易重做；`confirmReversal` 不再標 `@Transactional`）；v1.5（Sprint 323：DEF-447——確認逆轉改用條件式更新搶占，同時確認同一張逆轉時輸家得 `409 E-5014`（修復前：正式環境靠 `credit_notes` 的唯一約束偶然擋住、輸家 `500 E-9900`；沒有該約束時同一張逆轉會被沖銷兩次）；`transfer.reversed` webhook 改用條件式更新，與發起逆轉交錯時不會把剛進入 `REVERSAL_PENDING` 的結算單蓋回 `FAILED`、也不會清掉逆轉的四個欄位；重試撥款對舊 transfer 記錄改用條件式刪除搶占，同時重試時輸家得 `409 E-5014`（修復前 `500 E-9900`）；DEF-448 實測：同一租戶同一天的第二張逆轉確認回 `500`，**尚未修**，編號格式待使用者決定）；v1.4（Sprint 322：DEF-446——退款的直接扣款 `UPDATE` 帶狀態條件（`PENDING`／`PENDING_REVIEW`），批准／駁回搶先拿到鎖時，退款改走調整單（已作廢的結算單則不做事），不再直接扣在已批准或已作廢的結算單上；DEF-445——提交審核比照批准／駁回搶占＋重新載入，同時送出兩次提交時後到者得到 `409 E-5014`（與循序的第二次提交相同，修復前兩個都回 `200`），同時入帳的退款不再被提交的整列寫回蓋掉）；v1.3（Sprint 321：DEF-444——批准／駁回在搶占成功後重新載入結算單，整列寫回不再蓋掉同時入帳的退款，送給 Stripe 的撥款金額也是退款後的淨額；DEF-443——撥款觸發移到核准交易**提交之後**、在自己的新交易裡執行，撥款失敗不再連帶回滾核准，重試端點也接受『已核准但沒有 transfer』的結算單；⚠️ **核准回應的 `data.status` 一律是 `APPROVED`**，修復前撥款成功時是 `PAID`）；v1.2（Sprint 320：DEF-442——批准／駁回改用條件式更新搶占，同時送出時只有先到的成功、輸家 `409 E-5014` 且不留任何痕跡，並補上真實競態測試）；v1.1（Sprint 319：DEF-441——Transfer 佔位輸給另一個交易時回明確的 `E-5014`，並補上真實競態測試）；v1.0（Sprint 262，首次建立；Sprint 16 規劃時僅留一張 6 端點的草表於 `API_Index.md`，從未真正建立獨立文件，DEF-364）
> **建立日期**: 2026-10-13
> **最後更新**: 2026-11-05
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

1. **結算單狀態機**：`PENDING → PENDING_REVIEW → APPROVED → PAID`（正常路徑）；`PENDING_REVIEW → REJECTED`（駁回，釋放已認領的訂單/訂房，讓下一期重新結算）；`APPROVED → FAILED`（撥款失敗，可重試回到 `APPROVED` 再次嘗試）；`PAID → REVERSAL_PENDING → REVERSED`（雙重授權逆轉，需 `SUPER_ADMIN` 與 `CFO` 各一人、角色不同）。**狀態轉換一律用 `BusinessException(E_5014)` 擋非法轉換**，沒有獨立的狀態圖算式，以程式碼的 `if (status != X)` 判斷為準。**同一張結算單被同時批准／駁回時，只有一個請求會成功**（Sprint 320，DEF-442）：`approveStatement`／`rejectStatement`（與 Sprint 137 DEF-138 的 `initiateReversal`）在任何寫入之前，先用資料庫條件式更新 `updateStatusIfCurrent`（`UPDATE … WHERE id = ? AND status = 預期狀態`）搶占；搶輸的請求（影響 0 列）得到 `409 E-5014`，不留稽核、不建立 transfer、不釋放訂單。上面的 `if (status != X)` 只是循序情境的前置檢查，擋不住兩個請求同時通過它。
2. **每筆訂單/訂房恰好結算一次**：`SettlementGenerator` 用 `UPDATE ... WHERE settled_statement_id IS NULL` 原子認領當週已完成的訂單與訂房，認領不足即回滾整張結算單（避免漏單或重複結算）；駁回時反向釋放（`releaseOrdersOfStatement`／`releaseBookingsOfStatement`／`releaseAppliedTo`），讓下一期能重新認領。
3. **撥款與審核解耦**（Sprint 321，DEF-443 起名實相符）：`SettlementReviewer.approveStatement` 在**核准交易提交之後**（`afterCommit` 回呼）才呼叫 `TransferService.createTransferAfterApproval`——它在自己的 `REQUIRES_NEW` 新交易裡執行 `createTransferForStatement`——所以**任何撥款失敗都只記錄，絕不回滾「已核准」這個 Admin 決策**。修復前這句話不成立（Sprint 320、321 在兩個不同的觸發點實測）：撥款原本在核准的交易裡呼叫、用 try-catch 隔離，但 `createTransferForStatement` 是 `@Transactional`（加入呼叫端的交易），穿出它的交易代理的 `RuntimeException` 會讓核准的交易 rollback-only，核准連同稽核被回滾並回 `500 E-9900`。現在：已知的撥款失敗（Stripe 拒絕等，閘道包成 `BusinessException`）記為 transfer／結算單 `FAILED`（可重試）；非預期例外則撥款自己的寫入整個回滾（含 transfer 佔位，不留 `PENDING` 記錄擋住之後的重試），結算單停在 `APPROVED`、沒有 transfer 記錄，用 §4.12 補撥款。`Transfer` 記錄以 `settlement_statement_id` 唯一索引**原子佔位**防止併發重複撥款（佔位輸給另一個交易的那個請求在 `TransferService` 層以 `E-5014` 乾淨失敗，不撥款、不重查；DEF-441，Sprint 319。兩個批准在更早的狀態搶占就分出勝負，不會走到這裡；佔位的競爭者只剩撥款觸發與管理員重試）；賣家 Stripe Connect 帳戶未完成 onboarding 時狀態為 `SKIPPED_ONBOARDING_INCOMPLETE`（非失敗，可之後補建）。
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
| `creditNoteNumber` | `CN-{tenantId前8碼}-{開立日yyyyMMdd}-{當日流水號}`（流水號＝該租戶當日已開立張數＋1，至少兩位，例如 `CN-5946a51a-20261103-01`；Sprint 324 之前沒有流水號後綴〔`CN-{前8碼}-{yyyyMMdd}`〕，舊票據不遷移） |
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
| 狀態不是 `PENDING`（含循序的第二次提交） | 409 | `E-5014` |
| 兩次提交同時送出（例如連點兩下）：後到者 | 409 | `E-5014`（與上一列同一個結果；Sprint 322 之前兩個請求都回 `200`） |

**併發行為（Sprint 322，DEF-445）**：提交以條件式更新（`PENDING → PENDING_REVIEW`）搶占，搶占成功後重新載入結算單，所以回應的 `status`、`totalRefunds`、`netSettlementAmount` 是鎖住當下最新的已提交值——提交與退款同時發生時，退款的扣款不會被提交蓋掉，回應也不會顯示過期的金額（修復前實測：提交 `200` 但 `total_refunds` 仍 `0.00`、淨額仍 `900.00`）。

### 4.4 `GET /v2/admin/settlements/pending` — Admin 待審核列表

**權限**：`admin:read`。非 `SUPER_ADMIN` 僅能查自己租戶；`SUPER_ADMIN` 可用 `tenantId` 查詢參數指定租戶，未指定則跨租戶總覽（Sprint 81，DEF-040 修復）。查詢參數：`tenantId`（選填）、`page`、`size`。

### 4.5 `PUT /v2/admin/settlements/{statementId}/approve` — Admin 核准

**權限**：`admin:write`。非 `SUPER_ADMIN` 僅能核准自己租戶（Sprint 81，DEF-040 修復）。`PENDING_REVIEW → APPROVED`，並寫入稽核紀錄（`SETTLEMENT_APPROVED`）。**核准交易提交之後才嘗試觸發撥款**（`TransferService.createTransferAfterApproval`，見 §1.2 第 3 點）。**回應是核准決定當下的快照：`200`、`data.status = APPROVED`**；撥款結果（`PAID`／`FAILED`／略過／仍是 `APPROVED`）需另呼叫 §4.1/4.2 或 §4.11 查詢，撥款失敗（任何例外）只記錄 log、不影響這個回應。⚠️ **行為變更（Sprint 321，DEF-443）**：修復前撥款在核准的交易裡同步執行，撥款成功時回應的 `data.status` 是 `PAID`（Sprint 321 探針實測；已知撥款失敗時依同一機制應是 `FAILED`，未另外實測）；現在一律是 `APPROVED`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 狀態不是 `PENDING_REVIEW` | 409 | `E-5014` |
| 併發搶占輸了（另一個請求已先批准或駁回這張結算單，包含同一位管理員連點兩下；條件式更新影響 0 列，Sprint 320 DEF-442） | 409 | `E-5014` |

**同時送出的批准／駁回只有先到的成功**（Sprint 320，DEF-442）：輸家的交易整個回滾——沒有稽核紀錄、沒有 transfer、沒有釋放訂單／訂房／調整單，結算單停在贏家寫下的狀態。實測（真實 PostgreSQL，兩位 `SUPER_ADMIN` 同時送出，[SettlementReviewRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java)）的修復前行為：批准 vs 駁回兩邊都回 `200`、後寫者覆寫先寫者（結算單 `REJECTED` 但 transfer 已建立且訂單被釋放，或相反）；批准 vs 批准的後到者回 `500 E-9900`（見 §7）。

### 4.6 `PUT /v2/admin/settlements/{statementId}/reject` — Admin 駁回

**權限**：`admin:write`。非 `SUPER_ADMIN` 僅能駁回自己租戶。`PENDING_REVIEW → REJECTED`，查詢參數 `reason`（選填），寫入稽核紀錄（`SETTLEMENT_REJECTED`）。**駁回會釋放本期已認領的訂單、訂房與折入的調整單**（`releaseOrdersOfStatement`／`releaseBookingsOfStatement`／`releaseAppliedTo`），讓下一期重新結算——否則這些訂單/訂房會永遠掛在一張死掉的結算單上，調整單（賣家該被扣的退款）也隨之消失。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 狀態不是 `PENDING_REVIEW` | 409 | `E-5014` |
| 併發搶占輸了（另一個請求已先批准或駁回這張結算單；條件式更新影響 0 列，Sprint 320 DEF-442） | 409 | `E-5014` |

輸家不寫入駁回原因與審核人、不釋放任何訂單／訂房／調整單（釋放發生在搶占成功之後）。若輸家也「成功」，會在贏家批准（將撥款）的結算單上釋放訂單，下一期重複結算——這正是修復前批准先、駁回後的實測結果。

### 4.7 `POST /v2/admin/settlements/generate` — 手動補產結算單（DEF-287，Sprint 208）🆕

**權限**：`settlement:generate`（**僅 `SUPER_ADMIN` 持有**，`RolePermissionMapping` 未對任何其他角色個別授予）。用途：(1) 排程當週漏產/失敗時補救；(2) 上線前以 Stripe 測試模式走查撥款，不必等下週一排程。查詢參數：`tenantId`（必填）、`periodStart`（必填，`yyyy-MM-dd`）、`periodEnd`（選填，未指定則 `periodStart + 6` 天，比照週結算天數）。

**冪等**：與排程共用同一套「同租戶＋期間已有結算單就直接回傳既有的」檢查與「原子認領訂單/訂房」併發防護——手動觸發不會讓同一筆訂單被兩張結算單重複結算。

**同一期間的並行請求**（Sprint 326，DEF-450）：結算單號是決定性的（`STL-{租戶 id 前 8 碼}-{期間起日 yyyyMMdd}`），兩個並行請求都通過「是否已有結算單」的檢查後各自 INSERT，輸的那個撞 `(tenant_id, statement_number)` 唯一約束（Flyway `V35`）。輸家的交易整個回滾、在新交易裡重做（`UniqueConflictRetry`，最多 3 次），重做時冪等檢查看得到贏家的結算單，回 **`200` 與贏家的那一張**；資料庫只剩一張。修復前輸家回 `500 E-9900`。

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

**權限**：`settlement:reverse`（`SUPER_ADMIN` 或 `CFO`）。`REVERSAL_PENDING → REVERSED`，成功後產生 `CreditNote`（§3.3），編號 `CN-{租戶 id 前 8 碼}-{開立日}-{當日流水號}`；同一租戶同一天可以確認多張逆轉（Sprint 324，DEF-448；修復前第二張回 `500`）。**確認人角色必須與發起人角色不同**（例如發起人是 `SUPER_ADMIN`，確認人須是 `CFO`，反之亦然）——這是雙重授權機制的核心檢查，單一角色無法自己發起又自己確認。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 確認人角色不是 `SUPER_ADMIN`／`CFO` | 403 | `E-1007` |
| 狀態不是 `REVERSAL_PENDING`（含併發搶占失敗：條件式更新 `updateStatusIfCurrent(REVERSAL_PENDING → REVERSED)` 回 0 筆，Sprint 323 DEF-447；輸家不產生信用票據） | 409 | `E-5014` |
| **確認人角色與發起人角色相同**（雙重授權失敗） | 403 | `E-1007`（訊息：「Reversal confirmation must come from a different role than the initiator」） |
| 確認人使用者不存在 | 404 | `E-1006` |

> **逆轉與 Stripe webhook 的優先順序**：若 Stripe 主動送達 `transfer.reversed` webhook（`TransferService.handleTransferReversedWebhook`），只有結算單仍是 `PAID` 時才會被 webhook 改回 `FAILED`；若本端點已將其推進到 `REVERSAL_PENDING`／`REVERSED`，webhook **不會覆寫**已在正式雙重授權流程中處理或完成的決策，只記錄 info log。**Sprint 323（DEF-447）起這個保證在競態下也成立**：webhook 以條件式更新（`PAID → FAILED`，不載入結算單實體）取代原本的『讀到 `PAID` 才 `setStatus`＋`save`』。修復前與發起逆轉交錯時（webhook 讀到 `PAID` 之後、寫入之前，發起逆轉提交了），webhook 的整列寫回會把結算單蓋回 `FAILED`，連發起人、角色、時間、原因四個逆轉欄位也被清成 `null`（實測），發起人卻拿到 `200`；現在搶輸（0 筆）只記錄，`transfer` 仍記為 `REVERSED`（Stripe 的事實不因此改變）。

### 4.11 `GET /v2/transfers` — 撥款對帳記錄 🆕

**權限**：`order:read`。一般角色（含 `ADMIN`）僅能查自己租戶；`SUPER_ADMIN` 可加 `tenantId` 查詢參數查任意租戶。查詢參數：`tenantId`（選填，僅 `SUPER_ADMIN` 有效）、`page`、`size`（上限 100）。成功 `200`，`data` 是 `TransferListResponse`。

### 4.12 `POST /v2/admin/transfers/{statementId}/retry` — 重試失敗撥款 🆕

**權限**：`admin:write`。限自己租戶的 `ADMIN`，或 `SUPER_ADMIN` 跨租戶。僅 `FAILED` 或 `SKIPPED_ONBOARDING_INCOMPLETE` 狀態的 transfer 可重試。重試方式是**整個重新走一次 `createTransferForStatement` 流程**：先刪除舊 transfer 記錄（Sprint 323 DEF-447 起用**條件式刪除**當搶占：只有仍是 `FAILED`／`SKIPPED_ONBOARDING_INCOMPLETE` 的才刪得掉，輸家得 `409 E-5014`，不碰結算單、也不會走到 Stripe）、把結算單狀態退回 `APPROVED`，再重新判斷前置條件（Stripe Connect 帳戶是否已就緒）並嘗試撥款——不是單純的「再送一次相同請求」。**Sprint 321（DEF-443）起也接受『已核准（`APPROVED`）但完全沒有 transfer 記錄』的結算單**——核准之後的撥款觸發本身失敗（非預期例外，撥款交易整個回滾）就是這個狀態：沒有舊記錄可刪，直接觸發 `createTransferForStatement`（冪等；併發的重試靠 transfer 佔位的唯一索引只讓一個真的撥款）。其他狀態的結算單沒有 transfer 並不代表撥款失敗，由 `createTransferForStatement` 自己的『只有 `APPROVED` 可撥款』檢查擋下，同為 `409 E-5014`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 結算單不存在 | 404 | `E-5013` |
| 非 `SUPER_ADMIN` 且結算單不屬於自己租戶 | 403 | `E-1007` |
| 既有 transfer 記錄的狀態不是 `FAILED`／`SKIPPED_ONBOARDING_INCOMPLETE`（例如已 `COMPLETED`）；或沒有 transfer 記錄、結算單也不是 `APPROVED`；或舊記錄已被另一個併發的重試搶先刪除（條件式刪除回 0 筆；修復前輸家是 `500 E-9900`） | 409 | `E-5014` |

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
| `E-5014` | 409 | 無效的結算單／transfer 狀態轉換；批准／駁回／逆轉發起的併發搶占輸了（DEF-442／DEF-138）；或 transfer 佔位輸給另一個併發交易（DEF-441） |
| `E-9008` | 400 | 數值超出範圍（§4.7：`periodEnd` 早於 `periodStart`） |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| 真實 Stripe Connect 撥款未對真實服務驗證 | `createTransferForStatement` 呼叫 `PaymentGatewayFactory.createTransfer`，與訂單/訂房付款同樣的「未對真實 Stripe 驗證」限制（見 [API_M06_Booking.md](../API_M06_Booking.md) §7）適用於此 |
| 逆轉不處理真實資金收回 | `CreditNote` 僅會計沖銷記錄，不呼叫任何 Stripe API 把已撥款的錢拿回來（Sprint 80/81 既定範圍外） |
| 本文件撰寫前自動化守門未覆蓋此模組 | `ApiRouteDocDriftTest` 的 `GUARDED` 清單目前只含 M03／M04／M05／M06，本文件完成後是否納入 M07 由下一輪決定（DEF-372） |
| `Transfer` 併發防護靠唯一索引而非樂觀鎖 | `createTransferForStatement` 的併發防護是 `saveAndFlush` 搶 `idx_transfers_settlement_statement_id` 唯一索引，非 `@Version` 樂觀鎖；重試時先刪記錄再重建；Sprint 323（DEF-447）起兩個同時的重試靠『條件式刪除舊記錄』分出勝負（修復前實測輸家是 `500 E-9900`：實體層級的 `delete` 刪不到列，Hibernate 的列數檢查丟 `ObjectOptimisticLockingFailureException`；結算單、transfer 與閘道呼叫次數本來就是安全的）。**佔位本身的競態已有真實 PostgreSQL 測試**（DEF-441，Sprint 319，`UniqueConstraintRaceRealStackIntegrationTest.createTransfer_losingTheClaimRace_failsCleanlyWithE5014`）：輸家得到明確的 `E-5014`、只剩贏家那一筆 transfer（不會撥款兩次）。原本的 catch 想「回傳贏家的記錄」而在同一個交易裡重查，PostgreSQL 在唯一約束違反後交易已中止，重查必然失敗；刻意不讓輸家改成「成功」——`approveStatement` 在自己的交易裡先把結算單改成 `APPROVED`，輸家若成功並提交，會把贏家已推進到 `PAID` 的結算單覆寫回 `APPROVED`（遺失更新；Sprint 320 以突變驗證證實會發生：把批准的狀態搶占結果忽略時，後到的批准回 `200`、把 `PAID` 改回 `APPROVED`，transfer 仍是 `COMPLETED`），讓整個操作失敗並回滾才是安全的。Sprint 320 起兩個批准在更早的狀態搶占就分出勝負，不會走到這裡；Sprint 321（DEF-443）起撥款在核准提交之後、自己的交易裡執行，這個 `E-5014` 只會讓撥款自己失敗並記錄，不再影響核准 |
| 核准後觸發撥款的 try-catch 擋不住回滾（DEF-443，Sprint 320 實測、Sprint 321 在第二個觸發點實測並**已修**） | 修復前 `approveStatement` 用 `try { createTransferForStatement } catch (RuntimeException) { log }` 隔離撥款失敗，但 `createTransferForStatement` 是 `@Transactional`（加入呼叫端的交易）：任何 `RuntimeException` 穿出它的交易代理時，外層交易就被標成 rollback-only；`approveStatement` 吞掉例外後正常返回，提交時 Spring 丟 `UnexpectedRollbackException`，**整個核准（含稽核）被回滾，HTTP `500 E-9900`**。實測兩個獨立的觸發點：Sprint 320（transfer 佔位撞唯一索引的 `E-5014`）與 Sprint 321（閘道丟 `IllegalStateException`）。**修法**（Sprint 321）：撥款改在核准交易提交之後（`afterCommit`）、透過 `TransferService.createTransferAfterApproval`（`REQUIRES_NEW`）在自己的新交易裡執行，例外只記錄；非預期失敗後結算單停在 `APPROVED`、沒有 transfer 記錄，重試端點（§4.12）改為也接受這個狀態。**行為變更**：核准回應的 `data.status` 一律是 `APPROVED`（修復前撥款成功時是 `PAID`）。真實棧測試：[SettlementApprovalPayoutTriggerRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementApprovalPayoutTriggerRealStackIntegrationTest.java) |
| 批准／駁回的整列寫回會蓋掉同時入帳的退款扣款（DEF-444，Sprint 320 實測、Sprint 321 **已修批准／駁回**；`submitForReview` 與退款端見下一列） | 修復前 `SettlementStatement` 沒有 `@Version` 也沒有 `@DynamicUpdate`，批准／駁回在讀取結算單後以**整列** `UPDATE` 寫回，用讀取當下的 `total_refunds`／`net_settlement_amount` 蓋掉同時入帳的退款扣款（`SettlementAdjustmentService.handleOrderRefund` 對 `PENDING`／`PENDING_REVIEW` 的結算單以原子 `UPDATE` 直接扣款），transfer 也拿舊淨額撥款。實測（Sprint 321，真實 PostgreSQL，真實的退款服務當對手）：批准後 `total_refunds` 仍 `0.00`、淨額仍 `900.00`、transfer 金額 `900.00`；修復後，送給 Stripe 的是退款後的 `800.00`＝`80000` 分。**修法**（Sprint 321）：搶占（`updateStatusIfCurrent`）成功後、任何 setter 之前 `entityManager.refresh(statement)`——搶占成功＝這一列已被本交易鎖住（其他交易對它的 UPDATE 要等本交易結束），此時重新載入拿到的是鎖住當下最新的已提交值（比照 `PaymentStateService` 在條件式 UPDATE 後 `refresh` 的既有作法）。測試：[SettlementReviewRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java) |
| 提交審核的整列寫回與退款直接扣款的狀態條件（DEF-445、DEF-446，Sprint 321 實測、Sprint 322 **已修**；其他整列寫回路徑見下一列） | ① **`submitForReview`**（`PENDING → PENDING_REVIEW`）修復前同樣整列寫回、而且沒有搶占：退款在提交讀取之後提交，就被舊金額蓋掉（實測：提交 `200`、`total_refunds` 仍 `0.00`、淨額仍 `900.00`；兩次同時提交都回 `200`）。**修法**（Sprint 322）：比照批准／駁回——`updateStatusIfCurrent(id, PENDING, PENDING_REVIEW)` 搶占（輸家 `409 E-5014`），搶占成功後 `entityManager.refresh`；狀態已由搶占的 UPDATE 寫完，之後不再有任何 `setStatus`／`save`（碰 setter 會讓髒檢查整列寫回）。② **退款的直接扣款**修復前沒有狀態條件（`applyRefundDeduction` 的 `WHERE id = :id`）：批准先拿到鎖、退款（先前讀到 `PENDING_REVIEW`）後到時，退款直接扣在已批准的結算單上（實測：`total_refunds = 100.00`、淨額 `800.00`、調整單 0 筆；撥款金額是 `900.00` 還是 `800.00` 取決於交錯），而設計上 `APPROVED` 以後的退款應走調整單、下一期折入；駁回先拿到鎖時，退款被扣在已作廢的結算單上。**修法**（Sprint 322）：UPDATE 加 `AND status IN ('PENDING','PENDING_REVIEW')`，由資料庫在 UPDATE 生效的那一刻判斷（被鎖擋住的 UPDATE 在鎖釋放後對最新已提交的版本重新評估 `WHERE`，PostgreSQL READ COMMITTED）；影響 0 列時 `SettlementAdjustmentService.applyDirectDeduction` **重新讀取目前狀態**（`findStatusById` 純量查詢，不受持久化上下文快取影響）再依同一張狀態表處理——`APPROVED`／`PAID`／`FAILED` 建調整單（訂單版或訂房版）、`REJECTED`／`REVERSAL_PENDING`／`REVERSED` 不做事（訂單已釋放，重新結算時由 `Payment.refundedAmount` 帶入；這時再建調整單會重複扣）、結算單不存在記 `warn`。這條路徑**絕不丟例外**：`handleOrderRefund`／`handleBookingRefund` 加入退款的交易，例外穿出會把它標成 rollback-only。結果：批准先拿到鎖時，退款走調整單、結算單的 `total_refunds` 與淨額不再變動，撥款用的就是批准當下的淨額，退款於下一期折入。其他同樣整列寫回結算單的路徑（`TransferService`、逆轉、`handleTransferReversedWebhook`）見下一列（DEF-447，Sprint 323 已量測並修）。測試：[SettlementReviewRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java)、[SettlementAdjustmentServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementAdjustmentServiceTest.java)、[SettlementReviewerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReviewerTest.java) |
| 結算單核准之後的狀態對狀態競態（DEF-447，Sprint 323 實測並**已修**） | 先盤點 `backend/src/main/java` 對 `settlement_statements` 的全部寫入點（10 個方法、14 個呼叫點），再對『同一個狀態上真的有並行寫入者』的路徑用真實 PostgreSQL 量測（對手交易持鎖、`pg_stat_activity` 確認鎖序）。**修復前實測**（未修改的程式碼，預測逐格相符）：① `transfer.reversed` webhook 與發起逆轉交錯時，webhook 的整列寫回把結算單蓋回 `FAILED`，逆轉的發起人、角色、時間、原因全被清成 `null`，發起人卻拿到 `200`；② 兩位 CFO 同時確認同一張逆轉：有 `credit_notes` 唯一約束時輸家 `500 E-9900`（`DataIntegrityViolationException`），沒有該約束時**兩個都成功、同一張逆轉被沖銷兩次**（兩張編號相同的信用票據）；③ 兩位管理員同時重試同一筆失敗的撥款：輸家 `500 E-9900`（`ObjectOptimisticLockingFailureException`），結算單、transfer 與閘道呼叫次數（1 次）本來就安全。**修法**（Sprint 323）：webhook 與確認逆轉都改成條件式更新搶占（`PAID → FAILED`、`REVERSAL_PENDING → REVERSED`），搶輸得 `409 E-5014`／只記錄；確認逆轉搶占成功後**不再** `setStatus`／`save`，回應的狀態補在 DTO 上（沒有 `refresh`：`REVERSAL_PENDING` 的內容是凍結的，沒有可達的並行寫入者）；重試對舊 transfer 記錄改用條件式刪除搶占（`TransferRepository.deleteByIdAndStatusIn`），搶占在寫結算單之前。**沒有改動**：`initiateReversal` 搶占後沒有 `refresh`、`createTransferForStatement` 的整列寫回——依寫入者盤點，`PAID`／`APPROVED` 狀態上除了 webhook 之外沒有可達的並行寫入者，**單獨存在時沒有可量測的失敗**（這是讀碼整理，沒有另外執行）。⚠️ **可見行為變更**：同時確認／同時重試的輸家從 `500` 變成 `409 E-5014`；webhook 與確認逆轉不再推進結算單的 `updated_at`（生產碼沒有任何讀取者，回應 DTO 與前端也不含它）。測試：[SettlementPostApprovalRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementPostApprovalRaceRealStackIntegrationTest.java)、[TransferServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/TransferServiceTest.java)、[SettlementReversalServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReversalServiceTest.java) |
| 信用票據編號（DEF-448，Sprint 323 實測、Sprint 324 **已修**） | 修復前：`SettlementReversalService.generateCreditNoteNumber` 的編號是 `CN-{租戶 id 前 8 碼}-{今天 yyyyMMdd}`（每個租戶每天一個），而 Flyway `V36` 的 `credit_notes` 有 `UNIQUE (tenant_id, credit_note_number)`（`VARCHAR(30)`）。**實測**（Sprint 323，一次性探針，真實 PostgreSQL，與 `V36` 同形的唯一索引）：同一租戶、同一天兩張 `PAID` 結算單各自『發起→確認』——第一張 `200`、編號 `CN-33fc26b4-20261102`；第二張的確認 `500 E-9900`，回滾後維持 `REVERSAL_PENDING`，要等隔天才可能確認成功（確定性缺陷，不是競態）。**修法**（Sprint 324，編號格式由使用者以互動選項選定）：編號改成 `CN-{租戶 id 前 8 碼}-{開立日 yyyyMMdd}-{當日流水號}`（流水號＝該租戶當日已開立張數＋1，至少兩位，例如 `CN-5946a51a-20261103-01`，`VARCHAR(30)` 放得進）；做法同 `SupportTicketService` 的工單單號——計數後 `INSERT`，撞 `V36` 唯一約束（兩張不同結算單同時確認算出同一個號碼）時 `UniqueConflictRetry` 把整個交易（含狀態搶占）丟掉、在新交易重做，最多 3 次。已開立的舊格式編號不遷移，也不會被算進當日流水號。**限制**：開立日沿用原本的 `LocalDate.now()`（JVM 預設時區）；同一租戶同一天連續輸給 3 個不同對手才會把撞號往外拋（`500`），實務上不會發生。測試：[SettlementPostApprovalRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementPostApprovalRaceRealStackIntegrationTest.java)（`confirmTwoStatementsOfTheSameTenantOnTheSameDay_getConsecutiveNumbers`、`confirmLosesTheDailyNumberToAnotherStatement_retriesWithTheNextNumber`、`dailyNumberRestartsAtOneEachDay`）、[SettlementReversalServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReversalServiceTest.java) |
| 週結算作業的交易邊界（DEF-451，Sprint 326 實測並**已修**） | 修復前 `generateWeeklyStatements` 是一個 `@Transactional`（排程註冊的目標是 CGLIB 代理，**外層交易確實存在**），迴圈裡的 `generateStatementForTenant` 是同類別的自我呼叫、自己的 `@Transactional` 不生效，所以所有租戶共用一個交易。**實測**（Sprint 326，一次性探針，真實 PostgreSQL；輸出原文在 [SPRINT_326_PLAN.md](../../04_planning/SPRINT_326_PLAN.md) 附錄 A）：① 某個租戶的資料庫層級失敗（唯一約束）讓**其前後所有租戶**的結算單一起丟掉、訂單全未認領，作業以 `UnexpectedRollbackException` 結束；② 『認領筆數不足』（1／2）的 `IllegalStateException` 被迴圈吞掉後外層交易照常提交，結算單留在資料庫（`total_orders=2`、`adjustment_amount=-30`）但只有 1 張訂單掛在它底下、調整單仍 `PENDING`，下一次作業的冪等檢查會直接回傳這張半套結算單、不會修它。**修法**：外層不開交易，每個租戶各自一個交易（`UniqueConflictRetry`）。**限制**：失敗的租戶要等下週一排程（或 `SUPER_ADMIN` 用 §4.7 手動補產）才有結算單；兩種觸發條件在真實環境的發生頻率沒有數據 |
| 撥款失敗原因可能模糊 | `TransferService.createTransferForStatement` 的 catch block 只接 `BusinessException`（已知失敗記為 `FAILED`，原因是錯誤碼的預設說明加閘道說明）；其他未分類例外不會被記成 `FAILED`，而是往外拋。Sprint 321（DEF-443）起，經核准觸發時這個例外被記錄（`ERROR` 日誌 `Transfer trigger failed after settlement approval`）、撥款的新交易整個回滾，結算單停在 `APPROVED`、沒有 transfer 記錄，用 §4.12 補撥款；經重試端點觸發時例外直接回給呼叫的管理員（由 `GlobalExceptionHandler` 處理）。 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.2.1 結算與沖正（CFO 雙重授權）、Stripe Connect 分帳 |
| FRD | §6B 金流與結算（BR-M07-09～12 結算與撥款規則） |
| 相關規格 | [API_M05_Order.md](API_M05_Order.md) §7（訂單付款與退款）、[API_M06_Booking.md](../API_M06_Booking.md) §4.8～4.10／4.15（訂房付款與人工退款）、[API_Error_Codes.md](../API_Error_Codes.md) |
| 實作 | `SettlementController`、`TransferController`（`api/controller/settlement/`）；`SettlementGenerator`、`SettlementReviewer`、`SettlementReversalService`、`TransferService`、`SettlementCalculator`（`core/settlement/`） |
| 契約測試 | [TransferControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/TransferControllerE2ETest.java)、[M07SettlementIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M07SettlementIntegrationTest.java)、[SettlementReversalServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReversalServiceTest.java)、[SettlementReviewerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementReviewerTest.java)、[SettlementGeneratorManualTriggerTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementGeneratorManualTriggerTest.java)、[SettlementConcurrentClaimIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementConcurrentClaimIntegrationTest.java)（原子認領併發防護）、[SettlementReviewRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java)（批准／駁回併發，真實 PostgreSQL，Sprint 320；Sprint 321 擴充：同時入帳的退款不被蓋掉、送給 Stripe 的金額）、[SettlementApprovalPayoutTriggerRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementApprovalPayoutTriggerRealStackIntegrationTest.java)（核准後撥款觸發失敗不回滾核准、非預期失敗後可補撥款，Sprint 321）、[SettlementPostApprovalRaceRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementPostApprovalRaceRealStackIntegrationTest.java)（核准之後的撥款重試、webhook、逆轉確認的併發，DEF-447） |
| 相容性 | 既有 [TC_M07_Settlement.md](../../03_testing/TC_M07_Settlement.md) 引用的 `API-M07-S-001～006` 編號在本文件保持不變 |

---

**文件狀態**：v1.6（Sprint 324，DEF-448）；v1.5（Sprint 323，DEF-447／DEF-448）；v1.4（Sprint 322，DEF-445／DEF-446）；v1.3（Sprint 321，DEF-443／DEF-444）；v1.2（Sprint 320，DEF-442）；v1.1（Sprint 319，DEF-441）；v1.0（Sprint 262 新建，DEF-364）。
