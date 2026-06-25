# Release Notes - v2026.08.01-01

**發布日期**: 2026-08-01
**發布類型**: Minor（技術債清零 + 新功能）
**Sprint**: Sprint 20
**Git Tag**: `v2026.08.01-01`
**基於 Commit**: Sprint 20 開發完成 commit
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 20 達成兩大里程碑：**生產程式碼技術債雙零**（`catch(Exception)` = 0、`@Deprecated` = 0）和**M09/M08 新功能上線**。MQ 模組 7 處過寬例外全部細分；AdminControllerE2ETest 間歇性失敗根本原因定位並修復；M09 通知歷史查詢 API（3 支端點）與 M08 評分統計端點（1 支端點）正式上線。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 6 / 6（100%）|
| Story Points | 10 SP（規劃 11 SP） |
| 達成率 | 100% 規劃目標 |
| 測試狀態 | 583+ tests, 0 Failures, 0 Errors |
| 新增測試 | 19 個（Unit 9 + Integration 10） |
| 新增 Flyway Migration | V42__Create_Notification_History.sql |
| CI 狀態 | ✅ BUILD SUCCESS |

---

## 新功能 ✨

### M09 通知系統 — 通知歷史查詢（US-005）

#### 通知歷史列表 API

- **新增 API**: `GET /v2/notifications/history`
- **功能**: 查詢登入用戶通知歷史記錄（分頁，最新在前）
- **分頁參數**: `page`（預設 0）、`size`（預設 20，最大 50）
- **需認證**: 需要 Bearer Token（登入用戶）

**回應範例**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "notificationId": "550e8400-e29b-41d4-a716-446655440000",
        "type": "ORDER_CONFIRMED",
        "channel": "IN_APP",
        "title": "訂單已確認",
        "body": "您的訂單 #1234 已確認",
        "isRead": false,
        "readAt": null,
        "createdAt": "2026-07-25T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "unreadCount": 3
  }
}
```

#### 標記單筆通知已讀 API

- **新增 API**: `PUT /v2/notifications/history/{historyId}/read`
- **功能**: 將指定通知標記為已讀（設定 `isRead: true` 和 `readAt`）
- **冪等設計**: 已讀記錄重複呼叫不會更新
- **需認證**: 只能標記自己的通知

**回應範例**:
```json
{
  "success": true,
  "message": "Notification marked as read",
  "data": {
    "notificationId": "550e8400-e29b-41d4-a716-446655440000",
    "isRead": true,
    "readAt": "2026-07-25T11:00:00Z"
  }
}
```

#### 通知歷史未讀計數 API

- **新增 API**: `GET /v2/notifications/history/unread-count`
- **功能**: 取得登入用戶的未讀通知歷史數量
- **需認證**: 需要 Bearer Token

**回應範例**:
```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "unreadCount": 3
  }
}
```

---

### M08 評價系統 — 商品評分統計（US-006）

#### 商品評分統計 API

- **新增 API**: `GET /v2/products/{productId}/reviews/stats`
- **功能**: 查詢指定商品的評分統計（平均分、總評數、星級分布）
- **需認證**: 需要 Bearer Token

**回應範例（有評分）**:
```json
{
  "success": true,
  "data": {
    "listingId": "550e8400-e29b-41d4-a716-446655440001",
    "averageRating": 4.39,
    "totalReviews": 18,
    "rating1Count": 0,
    "rating2Count": 1,
    "rating3Count": 2,
    "rating4Count": 5,
    "rating5Count": 10,
    "distribution": {
      "1": 0, "2": 1, "3": 2, "4": 5, "5": 10
    }
  }
}
```

**回應範例（無評分）**:
```json
{
  "success": true,
  "data": {
    "listingId": "...",
    "averageRating": null,
    "totalReviews": 0,
    "rating1Count": 0,
    "rating2Count": 0,
    "rating3Count": 0,
    "rating4Count": 0,
    "rating5Count": 0
  }
}
```

---

## 技術債清理 🔧

### MQ catch(Exception) 細分（US-001）

**修改範圍**: `NotificationProducerService.java`（1 處）、`NotificationConsumerService.java`（6 處）

- 所有 `catch(Exception)` 改為具體例外類型（`AmqpException`、`MessagingException`、`BusinessException`、`DataAccessException`）
- 消費者端 DLQ 保護邏輯明確化
- **驗證**: `grep -rn "catch (Exception" .../mq/` 結果為 0

### AdminControllerE2ETest 間歇性失敗修復（US-002）

- **根本原因**: BOOKING_ENABLED feature toggle 依賴 `TestDatabaseInitializer` 全域初始化，易被其他測試修改
- **修復**: 在 `@BeforeEach` 明確重建所需 feature toggle，消除順序依賴
- **驗證**: 全套執行連續 3 次均無失敗

### 非 MQ catch(Exception) 審查（US-003）

| 位置 | 決策 | 說明 |
|------|------|------|
| `BookingController.java` | 細分 | 控制器層不應有寬泛 catch |
| `NotificationService.java` × 2 | 保留 + 說明 | 通知容錯：單次通知失敗不影響業務流程 |
| `RedisCartService.java` | 保留 + 說明 | Redis 快取 fallback：降級為直接查詢 DB |
| `SettlementGenerator.java` | 保留 + 說明 | 結算批次容錯：單筆失敗不中斷整批 |

### @Deprecated 清理（US-004）

| 位置 | 處理方式 | 數量 |
|------|---------|------|
| `SettlementService.java` | 呼叫方遷移後刪除 | 8 處 |
| `MediaService.java` | 直接刪除（無呼叫方） | 1 處 |
| `ReviewDto.java` | 直接刪除（無呼叫方） | 1 處 |
| `RefreshTokenService.java` | 呼叫方遷移後刪除 | 1 處 |

**驗證**: `grep -rn "@Deprecated" .../main/java/` 結果為 0

---

## Bug 修正 🐛

### averageRating null 語義修正

- **問題**: `GET /v2/reviews/listing/{id}/stats` 無評分時回傳 `averageRating: 0.0`（語義不清）
- **修正**: 無評分時回傳 `averageRating: null`（前端可顯示「尚無評分」）
- **影響**: `GET /v2/products/{id}/reviews/stats` 和 `GET /v2/reviews/listing/{id}/stats` 同步受益

---

## 資料庫變更 🗃️

### V42__Create_Notification_History.sql

```sql
CREATE TABLE notification_history (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    tenant_id     UUID,
    notification_type VARCHAR(50)  NOT NULL,
    channel       VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    title         VARCHAR(500) NOT NULL,
    body          TEXT,
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_history PRIMARY KEY (id)
);
-- 索引: idx_notif_history_user_id, idx_notif_history_user_unread, idx_notif_history_created_at
```

---

## 破壞性變更 ⚠️

無破壞性變更。

---

## 升級注意事項

1. 執行 Flyway Migration（V42 自動執行）
2. `averageRating` 欄位可能由 `0.0` 變為 `null`（前端需處理 null 情境）

---

**版本**: v2026.08.01-01
**文件版本**: v1.0
**建立日期**: 2026-06-26
**建立者**: Dev David + Claude Code
