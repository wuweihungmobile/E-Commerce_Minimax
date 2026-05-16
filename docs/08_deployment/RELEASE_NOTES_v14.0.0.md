# Release Notes - v14.0.0

**發布日期**: 2026-05-16
**發布類型**: Minor (新增功能)
**Sprint**: Sprint 14
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 新功能 ✨

### M09 通知系統 Phase 2 (MQ 非同步發送)

#### MQ 非同步通知機制
- **US-001**: MQ 非同步發送實作
  - `NotificationProducerService` - 訊息生產者，使用 Redis Stream
  - `NotificationConsumerService` - 訊息消費者，@Scheduled 定時消費
  - `RedisStreamConfig` - Redis Stream 設定，NOTIFICATION_STREAM, NOTIFICATION_CONSUMER_GROUP

#### 訊息格式與可靠性
- `NotificationMessage.java` - 統一訊息格式
  - messageId, userId, notificationType, title, content, data
  - channel, recipient, createdAt, retryCount, errorMessage
- 重試機制: MAX_RETRY_COUNT = 3, RETRY_DELAY_SECONDS = 30
- DLQ (Dead Letter Queue) - 失敗訊息處理機制

### M07 金流 Phase 2-B (Stripe 整合準備)

#### 統一支付介面
- **US-002**: Stripe/LinePay 整合介面
  - `PaymentGateway.java` - 統一介面定義
  - `PaymentGatewayFactory.java` - 工廠類，根據 paymentMethod 選擇對應網關
  - `StripePaymentGateway.java` - Stripe 實現 (Phase 2-B 預留)
  - `LinePayPaymentGateway.java` - LinePay 實現
  - `MockPaymentGateway.java` - Mock 實現 (Phase 2-A)

#### PaymentGateway 操作
- `createPaymentIntent()` - 建立支付意圖
- `confirmPayment()` - 確認支付
- `processRefund()` - 處理退款 (全額/部分)
- `getPaymentStatus()` - 查詢支付狀態

### M18 知識管理 Phase 2-C (FAQ 進階功能)

#### FAQ 置頂排序
- **US-003**: FAQ 文章置頂功能
  - `FaqService.getPinnedArticles()` - 取得置頂文章列表
  - `findByTenantIdAndIsPinnedTrueOrderBySortOrderAsc` - Repository 方法
  - 支援 `isPinned` 欄位和 `sortOrder` 排序

#### 關鍵字高亮搜尋
- `FaqService.searchArticlesWithHighlight()` - 搜尋並高亮關鍵字
- `highlightKeyword()` - 高亮關鍵字方法
- `highlightedQuestion`, `highlightedAnswer` - DTO 欄位
- 預設高亮標籤: `<mark>` 和 `</mark>`

#### FAQ 分類統計
- `FaqService.getCategoryStats()` - 取得分類統計
- `CategoryStatsDto` - 統計 DTO (categoryId, categoryName, categorySlug, totalArticles, publishedArticles)
- `countByTenantIdAndCategoryId` - 統計方法

### M07 金流 (退款機制完善)

#### 全額/部分退款
- **US-004**: 退款機制完善
  - `MockPaymentGateway.processRefund()` - 退款處理
  - 退款金額驗證 (不可超過支付金額)
  - `RefundResult` - 包含 refundId, refundAmount, status
  - Idempotency Key - 防止重複退款

---

## 改進 🚀

### MQ 訊息處理
- 非同步處理提升系統效能
- Redis Stream 訊息持久化
- 失敗訊息 DLQ 處理

### 統一支付介面
- 多支付網關統一抽象
- 工廠模式選擇合適支付網關
- Phase 2-B Stripe 預留介面

### FAQ 功能增強
- 置頂文章優先顯示
- 搜尋結果關鍵字標記
- 分類統計一目了然

---

## 技術改進 🔧

### 新增檔案

| 模組 | 檔案路徑 | 說明 |
|------|----------|------|
| M09 | `infrastructure/mq/NotificationMessage.java` | 訊息格式 |
| M09 | `infrastructure/mq/NotificationProducerService.java` | 生產者服務 |
| M09 | `infrastructure/mq/NotificationConsumerService.java` | 消費者服務 |
| M09 | `infrastructure/mq/RedisStreamConfig.java` | Redis Stream 設定 |
| M07 | `infrastructure/payment/PaymentGateway.java` | 統一介面 |
| M07 | `infrastructure/payment/PaymentGatewayRequestResponse.java` | DTO 類別 |
| M07 | `infrastructure/payment/StripePaymentGateway.java` | Stripe 實現 |
| M07 | `infrastructure/payment/LinePayPaymentGateway.java` | LinePay 實現 |
| M07 | `infrastructure/payment/MockPaymentGateway.java` | Mock 實現 |
| M07 | `infrastructure/payment/PaymentGatewayFactory.java` | 工廠類 |

### 修改檔案

| 模組 | 檔案路徑 | 說明 |
|------|----------|------|
| M18 | `core/faq/FaqService.java` | 新增 3 個方法 |
| M18 | `domain/repository/faq/FaqArticleRepository.java` | 新增 3 個方法 |
| M18 | `api/dto/faq/FaqArticleDto.java` | 新增 2 個欄位 |
| M07 | `domain/repository/PaymentRepository.java` | 新增 findByTransactionId |

---

## Bug 修復 🐛

| Issue | 描述 | PR |
|-------|------|-----|
| 無 | - | - |

---

## 重大變更 ⚠️

### 新 API 端點

| 模組 | 端點 | 方法 | 說明 |
|------|------|------|------|
| M09 | `/v2/notifications/mq/send` | POST | 發送 MQ 訊息 |
| M07 | `/v2/payments/intent` | POST | 建立支付意圖 |
| M07 | `/v2/payments/confirm` | POST | 確認支付 |
| M07 | `/v2/payments/refund` | POST | 處理退款 |
| M07 | `/v2/payments/status/{transactionId}` | GET | 查詢支付狀態 |
| M18 | `/v2/faqs/pinned` | GET | 取得置頂文章 |
| M18 | `/v2/faqs/search` | GET | 搜尋文章 (高亮) |
| M18 | `/v2/faqs/categories/stats` | GET | 取得分類統計 |

### 依賴變更

| 技術 | 版本 | 說明 |
|------|------|------|
| Spring Data Redis | 3.2.5 | Redis Stream 操作 |
| Redis Client | 預設 | StreamOperations |

---

## 已知問題
- 無

---

## 升級指南

### 前置條件
- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Redis 6+ (需支援 Stream)

### 部署步驟
1. 執行資料庫遷移: `mvn flyway:migrate` (如有)
2. 重啟 Backend 服務
3. 確認 Redis 連線正常
4. 驗證 API: `GET /v2/faqs/pinned`

### 版本相容性
- 此版本為 Minor 版本，向下相容
- 現有 API 不受影響
- 新增 M09/M07/M18 Phase 2 功能

---

## 相關連結

- [Sprint 14 Plan](docs/04_planning/SPRINT_14_PLAN.md)
- [Sprint 14 Tasks](docs/05_development/SPRINT_14_TASKS.md)
- [Sprint 14 Review](docs/05_development/SPRINT_14_REVIEW.md)

---

## 貢獻者

- Dev Team (Backend Development)
- QA Team (Testing & Validation)

---

**文件版本**: AISDLC v0.09
**驗證狀態**: ✅ 四方專家審議通過
**Sprint 14 狀態**: ✅ Ready for Release