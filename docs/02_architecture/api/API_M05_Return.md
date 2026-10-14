# API 規格文件 - M05 退貨申請 / API Specification - M05 Return Request

> **文件編號**: API-M05-RET
> **文件版本**: v1.0（Sprint 265，首次建立；Sprint 118 功能上線至今從未有獨立規格文件，DEF-366）
> **建立日期**: 2026-10-14
> **最後更新**: 2026-10-14
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`ReturnRequestController`（`api/controller/returns/`）、`ReturnRequestService`（`core/returns/`）、`ReturnRequest`／`ReturnRequestItem`（`domain/model/returns/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[ReturnRequestServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/returns/ReturnRequestServiceTest.java)、[M05ReturnRequestIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M05ReturnRequestIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **模組歸屬說明（重要）**：PRD §8.2.12/§8.2.13 的 `return_requests`／`return_request_items` 資料表定義、FRD §6.2 功能總覽表的「退貨退款」一行、既有測試命名（`M05ReturnRequestIntegrationTest`）三處皆指向 M05；但既有 [API_M05_Order.md](API_M05_Order.md) 檔頭「範圍外」卻寫著「退貨（**M11**）」——M11 在 PRD 實際是物流追蹤模組，與退貨申請的業務性質不符，判定為該行的既有誤記（可能是功能尚未實作前的推測性占位，從未更新）。本文件依前三處證據歸入 **M05**，並已同步更正 API_M05_Order.md 的錯誤標註（見本文件 §7）

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M05（退貨申請子功能，與 [API_M05_Order.md](API_M05_Order.md) 同模組不同文件） |
| **模組名稱** | 退貨申請 / Return Request |
| **描述** | 買家針對已送達/已完成的訂單品項申請退貨，店家審核（核准/拒絕）後買家寄回，店家確認收貨並回補庫存 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/returns/...`、`/api/v2/dashboard/returns/...` |
| **多租戶隔離** | `ReturnRequest.tenantId` 取自原訂單所屬店鋪。買家只能讀寫自己的退貨單；店家僅能看自己店鋪的退貨單，且**必須是真正的店鋪租戶**（`TenantContext.isStoreTenant`）——沒有店鋪的一般消費者被視為「系統租戶佔位值」，不會被誤判為與其他買家同租戶（Sprint 234 修法，`getTenantReturnRequests`／`getReturnRequest`／`loadForTenant` 三處皆檢查）；管理員不受限 |
| **範圍外** | 物流追蹤（M11）、結算/撥款（M07，退貨本身不觸發自動退款，退款是否發生由人工或既有訂單退款流程另行處理，本模組只負責退貨單生命週期與庫存回補） |

### 1.2 設計重點

1. **PRD 對退貨沒有 API 規格章節**：PRD §9.6「M05 API 規格概要」只列 5 個訂單端點，完全沒有退貨端點；FRD §6 也只有功能總覽表一行「退貨退款｜退貨與退款處理｜P1｜Phase 2」，沒有任何 User Story／AC／API 規格。本文件的端點清單、狀態機、授權規則**全部是從程式碼實際行為逆向整理**，不是對照既有規格撰寫（`createReturnRequest` 的訂單狀態檢查，程式碼註解自己也承認「PRD 對退貨沒有規格，此處為實作當時的明示假設」）。
2. **狀態機**（詳見 §3.3）：`REQUESTED → APPROVED → RECEIVED`（唯一會動庫存的一步）；`REQUESTED → REJECTED`；`REQUESTED`／`APPROVED → CANCELLED`（買家可撤回）。`REJECTED`／`RECEIVED`／`CANCELLED` 為終態。
3. **併發防護全部採條件式 UPDATE（CAS）**，不是樂觀鎖版本號：`cancelIfStatusIn`（DEF-131）、`reviewIfStatus`（DEF-130/133，approve/reject 共用）、`updateStatusIfCurrent`（DEF-136，收貨用）皆回傳受影響列數，0 代表「狀態已被別人搶先改掉」，一律轉譯為 `E-5017`。建立退貨單時用訂單悲觀鎖（`findByIdForUpdate`）序列化「讀各品項已申請退貨總量 → 比對可退量 → 建立新單」，避免同一品項被併發重複申請超額（DEF-132）。
4. **先查擁有權、才查狀態**（`loadForTenant` 的順序，承 DEF-036 教訓）：避免讓未授權的呼叫者從錯誤訊息的差異中推斷退貨單是否存在、目前是什麼狀態。
5. **收貨才動庫存，且分「可售／不可售」兩筆台帳**：`receiveReturn` 核准後，對每個有 `skuId` 的品項呼叫 `ProductInventoryService.applyReturnReceipt`——先把「可售+不可售」合計數量全部加回總量並寫一筆 `RETURN` 台帳，若 `unsellableQty > 0` 再扣掉不可售部分並寫一筆 `SCRAP` 台帳；淨效果是只有可售數量真正回補到可販售庫存，但異動歷史兩筆都看得到。
6. **取消用的是 `return:create` 權限，不是獨立的 `return:cancel`**——`@PreAuthorize` 設計上把「建立」與「撤回」視為買家對自己退貨單的同一類操作權限。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M05-RET-001 | 買家針對已送達/已完成的訂單品項申請退貨 | `POST /v2/returns` | ✅ 已實作 |
| US-M05-RET-002 | 買家查詢自己的退貨單列表與詳情、撤回申請 | `GET /v2/returns`、`GET /v2/returns/{returnId}`、`POST /v2/returns/{returnId}/cancel` | ✅ 已實作 |
| US-M05-RET-003 | 店家查詢自己店鋪的退貨單、核准或拒絕 | `GET /v2/dashboard/returns`、`POST .../approve`、`POST .../reject` | ✅ 已實作 |
| US-M05-RET-004 | 店家確認收貨，依實際可售/不可售數量回補庫存 | `POST /v2/dashboard/returns/{returnId}/receive` | ✅ 已實作 |

FRD 無對應章節（§6.2 僅一行功能總覽，無 US/AC），上表為本文件依實作新增。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M05-RET-001 | POST | `/v2/returns` | 建立退貨申請 | `return:create` |
| API-M05-RET-002 | GET | `/v2/returns` | 買家自己的退貨單列表 | `return:read` |
| API-M05-RET-003 | GET | `/v2/returns/{returnId}` | 退貨單詳情 | `return:read` |
| API-M05-RET-004 | POST | `/v2/returns/{returnId}/cancel` | 撤回退貨申請 | `return:create` |
| API-M05-RET-005 | GET | `/v2/dashboard/returns` | 店家自己店鋪的退貨單列表 | `return:read` |
| API-M05-RET-006 | POST | `/v2/dashboard/returns/{returnId}/approve` | 核准退貨 | `return:review` |
| API-M05-RET-007 | POST | `/v2/dashboard/returns/{returnId}/reject` | 拒絕退貨 | `return:review` |
| API-M05-RET-008 | POST | `/v2/dashboard/returns/{returnId}/receive` | 確認收貨、回補庫存 | `return:review` |

共 8 個端點，分買家層（`/v2/returns`）與店家層（`/v2/dashboard/returns`）兩層，**沒有平台/admin 專屬路由**（管理員透過既有角色判斷跨租戶放行，不需另開路由）。

---

## 3. 資料模型 / Data Model

### 3.1 `ReturnRequest`（表 `return_requests`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | 取自原訂單所屬店鋪，not null |
| `returnNumber` | String | 唯一單號，格式 `RMA-yyyyMMdd-{8 碼大寫亂數}` |
| `orderId` | UUID | 原訂單 ID，not null |
| `customerId` | UUID | 申請人，not null |
| `status` | enum `ReturnStatus` | 見 §3.3，預設 `REQUESTED` |
| `reason` | String | 買家申請原因，選填，上限 1000 字 |
| `rejectionReason` | String | 店家拒絕原因，選填，上限 1000 字 |
| `reviewedBy` | UUID | 核准/拒絕者，審核後才有值 |
| `reviewedAt` | Instant | 審核時間 |
| `receivedBy` | UUID | 確認收貨者，收貨後才有值 |
| `receivedAt` | Instant | 收貨時間 |
| `createdAt` / `updatedAt` | Instant | 由 `@PrePersist`/`@PreUpdate` 寫入，非資料庫端唯讀欄位 |
| `items` | `List<ReturnRequestItem>` | 一對多，`cascade=ALL, orphanRemoval=true` |

**⚠️ 已知的回應欄位落差**：`reviewedBy`、`receivedBy`、`updatedAt` 三個 Entity 欄位存在，但 `ReturnDto.Response`（§4 各端點回應）從未映射這三者，呼叫方無法得知「誰」審核/收貨、也看不到更新時間。

### 3.2 `ReturnRequestItem`（表 `return_request_items`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `returnRequest` | `@ManyToOne` | 指回父實體，`return_request_id` not null |
| `orderItemId` | UUID | 對應原訂單品項，not null |
| `skuId` | UUID | Entity 宣告 `nullable = false`，但 Service 建立時若原訂單品項的 SKU 已被刪除會代入 `null`（見 §7 已知限制） |
| `requestedQty` | Integer | 申請退貨數量，not null |
| `sellableQty` / `unsellableQty` | Integer | 收貨確認後才填值，之前為 `null`；`receivedQty()` 衍生方法 = 兩者相加（null 視為 0） |
| `createdAt` | Instant | |

無 `insertable=false`／`updatable=false` 的唯讀影子欄位（與本專案其他實體常見的 shadow-field 陷阱不同，此處所有欄位皆由應用程式直接讀寫）。

### 3.3 狀態機 / State Machine

```
REQUESTED ──approve──→ APPROVED ──receive──→ RECEIVED（🔴唯一會動庫存的一步）
    │                      │
    ├──reject──→ REJECTED  │
    └──cancel──→ CANCELLED ←──cancel──┘
```

- `REJECTED`／`RECEIVED`／`CANCELLED` 為終態，無法再轉換。
- `REJECTED`／`CANCELLED` 不佔用「可退額度」：同一訂單品項的已申請退貨量計算會排除這兩種終態的退貨單，買家可針對同品項重新申請。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/returns` — 建立退貨申請

**權限**：`return:create`，且訂單必須是呼叫者本人的（非管理員時）。訂單狀態必須是 `DELIVERED` 或 `COMPLETED`（此條件為程式碼依實作當時的假設設定，PRD 無對應規格）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `orderId` | UUID | 必填 | |
| `reason` | String | 選填 | 上限 1000 字 |
| `items` | `List<Item>` | 必填，至少 1 筆 | |
| `items[].orderItemId` | UUID | 必填 | |
| `items[].quantity` | Integer | 必填，≥1 | 申請退貨數量 |

**回應**：`ReturnDto.Response`（見 §3.1，`id`／`returnNumber`／`orderId`／`customerId`／`tenantId`／`status`／`reason`／`rejectionReason`／`reviewedAt`／`receivedAt`／`createdAt`／`items`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不存在 | 404 | `E-5000` |
| 訂單不屬於呼叫者本人 | 403 | `E-1007` |
| 訂單狀態不是 `DELIVERED`/`COMPLETED` | 400 | `E-5019` |
| 品項不屬於此訂單 | 404 | `E-5003` |
| 申請數量超過可退數量（含先前未終結的退貨單佔用額度） | 400 | `E-5018` |

### 4.2 `GET /v2/returns` — 買家自己的退貨單列表

**權限**：`return:read`。只回傳呼叫者自己的退貨單，分頁參數 `page`（預設 0）、`size`（預設 20，上限 100）。

### 4.3 `GET /v2/returns/{returnId}` — 退貨單詳情

**權限**：`return:read`，且三種情況任一才放行：管理員；本人；真正的店鋪租戶且與此退貨單同租戶。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 退貨單不存在 | 404 | `E-5016` |
| 非本人、非同租戶店家、非管理員 | 403 | `E-1007` |

### 4.4 `POST /v2/returns/{returnId}/cancel` — 撤回退貨申請

**權限**：`return:create`（與建立共用權限），且限本人（非管理員時）。只能在 `REQUESTED`／`APPROVED` 狀態撤回。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 退貨單不存在 | 404 | `E-5016` |
| 非本人 | 403 | `E-1007` |
| 狀態不允許撤回（已是終態，或併發搶輸） | 400 | `E-5017` |

### 4.5 `GET /v2/dashboard/returns` — 店家自己店鋪的退貨單列表

**權限**：`return:read`，且限真正的店鋪租戶。非管理員且非真正店鋪租戶（系統租戶佔位值）時**直接回空頁**，不拋例外（Sprint 234 修法，與例外拋出相比不洩漏「是否存在其他租戶的退貨單」）。

### 4.6 `POST /v2/dashboard/returns/{returnId}/approve` — 核准退貨

**權限**：`return:review`，且限真正的店鋪租戶（與此退貨單同租戶）或管理員。只能從 `REQUESTED` 轉為 `APPROVED`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 退貨單不存在 | 404 | `E-5016` |
| 非同租戶店家、非管理員 | 403 | `E-1007` |
| 狀態不是 `REQUESTED`（含併發搶輸） | 400 | `E-5017` |

### 4.7 `POST /v2/dashboard/returns/{returnId}/reject` — 拒絕退貨

**權限**：同 §4.6。只能從 `REQUESTED` 轉為 `REJECTED`。請求 body 可整個省略（`required = false`），僅 `rejectionReason`（選填，上限 1000 字）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 同 §4.6 | | |

### 4.8 `POST /v2/dashboard/returns/{returnId}/receive` — 確認收貨、回補庫存

**權限**：同 §4.6。只能從 `APPROVED` 轉為 `RECEIVED`。**退貨流程中唯一會異動庫存的端點**（見 §1.2 第 5 點）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `items` | `List<Item>` | 必填，至少 1 筆 | |
| `items[].itemId` | UUID | 必填 | 對應 `ReturnRequestItem.id`（**非** `orderItemId`） |
| `items[].sellableQty` | Integer | 必填，≥0 | |
| `items[].unsellableQty` | Integer | 必填，≥0 | |

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 退貨單不存在 | 404 | `E-5016` |
| 非同租戶店家、非管理員 | 403 | `E-1007` |
| 品項不屬於此退貨單 | 404 | `E-5003` |
| 收到數量超過申請數量 | 400 | `E-5018` |
| 狀態不是 `APPROVED`（含併發搶輸） | 400 | `E-5017` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 買家本人 | 同租戶店家 | ADMIN／SUPER_ADMIN |
|------|:----:|:----:|:----:|
| 建立／撤回（§4.1、4.4） | ✅ | ❌ | ✅ |
| 查詢自己列表（§4.2） | ✅ | — | — |
| 查詢詳情（§4.3） | ✅（本人） | ✅ | ✅ |
| 店家列表（§4.5） | ❌ | ✅ | ✅（跨租戶） |
| 核准／拒絕／收貨（§4.6～4.8） | ❌ | ✅ | ✅（跨租戶） |

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-5016` | 404 | 找不到退貨單 |
| `E-5017` | 400 | 退貨單狀態不允許此操作（含審核/收貨/撤回的併發搶輸） |
| `E-5018` | 400 | 申請或收貨數量超過可退/可收數量 |
| `E-5019` | 400 | 訂單狀態不允許申請退貨 |

另使用以下跨模組共用錯誤碼：`E-1007`（權限/擁有權不足）、`E-5000`（找不到訂單）、`E-5003`（訂單/退貨品項不屬於此訂單/退貨單）。

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| 回應缺少審核/收貨的操作者與更新時間 | `ReturnDto.Response` 未映射 Entity 既有的 `reviewedBy`／`receivedBy`／`updatedAt` 三個欄位（見 §3.1） |
| `ReturnRequestItem.skuId` 的 `nullable=false` 與實際可能寫入 `null` 的落差 | Entity 宣告為 not null，但 `ReturnRequestService` 建立退貨單時若原訂單品項的 SKU 已被刪除，會代入 `null`——理論上應在資料庫 NOT NULL 約束觸發失敗，但目前無測試驗證此邊界情境，建議後續確認 SKU 被刪除後申請退貨的真實行為 |
| PRD／FRD 對退貨完全沒有 API 規格或 AC | 本文件的端點清單、狀態機、數量校驗規則全部是從程式碼實際行為逆向整理，業務規則（如「只有 DELIVERED/COMPLETED 可退」）是實作當時的假設，未見任何需求文件明文佐證 |
| 模組歸屬文件間不一致（已部分更正） | 見文件頂部「模組歸屬說明」；[API_M05_Order.md](API_M05_Order.md) 原「範圍外：退貨（M11）」已於本輪更正為指向本文件 |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `ReturnRequestController` 至 `GUARDED` 清單，從此有常態機械防漂移 |
| Controller／`@PreAuthorize` 層完全無測試覆蓋 | 既有測試（`ReturnRequestServiceTest`、`M05ReturnRequestIntegrationTest`）皆直接呼叫 Service，不經過 Spring Security／HTTP 層，本文件列出的權限字串未被任何測試驗證過會正確生效或正確 403 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §8.2.12／§8.2.13 資料表定義（`return_requests`／`return_request_items`） |
| FRD | §6.2 功能總覽表「退貨退款」一行（無專章、無 US/AC） |
| 相關規格 | [API_Error_Codes.md](../API_Error_Codes.md)、[API_M05_Order.md](API_M05_Order.md) |
| 實作 | `ReturnRequestController`（`api/controller/returns/`）；`ReturnRequestService`（`core/returns/`）；`ReturnRequest`、`ReturnRequestItem`（`domain/model/returns/`） |
| 契約測試 | [ReturnRequestServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/returns/ReturnRequestServiceTest.java)、[M05ReturnRequestIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M05ReturnRequestIntegrationTest.java) |

---

**文件狀態**：v1.0（Sprint 265 新建，DEF-366）。
