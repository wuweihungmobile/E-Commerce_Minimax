# API 規格文件 - M10 即時通訊 / API Specification - M10 Chat (IM)

> **文件編號**: API-M10
> **文件版本**: v1.1（Sprint 319：DEF-441——§1 第 2 點更正：搶輸者拿到贏家那一筆的做法改為整個交易重做，由真實競態測試守住）；v1.0（Sprint 274，首次建立；Sprint 25 功能上線至今 `docs/02_architecture/` 下從未有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-16
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`ChatController`（`api/controller/`）、`ChatService`（`core/chat/`）、`Conversation`／`Message`（`domain/model/chat/`）、`WebSocketConfig`（`api/config/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[ChatServiceStompBroadcastTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/chat/ChatServiceStompBroadcastTest.java)、[ChatServiceTenantResolutionTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/chat/ChatServiceTenantResolutionTest.java)、[M10ChatIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatIntegrationTest.java)、[M10ChatTenantIsolationIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatTenantIsolationIntegrationTest.java)、[M10ChatUnreadCountConcurrencyIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatUnreadCountConcurrencyIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M10 章節，僅 PRD §9.9／§9.12 提及（Phase 2 即時通訊）

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M10 |
| **模組名稱** | 即時通訊 / Chat (IM) |
| **描述** | 買家與店家（或平台使用者之間）一對一對話；REST 建立對話/查歷史，STOMP WebSocket 即時推播新訊息 |
| **API 前綴** | 後端 context path 是 `/api`，完整網址為 `/api/v2/chat/...`；WebSocket 端點另見 §1.3 |
| **多租戶隔離** | 對話的 `tenantId` 是**推導值**，非存取控制依據——存取控制完全靠「呼叫者是否為 `initiatorId`／`recipientId` 之一」（ownership，非 tenant-based） |
| **權限模型** | 全部 6 個端點僅檢查 `isAuthenticated()`，**不分角色、不設 `hasAuthority` 權限字串**；任何已登入角色（含 `GUEST`）皆可呼叫，由 Service 層的 ownership 檢查決定能存取哪些對話 |

### 1.2 設計重點

1. **對話租戶的推導規則**（`resolveTenantId`）：有 `listingId` → 取該 listing 的 `tenantId`；否則有 `orderId` → 取該 order 的 `tenantId`；兩者皆無或關聯實體不存在 → 退回系統租戶常數 `00000000-0000-0000-0000-000000000001`。此 `tenantId` 目前只存入資料庫，**不出現在任何回應 DTO、也不被任何查詢用作過濾條件**。
2. **同一對 initiator/recipient 至多一筆作用中對話**：`createConversation` 先查詢既有作用中對話，沒有才建立；DB 另有 `(initiator_id, recipient_id) WHERE is_active=true` 部分唯一索引（V81）兜底併發搶建（DEF-118），搶輸的請求回 `200` 與搶贏者那一筆，**不會**把自己的 `initialMessage` 併入。做法（DEF-441，Sprint 319）：搶輸時整個交易回滾、在全新的交易重做（`UniqueConflictRetry`），第二次的「是否已有對話」檢查就看得到贏家那一筆。此前的寫法是 catch 之後在同一個交易裡重查，但 PostgreSQL 在唯一約束違反後整個交易已中止，重查必然失敗——搶輸者實際得到 `500 E-9900`（mock Repository 的單元測試看不到；`UniqueConstraintRaceRealStackIntegrationTest.createConversation_losingTheRace_returnsTheWinnersConversation` 用真實競態守住；觸發條件是同一位使用者對同一個對象的兩個發起請求幾乎同時送出，例如重複點擊或重送）。
3. **「發起人／收件人」是依建立對話時呼叫者身分固定下來的角色**，之後不會因為誰說話而互換；`initiatorUnreadCount`／`recipientUnreadCount` 各自獨立計數。
4. **軟刪除會讓同一對使用者可以建立全新對話**：`deleteConversation` 只將 `isActive` 設為 `false`；因為唯一索引只保護 `is_active = true` 的列，刪除後若雙方其中一人再呼叫 `POST /conversations`，會產生一筆**全新**的對話（原對話與其訊息仍存在資料庫，但雙方都查不到），不是「復原已刪除對話」。
5. **即時推播**：`sendMessage` 成功寫入後，透過 Spring `SimpMessagingTemplate` 廣播到 STOMP 目的地 `/queue/conversations/{conversationId}/messages`；訂閱授權由 `StompAuthChannelInterceptor` 在 `SUBSCRIBE` frame 把關（DEF-035 已修復，非本文件範圍）。
6. **分頁上限**：`getUserConversations`／`getMessages` 皆用 `PageableUtils.of(page, size, 50)`——`page` 下限 0，`size` 上限固定 **50**（與呼叫端傳入值無關），下限 1。Controller 的 `@RequestParam` 預設值不同：對話列表預設 `size=20`，訊息歷史預設 `size=50`。

### 1.3 WebSocket 端點（STOMP over SockJS）

| 項目 | 值 |
|------|-----|
| 連線端點 | `/ws`（`withSockJS()`，支援 SockJS fallback） |
| Broker destinations | `/topic`（廣播）、`/queue`（點對點，本模組使用此類） |
| Application prefix | `/app`（送到 `@MessageMapping` 方法；本模組 `ChatController` 無任何 `@MessageMapping`，純 REST + Service 主動 `convertAndSend`） |
| 訂閱目的地 | `/queue/conversations/{conversationId}/messages`（收到 `ChatDto.MessageResponse` JSON） |
| 連線/訂閱授權 | `StompAuthChannelInterceptor`（`configureClientInboundChannel`） |

### 1.4 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M10-001 | 使用者發起與他人的對話（可選關聯商品/訂單，可帶首則訊息） | `POST /v2/chat/conversations` | ✅ 已實作 |
| US-M10-002 | 使用者查詢自己的對話列表 | `GET /v2/chat/conversations` | ✅ 已實作 |
| US-M10-003 | 使用者刪除（封存）對話 | `DELETE /v2/chat/conversations/{id}` | ✅ 已實作 |
| US-M10-004 | 使用者在對話中發送訊息，對方即時收到 STOMP 推播 | `POST /v2/chat/messages` | ✅ 已實作 |
| US-M10-005 | 使用者查詢對話的訊息歷史 | `GET /v2/chat/conversations/{id}/messages` | ✅ 已實作 |
| US-M10-006 | 使用者將對話標記已讀 | `PUT /v2/chat/conversations/{id}/read` | ✅ 已實作 |

FRD 完全無 M10 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M10-001 | POST | `/v2/chat/conversations` | 建立對話（已存在則回傳既有的） | 已登入（任何角色） |
| API-M10-002 | GET | `/v2/chat/conversations` | 取得自己的對話列表 | 已登入（任何角色） |
| API-M10-003 | DELETE | `/v2/chat/conversations/{conversationId}` | 刪除（封存）對話 | 已登入（任何角色），限本人 |
| API-M10-004 | POST | `/v2/chat/messages` | 發送訊息 | 已登入（任何角色），限對話本人 |
| API-M10-005 | GET | `/v2/chat/conversations/{conversationId}/messages` | 取得訊息歷史 | 已登入（任何角色），限對話本人 |
| API-M10-006 | PUT | `/v2/chat/conversations/{conversationId}/read` | 標記對話已讀 | 已登入（任何角色），限對話本人 |

共 6 個端點。

---

## 3. 資料模型 / Data Model

### 3.1 `Conversation`（表 `conversations`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | 推導值，見 §1.2 第 1 點；**不出現在回應中** |
| `listingId` / `orderId` | UUID | 選填，對話的關聯情境 |
| `conversationType` | enum | `DIRECT`／`LISTING_INQUIRY`／`ORDER_INQUIRY`／`BOOKING_INQUIRY`，未指定時預設 `DIRECT` |
| `initiatorId` / `recipientId` | UUID | 建立時固定，之後不互換 |
| `initiatorUnreadCount` / `recipientUnreadCount` | int | 各自獨立；新訊息送達時原子遞增（`recordNewMessage`，DEF-056） |
| `isActive` | boolean | `deleteConversation` 設為 `false`；`findByInitiatorIdAndRecipientIdAndIsActiveTrue` 只認 `true` |
| `lastMessageId` / `lastMessagePreview`（50 字截斷） / `lastMessageAt` | — | 最新訊息快照 |
| `createdAt` / `updatedAt` | Instant | |

唯一索引：`(initiator_id, recipient_id) WHERE is_active = true`（V81，部分索引）。

### 3.2 `Message`（表 `messages`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `conversationId` | UUID | `insertable=false, updatable=false` 唯讀鏡像欄位（真正來源是 `conversation` 關聯，見 §7 DEF-012 歷史修復） |
| `senderId` | UUID | |
| `messageType` | enum | `TEXT`／`IMAGE`／`FILE`／`SYSTEM`，未指定時預設 `TEXT`（**全庫無任何路徑送出 `SYSTEM` 類型訊息**，與 `IMAGE`/`FILE` 一样僅由前端自行帶值，後端不做對應驗證） |
| `content` | String | 必填 |
| `attachments` | `List<String>`（JSON） | 選填 |
| `isRead` / `readAt` / `readBy` | — | `markAsRead` 批次更新 |
| `isDeleted` | boolean | 預設 `false`；**全庫無任何寫入路徑會設為 `true`**——沒有「刪除單則訊息」功能，只能整個對話一起封存（§1.2 第 4 點） |
| `createdAt` | Instant | |

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/chat/conversations` — 建立對話

**權限**：`isAuthenticated()`。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|:--:|------|
| `recipientId` | UUID | 必填 | |
| `listingId` | UUID | 選填 | 影響 `tenantId` 推導 |
| `orderId` | UUID | 選填 | 影響 `tenantId` 推導 |
| `conversationType` | enum | 選填 | 預設 `DIRECT` |
| `initialMessage` | String | 選填 | 非空白時建立後立即寫入第一則訊息並更新 `lastMessage*` |

**回應**：`ConversationResponse`（200）。若呼叫者與 `recipientId` 已有作用中對話，**直接回傳既有那一筆**，不比對/更新 `listingId`／`orderId`／`conversationType`，也不送出 `initialMessage`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `recipientId` 缺失 | 400 | `E-9000`（`@Valid` 驗證失敗） |

### 4.2 `GET /v2/chat/conversations` — 取得對話列表

**權限**：`isAuthenticated()`。查詢參數 `page`（預設 0）、`size`（預設 20，上限 50）。只回傳呼叫者為 `initiatorId` 或 `recipientId` 的作用中對話，依 `lastMessageAt` 降序。

**回應**：`ConversationListResponse`（200），`unreadCount` 彙總欄位對稱計入呼叫者身為 `initiatorId` 或 `recipientId` 兩種角色各自未讀的對話（Sprint 278／DEF-383 修復前只計入 `recipientId` 角色）。每筆 `conversations[].unreadCount` 依瀏覽者在該對話中的角色回傳對應的未讀計數（修復前永遠是 `null`，見 §7 DEF-383）。

### 4.3 `DELETE /v2/chat/conversations/{conversationId}` — 刪除對話

**權限**：`isAuthenticated()`，且限本人（`initiatorId` 或 `recipientId`）。僅將 `isActive` 設為 `false`（軟刪除，§1.2 第 4 點），回應 200／`data: null`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 對話不存在或非本人 | 404 | `E-8100`（Sprint 278／DEF-382 修復前誤用 `E-9005`，見 §7） |

### 4.4 `POST /v2/chat/messages` — 發送訊息

**權限**：`isAuthenticated()`，且限對話本人。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|:--:|------|
| `conversationId` | UUID | 必填 | |
| `content` | String | 必填（`@NotBlank`） | |
| `messageType` | enum | 選填 | 預設 `TEXT` |
| `attachments` | `List<String>` | 選填 | |

**回應**：`MessageResponse`（200）。成功寫入後原子更新對話的 `lastMessage*` 與對方未讀計數（DEF-056），並透過 STOMP 廣播到 `/queue/conversations/{conversationId}/messages`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `conversationId`／`content` 缺失 | 400 | `E-9000` |
| 對話不存在或非本人 | 404 | `E-8100`（Sprint 278／DEF-382 修復前誤用 `E-9005`，見 §7） |

### 4.5 `GET /v2/chat/conversations/{conversationId}/messages` — 取得訊息歷史

**權限**：`isAuthenticated()`，且限對話本人。查詢參數 `page`（預設 0）、`size`（預設 50，上限 50）。依 `createdAt` 降序（最新在前）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 對話不存在或非本人 | 404 | `E-8100`（Sprint 278／DEF-382 修復前誤用 `E-9005`，見 §7） |

### 4.6 `PUT /v2/chat/conversations/{conversationId}/read` — 標記已讀

**權限**：`isAuthenticated()`，且限對話本人。將對話中**對方**（`senderId != 呼叫者`）尚未讀取的訊息全部標記 `isRead=true`／`readAt`／`readBy`，並將呼叫者對應的未讀計數（`initiatorUnreadCount` 或 `recipientUnreadCount`）歸零。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 對話不存在或非本人 | 404 | `E-8100`（Sprint 278／DEF-382 修復前誤用 `E-9005`，見 §7） |

---

## 5. 權限矩陣 / Permission Matrix

本模組不以角色或 `hasAuthority` 區分權限——6 個端點皆只要求 `isAuthenticated()`，任何已登入角色（`GUEST`／`BUYER`／`SELLER`／`HOST`／`STORE_OWNER`／`STORE_STAFF`／`ADMIN`／`CFO`／`SUPER_ADMIN`）皆可呼叫。實際存取邊界完全靠 ownership 檢查（§4.3～4.6 的「限本人／限對話本人」），不是角色矩陣，故不另列角色 × 端點表格。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-9000` | 400 | 請求欄位缺失（`@Valid` 驗證失敗，`recipientId`／`conversationId`／`content`） |
| `E-8100` | 404 | 對話不存在或非本人（Sprint 278／DEF-382 新增 M10 專屬碼，修復前誤用 `E-9005`，見 §7） |

本模組原無任何專屬錯誤碼區段（不像 M05 的 E-5xxx、M18 的 E-8xxx），Sprint 278（DEF-382）新增 `E-8100`（Chat & Messaging，E-8100s）。

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `E-9005` 誤用（**DEF-382，已於 Sprint 278 修復**） | `sendMessage`／`getMessages`／`markAsRead`／`deleteConversation` 四處對話不存在時，原一律拋 `ErrorCode.E_9005`（官方定義「缺少必填欄位」），語意不符。已新增 M10 專屬碼 `E-8100`（找不到對話，404）取代 |
| 未讀計數雙重缺口（**DEF-383，已於 Sprint 278 修復**） | (1) `ConversationResponse.unreadCount` 原恆為 `null`，已改為 `ChatService.toConversationResponse` 依瀏覽者在對話中的角色（initiator／recipient）回傳對應的未讀計數欄位；(2) `ConversationListResponse.unreadCount` 的 `countUnreadConversations` 查詢原只比對 `recipientId`，已改為對稱比對 initiator／recipient 兩種角色。修復前**無任何前端頁面讀取這兩個欄位**——`ConversationList.tsx` 改用 STOMP 即時事件自建用戶端 `unreadMap`，故修復前不是使用者可見的畫面錯誤 |
| `ChatDto.TypingIndicator`／`WebSocketMessage` 是死 DTO | 定義存在，但全庫無任何程式碼路徑建構或傳送這兩個類型——「正在輸入」與通用 WebSocket 事件封包是 Phase 2 規劃殘留，從未真正實作 |
| `Message.isDeleted` 永遠是 `false` | 欄位與查詢條件（`findByConversationIdAndIsDeletedFalse...`）都存在，但沒有任何寫入路徑會設為 `true`；沒有「刪除單則訊息」功能 |
| `tenantId` 推導值不可見 | `resolveTenantId` 寫入 DB 但不出現在任何回應 DTO，也不被任何查詢用作過濾條件；純粹是歷史資料欄位（V9/V56 回填規則），非目前存取控制的一部分 |
| Controller／`@PreAuthorize` 層測試覆蓋 | `ChatServiceStompBroadcastTest`／`ChatServiceTenantResolutionTest` 直接呼叫 Service；`M10ChatIntegrationTest` 系列經 HTTP 層，`isAuthenticated()` 的授權行為有實測覆蓋 |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `ChatController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §9.9／§9.12（M10 即時通訊，Phase 2） |
| FRD | 無章節（見文件頂部狀態說明） |
| 實作 | `ChatController`（`api/controller/`）；`ChatService`（`core/chat/`）；`Conversation`、`Message`（`domain/model/chat/`）；`WebSocketConfig`、`StompAuthChannelInterceptor`（`api/config/`、`api/filter/`） |
| 契約測試 | [ChatServiceStompBroadcastTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/chat/ChatServiceStompBroadcastTest.java)、[ChatServiceTenantResolutionTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/chat/ChatServiceTenantResolutionTest.java)、[M10ChatIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatIntegrationTest.java)、[M10ChatTenantIsolationIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatTenantIsolationIntegrationTest.java)、[M10ChatUnreadCountConcurrencyIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M10ChatUnreadCountConcurrencyIntegrationTest.java) |
| 前端 | `frontend/src/services/chat.ts`、`frontend/src/hooks/useChatSocket.ts`、`frontend/src/components/chat/`、`frontend/src/app/dashboard/chat/page.tsx` |

---

**文件狀態**：v1.0（Sprint 274 新建，DEF-286 範圍）。
