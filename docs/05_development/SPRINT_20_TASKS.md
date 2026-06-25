# Sprint 20 執行記錄 / Sprint 20 Tasks

> **Sprint 編號**: Sprint 20
> **期間**: 2026-07-21 ~ 2026-08-01
> **建立日期**: 2026-06-26
> **狀態**: ✅ 開發完成

---

## 執行摘要

| 類型 | 數量 |
|------|------|
| 完成 User Stories | 6/6（US-001 ~ US-006） |
| 新增測試數 | 19 個（9 Unit + 10 Integration） |
| 總測試數 | 583+ tests |
| CI 狀態 | ✅ BUILD SUCCESS |

---

## US-001：MQ catch(Exception) 細分（✅ 完成）

**Commit**: `6619cb0`

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | 掃描確認 MQ 7 處位置 |
| AC-002 | ✅ | 改為具體例外類型（MessagingException / AmqpException 等） |
| AC-003 | ✅ | 每個 catch 有對應 ErrorCode 或 fallback 邏輯 |
| AC-004 | ✅ | mvn test 100% 通過 |
| AC-005 | ✅ | `grep -rn "catch (Exception" .../mq/` 結果為 0 |

---

## US-002：AdminControllerE2ETest 間歇性失敗修復（✅ 完成）

**Commit**: `c9452e7`

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | 根本原因：BOOKING_ENABLED feature toggle 依賴 TestDatabaseInitializer |
| AC-002 | ✅ | 在 @BeforeEach 明確重建 feature toggle，消除依賴 |
| AC-003 | ✅ | 全套執行連續 3 次均無失敗 |
| AC-004 | ✅ | 單獨執行仍通過 |

---

## US-003：非 MQ catch(Exception) 審查（✅ 完成）

**Commit**: `bde4945`

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | 審查 5 處（SettlementGenerator / NotificationService × 2 / RedisCartService / BookingController） |
| AC-002 | ✅ | 決策記錄：BookingController 細分；其餘 4 處為合理容錯設計 |
| AC-003 | ✅ | BookingController catch(Exception) 已細分 |
| AC-004 | ✅ | 合理設計處已加說明性 Log |
| AC-005 | ✅ | mvn test 100% 通過（295 tests） |

---

## US-004：@Deprecated 清理（✅ 完成）

**Commit**: `b9da279`

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | 掃描確認 11 處（SettlementService × 8, MediaService × 1, ReviewDto × 1, RefreshTokenService × 1） |
| AC-002 | ✅ | 確認呼叫方（grep 驗證） |
| AC-003 | ✅ | 無呼叫方方法已刪除 |
| AC-004 | ✅ | 有呼叫方方法已遷移後刪除 |
| AC-005 | ✅ | mvn test 100% 通過 |
| AC-006 | ✅ | `grep -rn "@Deprecated" .../main/java/` 結果為 0 |

---

## US-005：M09 通知歷史查詢 API（✅ 完成）

**Commit**: Sprint 20 US-005 batch

### 新增檔案
- `V42__Create_Notification_History.sql` — Flyway Migration
- `NotificationHistory.java` — Entity
- `NotificationHistoryRepository.java` — Repository
- `NotificationHistoryService.java` — Service（查詢/已讀/未讀計數）
- `NotificationHistoryServiceTest.java` — 單元測試（9 tests）
- `M09NotificationHistoryIntegrationTest.java` — 整合測試（7 tests）

### 修改檔案
- `NotificationDto.java` — 新增 HistoryResponse / HistoryListResponse / HistoryUnreadCountResponse
- `NotificationService.java` — sendNotification() 新增 history 寫入
- `NotificationController.java` — 新增 /history GET/PUT/GET 三支端點

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | `GET /v2/notifications/history` 分頁查詢 |
| AC-002 | ✅ | 回應含 notificationId/type/channel/title/body/isRead/createdAt |
| AC-003 | ✅ | `PUT /v2/notifications/history/{id}/read` 單筆標記已讀 |
| AC-004 | ✅ | `GET /v2/notifications/history/unread-count` |
| AC-005 | ✅ | V42 Migration 建立 notification_history 表（含 is_read/user_id/tenant_id） |
| AC-006 | ✅ | mvn verify -Pintegration-test 全套通過 |

---

## US-006：M08 評分統計端點（✅ 完成）

**Commit**: Sprint 20 US-006 batch

### 修改檔案
- `ProductController.java` — 新增 `GET /v2/products/{productId}/reviews/stats`
- `ReviewService.java` — 修正 averageRating null 行為（無評分時回傳 null 而非 0.0）

### 新增檔案
- `M08ReviewStatsIntegrationTest.java` — 整合測試（3 tests）

| AC | 狀態 | 說明 |
|----|------|------|
| AC-001 | ✅ | `GET /v2/products/{productId}/reviews/stats` 回傳 averageRating/totalReviews/distribution |
| AC-002 | ✅ | 無評分時 averageRating: null, totalReviews: 0 |
| AC-003 | ✅ | 基於現有 reviews 表，無需新增 Migration |
| AC-004 | ✅ | mvn test 100% 通過 |
| AC-005 | ✅ | API 端點文件已更新（Controller 說明） |

---

## 技術說明

### notification_history vs notifications 表設計決策
- `notifications`：MQ 發送追蹤（retry_count, error_message, sent_at, recipient）
- `notification_history`：用戶端已讀/未讀歷史（輕量，面向前端）
- 分離原因：職責分明，未來可獨立擴充或歸檔

### averageRating null 修正
- Sprint 19 之前：無評分回傳 `0.0`（容易誤判）
- Sprint 20 修正：無評分回傳 `null`（符合 AC-002，前端可顯示「尚無評分」）

---

**文件版本**: v1.0
**建立日期**: 2026-06-26
**建立者**: Dev David + Claude Code
