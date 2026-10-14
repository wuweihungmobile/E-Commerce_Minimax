# API 規格文件 - M18 客服工單 / API Specification - M18 Support Ticket

> **文件編號**: API-M18-SUP
> **文件版本**: v1.0（Sprint 265，首次建立；Sprint 91 功能上線至今 `docs/02_architecture/` 下從未有獨立規格文件，DEF-367）
> **建立日期**: 2026-10-14
> **最後更新**: 2026-10-14
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`SupportTicketController`（`api/controller/support/`）、`SupportTicketService`／`SupportMessageService`（`core/support/`）、`SupportTicket`／`SupportMessage`（`domain/model/support/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[SupportTicketServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/support/SupportTicketServiceTest.java)、[SupportMessageServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/support/SupportMessageServiceTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **本文件撰寫範圍說明**：PRD §6.10 M18「知識管理系統」是較大的模組，除客服工單外還含知識庫文章等其他功能（`knowledge_articles` 等表）。本文件**只涵蓋客服工單子功能**（`SupportTicketController`／`SupportMessageService` 涉及的端點），不涵蓋 M18 其他子功能
> **既有規格文件說明**：`docs/01_requirements/M18_Knowledge_Management_SPEC.md` §5 已有一份相當完整的客服系統規格（資料模型、RBAC 矩陣、9 條 API），但（1）放在 `01_requirements/` 而非本目錄 `02_architecture/api/`，未納入 `ApiRouteDocDriftTest` 機械防漂移；（2）其 API 表漏列 2 條實際存在的端點（店家層與平台層的工單詳情查詢）。本文件依實作補齊並取代其 API 規格部分的權威性，資料模型定義兩者一致，不重複矛盾

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M18（客服工單子功能） |
| **模組名稱** | 客服工單 / Support Ticket |
| **描述** | 買家提交客服工單（可選關聯訂單），店家／平台客服回覆與處理；三層路由：買家、店家、平台 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/support/tickets/...`、`/api/v2/dashboard/support/tickets/...`、`/api/v2/admin/support/tickets/...` |
| **多租戶隔離** | 工單可為「平台工單」（`tenantId=null`，未關聯任何店鋪）或「店鋪工單」（關聯下單的店鋪）。店家只能看自己店鋪的工單，且**必須是真正的店鋪租戶**（`TenantContext.isStoreTenant`，Sprint 234 系列修法）；`SUPER_ADMIN` 不受限；平台層端點（`/admin/...`）由 `@PreAuthorize` 完全鎖死存取邊界，Service 層不再篩租戶 |
| **範圍外** | M18 其他子功能（知識庫文章等，見文件頂部範圍說明） |

### 1.2 設計重點

1. **三層路由**：買家層 `/v2/support/tickets`（建立、查詢自己的、留言）；店家層 `/v2/dashboard/support/tickets`（查詢自己店鋪的、更新狀態、留言）；平台層 `/v2/admin/support/tickets`（查詢全部、指派）。
2. **刻意用 404 而非 403 隱藏工單存在性**：非本人／非同租戶店家查詢他人工單時，一律回 `E-8008`（404 找不到），**不是** `E-8009`（403 無權限）——程式碼註解明確寫「不洩漏工單是否存在」，與多數模組「查無權限才 403」的慣例不同。因此 `E-8009` 目前是**定義了但從未被任何程式碼路徑拋出的死碼**（已用 `grep` 確認 `ErrorCode.java` 定義與 `GlobalExceptionHandler` 分組之外，全庫零 `throw` 呼叫點）。
3. **狀態機**（詳見 §3.3）：`OPEN → IN_PROGRESS → RESOLVED → CLOSED`，`CLOSED` 為終態；`OPEN`／`IN_PROGRESS` 皆可直接跳到 `CLOSED`（例如買家自行取消問題或客服判定無需繼續處理）。轉為 `RESOLVED` 時若 `resolvedAt` 尚未設定會自動寫入當下時間。
4. **平台層「指派」端點完全不做租戶篛選**：`assignTicket` 的存取邊界完全交給 Controller 的 `@PreAuthorize("hasAuthority('support_ticket:manage:all')")`，比照既有 M16 審批端點的慣例（Service 內部不重複檢查）。
5. **工單編號產生用「查詢當日序號 + 重試」而非原子操作**：`countByTicketNumberStartingWith` 算當日序號後組字串，若撞到資料庫唯一約束（同秒高併發）則捕捉 `DataIntegrityViolationException` 重試，上限 3 次（DEF-139）。
6. **`SenderType.SYSTEM` 是定義了但目前沒有任何程式碼路徑會寫入的列舉值**（與 `E-8009` 同類「預留但未啟用」狀態），目前訊息只會是 `CUSTOMER` 或 `STAFF`。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M18-SUP-001 | 買家提交客服工單（可選關聯訂單） | `POST /v2/support/tickets` | ✅ 已實作 |
| US-M18-SUP-002 | 買家查詢自己的工單列表與詳情、留言 | `GET /v2/support/tickets`、`GET .../{id}`、`POST .../{id}/messages` | ✅ 已實作 |
| US-M18-SUP-003 | 店家查詢自己店鋪的工單、更新狀態、回覆留言 | `GET /v2/dashboard/support/tickets`、`GET .../{id}`、`PUT .../{id}`、`POST .../{id}/messages` | ✅ 已實作 |
| US-M18-SUP-004 | 平台客服查詢全部工單、指派處理人 | `GET /v2/admin/support/tickets`、`GET .../{id}`、`PUT .../{id}/assign` | ✅ 已實作 |

FRD 完全無 M18 章節（PRD 側已有 `M18_Knowledge_Management_SPEC.md` §5，見文件頂部說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M18-SUP-001 | POST | `/v2/support/tickets` | 買家提交工單 | `support_ticket:create` |
| API-M18-SUP-002 | GET | `/v2/support/tickets` | 買家自己的工單列表 | `support_ticket:read` |
| API-M18-SUP-003 | GET | `/v2/support/tickets/{id}` | 買家查詢自己的工單詳情 | `support_ticket:read` |
| API-M18-SUP-004 | POST | `/v2/support/tickets/{id}/messages` | 買家留言 | `support_ticket:create` |
| API-M18-SUP-005 | GET | `/v2/dashboard/support/tickets` | 店家自己店鋪的工單列表 | `support_ticket:read` |
| API-M18-SUP-006 | GET | `/v2/dashboard/support/tickets/{id}` | 店家查詢工單詳情 | `support_ticket:read` |
| API-M18-SUP-007 | PUT | `/v2/dashboard/support/tickets/{id}` | 店家更新工單狀態/優先級 | `support_ticket:update` |
| API-M18-SUP-008 | POST | `/v2/dashboard/support/tickets/{id}/messages` | 店家回覆留言 | `support_ticket:create` |
| API-M18-SUP-009 | GET | `/v2/admin/support/tickets` | 平台查詢全部工單 | `support_ticket:manage:all` |
| API-M18-SUP-010 | GET | `/v2/admin/support/tickets/{id}` | 平台查詢工單詳情（跨租戶） | `support_ticket:manage:all` |
| API-M18-SUP-011 | PUT | `/v2/admin/support/tickets/{id}/assign` | 平台指派處理人 | `support_ticket:manage:all` |

共 11 個端點（`M18_Knowledge_Management_SPEC.md` §5.4 的既有表格只列 9 條，漏了 API-M18-SUP-006／010 兩條工單詳情查詢，見文件頂部說明）。

**⚠️ 路徑變數命名提醒**：所有 `{id}` 皆為路徑樣板字面寫法（Java 方法參數變數名為 `ticketId`，但 `@PathVariable("id")` 的路徑模板本身是 `{id}`）。

---

## 3. 資料模型 / Data Model

### 3.1 `SupportTicket`（表 `support_tickets`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | 可為 `null`（平台工單，未關聯店鋪） |
| `ticketNumber` | String | 唯一單號，格式 `TK-yyyyMMdd-NNN`（當日序號） |
| `category` | enum `TicketCategory` | `PRODUCT`／`BOOKING`／`PAYMENT`／`TECHNICAL`／`OTHER` |
| `subject` | String | 上限 200 字，not null |
| `description` | String | not null |
| `status` | enum `TicketStatus` | 見 §3.3，預設 `OPEN` |
| `priority` | enum `TicketPriority` | `LOW`／`NORMAL`／`HIGH`／`URGENT`，預設 `NORMAL` |
| `customerId` | UUID | 提交者，not null |
| `assignedTo` | UUID | 處理者，選填 |
| `orderId` | UUID | 相關訂單，選填 |
| `createdAt` / `updatedAt` / `resolvedAt` | Instant | |

`@DynamicUpdate`：更新時只送出真正改過的欄位（DEF-163），讓併發的「更新狀態」與「指派處理人」互不覆蓋對方已提交的欄位。無 `insertable=false`／`updatable=false` 的唯讀影子欄位。

### 3.2 `SupportMessage`（表 `support_messages`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `ticketId` | UUID | not null |
| `senderId` | UUID | not null |
| `senderType` | enum `SenderType` | `CUSTOMER`／`STAFF`／`SYSTEM`（`SYSTEM` 目前無程式碼路徑寫入，見 §1.2 第 6 點） |
| `message` | String | not null |
| `attachments` | `List<String>`（JSONB） | 選填 |
| `createdAt` | Instant | |

無唯讀影子欄位。

### 3.3 狀態機 / State Machine

```
OPEN ──→ IN_PROGRESS ──→ RESOLVED ──→ CLOSED（終態）
 │                           │
 └──────────→ CLOSED ←───────┘
```

精確規則：`OPEN → {IN_PROGRESS, CLOSED}`；`IN_PROGRESS → {RESOLVED, CLOSED}`；`RESOLVED → {CLOSED}`；`CLOSED → {}`（無合法轉換）。不合法的狀態轉換拋 `E-8010`。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/support/tickets` — 買家提交工單

**權限**：`support_ticket:create`。若帶 `orderId`，會驗證該訂單確實屬於呼叫者本人（`findByIdAndUserId`），否則視為平台工單不關聯訂單（`tenantId=null`）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `category` | String | 必填 | 需能解析成 `TicketCategory`，否則 `E-8010` |
| `subject` | String | 必填 | 上限 200 字 |
| `description` | String | 必填 | |
| `orderId` | UUID | 選填 | |

**回應**：`TicketResponse`（`id`／`tenantId`／`ticketNumber`／`category`／`subject`／`description`／`status`／`priority`／`customerId`／`assignedTo`／`orderId`／`createdAt`／`updatedAt`／`resolvedAt`／`messages`——建立時 `messages` 為 `null`，僅工單詳情端點會填值）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `orderId` 不是自己的訂單（或不存在） | 404 | `E-5000`（IDOR-safe：不透露該訂單是否存在／屬於誰） |
| `category` 字串無法解析 | 400 | `E-8010` |

### 4.2 `GET /v2/support/tickets` — 買家自己的工單列表

**權限**：`support_ticket:read`。只回傳呼叫者自己的工單，`messages` 欄位為 `null`。分頁參數 `page`（預設 0）、`size`（預設 20）。

### 4.3 `GET /v2/support/tickets/{id}` — 買家查詢自己的工單詳情

**權限**：`support_ticket:read`，且限本人。`messages` 欄位會填入完整留言列表。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在或非本人 | 404 | `E-8008`（見 §1.2 第 2 點：刻意不回 403） |

### 4.4 `POST /v2/support/tickets/{id}/messages` — 買家留言

**權限**：`support_ticket:create`，且限本人的工單。

**請求**：`message`（String，必填）、`attachments`（`List<String>`，選填）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在或非本人 | 404 | `E-8008` |

### 4.5 `GET /v2/dashboard/support/tickets` — 店家自己店鋪的工單列表

**權限**：`support_ticket:read`，且限真正的店鋪租戶。非 `SUPER_ADMIN` 且非真正店鋪租戶時**直接回空頁**，不拋例外（Sprint 234 修法，與 §4.9 的回應模式相同）。

### 4.6 `GET /v2/dashboard/support/tickets/{id}` — 店家查詢工單詳情

**權限**：`support_ticket:read`，且限同租戶（`SUPER_ADMIN` 不受限）。`messages` 欄位會填入完整留言列表。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在、非同租戶、呼叫者非真正店鋪租戶 | 404 | `E-8008`（系統租戶呼叫者一律視為找不到，不查 DB） |

### 4.7 `PUT /v2/dashboard/support/tickets/{id}` — 店家更新工單狀態/優先級

**權限**：`support_ticket:update`，且限同租戶（`SUPER_ADMIN` 不受限）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `status` | String | 必填 | 需能解析成 `TicketStatus`，且須為合法轉換（§3.3），否則 `E-8010` |
| `priority` | String | 選填 | 需能解析成 `TicketPriority`，否則 `E-8010` |

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在、非同租戶 | 404 | `E-8008` |
| 狀態/優先級字串非法，或狀態轉換不合法 | 400 | `E-8010` |

### 4.8 `POST /v2/dashboard/support/tickets/{id}/messages` — 店家回覆留言

**權限**：`support_ticket:create`，且限同租戶（`SUPER_ADMIN` 不受限）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在、非同租戶 | 404 | `E-8008` |

### 4.9 `GET /v2/admin/support/tickets` — 平台查詢全部工單

**權限**：`support_ticket:manage:all`。Controller 層已鎖死存取邊界，Service 不篩租戶，回傳全平台工單。

### 4.10 `GET /v2/admin/support/tickets/{id}` — 平台查詢工單詳情（跨租戶）

**權限**：`support_ticket:manage:all`。等同以 `SUPER_ADMIN` 身分呼叫 §4.6 的邏輯，可查任意租戶/平台工單。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在 | 404 | `E-8008` |

### 4.11 `PUT /v2/admin/support/tickets/{id}/assign` — 平台指派處理人

**權限**：`support_ticket:manage:all`。Service 層**完全不做租戶篩選**（見 §1.2 第 4 點），存取邊界只靠 `@PreAuthorize`。

**請求**：`assignedTo`（UUID，必填）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 工單不存在 | 404 | `E-8008` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 買家本人 | 同租戶店家 | SUPER_ADMIN |
|------|:----:|:----:|:----:|
| 提交工單（§4.1） | ✅ | ❌ | ❌ |
| 查詢/留言自己的工單（§4.2～4.4） | ✅ | ❌ | ❌ |
| 店家列表/詳情/更新/留言（§4.5～4.8） | ❌ | ✅ | ✅（跨租戶） |
| 平台列表/詳情/指派（§4.9～4.11） | ❌ | ❌ | ✅ |

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-8008` | 404 | 找不到工單（含非本人/非同租戶，刻意不回 403，見 §1.2） |
| `E-8009` | 403 | **已定義但目前全庫無任何拋出點**（死碼，見 §1.2 第 2 點） |
| `E-8010` | 400 | `category`／`status`／`priority` 字串無法解析成列舉，或狀態轉換不合法 |

另使用以下跨模組共用錯誤碼：`E-5000`（找不到訂單，§4.1 的 `orderId` 驗證失敗）。

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `E-8009` 是死碼 | 已定義 HTTP 狀態碼與測試案例，但業務邏輯從不拋出（一律用 `E-8008` 取代，避免洩漏工單存在性），見 §1.2 第 2 點 |
| `SenderType.SYSTEM` 未啟用 | 列舉值存在，但目前沒有任何程式碼路徑寫入此類型的留言 |
| 工單編號產生非原子操作 | `countByTicketNumberStartingWith` 查詢後組字串，靠捕捉 DB 唯一約束違反重試（上限 3 次），高併發下理論上仍有極小機率連續 3 次撞號失敗（DEF-139） |
| FRD 完全無 M18 章節 | PRD 側有 `M18_Knowledge_Management_SPEC.md` §5，但 FRD（User Story/AC 的權威來源）從未補上對應章節 |
| Controller／`@PreAuthorize` 層完全無測試覆蓋 | 既有測試（`SupportTicketServiceTest`、`SupportMessageServiceTest`）皆直接呼叫 Service，不經過 Spring Security／HTTP 層，本文件列出的權限字串未被任何測試驗證過會正確生效或正確 403/404 |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `SupportTicketController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.10 M18 知識管理系統（客服系統子功能，Phase 2-B，P2） |
| 相關規格 | `docs/01_requirements/M18_Knowledge_Management_SPEC.md` §5～6（資料模型／RBAC 矩陣，與本文件一致但 API 表少 2 條）、[API_Error_Codes.md](../API_Error_Codes.md) |
| 實作 | `SupportTicketController`（`api/controller/support/`）；`SupportTicketService`、`SupportMessageService`（`core/support/`）；`SupportTicket`、`SupportMessage`（`domain/model/support/`） |
| 契約測試 | [SupportTicketServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/support/SupportTicketServiceTest.java)、[SupportMessageServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/support/SupportMessageServiceTest.java) |

---

**文件狀態**：v1.0（Sprint 265 新建，DEF-367）。
