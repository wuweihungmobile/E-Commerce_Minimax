# API 規格文件 - M09 訊息通知中心 / API Specification - M09 Notification Center

> **文件編號**: API-M09-NOTIF
> **文件版本**: v1.1（Sprint 287：補登 Sprint 286／DEF-330(a) 已改的範本列表與詳情權限，以及 Sprint 287／DEF-394 的 ADMIN 跨租戶管理範本；v1.0 為 Sprint 272 首次建立，Sprint 20 功能上線至今 `docs/02_architecture/` 下從未有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-15
> **最後更新**: 2026-10-20
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController`（`api/controller/`）、`NotificationService`／`NotificationHistoryService`／`NotificationPreferenceService`／`NotificationTemplateService`（`core/notification/`）、`NotificationProducerService`／`NotificationConsumerService`（`infrastructure/mq/`）、`Notification`／`NotificationHistory`／`NotificationTemplate`／`UserNotificationPreference`（`domain/model/notification/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有測試佐證：[NotificationServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/notification/NotificationServiceTest.java)、[NotificationHistoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/notification/NotificationHistoryServiceTest.java)、[NotificationPreferenceServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/notification/NotificationPreferenceServiceTest.java)、[NotificationTemplateServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/notification/NotificationTemplateServiceTest.java)、[NotificationConsumerServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/infrastructure/mq/NotificationConsumerServiceTest.java)、[M09NotificationHistoryIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M09NotificationHistoryIntegrationTest.java)（HTTP 層，真實 JWT）、[M09NotificationTemplateIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M09NotificationTemplateIntegrationTest.java)（HTTP 層，真實 JWT）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **本文件撰寫範圍說明**：涵蓋 FRD §6C「M09 訊息通知中心」描述的三個子功能——站內通知（含歷史查詢）、通知偏好設定、通知範本管理。PRD 原始 M09 設計的 Phase 2 願景（RabbitMQ、多通道真實寄送、模板化渲染全面套用）僅部分落地，詳見 §1.2 與 FRD §6C.7

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M09 |
| **模組名稱** | 訊息通知中心 / Notification Center |
| **描述** | 站內通知（列表、未讀數、已讀、刪除、獨立的歷史查詢）、使用者通知偏好設定（依類型×通道開關）、通知範本管理（建立/更新/刪除/渲染預覽）；管理員可直接發送或廣播通知 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/notifications/...`、`/api/v2/notification-templates/...`、`/api/v2/dashboard/notification-templates/...` |
| **多租戶隔離** | 通知本身、通知歷史、使用者偏好皆以 `userId` 為界，與租戶無關（買家與賣家共用同一套資料表，不分租戶）。通知範本的 `tenantId` 可為 `null`（平台全域範本）或指定租戶（店鋪自訂範本），詳見 §3.3 |
| **範圍外** | PRD 原始設計的訊息佇列選型（RabbitMQ）、EMAIL/SMS/PUSH 以外的推播閘道、模板引擎在發送路徑的實際套用（目前 `renderTemplate` 僅供預覽，`NotificationConsumerService.dispatch` 並未讀取範本內容） |

### 1.2 設計重點

1. **三個子功能、三個 Controller**：`NotificationController`（站內通知本體＋歷史查詢）、`NotificationPreferenceController`（使用者偏好）、`NotificationTemplateController`（範本管理與渲染預覽）。
2. **非同步送出管線**：`sendNotification` 先在同一交易內寫入一列 `notifications`（`isSent=false`），再呼叫 `NotificationProducerService` 把訊息推入 Redis List（`leftPush`）；`NotificationConsumerService` 以 `@Scheduled(fixedDelay=1000)` 的 `rightPop` 消費，更新**同一列**而不是新增第二列（Sprint 219，DEF-305：預建列與入佇在同一交易裡，消費者讀取時原交易可能還沒提交，找不到預建列時不新增重複列，改拋例外交給重試機制）。`broadcastNotification`／廣播入佇沒有預建列，由消費者直接新增一列。
3. **依管道決定真實送出結果（Sprint 271，DEF-317）**：`NotificationConsumerService.dispatch()` 依 `channel` 決定 `isSent`——**IN_APP** 資料列本身可見即視為必定送達；**EMAIL** 收件地址非空且既有 `EmailSender.canDeliver()`（Sprint 204，見 [SRD §5.5](../SRD_System_Architecture.md)）為真才呼叫 `EmailSender.send()`，`canDeliver()=false` 或送出拋 `EmailDeliveryException` 時明確標記未送達並記錄原因；**SMS／PUSH** 目前無任何閘道實作，直接標記未送達並說明原因，**不嘗試送出、不拋例外**（刻意避免觸發訊息層級的重試機制去重試永遠不會成功的事）。**2026-10-15 之前**（Sprint 271 前）所有通道一律標記 `isSent=true`，`isSent` 欄位歷史資料可能不可信。
4. **失敗重試與 DLQ**：消費失敗（例外）時以 `messageId` 為 key 寫入 Redis（`notification:retry:{messageId}`，TTL = 30 秒 × 重試次數），由另一個 `@Scheduled(fixedDelay=5000)` 排程掃描重試；滿 3 次後移入 `notification:dlq:{messageId}`（Redis key，無消費者讀取，等同手動介入才能處理）。**此重試機制只處理訊息處理過程中拋出的例外（如 DEF-305 的預建列不可見），不會因為 EMAIL／SMS／PUSH 回報未送達而重試**——未送達是 `dispatch()` 的正常回傳值，不是例外。
5. **使用者偏好可在送出前完全攔截**：`NotificationService.sendNotification` 在建立任何資料列、入任何佇列**之前**先檢查 `NotificationPreferenceService.isEnabled`；停用時直接回傳一個未落地的 `NotificationResponse`（`isSent=false`、`errorMessage="Skipped by user preference"`），**不寫入 `notifications` 表、不進佇列**。`broadcastNotification` 不受此限（逐使用者呼叫 `sendToQueue`，略過偏好檢查，詳見 §4.2）。無明確設定的 type×channel 組合預設 `enabled=true`（向後相容）。
6. **刪除／標記已讀的擁有權檢查回 403 而非 404**：與 M18 客服工單「刻意用 404 隱藏存在性」的慣例不同，`deleteNotification`／`markOneAsRead`（歷史）對「資源存在但不是本人的」回 `E-1007`（403），對「資源完全不存在」回 `E-8002`（404）——呼叫者可藉由 403/404 的差異反推某個 ID 是否存在（輕微的列舉線索），風險低（`notificationId`／`historyId` 皆為隨機 UUID，不可枚舉猜測），僅記錄行為差異供後續模組比較參考。
7. **`BuyerNotificationService` 是另一個發送入口，不經過本文件任何 Controller**：由其他模組（訂單、訂房、退款）的業務流程在交易提交後（`afterCommit`）直接注入呼叫，涵蓋 18 個業務事件通知方法（取消、退款、付款逾時/失敗、訂單生命週期各階段、評價邀請等），一律寫死 `channel=IN_APP`，不受 Sprint 271 的 EMAIL 真實送出影響；是否讓這些事件額外送 EMAIL 屬產品決策，詳見 FRD §6C.7、[DEF-317](../../04_planning/DEFERRED_ITEMS_TRACKER.md)。本服務盡力而為、永不拋例外（Redis／DB 暫時失敗不可讓已完成的交易失敗），因此其發送路徑**沒有對外 API**，不在本文件 §2～§6 的端點清單中，僅在此記錄其存在與邊界。
8. **管理員發送 API 一律使用使用者本人註冊的聯絡方式**：`sendNotification`／`broadcastNotification` 的 `recipient` 由後端依 `channel` 從 `User` 實體查（EMAIL 用 `user.getEmail()`，SMS 用 `user.getPhone()`），完全不信任請求內的 `recipient` 欄位真正送達（DEF-238，Sprint 180：先前允許呼叫端任意指定收件地址，等同把通知管線變成內容可控的開放中繼站，已收斂）。`SendRequest.recipient` 欄位目前在 Service 層被忽略。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M09-NOTIF-001 | 買家查詢自己的通知列表、未讀數、標記已讀、刪除 | `GET /v2/notifications`、`PUT .../read`、`DELETE .../{id}`、`GET .../unread-count` | ✅ 已實作 |
| US-M09-NOTIF-002 | 買家查詢獨立的通知歷史（已讀/未讀） | `GET /v2/notifications/history`、`PUT .../{id}/read`、`GET .../unread-count` | ✅ 已實作 |
| US-M09-NOTIF-003 | 使用者設定每種通知類型×通道是否接收 | `GET /v2/notifications/preferences`、`PUT .../preferences` | ✅ 已實作（是否真的攔截送出見 §1.2 第 5 點） |
| US-M09-NOTIF-004 | SUPER_ADMIN 對指定使用者發送通知，或對全平台/單一租戶廣播 | `POST /v2/notifications/send`、`POST .../broadcast` | ✅ 已實作 |
| US-M09-NOTIF-005 | 平台與店鋪管理通知範本（建立/查詢/更新/刪除/渲染預覽） | `GET/POST/PUT/DELETE .../notification-templates`、`POST .../render` | ✅ 已實作（範本渲染結果目前不會被 §4.1/4.2 的實際發送路徑讀取，見 §1.1 範圍外） |
| US-M09-NOTIF-006 | 買家交易事件（取消/退款/付款逾時等）自動收到站內通知 | 無對外端點，見 §1.2 第 7 點 | ✅ 已實作（`BuyerNotificationService`，Sprint 229/246/249/250） |

對應 PRD US-001（預訂成功即時通知）、US-005（退款狀態即時通知）、US-014（支付失敗通知提醒）；FRD §6C 詳述現況與 PRD 落差。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M09-NOTIF-001 | POST | `/v2/notifications/send` | 對指定使用者發送通知 | `hasRole('SUPER_ADMIN')` |
| API-M09-NOTIF-002 | POST | `/v2/notifications/broadcast` | 廣播通知（全平台或單一租戶） | `hasRole('SUPER_ADMIN')` |
| API-M09-NOTIF-003 | GET | `/v2/notifications` | 取得自己的通知列表 | 已登入 |
| API-M09-NOTIF-004 | PUT | `/v2/notifications/read` | 標記通知已讀（指定或全部） | 已登入 |
| API-M09-NOTIF-005 | DELETE | `/v2/notifications/{notificationId}` | 刪除自己的通知 | 已登入 |
| API-M09-NOTIF-006 | GET | `/v2/notifications/unread-count` | 取得未讀數 | 已登入 |
| API-M09-NOTIF-007 | GET | `/v2/notifications/history` | 取得通知歷史列表 | 已登入 |
| API-M09-NOTIF-008 | PUT | `/v2/notifications/history/{historyId}/read` | 標記單筆歷史已讀 | 已登入 |
| API-M09-NOTIF-009 | GET | `/v2/notifications/history/unread-count` | 取得歷史未讀數 | 已登入 |
| API-M09-NOTIF-010 | GET | `/v2/notifications/preferences` | 取得自己的通知偏好 | 已登入 |
| API-M09-NOTIF-011 | PUT | `/v2/notifications/preferences` | Upsert 單一通知偏好 | 已登入 |
| API-M09-NOTIF-012 | GET | `/v2/notification-templates` | 範本列表（ADMIN／SUPER_ADMIN 可用 `targetTenantId` 指定店鋪） | `notification_template:read` |
| API-M09-NOTIF-013 | GET | `/v2/notification-templates/{id}` | 範本詳情（ADMIN／SUPER_ADMIN 可跨租戶） | `notification_template:read` |
| API-M09-NOTIF-014 | POST | `/v2/dashboard/notification-templates` | 建立範本（ADMIN／SUPER_ADMIN 可用 `targetTenantId` 代建） | `notification_template:create` |
| API-M09-NOTIF-015 | PUT | `/v2/dashboard/notification-templates/{id}` | 更新範本（ADMIN／SUPER_ADMIN 可跨租戶） | `notification_template:update` |
| API-M09-NOTIF-016 | DELETE | `/v2/dashboard/notification-templates/{id}` | 刪除範本（軟刪除；ADMIN／SUPER_ADMIN 可跨租戶） | `notification_template:delete` |
| API-M09-NOTIF-017 | POST | `/v2/notification-templates/render` | 渲染範本預覽 | `notification_template:read` |

共 17 個端點。範本的三個讀取端點（012／013／017）現在一致要求 `notification_template:read`（012／013 原為 `isAuthenticated()`，任何登入者皆可列出／查看範本，已於 Sprint 286／DEF-330(a) 修復；017 一直都檢查此權限）。**ADMIN／SUPER_ADMIN 可跨租戶管理範本（Sprint 287／DEF-394）**：012 與 014 以可選的 `targetTenantId` 指定店鋪，013／015／016 略過「範本須屬於呼叫者租戶」的擁有權檢查；STORE_OWNER／STORE_STAFF 仍限自己的租戶。017（渲染預覽）不在此範圍。詳見 §4.12～4.16 與 §7。

**⚠️ 路徑變數命名提醒**：`{id}` 對應 Controller 參數 `@PathVariable UUID id`；`{notificationId}`／`{historyId}` 對應相同名稱的 `@PathVariable`，與路徑模板文字一致。

---

## 3. 資料模型 / Data Model

### 3.1 `Notification`（表 `notifications`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `userId` | UUID | 收件使用者，not null |
| `notificationType` | enum `NotificationType` | 見下方列舉，not null |
| `title` | String | not null |
| `content` | String (TEXT) | |
| `data` | `Map<String,Object>`（JSONB） | 附加資料（例如 `bookingId`、`refundAmount`），選填 |
| `channel` | enum `NotificationChannel` | not null，預設 `IN_APP` |
| `recipient` | String | EMAIL 位址或 SMS 號碼；`IN_APP` 為 `null` |
| `isSent` | Boolean | 預設 `false`；Sprint 271 起才反映真實送出結果（見 §1.2 第 3 點） |
| `isRead` | Boolean | 預設 `false` |
| `sentAt` / `readAt` | Instant | |
| `errorMessage` | String | 未送達原因（Sprint 271 起有意義的內容，見 §1.2 第 3 點） |
| `retryCount` | Integer | 預設 `0`，消費失敗時遞增 |
| `createdAt` / `updatedAt` | Instant | |

無唯讀影子欄位。

### 3.2 `NotificationHistory`（表 `notification_history`）

與 `notifications` **是獨立的兩張表、兩套 API**（§1.3 US-M09-NOTIF-001 vs 002），由 `NotificationConsumerService` 在 Consumer ACK 後額外寫入一筆（保障「至少一次送達」語意，AI-502），**與對應 `notifications` 列的 `isSent` 結果無關**——即便 EMAIL／SMS／PUSH 回報未送達，仍會寫入一筆通知歷史。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `userId` | UUID | not null |
| `tenantId` | UUID | 選填，目前寫入路徑一律傳 `null`（`NotificationConsumerService` 呼叫 `createHistory` 時第二參數固定 `null`） |
| `notificationType` | String | not null（存原始字串，非資料庫列舉） |
| `channel` | String | not null，預設 `"IN_APP"` |
| `title` / `body` | String | `title` not null |
| `isRead` | Boolean | not null，預設 `false` |
| `readAt` / `createdAt` | Instant | `createdAt` 建立後不可變 |

### 3.3 `NotificationTemplate`（表 `notification_templates`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | 可為 `null`（平台全域範本）或指定租戶（店鋪自訂） |
| `templateCode` | String | not null，上限 100 字；同一 `(templateCode, tenantId)` 唯一（`existsByTemplateCodeAndTenantId`） |
| `notificationType` | enum（範本自己的 `NotificationTemplate.NotificationType`） | not null；與 `Notification.NotificationType` 列舉值相同但是**不同的 Java 型別**，無共用來源 |
| `channel` | enum（範本自己的 `NotificationTemplate.NotificationChannel`） | not null，預設 `IN_APP`；同樣是獨立列舉 |
| `name` | String | not null，上限 200 字 |
| `subject` | String | 選填，上限 500 字（EMAIL 用） |
| `contentTemplate` | String (TEXT) | not null，`{{variable}}` 佔位符語法 |
| `variables` | `List<String>`（JSONB） | 未明確提供時，由 `contentTemplate` 以正規表示式自動擷取 |
| `isActive` | Boolean | 預設 `true` |
| `priority` | Integer | 預設 `0` |
| `createdBy` / `updatedBy` | UUID | |
| `createdAt` / `updatedAt` / `deletedAt` | Instant | 刪除為軟刪除：`deletedAt` 寫入 + `isActive=false`，不實際移除資料列 |

### 3.4 `UserNotificationPreference`（表 `user_notification_preferences`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `userId` | UUID | not null |
| `notificationType` | enum `Notification.NotificationType` | not null（與 `Notification` 共用同一個列舉型別，和 §3.3 範本的獨立列舉不同） |
| `channel` | enum `Notification.NotificationChannel` | not null |
| `enabled` | boolean | not null，預設 `true` |
| `createdAt` / `updatedAt` | Instant | |

唯一約束 `(user_id, notification_type, channel)`。

### 3.5 列舉 / Enums

| 列舉 | 值 |
|------|-----|
| `NotificationType`（`Notification`／DTO 共用） | `ORDER_CONFIRMED`、`ORDER_PAID`、`ORDER_SHIPPED`、`ORDER_DELIVERED`、`ORDER_COMPLETED`、`ORDER_CANCELLED`、`BOOKING_CONFIRMED`、`BOOKING_REMINDER`、`PAYMENT_SUCCESS`、`PAYMENT_FAILED`、`REFUND_COMPLETED`、`REVIEW_REQUEST`、`NEW_MESSAGE`、`SYSTEM_ANNOUNCEMENT` |
| `NotificationChannel`（`Notification`／DTO 共用） | `IN_APP`、`EMAIL`、`SMS`、`PUSH` |
| `NotificationTemplate.NotificationType` / `NotificationTemplate.NotificationChannel` | 值與上列相同，但是**獨立的 Java enum**（§3.3），字串層面相容，型別層面不相容 |

`BOOKING_REMINDER`／`NEW_MESSAGE`／`SYSTEM_ANNOUNCEMENT` 三個類型目前**沒有任何程式碼路徑會建立**（`BuyerNotificationService` 與管理員 API 皆未使用；`BOOKING_REMINDER` 對應 PRD US-011，明文列為 Phase 2）。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/notifications/send` — 對指定使用者發送通知

**權限**：`hasRole('SUPER_ADMIN')`（DEF-238，Sprint 180：先前曾開放 STORE_OWNER/ADMIN，因 Service 層對 `userId` 完全不做範圍檢查，任一店主可對平台任何使用者發通知，已收斂為僅限 SUPER_ADMIN）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `userId` | UUID | 必填 | |
| `notificationType` | enum 字串 | 必填 | 需能解析成 `NotificationType` |
| `title` / `content` | String | 必填 | |
| `data` | `Map<String,Object>` | 選填 | |
| `channel` | enum 字串 | 選填 | 未提供預設 `IN_APP` |
| `recipient` | String | 選填 | **目前被忽略**（§1.2 第 8 點），一律改用使用者註冊資料 |

**回應**：`NotificationResponse`。若使用者停用該 type×channel 偏好，回應 `isSent=false`、`errorMessage="Skipped by user preference"`，且**不會**真的建立任何資料列（§1.2 第 5 點）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `userId` 不存在 | 404 | `E-1006` |
| 必填欄位缺失 | 400 | `E-9000` |

### 4.2 `POST /v2/notifications/broadcast` — 廣播通知

**權限**：`hasRole('SUPER_ADMIN')`。帶 `tenantId` 則只對該租戶的使用者廣播，否則對全平台所有使用者（`userRepository.findAll()`，**無分頁，一次載入全部使用者**，大型平台應留意效能）。

**請求**：同 §4.1 但無 `userId`／`recipient`，改用選填 `tenantId`。

**回應**：`int`（成功入佇的使用者數）。**不受使用者偏好設定攔截**——逐使用者呼叫 `NotificationProducerService.sendToQueue`（跳過 `NotificationService.sendNotification` 的偏好檢查邏輯），單一使用者入佇失敗（例外）只記警告、不中斷整體廣播。

### 4.3 `GET /v2/notifications` — 取得自己的通知列表

**權限**：已登入（`isAuthenticated()`）。查詢參數：`page`（預設 0）、`size`（預設 20，內部上限 50）、`unreadOnly`（預設 `false`）。回應 `NotificationListResponse`（含 `unreadCount`）。

### 4.4 `PUT /v2/notifications/read` — 標記通知已讀

**權限**：已登入。請求 body 選填：`notificationIds`（`List<UUID>`，`null` 或省略 body 時標記**全部**未讀通知為已讀）。只會更新屬於呼叫者自己的通知（逐筆 `filter(n -> n.getUserId().equals(userId))`，不屬於自己的 ID 靜默忽略，不報錯）。回應沿用 §4.3 格式（固定回傳前 20 筆）。

### 4.5 `DELETE /v2/notifications/{notificationId}` — 刪除自己的通知

**權限**：已登入，且限本人。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 通知不存在 | 404 | `E-8002` |
| 通知存在但非本人 | 403 | `E-1007`（與 §1.2 第 6 點「非 404」的設計一致） |

### 4.6 `GET /v2/notifications/unread-count` — 取得未讀數

**權限**：已登入。回應 `UnreadCountResponse`（`userId`、`unreadCount`）。

### 4.7 `GET /v2/notifications/history` — 取得通知歷史列表

**權限**：已登入。查詢參數：`page`（預設 0）、`size`（預設 20，內部上限 50）。回應 `HistoryListResponse`（含 `unreadCount`）。

### 4.8 `PUT /v2/notifications/history/{historyId}/read` — 標記單筆歷史已讀

**權限**：已登入，且限本人。已是已讀時為 no-op（不更新 `readAt`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 歷史不存在 | 404 | `E-8002`（與 §4.5 共用同一個錯誤碼，訊息文字寫「找不到通知」而非「找不到通知歷史」，見 §7） |
| 歷史存在但非本人 | 403 | `E-1007` |

### 4.9 `GET /v2/notifications/history/unread-count` — 取得歷史未讀數

**權限**：已登入。回應 `HistoryUnreadCountResponse`。

### 4.10 `GET /v2/notifications/preferences` — 取得自己的通知偏好

**權限**：已登入（無明確 `@PreAuthorize`，落入 `SecurityConfig` 的預設規則 `anyRequest().authenticated()`）。回應所有 `NotificationType × NotificationChannel` 組合（14 類型 × 4 通道 = 56 筆）的 `PreferenceItem` 陣列；未明確設定的組合回傳 `enabled=true`。

### 4.11 `PUT /v2/notifications/preferences` — Upsert 單一通知偏好

**權限**：已登入（同 §4.10）。

**請求**：`notificationType`（String，必填）、`channel`（String，必填）、`enabled`（Boolean，必填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `notificationType`／`channel` 字串無法解析成列舉 | 400 | `E-9000` |

### 4.12 `GET /v2/notification-templates` — 範本列表

**權限**：`notification_template:read`（STORE_OWNER／STORE_STAFF／ADMIN／SUPER_ADMIN；Sprint 286／DEF-330(a) 之前為 `isAuthenticated()`，任何登入者皆可呼叫）。查詢參數：`notificationType`、`channel`、`isActive`（皆選填，用於篩選）、`page`（預設 0）、`size`（預設 20，內部上限 100）、`targetTenantId`（UUID，選填，Sprint 287／DEF-394）。查詢範圍的租戶為：**呼叫者是 ADMIN／SUPER_ADMIN 且有帶 `targetTenantId`** 時為該租戶，否則為呼叫者的 `TenantContext.getCurrentTenant()`（`NotificationTemplateRepository.searchTemplates`，實際租戶篩選邏輯在 Repository 查詢條件內，非本文件覆蓋範圍的 SQL 細節）。**非管理員帶 `targetTenantId` 會被忽略**（一律查自己的租戶），不是錯誤，也不會回 403。

### 4.13 `GET /v2/notification-templates/{id}` — 範本詳情

**權限**：`notification_template:read`（同 §4.12）。ADMIN／SUPER_ADMIN 不受下表第二列的租戶限制，可讀取任一租戶的範本（Sprint 287／DEF-394）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 範本不存在 | 404 | `E-8003` |
| 範本的 `tenantId` 非 `null` 且與呼叫者租戶不同（**呼叫者不是 ADMIN／SUPER_ADMIN 時**） | 403 | `E-1007` |

### 4.14 `POST /v2/dashboard/notification-templates` — 建立範本

**權限**：`notification_template:create`（STORE_OWNER／ADMIN／SUPER_ADMIN）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `templateCode` | String | 必填 | 上限 100 字 |
| `notificationType` / `channel` | enum 字串 | 必填 | |
| `name` | String | 必填 | 上限 200 字 |
| `subject` | String | 選填 | 上限 500 字 |
| `contentTemplate` | String | 必填 | |
| `variables` | `List<String>` | 選填 | 未提供則自動從 `contentTemplate` 擷取 |
| `isActive` | Boolean | 選填 | 預設 `true` |
| `priority` | Integer | 選填 | 預設 `0` |
| `targetTenantId` | UUID | 選填 | Sprint 287／DEF-394。**僅 ADMIN／SUPER_ADMIN 有效**：代表指定店鋪建立範本，資料列的 `tenantId` 與 `templateCode` 唯一性檢查都以此租戶為準；其他角色帶值會被忽略，沿用呼叫者自己的租戶。**不驗證該租戶是否存在**（見 §7） |

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 同租戶下 `templateCode` 已存在（ADMIN 代建時指「目標租戶」下） | 409 | `E-8012`（Sprint 278／DEF-378 修復前為誤用付款模組的 `E-6001`，見 §7） |

### 4.15 `PUT /v2/dashboard/notification-templates/{id}` — 更新範本

**權限**：`notification_template:update`（同 §4.14 角色）。所有欄位皆選填，只更新有提供的欄位；改 `contentTemplate` 會重新自動擷取 `variables`（除非同時明確提供 `variables`，後者優先）。**ADMIN／SUPER_ADMIN 可更新任一租戶的範本**（Sprint 287／DEF-394）；此時 `templateCode` 改名的重複檢查比對的是**該範本實際所屬的租戶**，而不是管理員自己的（系統）租戶。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 範本不存在 | 404 | `E-8003` |
| 非同租戶（呼叫者不是 ADMIN／SUPER_ADMIN 時） | 403 | `E-1007` |
| 改名後與既有 `templateCode` 衝突（同一租戶範圍內） | 409 | `E-8012`（同 §4.14） |

### 4.16 `DELETE /v2/dashboard/notification-templates/{id}` — 刪除範本

**權限**：`notification_template:delete`（同 §4.14 角色）。軟刪除（§3.3）。ADMIN／SUPER_ADMIN 可刪除任一租戶的範本（Sprint 287／DEF-394）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 範本不存在 | 404 | `E-8003` |
| 非同租戶（呼叫者不是 ADMIN／SUPER_ADMIN 時） | 403 | `E-1007` |

### 4.17 `POST /v2/notification-templates/render` — 渲染範本預覽

**權限**：`notification_template:read`（STORE_OWNER／STORE_STAFF／ADMIN／SUPER_ADMIN；唯一真正檢查此權限字串的範本端點，見 §2）。

**請求**：`templateCode`（String）、`variables`（`Map<String,String>`）。依呼叫者 `tenantId` 查找範本（`findByTemplateCodeAndTenantId`，不會跨租戶找到其他店鋪的同名範本）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 範本不存在（含同代碼但不同租戶） | 404 | `E-8003` |
| 範本 `isActive=false` | 403 | `E-8011`（語意上較接近「範本已停用」而非「無權限」，但 `GlobalExceptionHandler` 把 `E-8011` 歸在 403 群組） |

**本端點僅回傳渲染結果供人工預覽，不會觸發任何實際發送**（§1.1 範圍外）。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 買家/一般登入者 | STORE_STAFF | STORE_OWNER／ADMIN | SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| 發送／廣播（§4.1～4.2） | ❌ | ❌ | ❌ | ✅ |
| 自己的通知/歷史/偏好（§4.3～4.11） | ✅ | ✅ | ✅ | ✅ |
| 範本列表/詳情（§4.12～4.13） | ❌（Sprint 286／DEF-330(a) 起；先前任何登入者皆可） | ✅ | ✅ | ✅ |
| 範本建立/更新/刪除（§4.14～4.16） | ❌ | ❌ | ✅ | ✅ |
| 範本渲染預覽（§4.17） | ❌ | ✅ | ✅ | ✅ |

**租戶範圍**：ADMIN／SUPER_ADMIN 在範本的列表、詳情、建立、更新、刪除可跨租戶（`targetTenantId` 與擁有權旁路，Sprint 287／DEF-394）；STORE_OWNER／STORE_STAFF 限自己的租戶；渲染預覽（§4.17）一律依呼叫者自己的租戶查找。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1006` | 404 | 找不到使用者（§4.1） |
| `E-8002` | 404 | 找不到通知或通知歷史（§4.5、§4.8；兩種資源共用同一個碼，訊息文字固定寫「找不到通知」，見 §7） |
| `E-1007` | 403 | 存取他人的通知/歷史/範本（§4.5、§4.8、§4.13、§4.15、§4.16） |
| `E-8003` | 404 | 找不到通知範本（§4.13、§4.15、§4.16、§4.17） |
| `E-8012` | 409 | 範本代碼重複（§4.14、§4.15；Sprint 278／DEF-378 修復前誤用付款模組的 `E-6001`，見 §7） |
| `E-8011` | 403 | 範本已停用（§4.17） |
| `E-9000` | 400 | 列舉字串無法解析、或 Bean Validation 失敗（所有 `@Valid` 請求共用） |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| **`E-6001` 錯誤碼誤用（登記為 DEF-378，已於 Sprint 278 修復）** | `NotificationTemplateService.createTemplate`／`updateTemplate` 對「範本代碼重複」原拋 `BusinessException(ErrorCode.E_6001, ...)`；`E_6001` 的官方定義是「付款失敗」，與範本代碼衝突毫無語意關聯。已新增專屬碼 `E-8012`（範本代碼重複，409）取代，`NotificationTemplateServiceTest` 補強為斷言具體錯誤碼 |
| ~~範本讀取端點權限不一致~~（已於 Sprint 286／DEF-330(a) 修復） | §4.12／4.13（列表/詳情）原僅要求登入、不檢查 `notification_template:read`，現已改為該權限，與 §4.17 一致；一般買家不再能列出或查看範本 |
| ADMIN 跨租戶管理範本僅有後端能力（Sprint 287／DEF-394） | 目前沒有任何前端畫面讓 ADMIN 選定店鋪後管理其通知範本，`targetTenantId` 只能由 API 直接呼叫。§4.17（渲染預覽）未納入跨租戶範圍，仍只依呼叫者自己的租戶查找範本 |
| `targetTenantId` 不驗證租戶是否存在（Sprint 287，讀碼確認、未實測） | `notification_templates.tenant_id` 是可為 `NULL` 的 UUID，只有索引、沒有外鍵（見 V28 migration），`createTemplate` 也沒有檢查目標租戶是否存在；ADMIN／SUPER_ADMIN 帶一個不存在的 `targetTenantId` 會建出一筆不屬於任何店鋪的範本。只有管理員角色能觸發，風險低，未登記為獨立缺陷 |
| `isSent` 欄位的歷史資料不可信 | Sprint 271（DEF-317）之前，所有通道的 `isSent` 一律寫 `true`，即使 EMAIL／SMS／PUSH 從未真正送達。Sprint 271 之後新建立的通知才反映真實結果，資料庫裡 Sprint 271 之前的既有列不會被回溯更正 |
| `NotificationHistory.tenantId` 寫入路徑固定為 `null` | 欄位存在且可儲存租戶 ID，但 `NotificationConsumerService` 呼叫 `createHistory` 時第二參數固定傳 `null`，目前沒有任何路徑會寫入非空值 |
| `BOOKING_REMINDER`／`NEW_MESSAGE`／`SYSTEM_ANNOUNCEMENT` 從未被觸發 | 三個列舉值存在、資料庫可儲存，但全庫無任何程式碼路徑會以這些類型建立通知（§3.5） |
| 範本的 `NotificationType`／`NotificationChannel` 與通知本體是兩個獨立的 Java enum | 字串值相同（可用字串互相解析），但型別不互通，維護時需注意兩處同步修改（§3.3、§3.5） |
| `NotificationController`／`NotificationPreferenceController` 的 `@PreAuthorize` 層幾乎無 HTTP 層測試覆蓋 | 既有測試（`NotificationServiceTest`、`NotificationPreferenceServiceTest`）皆直接呼叫 Service，不經過 Spring Security／HTTP 層；只有 `/v2/notifications/history/**`（`NotificationController` 的歷史子功能）與範本管理（`NotificationTemplateController`）有 `M09*IntegrationTest` 的 MockMvc／真實 JWT 覆蓋。本文件列出的 `/v2/notifications/send`／`broadcast`／列表／已讀／刪除／未讀數／偏好設定的權限字串，未被任何測試驗證過會正確生效或正確回應 401/403 |
| FRD §6C 先前描述已部分過時 | Sprint 271 前 FRD §6C.2／6C.5 寫「EMAIL/SMS/PUSH 一律標為已送出」，已隨本輪一併更新為 Sprint 271 後的真實行為（見本文件 §1.2 第 3 點），詳見 FRD §6C 修訂記錄 |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | US-001（預訂成功即時通知）、US-005（退款狀態即時通知）、US-014（支付失敗通知提醒）；M09 原始設計為 Phase 2（RabbitMQ、多通道、模板化） |
| FRD | §6C「M09 訊息通知中心」（現況與 PRD 落差的權威敘述） |
| 相關追蹤項目 | [DEF-286](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（API 文件完整性，本文件為其範圍內一項）、[DEF-317](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（Sprint 271，通知管道誠實化）、[DEF-305](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（預建列與消費者更新時序）、[DEF-238](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（收件人收斂為使用者註冊資料）、[DEF-378](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（本文件新發現，`E-6001` 誤用，已於 Sprint 278 修復為 `E-8012`）、[DEF-330](../../04_planning/DEFERRED_ITEMS_TRACKER.md)(a)（Sprint 286，範本列表／詳情改用 `notification_template:read`）、[DEF-394](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（Sprint 287，ADMIN 跨租戶管理範本） |
| 實作 | `NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController`（`api/controller/`）；`NotificationService`／`NotificationHistoryService`／`NotificationPreferenceService`／`NotificationTemplateService`（`core/notification/`）；`NotificationProducerService`／`NotificationConsumerService`（`infrastructure/mq/`）；`BuyerNotificationService`（`core/notification/`，無對外端點，見 §1.2 第 7 點） |
| 契約測試 | 見文件頂部「依據」一行所列測試 |

---

**文件狀態**：v1.1（Sprint 287：補登 DEF-330(a) 與 DEF-394 的範本權限／跨租戶行為；v1.0 為 Sprint 272 新建，DEF-286 範圍內首次補齊 M09）。
